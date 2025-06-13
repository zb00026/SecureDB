package com.verlake.dam.service.assets.fetchers;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Oracle-specific implementation for fetching user access information
 */
public class OracleUserAccessFetcher extends AbstractDatabaseUserAccessFetcher {
    
    @Override
    protected String getQuery() {
        return """
            SELECT DISTINCT
                grantee,
                privilege as permission,
                table_name as scope,
                'TABLE' as object_type,
                grantable
            FROM dba_tab_privs 
            WHERE owner = UPPER(?)
            UNION ALL
            SELECT DISTINCT
                grantee,
                privilege as permission,
                'DATABASE' as scope,
                'DATABASE' as object_type,
                admin_option as grantable
            FROM dba_sys_privs
            WHERE grantee NOT IN ('SYS', 'SYSTEM', 'PUBLIC')
            ORDER BY grantee, permission
            """;
    }

    @Override
    protected void setQueryParameters(PreparedStatement stmt, String databaseName) throws SQLException {
        stmt.setString(1, databaseName);
    }

    @Override
    protected void processResultSetRow(ResultSet rs, String databaseName) throws SQLException {
        String grantee = rs.getString("grantee");
        String permission = rs.getString("permission");
        String scope = rs.getString("scope");
        String objectType = rs.getString("object_type");
        boolean grantable = "YES".equals(rs.getString("grantable"));
        
        String username = extractUsernameFromGrantee(grantee);
        addUserPermission(username, permission, scope, objectType, grantable);
    }
}
