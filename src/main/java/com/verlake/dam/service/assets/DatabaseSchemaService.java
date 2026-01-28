package com.verlake.dam.service.assets;

import com.mongodb.client.MongoDatabase;
import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.assets.dto.ColumnSchemaDTO;
import com.verlake.dam.entity.assets.dto.DatabaseSchemaDTO;
import com.verlake.dam.entity.assets.dto.TableSchemaDTO;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.enums.DatabaseType;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.exception.DatabaseAccessException;
import com.verlake.dam.repository.assets.AccessRequestRepository;
import com.verlake.dam.repository.assets.AssetCredentialsRepository;
import com.verlake.dam.service.assets.common.AssetValidationUtils;
import com.verlake.dam.service.assets.common.DatabaseConnectionUtils;
import com.verlake.dam.service.assets.mongodb.MongoDBConnectionUtils;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class DatabaseSchemaService {

    private final DatabaseConnectionUtils databaseConnectionUtils;
    private final AssetValidationUtils assetValidationUtils;
    private final UserService userService;
    private final AssetService assetService;
    private final AccessRequestRepository accessRequestRepository;
    private final AssetCredentialsRepository assetCredentialsRepository;
    private final AccessRequestService accessRequestService;

    public DatabaseSchemaService(DatabaseConnectionUtils databaseConnectionUtils,
                                 AssetValidationUtils assetValidationUtils,
                                 UserService userService,
                                 AssetService assetService,
                                 AccessRequestRepository accessRequestRepository,
                                 AssetCredentialsRepository assetCredentialsRepository, 
                                 AccessRequestService accessRequestService) {
        this.databaseConnectionUtils = databaseConnectionUtils;
        this.assetValidationUtils = assetValidationUtils;
        this.userService = userService;
        this.assetService = assetService;
        this.accessRequestRepository = accessRequestRepository;
        this.assetCredentialsRepository = assetCredentialsRepository;
        this.accessRequestService = accessRequestService;
    }

    /**
     * Get database schema for the current user based on their role and permissions
     * 
     * @param assetId Asset ID (required for asset owners)
     * @param requestId Access request ID (required for developers)
     * @param isAssetOwner true if requesting as asset owner, false if as developer, null to auto-detect
     * @return Database schema filtered by user's permissions
     */
    @Transactional(readOnly = true)
    public DatabaseSchemaDTO getSchemaForCurrentUser(Long assetId, Long requestId, Boolean isAssetOwner) throws CommonUtils.CryptoException {
        log.debug("Fetching database schema for current user - assetId: {}, requestId: {}, isAssetOwner: {}", 
                assetId, requestId, isAssetOwner);
        
        User currentUser = userService.getCurrentUser();
        boolean hasAssetOwnerRole = userService.hasRole(currentUser, Roles.ASSET_OWNER.getOriginalName());
        boolean hasDeveloperRole = userService.hasRole(currentUser, Roles.DEVELOPER.getOriginalName());
        
        log.debug("Current user roles - AssetOwner: {}, Developer: {}", hasAssetOwnerRole, hasDeveloperRole);
        
        // Priority 1: If requestId is provided, use developer path (explicit developer choice)
        if (requestId != null) {
            return handleDeveloperRequest(requestId, hasDeveloperRole);
        }
        
        // Priority 2: If assetId is provided, determine access type
        if (assetId != null) {
            return handleAssetRequest(assetId, isAssetOwner, currentUser, hasAssetOwnerRole, hasDeveloperRole);
        }
        
        // No parameters provided
        throw new DatabaseAccessException("Please provide either assetId (for asset owners) or requestId (for developers).", null);
    }
    
    /**
     * Handle developer request with requestId
     */
    private DatabaseSchemaDTO handleDeveloperRequest(Long requestId, boolean hasDeveloperRole) throws CommonUtils.CryptoException {
        if (!hasDeveloperRole) {
            throw new DatabaseAccessException("Insufficient permissions. You need DEVELOPER role to access schema via requestId.", null);
        }
        log.debug("Using developer path with explicit requestId: {}", requestId);
        return getSchemaForDeveloper(requestId);
    }
    
    /**
     * Handle asset request with assetId
     */
    private DatabaseSchemaDTO handleAssetRequest(Long assetId, Boolean isAssetOwner, User currentUser, 
                                               boolean hasAssetOwnerRole, boolean hasDeveloperRole) throws CommonUtils.CryptoException {
        Asset asset = validateAsset(assetId);
        
        // Check if asset is locked
        assetService.validateAssetNotLocked(asset, !hasAssetOwnerRole);
        
        // If isAssetOwner is explicitly provided, use it directly
        if (isAssetOwner != null) {
            return handleExplicitAccessType(assetId, isAssetOwner, currentUser, hasAssetOwnerRole, hasDeveloperRole);
        }
        
        // Auto-detect access type
        return handleAutoDetectAccess(assetId, currentUser, asset, hasAssetOwnerRole, hasDeveloperRole);
    }
    
    /**
     * Validate asset exists
     */
    private Asset validateAsset(Long assetId) {
        Asset asset = assetService.findById(assetId);
        if (asset == null) {
            throw new DatabaseAccessException("Asset not found: " + assetId, null);
        }
        return asset;
    }
    
    /**
     * Handle explicit access type (isAssetOwner parameter provided)
     */
    private DatabaseSchemaDTO handleExplicitAccessType(Long assetId, Boolean isAssetOwner, User currentUser,
                                                     boolean hasAssetOwnerRole, boolean hasDeveloperRole) throws CommonUtils.CryptoException {
        if (Boolean.TRUE.equals(isAssetOwner)) {
            if (!hasAssetOwnerRole) {
                throw new DatabaseAccessException("Insufficient permissions. You need ASSET_OWNER role to access schema as asset owner.", null);
            }
            log.info("Using asset owner path for asset {} (explicitly requested)", assetId);
            return getSchemaForAssetOwner(assetId);
        } else {
            if (!hasDeveloperRole) {
                throw new DatabaseAccessException("Insufficient permissions. You need DEVELOPER role to access schema as developer.", null);
            }
            log.info("Using developer path for asset {} (explicitly requested)", assetId);
            return getSchemaForDeveloperByAssetId(assetId, currentUser);
        }
    }
    
    /**
     * Handle auto-detect access type (isAssetOwner parameter not provided)
     */
    private DatabaseSchemaDTO handleAutoDetectAccess(Long assetId, User currentUser, Asset asset,
                                                   boolean hasAssetOwnerRole, boolean hasDeveloperRole) throws CommonUtils.CryptoException {
        log.debug("isAssetOwner not provided, auto-detecting access type for asset: {}", assetId);
        
        // Try asset owner path first
        if (hasAssetOwnerRole && tryAssetOwnerPath(assetId, currentUser, asset)) {
            return getSchemaForAssetOwner(assetId);
        }
        
        // Try developer path
        if (hasDeveloperRole) {
            return tryDeveloperPath(assetId, currentUser);
        }
        
        // No valid access found
        throw createNoAccessException(assetId, hasAssetOwnerRole, hasDeveloperRole);
    }
    
    /**
     * Try asset owner path
     */
    private boolean tryAssetOwnerPath(Long assetId, User currentUser, Asset asset) {
        Optional<AssetCredential> ownerCredential = assetCredentialsRepository
                .findByUserAndAssetAndUserAccessType(currentUser, asset, Roles.ASSET_OWNER.getOriginalName());
        
        if (ownerCredential.isPresent()) {
            log.info("Auto-detected: User has asset owner credentials for asset {}. Using asset owner path.", assetId);
            return true;
        } else {
            log.debug("User has ASSET_OWNER role but no credentials for asset {}. Checking developer access...", assetId);
            return false;
        }
    }
    
    /**
     * Try developer path
     */
    private DatabaseSchemaDTO tryDeveloperPath(Long assetId, User currentUser) throws CommonUtils.CryptoException {
        log.debug("Checking for developer access request for asset: {}", assetId);
        try {
            return getSchemaForDeveloperByAssetId(assetId, currentUser);
        } catch (DatabaseAccessException e) {
            throw e;
        } catch (CommonUtils.CryptoException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error checking developer access for asset {}: {}", assetId, e.getMessage());
            throw new DatabaseAccessException("Cannot access schema for asset: " + assetId + ". Please provide requestId or ensure you have asset owner access.", e);
        }
    }
    
    /**
     * Create appropriate no access exception
     */
    private DatabaseAccessException createNoAccessException(Long assetId, boolean hasAssetOwnerRole, boolean hasDeveloperRole) {
        if (!hasAssetOwnerRole && !hasDeveloperRole) {
            return new DatabaseAccessException("Insufficient permissions. You need ASSET_OWNER or DEVELOPER role to access schema.", null);
        }
        
        StringBuilder errorMsg = new StringBuilder("Cannot access schema for asset: " + assetId);
        if (hasAssetOwnerRole) {
            errorMsg.append(". You do not have asset owner credentials for this asset.");
        }
        if (hasDeveloperRole) {
            if (hasAssetOwnerRole) {
                errorMsg.append(" Also, no approved developer access request found.");
            } else {
                errorMsg.append(". No approved developer access request found. Please create and get approval for an access request first.");
            }
        }
        return new DatabaseAccessException(errorMsg.toString(), null);
    }
    
    /**
     * Get schema for developer by assetId (finds access request automatically)
     */
    private DatabaseSchemaDTO getSchemaForDeveloperByAssetId(Long assetId, User currentUser) throws CommonUtils.CryptoException {
        Asset asset = assetService.findById(assetId);
        if (asset == null) {
            throw new DatabaseAccessException("Asset not found: " + assetId, null);
        }
        
        // Check if asset is locked
        assetService.validateAssetNotLocked(asset, true);
        
        List<AccessRequest> requests = accessRequestRepository.findLatestRequestByAssetAndRequestor(asset, currentUser);
        
        if (requests.isEmpty()) {
            throw new DatabaseAccessException(
                    "No access request found for asset: " + assetId + ". Please create an access request first.", null);
        }
        
        AccessRequest approvedRequest = requests.stream()
                .filter(ar -> ar.getAssetApproverStatus() == ApprovalStatus.APPROVED 
                        && ar.getDeveloperApproverStatus() == ApprovalStatus.APPROVED)
                .findFirst()
                .orElseThrow(() -> {
                    // Check if there are pending requests
                    boolean hasPending = requests.stream()
                            .anyMatch(ar -> ar.getAssetApproverStatus() == ApprovalStatus.REQUESTED
                                    || ar.getAssetApproverStatus() == ApprovalStatus.APPROVAL_IN_PROGRESS);
                    
                    String errorMessage = hasPending 
                            ? "Access request for asset: " + assetId + " is pending approval. Please wait for approval."
                            : "No approved access request found for asset: " + assetId + ". Please create and get approval for an access request first.";
                    
                    return new DatabaseAccessException(errorMessage, null);
                });
        
        log.info("Found approved developer access request for asset {}. Using developer path.", assetId);
        return getSchemaForDeveloper(approvedRequest.getId());
    }
    
    /**
     * Get database schema for a developer with access request validation
     */
    @Transactional(readOnly = true)
    public DatabaseSchemaDTO getSchemaForDeveloper(Long requestId) throws CommonUtils.CryptoException {
        log.debug("Fetching database schema for developer with request ID: {}", requestId);
        
        AccessRequest accessRequest = assetValidationUtils.validateAccessRequest(requestId);
        AssetCredential credential = assetValidationUtils.validateAssetCredential(accessRequest);
        
        // Check if asset is locked
        assetService.validateAssetNotLocked(accessRequest.getAsset(), true);
        
        // Encrypt temporary credential if needed
        accessRequestService.encryptTemporaryCredential(credential, accessRequest);
        
        assetValidationUtils.validateAccessRequestStatus(accessRequest);
        
        return fetchDatabaseSchema(accessRequest.getAsset(), credential);
    }

    /**
     * Get database schema for an asset owner
     * Gets credential directly from asset_credentials table by assetId, userId, and userAccessType = "Asset Owner"
     */
    @Transactional(readOnly = true)
    public DatabaseSchemaDTO getSchemaForAssetOwner(Long assetId) {
        log.debug("Fetching database schema for asset owner with asset ID: {}", assetId);
        
        // Get current user
        User currentUser = userService.getCurrentUser();
        
        // Get asset
        Asset asset = assetService.findById(assetId);
        if (asset == null) {
            throw new DatabaseAccessException("Asset not found: " + assetId, null);
        }
        
        // Check if asset is locked
        assetService.validateAssetNotLocked(asset, true);
        
        // Get credential directly from asset_credentials table by assetId, userId, and userAccessType = "Asset Owner"
        AssetCredential credential = assetCredentialsRepository
                .findByUserAndAssetAndUserAccessType(currentUser, asset, Roles.ASSET_OWNER.getOriginalName())
                .orElseThrow(() -> {
                    log.error("Asset owner credential not found for user: {}, asset: {}", currentUser.getId(), assetId);
                    return new DatabaseAccessException("You do not have asset owner credentials for asset: " + assetId + ". Please ensure you are assigned as an asset owner.", null);
                });
        
        log.debug("Found asset owner credential for user: {}, asset: {}", currentUser.getId(), assetId);
        
        return fetchDatabaseSchema(asset, credential);
    }

    /**
     * Fetch database schema using the provided credential
     */
    private DatabaseSchemaDTO fetchDatabaseSchema(Asset asset, AssetCredential credential) {
        // Handle MongoDB separately since it doesn't use JDBC
        if (asset.getDatabaseType() == DatabaseType.MONGODB) {
            return fetchMongoDBSchema(asset, credential);
        }
        
        AssetCredential tempCredential = databaseConnectionUtils.createDecryptedTempCredential(credential);
        
        try (Connection connection = databaseConnectionUtils.getConnectionFromAssetCredential(tempCredential)) {
            String databaseName = connection.getCatalog();
            
            List<TableSchemaDTO> tables = fetchTables(connection, asset.getDatabaseType());
            
            int totalColumns = tables.stream()
                    .mapToInt(TableSchemaDTO::getColumnCount)
                    .sum();
            
            return DatabaseSchemaDTO.builder()
                    .databaseName(databaseName)
                    .tables(tables)
                    .totalTables(tables.size())
                    .totalColumns(totalColumns)
                    .build();
                    
        } catch (SQLException e) {
            log.error("Failed to fetch database schema for asset: {}", asset.getId(), e);
            throw new DatabaseAccessException("Failed to fetch database schema: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch MongoDB schema (collections) using the provided credential
     */
    private DatabaseSchemaDTO fetchMongoDBSchema(Asset asset, AssetCredential credential) {
        AssetCredential tempCredential = databaseConnectionUtils.createDecryptedTempCredential(credential);
        
        try {
            // Get MongoDB database using utility
            MongoDatabase mongoDb = MongoDBConnectionUtils.getMongoDatabase(tempCredential);
            String databaseName = mongoDb.getName();
            
            // List all collections (similar to tables in SQL databases)
            List<String> collections = MongoDBConnectionUtils.listCollections(mongoDb);
            
            // Convert collections to TableSchemaDTO format
            List<TableSchemaDTO> tables = new ArrayList<>();
            for (String collectionName : collections) {
                // For MongoDB, we don't have column information in the same way as SQL databases
                // Collections are schema-less, so we create a basic table schema
                List<ColumnSchemaDTO> columns = inferMongoDBCollectionSchema(mongoDb, collectionName);
                
                tables.add(TableSchemaDTO.builder()
                        .tableName(collectionName)
                        .tableType("COLLECTION")
                        .tableComment(null)
                        .columns(columns)
                        .columnCount(columns.size())
                        .schema(null) // MongoDB doesn't use schemas
                        .build());
            }
            
            int totalColumns = tables.stream()
                    .mapToInt(TableSchemaDTO::getColumnCount)
                    .sum();
            
            return DatabaseSchemaDTO.builder()
                    .databaseName(databaseName)
                    .tables(tables)
                    .totalTables(tables.size())
                    .totalColumns(totalColumns)
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to fetch MongoDB schema for asset: {}", asset.getId(), e);
            throw new DatabaseAccessException("Failed to fetch MongoDB schema: " + e.getMessage(), e);
        }
    }

    /**
     * Infers schema for a MongoDB collection by analyzing a sample document
     * 
     * @param mongoDb The MongoDB database
     * @param collectionName The name of the collection
     * @return List of column schemas inferred from the sample document
     */
    private List<ColumnSchemaDTO> inferMongoDBCollectionSchema(MongoDatabase mongoDb, String collectionName) {
        List<ColumnSchemaDTO> columns = new ArrayList<>();
        
        try {
            Document sampleDoc = mongoDb.getCollection(collectionName).find().first();
            if (sampleDoc != null) {
                // Create columns based on sample document keys
                for (String key : sampleDoc.keySet()) {
                    Object value = sampleDoc.get(key);
                    String dataType = value != null ? value.getClass().getSimpleName() : "Object";
                    
                    columns.add(ColumnSchemaDTO.builder()
                            .columnName(key)
                            .dataType(dataType)
                            .isNullable(true) // MongoDB fields are always nullable
                            .build());
                }
            }
        } catch (Exception e) {
            log.debug("Could not infer schema for collection {}: {}", collectionName, e.getMessage());
        }
        
        return columns;
    }

    /**
     * Fetch tables and their columns from the database
     */
    private List<TableSchemaDTO> fetchTables(Connection connection, DatabaseType databaseType) throws SQLException {
        List<TableSchemaDTO> tables = new ArrayList<>();
        
        // Get table metadata
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = connection.getCatalog();
        String schema = getSchemaName(databaseType, catalog);
        
        // First, try to get tables with explicit permissions (more efficient for PostgreSQL)
        List<String> accessibleTables = getTablesWithExplicitPermissions(connection, databaseType, schema);
        
        if (!accessibleTables.isEmpty()) {
            // Use the list of tables we know the user has permissions for
            log.debug("Found {} tables with explicit permissions for schema: {}", accessibleTables.size(), schema);
            for (String tableName : accessibleTables) {
                try {
                    String tableType = getTableType(metaData, catalog, schema, tableName);
                    String tableComment = getTableComment(metaData, catalog, schema, tableName, databaseType);
                    
                    // Get columns for this table
                    List<ColumnSchemaDTO> columns = fetchColumns(metaData, catalog, schema, tableName, databaseType, connection);
                    
                    tables.add(TableSchemaDTO.builder()
                            .tableName(tableName)
                            .tableType(tableType)
                            .tableComment(tableComment)
                            .columns(columns)
                            .columnCount(columns.size())
                            .schema(schema)
                            .build());
                } catch (SQLException e) {
                    log.warn("Error processing table {}: {}", tableName, e.getMessage());
                }
            }
        } else {
            // Fallback: use getTables() but filter by permissions
            log.debug("No explicit permissions found, using getTables() fallback for schema: {}", schema);
            try (ResultSet tableResultSet = metaData.getTables(catalog, schema, "%", new String[]{Constants.SCHEMA_TYPE_TABLE, Constants.SCHEMA_TYPE_VIEW})) {
                while (tableResultSet.next()) {
                    String tableName = tableResultSet.getString(Constants.SCHEMA_FIELD_TABLE_NAME);
                    String tableType = tableResultSet.getString(Constants.SCHEMA_FIELD_TABLE_TYPE);
                    String tableComment = getTableComment(tableResultSet, databaseType);
                    
                    // Check if user has permission to access this table
                    if (hasTablePermission(connection, databaseType, schema, tableName)) {
                        // Get columns for this table
                        List<ColumnSchemaDTO> columns = fetchColumns(metaData, catalog, schema, tableName, databaseType, connection);
                        
                        tables.add(TableSchemaDTO.builder()
                                .tableName(tableName)
                                .tableType(tableType)
                                .tableComment(tableComment)
                                .columns(columns)
                                .columnCount(columns.size())
                                .schema(schema)
                                .build());
                    } else {
                        log.debug("User does not have permission to access table: {}.{}", schema, tableName);
                    }
                }
            }
        }
        
        return tables;
    }

    /**
     * Check if the current user has permission to access a specific table
     */
    private boolean hasTablePermission(Connection connection, DatabaseType databaseType, String schema, String tableName) {
        try {
            switch (databaseType) {
                case MYSQL, POSTGRESQL, SQLSERVER:
                    return checkTablePermission(connection, schema, tableName);
                case ORACLE:
                    return hasOracleTablePermission(connection, tableName);
                default:
                    return true;
            }
        } catch (SQLException e) {
            log.debug("Error checking table permission for {}.{}: {}", schema, tableName, e.getMessage());
            // If we can't check permissions, assume permission exists to avoid blocking legitimate access
            return true;
        }
    }
    
    /**
     * Check Oracle table permissions using user_tab_privs
     */
    private boolean hasOracleTablePermission(Connection connection, String tableName) throws SQLException {
        String query = """
            SELECT COUNT(*) as count
            FROM user_tab_privs 
            WHERE table_name = ? 
                AND privilege IN ('SELECT', 'INSERT', 'UPDATE', 'DELETE', 'ALL')
        """;
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, tableName.toUpperCase());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(Constants.DB_QUERY_RESULT_COUNT) > 0;
                }
            }
        }
        return false;
    }
    
    /**
     * Common method to check table permissions for information_schema databases
     * Used by MySQL, PostgreSQL, and SQL Server (all use identical information_schema queries)
     */
    private boolean checkTablePermission(Connection connection, String schema, String tableName) throws SQLException {
        // 1) Prefer explicit privilege check (where available)
        // Use SELECT only; 'ALL' is not a valid privilege_type value in information_schema
        String explicitPrivilegeQuery = """
            SELECT COUNT(*) as count
            FROM information_schema.table_privileges 
            WHERE table_schema = ? 
                AND table_name = ? 
                AND grantee = CURRENT_USER
                AND privilege_type IN ('SELECT')
        """;

        try (PreparedStatement stmt = connection.prepareStatement(explicitPrivilegeQuery)) {
            stmt.setString(1, schema);
            stmt.setString(2, tableName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(Constants.DB_QUERY_RESULT_COUNT) > 0) {
                    return true;
                }
            }
        } catch (SQLException e) {
            log.debug("Error checking table privilege via information_schema for {}.{}: {}", schema, tableName, e.getMessage());
        }

        // 2) Fallback: use DatabaseMetaData.getTables() to check if table exists
        // Note: This shows all tables the user can see metadata for, not just ones with permissions
        // But it's safer than dynamic SQL and we'll be permissive for backward compatibility
        try {
            DatabaseMetaData md = connection.getMetaData();
            String catalog = connection.getCatalog();
            try (ResultSet tables = md.getTables(catalog, schema, tableName, new String[]{Constants.SCHEMA_TYPE_TABLE, Constants.SCHEMA_TYPE_VIEW})) {
                if (tables.next()) {
                    // Table exists and user can see it in metadata
                    // Be permissive: if user can see table metadata, assume they have some access
                    // The actual query execution will fail if they don't have permissions
                    log.debug("Table {}.{} exists in metadata, assuming user has access", schema, tableName);
                    return true;
                } else {
                    // Table doesn't exist or user can't see it
                    return false;
                }
            }
        } catch (SQLException e) {
            log.debug("DatabaseMetaData.getTables() failed for {}.{}: {}", schema, tableName, e.getMessage());
            return false;
        }
    }

    /**
     * Fetch columns for a specific table
     */
    private List<ColumnSchemaDTO> fetchColumns(DatabaseMetaData metaData, String catalog, String schema, 
                                             String tableName, DatabaseType databaseType, Connection connection) throws SQLException {
        List<ColumnSchemaDTO> columns = new ArrayList<>();
        
        try (ResultSet columnResultSet = metaData.getColumns(catalog, schema, tableName, "%")) {
            while (columnResultSet.next()) {
                String columnName = columnResultSet.getString(Constants.SCHEMA_FIELD_COLUMN_NAME);
                columns.add(ColumnSchemaDTO.builder()
                        .columnName(columnName)
                        .dataType(columnResultSet.getString(Constants.SCHEMA_FIELD_TYPE_NAME))
                        .columnType(columnResultSet.getString(Constants.SCHEMA_FIELD_COLUMN_SIZE) != null ? 
                                columnResultSet.getString(Constants.SCHEMA_FIELD_TYPE_NAME) + "(" + columnResultSet.getString(Constants.SCHEMA_FIELD_COLUMN_SIZE) + ")" :
                                columnResultSet.getString(Constants.SCHEMA_FIELD_TYPE_NAME))
                        .isNullable(Constants.SCHEMA_NULLABLE_YES.equals(columnResultSet.getString(Constants.SCHEMA_FIELD_IS_NULLABLE)))
                        .columnDefault(columnResultSet.getString(Constants.SCHEMA_FIELD_COLUMN_DEF))
                        .columnComment(getColumnComment(columnResultSet, databaseType))
                        .columnKey(getColumnKey(columnResultSet, databaseType, connection))
                        .extra(getExtraInfo(columnResultSet, databaseType))
                        .ordinalPosition(columnResultSet.getInt(Constants.SCHEMA_FIELD_ORDINAL_POSITION))
                        .build());
               
            }
        }
        
        return columns;
    }

    /**
     * Get tables with explicit permissions (more efficient for PostgreSQL)
     */
    private List<String> getTablesWithExplicitPermissions(Connection connection, DatabaseType databaseType, String schema) {
        List<String> accessibleTables = new ArrayList<>();
        
        try {
            switch (databaseType) {
                case MYSQL, POSTGRESQL, SQLSERVER:
                    accessibleTables = getTablesWithExplicitPermissionsFromInformationSchema(connection, schema);
                    break;
                case ORACLE:
                    accessibleTables = getTablesWithExplicitPermissionsFromOracle(connection);
                    break;
                default:
                    log.debug("Unsupported database type for explicit permissions: {}", databaseType);
                    break;
            }
        } catch (SQLException e) {
            log.debug("Error getting tables with explicit permissions: {}", e.getMessage());
        }
        
        return accessibleTables;
    }
    
    /**
     * Get tables with explicit permissions from information_schema (MySQL, PostgreSQL, SQL Server)
     */
    private List<String> getTablesWithExplicitPermissionsFromInformationSchema(Connection connection, String schema) throws SQLException {
        List<String> tables = new ArrayList<>();
        
        String query = """
            SELECT DISTINCT table_name
            FROM information_schema.table_privileges 
            WHERE table_schema = ? 
                AND grantee = CURRENT_USER
                AND privilege_type IN ('SELECT', 'INSERT', 'UPDATE', 'DELETE')
        """;
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, schema);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tables.add(rs.getString(Constants.SCHEMA_FIELD_TABLE_NAME));
                }
            }
        }
        
        return tables;
    }
    
    /**
     * Get tables with explicit permissions from Oracle
     */
    private List<String> getTablesWithExplicitPermissionsFromOracle(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<>();
        
        String query = """
            SELECT DISTINCT table_name
            FROM user_tab_privs 
            WHERE privilege IN ('SELECT', 'INSERT', 'UPDATE', 'DELETE', 'ALL')
        """;
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tables.add(rs.getString(Constants.SCHEMA_FIELD_TABLE_NAME));
                }
            }
        }
        
        return tables;
    }
    
    /**
     * Get table type for a specific table
     */
    private String getTableType(DatabaseMetaData metaData, String catalog, String schema, String tableName) throws SQLException {
        try (ResultSet tableResultSet = metaData.getTables(catalog, schema, tableName, new String[]{Constants.SCHEMA_TYPE_TABLE, Constants.SCHEMA_TYPE_VIEW})) {
            if (tableResultSet.next()) {
                return tableResultSet.getString(Constants.SCHEMA_FIELD_TABLE_TYPE);
            }
        }
        return Constants.SCHEMA_TYPE_TABLE; // default
    }
    
    /**
     * Get table comment for a specific table
     */
    private String getTableComment(DatabaseMetaData metaData, String catalog, String schema, String tableName, DatabaseType databaseType) throws SQLException {
        try (ResultSet tableResultSet = metaData.getTables(catalog, schema, tableName, new String[]{Constants.SCHEMA_TYPE_TABLE, Constants.SCHEMA_TYPE_VIEW})) {
            if (tableResultSet.next()) {
                return getTableComment(tableResultSet, databaseType);
            }
        }
        return ""; // default
    }

    /**
     * Get schema name based on database type
     */
    private String getSchemaName(DatabaseType databaseType, String catalog) {
        switch (databaseType) {
            case MYSQL:
                return catalog; // MySQL uses database name as schema
            case POSTGRESQL:
                return "public"; // PostgreSQL default schema
            case SQLSERVER:
                return "dbo"; // SQL Server default schema
            case ORACLE:
                return catalog.toUpperCase(); // Oracle uses uppercase
            default:
                return null;
        }
    }

    /**
     * Get table comment based on database type
     */
    private String getTableComment(ResultSet tableResultSet, DatabaseType databaseType) {
        try {
            return tableResultSet.getString(Constants.SCHEMA_FIELD_REMARKS);
        } catch (SQLException e) {
            log.debug("Could not fetch table comment for database type: {}", databaseType);
            return null;
        }
    }

    /**
     * Get column comment based on database type
     */
    private String getColumnComment(ResultSet columnResultSet, DatabaseType databaseType) {
        try {
            return columnResultSet.getString(Constants.SCHEMA_FIELD_REMARKS);
        } catch (SQLException e) {
            log.debug("Could not fetch column comment for database type: {}", databaseType);
            return null;
        }
    }

    /**
     * Get column key information (PRIMARY KEY, UNIQUE, etc.)
     */
    private String getColumnKey(ResultSet columnResultSet, DatabaseType databaseType, Connection connection) {
        try {
            switch (databaseType) {
                case MYSQL:
                    return getMySQLColumnKey(columnResultSet);
                case POSTGRESQL:
                    return getPostgreSQLColumnKey(columnResultSet, connection);
                case SQLSERVER:
                    return getSQLServerColumnKey(columnResultSet, connection);
                case ORACLE:
                    return getOracleColumnKey(columnResultSet, connection);
                default:
                    return null;
            }
        } catch (SQLException e) {
            log.debug("Could not fetch column key for database type: {}", databaseType);
            return null;
        }
    }
    
    /**
     * Get MySQL column key information
     */
    private String getMySQLColumnKey(ResultSet columnResultSet) throws SQLException {
        try {
            String columnKey = columnResultSet.getString(Constants.SCHEMA_FIELD_COLUMN_KEY);
            if (columnKey != null && !columnKey.isEmpty()) {
                return columnKey;
            }
        } catch (SQLException e) {
            log.debug("COLUMN_KEY field not available in MySQL metadata: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get PostgreSQL column key information
     */
    private String getPostgreSQLColumnKey(ResultSet columnResultSet, Connection connection) throws SQLException {
        String tableName = columnResultSet.getString(Constants.SCHEMA_FIELD_TABLE_NAME);
        String columnName = columnResultSet.getString(Constants.SCHEMA_FIELD_COLUMN_NAME);
        
        String query = """
            SELECT tc.constraint_type
            FROM information_schema.table_constraints tc
            JOIN information_schema.constraint_column_usage ccu 
                ON tc.constraint_name = ccu.constraint_name 
                AND tc.table_schema = ccu.table_schema
            WHERE tc.table_name = ? 
                AND ccu.column_name = ?
                AND tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE')
            ORDER BY 
                CASE tc.constraint_type 
                    WHEN 'PRIMARY KEY' THEN 1 
                    WHEN 'UNIQUE' THEN 2 
                    ELSE 3 
                END
            LIMIT 1
            """;
        
        return executeColumnKeyQuery(connection, query, tableName, columnName, Constants.DB_TYPE_POSTGRESQL);
    }
    
    /**
     * Get SQL Server column key information
     */
    private String getSQLServerColumnKey(ResultSet columnResultSet, Connection connection) throws SQLException {
        String tableName = columnResultSet.getString(Constants.SCHEMA_FIELD_TABLE_NAME);
        String columnName = columnResultSet.getString(Constants.SCHEMA_FIELD_COLUMN_NAME);
        
        String query = """
            SELECT tc.constraint_type
            FROM information_schema.table_constraints tc
            JOIN information_schema.constraint_column_usage ccu 
                ON tc.constraint_name = ccu.constraint_name 
                AND tc.table_schema = ccu.table_schema
            WHERE tc.table_name = ? 
                AND ccu.column_name = ?
                AND tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE')
            ORDER BY 
                CASE tc.constraint_type 
                    WHEN 'PRIMARY KEY' THEN 1 
                    WHEN 'UNIQUE' THEN 2 
                    ELSE 3 
                END
            """;
        
        return executeColumnKeyQuery(connection, query, tableName, columnName, Constants.DB_TYPE_SQL_SERVER);
    }
    
    /**
     * Get Oracle column key information
     */
    private String getOracleColumnKey(ResultSet columnResultSet, Connection connection) throws SQLException {
        String tableName = columnResultSet.getString(Constants.SCHEMA_FIELD_TABLE_NAME);
        String columnName = columnResultSet.getString(Constants.SCHEMA_FIELD_COLUMN_NAME);
        
        String query = """
            SELECT uc.constraint_type
            FROM user_constraints uc
            JOIN user_cons_columns ucc ON uc.constraint_name = ucc.constraint_name
            WHERE uc.table_name = ? 
                AND ucc.column_name = ?
                AND uc.constraint_type IN ('P', 'U')
            ORDER BY 
                CASE uc.constraint_type 
                    WHEN 'P' THEN 1 
                    WHEN 'U' THEN 2 
                    ELSE 3 
                END
            """;
        
        return executeOracleColumnKeyQuery(connection, query, tableName, columnName);
    }
    
    /**
     * Common method to execute column key queries for information_schema databases
     */
    private String executeColumnKeyQuery(Connection connection, String query, String tableName, String columnName, String databaseType) {
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, tableName);
            stmt.setString(2, columnName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String constraintType = rs.getString("constraint_type");
                    return constraintType.equals("PRIMARY KEY") ? "PRI" : "UNI";
                }
            }
        } catch (SQLException e) {
            log.debug("Could not fetch {} column key: {}", databaseType, e.getMessage());
        }
        return null;
    }
    
    /**
     * Execute Oracle-specific column key query
     */
    private String executeOracleColumnKeyQuery(Connection connection, String query, String tableName, String columnName) {
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, tableName.toUpperCase());
            stmt.setString(2, columnName.toUpperCase());
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String constraintType = rs.getString("constraint_type");
                    return constraintType.equals("P") ? "PRI" : "UNI";
                }
            }
        } catch (SQLException e) {
            log.debug("Could not fetch Oracle column key: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Get extra information (AUTO_INCREMENT, etc.)
     */
    private String getExtraInfo(ResultSet columnResultSet, DatabaseType databaseType) {
        try {
            switch (databaseType) {
                case MYSQL:
                    return columnResultSet.getString(Constants.SCHEMA_FIELD_IS_AUTOINCREMENT);
                case POSTGRESQL, SQLSERVER, ORACLE:
                    return null;
                default:
                    return null;
            }
        } catch (SQLException e) {
            log.debug("Could not fetch extra info for database type: {}", databaseType);
            return null;
        }
    }
}
