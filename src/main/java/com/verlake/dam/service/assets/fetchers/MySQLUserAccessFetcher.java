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


} 