package com.verlake.dam.service.assets.fetchers;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * PostgreSQL-specific implementation for fetching user access information
 */
public class PostgreSQLUserAccessFetcher extends StandardDatabaseUserAccessFetcher {
    
    
    protected String getQueryWithoutPostgres() {
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
            WHERE (table_schema = ? OR table_catalog = ?)
            UNION ALL
            SELECT DISTINCT
                grantee,
                privilege_type as permission,
                ? as database_name,
                NULL as table_name,
                is_grantable as grantable,
                'SCHEMA' as object_type
            FROM information_schema.role_usage_grants
            WHERE object_schema = ?
            ORDER BY grantee, permission
            """;
    }
    
    @Override
    protected void setQueryParameters(PreparedStatement stmt, String databaseName) throws SQLException {
        stmt.setString(1, databaseName);
        stmt.setString(2, databaseName);
        stmt.setString(3, databaseName);
        stmt.setString(4, databaseName);
    }
    
    /**
     * Override to allow postgres user if it's registered as asset owner
     * The actual filtering of system users will be done in DatabaseAccessService
     * based on asset_credentials table
     */
    @Override
    protected boolean isSystemUser(String username) {
        // Don't filter out postgres here - let DatabaseAccessService handle it
        // based on whether it's registered as an asset owner
        if (username == null || username.trim().isEmpty()) {
            return true;
        }
        
        String user = username.toLowerCase().trim();
        
        // Filter out common system users, but allow postgres to pass through
        // so it can be shown if registered as asset owner
        return user.equals("root") ||
               user.equals("mysql.sys") ||
               user.equals("mysql.session") ||
               user.equals("mysql.infoschema") ||
               user.equals("sys") ||
               user.equals("system") ||
               user.equals("public") ||
               // Note: postgres is NOT filtered here - let DatabaseAccessService decide
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