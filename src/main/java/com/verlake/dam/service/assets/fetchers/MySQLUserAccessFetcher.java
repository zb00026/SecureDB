package com.verlake.dam.service.assets.fetchers;

/**
 * MySQL-specific implementation for fetching user access information
 */
public class MySQLUserAccessFetcher extends StandardDatabaseUserAccessFetcher {
    
    @Override
    protected String getQuery() {
        return """
            SELECT DISTINCT 
                GRANTEE as grantee,
                PRIVILEGE_TYPE as permission,
                TABLE_SCHEMA as database_name,
                TABLE_NAME as table_name,
                IS_GRANTABLE as grantable,
                'TABLE' as object_type
            FROM information_schema.TABLE_PRIVILEGES 
            WHERE TABLE_SCHEMA = ?
            UNION ALL
            SELECT DISTINCT 
                GRANTEE as grantee,
                PRIVILEGE_TYPE as permission,
                ? as database_name,
                NULL as table_name,
                IS_GRANTABLE as grantable,
                'DATABASE' as object_type
            FROM information_schema.USER_PRIVILEGES
            ORDER BY grantee, permission
            """;
    }
    
    /**
     * Override to allow root user if it's registered as asset owner
     * The actual filtering of system users will be done in DatabaseAccessService
     * based on asset_credentials table
     */
    @Override
    protected boolean isSystemUser(String username) {
        // Don't filter out root here - let DatabaseAccessService handle it
        // based on whether it's registered as an asset owner
        if (username == null || username.trim().isEmpty()) {
            return true;
        }
        
        String user = username.toLowerCase().trim();
        
        // Filter out common system users, but allow root to pass through
        // so it can be shown if registered as asset owner
        return // Note: root is NOT filtered here - let DatabaseAccessService decide
               user.equals("mysql.sys") ||
               user.equals("mysql.session") ||
               user.equals("mysql.infoschema") ||
               user.equals("sys") ||
               user.equals("system") ||
               user.equals("public") ||
               user.equals("postgres") ||
               user.equals("administrator") ||
               user.equals("sa") ||
               user.equals("dbo") ||
               user.equals("guest") ||
               user.startsWith("##") ||
               user.startsWith("nt ") ||
               user.startsWith("nt\\") ||
               user.contains("infoschema") ||
               user.contains("performance_schema") ||
               user.contains("information_schema");
    }

} 