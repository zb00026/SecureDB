package com.verlake.dam.service.assets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.assets.AssetObject;
import com.verlake.dam.entity.assets.dto.AssetAccessDTO;
import com.verlake.dam.entity.assets.dto.UserAccessDTO;
import com.verlake.dam.entity.assets.dto.PermissionDTO;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.repository.assets.AssetCredentialsRepository;
import com.verlake.dam.repository.assets.AssetObjectRepository;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.enums.DatabaseType;
import com.verlake.dam.exception.DatabaseAccessException;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.service.assets.common.DatabaseConnectionUtils;
import com.verlake.dam.service.assets.fetchers.*;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.sql.*;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.SecureRandom;

@Service
@Slf4j
public class DatabaseAccessService {
    private final AssetObjectRepository assetObjectRepository;
    private final AssetCredentialsRepository assetCredentialsRepository;
    private final KeycloakService keycloakService;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();
    private final UserService userService;
    private final DatabaseConnectionUtils databaseConnectionUtils;

    public DatabaseAccessService(AssetObjectRepository assetObjectRepository,
            AssetCredentialsRepository assetCredentialsRepository,
            KeycloakService keycloakService, UserService userService,
            DatabaseConnectionUtils databaseConnectionUtils) {
        this.assetObjectRepository = assetObjectRepository;
        this.assetCredentialsRepository = assetCredentialsRepository;
        this.keycloakService = keycloakService;
        this.objectMapper = new ObjectMapper();
        this.userService = userService;
        this.databaseConnectionUtils = databaseConnectionUtils;
    }

    public void updateAssetObjects(AssetCredential credential) throws SQLException {
        String objectsJson = fetchDatabaseObjects(credential);

        AssetObject assetObject = assetObjectRepository.findByAssetCredential(credential)
                .orElse(new AssetObject());

        assetObject.setAssetCredential(credential);
        assetObject.setAsset(credential.getAsset());
        assetObject.setObjectsJson(objectsJson);

        assetObjectRepository.save(assetObject);
    }

    private String fetchDatabaseObjects(AssetCredential credential) throws SQLException {
        ObjectNode rootNode = objectMapper.createObjectNode();

        switch (credential.getAsset().getDatabaseType()) {
            case MYSQL:
                return fetchMySQLObjects(credential, rootNode);
            case POSTGRESQL:
                return fetchPostgreSQLObjects(credential, rootNode);
            case SQLSERVER:
                return fetchSQLServerObjects(credential, rootNode);
            case ORACLE:
                return fetchOracleObjects(credential, rootNode);
            default:
                throw new DatabaseAccessException(
                        Constants.getMessage("error.database.type.not.supported") + credential.getAsset().getDatabaseType(), null);
        }
    }

    private String fetchMySQLObjects(AssetCredential credential, ObjectNode rootNode) throws SQLException {
        String jdbcUrl = databaseConnectionUtils.buildJdbcUrl(credential.getAsset());

        Connection connection = DriverManager.getConnection(jdbcUrl, credential.getUsername(),
                credential.getPassword());
        // Initialize categories and arrays
        initializeCategories(rootNode);

        // Fetch and categorize grants
        fetchAndCategorizeGrants(connection, rootNode);

        // Fetch databases and their objects
        fetchDatabasesAndObjects(connection, rootNode);

        // Add information_schema objects
        fetchInformationSchemaObjects(connection, rootNode);

        return rootNode.toString();
    }

    private void initializeCategories(ObjectNode rootNode) {
        // Create category objects
        ObjectNode databaseCategory = rootNode.putObject(Constants.ASSET_ACCESS_OBJECT_DATABASE);
        ObjectNode tableCategory = rootNode.putObject(Constants.ASSET_ACCESS_OBJECT_TABLE);
        ObjectNode viewCategory = rootNode.putObject(Constants.ASSET_ACCESS_OBJECT_VIEW);
        ObjectNode procedureCategory = rootNode.putObject(Constants.ASSET_ACCESS_OBJECT_PROCEDURE);

        // Add grants arrays to each category
        databaseCategory.putArray(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        tableCategory.putArray(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        viewCategory.putArray(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        procedureCategory.putArray(Constants.ACCESS_OBJECT_ATTR_GRANTS);

        // Add data arrays
        databaseCategory.putArray(Constants.ACCESS_OBJECT_ATTR_DATA);
        tableCategory.putArray(Constants.ACCESS_OBJECT_ATTR_DATA);
        viewCategory.putArray(Constants.ACCESS_OBJECT_ATTR_DATA);
        procedureCategory.putArray(Constants.ACCESS_OBJECT_ATTR_DATA);
    }

    private void fetchAndCategorizeGrants(Connection connection, ObjectNode rootNode) {
        ArrayNode databaseGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_DATABASE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        ArrayNode tableGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_TABLE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        ArrayNode viewGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_VIEW)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        ArrayNode procedureGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_PROCEDURE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);

        try (Statement stmt = connection.createStatement();
                ResultSet grantRs = stmt.executeQuery(Constants.MYSQL_QUERY_SHOW_GRANTS)) {
            while (grantRs.next()) {
                String grantStr = grantRs.getString(1);
                categorizeGrant(grantStr, databaseGrants, tableGrants, viewGrants, procedureGrants);
            }
        } catch (SQLException e) {
            log.error(Constants.LOG_ERROR_FETCHING_GRANTS, e);
        }
    }

    private void fetchDatabasesAndObjects(Connection connection, ObjectNode rootNode) {
        ArrayNode databaseData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_DATABASE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode tableData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_TABLE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode viewData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_VIEW)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode procedureData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_PROCEDURE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);

