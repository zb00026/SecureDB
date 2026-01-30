package com.verlake.dam.service.ai;

import com.mongodb.client.MongoDatabase;
import com.verlake.dam.entity.ai.AICategory;
import com.verlake.dam.entity.ai.FieldSuggestion;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.enums.DatabaseType;
import com.verlake.dam.exception.DatabaseAccessException;
import com.verlake.dam.service.assets.AssetService;
import com.verlake.dam.service.assets.DatabaseAccessService;
import com.verlake.dam.service.assets.common.DatabaseConnectionUtils;
import com.verlake.dam.service.assets.mongodb.MongoDBConnectionUtils;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.DatabaseQueryUtils;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class SchemaAnalysisService {
    
    private final DataSource dataSource;
    private final GeminiAIService geminiAIService;
    private final AISensitivePatternService patternService;
    private final AICategoryService categoryService;
    private final AssetService assetService;
    private final DatabaseAccessService databaseAccessService;
    private final DatabaseConnectionUtils databaseConnectionUtils;
    private final KeycloakService keycloakService;

    @Value("${ai.masking.enable.auto-detection:true}")
    private boolean enableAutoDetection;
    
    @Value("${ai.masking.enable.sample-data:false}")
    private boolean enableSampleData;
    
    @Value("${ai.masking.sample-data.timeout-ms:5000}")
    private int sampleDataTimeoutMs;
    
    public SchemaAnalysisService(DataSource dataSource, GeminiAIService geminiAIService, AISensitivePatternService patternService, AICategoryService categoryService, AssetService assetService, DatabaseAccessService databaseAccessService, DatabaseConnectionUtils databaseConnectionUtils, KeycloakService keycloakService) {
        this.dataSource = dataSource;
        this.geminiAIService = geminiAIService;
        this.patternService = patternService;
        this.categoryService = categoryService;
        this.assetService = assetService;
        this.databaseAccessService = databaseAccessService;
        this.databaseConnectionUtils = databaseConnectionUtils;
        this.keycloakService = keycloakService;
    }
    
    /**
     * Analyze asset schema and suggest masking policies
     * 
     * @param asset The asset to analyze
     * @param userContext User context for analysis
     * @return List of field suggestions
     * @throws DatabaseAccessException if database access fails
     */
    public List<FieldSuggestion> analyzeAssetSchema(Asset asset, String userContext) {
        return analyzeAssetSchema(asset, userContext, enableSampleData);
    }
    
    /**
     * Analyze asset schema and suggest masking policies with sample data option
     * 
     * @param asset The asset to analyze
     * @param userContext User context for analysis
     * @param includeSampleData Whether to collect sample data (performance impact)
     * @return List of field suggestions
     * @throws DatabaseAccessException if database access fails
     */
    public List<FieldSuggestion> analyzeAssetSchema(Asset asset, String userContext, boolean includeSampleData) {
        if (!enableAutoDetection) {
            log.info("Auto-detection is disabled");
            return new ArrayList<>();
        }
        
        try {
            List<TableSchema> tables = getAssetSchema(asset, includeSampleData);
            String schemaInfo = formatSchemaForAI(tables);
            
            // Get AI suggestions
            List<FieldSuggestion> aiSuggestions = geminiAIService.suggestMaskingPolicies(schemaInfo, userContext);

            // Enhance with rule-based detection
            List<FieldSuggestion> ruleSuggestions = detectSensitiveFieldsByRules(tables);

            // Context-driven narrowing by category (e.g., only SSN if user asked for SSN)
            AICategory focusCategory = inferCategoryFromContext(userContext);
            if (focusCategory != null) {
                aiSuggestions = filterByCategoryHeuristics(aiSuggestions, focusCategory);
                ruleSuggestions = filterByCategory(ruleSuggestions, focusCategory);
            }

            // If user specified particular tables/fields in the request, narrow to those
            aiSuggestions = filterByTableFieldFromText(aiSuggestions, userContext);
            ruleSuggestions = filterByTableFieldFromText(ruleSuggestions, userContext);

            // Merge and deduplicate suggestions
            return mergeAndRankSuggestions(aiSuggestions, ruleSuggestions);
            
        } catch (SQLException e) {
            log.error("Database error analyzing schema for asset {}: {}", asset.getId(), e.getMessage());
            throw new DatabaseAccessException("Failed to analyze asset schema for asset ID: " + asset.getId(), e);
        } catch (Exception e) {
            log.error("Unexpected error analyzing schema for asset {}: {}", asset.getId(), e.getMessage());
            throw new DatabaseAccessException("Unexpected error analyzing asset schema for asset ID: " + asset.getId(), e);
        }
    }
    
    /**
     * Get schema information for specific tables
     * 
     * @param asset The asset to analyze
     * @param tableNames List of table names to analyze
     * @param userContext User context for analysis
     * @return List of field suggestions
     * @throws DatabaseAccessException if database access fails
     */
    public List<FieldSuggestion> analyzeSpecificTables(Asset asset, List<String> tableNames, String userContext) {
        try {
            List<TableSchema> tables = getSpecificTablesSchema(asset, tableNames);
            String schemaInfo = formatSchemaForAI(tables);
            
            return geminiAIService.suggestMaskingPolicies(schemaInfo, userContext);
            
        } catch (SQLException e) {
            log.error("Database error analyzing specific tables for asset {}: {}", asset.getId(), e.getMessage());
            throw new DatabaseAccessException("Failed to analyze specific tables for asset ID: " + asset.getId(), e);
        } catch (Exception e) {
            log.error("Unexpected error analyzing specific tables for asset {}: {}", asset.getId(), e.getMessage());
            throw new DatabaseAccessException("Unexpected error analyzing specific tables for asset ID: " + asset.getId(), e);
        }
    }
    
    /**
     * Get database schema for an asset
     * 
     * @param asset The asset to get schema for
     * @param includeSampleData Whether to collect sample data for fields
     * @return List of table schemas
     * @throws SQLException if database access fails
     * @throws DatabaseAccessException if asset connection fails
     */
    private List<TableSchema> getAssetSchema(Asset asset, boolean includeSampleData) throws SQLException {
        // Handle MongoDB separately since it doesn't use JDBC
        if (asset.getDatabaseType() == DatabaseType.MONGODB) {
            return getMongoDBSchema(asset, includeSampleData);
        }
        
        List<TableSchema> tables = new ArrayList<>();
        
        try (Connection connection = getAssetConnection(asset)) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            // Get all tables for the database
            String catalog = asset.getDatabaseName();
            String schema = getSchemaName(asset.getDatabaseType());
            
            try (ResultSet tableResultSet = metaData.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                while (tableResultSet.next()) {
                    String tableName = tableResultSet.getString("TABLE_NAME");
                    
                    // Skip system tables
                    if (isSystemTable(tableName, asset.getDatabaseType())) {
                        continue;
                    }
                    
                    TableSchema table = createTableSchema(metaData, catalog, schema, tableName, asset, connection, includeSampleData);
                    tables.add(table);
                }
            }
        } catch (SQLException e) {
            log.error("Database error getting schema for asset {}: {}", asset.getId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error getting schema for asset {}: {}", asset.getId(), e.getMessage());
            throw new DatabaseAccessException("Failed to get asset schema for asset ID: " + asset.getId(), e);
        }
        
        return tables;
    }
    
    /**
     * Get schema for specific tables
     * 
     * @param asset The asset to get schema for
     * @param tableNames List of table names to get schema for
     * @return List of table schemas
     * @throws SQLException if database access fails
     * @throws DatabaseAccessException if asset connection fails
     */
    private List<TableSchema> getSpecificTablesSchema(Asset asset, List<String> tableNames) throws SQLException {
        // Handle MongoDB separately since it doesn't use JDBC
        if (asset.getDatabaseType() == DatabaseType.MONGODB) {
            return getMongoDBSchemaForCollections(asset, tableNames, enableSampleData);
        }
        
        List<TableSchema> tables = new ArrayList<>();
        
        try (Connection connection = getAssetConnection(asset)) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = asset.getDatabaseName();
            String schema = getSchemaName(asset.getDatabaseType());
            
            for (String tableName : tableNames) {
                TableSchema table = createTableSchema(metaData, catalog, schema, tableName, asset, connection, enableSampleData);
                tables.add(table);
            }
        } catch (SQLException e) {
            log.error("Database error getting specific tables schema for asset {}: {}", asset.getId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error getting specific tables schema for asset {}: {}", asset.getId(), e.getMessage());
            throw new DatabaseAccessException("Failed to get specific tables schema for asset ID: " + asset.getId(), e);
        }
        
        return tables;
    }
    
    /**
     * Create a TableSchema object with fields for the given table
     * 
     * @param metaData Database metadata
     * @param catalog Database catalog
     * @param schema Database schema
     * @param tableName Table name
     * @param asset The asset containing database connection information
     * @param connection Database connection to reuse
     * @param includeSampleData Whether to collect sample data for fields
     * @return TableSchema object with fields populated
     * @throws SQLException if database access fails
     */
    private TableSchema createTableSchema(DatabaseMetaData metaData, String catalog, String schema, String tableName, Asset asset, Connection connection, boolean includeSampleData) throws SQLException {
        TableSchema table = new TableSchema();
        table.setTableName(tableName);
        table.setFields(getTableFields(metaData, catalog, schema, tableName, asset, connection, includeSampleData));
        return table;
    }
    
    /**
     * Get fields for a specific table
     * 
     * @param metaData Database metadata
     * @param catalog Database catalog
     * @param schema Database schema
     * @param tableName Table name
     * @param asset The asset containing database connection information
     * @param connection Database connection to reuse
     * @param includeSampleData Whether to collect sample data for fields
     * @return List of field schemas
     * @throws SQLException if database access fails
     * @throws DatabaseAccessException if sample value retrieval fails
     */
    private List<FieldSchema> getTableFields(DatabaseMetaData metaData, String catalog, String schema, String tableName, Asset asset, Connection connection, boolean includeSampleData) throws SQLException {
        List<FieldSchema> fields = new ArrayList<>();
        
        try (ResultSet columnResultSet = metaData.getColumns(catalog, schema, tableName, "%")) {
            while (columnResultSet.next()) {
                
                FieldSchema field = new FieldSchema();
                field.setFieldName(columnResultSet.getString("COLUMN_NAME"));
                field.setDataType(columnResultSet.getString("TYPE_NAME"));
                field.setColumnSize(columnResultSet.getInt("COLUMN_SIZE"));
                field.setIsNullable(columnResultSet.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                field.setDefaultValue(columnResultSet.getString("COLUMN_DEF"));
                
                // Get sample data (first non-null value) - optional for performance
                if (includeSampleData && enableSampleData) {
                    field.setSampleValue(getSampleValueWithTimeout(asset, tableName, field.getFieldName(), connection));
                }
                
                fields.add(field);
            }
        } catch (SQLException e) {
            log.error("Database error getting fields for table {}: {}", tableName, e.getMessage());
            throw e;
        }
        
        return fields;
    }
    
    /**
     * Get sample value for a field with timeout (anonymized)
     * Uses parameterized queries to prevent SQL injection
     * 
     * @param asset The asset containing database connection information
     * @param tableName Table name
     * @param fieldName Field name
     * @param connection Database connection to reuse
     * @return Anonymized sample value or null if not available or timeout
     */
    private String getSampleValueWithTimeout(Asset asset, String tableName, String fieldName, Connection connection) {
        try {
            // Set query timeout to prevent long-running queries
            return getSampleValueWithQueryTimeout(asset, tableName, fieldName, connection, sampleDataTimeoutMs);
        } catch (Exception e) {
            log.debug("Failed to get sample value for {}.{} (timeout/error): {}", tableName, fieldName, e.getMessage());
            return null; // Gracefully degrade - continue without sample data
        }
    }
    
    /**
     * Get sample value with query timeout
     * 
     * @param asset The asset containing database connection information
     * @param tableName Table name
     * @param fieldName Field name
     * @param connection Database connection to reuse
     * @param timeoutMs Query timeout in milliseconds
     * @return Anonymized sample value or null if not available
     */
    private String getSampleValueWithQueryTimeout(Asset asset, String tableName, String fieldName, Connection connection, int timeoutMs) {
        // Validate and sanitize inputs using secure validation
        if (!CommonUtils.isValidSqlIdentifier(tableName) || 
            !CommonUtils.isValidSqlIdentifier(fieldName)) {
            log.debug("Invalid identifier detected: table={}, field={}", tableName, fieldName);
            return null;
        }
        
        // Build database-specific query using utility
        String query = DatabaseQueryUtils.buildSampleValueQuery(asset.getDatabaseType(), tableName, fieldName);
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            // Set query timeout to prevent long-running queries
            stmt.setQueryTimeout(timeoutMs / 1000); // Convert to seconds
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString(1);
                    return anonymizeSampleValue(value);
                }
            }
        } catch (SQLException e) {
            // Log as debug instead of error since this is now optional
            log.debug("Database error getting sample value for {}.{}: {}", tableName, fieldName, e.getMessage());
            return null;
        } catch (Exception e) {
            log.debug("Unexpected error getting sample value for {}.{}: {}", tableName, fieldName, e.getMessage());
            return null;
        }
        
        return null;
    }

    /**
     * Anonymize sample values for AI analysis
     */
    private String anonymizeSampleValue(String value) {
        if (value == null || value.length() <= 2) {
            return value;
        }
        
        // Basic anonymization - show pattern but not actual data
        if (value.contains("@")) {
            return "sample@domain.com";
        } else if (value.matches("\\d+")) {
            return "123...";
        } else if (value.length() > 10) {
            return value.substring(0, 3) + "...";
        } else {
            return value.charAt(0) + "***";
        }
    }
    
    /**
     * Rule-based sensitive field detection
     */
    private List<FieldSuggestion> detectSensitiveFieldsByRules(List<TableSchema> tables) {
        List<FieldSuggestion> suggestions = new ArrayList<>();
        
        for (TableSchema table : tables) {
            for (FieldSchema field : table.getFields()) {
                FieldSuggestion suggestion = analyzeFieldByRules(table.getTableName(), field);
                if (suggestion != null) {
                    suggestions.add(suggestion);
                }
            }
        }
        
        return suggestions;
    }
    
    /**
     * Analyze individual field using database patterns
     * 
     * @param tableName Table name
     * @param field Field schema
     * @return Field suggestion or null if no suggestion found
     * @throws DatabaseAccessException if pattern analysis fails
     */
    private FieldSuggestion analyzeFieldByRules(String tableName, FieldSchema field) {
        try {
            // Use database patterns instead of hardcoded ones
            String schemaInfo = tableName + "." + field.getFieldName();
            List<FieldSuggestion> suggestions = patternService.generateFieldSuggestions(schemaInfo);
            
            if (!suggestions.isEmpty()) {
                // Take the first (highest confidence) suggestion
                FieldSuggestion dbSuggestion = suggestions.get(0);
                
                // Create a new suggestion with the field's actual data
                return FieldSuggestion.builder()
                    .tableName(tableName)
                    .fieldName(field.getFieldName())
                    .dataType(field.getDataType())
                    .suggestedStrategy(dbSuggestion.getSuggestedStrategy())
                    .confidence(dbSuggestion.getConfidence())
                    .reason(dbSuggestion.getReason())
                    .sampleValue(field.getSampleValue())
                    .detectionMethod("database_pattern")
                    .preserveChars(dbSuggestion.getPreserveChars())
                    .maskChar(dbSuggestion.getMaskChar())
                    .sensitivityLevel(dbSuggestion.getSensitivityLevel())
                    .category(dbSuggestion.getCategory())
                    .isPrimaryKey(false)
                    .isNullable(field.getIsNullable())
                    .build();
            }
        } catch (Exception e) {
            log.error("Error analyzing field {} using database patterns: {}", field.getFieldName(), e.getMessage());
            throw new DatabaseAccessException("Failed to analyze field " + tableName + "." + field.getFieldName() + " using database patterns", e);
        }
        
        return null;
    }

    /**
     * Infer a focused category from the user's request using database-driven detection
     * 
     * @param userContext User context to analyze
     * @return Inferred category or null if not found
     * @throws DatabaseAccessException if category inference fails
     */
    private AICategory inferCategoryFromContext(String userContext) {
        if (userContext == null || userContext.trim().isEmpty()) {
            return null;
        }
        
        try {
            return categoryService.inferCategoryFromContext(userContext);
        } catch (Exception e) {
            log.error("Error inferring category from context: {}", e.getMessage());
            throw new DatabaseAccessException("Failed to infer category from context", e);
        }
    }

    /**
     * Filter suggestions by category
     * 
     * @param suggestions List of suggestions to filter
     * @param category Category to filter by
     * @return Filtered suggestions
     * @throws DatabaseAccessException if filtering fails
     */
    private List<FieldSuggestion> filterByCategory(List<FieldSuggestion> suggestions, AICategory category) {
        if (suggestions == null || category == null) {
            return new ArrayList<>();
        }
        
        try {
            return categoryService.filterByCategory(suggestions, category);
        } catch (Exception e) {
            log.error("Error filtering suggestions by category: {}", e.getMessage());
            throw new DatabaseAccessException("Failed to filter suggestions by category", e);
        }
    }

    /**
     * For AI-sourced suggestions that may lack strict categories, use name heuristics
     * 
     * @param suggestions List of suggestions to filter
     * @param category Category to filter by
     * @return Filtered suggestions
     * @throws DatabaseAccessException if filtering fails
     */
    private List<FieldSuggestion> filterByCategoryHeuristics(List<FieldSuggestion> suggestions, AICategory category) {
        if (suggestions == null || category == null) {
            return new ArrayList<>();
        }
        
        try {
            return categoryService.filterByCategoryHeuristics(suggestions, category);
        } catch (Exception e) {
            log.error("Error filtering suggestions by category heuristics: {}", e.getMessage());
            throw new DatabaseAccessException("Failed to filter suggestions by category heuristics", e);
        }
    }

    private List<FieldSuggestion> filterByTableFieldFromText(List<FieldSuggestion> suggestions, String userContext) {
        if (suggestions == null || userContext == null) return suggestions;
        String text = userContext.toLowerCase();
        // crude extraction: any word after 'in the' may indicate a table name
        // e.g., "in the customer table" -> customer
        String table = null;
        int idx = text.indexOf(" in the ");
        if (idx >= 0) {
            String rest = text.substring(idx + 8).trim();
            String[] parts = rest.split("[ .]");
            if (parts.length > 0) {
                table = parts[0];
            }
        }
        if (table == null || table.isBlank()) return suggestions;
        String token = table.toLowerCase();
        List<FieldSuggestion> out = new ArrayList<>();
        for (FieldSuggestion s : suggestions) {
            if (s.getTableName() != null) {
                String tn = s.getTableName().toLowerCase();
                if (tn.equals(token) || tn.contains(token) || token.contains(tn)) {
                    out.add(s);
                }
            }
        }
        // Do not fallback to original; keep filtered, allowing later clarification if empty
        return out;
    }

    /**
     * Validate suggestions against actual schema and hydrate metadata
     * 
     * @param asset The asset to validate against
     * @param suggestions List of suggestions to validate
     * @return Validated and hydrated suggestions
     * @throws DatabaseAccessException if validation fails
     */
    public List<FieldSuggestion> validateAndHydrateSuggestions(Asset asset, List<FieldSuggestion> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) return new ArrayList<>();
        
        // Handle MongoDB separately since it doesn't use JDBC
        if (asset.getDatabaseType() == DatabaseType.MONGODB) {
            try {
                Map<String, Map<String, String>> tableToColumns = buildMongoDBTableColumnMap(asset);
                return validateSuggestionsAgainstSchema(suggestions, tableToColumns);
            } catch (Exception e) {
                log.error("Database error validating MongoDB suggestions against schema for asset {}: {}", asset.getId(), e.getMessage());
                throw new DatabaseAccessException("Failed to validate MongoDB suggestions against schema for asset ID: " + asset.getId(), e);
            }
        }
        
        try (Connection connection = getAssetConnection(asset)) {
            Map<String, Map<String, String>> tableToColumns = buildTableColumnMap(connection, asset);
            return validateSuggestionsAgainstSchema(suggestions, tableToColumns);
        } catch (SQLException e) {
            log.error("Database error validating suggestions against schema for asset {}: {}", asset.getId(), e.getMessage());
            throw new DatabaseAccessException("Failed to validate suggestions against schema for asset ID: " + asset.getId(), e);
        } catch (Exception e) {
            log.error("Unexpected error validating suggestions against schema for asset {}: {}", asset.getId(), e.getMessage());
            throw new DatabaseAccessException("Unexpected error validating suggestions against schema for asset ID: " + asset.getId(), e);
        }
    }
    
    /**
     * Build MongoDB table to column mapping
     * 
     * @param asset The asset to build mapping for
     * @return Map of collection names to field mappings
     * @throws DatabaseAccessException if MongoDB connection fails
     */
    private Map<String, Map<String, String>> buildMongoDBTableColumnMap(Asset asset) {
        try {
            // Get asset owner's credential
            AssetCredential ownerCredential = assetService.findOwnerCredentialByAssetId(asset.getId());
            if (ownerCredential == null) {
                throw new DatabaseAccessException("No asset credential found for asset: " + asset.getId(), null);
            }
            
            // Create decrypted temp credential
            AssetCredential tempCredential = databaseConnectionUtils.createDecryptedTempCredential(ownerCredential);
            
            // Get MongoDB database using utility
            MongoDatabase mongoDb = MongoDBConnectionUtils.getMongoDatabase(tempCredential);
            
            // List all collections
            List<String> collections = MongoDBConnectionUtils.listCollections(mongoDb);
            
            Map<String, Map<String, String>> tableToColumns = new HashMap<>();
            
            for (String collectionName : collections) {
                // Skip system collections
                if (isSystemTable(collectionName, DatabaseType.MONGODB)) {
                    continue;
                }
                
                // Get fields from a sample document
                Map<String, String> fields = getMongoDBCollectionFields(mongoDb, collectionName);
                tableToColumns.put(collectionName.toLowerCase(), fields);
            }
            
            return tableToColumns;
        } catch (Exception e) {
            log.error("Database error building MongoDB table column map for asset {}: {}", asset.getId(), e.getMessage());
            throw new DatabaseAccessException("Failed to build MongoDB table column map for asset ID: " + asset.getId(), e);
        }
    }
    
    /**
     * Get fields from a MongoDB collection by sampling a document
     * 
     * @param mongoDb MongoDB database
     * @param collectionName Collection name
     * @return Map of field names to data types
     */
    private Map<String, String> getMongoDBCollectionFields(MongoDatabase mongoDb, String collectionName) {
        Map<String, String> fields = new HashMap<>();
        
        try {
            // Get a sample document to infer fields
            Document sampleDoc = mongoDb.getCollection(collectionName).find().first();
            
            if (sampleDoc != null) {
                for (String key : sampleDoc.keySet()) {
                    Object value = sampleDoc.get(key);
                    String dataType = value != null ? value.getClass().getSimpleName() : "Object";
                    fields.put(key.toLowerCase(), dataType);
                }
            }
        } catch (Exception e) {
            log.debug("Could not infer fields for MongoDB collection {}: {}", collectionName, e.getMessage());
        }
        
        return fields;
    }
    
    /**
     * Build table to column mapping
     * 
     * @param connection Database connection
     * @param asset The asset to build mapping for
     * @return Map of table names to column mappings
     * @throws SQLException if database access fails
     * @throws DatabaseAccessException if metadata access fails
     */
    private Map<String, Map<String, String>> buildTableColumnMap(Connection connection, Asset asset) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = asset.getDatabaseName();
        String schema = getSchemaName(asset.getDatabaseType());
        Map<String, Map<String, String>> tableToColumns = new HashMap<>();

        try (ResultSet tableResultSet = metaData.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (tableResultSet.next()) {
                String tableName = tableResultSet.getString("TABLE_NAME");
                if (isSystemTable(tableName, asset.getDatabaseType())) continue;
                
                Map<String, String> cols = getTableColumns(metaData, catalog, schema, tableName);
                tableToColumns.put(tableName.toLowerCase(), cols);
            }
        } catch (SQLException e) {
            log.error("Database error building table column map for asset {}: {}", asset.getId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error building table column map for asset {}: {}", asset.getId(), e.getMessage());
            throw new DatabaseAccessException("Failed to build table column map for asset ID: " + asset.getId(), e);
        }
        
        return tableToColumns;
    }
    
    /**
     * Get columns for a specific table
     * 
     * @param metaData Database metadata
     * @param catalog Database catalog
     * @param schema Database schema
     * @param tableName Table name
     * @return Map of column names to data types
     * @throws SQLException if database access fails
     */
    private Map<String, String> getTableColumns(DatabaseMetaData metaData, String catalog, String schema, String tableName) throws SQLException {
        Map<String, String> cols = new HashMap<>();
        try (ResultSet columnResultSet = metaData.getColumns(catalog, schema, tableName, "%")) {
            while (columnResultSet.next()) {
                String col = columnResultSet.getString("COLUMN_NAME");
                String type = columnResultSet.getString("TYPE_NAME");
                cols.put(col.toLowerCase(), type);
            }
        } catch (SQLException e) {
            log.error("Database error getting columns for table {}: {}", tableName, e.getMessage());
            throw e;
        }
        return cols;
    }
    
    /**
     * Validate suggestions against schema
     */
    private List<FieldSuggestion> validateSuggestionsAgainstSchema(List<FieldSuggestion> suggestions, 
                                                                  Map<String, Map<String, String>> tableToColumns) {
        List<FieldSuggestion> valid = new ArrayList<>();
        
        for (FieldSuggestion s : suggestions) {
            if (isValidSuggestion(s, tableToColumns)) {
                hydrateSuggestion(s, tableToColumns);
                valid.add(s);
            }
        }
        
        return valid;
    }
    
    /**
     * Check if suggestion is valid
     */
    private boolean isValidSuggestion(FieldSuggestion s, Map<String, Map<String, String>> tableToColumns) {
        if (s.getTableName() == null || s.getFieldName() == null) {
            return false;
        }
        
        Map<String, String> cols = tableToColumns.get(s.getTableName().toLowerCase());
        if (cols == null) {
            return false;
        }
        
        return cols.get(s.getFieldName().toLowerCase()) != null;
    }
    
    /**
     * Hydrate suggestion with schema data
     * 
     * @param s Field suggestion to hydrate
     * @param tableToColumns Table to column mapping
     * @throws DatabaseAccessException if hydration fails
     */
    private void hydrateSuggestion(FieldSuggestion s, Map<String, Map<String, String>> tableToColumns) {
        try {
            Map<String, String> cols = tableToColumns.get(s.getTableName().toLowerCase());
            String dtype = cols.get(s.getFieldName().toLowerCase());
            
            // Hydrate data type
            s.setDataType(dtype);
            
            // Ensure reasonable defaults
            if (s.getSuggestedStrategy() == null || s.getSuggestedStrategy().isBlank()) {
                inferStrategyFromFieldPatterns(s, dtype);
            }
            
            if (s.getMaskChar() == null || s.getMaskChar().isBlank()) {
                s.setMaskChar("*");
            }
            if (s.getPreserveChars() == null) {
                s.setPreserveChars(2);
            }
        } catch (Exception e) {
            log.error("Error hydrating suggestion for {}.{}: {}", s.getTableName(), s.getFieldName(), e.getMessage());
            throw new DatabaseAccessException("Failed to hydrate suggestion for " + s.getTableName() + "." + s.getFieldName(), e);
        }
    }
    
    /**
     * Infer strategy from field patterns
     * 
     * @param s Field suggestion to update
     * @param dtype Data type
     * @throws DatabaseAccessException if strategy inference fails
     */
    private void inferStrategyFromFieldPatterns(FieldSuggestion s, String dtype) {
        try {
            FieldSchema fieldSchema = new FieldSchema();
            fieldSchema.setFieldName(s.getFieldName());
            fieldSchema.setDataType(dtype);
            fieldSchema.setIsNullable(true);
            
            FieldSuggestion inferred = analyzeFieldByRules(s.getTableName(), fieldSchema);
            if (inferred != null) {
                s.setSuggestedStrategy(inferred.getSuggestedStrategy());
                if (s.getPreserveChars() == null) {
                    s.setPreserveChars(inferred.getPreserveChars());
                }
                if (s.getMaskChar() == null) {
                    s.setMaskChar("*");
                }
            }
        } catch (Exception e) {
            log.error("Error inferring strategy from field patterns for {}.{}: {}", s.getTableName(), s.getFieldName(), e.getMessage());
            throw new DatabaseAccessException("Failed to infer strategy from field patterns for " + s.getTableName() + "." + s.getFieldName(), e);
        }
    }
    
    /**
     * Merge AI and rule-based suggestions
     */
    private List<FieldSuggestion> mergeAndRankSuggestions(List<FieldSuggestion> aiSuggestions, List<FieldSuggestion> ruleSuggestions) {
        Map<String, FieldSuggestion> merged = new HashMap<>();
        
        // Add rule-based suggestions first
        for (FieldSuggestion suggestion : ruleSuggestions) {
            String key = suggestion.getTableName() + "." + suggestion.getFieldName();
            merged.put(key, suggestion);
        }
        
        // Merge with AI suggestions (AI takes precedence for strategy)
        for (FieldSuggestion aiSuggestion : aiSuggestions) {
            String key = aiSuggestion.getTableName() + "." + aiSuggestion.getFieldName();
            FieldSuggestion existing = merged.get(key);
            
            if (existing != null) {
                // Merge suggestions - prefer AI strategy but keep higher confidence
                existing.setSuggestedStrategy(aiSuggestion.getSuggestedStrategy());
                existing.setConfidence(Math.max(existing.getConfidence(), aiSuggestion.getConfidence()));
                existing.setReason(existing.getReason() + " | AI: " + aiSuggestion.getReason());
            } else {
                merged.put(key, aiSuggestion);
            }
        }
        
        // Sort by confidence and sensitivity
        return merged.values().stream()
            .sorted((a, b) -> {
                int sensitivityCompare = b.getSensitivityLevel().compareTo(a.getSensitivityLevel());
                if (sensitivityCompare != 0) return sensitivityCompare;
                return Double.compare(b.getConfidence(), a.getConfidence());
            })
            .toList();
    }
    
    /**
     * Format schema information for AI analysis
     */
    private String formatSchemaForAI(List<TableSchema> tables) {
        StringBuilder sb = new StringBuilder();
        
        for (TableSchema table : tables) {
            sb.append("Table: ").append(table.getTableName()).append("\n");
            for (FieldSchema field : table.getFields()) {
                sb.append("  - ").append(field.getFieldName())
                  .append(" (").append(field.getDataType()).append(")")
                  .append(Boolean.TRUE.equals(field.getIsNullable()) ? " NULLABLE" : " NOT NULL");
                
                if (field.getSampleValue() != null) {
                    sb.append(" Sample: ").append(field.getSampleValue());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Get MongoDB schema (collections) for an asset
     * 
     * @param asset The asset to get schema for
     * @param includeSampleData Whether to collect sample data for fields
     * @return List of table schemas
     * @throws DatabaseAccessException if MongoDB connection fails
     */
    private List<TableSchema> getMongoDBSchema(Asset asset, boolean includeSampleData) {
        try {
            // Get asset owner's credential
            AssetCredential ownerCredential = assetService.findOwnerCredentialByAssetId(asset.getId());
            if (ownerCredential == null) {
                throw new DatabaseAccessException("No asset credential found for asset: " + asset.getId(), null);
            }
            
            // Create decrypted temp credential
            AssetCredential tempCredential = databaseConnectionUtils.createDecryptedTempCredential(ownerCredential);
            
            // Get MongoDB database using utility
            MongoDatabase mongoDb = MongoDBConnectionUtils.getMongoDatabase(tempCredential);
            
            // List all collections (similar to tables in SQL databases)
            List<String> collections = MongoDBConnectionUtils.listCollections(mongoDb);
            
            // Convert collections to TableSchema format
            List<TableSchema> tables = new ArrayList<>();
            for (String collectionName : collections) {
                // Skip system collections
                if (isSystemTable(collectionName, DatabaseType.MONGODB)) {
                    continue;
                }
                
                TableSchema table = createMongoDBTableSchema(mongoDb, collectionName, includeSampleData);
                tables.add(table);
            }
            
            return tables;
        } catch (Exception e) {
            log.error("Failed to get MongoDB schema for asset {}: {}", asset.getId(), e.getMessage(), e);
            throw new DatabaseAccessException("Failed to get MongoDB schema for asset ID: " + asset.getId(), e);
        }
    }
    
    /**
     * Get MongoDB schema for specific collections
     * 
     * @param asset The asset to get schema for
     * @param collectionNames List of collection names to get schema for
     * @param includeSampleData Whether to collect sample data for fields
     * @return List of table schemas
     * @throws DatabaseAccessException if MongoDB connection fails
     */
    private List<TableSchema> getMongoDBSchemaForCollections(Asset asset, List<String> collectionNames, boolean includeSampleData) {
        try {
            // Get asset owner's credential
            AssetCredential ownerCredential = assetService.findOwnerCredentialByAssetId(asset.getId());
            if (ownerCredential == null) {
                throw new DatabaseAccessException("No asset credential found for asset: " + asset.getId(), null);
            }
            
            // Create decrypted temp credential
            AssetCredential tempCredential = databaseConnectionUtils.createDecryptedTempCredential(ownerCredential);
            
            // Get MongoDB database using utility
            MongoDatabase mongoDb = MongoDBConnectionUtils.getMongoDatabase(tempCredential);
            
            // Convert collections to TableSchema format
            List<TableSchema> tables = new ArrayList<>();
            for (String collectionName : collectionNames) {
                TableSchema table = createMongoDBTableSchema(mongoDb, collectionName, includeSampleData);
                tables.add(table);
            }
            
            return tables;
        } catch (Exception e) {
            log.error("Failed to get MongoDB schema for collections {} in asset {}: {}", collectionNames, asset.getId(), e.getMessage(), e);
            throw new DatabaseAccessException("Failed to get MongoDB schema for collections: " + e.getMessage(), e);
        }
    }
    
    /**
     * Create TableSchema from MongoDB collection
     * 
     * @param mongoDb MongoDB database
     * @param collectionName Collection name
     * @param includeSampleData Whether to collect sample data
     * @return TableSchema object
     */
    private TableSchema createMongoDBTableSchema(MongoDatabase mongoDb, String collectionName, boolean includeSampleData) {
        TableSchema table = new TableSchema();
        table.setTableName(collectionName);
        table.setFields(new ArrayList<>());
        
        try {
            // Get a sample document to infer schema
            Document sampleDoc = mongoDb.getCollection(collectionName).find().first();
            
            if (sampleDoc != null) {
                // Create fields based on sample document keys
                for (String key : sampleDoc.keySet()) {
                    Object value = sampleDoc.get(key);
                    FieldSchema field = new FieldSchema();
                    field.setFieldName(key);
                    field.setDataType(value != null ? value.getClass().getSimpleName() : "Object");
                    field.setIsNullable(true); // MongoDB fields are always nullable
                    field.setColumnSize(null); // MongoDB doesn't have fixed column sizes
                    field.setDefaultValue(null);
                    
                    // Add sample value if requested
                    if (includeSampleData && value != null) {
                        field.setSampleValue(value.toString());
                    }
                    
                    table.getFields().add(field);
                }
            }
        } catch (Exception e) {
            log.debug("Could not infer schema for MongoDB collection {}: {}", collectionName, e.getMessage());
        }
        
        return table;
    }
    
    /**
     * Get database connection for asset using asset owner's credential
     * Note: MongoDB is not supported - use getMongoDBSchema() instead
     * 
     * @param asset The asset to get connection for
     * @return Database connection
     * @throws SQLException if database connection fails
     * @throws DatabaseAccessException if asset credential not found or connection fails, or if MongoDB is used
     */
    private Connection getAssetConnection(Asset asset) throws SQLException {
        // MongoDB doesn't use JDBC - prevent accidental usage
        if (asset.getDatabaseType() == DatabaseType.MONGODB) {
            throw new DatabaseAccessException(
                "MongoDB connections must use MongoDBConnectionUtils instead of JDBC Connection. " +
                "Use getMongoDBSchema() or getMongoDBSchemaForCollections() for MongoDB assets.", 
                null);
        }
        
        try {
            // Get asset owner's credential
            AssetCredential ownerCredential = assetService.findOwnerCredentialByAssetId(asset.getId());
            if (ownerCredential == null) {
                throw new DatabaseAccessException("No asset credential found for asset: " + asset.getId(), null);
            }
            
            // Create a detached copy to avoid modifying the managed entity
            AssetCredential credentialCopy = AssetCredential.builder()
                .id(ownerCredential.getId())
                .asset(ownerCredential.getAsset())
                .user(ownerCredential.getUser())
                .username(ownerCredential.getUsername())
                .password(ownerCredential.getPassword())
                .userAccessType(ownerCredential.getUserAccessType())
                .isDeleted(ownerCredential.getIsDeleted())
                .isTemporaryPassword(ownerCredential.getIsTemporaryPassword())
                .build();
            
            if (!credentialCopy.getIsTemporaryPassword()) {
                String decPsd = databaseConnectionUtils.decryptCredentialPassword(ownerCredential);
                credentialCopy.setPassword(decPsd);
            }
            
            // Use the credential copy to get connection
            return databaseConnectionUtils.getConnectionFromAssetCredential(credentialCopy);
        } catch (SQLException e) {
            log.error("Database error getting connection for asset {}: {}", asset.getId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error getting connection for asset {}: {}", asset.getId(), e.getMessage());
            throw new DatabaseAccessException("Failed to get database connection for asset ID: " + asset.getId(), e);
        }
    }
    
    /**
     * Get schema name based on database type
     */
    private String getSchemaName(DatabaseType databaseType) {
        return switch (databaseType) {
            case MYSQL -> null; // MySQL uses catalog
            case POSTGRESQL -> "public";
            case SQLSERVER -> "dbo";
            case ORACLE -> "USER"; // or specific schema
            case MONGODB -> null; // MongoDB uses databases, not schemas
        };
    }
    
    /**
     * Check if table is a system table
     */
    private boolean isSystemTable(String tableName, DatabaseType databaseType) {
        String lowerTableName = tableName.toLowerCase();
        
        return switch (databaseType) {
            case MYSQL -> lowerTableName.startsWith("information_schema") || 
                         lowerTableName.startsWith("performance_schema") ||
                         lowerTableName.startsWith("mysql");
            case POSTGRESQL -> lowerTableName.startsWith("pg_") || 
                              lowerTableName.startsWith("information_schema");
            case SQLSERVER -> lowerTableName.startsWith("sys") || 
                             lowerTableName.startsWith("msdb");
            case ORACLE -> lowerTableName.startsWith("sys") || 
                          lowerTableName.startsWith("dba_");
            case MONGODB -> lowerTableName.startsWith("system.") || 
                           lowerTableName.equals("admin") ||
                           lowerTableName.equals("local") ||
                           lowerTableName.equals("config");
        };
    }
    
    // Helper classes for schema representation
    private static class TableSchema {
        private String tableName;
        private List<FieldSchema> fields;
        
        // Getters and setters
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public List<FieldSchema> getFields() { return fields; }
        public void setFields(List<FieldSchema> fields) { this.fields = fields; }
    }
    
    private static class FieldSchema {
        private String fieldName;
        private String dataType;
        private Integer columnSize;
        private Boolean isNullable;
        private String defaultValue;
        private String sampleValue;
        
        // Getters and setters
        public String getFieldName() { return fieldName; }
        public void setFieldName(String fieldName) { this.fieldName = fieldName; }
        public String getDataType() { return dataType; }
        public void setDataType(String dataType) { this.dataType = dataType; }
        public Integer getColumnSize() { return columnSize; }
        public void setColumnSize(Integer columnSize) { this.columnSize = columnSize; }
        public Boolean getIsNullable() { return isNullable; }
        public void setIsNullable(Boolean isNullable) { this.isNullable = isNullable; }
        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
        public String getSampleValue() { return sampleValue; }
        public void setSampleValue(String sampleValue) { this.sampleValue = sampleValue; }
    }
    

} 