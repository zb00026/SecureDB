package com.verlake.dam.service.assets.fetchers;

import com.verlake.dam.entity.assets.dto.UserAccessDTO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Interface for fetching user access information from different database types
 */
public interface DatabaseUserAccessFetcher {
    /**
     * Fetches user access information from the database
     * 
     * @param connection The database connection
     * @param databaseName The name of the database
     * @return List of UserAccessDTO containing user permissions
     * @throws SQLException if database access fails
     */
    List<UserAccessDTO> fetchUserAccess(Connection connection, String databaseName) throws SQLException;
} 