        try (ResultSet catalogRs = connection.getMetaData().getCatalogs()) {
            while (catalogRs.next()) {
                String dbName = catalogRs.getString(Constants.DB_COLUMN_TABLE_CAT);

                // Skip system databases if needed
                if (isSystemDatabase(dbName)) {
                    continue;
                }

                databaseData.add(dbName);
                fetchDatabaseObjects(connection, dbName, tableData, viewData, procedureData);
            }
        } catch (SQLException e) {
            log.error(Constants.LOG_ERROR_FETCHING_DATABASES, e);
        }
    }

    private boolean isSystemDatabase(String dbName) {
        return dbName.equals(Constants.MYSQL_SYSTEM_DB_MYSQL) || dbName.equals(Constants.MYSQL_SYSTEM_DB_PERFORMANCE_SCHEMA) ||
                dbName.equals(Constants.MYSQL_SYSTEM_DB_SYS) || dbName.equals(Constants.MYSQL_SYSTEM_DB_INFORMATION_SCHEMA);
    }

    private void fetchDatabaseObjects(Connection connection, String dbName,
            ArrayNode tableData, ArrayNode viewData, ArrayNode procedureData) {
        try {
            // Skip USE statement and directly query tables from the database
            fetchTables(connection, dbName, tableData);
            fetchViews(connection, dbName, viewData);
            fetchProcedures(connection, dbName, procedureData);
        } catch (Exception e) {
            log.error(Constants.LOG_ERROR_FETCHING_OBJECTS_FOR_DB + dbName, e);
        }
    }

    private void fetchTables(Connection connection, String dbName, ArrayNode tableData) throws SQLException {
        // Validate database name to prevent SQL injection
        if (!dbName.matches("^\\w+$")) {
            log.error("Invalid database name: {}", dbName);
            return;
        }

        // Use INFORMATION_SCHEMA.TABLES instead of SHOW TABLES FROM
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ?")) {
            stmt.setString(1, dbName);
            try (ResultSet tableRs = stmt.executeQuery()) {
                while (tableRs.next()) {
                    String tableName = tableRs.getString(Constants.INFORMATION_SCHEMA_TABLE_NAME);
                    tableData.add(dbName + "." + tableName);
                }
            }
        }
    }

    private void fetchViews(Connection connection, String dbName, ArrayNode viewData) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_SCHEMA = ?")) {
            stmt.setString(1, dbName);
            try (ResultSet viewRs = stmt.executeQuery()) {
                while (viewRs.next()) {
                    String viewName = viewRs.getString(Constants.INFORMATION_SCHEMA_TABLE_NAME);
                    viewData.add(dbName + "." + viewName);
                }
            }
        } catch (Exception e) {
            log.error(Constants.LOG_ERROR_FETCHING_VIEWS_FOR_DB + dbName, e);
        }
    }

    private void fetchProcedures(Connection connection, String dbName, ArrayNode procedureData) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT ROUTINE_NAME FROM INFORMATION_SCHEMA.ROUTINES WHERE ROUTINE_SCHEMA = ?")) {
            stmt.setString(1, dbName);
            try (ResultSet procRs = stmt.executeQuery()) {
                while (procRs.next()) {
                    String procName = procRs.getString(Constants.DB_COLUMN_ROUTINE_NAME);
                    procedureData.add(dbName + "." + procName);
                }
            }
        } catch (Exception e) {
            log.error(Constants.LOG_ERROR_FETCHING_PROCEDURES_FOR_DB + dbName, e);
        }
    }

    private void fetchInformationSchemaObjects(Connection connection, ObjectNode rootNode) {
        ArrayNode tableData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_TABLE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);

        try (Statement stmt = connection.createStatement();
                ResultSet infoSchemaRs = stmt.executeQuery(
                        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                                "WHERE TABLE_SCHEMA = 'information_schema' AND TABLE_TYPE = 'BASE TABLE'")) {
            while (infoSchemaRs.next()) {
                String tableName = infoSchemaRs.getString(Constants.INFORMATION_SCHEMA_TABLE_NAME);
                tableData.add(Constants.INFORMATION_SCHEMA_PREFIX + tableName);
            }
        } catch (Exception e) {
            log.error(Constants.LOG_ERROR_FETCHING_INFO_SCHEMA, e);
        }
    }

    private void categorizeGrant(String grantStr, ArrayNode databaseGrants, ArrayNode tableGrants,
            ArrayNode viewGrants, ArrayNode procedureGrants) {
        ObjectNode grantNode = objectMapper.createObjectNode();
        grantStr = grantStr.toUpperCase();

        if (grantStr.contains("ON *.*")) {
            handleGlobalGrant(grantStr, grantNode, databaseGrants, tableGrants, viewGrants, procedureGrants);
        } else if (grantStr.contains("ON")) {
            handleSpecificGrant(grantStr, grantNode, tableGrants, viewGrants, procedureGrants);
        }
    }

    private void handleGlobalGrant(String grantStr, ObjectNode grantNode,
            ArrayNode databaseGrants, ArrayNode tableGrants,
            ArrayNode viewGrants, ArrayNode procedureGrants) {
        // Convert MySQL grant to template format
        String templateGrant = convertMySQLGrantToTemplate(grantStr);
        grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, templateGrant);
        databaseGrants.add(grantNode);

        if (grantStr.contains(Constants.ACCESS_LEVEL_TEMPLATE_CREATE_VIEW)
                || grantStr.contains(Constants.ACCESS_LEVEL_TEMPLATE_FULL)) {
            addViewGrantFromGlobal(viewGrants);
        }

        if (grantStr.contains(Constants.ACCESS_LEVEL_TEMPLATE_CREATE_ROUTINE)
                || grantStr.contains(Constants.ACCESS_LEVEL_TEMPLATE_ALTER_ROUTINE)) {
            addProcedureGrantFromGlobal(procedureGrants);
        }

        if (hasTablePrivileges(grantStr)) {
            addTableGrantFromGlobal(tableGrants);
        }
    }

    private void addViewGrantFromGlobal(ArrayNode viewGrants) {
        ObjectNode viewGrantNode = objectMapper.createObjectNode();
        viewGrantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "GRANT CREATE VIEW ON $DATABASE.* TO $USER");
        viewGrants.add(viewGrantNode);
    }

    private void addProcedureGrantFromGlobal(ArrayNode procedureGrants) {
        ObjectNode procGrantNode = objectMapper.createObjectNode();
        procGrantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "GRANT CREATE ROUTINE ON $DATABASE.* TO $USER");
        procedureGrants.add(procGrantNode);
    }

    private boolean hasTablePrivileges(String grantStr) {
        return grantStr.contains(Constants.MYSQL_QUERY_SELECT) || grantStr.contains(Constants.MYSQL_QUERY_INSERT) ||
                grantStr.contains(Constants.MYSQL_QUERY_UPDATE) || grantStr.contains(Constants.MYSQL_QUERY_DELETE) ||
                grantStr.contains(Constants.MYSQL_QUERY_CREATE) || grantStr.contains(Constants.MYSQL_QUERY_ALTER);
    }

    private void addTableGrantFromGlobal(ArrayNode tableGrants) {
        ObjectNode tableGrantNode = objectMapper.createObjectNode();
        tableGrantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "GRANT SELECT, INSERT, UPDATE, DELETE ON $DATABASE.* TO $USER");
        tableGrants.add(tableGrantNode);
    }

    private void handleSpecificGrant(String grantStr, ObjectNode grantNode,
            ArrayNode tableGrants, ArrayNode viewGrants,
            ArrayNode procedureGrants) {
        // Convert MySQL grant to template format
        String templateGrant = convertMySQLGrantToTemplate(grantStr);
        grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, templateGrant);

        if (isViewGrant(grantStr)) {
            viewGrants.add(grantNode);
        } else if (isProcedureGrant(grantStr)) {
            procedureGrants.add(grantNode);
        } else {
            tableGrants.add(grantNode);
        }
    }

    private String convertMySQLGrantToTemplate(String grantStr) {
        // Convert MySQL GRANT statement to use placeholders
        // Examples:
        // "GRANT SELECT ON `database`.`table` TO 'user'@'host'" -> "GRANT SELECT ON $DATABASE.table TO $USER"
        // "GRANT ALL PRIVILEGES ON *.* TO 'user'@'host'" -> "GRANT ALL PRIVILEGES ON *.* TO $USER"
        
        String template = grantStr;
        
        // Replace username with $USER placeholder
        // Pattern: TO 'username'@'host' or TO `username`@`host`
        // Use safer regex without nested quantifiers to prevent ReDoS
        template = template.replaceAll("TO\\s+['`\"]([^'`\"@]+)['`\"]@['`\"]([^'`\"]+)['`\"]", "TO $USER");
        
        // Replace database name with $DATABASE placeholder
        // Pattern: ON `database`.`table` or ON database.table
        // Use safer regex without nested quantifiers to prevent ReDoS
        template = template.replaceAll("ON\\s+['`\"]([^'`\".]+)['`\"]\\.", "ON $DATABASE.");
        template = template.replaceAll("ON\\s+([^.\\s]+)\\.", "ON $DATABASE.");
        
        // For global grants (ON *.*)
        template = template.replaceAll("ON\\s+\\*\\.\\*", "ON $DATABASE.*");
        
        return template;
    }

    private boolean isViewGrant(String grantStr) {
        return grantStr.contains(Constants.ACCESS_LEVEL_TEMPLATE_CREATE_VIEW)
                || grantStr.contains(Constants.ACCESS_LEVEL_TEMPLATE_FULL);
    }

    private boolean isProcedureGrant(String grantStr) {
        return grantStr.contains(Constants.SQL_KEYWORD_ROUTINE) || grantStr.contains(Constants.SQL_KEYWORD_PROCEDURE);
    }

        private JdbcTemplate createJdbcTemplate(AssetCredential credential, String driverClassName, String urlPrefix) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverClassName);
        
        String jdbcUrl = urlPrefix + credential.getAsset().getHostUrl();
        // Add SSL parameters for MSSQL
        if (urlPrefix.equals(Constants.JDBC_SQLSERVER_URL)) {
            jdbcUrl += Constants.JDBC_SQLSERVER_SSL_PARAMS;
        }
        
        dataSource.setUrl(jdbcUrl);
        dataSource.setUsername(credential.getUsername());
        dataSource.setPassword(credential.getPassword());
        return new JdbcTemplate(dataSource);
    }

    private String fetchPostgreSQLObjects(AssetCredential credential, ObjectNode rootNode) {
        JdbcTemplate jdbcTemplate = createJdbcTemplate(
                credential,
                Constants.DRIVER_POSTGRESQL,
                "jdbc:postgresql://");

        // Initialize categories with proper structure
        initializeCategories(rootNode);

        // Get grants and data arrays
        ArrayNode databaseGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_DATABASE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        ArrayNode tableGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_TABLE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);

        ArrayNode databaseData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_DATABASE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode tableData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_TABLE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode viewData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_VIEW)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode procedureData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_PROCEDURE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);

        // Query for role privileges
        List<Map<String, Object>> privileges = jdbcTemplate.queryForList(
                "SELECT * FROM information_schema.role_table_grants WHERE grantee = current_user");

        for (Map<String, Object> privilege : privileges) {
            String grantType = (String) privilege.get(Constants.POSTGRES_PRIVILEGE_TYPE_COLUMN);
            String tableName = (String) privilege.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);
            
            ObjectNode grantNode = objectMapper.createObjectNode();
            grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT %s %s.%s TO $USER",
                            grantType, Constants.SQL_TEMPLATE_ON_SCHEMA, tableName));

            if (tableName.startsWith(Constants.POSTGRES_SYSTEM_PREFIX)) {
                databaseGrants.add(grantNode);
            } else {
                tableGrants.add(grantNode);
            }
        }

        // Add database-level grants for PostgreSQL
        ObjectNode connectGrant = objectMapper.createObjectNode();
        connectGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "GRANT CONNECT ON DATABASE $DATABASE TO $USER");
        databaseGrants.add(connectGrant);

        ObjectNode tempGrant = objectMapper.createObjectNode();
        tempGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "GRANT TEMP ON DATABASE $DATABASE TO $USER");
        databaseGrants.add(tempGrant);

        ObjectNode createGrant = objectMapper.createObjectNode();
        createGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "GRANT CREATE ON DATABASE $DATABASE TO $USER");
        databaseGrants.add(createGrant);

        // Add schema-level grants
        ObjectNode schemaUsageGrant = objectMapper.createObjectNode();
        schemaUsageGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "GRANT USAGE ON SCHEMA $SCHEMA TO $USER");
        databaseGrants.add(schemaUsageGrant);

        ObjectNode schemaCreateGrant = objectMapper.createObjectNode();
        schemaCreateGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "GRANT CREATE ON SCHEMA $SCHEMA TO $USER");
        databaseGrants.add(schemaCreateGrant);

        // Fetch schema/database names
        List<Map<String, Object>> schemas = jdbcTemplate.queryForList(
                "SELECT schema_name FROM information_schema.schemata WHERE schema_name NOT IN ('information_schema', 'pg_catalog', 'pg_toast')");
        for (Map<String, Object> schema : schemas) {
            String schemaName = (String) schema.get(Constants.DB_COLUMN_SCHEMA_NAME);
            databaseData.add(schemaName);
            }

        // Fetch tables
        List<Map<String, Object>> tables = jdbcTemplate.queryForList(
                "SELECT table_schema, table_name FROM information_schema.tables WHERE table_schema NOT IN ('information_schema', 'pg_catalog', 'pg_toast')");
        for (Map<String, Object> table : tables) {
            String schemaName = (String) table.get(Constants.DB_COLUMN_TABLE_SCHEMA);
            String tableName = (String) table.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);
            tableData.add(schemaName + "." + tableName);
        }

        // Fetch views
        List<Map<String, Object>> views = jdbcTemplate.queryForList(
                "SELECT table_schema, table_name FROM information_schema.views WHERE table_schema NOT IN ('information_schema', 'pg_catalog', 'pg_toast')");
        for (Map<String, Object> view : views) {
            String schemaName = (String) view.get(Constants.DB_COLUMN_TABLE_SCHEMA);
            String viewName = (String) view.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);
            viewData.add(schemaName + "." + viewName);
        }

        // Fetch procedures/functions
        List<Map<String, Object>> routines = jdbcTemplate.queryForList(
                "SELECT routine_schema, routine_name FROM information_schema.routines WHERE routine_schema NOT IN ('information_schema', 'pg_catalog', 'pg_toast')");
        for (Map<String, Object> routine : routines) {
            String schemaName = (String) routine.get("routine_schema");
            String routineName = (String) routine.get("routine_name");
            procedureData.add(schemaName + "." + routineName);
        }

        return rootNode.toString();
    }

    private String fetchSQLServerObjects(AssetCredential credential, ObjectNode rootNode) {
        JdbcTemplate jdbcTemplate = createJdbcTemplate(
                credential,
                Constants.DRIVER_SQLSERVER,
                Constants.JDBC_SQLSERVER_URL);

        // Initialize categories with proper structure
        initializeCategories(rootNode);

        // Get grants and data arrays
        ArrayNode databaseGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_DATABASE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        ArrayNode tableGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_TABLE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        ArrayNode viewGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_VIEW)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        ArrayNode procedureGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_PROCEDURE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);

        ArrayNode databaseData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_DATABASE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode tableData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_TABLE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode viewData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_VIEW)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode procedureData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_PROCEDURE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);

        // Instead of querying existing permissions, we'll create templates based on available objects
        // This approach works regardless of the user's permission to view system tables
        
        // For tables - create permission templates for each table
        List<Map<String, Object>> tables = jdbcTemplate.queryForList(
                "SELECT SCHEMA_NAME(schema_id) as schema_name, name as table_name FROM sys.tables");
        
        for (Map<String, Object> table : tables) {
            String schemaName = (String) table.get(Constants.DB_COLUMN_SCHEMA_NAME);
            String tableName = (String) table.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);
            
            // Add SELECT permission template
            ObjectNode selectGrant = objectMapper.createObjectNode();
            selectGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT SELECT ON [%s].[%s] TO [$USER]", schemaName, tableName));
            tableGrants.add(selectGrant);
            
            // Add INSERT permission template
            ObjectNode insertGrant = objectMapper.createObjectNode();
            insertGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT INSERT ON [%s].[%s] TO [$USER]", schemaName, tableName));
            tableGrants.add(insertGrant);
            
            // Add UPDATE permission template
            ObjectNode updateGrant = objectMapper.createObjectNode();
            updateGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT UPDATE ON [%s].[%s] TO [$USER]", schemaName, tableName));
            tableGrants.add(updateGrant);
            
            // Add DELETE permission template
            ObjectNode deleteGrant = objectMapper.createObjectNode();
            deleteGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT DELETE ON [%s].[%s] TO [$USER]", schemaName, tableName));
            tableGrants.add(deleteGrant);
        }
        
        // For views - create permission templates for each view
        List<Map<String, Object>> views = jdbcTemplate.queryForList(
                "SELECT SCHEMA_NAME(schema_id) as schema_name, name as view_name FROM sys.views");
        
        for (Map<String, Object> view : views) {
            String schemaName = (String) view.get(Constants.DB_COLUMN_SCHEMA_NAME);
            String viewName = (String) view.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);
            
            ObjectNode selectGrant = objectMapper.createObjectNode();
            selectGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT SELECT ON [%s].[%s] TO [$USER]", schemaName, viewName));
            viewGrants.add(selectGrant);
        }
        
        // For procedures - create permission templates for each procedure
        List<Map<String, Object>> procedures = jdbcTemplate.queryForList(
                "SELECT SCHEMA_NAME(schema_id) as schema_name, name as procedure_name FROM sys.procedures");
        
        for (Map<String, Object> procedure : procedures) {
            String schemaName = (String) procedure.get(Constants.DB_COLUMN_SCHEMA_NAME);
            String procedureName = (String) procedure.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);
            
            ObjectNode executeGrant = objectMapper.createObjectNode();
            executeGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT EXECUTE ON [%s].[%s] TO [$USER]", schemaName, procedureName));
            procedureGrants.add(executeGrant);
        }

        // Add database-level role memberships (matching the predefined templates in the database)
        ObjectNode fullAccessRole = objectMapper.createObjectNode();
        fullAccessRole.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "ALTER ROLE [db_owner] ADD MEMBER [$USER]");
        databaseGrants.add(fullAccessRole);

        ObjectNode readAccessRole = objectMapper.createObjectNode();
        readAccessRole.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "ALTER ROLE [db_datareader] ADD MEMBER [$USER]");
        databaseGrants.add(readAccessRole);

        ObjectNode writeAccessRole = objectMapper.createObjectNode();
        writeAccessRole.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "ALTER ROLE [db_datawriter] ADD MEMBER [$USER]");
        databaseGrants.add(writeAccessRole);

        // Fetch user-defined schemas (excluding system schemas)
        List<Map<String, Object>> schemas = jdbcTemplate.queryForList(
                "SELECT name FROM sys.schemas WHERE name NOT IN ('dbo', 'guest', 'INFORMATION_SCHEMA', 'sys', " +
                "'db_owner', 'db_accessadmin', 'db_securityadmin', 'db_ddladmin', 'db_backupoperator', " +
                "'db_datareader', 'db_datawriter', 'db_denydatareader', 'db_denydatawriter')");
        
        // Always include 'dbo' as it's the default schema users work with
        databaseData.add(Constants.SQLSERVER_SCHEMA_DBO);
        
        // Add any custom schemas
        for (Map<String, Object> schema : schemas) {
            String schemaName = (String) schema.get("name");
            databaseData.add(schemaName);
        }

        // Populate data arrays with object names (for UI display)
        for (Map<String, Object> table : tables) {
            String schemaName = (String) table.get(Constants.DB_COLUMN_SCHEMA_NAME);
            String tableName = (String) table.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);
            tableData.add(schemaName + "." + tableName);
        }

        for (Map<String, Object> view : views) {
            String schemaName = (String) view.get(Constants.DB_COLUMN_SCHEMA_NAME);
            String viewName = (String) view.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);
            viewData.add(schemaName + "." + viewName);
        }

        for (Map<String, Object> procedure : procedures) {
            String schemaName = (String) procedure.get(Constants.DB_COLUMN_SCHEMA_NAME);
            String procedureName = (String) procedure.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);
            procedureData.add(schemaName + "." + procedureName);
        }

        return rootNode.toString();
    }

    private String fetchOracleObjects(AssetCredential credential, ObjectNode rootNode) {
        JdbcTemplate jdbcTemplate = createJdbcTemplate(
                credential,
                Constants.DRIVER_ORACLE,
                Constants.JDBC_ORACLE_THIN_PREFIX);

        // Initialize categories with proper structure
        initializeCategories(rootNode);

        // Get grants and data arrays
        ArrayNode databaseGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_DATABASE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        ArrayNode tableGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_TABLE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        ArrayNode viewGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_VIEW)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);
        ArrayNode procedureGrants = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_PROCEDURE)
                .get(Constants.ACCESS_OBJECT_ATTR_GRANTS);

        ArrayNode databaseData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_DATABASE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode tableData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_TABLE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode viewData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_VIEW)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);
        ArrayNode procedureData = (ArrayNode) rootNode.get(Constants.ASSET_ACCESS_OBJECT_PROCEDURE)
                .get(Constants.ACCESS_OBJECT_ATTR_DATA);

        // Query for system privileges
        List<Map<String, Object>> sysPrivs = jdbcTemplate.queryForList(
                "SELECT * FROM USER_SYS_PRIVS");

        // Query for object privileges
        List<Map<String, Object>> objPrivs = jdbcTemplate.queryForList(
                "SELECT * FROM USER_TAB_PRIVS WHERE GRANTEE = USER");

        // Handle system privileges
        for (Map<String, Object> priv : sysPrivs) {
            String privilege = (String) priv.get(Constants.DB_COLUMN_PRIVILEGE);

            ObjectNode grantNode = objectMapper.createObjectNode();
            grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT %s TO $USER", privilege));
            databaseGrants.add(grantNode);
        }

        // Handle object privileges
        for (Map<String, Object> priv : objPrivs) {
            String objectType = (String) priv.get(Constants.DB_COLUMN_TYPE);
            String privilege = (String) priv.get(Constants.DB_COLUMN_PRIVILEGE);
            String objectName = (String) priv.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);

            ObjectNode grantNode = objectMapper.createObjectNode();
            grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT %s %s.%s TO $USER",
                            privilege, Constants.SQL_TEMPLATE_ON_SCHEMA, objectName));

            if (objectType.equals(Constants.SQL_KEYWORD_TABLE)) {
                tableGrants.add(grantNode);
            } else if (objectType.equals(Constants.SQL_KEYWORD_VIEW)) {
                viewGrants.add(grantNode);
            } else if (objectType.equals(Constants.SQL_KEYWORD_PROCEDURE)) {
                procedureGrants.add(grantNode);
            } else {
                // Default to table grants for unknown object types
                tableGrants.add(grantNode);
            }
        }

        // Add database-level grants for Oracle
        ObjectNode connectGrant = objectMapper.createObjectNode();
        connectGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "GRANT CONNECT TO $USER");
        databaseGrants.add(connectGrant);

        ObjectNode resourceGrant = objectMapper.createObjectNode();
        resourceGrant.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "GRANT RESOURCE TO $USER");
        databaseGrants.add(resourceGrant);

        // Fetch tablespaces (databases in Oracle)
        List<Map<String, Object>> tablespaces = jdbcTemplate.queryForList(
                "SELECT TABLESPACE_NAME FROM USER_TABLESPACES");
        for (Map<String, Object> tablespace : tablespaces) {
            String tablespaceName = (String) tablespace.get(Constants.DB_COLUMN_TABLESPACE_NAME);
            databaseData.add(tablespaceName);
        }

        // Fetch tables
        List<Map<String, Object>> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME, OWNER FROM USER_TABLES");
        for (Map<String, Object> table : tables) {
            String tableName = (String) table.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);
            String owner = (String) table.get(Constants.DB_COLUMN_OWNER);
            tableData.add(owner + "." + tableName);
        }

        // Fetch views
        List<Map<String, Object>> views = jdbcTemplate.queryForList(
                "SELECT VIEW_NAME, OWNER FROM USER_VIEWS");
        for (Map<String, Object> view : views) {
            String viewName = (String) view.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);
            String owner = (String) view.get(Constants.DB_COLUMN_OWNER);
            viewData.add(owner + "." + viewName);
        }

        // Fetch procedures
        List<Map<String, Object>> procedures = jdbcTemplate.queryForList(
                "SELECT OBJECT_NAME, OWNER FROM USER_OBJECTS WHERE OBJECT_TYPE = 'PROCEDURE'");
        for (Map<String, Object> procedure : procedures) {
            String procedureName = (String) procedure.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);
            String owner = (String) procedure.get(Constants.DB_COLUMN_OWNER);
            procedureData.add(owner + "." + procedureName);
        }

        return rootNode.toString();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateAccessRequestCredentialPassword(AssetCredential devCredential, String newPassword)
            throws CommonUtils.CryptoException {
        String userKey = keycloakService.getUserKey();
        if (!devCredential.getIsTemporaryPassword()) {
            String decPsd = databaseConnectionUtils.decryptCredentialPassword(devCredential);
            devCredential.setPassword(decPsd);
        }
        try (Connection connection = databaseConnectionUtils.getConnectionFromAssetCredential(devCredential)) {
            String alterUserSql;
            PreparedStatement statement;

            switch (devCredential.getAsset().getDatabaseType()) {
                case MYSQL:
                    alterUserSql = "ALTER USER ?@'%' IDENTIFIED BY ?";
                    statement = connection.prepareStatement(alterUserSql);
                    statement.setString(1, devCredential.getUsername());
                    statement.setString(2, newPassword);
                    statement.execute();
                    break;

                case POSTGRESQL:
                    // PostgreSQL doesn't support parameter binding for usernames in DDL
                    String username = devCredential.getUsername().replace("\"", "\"\"");
                    String password = newPassword.replace("'", "''");
                    alterUserSql = String.format("ALTER USER \"%s\" WITH PASSWORD '%s'", username, password);
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute(alterUserSql);
                    }
                    break;

                case ORACLE:
                    alterUserSql = Constants.ALTER_USER_IDENTIFIED_BY;
                    statement = connection.prepareStatement(alterUserSql);
                    statement.setString(1, devCredential.getUsername());
                    statement.setString(2, newPassword);
                    statement.execute();
                    break;

                case SQLSERVER:
                    // SQL Server doesn't support parameter binding for usernames in DDL
                    String sqlServerUsername = devCredential.getUsername().replace("[", "[[]").replace("]", "]]");
                    String sqlServerPassword = newPassword.replace("'", "''");
                    
                    String alterLoginSql = String.format("ALTER LOGIN [%s] WITH PASSWORD = '%s' OLD_PASSWORD = '%s'", 
                        sqlServerUsername, sqlServerPassword, devCredential.getPassword());
                    try (Statement stmt = connection.createStatement()) {
                        stmt.executeUpdate(alterLoginSql);
                        log.info("Updated SQL Server login password: {}", devCredential.getUsername());
                    }
                    break;

                default:
                    throw new DatabaseAccessException(
                            Constants.getMessage(Constants.ERROR_UNSUPPORTED_DATABASE_TYPE) + devCredential.getAsset().getDatabaseType(), null);
            }
            devCredential.setPassword(CommonUtils.encrypt(userKey, newPassword));
            devCredential.setIsTemporaryPassword(false);
            assetCredentialsRepository.save(devCredential);
        } catch (SQLException e) {
            log.error(Constants.getMessage("log.error.updating.password"), e);
            throw new DatabaseAccessException(e.getMessage(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAccessRequestorInAsset(AssetCredential credential, User requestor, AccessRequest accessRequest,
            String existUsername, Map<String, String> newCredMapper) {

        try (Connection connection = databaseConnectionUtils.getConnectionFromAssetCredential(credential)) {
            // Extract username from email (everything before @)
            String username = getCredentialForAccess(connection, credential, requestor, existUsername, newCredMapper);

            // Execute access SQL if provided
            if (shouldExecuteAccessSql(accessRequest)) {
                executeAccessSqlStatements(connection, accessRequest, credential, username);
            }

        } catch (SQLException | CommonUtils.CryptoException e) {
            log.error(Constants.getMessage("error.connecting.to.database"), e);
            throw new DatabaseAccessException(Constants.getMessage("error.connecting.to.database"), e);
        }
    }
    
    private boolean shouldExecuteAccessSql(AccessRequest accessRequest) {
        return accessRequest != null && accessRequest.getAccessSql() != null;
    }
    
    private void executeAccessSqlStatements(Connection connection, AccessRequest accessRequest, 
            AssetCredential credential, String username) throws SQLException {
        String[] sqlStatements = prepareSqlStatements(accessRequest, credential, username);
        
        for (String sql : sqlStatements) {
            sql = sql.trim();
            if (!sql.isEmpty()) {
                executeSingleStatement(connection, sql);
            }
        }
    }
    
    private String[] prepareSqlStatements(AccessRequest accessRequest, AssetCredential credential, String username) {
        // Get the appropriate schema name based on database type
        String schemaName = getDefaultSchemaName(credential.getAsset().getDatabaseType(), credential.getAsset().getDatabaseName());
        
        // Split SQL statements by semicolon and execute each one
        return accessRequest.getAccessSql()
                .replace("$USER", username)
                .replace("$DATABASE", credential.getAsset().getDatabaseName())
                .replace("$SCHEMA", schemaName)
                .split(";");
    }
    
    private void executeSingleStatement(Connection connection, String sql) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            log.info("Executing SQL: {}", sql);
            stmt.execute(sql);
        } catch (SQLException e) {
            handleSqlExecutionError(sql, e);
        }
    }
    
    private void handleSqlExecutionError(String sql, SQLException e) throws SQLException {
        if (isObjectNotFoundError(e)) {
            log.error("SQL execution failed - object not found. SQL: {}, Error: {}", sql, e.getMessage());
            throw new DatabaseAccessException(
                String.format("Failed to execute SQL: %s. The referenced object may not exist in the database. " +
                            "Please verify that all tables, views, and other database objects referenced in the access request exist.", sql), e);
        } else {
            log.error("SQL execution failed. SQL: {}, Error: {}", sql, e.getMessage());
            throw e;
        }
    }
    
    private boolean isObjectNotFoundError(SQLException e) {
        return e.getMessage().contains(Constants.SQL_ERROR_OBJECT_NOT_FOUND) || 
               e.getMessage().contains(Constants.SQL_ERROR_DOES_NOT_EXIST);
    }

    private String getCheckUserSql(DatabaseType databaseType) {
        switch (databaseType) {
            case MYSQL:
                return "SELECT User, Host FROM mysql.user WHERE User = ?";
            case POSTGRESQL:
                return "SELECT * FROM pg_user WHERE usename = ?";
            case ORACLE:
                return "SELECT * FROM dba_users WHERE username = ?";
            case SQLSERVER:
                return "SELECT * FROM sys.database_principals WHERE name = ? AND type = 'S'";
            default:
                throw new DatabaseAccessException(Constants.getMessage(Constants.ERROR_UNSUPPORTED_DATABASE_TYPE) + databaseType, null);
        }
    }

    private String getCreateUserSql(DatabaseType databaseType) {
        switch (databaseType) {
            case MYSQL:
                return "CREATE USER ?@'%' IDENTIFIED BY ?";
            case POSTGRESQL:
                return "CREATE USER ? WITH PASSWORD ?";
            case ORACLE:
                return "CREATE USER ? IDENTIFIED BY ?";
            case SQLSERVER:
                return "CREATE LOGIN ? WITH PASSWORD = ?";
            default:
                throw new DatabaseAccessException(Constants.getMessage(Constants.ERROR_UNSUPPORTED_DATABASE_TYPE) + databaseType, null);
        }
    }

    private String getDefaultSchemaName(DatabaseType databaseType, String databaseName) {
        switch (databaseType) {
            case MYSQL:
                return databaseName; // MySQL uses database name as schema
            case POSTGRESQL:
                return Constants.POSTGRES_SCHEMA_PUBLIC;
            case ORACLE:
                return Constants.ORACLE_SCHEMA_USERS; // Oracle default tablespace/schema
            case SQLSERVER:
                return Constants.SQLSERVER_SCHEMA_DBO; // SQL Server default schema is 'dbo'
            default:
                return Constants.POSTGRES_SCHEMA_PUBLIC; // Default fallback
        }
    }

    private String generateUniqueUsername(String name) {
        // Remove spaces and special characters
        String baseUsername = name.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        // Generate random 4-digit number using SecureRandom
        int randomNum = secureRandom.nextInt(9000) + 1000;
        return baseUsername + randomNum;
    }

    private String generateRandomPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()";
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            int index = secureRandom.nextInt(chars.length());
            password.append(chars.charAt(index));
        }
        return Constants.TEMP_PSD_PREFIX + password.toString();
    }

    private String getCredentialForAccess(
            Connection connection,
            AssetCredential credential,
            User requestor,
            String existUsername,
            Map<String, String> newCredMapper)
            throws SQLException, CommonUtils.CryptoException {

        String username = requestor.getEmail().split("@")[0];
        if (existUsername.isEmpty()) {
            username = generateUniqueUsername(username);

            // Check if user exists
            String checkUserSql = getCheckUserSql(credential.getAsset().getDatabaseType());
            PreparedStatement checkUserStmt = connection.prepareStatement(checkUserSql);
            checkUserStmt.setString(1, username);
            ResultSet rs = checkUserStmt.executeQuery();

            if (!rs.next()) {
                // User doesn't exist, create one
                String password = generateRandomPassword();

                // Create user
                if (credential.getAsset().getDatabaseType() == DatabaseType.POSTGRESQL) {
                    // PostgreSQL doesn't support parameter binding for usernames in DDL
                    String createUserSql = String.format("CREATE USER \"%s\" WITH PASSWORD '%s'", 
                        username.replace("\"", "\"\""), password.replace("'", "''"));
                    try (Statement stmt = connection.createStatement()) {
                        stmt.executeUpdate(createUserSql);
                    }
                } else {
                    String createUserSql = getCreateUserSql(credential.getAsset().getDatabaseType());
                    PreparedStatement createUserStmt = connection.prepareStatement(createUserSql);
                    createUserStmt.setString(1, username);
                    createUserStmt.setString(2, password);
                    createUserStmt.executeUpdate();
                }

                // Store user credentials in asset_credentials table
                AssetCredential userCredential = new AssetCredential();
                userCredential.setAsset(credential.getAsset());
                userCredential.setUsername(username);
                userCredential.setPassword(password);
                userCredential.setUserAccessType(Roles.DEVELOPER.getOriginalName()); // Set appropriate role
                userCredential.setUser(requestor);
                userCredential.setIsTemporaryPassword(true);
                assetCredentialsRepository.saveAndFlush(userCredential);
                newCredMapper.put(Constants.CREDENTIAL_ID_KEY, userCredential.getId().toString());
                newCredMapper.put(Constants.EMAIL_VAR_DB_USERNAME, username);
                newCredMapper.put(Constants.EMAIL_VAR_DB_PASSWORD, password);
            }
        } else {
            username = existUsername;
        }
        return username;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeCredentialAccess(AssetCredential credential, AssetCredential ownerCredential)
            throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {

        try (Connection connection = databaseConnectionUtils.getConnectionFromAssetCredential(ownerCredential)) { // Use owner's connection
            String revokeUserSql;
            PreparedStatement statement;

            switch (credential.getAsset().getDatabaseType()) {
                case MYSQL:
                    // First revoke all privileges
                    revokeUserSql = "REVOKE ALL PRIVILEGES, GRANT OPTION FROM ?@'%'";
                    statement = connection.prepareStatement(revokeUserSql);
                    statement.setString(1, credential.getUsername());
                    statement.execute();

                    // Then drop the user
                    revokeUserSql = "DROP USER ?@'%'";
                    statement = connection.prepareStatement(revokeUserSql);
                    statement.setString(1, credential.getUsername());
                    statement.execute();
                    break;

                case POSTGRESQL:
                    // PostgreSQL doesn't support parameter binding for usernames in DDL
                    String username = credential.getUsername().replace("\"", "\"\"");
                    
                    // Revoke all privileges from all tables
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute(String.format("REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA %s FROM \"%s\"", Constants.POSTGRES_SCHEMA_PUBLIC, username));
                    }

                    // Revoke all privileges from all sequences
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute(String.format("REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA %s FROM \"%s\"", Constants.POSTGRES_SCHEMA_PUBLIC, username));
                    }

                    // Drop the user
                    try (Statement stmt = connection.createStatement()) {
                        stmt.execute(String.format("DROP USER IF EXISTS \"%s\"", username));
                    }
                    break;

                case ORACLE:
                    // Revoke all privileges and roles
                    revokeUserSql = "REVOKE ALL PRIVILEGES FROM ?";
                    statement = connection.prepareStatement(revokeUserSql);
                    statement.setString(1, credential.getUsername());
                    statement.execute();

                    // Drop the user
                    revokeUserSql = "DROP USER ? CASCADE";
                    statement = connection.prepareStatement(revokeUserSql);
                    statement.setString(1, credential.getUsername());
                    statement.execute();
                    break;

                case SQLSERVER:
                    // SQL Server doesn't support parameter binding for usernames in DDL
                    String sqlServerUsername = credential.getUsername().replace("[", "[[]").replace("]", "]]");
                    
                    // First drop the database user
                    dropSqlServerUser(connection, sqlServerUsername, credential.getUsername());
                    
                    // Then drop the login
                    dropSqlServerLogin(connection, sqlServerUsername, credential.getUsername());
                    break;

                default:
                    throw new DatabaseAccessException(
                            Constants.getMessage(Constants.ERROR_UNSUPPORTED_DATABASE_TYPE) + credential.getAsset().getDatabaseType(), null);
            }

            log.info("Successfully revoked access and dropped user: {}", credential.getUsername());
        } catch (SQLException e) {
            log.error(Constants.getMessage("log.error.revoking.access.for.user") + credential.getUsername(), e);
            throw new DatabaseAccessException(Constants.getMessage("error.revoking.database.access"), e);
        }
    }

    /**
     * Drops a SQL Server database user.
     * 
     * @param connection the database connection
     * @param sqlServerUsername the escaped SQL Server username
     * @param originalUsername the original username for logging
     */
    private void dropSqlServerUser(Connection connection, String sqlServerUsername, String originalUsername) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(String.format("DROP USER IF EXISTS [%s]", sqlServerUsername));
            log.info("Dropped SQL Server user: {}", originalUsername);
        } catch (SQLException e) {
            log.warn("Failed to drop SQL Server user {}: {}", originalUsername, e.getMessage());
        }
    }

    /**
     * Drops a SQL Server login.
     * 
     * @param connection the database connection
     * @param sqlServerUsername the escaped SQL Server username
     * @param originalUsername the original username for logging
     */
    private void dropSqlServerLogin(Connection connection, String sqlServerUsername, String originalUsername) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(String.format("DROP LOGIN [%s]", sqlServerUsername));
            log.info("Dropped SQL Server login: {}", originalUsername);
        } catch (SQLException e) {
            log.warn("Failed to drop SQL Server login {}: {}", originalUsername, e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> executeQueryWithCredentials(AssetCredential credential, String query, boolean isChangeRequest)
            throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException, InvalidKeyException, SQLException {
        return executeQueryWithCredentials(credential, query, isChangeRequest, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> executeQueryWithCredentialsDryRun(AssetCredential credential, String query, boolean isChangeRequest)
            throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException, InvalidKeyException, SQLException {
        return executeQueryWithCredentials(credential, query, isChangeRequest, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private Map<String, Object> executeQueryWithCredentials(AssetCredential credential, String query, boolean isChangeRequest, boolean isDryRun)
            throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException, InvalidKeyException, SQLException {
        
        String decryptedPassword = getDecryptedPassword(credential, query);
        String jdbcUrl = databaseConnectionUtils.buildJdbcUrl(credential.getAsset());
        
        try (Connection connection = DriverManager.getConnection(jdbcUrl, credential.getUsername(), decryptedPassword)) {
            String preparedQuery = prepareQueryForExecution(query, isChangeRequest, isDryRun);
            List<Map<String, Object>> allResults = executeAllQueries(connection, preparedQuery, isChangeRequest);
            
            return createFinalResult(allResults);
            
        } catch (SQLException e) {
            log.error("Error connecting to database: {}", jdbcUrl, e);
            throw new DatabaseAccessException("Error connecting to database: " + e.getMessage(), e);
        }
    }

    private String getDecryptedPassword(AssetCredential credential, String query) 
            throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        
        if (credential.getIsTemporaryPassword()) {
            log.error(Constants.getMessage("error.temp.password.query") + query);
            throw new DatabaseAccessException(Constants.getMessage("error.temp.password.query") + query, null);
        }

        return databaseConnectionUtils.decryptCredentialPassword(credential);
    }

    private String prepareQueryForExecution(String query, boolean isChangeRequest, boolean isDryRun) {
        if (!isChangeRequest) {
            return query;
        }
        
        String modifiedQuery = Constants.SQL_TRANSACTION_START + "; " + query;
        if (isDryRun) {
            modifiedQuery += query.trim().endsWith(Constants.SQL_STATEMENT_SEPARATOR) ? Constants.SQL_ROLLBACK_SUFFIX : "; " + Constants.SQL_TRANSACTION_ROLLBACK;
            log.debug("Dry run query: {}", modifiedQuery);
        } else {
            modifiedQuery += query.trim().endsWith(Constants.SQL_STATEMENT_SEPARATOR) ? Constants.SQL_COMMIT_SUFFIX : "; " + Constants.SQL_TRANSACTION_COMMIT;
            log.debug("Execution query: {}", modifiedQuery);
        }
        return modifiedQuery;
    }

    private List<Map<String, Object>> executeAllQueries(Connection connection, String query, boolean isChangeRequest) {
        String[] individualQueries = query.split(";");
        List<Map<String, Object>> allResults = new ArrayList<>();
        
        for (String individualQuery : individualQueries) {
            String trimmedQuery = individualQuery.trim();
            if (!trimmedQuery.isEmpty()) {
                Map<String, Object> queryResult = executeIndividualQuery(connection, trimmedQuery, isChangeRequest);
                allResults.add(queryResult);
            }
        }
        
        return allResults;
    }

    private Map<String, Object> executeIndividualQuery(Connection connection, String query, boolean isChangeRequest) {
        log.debug("Executing individual query: {}", query);
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            QueryType queryType = determineQueryType(query);
            return processQueryExecution(statement, query, queryType);
            
        } catch (SQLException e) {
            log.error("Error executing individual query: {}", query, e);
            return handleQueryError(query, e, isChangeRequest);
        }
    }

    private QueryType determineQueryType(String query) {
        String upperQuery = query.toUpperCase();
        
        if (isSelectQuery(upperQuery)) {
            return QueryType.SELECT;
        } else if (isTransactionControl(upperQuery)) {
            return QueryType.TRANSACTION_CONTROL;
        } else {
            return QueryType.DML;
        }
    }

    private boolean isSelectQuery(String upperQuery) {
        return upperQuery.startsWith(Constants.MYSQL_QUERY_SELECT) || 
               upperQuery.startsWith(Constants.MYSQL_QUERY_SHOW) || 
               upperQuery.startsWith(Constants.MYSQL_QUERY_DESCRIBE) || 
               upperQuery.startsWith(Constants.MYSQL_QUERY_DESC) ||
               upperQuery.startsWith(Constants.MYSQL_QUERY_EXPLAIN);
    }

    private boolean isTransactionControl(String upperQuery) {
        return upperQuery.startsWith(Constants.SQL_TRANSACTION_START) || 
               upperQuery.startsWith(Constants.SQL_TRANSACTION_COMMIT) || 
               upperQuery.startsWith(Constants.SQL_TRANSACTION_ROLLBACK) ||
               upperQuery.startsWith(Constants.SQL_TRANSACTION_BEGIN);
    }

    private Map<String, Object> processQueryExecution(PreparedStatement statement, String query, QueryType queryType) 
            throws SQLException {
        
        switch (queryType) {
            case SELECT:
                return executeSelectQuery(statement, query);
            case TRANSACTION_CONTROL:
                return executeTransactionControl(statement, query);
            case DML:
                return executeDmlQuery(statement, query);
            default:
                throw new IllegalArgumentException(Constants.getMessage("error.unknown.query.type") + queryType);
        }
    }

    private Map<String, Object> executeSelectQuery(PreparedStatement statement, String query) throws SQLException {
                    try (ResultSet resultSet = statement.executeQuery()) {
                        ResultSetMetaData metaData = resultSet.getMetaData();
            List<String> headers = extractHeaders(metaData);
            List<Map<String, Object>> data = extractData(resultSet, metaData);
            
            return createQueryResult(query, headers, data);
        }
    }

    private Map<String, Object> executeTransactionControl(PreparedStatement statement, String query) throws SQLException {
        statement.executeUpdate();
        List<String> headers = List.of(Constants.QUERY_RESULT_STATUS_HEADER);
        List<Map<String, Object>> data = List.of(Map.of(Constants.QUERY_RESULT_STATUS_HEADER, query.toUpperCase() + Constants.QUERY_RESULT_EXECUTED_SUCCESSFULLY));
        
        return createQueryResult(query, headers, data);
    }

    private Map<String, Object> executeDmlQuery(PreparedStatement statement, String query) throws SQLException {
        int affectedRows = statement.executeUpdate();
        List<String> headers = List.of(Constants.QUERY_RESULT_AFFECTED_ROWS_HEADER);
        List<Map<String, Object>> data = List.of(Map.of(Constants.QUERY_RESULT_AFFECTED_ROWS_HEADER, affectedRows));
        
        return createQueryResult(query, headers, data);
    }

    private List<String> extractHeaders(ResultSetMetaData metaData) throws SQLException {
        List<String> headers = new ArrayList<>();
                        int columnCount = metaData.getColumnCount();
                        
                        for (int i = 1; i <= columnCount; i++) {
            headers.add(metaData.getColumnName(i));
        }
        
        return headers;
                        }
                        
    private List<Map<String, Object>> extractData(ResultSet resultSet, ResultSetMetaData metaData) throws SQLException {
        List<Map<String, Object>> data = new ArrayList<>();
        int columnCount = metaData.getColumnCount();
        
                        while (resultSet.next()) {
                            Map<String, Object> row = new HashMap<>();
                            for (int i = 1; i <= columnCount; i++) {
                                String columnName = metaData.getColumnName(i);
                                Object value = resultSet.getObject(i);
                                row.put(columnName, value);
                            }
                            data.add(row);
                        }
        
        return data;
    }

    private Map<String, Object> createQueryResult(String query, List<String> headers, List<Map<String, Object>> data) {
        Map<String, Object> queryResult = new HashMap<>();
        queryResult.put("query", query);
        queryResult.put("headers", headers);
        queryResult.put("data", data);
        return queryResult;
    }

    private Map<String, Object> handleQueryError(String query, SQLException e, boolean isChangeRequest) {
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put("query", query);
        errorResult.put("headers", List.of(Constants.QUERY_RESULT_ERROR_HEADER));
        errorResult.put("data", List.of(Map.of(Constants.QUERY_RESULT_ERROR_HEADER, e.getMessage())));
        errorResult.put("hasError", true);
        
        if (!isChangeRequest) {
            throw new DatabaseAccessException(Constants.getMessage("error.executing.query") + e.getMessage(), e);
        }
        
        return errorResult;
    }

    private Map<String, Object> createFinalResult(List<Map<String, Object>> allResults) {
        Map<String, Object> finalResult = new HashMap<>();
        finalResult.put("results", allResults);
        finalResult.put("totalQueries", allResults.size());
        return finalResult;
    }

    private enum QueryType {
        SELECT,
        DML,
        TRANSACTION_CONTROL
    }

    /**
     * Fetch actual user access information by querying the target database directly
     * This method provides real-time information about database users and their permissions
     * 
     * @param asset The asset containing database connection information
     * @param adminCredential The credential to use for database connection
     * @return AssetAccessDTO containing all users and their permissions in the database
     * @throws DatabaseAccessException for database connection or access issues
     */
    public AssetAccessDTO fetchAssetUserAccess(Asset asset, AssetCredential adminCredential) {
        
        log.info("=== Starting fetchAssetUserAccess for asset: {} (ID: {}) ===", 
                asset.getName(), asset.getId());
        log.info("Using credential username: {}", adminCredential.getUsername());
        
        // Validate inputs
        validateInputs(asset, adminCredential);
        
        String decryptedPassword = databaseConnectionUtils.decryptCredentialPassword(adminCredential);
        
        // Create temporary credential for connection
        AssetCredential tempCredential = databaseConnectionUtils.createTempCredential(asset, adminCredential, decryptedPassword);
        
        List<UserAccessDTO> users = fetchUsersByDatabaseType(asset, tempCredential);
        
        log.info("Successfully fetched access information for {} users", users.size());
        return new AssetAccessDTO(
            asset.getId(),
            asset.getName(),
            asset.getDatabaseType().toString(),
            users
        );
    }

    /**
     * Validates the inputs for fetchAssetUserAccess method
     */
    private void validateInputs(Asset asset, AssetCredential adminCredential) {
        if (asset == null) {
            throw new DatabaseAccessException(Constants.getMessage("error.asset.cannot.be.null"), null);
        }
        
        if (adminCredential == null) {
            throw new DatabaseAccessException(Constants.getMessage("error.admin.credential.cannot.be.null"), null);
        }
        
        if (!databaseConnectionUtils.hasValidPassword(adminCredential)) {
            log.warn("Credential ID: {} has no password", adminCredential.getId());
            throw new DatabaseAccessException(Constants.getMessage("error.user.no.access.to.asset"), null);
        }
    }

    /**
     * Fetches users based on the database type
     */
    private List<UserAccessDTO> fetchUsersByDatabaseType(Asset asset, AssetCredential tempCredential) {
        try (Connection connection = databaseConnectionUtils.getConnectionFromAssetCredential(tempCredential)) {
            log.debug("Successfully connected to database: {}", asset.getDatabaseType());
            
            DatabaseUserAccessFetcher fetcher = switch (asset.getDatabaseType()) {
                case MYSQL -> new MySQLUserAccessFetcher();
                case POSTGRESQL -> new PostgreSQLUserAccessFetcher();
                case SQLSERVER -> new SQLServerUserAccessFetcher();
                case ORACLE -> new OracleUserAccessFetcher();
                default -> throw new DatabaseAccessException(
                        Constants.getMessage(Constants.ERROR_UNSUPPORTED_DATABASE_TYPE) + asset.getDatabaseType(), null);
            };
            
            return fetcher.fetchUserAccess(connection, asset.getDatabaseName());
        } catch (SQLException e) {
            log.error("Database connection or query error for asset ID: {} - {}", 
                     asset.getId(), e.getMessage());
            throw new DatabaseAccessException(Constants.getMessage("error.failed.to.connect.to.database"), e);
        }
    }

    /**
     * CRITICAL SECURITY OPERATION: Lock out users in the asset database
     * This method is coded defensively to prevent any security vulnerabilities
     * 
     * @param asset The asset containing database connection information
     * @param adminCredential The admin credential to use for the operation
     * @param lockAllUsers If true, locks all database users. If false, only locks Hagrid users.
     * @return Map containing operation results and statistics
     * @throws DatabaseAccessException for any database operation errors
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> lockoutAssetUsers(Asset asset, AssetCredential adminCredential, boolean lockAllUsers) {
        log.error("=== CRITICAL SECURITY OPERATION: Database user lockout starting ===");
        log.error("Asset: {} (ID: {}), Database: {}, Lock all users: {}", 
                 asset.getName(), asset.getId(), asset.getDatabaseType(), lockAllUsers);
        
        validateLockoutInputs(asset, adminCredential);
        
        String decryptedPassword = databaseConnectionUtils.decryptCredentialPassword(adminCredential);
        AssetCredential tempCredential = databaseConnectionUtils.createTempCredential(asset, adminCredential, decryptedPassword);
        
        Map<String, Object> result = new HashMap<>();
        List<String> lockedUsers = new ArrayList<>();
        List<String> failedUsers = new ArrayList<>();
        List<String> skippedUsers = new ArrayList<>();
        
        try (Connection connection = databaseConnectionUtils.getConnectionFromAssetCredential(tempCredential)) {
            List<String> usersToLock = getUsersToLock(connection, asset, lockAllUsers);
            String currentAdminUser = adminCredential.getUsername();
            
            log.warn("Found {} users to potentially lock. Admin user '{}' will be protected.", 
                    usersToLock.size(), currentAdminUser);
            
            for (String username : usersToLock) {
                if (isProtectedUser(username, currentAdminUser, asset.getDatabaseType())) {
                    skippedUsers.add(username + " (protected)");
                    log.info("PROTECTED: Skipping admin/system user: {}", username);
                    continue;
                }
                
                if (lockDatabaseUser(connection, username, asset.getDatabaseType())) {
                    lockedUsers.add(username);
                    log.warn("LOCKED: Successfully locked user: {}", username);
                } else {
                    failedUsers.add(username);
                    log.error("FAILED: Could not lock user: {}", username);
                }
            }
            
            result.put("success", true);
            result.put("operation", "lockout");
            result.put("assetId", asset.getId());
            result.put("assetName", asset.getName());
            result.put("databaseType", asset.getDatabaseType().toString());
            result.put("lockAllUsers", lockAllUsers);
            result.put("assetLocked", true);
            result.put("totalUsers", usersToLock.size());
            result.put("lockedUsers", lockedUsers);
            result.put("failedUsers", failedUsers);
            result.put("skippedUsers", skippedUsers);
            result.put("lockedCount", lockedUsers.size());
            result.put("failedCount", failedUsers.size());
            result.put("skippedCount", skippedUsers.size());
            
            log.error("Lockout operation completed: {} locked, {} failed, {} skipped", 
                     lockedUsers.size(), failedUsers.size(), skippedUsers.size());
            
            return result;
            
        } catch (SQLException e) {
            log.error("CRITICAL ERROR during user lockout for asset ID: {}", asset.getId(), e);
            throw new DatabaseAccessException(Constants.getMessage("error.failed.user.lockout") + e.getMessage(), e);
        }
    }

    /**
     * CRITICAL SECURITY OPERATION: Unlock users in the asset database
     * This method is coded defensively to prevent any security vulnerabilities
     * 
     * @param asset The asset containing database connection information
     * @param adminCredential The admin credential to use for the operation
     * @param unlockAllUsers If true, unlocks all database users. If false, only unlocks Hagrid users.
     * @return Map containing operation results and statistics
     * @throws DatabaseAccessException for any database operation errors
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> unlockAssetUsers(Asset asset, AssetCredential adminCredential, boolean unlockAllUsers) {
        log.error("=== CRITICAL SECURITY OPERATION: Database user unlock starting ===");
        log.error("Asset: {} (ID: {}), Database: {}, Unlock all users: {}", 
                 asset.getName(), asset.getId(), asset.getDatabaseType(), unlockAllUsers);
        
        validateLockoutInputs(asset, adminCredential);
        
        String decryptedPassword = databaseConnectionUtils.decryptCredentialPassword(adminCredential);
        AssetCredential tempCredential = databaseConnectionUtils.createTempCredential(asset, adminCredential, decryptedPassword);
        
        Map<String, Object> result = new HashMap<>();
        List<String> unlockedUsers = new ArrayList<>();
        List<String> failedUsers = new ArrayList<>();
        List<String> skippedUsers = new ArrayList<>();
        
        try (Connection connection = databaseConnectionUtils.getConnectionFromAssetCredential(tempCredential)) {
            List<String> usersToUnlock = getUsersToUnlock(connection, asset, unlockAllUsers);
            String currentAdminUser = adminCredential.getUsername();
            
            log.warn("Found {} users to potentially unlock. Admin user '{}' noted.", 
                    usersToUnlock.size(), currentAdminUser);
            
            for (String username : usersToUnlock) {
                if (unlockDatabaseUser(connection, username, asset.getDatabaseType())) {
                    unlockedUsers.add(username);
                    log.warn("UNLOCKED: Successfully unlocked user: {}", username);
                } else {
                    failedUsers.add(username);
                    log.error("FAILED: Could not unlock user: {}", username);
                }
            }
            
            result.put("success", true);
            result.put("operation", "unlock");
            result.put("assetId", asset.getId());
            result.put("assetName", asset.getName());
            result.put("databaseType", asset.getDatabaseType().toString());
            result.put("unlockAllUsers", unlockAllUsers);
            result.put("assetLocked", false);
            result.put("totalUsers", usersToUnlock.size());
            result.put("unlockedUsers", unlockedUsers);
            result.put("failedUsers", failedUsers);
            result.put("skippedUsers", skippedUsers);
            result.put("unlockedCount", unlockedUsers.size());
            result.put("failedCount", failedUsers.size());
            result.put("skippedCount", skippedUsers.size());
            
            log.error("Unlock operation completed: {} unlocked, {} failed, {} skipped", 
                     unlockedUsers.size(), failedUsers.size(), skippedUsers.size());
            
            return result;
            
        } catch (SQLException e) {
            log.error("CRITICAL ERROR during user unlock for asset ID: {}", asset.getId(), e);
            throw new DatabaseAccessException(Constants.getMessage("error.failed.user.unlock") + e.getMessage(), e);
        }
    }

    /**
     * Validates inputs for lockout/unlock operations
     */
    private void validateLockoutInputs(Asset asset, AssetCredential adminCredential) {
        if (asset == null) {
            throw new IllegalArgumentException(Constants.getMessage("error.asset.cannot.be.null"));
        }
        
        if (adminCredential == null) {
            throw new IllegalArgumentException(Constants.getMessage("error.admin.credential.cannot.be.null"));
        }
        
        if (!databaseConnectionUtils.hasValidPassword(adminCredential)) {
            throw new SecurityException(Constants.getMessage("error.admin.credential.no.password"));
        }
        
        if (asset.getDatabaseType() == null) {
            throw new IllegalArgumentException(Constants.getMessage("error.asset.database.type.null"));
        }
    }

    /**
     * Gets list of users to lock based on the lockAllUsers flag
     */
    private List<String> getUsersToLock(Connection connection, Asset asset, boolean lockAllUsers) throws SQLException {
        if (lockAllUsers) {
            return getAllDatabaseUsers(connection, asset.getDatabaseType());
        } else {
            return getHagridUsers(asset);
        }
    }

    /**
     * Gets list of users to unlock based on the unlockAllUsers flag
     */
    private List<String> getUsersToUnlock(Connection connection, Asset asset, boolean unlockAllUsers) throws SQLException {
        if (unlockAllUsers) {
            return getLockedDatabaseUsers(connection, asset.getDatabaseType());
        } else {
            return getLockedHagridUsers(connection, asset);
        }
    }

    /**
     * Gets all database users based on database type
     */
    private List<String> getAllDatabaseUsers(Connection connection, DatabaseType databaseType) throws SQLException {
        String query = switch (databaseType) {
            case MYSQL -> "SELECT User FROM mysql.user WHERE User NOT IN ('mysql.sys', 'mysql.session', 'mysql.infoschema')";
            case POSTGRESQL -> "SELECT usename FROM pg_user WHERE usename NOT LIKE 'pg_%' AND usename != 'postgres'";
            case ORACLE -> "SELECT username FROM dba_users WHERE username NOT IN ('SYS', 'SYSTEM', 'DBSNMP', 'SYSMAN', 'OUTLN')";
            case SQLSERVER -> "SELECT name FROM sys.database_principals WHERE type = 'S' AND name NOT IN ('dbo', 'guest', 'INFORMATION_SCHEMA', 'sys')";
            default -> throw new DatabaseAccessException(Constants.getMessage("error.unsupported.db.type.user.listing") + databaseType, null);
        };
        
        return executeUserQuery(connection, query);
    }

    /**
     * Gets locked database users based on database type
     */
    private List<String> getLockedDatabaseUsers(Connection connection, DatabaseType databaseType) throws SQLException {
        String query = switch (databaseType) {
            case MYSQL -> "SELECT User FROM mysql.user WHERE account_locked = 'Y' AND User NOT IN ('mysql.sys', 'mysql.session', 'mysql.infoschema')";
            case POSTGRESQL -> "SELECT usename FROM pg_user WHERE NOT usecanlogin AND usename NOT LIKE 'pg_%' AND usename != 'postgres'";
            case ORACLE -> "SELECT username FROM dba_users WHERE account_status = 'LOCKED' AND username NOT IN ('SYS', 'SYSTEM', 'DBSNMP', 'SYSMAN', 'OUTLN')";
            case SQLSERVER -> "SELECT name FROM sys.database_principals p JOIN sys.sql_logins l ON p.sid = l.sid WHERE l.is_disabled = 1 AND p.type = 'S'";
            default -> throw new DatabaseAccessException(Constants.getMessage("error.unsupported.db.type.locked.user.listing") + databaseType, null);
        };
        
        return executeUserQuery(connection, query);
    }

    /**
     * Gets Hagrid users (users managed by our system)
     */
    private List<String> getHagridUsers(Asset asset) throws SQLException {
        // Get users from our asset_credentials table for this asset
        List<AssetCredential> credentials = assetCredentialsRepository.findByAssetId(asset.getId());
        return credentials.stream()
                .map(AssetCredential::getUsername)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * Gets locked Hagrid users
     */
    private List<String> getLockedHagridUsers(Connection connection, Asset asset) throws SQLException {
        List<String> hagridUsers = getHagridUsers(asset);
        List<String> lockedUsers = getLockedDatabaseUsers(connection, asset.getDatabaseType());
        
        return hagridUsers.stream()
                .filter(lockedUsers::contains)
                .toList();
    }

    /**
     * Executes a user query and returns the list of usernames
     */
    private List<String> executeUserQuery(Connection connection, String query) throws SQLException {
        List<String> users = new ArrayList<>();
        
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                String username = rs.getString(1);
                if (username != null && !username.trim().isEmpty()) {
                    users.add(username.trim());
                }
            }
        }
        
        return users;
    }

    /**
     * Checks if a user should be protected from lockout operations
     */
    private boolean isProtectedUser(String username, String currentAdminUser, DatabaseType databaseType) {
        if (username == null || username.trim().isEmpty()) {
            return true;
        }
        
        // Always protect the current admin user
        if (username.equals(currentAdminUser)) {
            return true;
        }
        
        // Protect system users based on database type
        return switch (databaseType) {
            case MYSQL -> username.startsWith("mysql.") || 
                         username.equals("root") || 
                         username.equals("debian-sys-maint");
            
            case POSTGRESQL -> username.startsWith("pg_") || 
                              username.equals("postgres") || 
                              username.equals("postgresql");
            
            case ORACLE -> Set.of("SYS", "SYSTEM", "DBSNMP", "SYSMAN", "OUTLN", "ORACLE_OCM")
                              .contains(username.toUpperCase());
            
            case SQLSERVER -> Set.of("sa", "dbo", "guest", "INFORMATION_SCHEMA", "sys", "NT AUTHORITY\\SYSTEM")
                                 .contains(username);
            
            default -> false;
        };
    }

    /**
     * Locks a specific database user
     */
    private boolean lockDatabaseUser(Connection connection, String username, DatabaseType databaseType) {
        String lockSql = switch (databaseType) {
            case MYSQL -> "ALTER USER ?@'%' ACCOUNT LOCK";
            case POSTGRESQL -> "ALTER USER ? NOLOGIN";
            case ORACLE -> "ALTER USER ? ACCOUNT LOCK";
            case SQLSERVER -> "ALTER LOGIN ? DISABLE";
            default -> throw new DatabaseAccessException(Constants.getMessage("error.unsupported.db.type.user.locking") + databaseType, null);
        };
        
        return executeUserLockUnlockOperation(connection, lockSql, username, "lock");
    }

    /**
     * Unlocks a specific database user
     */
    private boolean unlockDatabaseUser(Connection connection, String username, DatabaseType databaseType) {
        String unlockSql = switch (databaseType) {
            case MYSQL -> "ALTER USER ?@'%' ACCOUNT UNLOCK";
            case POSTGRESQL -> "ALTER USER ? LOGIN";
            case ORACLE -> "ALTER USER ? ACCOUNT UNLOCK";
            case SQLSERVER -> "ALTER LOGIN ? ENABLE";
            default -> throw new DatabaseAccessException(Constants.getMessage("error.unsupported.db.type.user.unlocking") + databaseType, null);
        };
        
        return executeUserLockUnlockOperation(connection, unlockSql, username, "unlock");
    }

    /**
     * Executes the lock/unlock SQL operation for a user
     */
    private boolean executeUserLockUnlockOperation(Connection connection, String sql, String username, String operation) {
        try {
            // For PostgreSQL and SQL Server, we need to use string formatting instead of parameter binding
            if (sql.contains("ALTER USER ?")) {
                // PostgreSQL
                String escapedUsername = username.replace("\"", "\"\"");
                sql = sql.replace("ALTER USER ?", "ALTER USER \"" + escapedUsername + "\"");
                
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate(sql);
                }
            } else if (sql.contains("ALTER LOGIN ?")) {
                // SQL Server
                String escapedUsername = username.replace("[", "[[]").replace("]", "]]");
                sql = sql.replace("ALTER LOGIN ?", "ALTER LOGIN [" + escapedUsername + "]");
                
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeUpdate(sql);
                }
            } else {
                // MySQL and Oracle
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, username);
                    stmt.executeUpdate();
                }
            }
            
            log.debug("Successfully executed {} operation for user: {}", operation, username);
            return true;
            
        } catch (SQLException e) {
            log.error("Failed to {} user '{}': {}", operation, username, e.getMessage());
            return false;
        }
    }

}