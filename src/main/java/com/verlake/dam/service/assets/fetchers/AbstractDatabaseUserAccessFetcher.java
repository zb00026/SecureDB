package com.verlake.dam.service.assets.fetchers;

import com.verlake.dam.entity.assets.dto.PermissionDTO;
import com.verlake.dam.entity.assets.dto.UserAccessDTO;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for database user access fetchers
 * Provides common functionality for all database types
 */
@Slf4j
public abstract class AbstractDatabaseUserAccessFetcher implements DatabaseUserAccessFetcher {
    protected Map<String, UserAccessDTO> userMap = new HashMap<>();

    @Override
    public List<UserAccessDTO> fetchUserAccess(Connection connection, String databaseName) throws SQLException {
        userMap.clear();
        String sql = getQuery();
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            setQueryParameters(stmt, databaseName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    processResultSetRow(rs, databaseName);
                }
            }
        }
        
        return new ArrayList<>(userMap.values());
    }

    /**
     * Returns the SQL query specific to the database type
     * @return SQL query string
     */
    protected abstract String getQuery();
    
    /**
     * Sets the parameters for the prepared statement
     * @param stmt The prepared statement
     * @param databaseName The database name
     * @throws SQLException if parameter setting fails
     */
    protected abstract void setQueryParameters(PreparedStatement stmt, String databaseName) throws SQLException;
    
    /**
     * Processes a single row from the result set
     * @param rs The result set
     * @param databaseName The database name
     * @throws SQLException if result set processing fails
     */
    protected abstract void processResultSetRow(ResultSet rs, String databaseName) throws SQLException;

    /**
     * Common implementation for processing result set rows with standard column names
     * Used by MySQL and PostgreSQL fetchers which have the same result set structure
     * @param rs The result set
     * @param databaseName The database name
     * @throws SQLException if result set processing fails
     */
    protected void processStandardResultSetRow(ResultSet rs, String databaseName) throws SQLException {
        String grantee = rs.getString("grantee");
        String permission = rs.getString("permission");
        String tableName = rs.getString("table_name");
        String objectType = rs.getString("object_type");
        boolean grantable = "YES".equals(rs.getString("grantable"));
        
        String username = extractUsernameFromGrantee(grantee);
        String scope = tableName != null ? tableName : databaseName;
        
        addUserPermission(username, permission, scope, objectType, grantable);
    }

    /**
     * Adds a user permission to the user map
     * @param username The username
     * @param permission The permission type
     * @param scope The permission scope
     * @param objectType The object type
     * @param grantable Whether the permission is grantable
     */
    protected void addUserPermission(String username, String permission, String scope, 
                                   String objectType, boolean grantable) {
        if (isSystemUser(username)) {
            return;
        }

        UserAccessDTO user = userMap.computeIfAbsent(username, k -> {
            UserAccessDTO newUser = new UserAccessDTO();
            newUser.setUsername(k);
            newUser.setGrantee(k);
            newUser.setPermissions(new ArrayList<>());
            return newUser;
        });

        PermissionDTO permissionDTO = new PermissionDTO(permission, scope, objectType, grantable);
        user.getPermissions().add(permissionDTO);
    }

    /**
     * Extracts username from grantee string, handling database-specific formats
     * @param grantee The grantee string
     * @return The extracted username
     */
    protected String extractUsernameFromGrantee(String grantee) {
        if (grantee == null) {
            return "";
        }
        
        // Remove quotes and extract username
        String username = grantee.replace("'", "").replace("`", "");
        
        // For MySQL format 'user'@'host', extract just the user part
        if (username.contains("@")) {
            username = username.split("@")[0];
        }
        
        return username.trim();
    }
    
    /**
     * Determines if a username is a system user that should be filtered out
     * @param username The username to check
     * @return true if it's a system user, false otherwise
     */
    protected boolean isSystemUser(String username) {
        if (username == null || username.trim().isEmpty()) {
            return true;
        }
        
        String user = username.toLowerCase().trim();
        
        // Common system users across database types
        return user.equals("root") ||
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
               user.startsWith("##") ||          // SQL Server system accounts
               user.startsWith("nt ") ||         // Windows authentication accounts
               user.startsWith("nt\\") ||        // Windows authentication accounts
               user.contains("infoschema") ||
               user.contains("performance_schema") ||
               user.contains("information_schema");
    }
} 