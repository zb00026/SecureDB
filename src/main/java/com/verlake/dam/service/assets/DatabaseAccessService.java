package com.verlake.dam.service.assets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.client.*;
import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.assets.AssetObject;
import com.verlake.dam.entity.assets.dto.AssetAccessDTO;
import com.verlake.dam.entity.assets.dto.PermissionDTO;
import com.verlake.dam.entity.assets.dto.PermissionValidationResult;
import com.verlake.dam.entity.assets.dto.UserAccessDTO;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.DatabaseType;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.exception.DatabaseAccessException;
import com.verlake.dam.models.assets.FieldKeys;
import com.verlake.dam.models.assets.LockoutResultData;
import com.verlake.dam.models.assets.OperationMetadata;
import com.verlake.dam.models.assets.UserLists;
import com.verlake.dam.repository.assets.AssetCredentialsRepository;
import com.verlake.dam.repository.assets.AssetObjectRepository;
import com.verlake.dam.service.assets.common.DatabaseConnectionUtils;
import com.verlake.dam.service.assets.fetchers.*;
import com.verlake.dam.service.assets.mongodb.MongoDBConnectionUtils;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DatabaseAccessService {
    private final AssetObjectRepository assetObjectRepository;
    private final AssetCredentialsRepository assetCredentialsRepository;
    private final KeycloakService keycloakService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final UserService userService;
    private final DatabaseConnectionUtils databaseConnectionUtils;
    
    @PersistenceContext
    private EntityManager entityManager;

    public DatabaseAccessService(AssetObjectRepository assetObjectRepository,
            AssetCredentialsRepository assetCredentialsRepository,
            KeycloakService keycloakService, UserService userService,
            DatabaseConnectionUtils databaseConnectionUtils,
            ObjectMapper objectMapper) {
        this.assetObjectRepository = assetObjectRepository;
        this.assetCredentialsRepository = assetCredentialsRepository;
        this.keycloakService = keycloakService;
        this.objectMapper = objectMapper;
        this.userService = userService;
        this.databaseConnectionUtils = databaseConnectionUtils;
    }

    public void updateAssetObjects(AssetCredential credential) throws SQLException {
        // Check if this is an Asset Owner credential
        if (credential.getUserAccessType() != null &&
                credential.getUserAccessType().equals(Roles.ASSET_OWNER.getOriginalName())) {

            // Validate Asset Owner permissions before proceeding
            PermissionValidationResult validationResult = validateAssetOwnerPermissions(credential);

            if (!validationResult.isSufficient()) {
                log.warn("Asset Owner {} has insufficient permissions for asset {}: {}",
                        credential.getUsername(), credential.getAsset().getId(), validationResult.getWarningMessage());

                // Store the warning in the asset object for UI display
                String objectsJsonWithWarning = createObjectsJsonWithWarning(validationResult, 
                        credential.getAsset().getDatabaseType());
                saveAssetObjectWithWarning(credential, objectsJsonWithWarning, validationResult);
                return;
            }
        }

        String objectsJson = fetchDatabaseObjects(credential);

        // Find AssetObject using credential ID to avoid issues with temp credentials
        // If credential is a temp credential (not managed), use its ID if available
        // Otherwise, find by the credential reference
        AssetObject assetObject;
        if (credential.getId() != null) {
            assetObject = assetObjectRepository.findByAssetCredential_Id(credential.getId())
                    .orElse(new AssetObject());
        } else {
            assetObject = assetObjectRepository.findByAssetCredential(credential)
                    .orElse(new AssetObject());
        }

        // Set the credential reference - use original credential if this is a temp credential
        // For temp credentials, we need to reload the original from database
        AssetCredential credentialToSave = credential;
        if (credential.getId() != null && !entityManager.contains(credential)) {
            // This is a temp credential, reload the original managed credential
            credentialToSave = assetCredentialsRepository.findById(credential.getId())
                    .orElse(credential);
        }
        
        assetObject.setAssetCredential(credentialToSave);
        assetObject.setAsset(credentialToSave.getAsset());
        assetObject.setObjectsJson(objectsJson);

        assetObjectRepository.save(assetObject);
        
        // Detach credential from persistence context to prevent saving decrypted password
        // if credential was modified (e.g., password decrypted) during this operation
        // Only detach if it's managed
        if (entityManager.contains(credential)) {
            entityManager.detach(credential);
        }
    }

    private String fetchDatabaseObjects(AssetCredential credential) throws SQLException {
        ObjectNode rootNode = objectMapper.createObjectNode();

        switch (credential.getAsset().getDatabaseType()) {
            case MYSQL:
                return fetchMySQLObjects(credential, rootNode);
            case POSTGRESQL:
                return fetchPostgreSQLObjects(credential, rootNode);
            case SQLSERVER:
                return fetchSQLServerObjects(credential, rootNode);
            case ORACLE:
                return fetchOracleObjects(credential, rootNode);
            case MONGODB:
                return fetchMongoDBObjects(credential, rootNode);
            default:
                throw new DatabaseAccessException(
                        Constants.getMessage("error.database.type.not.supported")
                                + credential.getAsset().getDatabaseType(),
                        null);
        }
    }

    private String fetchMySQLObjects(AssetCredential credential, ObjectNode rootNode) throws SQLException {
        String jdbcUrl = databaseConnectionUtils.buildJdbcUrl(credential.getAsset());

        Connection connection = DriverManager.getConnection(jdbcUrl, credential.getUsername(),
                credential.getPassword());
        // Initialize categories and arrays
        initializeCategories(rootNode);

        // Fetch and categorize grants
        fetchAndCategorizeGrants(connection, rootNode);

        // Fetch databases and their objects
        fetchDatabasesAndObjects(connection, rootNode);

        // Add information_schema objects
        fetchInformationSchemaObjects(connection, rootNode);

        return rootNode.toString();
    }

    private void initializeCategories(ObjectNode rootNode) {
        // Create category objects
        ObjectNode databaseCategory = rootNode.putObject(Constants.ASSET_ACCESS_OBJECT_DATABASE);
        ObjectNode tableCategory = rootNode.putObject(Constants.ASSET_ACCESS_OBJECT_TABLE);
        ObjectNode viewCategory = rootNode.putObject(Constants.ASSET_ACCESS_OBJECT_VIEW);
        ObjectNode procedureCategory = rootNode.putObject(Constants.ASSET_ACCESS_OBJECT_PROCEDURE);

        // Add grants arrays to each category
        databaseCategory.putArray(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        tableCategory.putArray(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        viewCategory.putArray(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        procedureCategory.putArray(Constants.ACCESS_OBJECT_ATTR_GRANTS);

        // Add data arrays
        databaseCategory.putArray(Constants.ACCESS_OBJECT_ATTR_DATA);
        tableCategory.putArray(Constants.ACCESS_OBJECT_ATTR_DATA);
        viewCategory.putArray(Constants.ACCESS_OBJECT_ATTR_DATA);
        procedureCategory.putArray(Constants.ACCESS_OBJECT_ATTR_DATA);
    }

    private void fetchAndCategorizeGrants(Connection connection, ObjectNode rootNode) {
        ArrayNode databaseGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_DATABASE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        ArrayNode tableGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_TABLE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        ArrayNode viewGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_VIEW)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        ArrayNode procedureGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_PROCEDURE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);

        try (Statement stmt = connection.createStatement();
                ResultSet grantRs = stmt.executeQuery(Constants.MYSQL_QUERY_SHOW_GRANTS)) {
            while (grantRs.next()) {
                String grantStr = grantRs.getString(1);
                categorizeGrant(grantStr, databaseGrants, tableGrants, viewGrants, procedureGrants);
            }
        } catch (SQLException e) {
            log.error(Constants.LOG_ERROR_FETCHING_GRANTS, e);
        }
    }

    private void fetchDatabasesAndObjects(Connection connection, ObjectNode rootNode) {
        ArrayNode databaseData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_DATABASE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode tableData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_TABLE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode viewData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_VIEW)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode procedureData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_PROCEDURE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);

        try (ResultSet catalogRs = connection.getMetaData().getCatalogs()) {
            while (catalogRs.next()) {
                String dbName = catalogRs.getString(Constants.DB_COLUMN_TABLE_CAT);

                // Skip system databases if needed
                if (isSystemDatabase(dbName)) {
                    continue;
                }

                databaseData.add(dbName);
                fetchDatabaseObjects(connection, dbName, tableData, viewData, procedureData);
            }
        } catch (SQLException e) {
            log.error(Constants.LOG_ERROR_FETCHING_DATABASES, e);
        }
    }

    private boolean isSystemDatabase(String dbName) {
        return dbName.equals(Constants.MYSQL_SYSTEM_DB_MYSQL)
                || dbName.equals(Constants.MYSQL_SYSTEM_DB_PERFORMANCE_SCHEMA) ||
                dbName.equals(Constants.MYSQL_SYSTEM_DB_SYS)
                || dbName.equals(Constants.MYSQL_SYSTEM_DB_INFORMATION_SCHEMA);
    }

    private void fetchDatabaseObjects(Connection connection, String dbName,
            ArrayNode tableData, ArrayNode viewData, ArrayNode procedureData) {
        try {
            // Skip USE statement and directly query tables from the database
            fetchTables(connection, dbName, tableData);
            fetchViews(connection, dbName, viewData);
            fetchProcedures(connection, dbName, procedureData);
        } catch (Exception e) {
            log.error(Constants.LOG_ERROR_FETCHING_OBJECTS_FOR_DB + dbName, e);
        }
    }

    private void fetchTables(Connection connection, String dbName, ArrayNode tableData) throws SQLException {
        // Validate database name to prevent SQL injection using centralized, ReDoS-safe
        // validator
        if (!CommonUtils.isValidSqlIdentifier(dbName)) {
            log.error("Invalid database name: {}", dbName);
            return;
        }

        // Use INFORMATION_SCHEMA.TABLES instead of SHOW TABLES FROM
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ?")) {
            stmt.setString(1, dbName);
            try (ResultSet tableRs = stmt.executeQuery()) {
                while (tableRs.next()) {
                    String tableName = tableRs.getString(Constants.INFORMATION_SCHEMA_TABLE_NAME);
                    tableData.add(dbName + "." + tableName);
                }
            }
        }
    }

    private void fetchViews(Connection connection, String dbName, ArrayNode viewData) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_SCHEMA = ?")) {
            stmt.setString(1, dbName);
            try (ResultSet viewRs = stmt.executeQuery()) {
                while (viewRs.next()) {
                    String viewName = viewRs.getString(Constants.INFORMATION_SCHEMA_TABLE_NAME);
                    viewData.add(dbName + "." + viewName);
                }
            }
        } catch (Exception e) {
            log.error(Constants.LOG_ERROR_FETCHING_VIEWS_FOR_DB + dbName, e);
        }
    }

    private void fetchProcedures(Connection connection, String dbName, ArrayNode procedureData) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT ROUTINE_NAME FROM INFORMATION_SCHEMA.ROUTINES WHERE ROUTINE_SCHEMA = ?")) {
            stmt.setString(1, dbName);
            try (ResultSet procRs = stmt.executeQuery()) {
                while (procRs.next()) {
                    String procName = procRs.getString(Constants.DB_COLUMN_ROUTINE_NAME);
                    procedureData.add(dbName + "." + procName);
                }
            }
        } catch (Exception e) {
            log.error(Constants.LOG_ERROR_FETCHING_PROCEDURES_FOR_DB + dbName, e);
        }
    }

    private void fetchInformationSchemaObjects(Connection connection, ObjectNode rootNode) {
        ArrayNode tableData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_TABLE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);

        try (Statement stmt = connection.createStatement();
                ResultSet infoSchemaRs = stmt.executeQuery(
                        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                                "WHERE TABLE_SCHEMA = 'information_schema' AND TABLE_TYPE = 'BASE TABLE'")) {
            while (infoSchemaRs.next()) {
                String tableName = infoSchemaRs.getString(Constants.INFORMATION_SCHEMA_TABLE_NAME);
                tableData.add(Constants.INFORMATION_SCHEMA_PREFIX + tableName);
            }
        } catch (Exception e) {
            log.error(Constants.LOG_ERROR_FETCHING_INFO_SCHEMA, e);
        }
    }

    private void categorizeGrant(String grantStr, ArrayNode databaseGrants, ArrayNode tableGrants,
            ArrayNode viewGrants, ArrayNode procedureGrants) {
        ObjectNode grantNode = objectMapper.createObjectNode();
        grantStr = grantStr.toUpperCase();

        if (grantStr.contains("ON *.*")) {
            handleGlobalGrant(grantStr, grantNode, databaseGrants, tableGrants, viewGrants, procedureGrants);
        } else if (grantStr.contains("ON")) {
            handleSpecificGrant(grantStr, grantNode, tableGrants, viewGrants, procedureGrants);
        }
    }

    private void handleGlobalGrant(String grantStr, ObjectNode grantNode,
            ArrayNode databaseGrants, ArrayNode tableGrants,
            ArrayNode viewGrants, ArrayNode procedureGrants) {
        // Convert MySQL grant to template format
        String templateGrant = convertMySQLGrantToTemplate(grantStr);
        grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, templateGrant);
        databaseGrants.add(grantNode);

        if (grantStr.contains(Constants.ACCESS_LEVEL_TEMPLATE_CREATE_VIEW)
                || grantStr.contains(Constants.ACCESS_LEVEL_TEMPLATE_FULL)) {
            addViewGrantFromGlobal(viewGrants);
        }

        if (grantStr.contains(Constants.ACCESS_LEVEL_TEMPLATE_CREATE_ROUTINE)
                || grantStr.contains(Constants.ACCESS_LEVEL_TEMPLATE_ALTER_ROUTINE)) {
            addProcedureGrantFromGlobal(procedureGrants);
        }

        if (hasTablePrivileges(grantStr)) {
            addTableGrantFromGlobal(tableGrants);
        }
    }

    private void addViewGrantFromGlobal(ArrayNode viewGrants) {
        ObjectNode viewGrantNode = objectMapper.createObjectNode();
        viewGrantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "GRANT CREATE VIEW ON $DATABASE.* TO $USER");
        viewGrants.add(viewGrantNode);
    }

    private void addProcedureGrantFromGlobal(ArrayNode procedureGrants) {
        ObjectNode procGrantNode = objectMapper.createObjectNode();
        procGrantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "GRANT CREATE ROUTINE ON $DATABASE.* TO $USER");
        procedureGrants.add(procGrantNode);
    }

    private boolean hasTablePrivileges(String grantStr) {
        return grantStr.contains(Constants.MYSQL_QUERY_SELECT) || grantStr.contains(Constants.MYSQL_QUERY_INSERT) ||
                grantStr.contains(Constants.MYSQL_QUERY_UPDATE) || grantStr.contains(Constants.MYSQL_QUERY_DELETE) ||
                grantStr.contains(Constants.MYSQL_QUERY_CREATE) || grantStr.contains(Constants.MYSQL_QUERY_ALTER);
    }

    private void addTableGrantFromGlobal(ArrayNode tableGrants) {
        ObjectNode tableGrantNode = objectMapper.createObjectNode();
        tableGrantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                "GRANT SELECT, INSERT, UPDATE, DELETE ON $DATABASE.* TO $USER");
        tableGrants.add(tableGrantNode);
    }

    private void handleSpecificGrant(String grantStr, ObjectNode grantNode,
            ArrayNode tableGrants, ArrayNode viewGrants,
            ArrayNode procedureGrants) {
        // Convert MySQL grant to template format
        String templateGrant = convertMySQLGrantToTemplate(grantStr);
        grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, templateGrant);

        if (isViewGrant(grantStr)) {
            viewGrants.add(grantNode);
        } else if (isProcedureGrant(grantStr)) {
            procedureGrants.add(grantNode);
        } else {
            tableGrants.add(grantNode);
        }
    }

    private String convertMySQLGrantToTemplate(String grantStr) {
        // Convert MySQL GRANT statement to use placeholders
        // Examples:
        // "GRANT SELECT ON `database`.`table` TO 'user'@'host'" -> "GRANT SELECT ON
        // $DATABASE.table TO $USER"
        // "GRANT ALL PRIVILEGES ON *.* TO 'user'@'host'" -> "GRANT ALL PRIVILEGES ON
        // *.* TO $USER"

        String template = grantStr;

        // Replace username with $USER placeholder
        // Pattern: TO 'username'@'host' or TO `username`@`host`
        // Use safer regex without nested quantifiers to prevent ReDoS
        template = template.replaceAll("TO\\s+['`\"]([^'`\"@]+)['`\"]@['`\"]([^'`\"]+)['`\"]", "TO \\$USER");

        // Replace database name with $DATABASE placeholder
        // Pattern: ON `database`.`table` or ON database.table
        // Use safer regex without nested quantifiers to prevent ReDoS
        template = template.replaceAll("ON\\s+['`\"]([^'`\".]+)['`\"]\\.", "ON \\$DATABASE.");
        template = template.replaceAll("ON\\s+([^.\\s]+)\\.", "ON \\$DATABASE.");

        // For global grants (ON *.*)
        template = template.replaceAll("ON\\s+\\*\\.\\*", "ON \\$DATABASE.*");

        return template;
    }

    private boolean isViewGrant(String grantStr) {
        return grantStr.contains(Constants.ACCESS_LEVEL_TEMPLATE_CREATE_VIEW)
                || grantStr.contains(Constants.ACCESS_LEVEL_TEMPLATE_FULL);
    }

    private boolean isProcedureGrant(String grantStr) {
        return grantStr.contains(Constants.SQL_KEYWORD_ROUTINE) || grantStr.contains(Constants.SQL_KEYWORD_PROCEDURE);
    }

    private JdbcTemplate createJdbcTemplate(AssetCredential credential, String driverClassName, String urlPrefix) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverClassName);

        String jdbcUrl = urlPrefix + credential.getAsset().getHostUrl();
        // Add SSL parameters for MSSQL
        if (urlPrefix.equals(Constants.JDBC_SQLSERVER_URL)) {
            jdbcUrl += Constants.JDBC_SQLSERVER_SSL_PARAMS;
        }

        dataSource.setUrl(jdbcUrl);
        dataSource.setUsername(credential.getUsername());
        dataSource.setPassword(credential.getPassword());
        return new JdbcTemplate(dataSource);
    }

    private String fetchPostgreSQLObjects(AssetCredential credential, ObjectNode rootNode) {
        JdbcTemplate jdbcTemplate = createJdbcTemplate(
                credential,
                Constants.DRIVER_POSTGRESQL,
                "jdbc:postgresql://");

        // Initialize categories with proper structure
        initializeCategories(rootNode);

        // Get grants and data arrays
        ArrayNode databaseGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_DATABASE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        ArrayNode tableGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_TABLE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);

        ArrayNode databaseData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_DATABASE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode tableData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_TABLE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode viewData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_VIEW)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode procedureData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_PROCEDURE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);

        // Query for role privileges
        List<Map<String, Object>> privileges = jdbcTemplate.queryForList(
                "SELECT * FROM information_schema.role_table_grants WHERE grantee = current_user");

        for (Map<String, Object> privilege : privileges) {
            String grantType = (String) privilege.get(Constants.POSTGRES_PRIVILEGE_TYPE_COLUMN);
            String tableName = (String) privilege.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);

            ObjectNode grantNode = objectMapper.createObjectNode();
            grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT %s %s.%s TO $USER",
                            grantType, Constants.SQL_TEMPLATE_ON_SCHEMA, tableName));

            if (tableName.startsWith(Constants.POSTGRES_SYSTEM_PREFIX)) {
                databaseGrants.add(grantNode);
            } else {
                tableGrants.add(grantNode);
            }
        }

        // Add database-level grants for PostgreSQL
        ObjectNode connectGrant = objectMapper.createObjectNode();
        connectGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "GRANT CONNECT ON DATABASE $DATABASE TO $USER");
        databaseGrants.add(connectGrant);

        ObjectNode tempGrant = objectMapper.createObjectNode();
        tempGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "GRANT TEMP ON DATABASE $DATABASE TO $USER");
        databaseGrants.add(tempGrant);

        ObjectNode createGrant = objectMapper.createObjectNode();
        createGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "GRANT CREATE ON DATABASE $DATABASE TO $USER");
        databaseGrants.add(createGrant);

        // Add schema-level grants
        ObjectNode schemaUsageGrant = objectMapper.createObjectNode();
        schemaUsageGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "GRANT USAGE ON SCHEMA $SCHEMA TO $USER");
        databaseGrants.add(schemaUsageGrant);

        ObjectNode schemaCreateGrant = objectMapper.createObjectNode();
        schemaCreateGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "GRANT CREATE ON SCHEMA $SCHEMA TO $USER");
        databaseGrants.add(schemaCreateGrant);

        // Fetch schema/database names
        List<Map<String, Object>> schemas = jdbcTemplate.queryForList(
                "SELECT schema_name FROM information_schema.schemata WHERE schema_name NOT IN ('information_schema', 'pg_catalog', 'pg_toast')");
        for (Map<String, Object> schema : schemas) {
            String schemaName = (String) schema.get(Constants.DB_COLUMN_SCHEMA_NAME);
            databaseData.add(schemaName);
        }

        // Fetch tables
        List<Map<String, Object>> tables = jdbcTemplate.queryForList(
                "SELECT table_schema, table_name FROM information_schema.tables WHERE table_schema NOT IN ('information_schema', 'pg_catalog', 'pg_toast')");
        for (Map<String, Object> table : tables) {
            String schemaName = (String) table.get(Constants.DB_COLUMN_TABLE_SCHEMA);
            String tableName = (String) table.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);
            tableData.add(schemaName + "." + tableName);
        }

        // Fetch views
        List<Map<String, Object>> views = jdbcTemplate.queryForList(
                "SELECT table_schema, table_name FROM information_schema.views WHERE table_schema NOT IN ('information_schema', 'pg_catalog', 'pg_toast')");
        for (Map<String, Object> view : views) {
            String schemaName = (String) view.get(Constants.DB_COLUMN_TABLE_SCHEMA);
            String viewName = (String) view.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);
            viewData.add(schemaName + "." + viewName);
        }

        // Fetch procedures/functions
        List<Map<String, Object>> routines = jdbcTemplate.queryForList(
                "SELECT routine_schema, routine_name FROM information_schema.routines WHERE routine_schema NOT IN ('information_schema', 'pg_catalog', 'pg_toast')");
        for (Map<String, Object> routine : routines) {
            String schemaName = (String) routine.get("routine_schema");
            String routineName = (String) routine.get("routine_name");
            procedureData.add(schemaName + "." + routineName);
        }

        return rootNode.toString();
    }

    private String fetchSQLServerObjects(AssetCredential credential, ObjectNode rootNode) {
        JdbcTemplate jdbcTemplate = createJdbcTemplate(
                credential,
                Constants.DRIVER_SQLSERVER,
                Constants.JDBC_SQLSERVER_URL);

        // Initialize categories with proper structure
        initializeCategories(rootNode);

        // Get grants and data arrays
        ArrayNode databaseGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_DATABASE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        ArrayNode tableGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_TABLE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        ArrayNode viewGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_VIEW)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        ArrayNode procedureGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_PROCEDURE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);

        ArrayNode databaseData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_DATABASE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode tableData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_TABLE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode viewData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_VIEW)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode procedureData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_PROCEDURE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);

        // Instead of querying existing permissions, we'll create templates based on
        // available objects
        // This approach works regardless of the user's permission to view system tables

        // For tables - create permission templates for each table
        List<Map<String, Object>> tables = jdbcTemplate.queryForList(
                "SELECT SCHEMA_NAME(schema_id) as schema_name, name as table_name FROM sys.tables");

        for (Map<String, Object> table : tables) {
            String schemaName = (String) table.get(Constants.DB_COLUMN_SCHEMA_NAME);
            String tableName = (String) table.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);

            // Add SELECT permission template
            ObjectNode selectGrant = objectMapper.createObjectNode();
            selectGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT SELECT ON [%s].[%s] TO [$USER]", schemaName, tableName));
            tableGrants.add(selectGrant);

            // Add INSERT permission template
            ObjectNode insertGrant = objectMapper.createObjectNode();
            insertGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT INSERT ON [%s].[%s] TO [$USER]", schemaName, tableName));
            tableGrants.add(insertGrant);

            // Add UPDATE permission template
            ObjectNode updateGrant = objectMapper.createObjectNode();
            updateGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT UPDATE ON [%s].[%s] TO [$USER]", schemaName, tableName));
            tableGrants.add(updateGrant);

            // Add DELETE permission template
            ObjectNode deleteGrant = objectMapper.createObjectNode();
            deleteGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT DELETE ON [%s].[%s] TO [$USER]", schemaName, tableName));
            tableGrants.add(deleteGrant);
        }

        // For views - create permission templates for each view
        List<Map<String, Object>> views = jdbcTemplate.queryForList(
                "SELECT SCHEMA_NAME(schema_id) as schema_name, name as view_name FROM sys.views");

        for (Map<String, Object> view : views) {
            String schemaName = (String) view.get(Constants.DB_COLUMN_SCHEMA_NAME);
            String viewName = (String) view.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);

            ObjectNode selectGrant = objectMapper.createObjectNode();
            selectGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT SELECT ON [%s].[%s] TO [$USER]", schemaName, viewName));
            viewGrants.add(selectGrant);
        }

        // For procedures - create permission templates for each procedure
        List<Map<String, Object>> procedures = jdbcTemplate.queryForList(
                "SELECT SCHEMA_NAME(schema_id) as schema_name, name as procedure_name FROM sys.procedures");

        for (Map<String, Object> procedure : procedures) {
            String schemaName = (String) procedure.get(Constants.DB_COLUMN_SCHEMA_NAME);
            String procedureName = (String) procedure.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);

            ObjectNode executeGrant = objectMapper.createObjectNode();
            executeGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT EXECUTE ON [%s].[%s] TO [$USER]", schemaName, procedureName));
            procedureGrants.add(executeGrant);
        }

        // Add database-level role memberships (matching the predefined templates in the
        // database)
        ObjectNode fullAccessRole = objectMapper.createObjectNode();
        fullAccessRole.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "ALTER ROLE [db_owner] ADD MEMBER [$USER]");
        databaseGrants.add(fullAccessRole);

        ObjectNode readAccessRole = objectMapper.createObjectNode();
        readAccessRole.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                "ALTER ROLE [db_datareader] ADD MEMBER [$USER]");
        databaseGrants.add(readAccessRole);

        ObjectNode writeAccessRole = objectMapper.createObjectNode();
        writeAccessRole.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                "ALTER ROLE [db_datawriter] ADD MEMBER [$USER]");
        databaseGrants.add(writeAccessRole);

        // Fetch user-defined schemas (excluding system schemas)
        List<Map<String, Object>> schemas = jdbcTemplate.queryForList(
                "SELECT name FROM sys.schemas WHERE name NOT IN ('dbo', 'guest', 'INFORMATION_SCHEMA', 'sys', " +
                        "'db_owner', 'db_accessadmin', 'db_securityadmin', 'db_ddladmin', 'db_backupoperator', " +
                        "'db_datareader', 'db_datawriter', 'db_denydatareader', 'db_denydatawriter')");

        // Always include 'dbo' as it's the default schema users work with
        databaseData.add(Constants.SQLSERVER_SCHEMA_DBO);

        // Add any custom schemas
        for (Map<String, Object> schema : schemas) {
            String schemaName = (String) schema.get("name");
            databaseData.add(schemaName);
        }

        // Populate data arrays with object names (for UI display)
        for (Map<String, Object> table : tables) {
            String schemaName = (String) table.get(Constants.DB_COLUMN_SCHEMA_NAME);
            String tableName = (String) table.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);
            tableData.add(schemaName + "." + tableName);
        }

        for (Map<String, Object> view : views) {
            String schemaName = (String) view.get(Constants.DB_COLUMN_SCHEMA_NAME);
            String viewName = (String) view.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);
            viewData.add(schemaName + "." + viewName);
        }

        for (Map<String, Object> procedure : procedures) {
            String schemaName = (String) procedure.get(Constants.DB_COLUMN_SCHEMA_NAME);
            String procedureName = (String) procedure.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);
            procedureData.add(schemaName + "." + procedureName);
        }

        return rootNode.toString();
    }

    private String fetchOracleObjects(AssetCredential credential, ObjectNode rootNode) {
        JdbcTemplate jdbcTemplate = createJdbcTemplate(
                credential,
                Constants.DRIVER_ORACLE,
                Constants.JDBC_ORACLE_THIN_PREFIX);

        // Initialize categories with proper structure
        initializeCategories(rootNode);

        // Get grants and data arrays
        ArrayNode databaseGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_DATABASE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        ArrayNode tableGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_TABLE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        ArrayNode viewGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_VIEW)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        ArrayNode procedureGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_PROCEDURE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);

        ArrayNode databaseData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_DATABASE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode tableData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_TABLE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode viewData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_VIEW)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode procedureData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_PROCEDURE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);

        // Query for system privileges
        List<Map<String, Object>> sysPrivs = jdbcTemplate.queryForList(
                "SELECT * FROM USER_SYS_PRIVS");

        // Query for object privileges
        List<Map<String, Object>> objPrivs = jdbcTemplate.queryForList(
                "SELECT * FROM USER_TAB_PRIVS WHERE GRANTEE = USER");

        // Handle system privileges
        for (Map<String, Object> priv : sysPrivs) {
            String privilege = (String) priv.get(Constants.DB_COLUMN_PRIVILEGE);

            ObjectNode grantNode = objectMapper.createObjectNode();
            grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT %s TO $USER", privilege));
            databaseGrants.add(grantNode);
        }

        // Handle object privileges
        for (Map<String, Object> priv : objPrivs) {
            String objectType = (String) priv.get(Constants.DB_COLUMN_TYPE);
            String privilege = (String) priv.get(Constants.DB_COLUMN_PRIVILEGE);
            String objectName = (String) priv.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);

            ObjectNode grantNode = objectMapper.createObjectNode();
            grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT %s %s.%s TO $USER",
                            privilege, Constants.SQL_TEMPLATE_ON_SCHEMA, objectName));

            if (objectType.equals(Constants.SQL_KEYWORD_TABLE)) {
                tableGrants.add(grantNode);
            } else if (objectType.equals(Constants.SQL_KEYWORD_VIEW)) {
                viewGrants.add(grantNode);
            } else if (objectType.equals(Constants.SQL_KEYWORD_PROCEDURE)) {
                procedureGrants.add(grantNode);
            } else {
                // Default to table grants for unknown object types
                tableGrants.add(grantNode);
            }
        }

        // Add database-level grants for Oracle
        ObjectNode connectGrant = objectMapper.createObjectNode();
        connectGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "GRANT CONNECT TO $USER");
        databaseGrants.add(connectGrant);

        ObjectNode resourceGrant = objectMapper.createObjectNode();
        resourceGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "GRANT RESOURCE TO $USER");
        databaseGrants.add(resourceGrant);

        // Fetch tablespaces (databases in Oracle)
        List<Map<String, Object>> tablespaces = jdbcTemplate.queryForList(
                "SELECT TABLESPACE_NAME FROM USER_TABLESPACES");
        for (Map<String, Object> tablespace : tablespaces) {
            String tablespaceName = (String) tablespace.get(Constants.DB_COLUMN_TABLESPACE_NAME);
            databaseData.add(tablespaceName);
        }

        // Fetch tables
        List<Map<String, Object>> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME, OWNER FROM USER_TABLES");
        for (Map<String, Object> table : tables) {
            String tableName = (String) table.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);
            String owner = (String) table.get(Constants.DB_COLUMN_OWNER);
            tableData.add(owner + "." + tableName);
        }

        // Fetch views
        List<Map<String, Object>> views = jdbcTemplate.queryForList(
                "SELECT VIEW_NAME, OWNER FROM USER_VIEWS");
        for (Map<String, Object> view : views) {
            String viewName = (String) view.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);
            String owner = (String) view.get(Constants.DB_COLUMN_OWNER);
            viewData.add(owner + "." + viewName);
        }

        // Fetch procedures
        List<Map<String, Object>> procedures = jdbcTemplate.queryForList(
                "SELECT OBJECT_NAME, OWNER FROM USER_OBJECTS WHERE OBJECT_TYPE = 'PROCEDURE'");
        for (Map<String, Object> procedure : procedures) {
            String procedureName = (String) procedure.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);
            String owner = (String) procedure.get(Constants.DB_COLUMN_OWNER);
            procedureData.add(owner + "." + procedureName);
        }

        return rootNode.toString();
    }

    private String fetchMongoDBObjects(AssetCredential credential, ObjectNode rootNode) {
        // Initialize categories with proper structure
        initializeCategories(rootNode);

        // Get grants and data arrays
        ArrayNode databaseGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_DATABASE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        ArrayNode tableGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_TABLE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);

        ArrayNode databaseData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_DATABASE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode tableData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_TABLE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);

        try {
            // Get MongoDB database using utility
            MongoDatabase mongoDb = MongoDBConnectionUtils
                    .getMongoDatabase(credential);

            // MongoDB permission templates
            ObjectNode readGrant = objectMapper.createObjectNode();
            readGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, 
                    "db.grantRolesToUser(\"$USER\", [{role: \"read\", db: \"$DATABASE\"}])");
            tableGrants.add(readGrant);

            ObjectNode readWriteGrant = objectMapper.createObjectNode();
            readWriteGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, 
                    "db.grantRolesToUser(\"$USER\", [{role: \"readWrite\", db: \"$DATABASE\"}])");
            tableGrants.add(readWriteGrant);

            ObjectNode dbAdminGrant = objectMapper.createObjectNode();
            dbAdminGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, 
                    "db.grantRolesToUser(\"$USER\", [{role: \"dbAdmin\", db: \"$DATABASE\"}])");
            databaseGrants.add(dbAdminGrant);

            // Get current database name
            String currentDbName = mongoDb.getName();
            if (currentDbName != null && !currentDbName.isEmpty()) {
                databaseData.add(currentDbName);
            }

            // List all collections (similar to tables)
            List<String> collections = MongoDBConnectionUtils
                    .listCollections(mongoDb);
            for (String collectionName : collections) {
                tableData.add(currentDbName + "." + collectionName);
            }

            // Get MongoDB client to list all databases
            MongoClient mongoClient = MongoDBConnectionUtils
                    .createMongoClient(credential);
            try {
                MongoIterable<String> databaseNames = mongoClient.listDatabaseNames();
                Set<String> existingDbs = new HashSet<>();
                // Collect existing database names
                for (int i = 0; i < databaseData.size(); i++) {
                    existingDbs.add(databaseData.get(i).asText());
                }
                for (String dbName : databaseNames) {
                    // Skip system databases
                    if (!dbName.equals(Constants.MONGODB_ADMIN_DATABASE) && !dbName.equals("local") && !dbName.equals("config")
                        && !existingDbs.contains(dbName)) {
                            databaseData.add(dbName);
                            existingDbs.add(dbName);
                    }
                }
            } finally {
                mongoClient.close();
            }

        } catch (Exception e) {
            log.error("Error fetching MongoDB objects: {}", e.getMessage(), e);
            throw new DatabaseAccessException("Failed to fetch MongoDB objects: " + e.getMessage(), e);
        }

        return rootNode.toString();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateAccessRequestCredentialPassword(AssetCredential devCredential, String newPassword)
            throws CommonUtils.CryptoException {
        String userKey = keycloakService.getUserKey();
        if (!devCredential.getIsTemporaryPassword()) {
            String decPsd = databaseConnectionUtils.decryptCredentialPassword(devCredential);
            devCredential.setPassword(decPsd);
        }
        try (Connection connection = databaseConnectionUtils.getConnectionFromAssetCredential(devCredential)) {
            String alterUserSql;
            PreparedStatement statement;

            switch (devCredential.getAsset().getDatabaseType()) {
                case MYSQL:
                    alterUserSql = "ALTER USER ?@'%' IDENTIFIED BY ?";
                    statement = connection.prepareStatement(alterUserSql);
                    statement.setString(1, devCredential.getUsername());
                    statement.setString(2, newPassword);
                    statement.execute();
                    break;

                case POSTGRESQL:
                    // PostgreSQL doesn't support parameter binding for usernames in DDL
                    String username = devCredential.getUsername().replace("\"", "\"\"");
                    String password = newPassword.replace("'", "''");
                    alterUserSql = String.format("ALTER USER \"%s\" WITH PASSWORD '%s'", username, password);
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute(alterUserSql);
                    }
                    break;

                case MONGODB:
                    // MongoDB uses different API - use MongoDB utility
                    MongoDatabase mongoDb = MongoDBConnectionUtils
                            .getMongoDatabase(devCredential);
                    MongoDBConnectionUtils
                            .updateUserPassword(mongoDb, devCredential.getUsername(), newPassword);
                    break;

                case ORACLE:
                    alterUserSql = Constants.ALTER_USER_IDENTIFIED_BY;
                    statement = connection.prepareStatement(alterUserSql);
                    statement.setString(1, devCredential.getUsername());
                    statement.setString(2, newPassword);
                    statement.execute();
                    break;

                case SQLSERVER:
                    // SQL Server doesn't support parameter binding for identifiers in DDL.
                    // Validate and escape the identifier; bind secret values via PreparedStatement.
                    if (!CommonUtils.isValidUsername(devCredential.getUsername())) {
                        throw new IllegalArgumentException("Invalid username: " + devCredential.getUsername());
                    }
                    String escapedSqlServerUsername = CommonUtils
                            .escapeSqlServerIdentifier(devCredential.getUsername());
                    String alterLoginSqlTemplate = "ALTER LOGIN [" + escapedSqlServerUsername
                            + "] WITH PASSWORD = ? OLD_PASSWORD = ?"; // NOSONAR java:S2077 - identifier is validated
                                                                      // and safely escaped
                    try (PreparedStatement ps = connection.prepareStatement(alterLoginSqlTemplate)) {
                        ps.setString(1, newPassword);
                        ps.setString(2, devCredential.getPassword());
                        ps.executeUpdate();
                        log.info("Updated SQL Server login password: {}", devCredential.getUsername());
                    }
                    break;

                default:
                    throw new DatabaseAccessException(
                            Constants.getMessage(Constants.ERROR_UNSUPPORTED_DATABASE_TYPE)
                                    + devCredential.getAsset().getDatabaseType(),
                            null);
            }
            devCredential.setPassword(CommonUtils.encrypt(userKey, newPassword));
            devCredential.setIsTemporaryPassword(false);
            assetCredentialsRepository.save(devCredential);
        } catch (SQLException e) {
            log.error(Constants.getMessage(Constants.LOG_ERROR_UPDATING_PASSWORD_ID), e);
            throw new DatabaseAccessException(e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAccessRequestorInAsset(AssetCredential credential, User requestor, AccessRequest accessRequest,
            String existUsername, Map<String, String> newCredMapper) {

        // Handle MongoDB separately since it doesn't use JDBC Connection
        if (credential.getAsset().getDatabaseType() == DatabaseType.MONGODB) {
            checkMongoDBAccessRequestorInAsset(credential, requestor, accessRequest, existUsername, newCredMapper);
            return;
        }

        try (Connection connection = databaseConnectionUtils.getConnectionFromAssetCredential(credential)) {
            // Extract username from email (everything before @)
            String username = getCredentialForAccess(connection, credential, requestor, existUsername, newCredMapper);

            // Execute access SQL if provided
            if (shouldExecuteAccessSql(accessRequest)) {
                executeAccessSqlStatements(connection, accessRequest, credential, username);
            }

        } catch (SQLException | CommonUtils.CryptoException e) {
            log.error(Constants.getMessage("error.connecting.to.database"), e);
            throw new DatabaseAccessException(Constants.getMessage("error.connecting.to.database"), e);
        }
    }

    /**
     * Handles MongoDB access request approval (doesn't use JDBC Connection)
     */
    private void checkMongoDBAccessRequestorInAsset(AssetCredential credential, User requestor, AccessRequest accessRequest,
            String existUsername, Map<String, String> newCredMapper) {
        try {
            // Extract username from email (everything before @)
            String username = getCredentialForMongoDBAccess(credential, requestor, existUsername, newCredMapper);

            // Execute access MongoDB commands if provided
            if (shouldExecuteAccessSql(accessRequest)) {
                executeMongoDBAccessCommands(credential, accessRequest, username);
            }

        } catch (CommonUtils.CryptoException e) {
            log.error("Error processing MongoDB access request: {}", e.getMessage(), e);
            throw new DatabaseAccessException("Error processing MongoDB access request: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error connecting to MongoDB: {}", e.getMessage(), e);
            throw new DatabaseAccessException("Error connecting to MongoDB: " + e.getMessage(), e);
        }
    }

    /**
     * Executes MongoDB access commands (like grantRolesToUser) from access SQL
     */
    private void executeMongoDBAccessCommands(AssetCredential credential, AccessRequest accessRequest, String username) {
        try {
            MongoDatabase mongoDb = MongoDBConnectionUtils.getMongoDatabase(credential);
            String databaseName = mongoDb.getName();
            
            // Parse MongoDB commands from access SQL
            String mongoCommands = prepareMongoDBStatements(accessRequest, credential, username);
            String[] commandArray = mongoCommands.split(";");
            
            // Check if we have any grantRolesToUser commands - if so, ensure user exists first
            if (hasGrantOrRevokeCommands(commandArray)) {
                ensureMongoDBUserExists(credential, username, databaseName);
            }
            
            // Execute all commands
            executeMongoDBCommandArray(mongoDb, commandArray, username, credential);
        } catch (Exception e) {
            log.error("Error executing MongoDB access commands: {}", e.getMessage(), e);
            throw new DatabaseAccessException("Error executing MongoDB access commands: " + e.getMessage(), e);
        }
    }
    
    /**
     * Checks if command array contains grant or revoke role commands
     */
    private boolean hasGrantOrRevokeCommands(String[] commandArray) {
        for (String cmd : commandArray) {
            String trimmedCmd = cmd.trim();
            if (!trimmedCmd.isEmpty() && 
                (trimmedCmd.contains(Constants.MONGODB_COMMAND_GRANT_ROLES_TO_USER) || 
                 trimmedCmd.contains(Constants.MONGODB_COMMAND_REVOKE_ROLES_FROM_USER))) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Ensures MongoDB user exists before granting roles, creates user if needed
     */
    private void ensureMongoDBUserExists(AssetCredential credential, String username, String databaseName) {
        boolean userExists = checkMongoDBUserExists(credential, username);
        log.debug("Checking if MongoDB user {} exists in database {}: {}", username, databaseName, userExists);
        
        if (userExists) {
            log.debug("User {} already exists in database {}. Proceeding with role grants.", username, databaseName);
            return;
        }
        
        log.warn("User {} does not exist in database {} before granting roles. Creating user first.", username, databaseName);
        createAndVerifyMongoDBUser(credential, username, databaseName);
    }
    
    /**
     * Creates MongoDB user and verifies creation was successful
     */
    private void createAndVerifyMongoDBUser(AssetCredential credential, String username, String databaseName) {
        String password = generateRandomPassword();
        try {
            createMongoDBUser(credential, username, password);
            log.info("Successfully created MongoDB user {} in database {} before granting roles", username, databaseName);
            
            // Verify user was created successfully
            boolean verifyExists = checkMongoDBUserExists(credential, username);
            if (!verifyExists) {
                log.error("User {} was not created successfully in database {}. Cannot proceed with granting roles.", username, databaseName);
                throw new DatabaseAccessException("Failed to create MongoDB user " + username + " in database " + databaseName + ". User creation did not succeed.", null);
            }
        } catch (Exception e) {
            log.error("Failed to create MongoDB user {} in database {}: {}", username, databaseName, e.getMessage(), e);
            throw new DatabaseAccessException("Failed to create MongoDB user " + username + " in database " + databaseName + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Executes array of MongoDB commands
     */
    private void executeMongoDBCommandArray(MongoDatabase mongoDb, String[] commandArray, String username, AssetCredential credential) {
        for (String command : commandArray) {
            command = command.trim();
            if (!command.isEmpty()) {
                executeMongoDBAccessCommand(mongoDb, command, username, credential);
            }
        }
    }

    /**
     * Prepares MongoDB statements by replacing placeholders
     */
    private String prepareMongoDBStatements(AccessRequest accessRequest, AssetCredential credential, String username) {
        String databaseName = credential.getAsset().getDatabaseName();
        if (databaseName == null || databaseName.isEmpty()) {
            databaseName = Constants.MONGODB_ADMIN_DATABASE; // Default to admin database
        }
        
        return accessRequest.getAccessSql()
                .replace("$USER", username)
                .replace("$DATABASE", databaseName);
    }

    /**
     * Executes a single MongoDB access command (like grantRolesToUser)
     */
    private void executeMongoDBAccessCommand(MongoDatabase mongoDb, String command, String username, AssetCredential credential) {
        try {
            String trimmedCommand = command.trim();
            log.info("Executing MongoDB command: {}", trimmedCommand);
            
            // Try to parse as MongoDB shell command (db.grantRolesToUser(...))
            if (trimmedCommand.startsWith("db.")) {
                executeMongoDBShellAccessCommand(mongoDb, trimmedCommand, username, credential);
            } else {
                // Try to parse as JSON command
                executeMongoDBJsonCommand(mongoDb, trimmedCommand);
            }
        } catch (Exception e) {
            log.error("Error executing MongoDB access command: {}", command, e);
            throw new DatabaseAccessException("Error executing MongoDB access command: " + e.getMessage(), e);
        }
    }
    
    /**
     * Executes MongoDB command in JSON format
     */
    private void executeMongoDBJsonCommand(MongoDatabase mongoDb, String trimmedCommand) {
        try {
            Document commandDoc = Document.parse(trimmedCommand);
            mongoDb.runCommand(commandDoc);
            log.info("Successfully executed MongoDB JSON command");
        } catch (Exception e) {
            log.error("Failed to parse MongoDB command as JSON: {}", trimmedCommand, e);
            throw new DatabaseAccessException("Invalid MongoDB command format: " + trimmedCommand, e);
        }
    }

    /**
     * Executes MongoDB shell command (like db.grantRolesToUser(...))
     */
    private void executeMongoDBShellAccessCommand(MongoDatabase mongoDb, String command, String username, AssetCredential credential) {
        try {
            String databaseName = getDatabaseName(credential);
            
            if (command.contains(Constants.MONGODB_COMMAND_GRANT_ROLES_TO_USER)) {
                executeGrantRolesCommand(mongoDb, command, username, databaseName);
            } else if (command.contains(Constants.MONGODB_COMMAND_REVOKE_ROLES_FROM_USER)) {
                executeRevokeRolesCommand(mongoDb, command, username, databaseName);
            } else if (command.contains(Constants.MONGODB_COMMAND_CREATE_USER) || command.startsWith("db.createUser")) {
                executeCreateUserCommand(mongoDb, command, databaseName);
            } else {
                throw new DatabaseAccessException("Unsupported MongoDB command: " + command, null);
            }
        } catch (Exception e) {
            log.error("Error executing MongoDB shell access command: {}", command, e);
            throw new DatabaseAccessException("Error executing MongoDB shell access command: " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets database name from credential, defaulting to admin if not specified
     */
    private String getDatabaseName(AssetCredential credential) {
        String databaseName = credential.getAsset().getDatabaseName();
        if (databaseName == null || databaseName.isEmpty()) {
            databaseName = Constants.MONGODB_ADMIN_DATABASE;
        }
        return databaseName;
    }
    
    /**
     * Executes grantRolesToUser command
     */
    private void executeGrantRolesCommand(MongoDatabase mongoDb, String command, String username, String databaseName) {
        String rolesStr = extractRolesString(command);
        if (rolesStr == null) {
            throw new DatabaseAccessException("Invalid grantRolesToUser command format: " + command, null);
        }
        
        List<Document> roles = parseMongoDBRoles(rolesStr, databaseName);
        Document grantCommand = new Document(Constants.MONGODB_COMMAND_GRANT_ROLES_TO_USER, username)
                .append(Constants.MONGODB_FIELD_ROLES, roles);
        mongoDb.runCommand(grantCommand);
        log.info("Successfully granted roles to MongoDB user: {} in database: {}", username, databaseName);
    }
    
    /**
     * Executes revokeRolesFromUser command
     */
    private void executeRevokeRolesCommand(MongoDatabase mongoDb, String command, String username, String databaseName) {
        String rolesStr = extractRolesString(command);
        if (rolesStr == null) {
            throw new DatabaseAccessException("Invalid revokeRolesFromUser command format: " + command, null);
        }
        
        List<Document> roles = parseMongoDBRoles(rolesStr, databaseName);
        Document revokeCommand = new Document(Constants.MONGODB_COMMAND_REVOKE_ROLES_FROM_USER, username)
                .append(Constants.MONGODB_FIELD_ROLES, roles);
        mongoDb.runCommand(revokeCommand);
        log.info("Successfully revoked roles from MongoDB user: {} in database: {}", username, databaseName);
    }
    
    /**
     * Extracts roles string from command (between [ and ])
     */
    private String extractRolesString(String command) {
        int rolesStart = command.indexOf('[');
        int rolesEnd = command.lastIndexOf(']');
        
        if (rolesStart > 0 && rolesEnd > rolesStart) {
            return command.substring(rolesStart, rolesEnd + 1); // Include [ and ]
        }
        return null;
    }
    
    /**
     * Executes createUser command
     */
    private void executeCreateUserCommand(MongoDatabase mongoDb, String command, String databaseName) {
        int userStart = command.indexOf(Constants.MONGODB_COMMAND_CREATE_USER);
        String createUserPart = command.substring(userStart);
        
        if (!createUserPart.contains("(")) {
            throw new DatabaseAccessException("Invalid createUser command format: " + command, null);
        }
        
        int paramsStart = createUserPart.indexOf('(');
        int paramsEnd = createUserPart.lastIndexOf(')');
        
        if (paramsStart <= 0 || paramsEnd <= paramsStart) {
            throw new DatabaseAccessException("Invalid createUser command format: " + command, null);
        }
        
        String paramsStr = createUserPart.substring(paramsStart + 1, paramsEnd).trim();
        
        if (!paramsStr.startsWith("{")) {
            throw new DatabaseAccessException("Positional createUser format not yet supported. Use JSON format: {user: \"username\", pwd: \"password\", roles: [...]}", null);
        }
        
        Document createCommand = parseCreateUserParams(paramsStr);
        mongoDb.runCommand(createCommand);
        String createUsername = createCommand.getString(Constants.MONGODB_COMMAND_CREATE_USER);
        log.info("Successfully created MongoDB user: {} in database: {}", createUsername, databaseName);
    }
    
    /**
     * Parses createUser parameters and builds command document
     */
    private Document parseCreateUserParams(String paramsStr) {
        Document createUserDoc = Document.parse(paramsStr);
        String createUsername = createUserDoc.getString(Constants.MONGODB_FIELD_USER);
        String createPassword = createUserDoc.getString(Constants.MONGODB_FIELD_PWD);
        
        if (createUsername == null || createPassword == null) {
            throw new DatabaseAccessException("Invalid createUser command: missing user or pwd", null);
        }
        
        List<Document> createRoles = createUserDoc.getList(Constants.MONGODB_FIELD_ROLES, Document.class);
        
        Document createCommand = new Document(Constants.MONGODB_COMMAND_CREATE_USER, createUsername)
                .append(Constants.MONGODB_FIELD_PWD, createPassword);
        
        if (createRoles != null && !createRoles.isEmpty()) {
            createCommand.append(Constants.MONGODB_FIELD_ROLES, createRoles);
        } else {
            createCommand.append(Constants.MONGODB_FIELD_ROLES, new ArrayList<>());
        }
        
        return createCommand;
    }

    /**
     * Parses MongoDB roles string into List<Document>
     * Format: [{role: "read", db: "database"}] or [{role: "read", db: "database"}, {role: "readWrite", db: "database"}]
     */
    private List<Document> parseMongoDBRoles(String rolesStr, String defaultDatabase) {
        List<Document> roles = new ArrayList<>();
        
        try {
            rolesStr = rolesStr.trim();
            
            // Remove outer brackets if present
            if (rolesStr.startsWith("[") && rolesStr.endsWith("]")) {
                rolesStr = rolesStr.substring(1, rolesStr.length() - 1).trim();
            }
            
            // Split roles by }, { pattern
            if (rolesStr.startsWith("{")) {
                // Single or multiple roles
                String[] roleStrings = rolesStr.split("}\\s*,\\s*\\{");
                
                for (String roleStr : roleStrings) {
                    roleStr = roleStr.trim();
                    // Ensure it has braces
                    if (!roleStr.startsWith("{")) {
                        roleStr = "{" + roleStr;
                    }
                    if (!roleStr.endsWith("}")) {
                        roleStr = roleStr + "}";
                    }
                    Document role = parseMongoDBRole(roleStr, defaultDatabase);
                    roles.add(role);
                }
            } else {
                throw new DatabaseAccessException("Invalid MongoDB roles format: " + rolesStr, null);
            }
        } catch (Exception e) {
            log.error("Error parsing MongoDB roles: {}", rolesStr, e);
            throw new DatabaseAccessException("Error parsing MongoDB roles: " + e.getMessage(), e);
        }
        
        return roles;
    }

    /**
     * Parses a single MongoDB role document
     * Format: {role: "read", db: "database"}
     */
    private Document parseMongoDBRole(String roleStr, String defaultDatabase) {
        try {
            // Simple parsing for {role: "read", db: "database"} format
            // Extract role name and database
            String roleName = extractMongoDBRoleValue(roleStr, "role");
            String dbName = extractMongoDBRoleValue(roleStr, "db");
            
            if (roleName == null) {
                throw new DatabaseAccessException("Role name not found in: " + roleStr, null);
            }
            
            if (dbName == null) {
                dbName = defaultDatabase;
            }
            
            return new Document("role", roleName).append("db", dbName);
        } catch (Exception e) {
            log.error("Error parsing MongoDB role: {}", roleStr, e);
            throw new DatabaseAccessException("Error parsing MongoDB role: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts a value from MongoDB role string
     * Format: role: "read" or role: 'read'
     */
    private String extractMongoDBRoleValue(String roleStr, String key) {
        try {
            String pattern = key + "\\s*:\\s*[\"']([^\"']+)[\"']";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(roleStr);
            if (m.find()) {
                return m.group(1);
            }
            return null;
        } catch (Exception e) {
            log.debug("Error extracting MongoDB role value for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    private boolean shouldExecuteAccessSql(AccessRequest accessRequest) {
        return accessRequest != null && accessRequest.getAccessSql() != null;
    }

    private void executeAccessSqlStatements(Connection connection, AccessRequest accessRequest,
            AssetCredential credential, String username) throws SQLException {
        String[] sqlStatements = prepareSqlStatements(accessRequest, credential, username);

        for (String sql : sqlStatements) {
            sql = sql.trim();
            if (!sql.isEmpty()) {
                executeSingleStatement(connection, sql);
            }
        }
    }

    private String[] prepareSqlStatements(AccessRequest accessRequest, AssetCredential credential, String username) {
        // Get the appropriate schema name based on database type
        String schemaName = getDefaultSchemaName(credential.getAsset().getDatabaseType(),
                credential.getAsset().getDatabaseName());

        // Split SQL statements by semicolon and execute each one
        return accessRequest.getAccessSql()
                .replace("$USER", username)
                .replace("$DATABASE", credential.getAsset().getDatabaseName())
                .replace("$SCHEMA", schemaName)
                .split(";");
    }

    private void executeSingleStatement(Connection connection, String sql) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            log.info("Executing SQL: {}", sql);
            stmt.execute(sql);
        } catch (SQLException e) {
            handleSqlExecutionError(sql, e);
        }
    }

    private void handleSqlExecutionError(String sql, SQLException e) throws SQLException {
        if (isObjectNotFoundError(e)) {
            log.error("SQL execution failed - object not found. SQL: {}, Error: {}", sql, e.getMessage());
            throw new DatabaseAccessException(
                    String.format("Failed to execute SQL: %s. The referenced object may not exist in the database. " +
                            "Please verify that all tables, views, and other database objects referenced in the access request exist.",
                            sql),
                    e);
        } else {
            log.error("SQL execution failed. SQL: {}, Error: {}", sql, e.getMessage());
            throw e;
        }
    }

    private boolean isObjectNotFoundError(SQLException e) {
        return e.getMessage().contains(Constants.SQL_ERROR_OBJECT_NOT_FOUND) ||
                e.getMessage().contains(Constants.SQL_ERROR_DOES_NOT_EXIST);
    }

    private String getCheckUserSql(DatabaseType databaseType) {
        switch (databaseType) {
            case MYSQL:
                return "SELECT User, Host FROM mysql.user WHERE User = ?";
            case POSTGRESQL:
                return "SELECT * FROM pg_user WHERE usename = ?";
            case ORACLE:
                return "SELECT * FROM dba_users WHERE username = ?";
            case SQLSERVER:
                return "SELECT * FROM sys.database_principals WHERE name = ? AND type = 'S'";
            case MONGODB:
                // MongoDB doesn't use SQL - handled separately in getCredentialForAccess
                throw new DatabaseAccessException(
                        "MongoDB user checking should be handled via MongoDB API", null);
            default:
                throw new DatabaseAccessException(
                        Constants.getMessage(Constants.ERROR_UNSUPPORTED_DATABASE_TYPE) + databaseType, null);
        }
    }
    
    /**
     * Checks if a MongoDB user exists
     */
    private boolean checkMongoDBUserExists(AssetCredential credential, String username) {
        try {
            MongoDatabase mongoDb = MongoDBConnectionUtils
                    .getMongoDatabase(credential);
            return MongoDBConnectionUtils.userExists(mongoDb, username);
        } catch (Exception e) {
            log.error("Error checking MongoDB user existence: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Creates a MongoDB user
     */
    private void createMongoDBUser(AssetCredential adminCredential, String username, String password) {
        try {
            MongoDatabase mongoDb = MongoDBConnectionUtils
                    .getMongoDatabase(adminCredential);
            MongoDBConnectionUtils.createUser(mongoDb, username, password);
            log.info("Successfully created MongoDB user: {}", username);
        } catch (Exception e) {
            log.error("Error creating MongoDB user: {}", e.getMessage(), e);
            throw new DatabaseAccessException("Failed to create MongoDB user: " + e.getMessage(), e);
        }
    }

    private String getCreateUserSql(DatabaseType databaseType) {
        switch (databaseType) {
            case MYSQL:
                return "CREATE USER ?@'%' IDENTIFIED BY ?";
            case POSTGRESQL:
                return "CREATE USER ? WITH PASSWORD ?";
            case ORACLE:
                return "CREATE USER ? IDENTIFIED BY ?";
            case SQLSERVER:
                return "CREATE LOGIN ? WITH PASSWORD = ?";
            case MONGODB:
                // MongoDB doesn't use SQL - handled separately in getCredentialForAccess
                throw new DatabaseAccessException(
                        "MongoDB user creation should be handled via MongoDB API", null);
            default:
                throw new DatabaseAccessException(
                        Constants.getMessage(Constants.ERROR_UNSUPPORTED_DATABASE_TYPE) + databaseType, null);
        }
    }

    private String getDefaultSchemaName(DatabaseType databaseType, String databaseName) {
        switch (databaseType) {
            case MYSQL:
                return databaseName; // MySQL uses database name as schema
            case POSTGRESQL:
                return Constants.POSTGRES_SCHEMA_PUBLIC;
            case ORACLE:
                return Constants.ORACLE_SCHEMA_USERS; // Oracle default tablespace/schema
            case MONGODB:
                return databaseName != null ? databaseName : Constants.MONGODB_ADMIN_DATABASE; // MongoDB uses database name
            case SQLSERVER:
                return Constants.SQLSERVER_SCHEMA_DBO; // SQL Server default schema is 'dbo'
            default:
                return Constants.POSTGRES_SCHEMA_PUBLIC; // Default fallback
        }
    }

    /**
     * Generate a unique username based on the provided name and asset
     * Checks against existing users for the specific asset to ensure uniqueness
     */
    private String generateUniqueUsername(String name, Asset asset) {
        // Remove spaces and special characters
        String baseUsername = name.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();

        // Get existing usernames for this asset
        Set<String> existingUsernames = getExistingUsernamesForAsset(asset);

        // Generate unique username
        String username = baseUsername;
        int counter = 1000;
        username = baseUsername + System.currentTimeMillis() % 10000;
        
        while (existingUsernames.contains(username)) {
            username = baseUsername + counter;
            counter++;

            // Prevent infinite loop
            if (counter > 9999) {
                // Use timestamp as fallback
                username = baseUsername + System.currentTimeMillis() % 10000;
                break;
            }
        }

        return username;
    }

    /**
     * Get existing usernames for a specific asset
     */
    private Set<String> getExistingUsernamesForAsset(Asset asset) {
        Set<String> existingUsernames = new HashSet<>();

        try {
            // Get all credentials for this asset
            List<AssetCredential> credentials = assetCredentialsRepository.findByAssetId(asset.getId());

            for (AssetCredential credential : credentials) {
                processCredentialForUsernameCollection(credential, asset, existingUsernames);
            }

        } catch (Exception e) {
            log.warn("Error getting existing usernames for asset {}: {}", asset.getId(), e.getMessage());
        }

        return existingUsernames;
    }

    /**
     * Process a single credential to collect existing usernames for an asset
     */
    private void processCredentialForUsernameCollection(AssetCredential credential, Asset asset, Set<String> existingUsernames) {
        try (Connection connection = databaseConnectionUtils.getConnectionFromAssetCredential(credential)) {
            DatabaseType databaseType = asset.getDatabaseType();

            switch (databaseType) {
                case MYSQL:
                    existingUsernames.addAll(getMySQLUsernames(connection, credential.getUsername()));
                    break;
                case POSTGRESQL:
                    existingUsernames.addAll(getPostgreSQLUsernames(connection, credential.getUsername()));
                    break;
                case SQLSERVER:
                    existingUsernames.addAll(getSQLServerUsernames(connection, credential.getUsername()));
                    break;
                case MONGODB:
                    existingUsernames.addAll(getMongoDBUsernames(credential));
                    break;
                case ORACLE:
                    existingUsernames.addAll(getOracleUsernames(connection, credential.getUsername()));
                    break;
                default:
                    log.warn("Unsupported database type for username checking: {}", databaseType);
            }
        } catch (SQLException e) {
            log.warn("Error getting usernames for asset {}: {}", asset.getId(), e.getMessage());
        }
    }

    /**
     * Get MySQL usernames that match the pattern
     */
    private Set<String> getMySQLUsernames(Connection connection, String baseUsername) throws SQLException {
        Set<String> usernames = new HashSet<>();
        String query = "SELECT User FROM mysql.user WHERE User LIKE ? AND Host = '%'";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, baseUsername + "%");

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    usernames.add(rs.getString("User"));
                }
            }
        }

        return usernames;
    }

    /**
     * Get PostgreSQL usernames that match the pattern
     */
    private Set<String> getPostgreSQLUsernames(Connection connection, String baseUsername) throws SQLException {
        Set<String> usernames = new HashSet<>();
        String query = "SELECT usename FROM pg_user WHERE usename LIKE ?";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, baseUsername + "%");

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    usernames.add(rs.getString("usename"));
                }
            }
        }

        return usernames;
    }

    /**
     * Get SQL Server usernames that match the pattern
     */
    private Set<String> getSQLServerUsernames(Connection connection, String baseUsername) throws SQLException {
        Set<String> usernames = new HashSet<>();
        String query = "SELECT name FROM sys.database_principals WHERE name LIKE ? AND type = 'S'";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, baseUsername + "%");

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    usernames.add(rs.getString("name"));
                }
            }
        }

        return usernames;
    }

    /**
     * Get MongoDB usernames that match the pattern
     */
    private Set<String> getMongoDBUsernames(AssetCredential credential) {
        Set<String> usernames = new HashSet<>();
        try {
            MongoDatabase mongoDb = MongoDBConnectionUtils
                    .getMongoDatabase(credential);
            List<String> allUsers = MongoDBConnectionUtils.listMongoDBUsers(mongoDb);
            
            // Filter users that match the base username pattern
            String baseUsername = credential.getUsername();
            if (baseUsername != null) {
                for (String user : allUsers) {
                    if (user.startsWith(baseUsername)) {
                        usernames.add(user);
                    }
                }
            } else {
                // If no base username, return all users
                usernames.addAll(allUsers);
            }
        } catch (Exception e) {
            log.warn("Error getting MongoDB usernames: {}", e.getMessage());
        }
        return usernames;
    }

    /**
     * Get Oracle usernames that match the pattern
     */
    private Set<String> getOracleUsernames(Connection connection, String baseUsername) throws SQLException {
        Set<String> usernames = new HashSet<>();
        String query = "SELECT username FROM all_users WHERE username LIKE ?";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, baseUsername.toUpperCase() + "%");

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    usernames.add(rs.getString("username").toLowerCase());
                }
            }
        }

        return usernames;
    }

    private String generateRandomPassword() {
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String digits = "0123456789";
        String special = "!@#$%^&*()";
        String allChars = upper + lower + digits + special;

        // Ensure at least one character from each category to satisfy MySQL validate_password policy
        StringBuilder password = new StringBuilder();
        password.append(upper.charAt(secureRandom.nextInt(upper.length())));
        password.append(lower.charAt(secureRandom.nextInt(lower.length())));
        password.append(digits.charAt(secureRandom.nextInt(digits.length())));
        password.append(special.charAt(secureRandom.nextInt(special.length())));

        // Fill remaining characters randomly
        for (int i = 4; i < 12; i++) {
            password.append(allChars.charAt(secureRandom.nextInt(allChars.length())));
        }

        // Shuffle to avoid predictable positions of required characters
        char[] passwordArray = password.toString().toCharArray();
        for (int i = passwordArray.length - 1; i > 0; i--) {
            int j = secureRandom.nextInt(i + 1);
            char temp = passwordArray[i];
            passwordArray[i] = passwordArray[j];
            passwordArray[j] = temp;
        }

        return Constants.TEMP_PSD_PREFIX + new String(passwordArray);
    }

    private String getCredentialForAccess(
            Connection connection,
            AssetCredential credential,
            User requestor,
            String existUsername,
            Map<String, String> newCredMapper)
            throws SQLException, CommonUtils.CryptoException {

        String username = requestor.getEmail().split("@")[0];
        
        // Handle MongoDB separately since it doesn't use JDBC Connection
        if (credential.getAsset().getDatabaseType() == DatabaseType.MONGODB) {
            return getCredentialForMongoDBAccess(credential, requestor, existUsername, newCredMapper);
        }
        
        if (existUsername.isEmpty()) {
            username = generateUniqueUsername(username, credential.getAsset());

            // Check if user exists
            String checkUserSql = getCheckUserSql(credential.getAsset().getDatabaseType());
            PreparedStatement checkUserStmt = connection.prepareStatement(checkUserSql);
            checkUserStmt.setString(1, username);
            ResultSet rs = checkUserStmt.executeQuery();

            if (!rs.next()) {
                // User doesn't exist, create one
                String password = generateRandomPassword();

                // Create user
                if (credential.getAsset().getDatabaseType() == DatabaseType.POSTGRESQL) {
                    // PostgreSQL doesn't support parameter binding for identifiers in DDL.
                    // Validate and escape the identifier; bind secret via PreparedStatement.
                    if (!CommonUtils.isValidUsername(username)) {
                        throw new IllegalArgumentException("Invalid username: " + username);
                    }
                    String escapedPgUsername = CommonUtils.escapePostgresqlIdentifier(username);
                    // Securely escape password for PostgreSQL CREATE USER statement
                    String escapedPassword = escapePostgreSQLPassword(password);
                    String createUserTemplate = "CREATE USER \"" + escapedPgUsername + "\" WITH PASSWORD '"
                            + escapedPassword + "'"; // NOSONAR
                    // java:S2077
                    // -
                    // identifier
                    // is
                    // validated
                    // and
                    // safely
                    // escaped
                    try (PreparedStatement stmt = connection.prepareStatement(createUserTemplate)) {
                        stmt.executeUpdate();
                    }
                } else {
                    String createUserSql = getCreateUserSql(credential.getAsset().getDatabaseType());
                    PreparedStatement createUserStmt = connection.prepareStatement(createUserSql);
                    createUserStmt.setString(1, username);
                    createUserStmt.setString(2, password);
                    createUserStmt.executeUpdate();
                }

                // Store user credentials in asset_credentials table
                saveUserCredentialToRepository(credential, username, password, requestor, newCredMapper);
            }
        } else {
            username = existUsername;
        }
        return username;
    }
    
    /**
     * Handles credential access for MongoDB (doesn't use JDBC Connection)
     */
    private String getCredentialForMongoDBAccess(
            AssetCredential credential,
            User requestor,
            String existUsername,
            Map<String, String> newCredMapper)
            throws CommonUtils.CryptoException {
        
        String username = requestor.getEmail().split("@")[0];
        
        if (existUsername.isEmpty()) {
            username = generateUniqueUsername(username, credential.getAsset());

            // Check if user exists
            if (!checkMongoDBUserExists(credential, username)) {
                // User doesn't exist, create one
                String password = generateRandomPassword();
                
                // Create MongoDB user
                createMongoDBUser(credential, username, password);

                // Store user credentials in asset_credentials table
                saveUserCredentialToRepository(credential, username, password, requestor, newCredMapper);
            }
        } else {
            username = existUsername;
        }
        return username;
    }
    
    /**
     * Saves user credential to repository and populates the credential mapper
     * 
     * @param credential The asset credential containing asset information
     * @param username The username to set
     * @param password The password to set
     * @param requestor The user requesting access
     * @param newCredMapper The map to populate with credential information
     */
    private void saveUserCredentialToRepository(
            AssetCredential credential,
            String username,
            String password,
            User requestor,
            Map<String, String> newCredMapper) {
        AssetCredential userCredential = new AssetCredential();
        userCredential.setAsset(credential.getAsset());
        userCredential.setUsername(username);
        userCredential.setPassword(password);
        userCredential.setUserAccessType(Roles.ACCESSOR.getOriginalName());
        userCredential.setUser(requestor);
        userCredential.setIsTemporaryPassword(true);
        assetCredentialsRepository.saveAndFlush(userCredential);
        newCredMapper.put(Constants.CREDENTIAL_ID_KEY, userCredential.getId().toString());
        newCredMapper.put(Constants.EMAIL_VAR_DB_USERNAME, username);
        newCredMapper.put(Constants.EMAIL_VAR_DB_PASSWORD, password);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeCredentialAccess(AssetCredential credential, AssetCredential ownerCredential)
            throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {

        // Handle MongoDB separately since it doesn't use JDBC Connection
        if (credential.getAsset().getDatabaseType() == DatabaseType.MONGODB) {
            revokeMongoDBCredentialAccess(credential, ownerCredential);
            return;
        }

        try (Connection connection = databaseConnectionUtils.getConnectionFromAssetCredential(ownerCredential)) { // Use
                                                                                                                  // owner's
                                                                                                                  // connection
            String revokeUserSql;
            PreparedStatement statement;

            switch (credential.getAsset().getDatabaseType()) {
                case MYSQL:
                    // First revoke all privileges
                    revokeUserSql = "REVOKE ALL PRIVILEGES, GRANT OPTION FROM ?@'%'";
                    statement = connection.prepareStatement(revokeUserSql);
                    statement.setString(1, credential.getUsername());
                    statement.execute();

                    // Then drop the user
                    revokeUserSql = "DROP USER ?@'%'";
                    statement = connection.prepareStatement(revokeUserSql);
                    statement.setString(1, credential.getUsername());
                    statement.execute();
                    break;

                case POSTGRESQL:
                    // PostgreSQL doesn't support parameter binding for identifiers in DDL.
                    // Validate and escape identifier; build static template; no user-controllable
                    // SQL parts remain.
                    if (!CommonUtils.isValidUsername(credential.getUsername())) {
                        throw new IllegalArgumentException("Invalid username: " + credential.getUsername());
                    }
                    String escapedPgUser = CommonUtils.escapePostgresqlIdentifier(credential.getUsername());
                    // Revoke all privileges from all tables
                    try (Statement stmt = connection.createStatement()) {
                        String revokeTables = String.format(
                                "REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA %s FROM \"%s\"",
                                Constants.POSTGRES_SCHEMA_PUBLIC, escapedPgUser); // NOSONAR java:S2077 - identifier
                                                                                  // validated and escaped
                        stmt.execute(revokeTables);
                    }

                    // Revoke all privileges from all sequences
                    try (Statement stmt = connection.createStatement()) {
                        String revokeSeq = String.format(
                                "REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA %s FROM \"%s\"",
                                Constants.POSTGRES_SCHEMA_PUBLIC, escapedPgUser); // NOSONAR java:S2077
                        stmt.execute(revokeSeq);
                    }

                    // Drop the user
                    try (Statement stmt = connection.createStatement()) {
                        String dropUser = String.format("DROP USER IF EXISTS \"%s\"", escapedPgUser); // NOSONAR
                                                                                                      // java:S2077
                        stmt.execute(dropUser);
                    }
                    break;

                case ORACLE:
                    // Revoke all privileges and roles
                    revokeUserSql = "REVOKE ALL PRIVILEGES FROM ?";
                    statement = connection.prepareStatement(revokeUserSql);
                    statement.setString(1, credential.getUsername());
                    statement.execute();

                    // Drop the user
                    revokeUserSql = "DROP USER ? CASCADE";
                    statement = connection.prepareStatement(revokeUserSql);
                    statement.setString(1, credential.getUsername());
                    statement.execute();
                    break;

                case SQLSERVER:
                    // SQL Server doesn't support parameter binding for identifiers in DDL.
                    if (!CommonUtils.isValidUsername(credential.getUsername())) {
                        throw new IllegalArgumentException("Invalid username: " + credential.getUsername());
                    }
                    String escapedSqlUser = CommonUtils.escapeSqlServerIdentifier(credential.getUsername());
                    // First drop the database user
                    dropSqlServerUser(connection, escapedSqlUser, credential.getUsername());
                    // Then drop the login
                    dropSqlServerLogin(connection, escapedSqlUser, credential.getUsername());
                    break;

                default:
                    throw new DatabaseAccessException(
                            Constants.getMessage(Constants.ERROR_UNSUPPORTED_DATABASE_TYPE)
                                    + credential.getAsset().getDatabaseType(),
                            null);
            }

            log.info("Successfully revoked access and dropped user: {}", credential.getUsername());
        } catch (SQLException e) {
            log.error(Constants.getMessage("log.error.revoking.access.for.user") + credential.getUsername(), e);
            throw new DatabaseAccessException(Constants.getMessage("error.revoking.database.access"), e);
        }
    }
    
    /**
     * Revokes MongoDB credential access by dropping the user
     */
    private void revokeMongoDBCredentialAccess(AssetCredential credential, AssetCredential ownerCredential) {
        try {
            MongoDatabase mongoDb = MongoDBConnectionUtils
                    .getMongoDatabase(ownerCredential);
            
            // Drop the MongoDB user
            MongoDBConnectionUtils.dropUser(mongoDb, credential.getUsername());
            log.info("Successfully revoked MongoDB credential access for user: {}", credential.getUsername());
        } catch (Exception e) {
            log.error("Error revoking MongoDB credential access: {}", e.getMessage(), e);
            throw new DatabaseAccessException("Failed to revoke MongoDB credential access: " + e.getMessage(), e);
        }
    }

    /**
     * Drops a SQL Server database user.
     * 
     * @param connection        the database connection
     * @param sqlServerUsername the escaped SQL Server username
     * @param originalUsername  the original username for logging
     */
    private void dropSqlServerUser(Connection connection, String sqlServerUsername, String originalUsername) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(String.format("DROP USER IF EXISTS [%s]", sqlServerUsername)); // NOSONAR java:S2077 -
                                                                                        // identifier validated and
                                                                                        // safely escaped
            log.info("Dropped SQL Server user: {}", originalUsername);
        } catch (SQLException e) {
            log.warn("Failed to drop SQL Server user {}: {}", originalUsername, e.getMessage());
        }
    }

    /**
     * Drops a SQL Server login.
     * 
     * @param connection        the database connection
     * @param sqlServerUsername the escaped SQL Server username
     * @param originalUsername  the original username for logging
     */
    private void dropSqlServerLogin(Connection connection, String sqlServerUsername, String originalUsername) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(String.format("DROP LOGIN [%s]", sqlServerUsername)); // NOSONAR java:S2077 - identifier
                                                                               // validated and safely escaped
            log.info("Dropped SQL Server login: {}", originalUsername);
        } catch (SQLException e) {
            log.warn("Failed to drop SQL Server login {}: {}", originalUsername, e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> executeQueryWithCredentials(AssetCredential credential, String query,
            boolean isChangeRequest)
            throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException, InvalidKeyException, SQLException {
        return executeQueryWithCredentials(credential, query, isChangeRequest, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> executeQueryWithCredentialsDryRun(AssetCredential credential, String query,
            boolean isChangeRequest)
            throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException, InvalidKeyException, SQLException {
        return executeQueryWithCredentials(credential, query, isChangeRequest, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected Map<String, Object> executeQueryWithCredentials(AssetCredential credential, String query,
            boolean isChangeRequest, boolean isDryRun)
            throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException, InvalidKeyException, SQLException {

        // Handle MongoDB separately since it doesn't use JDBC
        if (credential.getAsset().getDatabaseType() == DatabaseType.MONGODB) {
            return executeMongoDBQuery(credential, query, isChangeRequest, isDryRun);
        }

        String decryptedPassword = getDecryptedPassword(credential, query);
        String jdbcUrl = databaseConnectionUtils.buildJdbcUrl(credential.getAsset());

        try (Connection connection = DriverManager.getConnection(jdbcUrl, credential.getUsername(),
                decryptedPassword)) {
            String preparedQuery = prepareQueryForExecution(query, isChangeRequest, isDryRun, credential.getAsset());
            List<Map<String, Object>> allResults = executeAllQueries(connection, preparedQuery, isChangeRequest);

            return createFinalResult(allResults);

        } catch (SQLException e) {
            log.error("Error connecting to database: {}", jdbcUrl, e);
            throw new DatabaseAccessException("Error connecting to database: " + e.getMessage(), e);
        }
    }

    /**
     * Execute MongoDB query using native MongoDB driver
     * Supports MongoDB commands, find operations, and aggregation pipelines
     */
    private Map<String, Object> executeMongoDBQuery(AssetCredential credential, String query,
            boolean isChangeRequest, boolean isDryRun) {
        AssetCredential tempCredential = databaseConnectionUtils.createDecryptedTempCredential(credential);
        
        try {
            // Get MongoDB database
            MongoDatabase mongoDb = MongoDBConnectionUtils.getMongoDatabase(tempCredential);
            
            // Parse and execute MongoDB query
            String preparedQuery = prepareQueryForExecution(query, isChangeRequest, isDryRun, credential.getAsset());
            List<Map<String, Object>> allResults = executeMongoDBQueries(mongoDb, preparedQuery, isChangeRequest);
            
            return createFinalResult(allResults);
            
        } catch (Exception e) {
            log.error("Error executing MongoDB query: {}", query, e);
            throw new DatabaseAccessException("Error executing MongoDB query: " + e.getMessage(), e);
        }
    }

    /**
     * Execute MongoDB queries (split by semicolon if multiple)
     */
    private List<Map<String, Object>> executeMongoDBQueries(MongoDatabase mongoDb, String query, boolean isChangeRequest) {
        String[] individualQueries = query.split(";");
        List<Map<String, Object>> allResults = new ArrayList<>();
        
        for (String individualQuery : individualQueries) {
            String trimmedQuery = individualQuery.trim();
            if (!trimmedQuery.isEmpty()) {
                Map<String, Object> queryResult = executeMongoDBQuery(mongoDb, trimmedQuery, isChangeRequest);
                allResults.add(queryResult);
            }
        }
        
        return allResults;
    }

    /**
     * Execute a single MongoDB query
     * Supports:
     * - MongoDB commands (JSON format): {"find": "collectionName", "filter": {...}}
     * - Aggregation pipelines: [{"$match": {...}}, {"$group": {...}}]
     * - Simple find operations: db.collection.find() (converted to command format)
     */
    private Map<String, Object> executeMongoDBQuery(MongoDatabase mongoDb, String query, boolean isChangeRequest) {
        log.debug("Executing MongoDB query: {}", query);
        
        try {
            // Try to parse as JSON (MongoDB command or aggregation pipeline)
            JsonNode jsonNode = objectMapper.readTree(query);
            
            if (jsonNode.isArray()) {
                // Aggregation pipeline
                return executeMongoDBAggregation(mongoDb, query, jsonNode, isChangeRequest);
            } else if (jsonNode.isObject()) {
                // MongoDB command
                return executeMongoDBCommand(mongoDb, query, isChangeRequest);
            }
        } catch (JsonProcessingException e) {
            // Not JSON, try to parse as MongoDB shell command
            log.debug("Query is not JSON, trying to parse as MongoDB shell command: {}", query);
            return executeMongoDBShellCommand(mongoDb, query, isChangeRequest);
        } catch (Exception e) {
            log.error("Error executing MongoDB query: {}", query, e);
            return handleMongoDBQueryError(query, e, isChangeRequest);
        }
        
        // Fallback: treat as error
        return handleMongoDBQueryError(query, 
            new IllegalArgumentException("Unsupported MongoDB query format"), isChangeRequest);
    }

    /**
     * Execute MongoDB aggregation pipeline
     */
    private Map<String, Object> executeMongoDBAggregation(MongoDatabase mongoDb, String query, 
            JsonNode pipelineNode, boolean isChangeRequest) {
        try {
            List<Document> pipeline = new ArrayList<>();
            
            for (JsonNode stage : pipelineNode) {
                pipeline.add(Document.parse(stage.toString()));
            }
            
            // Extract collection name from query or use default
            String collectionName = extractCollectionNameFromQuery(query);
            if (collectionName == null) {
                throw new IllegalArgumentException("Collection name not specified in aggregation pipeline");
            }
            
            MongoCollection<Document> collection = mongoDb.getCollection(collectionName);
            List<Map<String, Object>> data = new ArrayList<>();
            
            for (Document doc : collection.aggregate(pipeline)) {
                data.add(docToMap(doc));
            }
            
            List<String> headers = data.isEmpty() ? new ArrayList<>() : 
                new ArrayList<>(data.get(0).keySet());
            
            return createQueryResult(query, headers, data);
            
        } catch (Exception e) {
            log.error("Error executing MongoDB aggregation: {}", query, e);
            return handleMongoDBQueryError(query, e, isChangeRequest);
        }
    }

    /**
     * Execute MongoDB command (like find, count, etc.)
     */
    private Map<String, Object> executeMongoDBCommand(MongoDatabase mongoDb, String query, 
            boolean isChangeRequest) {
        try {
            Document command = Document.parse(query);
            
            // Handle find command
            if (command.containsKey("find")) {
                String collectionName = command.getString("find");
                MongoCollection<Document> collection = mongoDb.getCollection(collectionName);
                
                FindIterable<Document> findIterable = collection.find();
                
                // Apply filter if present
                if (command.containsKey("filter")) {
                    Document filter = command.get("filter", Document.class);
                    findIterable = collection.find(filter);
                }
                
                // Apply limit if present
                if (command.containsKey("limit")) {
                    findIterable = findIterable.limit(command.getInteger("limit"));
                }
                
                // Apply skip if present
                if (command.containsKey("skip")) {
                    findIterable = findIterable.skip(command.getInteger("skip"));
                }
                
                List<Map<String, Object>> data = new ArrayList<>();
                for (Document doc : findIterable) {
                    data.add(docToMap(doc));
                }
                
                List<String> headers = data.isEmpty() ? new ArrayList<>() : 
                    new ArrayList<>(data.get(0).keySet());
                
                return createQueryResult(query, headers, data);
            }
            
            // Handle other commands via runCommand
            Document result = mongoDb.runCommand(command);
            List<String> headers = List.of(Constants.QUERY_RESULT_FIELD_RESULT);
            List<Map<String, Object>> data = List.of(Map.of(Constants.QUERY_RESULT_FIELD_RESULT, docToMap(result)));
            
            return createQueryResult(query, headers, data);
            
        } catch (Exception e) {
            log.error("Error executing MongoDB command: {}", query, e);
            return handleMongoDBQueryError(query, e, isChangeRequest);
        }
    }

    /**
     * Execute MongoDB shell command (like db.collection.find())
     * This is a simplified parser for common MongoDB shell commands
     */
    private Map<String, Object> executeMongoDBShellCommand(MongoDatabase mongoDb, String query, boolean isChangeRequest) {
        try {
            String trimmedQuery = query.trim();
            
            // Pattern: db.collectionName.find(...)
            if (isFindCommand(trimmedQuery)) {
                Map<String, Object> result = executeFindCommand(mongoDb, trimmedQuery, query);
                if (result != null) {
                    return result;
                }
            }
            
            // If not recognized, try as a command
            return executeGenericMongoDBCommand(mongoDb, trimmedQuery, query);
            
        } catch (Exception e) {
            log.error("Error executing MongoDB shell command: {}", query, e);
            return handleMongoDBQueryError(query, e, isChangeRequest);
        }
    }
    
    /**
     * Checks if query is a find command (db.collection.find())
     */
    private boolean isFindCommand(String trimmedQuery) {
        return trimmedQuery.startsWith("db.") && trimmedQuery.contains(".find(");
    }
    
    /**
     * Executes MongoDB find command
     */
    private Map<String, Object> executeFindCommand(MongoDatabase mongoDb, String trimmedQuery, String originalQuery) {
        String[] parts = trimmedQuery.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        
        String collectionName = parts[1].split("\\(")[0].trim();
        MongoCollection<Document> collection = mongoDb.getCollection(collectionName);
        
        FindIterable<Document> findIterable = collection.find();
        findIterable = applyLimitIfPresent(findIterable, trimmedQuery);
        
        List<Map<String, Object>> data = collectFindResults(findIterable);
        List<String> headers = extractHeadersFromData(data);
        
        return createQueryResult(originalQuery, headers, data);
    }
    
    /**
     * Applies limit to find iterable if present in query
     */
    private FindIterable<Document> applyLimitIfPresent(FindIterable<Document> findIterable, String trimmedQuery) {
        if (!trimmedQuery.contains(".limit(")) {
            return findIterable;
        }
        
        try {
            String limitStr = extractLimitValue(trimmedQuery);
            int limit = Integer.parseInt(limitStr.trim());
            return findIterable.limit(limit);
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            // Ignore invalid limit
            return findIterable;
        }
    }
    
    /**
     * Extracts limit value from query string
     */
    private String extractLimitValue(String trimmedQuery) {
        int limitStart = trimmedQuery.indexOf(".limit(") + 7;
        String limitStr = trimmedQuery.substring(limitStart);
        int limitEnd = limitStr.indexOf(")");
        return limitStr.substring(0, limitEnd);
    }
    
    /**
     * Collects results from find iterable
     */
    private List<Map<String, Object>> collectFindResults(FindIterable<Document> findIterable) {
        List<Map<String, Object>> data = new ArrayList<>();
        for (Document doc : findIterable) {
            data.add(docToMap(doc));
        }
        return data;
    }
    
    /**
     * Extracts headers from data, returns empty list if data is empty
     */
    private List<String> extractHeadersFromData(List<Map<String, Object>> data) {
        if (data.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(data.get(0).keySet());
    }
    
    /**
     * Executes generic MongoDB command
     */
    private Map<String, Object> executeGenericMongoDBCommand(MongoDatabase mongoDb, String trimmedQuery, String originalQuery) {
        Document command = Document.parse("{" + trimmedQuery + "}");
        Document result = mongoDb.runCommand(command);
        List<String> headers = List.of(Constants.QUERY_RESULT_FIELD_RESULT);
        List<Map<String, Object>> data = List.of(Map.of(Constants.QUERY_RESULT_FIELD_RESULT, docToMap(result)));
        return createQueryResult(originalQuery, headers, data);
    }

    /**
     * Extract collection name from query string
     */
    private String extractCollectionNameFromQuery(String query) {
        // Try to find collection name in various formats
        if (query.contains("\"find\"")) {
            try {
                JsonNode jsonNode = objectMapper.readTree(query);
                if (jsonNode.has("find")) {
                    return jsonNode.get("find").asText();
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        
        // Try db.collectionName pattern
        if (query.contains("db.")) {
            String[] parts = query.split("db\\.");
            if (parts.length > 1) {
                String rest = parts[1];
                String[] collectionParts = rest.split("[\\.\\(]");
                if (collectionParts.length > 0) {
                    return collectionParts[0].trim();
                }
            }
        }
        
        return null;
    }

    /**
     * Convert MongoDB Document to Map
     */
    private Map<String, Object> docToMap(Document doc) {
        Map<String, Object> map = new HashMap<>();
        for (String key : doc.keySet()) {
            Object value = doc.get(key);
            if (value instanceof Document) {
                map.put(key, docToMap((Document) value));
            } else if (value instanceof List) {
                List<Object> list = new ArrayList<>();
                for (Object item : (List<?>) value) {
                    if (item instanceof Document) {
                        list.add(docToMap((Document) item));
                    } else {
                        list.add(item);
                    }
                }
                map.put(key, list);
            } else {
                map.put(key, value);
            }
        }
        return map;
    }

    /**
     * Handle MongoDB query errors
     */
    private Map<String, Object> handleMongoDBQueryError(String query, Exception e, boolean isChangeRequest) {
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put(Constants.QUERY_RESULT_FIELD_QUERY, query);
        errorResult.put(Constants.QUERY_RESULT_FIELD_HEADERS, List.of(Constants.QUERY_RESULT_ERROR_HEADER));
        errorResult.put("data", List.of(Map.of(Constants.QUERY_RESULT_ERROR_HEADER, e.getMessage())));
        errorResult.put("hasError", true);
        
        if (!isChangeRequest) {
            throw new DatabaseAccessException(Constants.getMessage("error.executing.query") + e.getMessage(), e);
        }
        
        return errorResult;
    }

    private String getDecryptedPassword(AssetCredential credential, String query)
            throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {

        if (credential.getIsTemporaryPassword()) {
            log.error(Constants.getMessage("error.temp.password.query") + query);
            throw new DatabaseAccessException(Constants.getMessage("error.temp.password.query") + query, null);
        }

        return databaseConnectionUtils.decryptCredentialPassword(credential);
    }

    private String prepareQueryForExecution(String query, boolean isChangeRequest, boolean isDryRun, Asset asset) {
        String modifiedQuery = query;

        // Add LIMIT clause for SELECT queries if recordCountLimit is set
        if (asset != null && asset.getRecordCountLimit() != null && asset.getRecordCountLimit() > 0) {
            modifiedQuery = addLimitClauseToSelectQueries(modifiedQuery, asset.getRecordCountLimit());
        }

        if (!isChangeRequest) {
            return modifiedQuery;
        }

        modifiedQuery = Constants.SQL_TRANSACTION_START + "; " + modifiedQuery;
        if (isDryRun) {
            modifiedQuery += modifiedQuery.trim().endsWith(Constants.SQL_STATEMENT_SEPARATOR)
                    ? Constants.SQL_ROLLBACK_SUFFIX
                    : "; " + Constants.SQL_TRANSACTION_ROLLBACK;
            log.debug("Dry run query: {}", modifiedQuery);
        } else {
            modifiedQuery += modifiedQuery.trim().endsWith(Constants.SQL_STATEMENT_SEPARATOR)
                    ? Constants.SQL_COMMIT_SUFFIX
                    : "; " + Constants.SQL_TRANSACTION_COMMIT;
            log.debug("Execution query: {}", modifiedQuery);
        }
        return modifiedQuery;
    }

    /**
     * Add LIMIT clause to SELECT queries based on asset's recordCountLimit
     */
    private String addLimitClauseToSelectQueries(String query, Integer recordCountLimit) {
        if (query == null || query.trim().isEmpty()) {
            return query;
        }

        // Split query by semicolon to handle multiple statements
        String[] queries = query.split(";");
        StringBuilder modifiedQuery = new StringBuilder();

        for (int i = 0; i < queries.length; i++) {
            String individualQuery = queries[i].trim();
            if (!individualQuery.isEmpty()) {
                if (i > 0) {
                    modifiedQuery.append("; ");
                }
                // Check if this query should have LIMIT clause applied (only SELECT queries)
                if (shouldApplyLimit(individualQuery.toUpperCase())) {
                    individualQuery = addLimitToSelectQuery(individualQuery, recordCountLimit);
                }

                modifiedQuery.append(individualQuery);
            }
        }

        return modifiedQuery.toString();
    }

    /**
     * Add LIMIT clause to a single SELECT query
     */
    private String addLimitToSelectQuery(String query, Integer recordCountLimit) {
        String upperQuery = query.toUpperCase().trim();

        // Check if LIMIT clause already exists
        if (upperQuery.contains(" " + Constants.SQL_KEYWORD_LIMIT + " ")) {
            log.debug("Query already contains LIMIT clause, skipping: {}", query);
            return query;
        }

        // Add LIMIT clause at the end of the query
        String modifiedQuery = query.trim();
        if (!modifiedQuery.endsWith(";")) {
            modifiedQuery += " " + Constants.SQL_KEYWORD_LIMIT + " " + recordCountLimit;
        } else {
            // Insert LIMIT before the semicolon
            modifiedQuery = modifiedQuery.substring(0, modifiedQuery.length() - 1) + " " + Constants.SQL_KEYWORD_LIMIT
                    + " " + recordCountLimit + ";";
        }

        log.debug("Added LIMIT {} to query: {}", recordCountLimit, modifiedQuery);
        return modifiedQuery;
    }

    private List<Map<String, Object>> executeAllQueries(Connection connection, String query, boolean isChangeRequest) {
        String[] individualQueries = query.split(";");
        List<Map<String, Object>> allResults = new ArrayList<>();

        for (String individualQuery : individualQueries) {
            String trimmedQuery = individualQuery.trim();
            if (!trimmedQuery.isEmpty()) {
                Map<String, Object> queryResult = executeIndividualQuery(connection, trimmedQuery, isChangeRequest);
                allResults.add(queryResult);
            }
        }

        return allResults;
    }

    private Map<String, Object> executeIndividualQuery(Connection connection, String query, boolean isChangeRequest) {
        log.debug("Executing individual query: {}", query);

        // Validate query to prevent SQL injection
        validateQueryForSecurity(query);

        // Security Note: PreparedStatement is used here, but since the entire query string comes from user input,
        // it doesn't provide SQL injection protection unless parameter placeholders (?) are used.
        // This is intentional for ad-hoc query execution (like a SQL client), but queries are validated before execution.
        // The validateQueryForSecurity() method checks for dangerous patterns and length limits.
        // Suppressing warning: This is an intentional feature for query execution, validated before use.
        try (@SuppressWarnings("java:S2077") // SQL injection: Query is validated before execution
             PreparedStatement statement = connection.prepareStatement(query)) {
            QueryType queryType = determineQueryType(query);
            return processQueryExecution(statement, query, queryType);

        } catch (SQLException e) {
            log.error("Error executing individual query: {}", query, e);
            return handleQueryError(query, e, isChangeRequest);
        }
    }

    /**
     * Validate SQL query for security concerns
     * Checks for dangerous patterns, query length limits, and suspicious constructs
     * 
     * @param query The SQL query to validate
     * @throws IllegalArgumentException if query contains dangerous patterns or exceeds limits
     */
    private void validateQueryForSecurity(String query) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Query cannot be null or empty");
        }

        // Check query length to prevent DoS attacks
        if (query.length() > 100000) { // 100KB limit
            throw new IllegalArgumentException("Query exceeds maximum length limit (100KB)");
        }

        String upperQuery = query.toUpperCase().trim();
        
        // Check for dangerous SQL patterns that could be used for injection
        // Note: This is a basic validation - comprehensive SQL parsing would be needed for complete protection
        // Only block patterns that are highly unlikely to be legitimate
        String[] dangerousPatterns = {
            "'; --", "'; /*", "'; #",  // SQL injection comment patterns (blocking only when combined with quote)
            "xp_cmdshell", "sp_executesql",  // SQL Server command execution (always dangerous)
            "INTO OUTFILE", "INTO DUMPFILE",  // File operations (dangerous when not controlled)
        };
        
        for (String pattern : dangerousPatterns) {
            if (upperQuery.contains(pattern)) {
                log.warn("Query contains potentially dangerous pattern: {}", pattern);
                throw new IllegalArgumentException("Query contains potentially dangerous SQL pattern: " + pattern);
            }
        }
        
        // Check for suspicious patterns that might indicate injection attempts
        // These are logged but not blocked to allow legitimate queries
        if (upperQuery.contains("';") && (upperQuery.contains("--") || upperQuery.contains("/*"))) {
            log.warn("Query contains suspicious SQL injection pattern - query: {}", query);
            // Don't block, but log for security audit
        }
    }

    private QueryType determineQueryType(String query) {
        String upperQuery = query.toUpperCase();

        if (isSelectQuery(upperQuery)) {
            return QueryType.SELECT;
        } else if (isTransactionControl(upperQuery)) {
            return QueryType.TRANSACTION_CONTROL;
        } else {
            return QueryType.DML;
        }
    }

    private boolean isSelectQuery(String upperQuery) {
        return upperQuery.startsWith(Constants.MYSQL_QUERY_SELECT) ||
                upperQuery.startsWith(Constants.MYSQL_QUERY_SHOW) ||
                upperQuery.startsWith(Constants.MYSQL_QUERY_DESCRIBE) ||
                upperQuery.startsWith(Constants.MYSQL_QUERY_DESC) ||
                upperQuery.startsWith(Constants.MYSQL_QUERY_EXPLAIN);
    }

    /**
     * Check if query should have LIMIT clause applied (only SELECT queries)
     */
    private boolean shouldApplyLimit(String upperQuery) {
        return upperQuery.startsWith(Constants.MYSQL_QUERY_SELECT);
    }

    private boolean isTransactionControl(String upperQuery) {
        return upperQuery.startsWith(Constants.SQL_TRANSACTION_START) ||
                upperQuery.startsWith(Constants.SQL_TRANSACTION_COMMIT) ||
                upperQuery.startsWith(Constants.SQL_TRANSACTION_ROLLBACK) ||
                upperQuery.startsWith(Constants.SQL_TRANSACTION_BEGIN);
    }

    private Map<String, Object> processQueryExecution(PreparedStatement statement, String query, QueryType queryType)
            throws SQLException {

        switch (queryType) {
            case SELECT:
                return executeSelectQuery(statement, query);
            case TRANSACTION_CONTROL:
                return executeTransactionControl(statement, query);
            case DML:
                return executeDmlQuery(statement, query);
            default:
                throw new IllegalArgumentException(Constants.getMessage("error.unknown.query.type") + queryType);
        }
    }

    private Map<String, Object> executeSelectQuery(PreparedStatement statement, String query) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            List<String> headers = extractHeaders(metaData);
            List<Map<String, Object>> data = extractData(resultSet, metaData);
            return createQueryResult(query, headers, data);
        }
    }

    private Map<String, Object> executeTransactionControl(PreparedStatement statement, String query)
            throws SQLException {
        statement.executeUpdate();
        List<String> headers = List.of(Constants.QUERY_RESULT_STATUS_HEADER);
        List<Map<String, Object>> data = List.of(Map.of(Constants.QUERY_RESULT_STATUS_HEADER,
                query.toUpperCase() + Constants.QUERY_RESULT_EXECUTED_SUCCESSFULLY));

        return createQueryResult(query, headers, data);
    }

    private Map<String, Object> executeDmlQuery(PreparedStatement statement, String query) throws SQLException {
        int affectedRows = statement.executeUpdate();
        List<String> headers = List.of(Constants.QUERY_RESULT_AFFECTED_ROWS_HEADER);
        List<Map<String, Object>> data = List.of(Map.of(Constants.QUERY_RESULT_AFFECTED_ROWS_HEADER, affectedRows));

        return createQueryResult(query, headers, data);
    }

    private List<String> extractHeaders(ResultSetMetaData metaData) throws SQLException {
        List<String> headers = new ArrayList<>();
        int columnCount = metaData.getColumnCount();

        for (int i = 1; i <= columnCount; i++) {
            headers.add(metaData.getColumnName(i));
        }

        return headers;
    }

    private List<Map<String, Object>> extractData(ResultSet resultSet, ResultSetMetaData metaData) throws SQLException {
        List<Map<String, Object>> data = new ArrayList<>();
        int columnCount = metaData.getColumnCount();

        while (resultSet.next()) {
            Map<String, Object> row = new HashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnName(i);
                Object value = resultSet.getObject(i);
                row.put(columnName, value);
            }
            data.add(row);
        }

        return data;
    }

    private Map<String, Object> createQueryResult(String query, List<String> headers, List<Map<String, Object>> data) {
        Map<String, Object> queryResult = new HashMap<>();
        queryResult.put(Constants.QUERY_RESULT_FIELD_QUERY, query);
        queryResult.put(Constants.QUERY_RESULT_FIELD_HEADERS, headers);
        queryResult.put("data", data);
        return queryResult;
    }

    private Map<String, Object> handleQueryError(String query, SQLException e, boolean isChangeRequest) {
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put(Constants.QUERY_RESULT_FIELD_QUERY, query);
        errorResult.put(Constants.QUERY_RESULT_FIELD_HEADERS, List.of(Constants.QUERY_RESULT_ERROR_HEADER));
        errorResult.put("data", List.of(Map.of(Constants.QUERY_RESULT_ERROR_HEADER, e.getMessage())));
        errorResult.put("hasError", true);

        if (!isChangeRequest) {
            throw new DatabaseAccessException(Constants.getMessage("error.executing.query") + e.getMessage(), e);
        }

        return errorResult;
    }

    private Map<String, Object> createFinalResult(List<Map<String, Object>> allResults) {
        Map<String, Object> finalResult = new HashMap<>();
        finalResult.put("results", allResults);
        finalResult.put("totalQueries", allResults.size());
        return finalResult;
    }

    private enum QueryType {
        SELECT,
        DML,
        TRANSACTION_CONTROL
    }

    /**
     * Fetch actual user access information by querying the target database directly
     * This method provides real-time information about database users and their
     * permissions
     * 
     * @param asset           The asset containing database connection information
     * @param adminCredential The credential to use for database connection
     * @return AssetAccessDTO containing all users and their permissions in the
     *         database
     * @throws DatabaseAccessException for database connection or access issues
     */
    public AssetAccessDTO fetchAssetUserAccess(Asset asset, AssetCredential adminCredential) {

        log.info("=== Starting fetchAssetUserAccess for asset: {} (ID: {}) ===",
                asset.getName(), asset.getId());
        log.info("Using credential username: {}", adminCredential.getUsername());

        // Validate inputs
        validateInputs(asset, adminCredential);

        String decryptedPassword = databaseConnectionUtils.decryptCredentialPassword(adminCredential);

        // Create temporary credential for connection
        AssetCredential tempCredential = databaseConnectionUtils.createTempCredential(asset, adminCredential,
                decryptedPassword);

        List<UserAccessDTO> users = fetchUsersByDatabaseType(asset, tempCredential);

        // Filter out internally generated usernames
        Set<String> internallyGeneratedUsernames = getInternallyGeneratedUsernames(asset);
        // Normalize usernames for comparison (remove quotes, lowercase for case-insensitive comparison)
        Set<String> normalizedInternallyGeneratedUsernames = internallyGeneratedUsernames.stream()
                .map(this::normalizeUsernameForComparison)
                .collect(Collectors.toSet());
        
        List<UserAccessDTO> filteredUsers = users.stream()
                .filter(user -> {
                    String username = user.getUsername();
                    if (username == null) {
                        return false;
                    }
                    // Normalize username for comparison
                    String normalizedUsername = normalizeUsernameForComparison(username);
                    
                    // Filter out usernames from database (case-insensitive, quote-insensitive comparison)
                    if (normalizedInternallyGeneratedUsernames.contains(normalizedUsername)) {
                        return false;
                    }
                    // Filter out usernames ending with 4 digits (pattern used for internally generated usernames)
                    return username.matches(".*\\d{4}$");
                })
                .toList();

        log.info("Successfully fetched access information for {} users ({} after filtering internally generated)", 
                users.size(), filteredUsers.size());
        return new AssetAccessDTO(
                asset.getId(),
                asset.getName(),
                asset.getDatabaseType().toString(),
                filteredUsers);
    }
    
    /**
     * Get set of internally generated usernames for an asset
     * These are usernames created by the system with isTemporaryPassword = true
     * Excludes asset owner credentials
     */
    private Set<String> getInternallyGeneratedUsernames(Asset asset) {
        return assetCredentialsRepository.findByAssetId(asset.getId()).stream()
                .filter(cred -> !Roles.ASSET_OWNER.getOriginalName().equals(cred.getUserAccessType()))
                .map(AssetCredential::getUsername)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
    
    /**
     * Normalize username for comparison by removing quotes and converting to lowercase
     * This handles PostgreSQL case sensitivity and quote differences
     */
    private String normalizeUsernameForComparison(String username) {
        if (username == null) {
            return "";
        }
        // Remove quotes (single, double, backticks) and convert to lowercase for case-insensitive comparison
        return username.replace("\"", "")
                      .replace("'", "")
                      .replace("`", "")
                      .toLowerCase()
                      .trim();
    }

    /**
     * Validates the inputs for fetchAssetUserAccess method
     */
    private void validateInputs(Asset asset, AssetCredential adminCredential) {
        if (asset == null) {
            throw new DatabaseAccessException(Constants.getMessage("error.asset.cannot.be.null"), null);
        }

        if (adminCredential == null) {
            throw new DatabaseAccessException(Constants.getMessage("error.admin.credential.cannot.be.null"), null);
        }

        if (!databaseConnectionUtils.hasValidPassword(adminCredential)) {
            log.warn("Credential ID: {} has no password", adminCredential.getId());
            throw new DatabaseAccessException(Constants.getMessage("error.user.no.access.to.asset"), null);
        }
    }

    /**
     * Fetches users based on the database type
     */
    private List<UserAccessDTO> fetchUsersByDatabaseType(Asset asset, AssetCredential tempCredential) {
        // Handle MongoDB separately since it doesn't use JDBC Connection
        if (asset.getDatabaseType() == DatabaseType.MONGODB) {
            return fetchMongoDBUserAccess(tempCredential);
        }
        
        try (Connection connection = databaseConnectionUtils.getConnectionFromAssetCredential(tempCredential)) {
            log.debug("Successfully connected to database: {}", asset.getDatabaseType());

            DatabaseUserAccessFetcher fetcher = switch (asset.getDatabaseType()) {
                case MYSQL -> new MySQLUserAccessFetcher();
                case POSTGRESQL -> new PostgreSQLUserAccessFetcher();
                case SQLSERVER -> new SQLServerUserAccessFetcher();
                case ORACLE -> new OracleUserAccessFetcher();
                default -> throw new DatabaseAccessException(
                        Constants.getMessage(Constants.ERROR_UNSUPPORTED_DATABASE_TYPE) + asset.getDatabaseType(),
                        null);
            };

            return fetcher.fetchUserAccess(connection, asset.getDatabaseName());
        } catch (SQLException e) {
            log.error("Database connection or query error for asset ID: {} - {}",
                    asset.getId(), e.getMessage());
            throw new DatabaseAccessException(Constants.getMessage("error.failed.to.connect.to.database"), e);
        }
    }
    
    /**
     * Fetches MongoDB user access information
     */
    private List<UserAccessDTO> fetchMongoDBUserAccess(AssetCredential tempCredential) {
        List<UserAccessDTO> userAccessList = new ArrayList<>();
        try {
            MongoDatabase mongoDb = MongoDBConnectionUtils
                    .getMongoDatabase(tempCredential);
            
            // Get all users
            List<String> users = MongoDBConnectionUtils.listMongoDBUsers(mongoDb);
            
            // For each user, get their roles/permissions
            for (String username : users) {
                UserAccessDTO userAccess = new UserAccessDTO();
                userAccess.setUsername(username);
                userAccess.setGrantee(username);
                
                // Get user roles from MongoDB
                List<PermissionDTO> permissions = getMongoDBUserPermissions(tempCredential, username);
                userAccess.setPermissions(permissions);
                
                userAccessList.add(userAccess);
            }
        } catch (Exception e) {
            log.error("Error fetching MongoDB user access: {}", e.getMessage(), e);
            throw new DatabaseAccessException("Failed to fetch MongoDB user access: " + e.getMessage(), e);
        }
        return userAccessList;
    }
    
    /**
     * Gets MongoDB user permissions/roles
     * Queries both the admin database and the current database to find all user roles
     * (root users are typically created in the admin database)
     */
    private List<PermissionDTO> getMongoDBUserPermissions(
            AssetCredential credential, String username) {
        List<PermissionDTO> permissions = new ArrayList<>();
        try {
            MongoClient mongoClient = MongoDBConnectionUtils.createMongoClient(credential);
            try {
                List<String> databasesToCheck = buildDatabasesToCheck(credential);
                permissions = queryUserPermissionsFromDatabases(mongoClient, databasesToCheck, username);
            } finally {
                mongoClient.close();
            }
            
            if (permissions.isEmpty()) {
                log.warn("No MongoDB user permissions found for user: {} in admin or current database", username);
            }
        } catch (Exception e) {
            log.warn("Error getting MongoDB user permissions for {}: {}", username, e.getMessage(), e);
        }
        return permissions;
    }
    
    /**
     * Builds list of databases to check for user permissions
     */
    private List<String> buildDatabasesToCheck(AssetCredential credential) {
        List<String> databasesToCheck = new ArrayList<>();
        databasesToCheck.add(Constants.MONGODB_ADMIN_DATABASE); // Always check admin database first
        
        String currentDbName = credential.getAsset().getDatabaseName();
        if (currentDbName == null || currentDbName.isEmpty()) {
            currentDbName = Constants.MONGODB_ADMIN_DATABASE;
        }
        
        if (!Constants.MONGODB_ADMIN_DATABASE.equals(currentDbName)) {
            databasesToCheck.add(currentDbName); // Also check current database
        }
        
        return databasesToCheck;
    }
    
    /**
     * Queries user permissions from multiple databases
     */
    private List<PermissionDTO> queryUserPermissionsFromDatabases(
            MongoClient mongoClient, List<String> databasesToCheck, String username) {
        List<PermissionDTO> permissions = new ArrayList<>();
        
        for (String dbName : databasesToCheck) {
            List<PermissionDTO> dbPermissions = queryUserPermissionsFromDatabase(mongoClient, dbName, username);
            if (!dbPermissions.isEmpty()) {
                permissions.addAll(dbPermissions);
                // Found user in this database, no need to check others
                break;
            }
        }
        
        return permissions;
    }
    
    /**
     * Queries user permissions from a single database
     */
    private List<PermissionDTO> queryUserPermissionsFromDatabase(
            MongoClient mongoClient, String dbName, String username) {
        try {
            MongoDatabase db = mongoClient.getDatabase(dbName);
            Document command = new Document(Constants.MONGODB_COMMAND_USERS_INFO, username);
            Document result = db.runCommand(command);
            List<Document> userList = result.getList(Constants.MONGODB_FIELD_USERS, Document.class);
            
            if (userList == null || userList.isEmpty()) {
                return new ArrayList<>();
            }
            
            Document user = userList.get(0);
            return extractPermissionsFromUser(user, dbName, username);
        } catch (Exception e) {
            log.debug("Error querying user info from database {}: {}", dbName, e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Extracts permissions from user document
     */
    private List<PermissionDTO> extractPermissionsFromUser(Document user, String dbName, String username) {
        List<PermissionDTO> permissions = new ArrayList<>();
        List<Document> roles = user.getList(Constants.MONGODB_FIELD_ROLES, Document.class);
        
        if (roles == null) {
            return permissions;
        }
        
        for (Document role : roles) {
            PermissionDTO permission = createPermissionFromRole(role, dbName, username);
            permissions.add(permission);
        }
        
        return permissions;
    }
    
    /**
     * Creates PermissionDTO from role document
     */
    private PermissionDTO createPermissionFromRole(Document role, String dbName, String username) {
        String roleName = role.getString("role");
        String roleDb = role.getString("db");
        
        PermissionDTO permission = new PermissionDTO();
        permission.setType(roleName);
        permission.setScope(roleDb != null ? roleDb : dbName);
        permission.setObjectType("DATABASE");
        permission.setGrantable(false); // MongoDB roles are not grantable in the same way as SQL
        
        log.debug("Found MongoDB role: {} on database: {} for user: {}", 
                roleName, permission.getScope(), username);
        
        return permission;
    }

    /**
     * CRITICAL SECURITY OPERATION: Lock out users in the asset database
     * This method is coded defensively to prevent any security vulnerabilities
     * 
     * @param asset           The asset containing database connection information
     * @param adminCredential The admin credential to use for the operation
     * @param lockAllUsers    If true, locks all database users. If false, only
     *                        locks Hagrids users.
     * @return Map containing operation results and statistics
     * @throws DatabaseAccessException for any database operation errors
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> lockoutAssetUsers(Asset asset, AssetCredential adminCredential, boolean lockAllUsers) {
        log.error("=== CRITICAL SECURITY OPERATION: Database user lockout starting ===");
        log.error("Asset: {} (ID: {}), Database: {}, Lock all users: {}",
                asset.getName(), asset.getId(), asset.getDatabaseType(), lockAllUsers);

        validateLockoutInputs(asset, adminCredential);

        String decryptedPassword = databaseConnectionUtils.decryptCredentialPassword(adminCredential);
        AssetCredential tempCredential = databaseConnectionUtils.createTempCredential(asset, adminCredential,
                decryptedPassword);

        Map<String, Object> result = new HashMap<>();
        List<String> lockedUsers = new ArrayList<>();
        List<String> failedUsers = new ArrayList<>();
        List<String> skippedUsers = new ArrayList<>();

        // Handle MongoDB separately since it doesn't use JDBC Connection
        if (asset.getDatabaseType() == DatabaseType.MONGODB) {
            return lockMongoDBUsers(asset, tempCredential, adminCredential, lockAllUsers);
        }

        try (Connection connection = databaseConnectionUtils.getConnectionFromAssetCredential(tempCredential)) {
            List<String> usersToLock = getUsersToLock(connection, asset, lockAllUsers);
            String currentAdminUser = adminCredential.getUsername();

            log.warn("Found {} users to potentially lock. Admin user '{}' will be protected.",
                    usersToLock.size(), currentAdminUser);

            for (String username : usersToLock) {
                if (isProtectedUser(username, currentAdminUser, asset.getDatabaseType())) {
                    skippedUsers.add(username + " (protected)");
                    log.info("PROTECTED: Skipping admin/system user: {}", username);
                    continue;
                }

                if (lockDatabaseUser(connection, username, asset.getDatabaseType())) {
                    lockedUsers.add(username);
                    log.warn("LOCKED: Successfully locked user: {}", username);
                } else {
                    failedUsers.add(username);
                    log.error("FAILED: Could not lock user: {}", username);
                }
            }

            OperationMetadata metadata = new OperationMetadata(
                    Constants.LOCKOUT_OPERATION_LOCKOUT, lockAllUsers, true, usersToLock.size());
            UserLists userLists = new UserLists(lockedUsers, failedUsers, skippedUsers);
            FieldKeys fieldKeys = new FieldKeys("lockedUsers", "lockedCount");
            LockoutResultData lockoutData = new LockoutResultData(metadata, userLists, fieldKeys);
            buildLockoutResult(result, asset, lockoutData);

            log.error("Lockout operation completed: {} locked, {} failed, {} skipped",
                    lockedUsers.size(), failedUsers.size(), skippedUsers.size());

            return result;

        } catch (SQLException e) {
            log.error("CRITICAL ERROR during user lockout for asset ID: {}", asset.getId(), e);
            throw new DatabaseAccessException(Constants.getMessage("error.failed.user.lockout") + e.getMessage(), e);
        }
    }

    /**
     * CRITICAL SECURITY OPERATION: Unlock users in the asset database
     * This method is coded defensively to prevent any security vulnerabilities
     * 
     * @param asset           The asset containing database connection information
     * @param adminCredential The admin credential to use for the operation
     * @param unlockAllUsers  If true, unlocks all database users. If false, only
     *                        unlocks Hagrids users.
     * @return Map containing operation results and statistics
     * @throws DatabaseAccessException for any database operation errors
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> unlockAssetUsers(Asset asset, AssetCredential adminCredential, boolean unlockAllUsers) {
        log.error("=== CRITICAL SECURITY OPERATION: Database user unlock starting ===");
        log.error("Asset: {} (ID: {}), Database: {}, Unlock all users: {}",
                asset.getName(), asset.getId(), asset.getDatabaseType(), unlockAllUsers);

        validateLockoutInputs(asset, adminCredential);

        String decryptedPassword = databaseConnectionUtils.decryptCredentialPassword(adminCredential);
        AssetCredential tempCredential = databaseConnectionUtils.createTempCredential(asset, adminCredential,
                decryptedPassword);

        Map<String, Object> result = new HashMap<>();
        List<String> unlockedUsers = new ArrayList<>();
        List<String> failedUsers = new ArrayList<>();
        List<String> skippedUsers = new ArrayList<>();

        // Handle MongoDB separately since it doesn't use JDBC Connection
        if (asset.getDatabaseType() == DatabaseType.MONGODB) {
            return unlockMongoDBUsers(asset, tempCredential, adminCredential, unlockAllUsers);
        }

        try (Connection connection = databaseConnectionUtils.getConnectionFromAssetCredential(tempCredential)) {
            List<String> usersToUnlock = getUsersToUnlock(connection, asset, unlockAllUsers);
            String currentAdminUser = adminCredential.getUsername();

            log.warn("Found {} users to potentially unlock. Admin user '{}' noted.",
                    usersToUnlock.size(), currentAdminUser);

            for (String username : usersToUnlock) {
                if (unlockDatabaseUser(connection, username, asset.getDatabaseType())) {
                    unlockedUsers.add(username);
                    log.warn("UNLOCKED: Successfully unlocked user: {}", username);
                } else {
                    failedUsers.add(username);
                    log.error("FAILED: Could not unlock user: {}", username);
                }
            }

            buildUnlockResult(result, asset, unlockAllUsers, usersToUnlock.size(), 
                    unlockedUsers, failedUsers, skippedUsers);

            log.error("Unlock operation completed: {} unlocked, {} failed, {} skipped",
                    unlockedUsers.size(), failedUsers.size(), skippedUsers.size());

            return result;

        } catch (SQLException e) {
            log.error("CRITICAL ERROR during user unlock for asset ID: {}", asset.getId(), e);
            throw new DatabaseAccessException(Constants.getMessage("error.failed.user.unlock") + e.getMessage(), e);
        }
    }


    /**
     * Builds the lockout result map with common fields
     */
    private void buildLockoutResult(Map<String, Object> result, Asset asset, LockoutResultData data) {
        result.put(Constants.LOCKOUT_FIELD_SUCCESS, true);
        result.put(Constants.LOCKOUT_FIELD_OPERATION, data.getMetadata().getOperation());
        result.put(Constants.LOCKOUT_FIELD_ASSET_ID, asset.getId());
        result.put(Constants.LOCKOUT_FIELD_ASSET_NAME, asset.getName());
        result.put(Constants.LOCKOUT_FIELD_DATABASE_TYPE, asset.getDatabaseType().toString());
        String allUsersKey = Constants.LOCKOUT_OPERATION_LOCKOUT.equals(data.getMetadata().getOperation()) 
                ? Constants.LOCKOUT_FIELD_LOCK_ALL_USERS 
                : Constants.LOCKOUT_FIELD_UNLOCK_ALL_USERS;
        result.put(allUsersKey, data.getMetadata().isAllUsersFlag());
        result.put(Constants.LOCKOUT_FIELD_ASSET_LOCKED, data.getMetadata().isAssetLocked());
        result.put(Constants.QUERY_RESULT_FIELD_TOTAL_USERS, data.getMetadata().getTotalUsers());
        result.put(data.getFieldKeys().getProcessedUsersKey(), data.getUserLists().getProcessedUsers());
        result.put(Constants.LOCKOUT_FIELD_FAILED_USERS, data.getUserLists().getFailedUsers());
        result.put(Constants.LOCKOUT_FIELD_SKIPPED_USERS, data.getUserLists().getSkippedUsers());
        result.put(data.getFieldKeys().getProcessedCountKey(), data.getUserLists().getProcessedUsers().size());
        result.put(Constants.LOCKOUT_FIELD_FAILED_COUNT, data.getUserLists().getFailedUsers().size());
        result.put(Constants.LOCKOUT_FIELD_SKIPPED_COUNT, data.getUserLists().getSkippedUsers().size());
    }

    /**
     * Builds the unlock result map with common fields
     */
    private void buildUnlockResult(Map<String, Object> result, Asset asset, boolean unlockAllUsers,
            int totalUsers, List<String> unlockedUsers, List<String> failedUsers, List<String> skippedUsers) {
        result.put(Constants.LOCKOUT_FIELD_SUCCESS, true);
        result.put(Constants.LOCKOUT_FIELD_OPERATION, Constants.LOCKOUT_OPERATION_UNLOCK);
        result.put(Constants.LOCKOUT_FIELD_ASSET_ID, asset.getId());
        result.put(Constants.LOCKOUT_FIELD_ASSET_NAME, asset.getName());
        result.put(Constants.LOCKOUT_FIELD_DATABASE_TYPE, asset.getDatabaseType().toString());
        result.put(Constants.LOCKOUT_FIELD_UNLOCK_ALL_USERS, unlockAllUsers);
        result.put(Constants.LOCKOUT_FIELD_ASSET_LOCKED, false);
        result.put(Constants.QUERY_RESULT_FIELD_TOTAL_USERS, totalUsers);
        result.put("unlockedUsers", unlockedUsers);
        result.put(Constants.LOCKOUT_FIELD_FAILED_USERS, failedUsers);
        result.put(Constants.LOCKOUT_FIELD_SKIPPED_USERS, skippedUsers);
        result.put("unlockedCount", unlockedUsers.size());
        result.put(Constants.LOCKOUT_FIELD_FAILED_COUNT, failedUsers.size());
        result.put(Constants.LOCKOUT_FIELD_SKIPPED_COUNT, skippedUsers.size());
    }

    /**
     * Validates inputs for lockout/unlock operations
     */
    private void validateLockoutInputs(Asset asset, AssetCredential adminCredential) {
        if (asset == null) {
            throw new IllegalArgumentException(Constants.getMessage("error.asset.cannot.be.null"));
        }

        if (adminCredential == null) {
            throw new IllegalArgumentException(Constants.getMessage("error.admin.credential.cannot.be.null"));
        }

        if (!databaseConnectionUtils.hasValidPassword(adminCredential)) {
            throw new SecurityException(Constants.getMessage("error.admin.credential.no.password"));
        }

        if (asset.getDatabaseType() == null) {
            throw new IllegalArgumentException(Constants.getMessage("error.asset.database.type.null"));
        }
    }

    /**
     * Gets list of users to lock based on the lockAllUsers flag
     */
    private List<String> getUsersToLock(Connection connection, Asset asset, boolean lockAllUsers) throws SQLException {
        if (lockAllUsers) {
            return getAllDatabaseUsers(connection, asset.getDatabaseType());
        } else {
            return getHagridUsers(asset);
        }
    }

    /**
     * Gets list of users to unlock based on the unlockAllUsers flag
     */
    private List<String> getUsersToUnlock(Connection connection, Asset asset, boolean unlockAllUsers)
            throws SQLException {
        if (unlockAllUsers) {
            return getLockedDatabaseUsers(connection, asset.getDatabaseType());
        } else {
            return getLockedHagridUsers(connection, asset);
        }
    }

    /**
     * Gets all database users based on database type
     */
    private List<String> getAllDatabaseUsers(Connection connection, DatabaseType databaseType) throws SQLException {
        String query = switch (databaseType) {
            case MYSQL ->
                "SELECT User FROM mysql.user WHERE User NOT IN ('mysql.sys', 'mysql.session', 'mysql.infoschema')";
            case POSTGRESQL -> "SELECT usename FROM pg_user WHERE usename NOT LIKE 'pg_%' AND usename != 'postgres'";
            case ORACLE ->
                "SELECT username FROM dba_users WHERE username NOT IN ('SYS', 'SYSTEM', 'DBSNMP', 'SYSMAN', 'OUTLN')";
            case SQLSERVER ->
                "SELECT name FROM sys.database_principals WHERE type = 'S' AND name NOT IN ('dbo', 'guest', 'INFORMATION_SCHEMA', 'sys')";
            default -> throw new DatabaseAccessException(
                    Constants.getMessage("error.unsupported.db.type.user.listing") + databaseType, null);
        };

        return executeUserQuery(connection, query);
    }

    /**
     * Gets locked database users based on database type
     */
    private List<String> getLockedDatabaseUsers(Connection connection, DatabaseType databaseType) throws SQLException {
        String query = switch (databaseType) {
            case MYSQL ->
                "SELECT User FROM mysql.user WHERE account_locked = 'Y' AND User NOT IN ('mysql.sys', 'mysql.session', 'mysql.infoschema')";
            case POSTGRESQL ->
                "SELECT rolname FROM pg_roles WHERE NOT rolcanlogin AND rolname NOT LIKE 'pg_%' AND rolname != 'postgres'";
            case ORACLE ->
                "SELECT username FROM dba_users WHERE account_status = 'LOCKED' AND username NOT IN ('SYS', 'SYSTEM', 'DBSNMP', 'SYSMAN', 'OUTLN')";
            case SQLSERVER ->
                "SELECT name FROM sys.database_principals p JOIN sys.sql_logins l ON p.sid = l.sid WHERE l.is_disabled = 1 AND p.type = 'S'";
            default -> throw new DatabaseAccessException(
                    Constants.getMessage("error.unsupported.db.type.locked.user.listing") + databaseType, null);
        };

        return executeUserQuery(connection, query);
    }

    /**
     * Gets Hagrids users (users managed by our system)
     */
    private List<String> getHagridUsers(Asset asset) throws SQLException {
        // Get users from our asset_credentials table for this asset
        List<AssetCredential> credentials = assetCredentialsRepository.findByAssetId(asset.getId());
        return credentials.stream()
                .map(AssetCredential::getUsername)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * Gets locked Hagrids users
     */
    private List<String> getLockedHagridUsers(Connection connection, Asset asset) throws SQLException {
        List<String> hagridUsers = getHagridUsers(asset);
        List<String> lockedUsers = getLockedDatabaseUsers(connection, asset.getDatabaseType());

        return hagridUsers.stream()
                .filter(lockedUsers::contains)
                .toList();
    }

    /**
     * Executes a user query and returns the list of usernames
     */
    private List<String> executeUserQuery(Connection connection, String query) throws SQLException {
        List<String> users = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(query);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String username = rs.getString(1);
                if (username != null && !username.trim().isEmpty()) {
                    users.add(username.trim());
                }
            }
        }

        return users;
    }

    /**
     * Checks if a user should be protected from lockout operations
     */
    private boolean isProtectedUser(String username, String currentAdminUser, DatabaseType databaseType) {
        if (username == null || username.trim().isEmpty()) {
            return true;
        }

        // Always protect the current admin user
        if (username.equals(currentAdminUser)) {
            return true;
        }

        // Protect system users based on database type
        return switch (databaseType) {
            case MYSQL -> username.startsWith("mysql.") ||
                    username.equals("root") ||
                    username.equals("debian-sys-maint");

            case POSTGRESQL -> username.startsWith("pg_") ||
                    username.equals("postgres") ||
                    username.equals("postgresql");

            case ORACLE -> Set.of("SYS", "SYSTEM", "DBSNMP", "SYSMAN", "OUTLN", "ORACLE_OCM")
                    .contains(username.toUpperCase());

            case SQLSERVER -> Set.of("sa", "dbo", "guest", "INFORMATION_SCHEMA", "sys", "NT AUTHORITY\\SYSTEM")
                    .contains(username);

            case MONGODB -> username.equals(Constants.MONGODB_ADMIN_DATABASE) || username.equals("root") || username.startsWith("__");

            default -> false;
        };
    }

    /**
     * Locks a specific database user
     */
    private boolean lockDatabaseUser(Connection connection, String username, DatabaseType databaseType) {
        String lockSql = switch (databaseType) {
            case MYSQL -> "ALTER USER ?@'%' ACCOUNT LOCK";
            case POSTGRESQL -> "ALTER USER ? NOLOGIN";
            case ORACLE -> "ALTER USER ? ACCOUNT LOCK";
            case SQLSERVER -> "ALTER LOGIN ? DISABLE";
            case MONGODB -> throw new DatabaseAccessException(
                    "MongoDB user locking should be handled via MongoDB API", null);
            default -> throw new DatabaseAccessException(
                    Constants.getMessage("error.unsupported.db.type.user.locking") + databaseType, null);
        };

        return executeUserLockUnlockOperation(connection, lockSql, username, "lock");
    }
    
    /**
     * Locks MongoDB users
     */
    private Map<String, Object> lockMongoDBUsers(Asset asset, AssetCredential tempCredential, 
            AssetCredential adminCredential, boolean lockAllUsers) {
        Map<String, Object> result = new HashMap<>();
        List<String> lockedUsers = new ArrayList<>();
        List<String> failedUsers = new ArrayList<>();
        List<String> skippedUsers = new ArrayList<>();
        
        try {
            MongoDatabase mongoDb = MongoDBConnectionUtils
                    .getMongoDatabase(tempCredential);
            
            List<String> usersToLock;
            if (lockAllUsers) {
                usersToLock = getAllMongoDBUsers(mongoDb);
            } else {
                usersToLock = getHagridUsers(asset);
            }
            
            String currentAdminUser = adminCredential.getUsername();
            log.warn("Found {} MongoDB users to potentially lock. Admin user '{}' will be protected.",
                    usersToLock.size(), currentAdminUser);
            
            for (String username : usersToLock) {
                if (isProtectedUser(username, currentAdminUser, asset.getDatabaseType())) {
                    skippedUsers.add(username + " (protected)");
                    log.info("PROTECTED: Skipping admin/system user: {}", username);
                    continue;
                }
                
                if (lockMongoDBUser(mongoDb, username)) {
                    lockedUsers.add(username);
                } else {
                    failedUsers.add(username);
                }
            }
            
            OperationMetadata metadata = new OperationMetadata(
                    Constants.LOCKOUT_OPERATION_LOCKOUT, lockAllUsers, true, usersToLock.size());
            UserLists userLists = new UserLists(lockedUsers, failedUsers, skippedUsers);
            FieldKeys fieldKeys = new FieldKeys("lockedUsers", "lockedCount");
            LockoutResultData lockoutData = new LockoutResultData(metadata, userLists, fieldKeys);
            buildLockoutResult(result, asset, lockoutData);
        } catch (Exception e) {
            log.error("Error locking MongoDB users: {}", e.getMessage(), e);
            throw new DatabaseAccessException("Failed to lock MongoDB users: " + e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * Unlocks MongoDB users
     */
    private Map<String, Object> unlockMongoDBUsers(Asset asset, AssetCredential tempCredential,
            AssetCredential adminCredential, boolean unlockAllUsers) {
        Map<String, Object> result = new HashMap<>();
        List<String> unlockedUsers = new ArrayList<>();
        List<String> failedUsers = new ArrayList<>();
        List<String> skippedUsers = new ArrayList<>();
        
        try {
            MongoDatabase mongoDb = MongoDBConnectionUtils
                    .getMongoDatabase(tempCredential);
            
            List<String> usersToUnlock;
            if (unlockAllUsers) {
                usersToUnlock = getLockedMongoDBUsers(mongoDb);
            } else {
                List<String> hagridUsers = getHagridUsers(asset);
                List<String> lockedUsers = getLockedMongoDBUsers(mongoDb);
                usersToUnlock = hagridUsers.stream()
                        .filter(lockedUsers::contains)
                        .toList();
            }
            
            String currentAdminUser = adminCredential.getUsername();
            log.warn("Found {} MongoDB users to potentially unlock. Admin user '{}' noted.",
                    usersToUnlock.size(), currentAdminUser);
            
            for (String username : usersToUnlock) {
                if (unlockMongoDBUser(mongoDb, username)) {
                    unlockedUsers.add(username);
                } else {
                    failedUsers.add(username);
                }
            }
            
            buildUnlockResult(result, asset, unlockAllUsers, usersToUnlock.size(), 
                    unlockedUsers, failedUsers, skippedUsers);
        } catch (Exception e) {
            log.error("Error unlocking MongoDB users: {}", e.getMessage(), e);
            throw new DatabaseAccessException("Failed to unlock MongoDB users: " + e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * Gets all MongoDB users
     */
    private List<String> getAllMongoDBUsers(MongoDatabase mongoDb) {
        try {
            return MongoDBConnectionUtils.listMongoDBUsers(mongoDb);
        } catch (Exception e) {
            log.error("Error getting all MongoDB users: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Gets locked MongoDB users (users with no roles)
     */
    private List<String> getLockedMongoDBUsers(MongoDatabase mongoDb) {
        List<String> lockedUsers = new ArrayList<>();
        try {
            List<String> allUsers = MongoDBConnectionUtils.listMongoDBUsers(mongoDb);
            for (String username : allUsers) {
                if (MongoDBConnectionUtils.isUserLocked(mongoDb, username)) {
                    lockedUsers.add(username);
                }
            }
        } catch (Exception e) {
            log.error("Error getting locked MongoDB users: {}", e.getMessage(), e);
        }
        return lockedUsers;
    }

    /**
     * Unlocks a specific database user
     */
    private boolean unlockDatabaseUser(Connection connection, String username, DatabaseType databaseType) {
        String unlockSql = switch (databaseType) {
            case MONGODB -> throw new DatabaseAccessException(
                    "MongoDB user unlocking should be handled via MongoDB API", null);
            case MYSQL -> "ALTER USER ?@'%' ACCOUNT UNLOCK";
            case POSTGRESQL -> "ALTER USER ? LOGIN";
            case ORACLE -> "ALTER USER ? ACCOUNT UNLOCK";
            case SQLSERVER -> "ALTER LOGIN ? ENABLE";
            default -> throw new DatabaseAccessException(
                    Constants.getMessage("error.unsupported.db.type.user.unlocking") + databaseType, null);
        };

        return executeUserLockUnlockOperation(connection, unlockSql, username, Constants.LOCKOUT_OPERATION_UNLOCK);
    }

    /**
     * Executes the lock/unlock SQL operation for a user
     * Uses proper escaping to prevent SQL injection
     */
    private boolean executeUserLockUnlockOperation(Connection connection, String sql, String username,
            String operation) {
        try {
            // Validate username to prevent SQL injection
            if (!CommonUtils.isValidUsername(username)) {
                log.error("Invalid username detected for {} operation: {}", operation, username);
                return false;
            }

            // For PostgreSQL and SQL Server, we need to use string formatting instead of
            // parameter binding
            if (sql.contains("ALTER USER ?")) {
                // PostgreSQL - use proper escaping
                String escapedUsername = CommonUtils.escapePostgresqlIdentifier(username);
                sql = sql.replace("ALTER USER ?", "ALTER USER \"" + escapedUsername + "\"");

                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate(sql);
                }
            } else if (sql.contains("ALTER LOGIN ?")) {
                // SQL Server - use proper escaping
                String escapedUsername = CommonUtils.escapeSqlServerIdentifier(username);
                sql = sql.replace("ALTER LOGIN ?", "ALTER LOGIN [" + escapedUsername + "]");

                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate(sql);
                }
            } else {
                // MySQL and Oracle - use parameterized queries
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, username);
                    stmt.executeUpdate();
                }
            }

            log.debug("Successfully executed {} operation for user: {}", operation, username);
            return true;

        } catch (SQLException e) {
            log.error("Failed to {} user '{}': {}", operation, username, e.getMessage());
            return false;
        }
    }

    /**
     * Validate Asset Owner permissions to ensure they have sufficient access to
     * grant permissions to others
     */
    private PermissionValidationResult validateAssetOwnerPermissions(AssetCredential credential) {
        try {
            // Test basic connectivity and permissions
            List<String> missingPermissions = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            List<String> existingPermissions = new ArrayList<>();
            List<String> grantablePermissions = new ArrayList<>();

            // Test 1: Can connect to the database and perform permission analysis
            return performPermissionValidation(credential, missingPermissions, warnings, existingPermissions,
                    grantablePermissions);

        } catch (Exception e) {
            log.error("Error validating Asset Owner permissions: {}", e.getMessage(), e);
            return new PermissionValidationResult(false,
                    "Permission validation failed: " + e.getMessage(),
                    new ArrayList<>());
        }
    }

    /**
     * Perform permission validation tests on the database connection
     */
    private PermissionValidationResult performPermissionValidation(AssetCredential credential,
            List<String> missingPermissions,
            List<String> warnings,
            List<String> existingPermissions,
            List<String> grantablePermissions) {
        // Handle MongoDB separately since it doesn't use JDBC
        if (credential.getAsset().getDatabaseType() == DatabaseType.MONGODB) {
            return performMongoDBPermissionValidation(credential, missingPermissions, warnings, 
                    existingPermissions, grantablePermissions);
        }

        try (Connection connection = databaseConnectionUtils.getConnectionFromAssetCredential(credential)) {
            if (connection == null) {
                return new PermissionValidationResult(false,
                        "Cannot establish database connection. Please verify credentials.",
                        new ArrayList<>());
            }

            // Test 2: Check if user can see information_schema (basic metadata access)
            if (!canAccessInformationSchema(connection)) {
                missingPermissions.add("INFORMATION_SCHEMA access");
                warnings.add("Cannot access database metadata - may not be able to see available tables/views");
            } else {
                existingPermissions.add("INFORMATION_SCHEMA access");
            }

            // Test 3: Analyze existing permissions and what can be granted
            PermissionAnalysisResult permissionAnalysis = analyzeExistingPermissions(connection,
                    credential.getAsset().getDatabaseType(), credential.getUsername());
            existingPermissions.addAll(permissionAnalysis.getExistingPermissions());
            grantablePermissions.addAll(permissionAnalysis.getGrantablePermissions());

            // Test 4: Check if user can grant permissions (varies by database type)
            if (!canGrantPermissions(connection, credential.getAsset().getDatabaseType(), credential.getUsername())) {
                missingPermissions.add("GRANT permissions");
                warnings.add("Cannot grant permissions to other users - may not be able to approve access requests");
            } else {
                existingPermissions.add("GRANT permissions");
            }

            // Test 5: Check if user can create/modify users (for some database types)
            if (!canManageUsers(connection, credential.getAsset().getDatabaseType(), credential.getUsername())) {
                missingPermissions.add(Constants.PERMISSION_USER_MANAGEMENT_PERMISSIONS);
                warnings.add("Cannot create or modify database users - may limit access management capabilities");
            } else {
                existingPermissions.add(Constants.PERMISSION_USER_MANAGEMENT_PERMISSIONS);
            }

            // Test 6: Check if user has administrative privileges
            if (!hasAdministrativePrivileges(connection, credential.getAsset().getDatabaseType(),
                    credential.getUsername())) {
                warnings.add(
                        "Limited administrative privileges - some advanced access management features may not be available");
            } else {
                existingPermissions.add("Administrative privileges");
            }

            // Test 7: Analyze specific table/view permissions
            TablePermissionAnalysis tableAnalysis = analyzeTablePermissions(connection,
                    credential.getAsset().getDatabaseType(), credential.getUsername());
            existingPermissions.addAll(tableAnalysis.getAccessibleTables());
            grantablePermissions.addAll(tableAnalysis.getGrantableTables());

            // Add warnings based on permission analysis
            addPermissionAnalysisWarnings(warnings, existingPermissions, grantablePermissions, tableAnalysis);

            // Determine if permissions are sufficient
            boolean isSufficient = missingPermissions.isEmpty() && !grantablePermissions.isEmpty();
            String warningMessage = buildEnhancedWarningMessage(missingPermissions, warnings, existingPermissions,
                    grantablePermissions);

            return new PermissionValidationResult(isSufficient, warningMessage, warnings);

        } catch (SQLException e) {
            return new PermissionValidationResult(false,
                    "Database connection failed: " + e.getMessage(),
                    new ArrayList<>());
        }
    }

    /**
     * Perform MongoDB permission validation tests
     */
    private PermissionValidationResult performMongoDBPermissionValidation(AssetCredential credential,
            List<String> missingPermissions,
            List<String> warnings,
            List<String> existingPermissions,
            List<String> grantablePermissions) {
        try {
            // Create decrypted temp credential for MongoDB connection
            // Handle both encrypted and already-decrypted passwords
            // Credentials from AuthController or OwnerAssetController may already be decrypted
            
            // Check if password exists first
            if (credential.getPassword() == null || credential.getPassword().trim().isEmpty()) {
                log.error("MongoDB credential password is empty for credential ID: {}", credential.getId());
                return new PermissionValidationResult(false,
                        "Credential password is empty. Please verify your credentials are correctly configured.",
                        new ArrayList<>());
            }
            
            // Check if password looks encrypted (Base64 format)
            // Encrypted passwords are Base64 encoded, so they have a specific format
            // If password doesn't look encrypted, use it as-is (already decrypted)
            String password = credential.getPassword();
            boolean looksEncrypted = isPasswordEncrypted(password);
            
            AssetCredential tempCredentialResult = createMongoDBTempCredential(credential, password, looksEncrypted);
            if (tempCredentialResult == null) {
                return new PermissionValidationResult(false,
                        "Failed to decrypt credential password. Please verify your credentials are correctly configured.",
                        new ArrayList<>());
            }
            AssetCredential tempCredential = tempCredentialResult;
            
            // Test 1: Can connect to MongoDB
            MongoDatabase mongoDb = MongoDBConnectionUtils.getMongoDatabase(tempCredential);
            
            // Test connection by running a simple command
            mongoDb.runCommand(new Document("ping", 1));
            existingPermissions.add("Database connection");

            // Test 2: Check if user can list collections (basic metadata access)
            checkMongoDBCollectionAccess(mongoDb, missingPermissions, warnings, existingPermissions);

            // Test 3: Analyze existing permissions and what can be granted
            PermissionAnalysisResult permissionAnalysis = analyzeMongoDBPermissions(tempCredential, credential.getUsername());
            existingPermissions.addAll(permissionAnalysis.getExistingPermissions());
            grantablePermissions.addAll(permissionAnalysis.getGrantablePermissions());

            // Test 4-6: Check role-based permissions
            checkMongoDBRolePermissions(existingPermissions, grantablePermissions, warnings);

            // Determine if permissions are sufficient for MongoDB
            boolean isSufficient = determineMongoDBPermissionSufficiency(missingPermissions, existingPermissions);
            String warningMessage = buildEnhancedWarningMessage(missingPermissions, warnings, existingPermissions,
                    grantablePermissions);

            return new PermissionValidationResult(isSufficient, warningMessage, warnings);

        } catch (Exception e) {
            log.error("Error validating MongoDB permissions: {}", e.getMessage(), e);
            // Check if this is a credential/decryption error
            if (databaseConnectionUtils.isCredentialRelatedError(e)) {
                return new PermissionValidationResult(false,
                        "Credential decryption failed. Please verify your credentials are correctly configured.",
                        new ArrayList<>());
            }
            return new PermissionValidationResult(false,
                    "MongoDB connection failed: " + e.getMessage(),
                    new ArrayList<>());
        }
    }

    /**
     * Creates a temporary credential for MongoDB connection, handling both encrypted and plain text passwords
     */
    private AssetCredential createMongoDBTempCredential(AssetCredential credential, String password, boolean looksEncrypted) {
        if (looksEncrypted) {
            try {
                return databaseConnectionUtils.createDecryptedTempCredential(credential);
            } catch (Exception e) {
                log.error("Failed to decrypt MongoDB credential password for credential ID: {}", 
                        credential.getId(), e);
                return null;
            }
        } else {
            log.debug("Password appears to be already decrypted for credential ID: {}", credential.getId());
            return databaseConnectionUtils.createTempCredential(
                    credential.getAsset(), credential, password);
        }
    }

    /**
     * Checks if user can list MongoDB collections
     */
    private void checkMongoDBCollectionAccess(MongoDatabase mongoDb, List<String> missingPermissions,
            List<String> warnings, List<String> existingPermissions) {
        try {
            List<String> collections = MongoDBConnectionUtils.listCollections(mongoDb);
            if (collections.isEmpty()) {
                warnings.add("No collections found - database may be empty");
            } else {
                existingPermissions.add("List collections access");
            }
        } catch (Exception e) {
            missingPermissions.add("List collections access");
            warnings.add("Cannot list collections - may not be able to see available collections");
        }
    }

    /**
     * Checks MongoDB role-based permissions (grant roles, manage users, admin privileges)
     */
    private void checkMongoDBRolePermissions(List<String> existingPermissions, 
            List<String> grantablePermissions, List<String> warnings) {
        // Check if user has admin roles (userAdmin, dbOwner, or root)
        boolean hasAdminRoles = hasMongoDBAdminRoles(existingPermissions);
        
        // Test 4: Check if user can grant roles
        checkMongoDBGrantRolesPermission(existingPermissions, grantablePermissions, warnings, hasAdminRoles);
        
        // Test 5: Check if user can manage users
        checkMongoDBUserManagementPermission(existingPermissions, warnings, hasAdminRoles);
        
        // Test 6: Check if user has administrative privileges
        checkMongoDBAdminPrivileges(existingPermissions, warnings);
    }

    /**
     * Checks if user has MongoDB admin roles (userAdmin, dbOwner, or root)
     */
    private boolean hasMongoDBAdminRoles(List<String> existingPermissions) {
        return existingPermissions.stream()
                .anyMatch(p -> p.contains(Constants.MONGODB_ROLE_USER_ADMIN) || 
                              p.contains(Constants.MONGODB_ROLE_DB_OWNER) || 
                              p.contains("root"));
    }

    /**
     * Checks if user can grant roles to other users
     */
    private void checkMongoDBGrantRolesPermission(List<String> existingPermissions, 
            List<String> grantablePermissions, List<String> warnings, boolean hasAdminRoles) {
        if (!hasAdminRoles) {
            warnings.add("Cannot grant roles to other users - may not be able to approve access requests. Consider granting userAdmin or dbOwner role for full access management capabilities.");
        } else {
            existingPermissions.add("GRANT roles permissions");
            grantablePermissions.addAll(existingPermissions.stream()
                    .filter(p -> p.contains(Constants.MONGODB_ROLE_USER_ADMIN) || 
                               p.contains(Constants.MONGODB_ROLE_DB_OWNER) || 
                               p.contains("root"))
                    .toList());
        }
    }

    /**
     * Checks if user can manage users
     */
    private void checkMongoDBUserManagementPermission(List<String> existingPermissions, 
            List<String> warnings, boolean hasAdminRoles) {
        if (!hasAdminRoles) {
            warnings.add("Cannot create or modify database users - may limit access management capabilities. Consider granting userAdmin or dbOwner role.");
        } else {
            existingPermissions.add(Constants.PERMISSION_USER_MANAGEMENT_PERMISSIONS);
        }
    }

    /**
     * Checks if user has administrative privileges
     */
    private void checkMongoDBAdminPrivileges(List<String> existingPermissions, List<String> warnings) {
        boolean hasAdminPrivileges = existingPermissions.stream()
                .anyMatch(p -> p.contains("root") || 
                             p.contains("dbOwner") ||
                             p.contains("dbAdminAnyDatabase") || 
                             p.contains("userAdminAnyDatabase"));
        if (!hasAdminPrivileges) {
            warnings.add("Limited administrative privileges - some advanced access management features may not be available");
        } else {
            existingPermissions.add("Administrative privileges");
        }
    }

    /**
     * Determines if MongoDB permissions are sufficient
     */
    private boolean determineMongoDBPermissionSufficiency(List<String> missingPermissions, 
            List<String> existingPermissions) {
        boolean hasBasicAccess = existingPermissions.stream()
                .anyMatch(p -> p.contains("read") || 
                             p.contains("readWrite") || 
                             p.contains("dbAdmin") ||
                             p.contains(Constants.MONGODB_ROLE_DB_OWNER) ||
                             p.contains(Constants.MONGODB_ROLE_USER_ADMIN) ||
                             p.contains("root"));
        return missingPermissions.isEmpty() && hasBasicAccess;
    }

    /**
     * Locks a MongoDB user
     */
    private boolean lockMongoDBUser(MongoDatabase mongoDb, String username) {
        try {
            MongoDBConnectionUtils.lockUser(mongoDb, username);
            log.warn("LOCKED: Successfully locked MongoDB user: {}", username);
            return true;
        } catch (Exception e) {
            log.error("FAILED: Could not lock MongoDB user {}: {}", username, e.getMessage());
            return false;
        }
    }

    /**
     * Unlocks a MongoDB user
     */
    private boolean unlockMongoDBUser(MongoDatabase mongoDb, String username) {
        try {
            MongoDBConnectionUtils.unlockUser(mongoDb, username);
            log.warn("UNLOCKED: Successfully unlocked MongoDB user: {}", username);
            return true;
        } catch (Exception e) {
            log.error("FAILED: Could not unlock MongoDB user {}: {}", username, e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a MongoDB user should be skipped during cleanup
     */
    private boolean shouldSkipMongoDBUser(String username, String credentialUsername) {
        return username.equals(credentialUsername) || 
               isProtectedUser(username, credentialUsername, DatabaseType.MONGODB);
    }

    /**
     * Drops a MongoDB temporary user
     */
    private boolean dropMongoDBTemporaryUser(MongoDatabase mongoDb, String username) {
        try {
            MongoDBConnectionUtils.dropUser(mongoDb, username);
            log.info("Successfully dropped MongoDB temporary user: {}", username);
            return true;
        } catch (Exception e) {
            log.error("Failed to drop MongoDB user {}: {}", username, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if password appears to be encrypted (Base64 format)
     * Encrypted passwords are Base64 encoded strings with specific characteristics
     * 
     * @param password The password to check
     * @return true if password looks encrypted, false if it appears to be plain text
     */
    private boolean isPasswordEncrypted(String password) {
        if (password == null || password.trim().isEmpty()) {
            return false;
        }
        
        // Encrypted passwords are Base64 encoded, so they:
        // 1. Only contain Base64 characters (A-Z, a-z, 0-9, +, /, =)
        // 2. Have length that's a multiple of 4 (Base64 padding)
        // 3. When decoded, have at least 12 bytes (IV) + some encrypted data
        
        // Check if password contains non-Base64 characters (excluding = for padding)
        // Common password characters that aren't in Base64: spaces, special chars like @, #, $, etc.
        String base64Pattern = "^[A-Za-z0-9+/]*={0,2}$";
        if (!password.matches(base64Pattern)) {
            // Contains non-Base64 characters - likely plain text
            return false;
        }
        
        // Check if it's valid Base64 and has reasonable length
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(password);
            // Encrypted data should have at least 12 bytes (IV) + some encrypted content
            // Minimum reasonable length would be around 16-20 bytes
            return decoded.length >= 12;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Check if user can access information_schema
     */
    private boolean canAccessInformationSchema(Connection connection) {
        try {
            String query = "SELECT COUNT(*) FROM information_schema.tables LIMIT 1";
            try (PreparedStatement stmt = connection.prepareStatement(query);
                    ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.debug("Cannot access information_schema: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if user can grant permissions
     */
    private boolean canGrantPermissions(Connection connection, DatabaseType databaseType, String username) {
        try {
            switch (databaseType) {
                case MYSQL:
                    return checkMySQLGrantPermissions(connection, username);
                case POSTGRESQL:
                    return checkPostgreSQLGrantPermissions(connection, username);
                case ORACLE:
                    return checkOracleGrantPermissions(connection, username);
                case SQLSERVER:
                    return checkSQLServerGrantPermissions(connection, username);
                default:
                    return true; // Assume sufficient for unknown types
            }
        } catch (SQLException e) {
            log.debug("Cannot verify grant permissions for user {}: {}", username, e.getMessage());
        }
        return false;
    }

    /**
     * Check MySQL grant permissions
     */
    private boolean checkMySQLGrantPermissions(Connection connection, String username) throws SQLException {
        // For MySQL, check both user_privileges and table_privileges with flexible
        // grantee matching
        String query = "SELECT COUNT(*) FROM (" +
                "SELECT IS_GRANTABLE FROM information_schema.user_privileges " +
                "WHERE GRANTEE LIKE CONCAT('''', ?, '''@%') AND IS_GRANTABLE = 'YES' " +
                "UNION ALL " +
                "SELECT IS_GRANTABLE FROM information_schema.table_privileges " +
                "WHERE GRANTEE LIKE CONCAT('''', ?, '''@%') AND IS_GRANTABLE = 'YES' LIMIT 1" +
                ") AS grant_check";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, username);
            stmt.setString(2, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Check PostgreSQL grant permissions
     */
    private boolean checkPostgreSQLGrantPermissions(Connection connection, String username) throws SQLException {
        String query = "SELECT COUNT(*) FROM information_schema.table_privileges WHERE grantee = ? AND is_grantable = 'YES' LIMIT 1";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Check Oracle grant permissions
     */
    private boolean checkOracleGrantPermissions(Connection connection, String username) throws SQLException {
        String query = "SELECT COUNT(*) FROM dba_tab_privs WHERE grantee = UPPER(?) AND grantable = 'YES' AND rownum = 1";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Check SQL Server grant permissions
     */
    private boolean checkSQLServerGrantPermissions(Connection connection, String username) throws SQLException {
        String query = "SELECT COUNT(*) FROM sys.database_permissions p JOIN sys.database_principals pr ON p.grantee_principal_id = pr.principal_id WHERE pr.name = ? AND p.state_desc = 'GRANT_WITH_GRANT_OPTION'";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Check if user can manage users
     */
    private boolean canManageUsers(Connection connection, DatabaseType databaseType, String username) {
        return executePermissionCheck(connection, databaseType, username, Constants.PERMISSION_CHECK_TYPE_USER_MANAGEMENT);
    }

    /**
     * Check if user has administrative privileges
     */
    private boolean hasAdministrativePrivileges(Connection connection, DatabaseType databaseType, String username) {
        return executePermissionCheck(connection, databaseType, username, "administrative");
    }

    /**
     * Generic method to execute permission checks with different privilege criteria
     */
    private boolean executePermissionCheck(Connection connection, DatabaseType databaseType, String username,
            String checkType) {
        try {
            String query = Constants.SQL_QUERY_SELECT_ZERO; // Default fallback query
            switch (databaseType) {
                case MYSQL:
                    if (Constants.PERMISSION_CHECK_TYPE_USER_MANAGEMENT.equals(checkType)) {
                        query = "SELECT COUNT(*) FROM information_schema.user_privileges " +
                                "WHERE GRANTEE LIKE CONCAT('''', ?, '''@%') AND PRIVILEGE_TYPE IN ('CREATE USER', 'SUPER') LIMIT 1";
                    } else { // administrative
                        query = "SELECT COUNT(*) FROM information_schema.user_privileges " +
                                "WHERE GRANTEE LIKE CONCAT('''', ?, '''@%') AND PRIVILEGE_TYPE IN ('SUPER', 'ALL PRIVILEGES') LIMIT 1";
                    }
                    break;
                case POSTGRESQL, ORACLE, SQLSERVER:
                    if (Constants.PERMISSION_CHECK_TYPE_USER_MANAGEMENT.equals(checkType)) {
                        switch (databaseType) {
                            case POSTGRESQL:
                                query = "SELECT CASE WHEN rolcreaterole = true THEN 1 ELSE 0 END FROM pg_roles WHERE rolname = ?";
                                break;
                            case ORACLE:
                                query = "SELECT COUNT(*) FROM dba_role_privs WHERE grantee = UPPER(?) AND granted_role IN ('DBA', 'RESOURCE') AND rownum = 1";
                                break;
                            case MONGODB:
                                // MongoDB user management check - check if user has userAdmin role
                                return false; // Simplified for now - MongoDB role checking requires different approach
                            case SQLSERVER:
                                query = "SELECT COUNT(*) FROM sys.database_role_members rm " +
                                        "JOIN sys.database_principals rp ON rm.role_principal_id = rp.principal_id " +
                                        "JOIN sys.database_principals mp ON rm.member_principal_id = mp.principal_id " +
                                        "WHERE mp.name = ? AND rp.name IN ('db_securityadmin', 'db_owner')";
                                break;
                            default:
                                query = Constants.SQL_QUERY_SELECT_ZERO; // Fallback query
                                break;
                        }
                    } else { // administrative
                        switch (databaseType) {
                            case POSTGRESQL:
                                query = "SELECT CASE WHEN rolsuper = true THEN 1 ELSE 0 END FROM pg_roles WHERE rolname = ?";
                                break;
                            case ORACLE:
                                query = "SELECT COUNT(*) FROM dba_role_privs WHERE grantee = UPPER(?) AND granted_role = 'DBA' AND rownum = 1";
                                break;
                            case SQLSERVER:
                                query = "SELECT COUNT(*) FROM sys.server_role_members rm " +
                                        "JOIN sys.server_principals rp ON rm.role_principal_id = rp.principal_id " +
                                        "JOIN sys.server_principals mp ON rm.member_principal_id = mp.principal_id " +
                                        "WHERE mp.name = ? AND rp.name = 'sysadmin'";
                                break;
                            default:
                                query = Constants.SQL_QUERY_SELECT_ZERO; // Fallback query
                                break;
                        }
                    }
                    break;
                default:
                    return true; // Assume sufficient for unknown types
            }

            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
            }
        } catch (SQLException e) {
            log.debug("Cannot verify {} permissions for user {}: {}", checkType, username, e.getMessage());
        }
        return false;
    }

    /**
     * Analyze existing permissions and what can be granted to others
     */
    private PermissionAnalysisResult analyzeExistingPermissions(Connection connection, DatabaseType databaseType,
            String username) {
        try {
            switch (databaseType) {
                case MYSQL:
                    return analyzeMySQLPermissions(connection, username);
                case POSTGRESQL:
                    return analyzePostgreSQLPermissions(connection, username);
                case ORACLE:
                    return analyzeOraclePermissions(connection, username);
                case SQLSERVER:
                    return analyzeSQLServerPermissions(connection, username);
                case MONGODB:
                    // MongoDB doesn't use JDBC Connection - use MongoDB-specific analysis
                    // This should not be called for MongoDB - use performMongoDBPermissionValidation instead
                    log.debug("MongoDB permission analysis requires MongoDB API access");
                    return new PermissionAnalysisResult(new ArrayList<>(), new ArrayList<>());
                default:
                    return new PermissionAnalysisResult(new ArrayList<>(), new ArrayList<>());
            }
        } catch (SQLException e) {
            log.debug("Cannot analyze existing permissions for user {}: {}", username, e.getMessage());
            return new PermissionAnalysisResult(new ArrayList<>(), new ArrayList<>());
        }
    }
    
    /**
     * Analyzes MongoDB user permissions
     */
    private PermissionAnalysisResult analyzeMongoDBPermissions(AssetCredential credential, String username) {
        List<String> existingPermissions = new ArrayList<>();
        List<String> grantablePermissions = new ArrayList<>();
        
        try {
            // Get user roles
            List<PermissionDTO> permissions = getMongoDBUserPermissions(credential, username);
            
            for (PermissionDTO permission : permissions) {
                String permissionStr = permission.getType() + " on " + permission.getScope();
                existingPermissions.add(permissionStr);
                
                // MongoDB roles that can grant roles to other users:
                // - userAdmin: can manage users and grant roles
                // - dbOwner: combines read, readWrite, dbAdmin, and userAdmin (can grant roles)
                // - root: superuser (can grant roles)
                // Note: dbAdmin alone CANNOT grant roles, only userAdmin can
                if (permission.getType() != null && 
                    (permission.getType().equals(Constants.MONGODB_ROLE_USER_ADMIN) || 
                     permission.getType().equals(Constants.MONGODB_ROLE_DB_OWNER) ||
                     permission.getType().equals("root") ||
                     permission.getType().contains("userAdminAnyDatabase"))) {
                    grantablePermissions.add(permissionStr);
                }
            }
        } catch (Exception e) {
            log.debug("Cannot analyze MongoDB permissions for user {}: {}", username, e.getMessage());
        }
        
        return new PermissionAnalysisResult(existingPermissions, grantablePermissions);
    }

    /**
     * Analyze MySQL permissions
     */
    private PermissionAnalysisResult analyzeMySQLPermissions(Connection connection, String username)
            throws SQLException {
        String query = "SELECT PRIVILEGE_TYPE, IS_GRANTABLE, 'GLOBAL' as scope FROM information_schema.user_privileges "
                +
                "WHERE GRANTEE LIKE CONCAT('''', ?, '''@%') " +
                "UNION ALL " +
                "SELECT PRIVILEGE_TYPE, IS_GRANTABLE, CONCAT(TABLE_SCHEMA, '.', TABLE_NAME) as scope FROM information_schema.table_privileges "
                +
                "WHERE GRANTEE LIKE CONCAT('''', ?, '''@%') " +
                "UNION ALL " +
                "SELECT PRIVILEGE_TYPE, IS_GRANTABLE, TABLE_SCHEMA as scope FROM information_schema.schema_privileges "
                +
                "WHERE GRANTEE LIKE CONCAT('''', ?, '''@%')";

        return executePermissionQuery(connection, query, username, 3, false);
    }

    /**
     * Analyze PostgreSQL permissions
     */
    private PermissionAnalysisResult analyzePostgreSQLPermissions(Connection connection, String username)
            throws SQLException {
        String query = "SELECT privilege_type, is_grantable, CONCAT(table_schema, '.', table_name) as scope " +
                "FROM information_schema.table_privileges WHERE grantee = ? " +
                "UNION ALL " +
                "SELECT privilege_type, is_grantable, object_name as scope " +
                "FROM information_schema.usage_privileges WHERE grantee = ?";

        return executePermissionQuery(connection, query, username, 2, false);
    }

    /**
     * Analyze Oracle permissions
     */
    private PermissionAnalysisResult analyzeOraclePermissions(Connection connection, String username)
            throws SQLException {
        String query = "SELECT privilege, grantable, CONCAT(owner, '.', table_name) as scope " +
                "FROM dba_tab_privs WHERE grantee = UPPER(?) " +
                "UNION ALL " +
                "SELECT privilege, grantable, 'SYSTEM' as scope " +
                "FROM dba_sys_privs WHERE grantee = UPPER(?)";

        return executePermissionQuery(connection, query, username, 2, false);
    }

    /**
     * Analyze SQL Server permissions
     */
    private PermissionAnalysisResult analyzeSQLServerPermissions(Connection connection, String username)
            throws SQLException {
        String query = "SELECT p.permission_name, p.state_desc, CONCAT(SCHEMA_NAME(t.schema_id), '.', t.name) as scope "
                +
                "FROM sys.database_permissions p " +
                "JOIN sys.database_principals pr ON p.grantee_principal_id = pr.principal_id " +
                "LEFT JOIN sys.tables t ON p.major_id = t.object_id " +
                "WHERE pr.name = ? AND t.name IS NOT NULL " +
                "UNION ALL " +
                "SELECT p.permission_name, p.state_desc, 'DATABASE' as scope " +
                "FROM sys.database_permissions p " +
                "JOIN sys.database_principals pr ON p.grantee_principal_id = pr.principal_id " +
                "WHERE pr.name = ? AND p.major_id = 0";

        return executePermissionQuery(connection, query, username, 2, true);
    }

    /**
     * Execute permission analysis query (common logic for all database types)
     */
    private PermissionAnalysisResult executePermissionQuery(Connection connection, String query,
            String username, int parameterCount,
            boolean isSQLServer) throws SQLException {
        List<String> existingPermissions = new ArrayList<>();
        List<String> grantablePermissions = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            // Set username parameter for each placeholder
            for (int i = 1; i <= parameterCount; i++) {
                stmt.setString(i, username);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    processPermissionRow(rs, existingPermissions, grantablePermissions, isSQLServer);
                }
            }
        }

        return new PermissionAnalysisResult(existingPermissions, grantablePermissions);
    }

    /**
     * Process a permission result set row
     */
    private void processPermissionRow(ResultSet rs, List<String> existingPermissions,
            List<String> grantablePermissions, boolean isSQLServer) throws SQLException {
        String privilege = rs.getString(1);
        String scope = rs.getString(3);

        boolean isGrantable;
        if (isSQLServer) {
            isGrantable = "GRANT_WITH_GRANT_OPTION".equals(rs.getString(2)) || "GRANT".equals(rs.getString(2));
        } else {
            String grantableValue = rs.getString(2);
            isGrantable = "YES".equalsIgnoreCase(grantableValue) || "true".equalsIgnoreCase(grantableValue);
        }

        // Create detailed permission string with scope
        String detailedPermission = privilege + " ON " + scope;
        existingPermissions.add(detailedPermission);
        if (isGrantable) {
            grantablePermissions.add(detailedPermission);
        }
    }

    /**
     * Analyze table-level permissions
     */
    private TablePermissionAnalysis analyzeTablePermissions(Connection connection, DatabaseType databaseType,
            String username) {
        List<String> accessibleTables = new ArrayList<>();
        List<String> grantableTables = new ArrayList<>();

        try {
            String query;
            switch (databaseType) {
                case MYSQL:
                    query = "SELECT CONCAT(TABLE_SCHEMA, '.', TABLE_NAME) as full_table_name, " +
                            "GROUP_CONCAT(DISTINCT PRIVILEGE_TYPE ORDER BY PRIVILEGE_TYPE SEPARATOR ', ') as privileges, "
                            +
                            "MAX(CASE WHEN IS_GRANTABLE = 'YES' THEN 1 ELSE 0 END) as can_grant " +
                            "FROM information_schema.table_privileges " +
                            "WHERE GRANTEE LIKE CONCAT('''', ?, '''@%') " +
                            "GROUP BY TABLE_SCHEMA, TABLE_NAME";
                    break;
                case POSTGRESQL:
                    query = "SELECT CONCAT(table_schema, '.', table_name) as full_table_name, " +
                            "STRING_AGG(DISTINCT privilege_type, ', ' ORDER BY privilege_type) as privileges, " +
                            "MAX(CASE WHEN is_grantable = 'YES' THEN 1 ELSE 0 END) as can_grant " +
                            "FROM information_schema.table_privileges " +
                            "WHERE grantee = ? " +
                            "GROUP BY table_schema, table_name";
                    break;
                case ORACLE:
                    query = "SELECT CONCAT(owner, '.', table_name) as full_table_name, " +
                            "LISTAGG(DISTINCT privilege, ', ') WITHIN GROUP (ORDER BY privilege) as privileges, " +
                            "MAX(CASE WHEN grantable = 'YES' THEN 1 ELSE 0 END) as can_grant " +
                            "FROM dba_tab_privs " +
                            "WHERE grantee = UPPER(?) " +
                            "GROUP BY owner, table_name";
                    break;
                case SQLSERVER:
                    query = "SELECT CONCAT(SCHEMA_NAME(t.schema_id), '.', t.name) as full_table_name, " +
                            "STRING_AGG(DISTINCT p.permission_name, ', ') WITHIN GROUP (ORDER BY p.permission_name) as privileges, "
                            +
                            "MAX(CASE WHEN p.state_desc = 'GRANT_WITH_GRANT_OPTION' THEN 1 ELSE 0 END) as can_grant " +
                            "FROM sys.tables t " +
                            "JOIN sys.database_permissions p ON t.object_id = p.major_id " +
                            "JOIN sys.database_principals pr ON p.grantee_principal_id = pr.principal_id " +
                            "WHERE pr.name = ? " +
                            "GROUP BY t.schema_id, t.name";
                    break;
                default:
                    return new TablePermissionAnalysis(new ArrayList<>(), new ArrayList<>());
            }

            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, username);
                try (ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        String fullTableName = rs.getString(1);
                        String privileges = rs.getString(2);
                        boolean canGrant = rs.getInt(3) > 0;

                        // Create detailed table permission string
                        String detailedTableAccess = fullTableName + " (" + privileges + ")";
                        accessibleTables.add(detailedTableAccess);

                        if (canGrant) {
                            grantableTables.add(detailedTableAccess);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log.debug("Cannot analyze table permissions for user {}: {}", username, e.getMessage());
        }

        return new TablePermissionAnalysis(accessibleTables, grantableTables);
    }

    /**
     * Add warnings based on permission analysis
     */
    private void addPermissionAnalysisWarnings(List<String> warnings, List<String> existingPermissions,
            List<String> grantablePermissions,
            TablePermissionAnalysis tableAnalysis) {

        // Check if user has very limited permissions
        if (existingPermissions.size() < 3) {
            warnings.add("Very limited database permissions detected - may not be able to effectively manage access");
        }

        // Check if user can grant any permissions
        if (grantablePermissions.isEmpty()) {
            warnings.add("Cannot grant any permissions to other users - will not be able to approve access requests");
        } else if (grantablePermissions.size() < 3) {
            warnings.add("Limited grant permissions - may not be able to provide full access to accessors");
        }

        // Check table access
        if (tableAnalysis.getAccessibleTables().isEmpty()) {
            warnings.add("No table access detected - cannot provide database access to accessors");
        } else if (tableAnalysis.getGrantableTables().isEmpty()) {
            warnings.add("Cannot grant table access to other users - limited access management capabilities");
        }

        // Check for critical missing permissions
        List<String> criticalPermissions = Arrays.asList("SELECT", "INSERT", "UPDATE", "DELETE");
        List<String> missingCritical = criticalPermissions.stream()
                .filter(perm -> !existingPermissions.contains(perm))
                .toList();

        if (!missingCritical.isEmpty()) {
            warnings.add("Missing critical permissions: " + String.join(", ", missingCritical) +
                    " - may not be able to provide complete database access");
        }
    }

    /**
     * Build enhanced warning message with permission analysis
     */
    private String buildEnhancedWarningMessage(List<String> missingPermissions, List<String> warnings,
            List<String> existingPermissions, List<String> grantablePermissions) {
        StringBuilder message = new StringBuilder();

        if (!missingPermissions.isEmpty()) {
            message.append("Access level is insufficient to be an asset owner. Missing permissions: ");
            message.append(String.join(", ", missingPermissions));
            message.append(". You may not be able to grant all kinds of permission to others.");
        }

        if (!existingPermissions.isEmpty()) {
            if (!message.isEmpty()) {
                message.append(" ");
            }
            message.append("Current permissions: ").append(String.join(", ", existingPermissions));
        }

        if (!grantablePermissions.isEmpty()) {
            if (!message.isEmpty()) {
                message.append(" ");
            }
            message.append("Can grant: ").append(String.join(", ", grantablePermissions));
        }

        if (!warnings.isEmpty()) {
            if (!message.isEmpty()) {
                message.append(" ");
            }
            message.append("Additional warnings: ");
            message.append(String.join("; ", warnings));
        }

        return message.toString();
    }

    /**
     * Create objects JSON with warning information (with database type support)
     */
    private String createObjectsJsonWithWarning(PermissionValidationResult validationResult, DatabaseType databaseType) {
        try {
            Map<String, Object> objectsData = new HashMap<>();
            
            // MongoDB uses collections instead of tables
            if (databaseType == DatabaseType.MONGODB) {
                objectsData.put("collections", new ArrayList<>());
                objectsData.put("databases", new ArrayList<>());
            } else {
                objectsData.put("tables", new ArrayList<>());
                objectsData.put("views", new ArrayList<>());
                objectsData.put("procedures", new ArrayList<>());
            }
            
            objectsData.put("permission_warning", validationResult.getWarningMessage());
            objectsData.put("permission_sufficient", validationResult.isSufficient());
            objectsData.put("warnings", validationResult.getWarnings());

            return objectMapper.writeValueAsString(objectsData);
        } catch (Exception e) {
            log.error("Failed to create objects JSON with warning: {}", e.getMessage());
            return "{\"error\": \"Failed to process database objects due to permission issues\"}";
        }
    }

    /**
     * Save asset object with warning information
     */
    private void saveAssetObjectWithWarning(AssetCredential credential, String objectsJsonWithWarning,
            PermissionValidationResult validationResult) {
        try {
            AssetObject assetObject = assetObjectRepository.findByAssetCredential(credential)
                    .orElse(new AssetObject());

            assetObject.setAssetCredential(credential);
            assetObject.setAsset(credential.getAsset());
            assetObject.setObjectsJson(objectsJsonWithWarning);

            // Add metadata about permission issues
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("permission_validation_failed", true);
            metadata.put("validation_timestamp", System.currentTimeMillis());
            metadata.put("missing_permissions", validationResult.getWarnings());

            assetObjectRepository.save(assetObject);

            log.warn("Saved asset object with permission warning for Asset Owner {} on asset {}",
                    credential.getUsername(), credential.getAsset().getId());

        } catch (Exception e) {
            log.error("Failed to save asset object with warning: {}", e.getMessage(), e);
        }
    }

    /**
     * Result class for permission analysis
     */
    private static class PermissionAnalysisResult {
        private final List<String> existingPermissions;
        private final List<String> grantablePermissions;

        public PermissionAnalysisResult(List<String> existingPermissions, List<String> grantablePermissions) {
            this.existingPermissions = existingPermissions;
            this.grantablePermissions = grantablePermissions;
        }

        public List<String> getExistingPermissions() {
            return existingPermissions;
        }

        public List<String> getGrantablePermissions() {
            return grantablePermissions;
        }
    }

    /**
     * Securely escape password for PostgreSQL CREATE USER statement
     * This method provides comprehensive protection against SQL injection
     */
    private String escapePostgreSQLPassword(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null");
        }

        // Validate password length and characters
        if (password.length() > 1000) {
            throw new IllegalArgumentException("Password too long (max 1000 characters)");
        }

        // Check for dangerous characters that could be used for SQL injection
        if (password.contains("--") || password.contains("/*") || password.contains("*/") ||
                password.contains(";") || password.contains("\\") || password.contains("\0")) {
            throw new IllegalArgumentException("Password contains invalid characters");
        }

        // Escape single quotes by doubling them (PostgreSQL standard)
        // This is the only character that needs escaping in PostgreSQL string literals
        return password.replace("'", "''");
    }

    /**
     * Clean up temporary users created for a specific asset
     */
    public void cleanupTemporaryUsersForAsset(Asset asset) {
        try {
            log.info("Cleaning up temporary users for asset: {}", asset.getId());

            // Get all credentials for this asset
            User curUser = userService.getCurrentUser();

            AssetCredential credential = assetCredentialsRepository
                    .findByUserAndAssetAndUserAccessType(curUser, asset, Roles.ASSET_OWNER.getOriginalName())
                    .orElseThrow(
                            () -> new DatabaseAccessException("Can not find valid Credential for this asset", null));
            cleanupTemporaryUsersForCredential(credential);

            log.info("Successfully cleaned up temporary users for asset: {}", asset.getId());

        } catch (Exception e) {
            log.error("Error cleaning up temporary users for asset: {}", asset.getId(), e);
            throw new DatabaseAccessException("Failed to cleanup temporary users for asset: " + e.getMessage(), e);
        }
    }

    /**
     * Clean up temporary users for a specific credential
     */
    private void cleanupTemporaryUsersForCredential(AssetCredential credential) {
        DatabaseType databaseType = credential.getAsset().getDatabaseType();

        String decPsd = databaseConnectionUtils.decryptCredentialPassword(credential);
        credential.setPassword(decPsd);

        if (databaseType == DatabaseType.MONGODB) {
            cleanupMongoDBTemporaryUsers(credential);
        } else {
            try (Connection connection = databaseConnectionUtils.getConnectionFromAssetCredential(credential)) {
                switch (databaseType) {
                    case MYSQL:
                        cleanupMySQLTemporaryUsers(connection);
                        break;
                    case POSTGRESQL:
                        cleanupPostgreSQLTemporaryUsers(connection);
                        break;
                    case SQLSERVER:
                        cleanupSQLServerTemporaryUsers(connection);
                        break;
                    case ORACLE:
                        cleanupOracleTemporaryUsers(connection);
                        break;
                    default:
                        log.warn("Unsupported database type for cleanup: {}", databaseType);
                }

            } catch (SQLException e) {
                log.error("Error cleaning up temporary users for credential: {}", credential.getId(), e);
                throw new DatabaseAccessException("Failed to cleanup temporary users: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Clean up MySQL temporary users
     */
    private void cleanupMySQLTemporaryUsers(Connection connection) throws SQLException {
        String query = """
                    SELECT User FROM mysql.user
                    WHERE User LIKE ? AND Host = '%'
                """;
        String baseUserName = userService.getCurrentUser().getEmail().split("@")[0];
        String usernamePattern = baseUserName + "%";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, usernamePattern);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String username = rs.getString("User");
                    dropUser(connection, DatabaseType.MYSQL, username);
                }
            }
        }
    }

    /**
     * Clean up PostgreSQL temporary users
     */
    private void cleanupPostgreSQLTemporaryUsers(Connection connection)
            throws SQLException {
        String query = """
                    SELECT usename FROM pg_user
                    WHERE usename LIKE ?
                """;

        String baseUserName = userService.getCurrentUser().getEmail().split("@")[0];
        String usernamePattern = baseUserName + "%";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, usernamePattern);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String username = rs.getString("usename");
                    dropUser(connection, DatabaseType.POSTGRESQL, username);
                }
            }
        }
    }

    /**
     * Clean up SQL Server temporary users
     */
    private void cleanupSQLServerTemporaryUsers(Connection connection) throws SQLException {
        String query = """
                    SELECT name FROM sys.database_principals
                    WHERE name LIKE ? AND type = 'S'
                """;

        String baseUserName = userService.getCurrentUser().getEmail().split("@")[0];
        String usernamePattern = baseUserName + "%";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, usernamePattern);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String username = rs.getString("name");
                    dropUser(connection, DatabaseType.SQLSERVER, username);
                }
            }
        }
    }

    /**
     * Clean up Oracle temporary users
     */
    private void cleanupOracleTemporaryUsers(Connection connection) throws SQLException {
        String query = """
                    SELECT username FROM all_users
                    WHERE username LIKE ?
                """;

        String baseUserName = userService.getCurrentUser().getEmail().split("@")[0];
        String usernamePattern = baseUserName.toUpperCase() + "%";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, usernamePattern);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String username = rs.getString("username");
                    dropUser(connection, DatabaseType.ORACLE, username);
                }
            }
        }
    }

    /**
     * Clean up MongoDB temporary users
     * Removes all users from the database except:
     * - The credential's username (asset owner's username)
     * - Protected system users (admin, root, __*)
     */
    private void cleanupMongoDBTemporaryUsers(AssetCredential credential) {
        try {
            String credentialUsername = credential.getUsername();
            log.info("Cleaning up MongoDB temporary users for database: {}, keeping credential username: {}", 
                     credential.getAsset().getDatabaseName(), credentialUsername);
            
            MongoDatabase mongoDb = MongoDBConnectionUtils.getMongoDatabase(credential);
            List<String> allUsers = MongoDBConnectionUtils.listMongoDBUsers(mongoDb);
            
            int droppedCount = 0;
            int skippedCount = 0;
            
            for (String username : allUsers) {
                // Skip the credential's username and protected system users
                if (shouldSkipMongoDBUser(username, credentialUsername)) {
                    log.debug("Skipping user: {}", username);
                    skippedCount++;
                    continue;
                }
                
                // Drop the temporary user
                if (dropMongoDBTemporaryUser(mongoDb, username)) {
                    droppedCount++;
                }
            }
            
            log.info("MongoDB cleanup completed. Dropped {} users, skipped {} users (credential + protected)", 
                     droppedCount, skippedCount);
            
        } catch (Exception e) {
            log.error("Error cleaning up MongoDB temporary users for credential: {}", credential.getId(), e);
            throw new DatabaseAccessException("Failed to cleanup MongoDB temporary users: " + e.getMessage(), e);
        }
    }

    /**
     * Drop a user from the database
     */
    private void dropUser(Connection connection, DatabaseType databaseType, String username) {
        try {
            String dropUserSql = getDropUserSql(databaseType, username);

            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(dropUserSql);
                log.info("Successfully dropped user: {} from {}", username, databaseType);
            }

        } catch (SQLException e) {
            log.warn("Failed to drop user: {} from {}: {}", username, databaseType, e.getMessage());
            // Don't throw exception - continue with other users
        }
    }

    /**
     * Get the appropriate DROP USER SQL for the database type
     */
    private String getDropUserSql(DatabaseType databaseType, String username) {
        switch (databaseType) {
            case MYSQL:
                return "DROP USER IF EXISTS '" + username + "'@'%'";
            case POSTGRESQL:
                return "DROP USER IF EXISTS \"" + username + "\"";
            case SQLSERVER:
                return "DROP USER IF EXISTS [" + username + "]";
            case ORACLE:
                return "DROP USER " + username + " CASCADE";
            case MONGODB:
                // MongoDB doesn't use SQL - handled separately
                return "db.dropUser(\"" + username + "\")";
            default:
                throw new IllegalArgumentException("Unsupported database type: " + databaseType);
        }
    }

    /**
     * Result class for table permission analysis
     */
    private static class TablePermissionAnalysis {
        private final List<String> accessibleTables;
        private final List<String> grantableTables;

        public TablePermissionAnalysis(List<String> accessibleTables, List<String> grantableTables) {
            this.accessibleTables = accessibleTables;
            this.grantableTables = grantableTables;
        }

        public List<String> getAccessibleTables() {
            return accessibleTables;
        }

        public List<String> getGrantableTables() {
            return grantableTables;
        }
    }
    

}