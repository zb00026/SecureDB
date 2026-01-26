package com.verlake.dam.utils;

import com.verlake.dam.enums.DatabaseType;
import java.util.List;
import java.util.Map;

/**
 * Utility class for building database-specific SQL queries
 */
public class DatabaseQueryUtils {
    
    /**
     * Private constructor to prevent instantiation of utility class
     */
    private DatabaseQueryUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Wrap identifier with database-specific quotes
     * 
     * @param identifier The identifier to wrap
     * @param databaseType Database type
     * @return Wrapped identifier
     */
    public static String wrapIdentifier(String identifier, DatabaseType databaseType) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return identifier;
        }
        
        String cleaned = cleanIdentifier(identifier);
        
        return switch (databaseType) {
            case MYSQL -> "`" + cleaned + "`";
            case POSTGRESQL, ORACLE -> "\"" + cleaned + "\"";
            case SQLSERVER -> "[" + cleaned + "]";
            case MONGODB -> cleaned; // MongoDB doesn't use quotes for identifiers
        };
    }
    
    /**
     * Clean identifier by removing existing quotes
     * 
     * @param identifier The identifier to clean
     * @return Cleaned identifier
     */
    public static String cleanIdentifier(String identifier) {
        if (identifier == null) {
            return null;
        }
        
        return identifier.replace("`", "")
                        .replace("\"", "")
                        .replace("[", "")
                        .replace("]", "")
                        .trim();
    }
    
    /**
     * Build a LIMIT/TOP clause for the specific database
     * 
     * @param databaseType Database type
     * @param limit Number of rows to limit
     * @return Database-specific limit clause
     */
    public static String buildLimitClause(DatabaseType databaseType, int limit) {
        return switch (databaseType) {
            case MYSQL, POSTGRESQL -> "LIMIT " + limit;
            case SQLSERVER -> "TOP " + limit;
            case ORACLE -> ""; // Oracle uses ROWNUM in WHERE clause
            case MONGODB -> ".limit(" + limit + ")"; // MongoDB uses .limit() method
        };
    }
    
    /**
     * Build a sample value query for the specific database
     * 
     * @param databaseType Database type
     * @param tableName Table name (should be validated before calling)
     * @param fieldName Field name (should be validated before calling)
     * @return Database-specific sample value query
     */
    public static String buildSampleValueQuery(DatabaseType databaseType, String tableName, String fieldName) {
        String wrappedTable = wrapIdentifier(tableName, databaseType);
        String wrappedField = wrapIdentifier(fieldName, databaseType);
        
        return switch (databaseType) {
            case MYSQL, POSTGRESQL -> String.format("SELECT %s FROM %s WHERE %s IS NOT NULL LIMIT 1", 
                wrappedField, wrappedTable, wrappedField);
            
            case SQLSERVER -> String.format("SELECT TOP 1 %s FROM %s WHERE %s IS NOT NULL", 
                wrappedField, wrappedTable, wrappedField);
            
            case ORACLE -> String.format("SELECT %s FROM %s WHERE %s IS NOT NULL AND ROWNUM = 1", 
                wrappedField, wrappedTable, wrappedField);
            case MONGODB -> String.format("db.%s.findOne({%s: {$ne: null}})", 
                wrappedTable, wrappedField); // MongoDB query syntax
        };
    }
    
    /**
     * Build a table existence check query for the specific database
     * 
     * @param databaseType Database type
     * @param tableName Table name
     * @param schemaName Schema name (can be null)
     * @return Database-specific table existence query
     */
    public static String buildTableExistsQuery(DatabaseType databaseType, String tableName, String schemaName) {
        return switch (databaseType) {
            case MYSQL -> String.format(
                "SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = '%s' AND TABLE_SCHEMA = '%s'", 
                tableName, schemaName != null ? schemaName : "DATABASE()");
            
            case POSTGRESQL -> String.format(
                "SELECT 1 FROM information_schema.tables WHERE table_name = '%s' AND table_schema = '%s'", 
                tableName.toLowerCase(), schemaName != null ? schemaName : "public");
            
            case SQLSERVER -> String.format(
                "SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = '%s' AND TABLE_SCHEMA = '%s'", 
                tableName, schemaName != null ? schemaName : "dbo");
            
            case ORACLE -> String.format(
                "SELECT 1 FROM USER_TABLES WHERE TABLE_NAME = '%s'", 
                tableName.toUpperCase());
            case MONGODB -> String.format(
                "db.getCollectionNames().indexOf('%s') !== -1", 
                tableName); // MongoDB collection existence check
        };
    }
    
    /**
     * Build a column existence check query for the specific database
     * 
     * @param databaseType Database type
     * @param tableName Table name
     * @param columnName Column name
     * @param schemaName Schema name (can be null)
     * @return Database-specific column existence query
     */
    public static String buildColumnExistsQuery(DatabaseType databaseType, String tableName, String columnName, String schemaName) {
        return switch (databaseType) {
            case MYSQL -> String.format(
                "SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '%s' AND COLUMN_NAME = '%s' AND TABLE_SCHEMA = '%s'", 
                tableName, columnName, schemaName != null ? schemaName : "DATABASE()");
            
            case POSTGRESQL -> String.format(
                "SELECT 1 FROM information_schema.columns WHERE table_name = '%s' AND column_name = '%s' AND table_schema = '%s'", 
                tableName.toLowerCase(), columnName.toLowerCase(), schemaName != null ? schemaName : "public");
            
            case SQLSERVER -> String.format(
                "SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '%s' AND COLUMN_NAME = '%s' AND TABLE_SCHEMA = '%s'", 
                tableName, columnName, schemaName != null ? schemaName : "dbo");
            
            case ORACLE -> String.format(
                "SELECT 1 FROM USER_TAB_COLUMNS WHERE TABLE_NAME = '%s' AND COLUMN_NAME = '%s'", 
                tableName.toUpperCase(), columnName.toUpperCase());
            case MONGODB -> String.format(
                "Object.keys(db.%s.findOne({}) || {}).indexOf('%s') !== -1", 
                tableName, columnName); // MongoDB field existence check
        };
    }
    
    /**
     * Create detailed newValue for audit trail including query and results
     * 
     * @param query The SQL query that was executed
     * @param success Whether the query execution was successful
     * @param result The query result map
     * @param errorMessage Error message if query failed
     * @return Detailed audit trail newValue string
     */
    public static String createDetailedNewValue(String query, boolean success, Map<String, Object> result, String errorMessage) {
        try {
            StringBuilder newValue = new StringBuilder();
            newValue.append("Query: ").append(query);
            
            if (success && result != null) {
                appendSuccessResult(newValue, result);
            } else if (!success) {
                appendFailureResult(newValue, errorMessage);
            } else {
                newValue.append(" | Result: SUCCESS - No data returned");
            }
            
            return newValue.toString();
            
        } catch (Exception e) {
            return createFallbackValue(query, success);
        }
    }
    
    /**
     * Append success result information to the newValue string
     */
    private static void appendSuccessResult(StringBuilder newValue, Map<String, Object> result) {
        newValue.append(" | Result: ");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultsList = (List<Map<String, Object>>) result.get(Constants.QUERY_RESULT_FIELD_RESULTS);
        
        if (resultsList != null && !resultsList.isEmpty()) {
            int totalRows = calculateTotalRows(resultsList);
            newValue.append("SUCCESS - ").append(totalRows).append(" rows returned");
            
            if (totalRows > 0) {
                newValue.append(" | Sample data: ");
                addSampleData(newValue, resultsList);
            }
        } else {
            newValue.append("SUCCESS - No data returned");
        }
    }
    
    /**
     * Append failure result information to the newValue string
     */
    private static void appendFailureResult(StringBuilder newValue, String errorMessage) {
        newValue.append(" | Result: FAILED");
        if (errorMessage != null) {
            newValue.append(" - ").append(errorMessage);
        }
    }
    
    /**
     * Calculate total number of rows across all query results
     */
    private static int calculateTotalRows(List<Map<String, Object>> resultsList) {
        int totalRows = 0;
        for (Map<String, Object> queryResult : resultsList) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) queryResult.get(Constants.QUERY_RESULT_FIELD_DATA);
            if (data != null) {
                totalRows += data.size();
            }
        }
        return totalRows;
    }
    
    /**
     * Create fallback value when detailed creation fails
     */
    private static String createFallbackValue(String query, boolean success) {
        return "Query: " + query + " | Result: " + (success ? "SUCCESS" : "FAILED");
    }
    
    /**
     * Add sample data to the newValue string
     * 
     * @param newValue StringBuilder to append sample data to
     * @param resultsList List of query results
     */
    private static void addSampleData(StringBuilder newValue, List<Map<String, Object>> resultsList) {
        try {
            for (Map<String, Object> queryResult : resultsList) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) queryResult.get(Constants.QUERY_RESULT_FIELD_DATA);
                
                if (data != null && !data.isEmpty()) {
                    appendSampleRows(newValue, data);
                    break; // Only show sample from first query result
                }
            }
        } catch (Exception e) {
            // Silently handle any errors in sample data creation
        }
    }
    
    /**
     * Append sample rows to the newValue string
     */
    private static void appendSampleRows(StringBuilder newValue, List<Map<String, Object>> data) {
        int maxSampleRows = 3; // Limit to first 3 rows
        int sampleCount = Math.min(data.size(), maxSampleRows);
        
        for (int i = 0; i < sampleCount; i++) {
            Map<String, Object> row = data.get(i);
            if (row != null) {
                appendSampleRow(newValue, row, i, sampleCount);
            }
        }
        
        if (data.size() > maxSampleRows) {
            newValue.append("... (+").append(data.size() - maxSampleRows).append(" more rows)");
        }
    }
    
    /**
     * Append a single sample row to the newValue string
     */
    private static void appendSampleRow(StringBuilder newValue, Map<String, Object> row, int rowIndex, int totalRows) {
        newValue.append("[");
        appendRowColumns(newValue, row);
        newValue.append("]");
        
        if (rowIndex < totalRows - 1) {
            newValue.append(", ");
        }
    }
    
    /**
     * Append row columns to the newValue string
     */
    private static void appendRowColumns(StringBuilder newValue, Map<String, Object> row) {
        int maxSampleColumns = 5; // Limit to first 5 columns
        int colCount = 0;
        
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (colCount >= maxSampleColumns) {
                newValue.append("...");
                break;
            }
            if (colCount > 0) {
                newValue.append(", ");
            }
            newValue.append(entry.getKey()).append("=").append(entry.getValue());
            colCount++;
        }
    }
}
