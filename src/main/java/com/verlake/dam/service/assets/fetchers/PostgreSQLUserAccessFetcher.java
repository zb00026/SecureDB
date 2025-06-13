package com.verlake.dam.service.assets.fetchers;

/**
 * PostgreSQL-specific implementation for fetching user access information
 */
public class PostgreSQLUserAccessFetcher extends StandardDatabaseUserAccessFetcher {
    
    @Override
    protected String getQuery() {
        return """
            SELECT DISTINCT
                grantee,
                privilege_type as permission,
                table_schema as database_name,
                table_name,
                is_grantable as grantable,
                CASE 
                    WHEN table_name IS NULL THEN 'DATABASE'
                    ELSE 'TABLE'
                END as object_type
            FROM information_schema.role_table_grants 
            WHERE table_schema = ? OR table_catalog = ?
            ORDER BY grantee, permission
            """;
    }


} 