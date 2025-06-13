package com.verlake.dam.service.assets.fetchers;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * SQL Server-specific implementation for fetching user access information
 */
public class SQLServerUserAccessFetcher extends AbstractDatabaseUserAccessFetcher {
    
    @Override
    protected String getQuery() {
        return """
            SELECT DISTINCT
                pr.name as grantee,
                dp.permission_name as permission,
                dp.state_desc as state,
                COALESCE(o.name, s.name, 'DATABASE') as scope,
                CASE 
                    WHEN o.type = 'U' THEN 'TABLE'
                    WHEN o.type = 'V' THEN 'VIEW'
                    WHEN o.type = 'P' THEN 'PROCEDURE'
                    WHEN s.name IS NOT NULL THEN 'SCHEMA'
                    ELSE 'DATABASE'
                END as object_type
            FROM sys.database_permissions dp
            LEFT JOIN sys.objects o ON dp.major_id = o.object_id
            LEFT JOIN sys.schemas s ON dp.major_id = s.schema_id
            LEFT JOIN sys.database_principals pr ON dp.grantee_principal_id = pr.principal_id
            WHERE pr.type IN ('S', 'U', 'G') 
                AND dp.state IN ('G', 'W')
            ORDER BY pr.name, dp.permission_name
            """;
    }

    @Override
    protected void setQueryParameters(PreparedStatement stmt, String databaseName) throws SQLException {
        // No parameters needed for SQL Server query
    }

    @Override
    protected void processResultSetRow(ResultSet rs, String databaseName) throws SQLException {
        String grantee = rs.getString("grantee");
        String permission = rs.getString("permission");
        String scope = rs.getString("scope");
        String objectType = rs.getString("object_type");
        String state = rs.getString("state");
        boolean grantable = "W".equals(state); // W = WITH GRANT OPTION
        
        String username = extractUsernameFromGrantee(grantee);
        addUserPermission(username, permission, scope, objectType, grantable);
    }
} 