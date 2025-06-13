package com.verlake.dam.service.assets.fetchers;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Common base class for database fetchers that use standard column names and parameter patterns
 * Used by MySQL and PostgreSQL fetchers which have similar result set structures
 */
public abstract class StandardDatabaseUserAccessFetcher extends AbstractDatabaseUserAccessFetcher {
    
    @Override
    protected void setQueryParameters(PreparedStatement stmt, String databaseName) throws SQLException {
        stmt.setString(1, databaseName);
        stmt.setString(2, databaseName);
    }

    @Override
    protected void processResultSetRow(ResultSet rs, String databaseName) throws SQLException {
        processStandardResultSetRow(rs, databaseName);
    }
} 