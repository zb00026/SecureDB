package com.verlake.dam.utils;

import com.verlake.dam.enums.DatabaseType;

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
        };
    }
}
