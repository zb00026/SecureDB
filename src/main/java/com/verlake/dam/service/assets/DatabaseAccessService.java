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
import java.sql.*;

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
                        "Database type not supported: " + credential.getAsset().getDatabaseType(), null);
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
                ResultSet grantRs = stmt.executeQuery("SHOW GRANTS FOR CURRENT_USER")) {
            while (grantRs.next()) {
                String grantStr = grantRs.getString(1);
                categorizeGrant(grantStr, databaseGrants, tableGrants, viewGrants, procedureGrants);
            }
        } catch (SQLException e) {
            log.error("Error fetching grants", e);
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
                String dbName = catalogRs.getString("TABLE_CAT");

                // Skip system databases if needed
                if (isSystemDatabase(dbName)) {
                    continue;
                }

                databaseData.add(dbName);
                fetchDatabaseObjects(connection, dbName, tableData, viewData, procedureData);
            }
        } catch (SQLException e) {
            log.error("Error fetching databases", e);
        }
    }

    private boolean isSystemDatabase(String dbName) {
        return dbName.equals("mysql") || dbName.equals("performance_schema") ||
                dbName.equals("sys") || dbName.equals("information_schema");
    }

    private void fetchDatabaseObjects(Connection connection, String dbName,
            ArrayNode tableData, ArrayNode viewData, ArrayNode procedureData) {
        try {
            // Skip USE statement and directly query tables from the database
            fetchTables(connection, dbName, tableData);
            fetchViews(connection, dbName, viewData);
            fetchProcedures(connection, dbName, procedureData);
        } catch (Exception e) {
            log.error("Error fetching objects for database: " + dbName, e);
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
                    String tableName = tableRs.getString("TABLE_NAME");
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
            log.error("Error fetching views for database: " + dbName, e);
        }
    }

    private void fetchProcedures(Connection connection, String dbName, ArrayNode procedureData) {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT ROUTINE_NAME FROM INFORMATION_SCHEMA.ROUTINES WHERE ROUTINE_SCHEMA = ?")) {
            stmt.setString(1, dbName);
            try (ResultSet procRs = stmt.executeQuery()) {
                while (procRs.next()) {
                    String procName = procRs.getString("ROUTINE_NAME");
                    procedureData.add(dbName + "." + procName);
                }
            }
        } catch (Exception e) {
            log.error("Error fetching procedures for database: " + dbName, e);
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
                tableData.add("information_schema." + tableName);
            }
        } catch (Exception e) {
            log.error("Error fetching information_schema tables", e);
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
        grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, Constants.ACCESS_LEVEL_TEMPLATE_FULL);
        grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, grantStr);
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
        viewGrantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, Constants.ACCESS_LEVEL_TEMPLATE_FULL);
        viewGrantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "VIEW privileges from global grant");
        viewGrants.add(viewGrantNode);
    }

    private void addProcedureGrantFromGlobal(ArrayNode procedureGrants) {
        ObjectNode procGrantNode = objectMapper.createObjectNode();
        procGrantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, Constants.ACCESS_LEVEL_TEMPLATE_FULL);
        procGrantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "PROCEDURE privileges from global grant");
        procedureGrants.add(procGrantNode);
    }

    private boolean hasTablePrivileges(String grantStr) {
        return grantStr.contains(Constants.MYSQL_QUERY_SELECT) || grantStr.contains(Constants.MYSQL_QUERY_INSERT) ||
                grantStr.contains(Constants.MYSQL_QUERY_UPDATE) || grantStr.contains(Constants.MYSQL_QUERY_DELETE) ||
                grantStr.contains(Constants.MYSQL_QUERY_CREATE) || grantStr.contains(Constants.MYSQL_QUERY_ALTER);
    }

    private void addTableGrantFromGlobal(ArrayNode tableGrants) {
        ObjectNode tableGrantNode = objectMapper.createObjectNode();
        tableGrantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, Constants.ACCESS_LEVEL_TEMPLATE_FULL);
        tableGrantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, "TABLE privileges from global grant");
        tableGrants.add(tableGrantNode);
    }

    private void handleSpecificGrant(String grantStr, ObjectNode grantNode,
            ArrayNode tableGrants, ArrayNode viewGrants,
            ArrayNode procedureGrants) {
        String privilege = extractPrivilege(grantStr);
        grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, privilege);
        grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, grantStr);

        if (isViewGrant(grantStr)) {
            viewGrants.add(grantNode);
        } else if (isProcedureGrant(grantStr)) {
            procedureGrants.add(grantNode);
        } else {
            tableGrants.add(grantNode);
        }
    }

    private boolean isViewGrant(String grantStr) {
        return grantStr.contains(Constants.ACCESS_LEVEL_TEMPLATE_CREATE_VIEW)
                || grantStr.contains(Constants.ACCESS_LEVEL_TEMPLATE_FULL);
    }

    private boolean isProcedureGrant(String grantStr) {
        return grantStr.contains("ROUTINE") || grantStr.contains("PROCEDURE");
    }

    private String extractPrivilege(String grantStr) {
        // Remove GRANT prefix
        String privilege = grantStr.replaceFirst("GRANT\\s+", "");

        // Extract the privilege part before ON
        int onIndex = privilege.indexOf(" ON ");
        if (onIndex != -1) {
            privilege = privilege.substring(0, onIndex).trim();
        }

        // Handle multiple privileges
        if (privilege.contains(",")) {
            return Constants.ACCESS_LEVEL_TEMPLATE_FULL;
        }

        // Handle specific privileges
        switch (privilege.toUpperCase()) {
            case "ALL PRIVILEGES":
                return Constants.ACCESS_LEVEL_TEMPLATE_FULL;
            case Constants.MYSQL_QUERY_SELECT:
                return "READ ACCESS";
            case Constants.MYSQL_QUERY_EXECUTE:
                return "EXECUTE";
            case Constants.ACCESS_LEVEL_TEMPLATE_CREATE_ROUTINE:
                return Constants.ACCESS_LEVEL_TEMPLATE_CREATE_ROUTINE;
            case Constants.ACCESS_LEVEL_TEMPLATE_ALTER_ROUTINE:
                return Constants.ACCESS_LEVEL_TEMPLATE_ALTER_ROUTINE;
            case Constants.ACCESS_LEVEL_TEMPLATE_FULL:
                return Constants.ACCESS_LEVEL_TEMPLATE_FULL;
            case Constants.ACCESS_LEVEL_TEMPLATE_CREATE_VIEW:
                return Constants.ACCESS_LEVEL_TEMPLATE_CREATE_VIEW;
            default:
                return privilege.trim();
        }
    }

    private JdbcTemplate createJdbcTemplate(AssetCredential credential, String driverClassName, String urlPrefix) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUrl(urlPrefix + credential.getAsset().getHostUrl());
        dataSource.setUsername(credential.getUsername());
        dataSource.setPassword(credential.getPassword());
        return new JdbcTemplate(dataSource);
    }

    private void initializeAccessObjectNodes(ObjectNode rootNode, ArrayNode... nodes) {
        if (nodes == null || nodes.length < 4) {
            throw new IllegalArgumentException("The nodes array must contain at least 4 elements");
        }

        nodes[0] = rootNode.putArray(Constants.ASSET_ACCESS_OBJECT_DATABASE);
        nodes[1] = rootNode.putArray(Constants.ASSET_ACCESS_OBJECT_TABLE);
        nodes[2] = rootNode.putArray(Constants.ASSET_ACCESS_OBJECT_VIEW);
        nodes[3] = rootNode.putArray(Constants.ASSET_ACCESS_OBJECT_PROCEDURE);
    }

    private String fetchPostgreSQLObjects(AssetCredential credential, ObjectNode rootNode) {
        JdbcTemplate jdbcTemplate = createJdbcTemplate(
                credential,
                "org.postgresql.Driver",
                "jdbc:postgresql://");

        // Initialize nodes
        ArrayNode[] nodes = new ArrayNode[4];
        initializeAccessObjectNodes(rootNode, nodes);
        ArrayNode databaseNode = nodes[0];
        ArrayNode tableNode = nodes[1];

        // Query for role privileges
        List<Map<String, Object>> privileges = jdbcTemplate.queryForList(
                "SELECT * FROM information_schema.role_table_grants WHERE grantee = current_user");

        for (Map<String, Object> privilege : privileges) {
            String grantType = (String) privilege.get("privilege_type");
            String tableName = (String) privilege.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);
            String schemaName = (String) privilege.get("table_schema");

            ObjectNode grantNode = objectMapper.createObjectNode();
            grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, grantType);
            grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT %s ON %s.%s TO %s",
                            grantType, schemaName, tableName, credential.getUsername()));

            if (tableName.startsWith("pg_")) {
                databaseNode.add(grantNode);
            } else {
                tableNode.add(grantNode);
            }
        }

        return rootNode.toString();
    }

    private String fetchSQLServerObjects(AssetCredential credential, ObjectNode rootNode) {
        JdbcTemplate jdbcTemplate = createJdbcTemplate(
                credential,
                "com.microsoft.sqlserver.jdbc.SQLServerDriver",
                "jdbc:sqlserver://");

        ArrayNode[] nodes = new ArrayNode[4];
        initializeAccessObjectNodes(rootNode, nodes);
        ArrayNode databaseNode = nodes[0];
        ArrayNode tableNode = nodes[1];
        ArrayNode viewNode = nodes[2];
        ArrayNode procedureNode = nodes[3];

        // Query for database permissions
        List<Map<String, Object>> permissions = jdbcTemplate.queryForList(
                "SELECT pr.principal_id, pr.name, pe.permission_name, pe.state_desc, " +
                        "ob.type_desc, SCHEMA_NAME(ob.schema_id) as schema_name, ob.name as object_name " +
                        "FROM sys.database_permissions pe " +
                        "JOIN sys.database_principals pr ON pe.grantee_principal_id = pr.principal_id " +
                        "JOIN sys.objects ob ON pe.major_id = ob.object_id " +
                        "WHERE pr.name = CURRENT_USER");

        for (Map<String, Object> permission : permissions) {
            String objectType = (String) permission.get("type_desc");
            String permissionName = (String) permission.get("permission_name");
            String schemaName = (String) permission.get("schema_name");
            String objectName = (String) permission.get("object_name");

            ObjectNode grantNode = objectMapper.createObjectNode();
            grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, permissionName);
            grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT %s ON [%s].[%s] TO [%s]",
                            permissionName, schemaName, objectName, credential.getUsername()));

            switch (objectType) {
                case "USER_TABLE":
                    tableNode.add(grantNode);
                    break;
                case "VIEW":
                    viewNode.add(grantNode);
                    break;
                case "SQL_STORED_PROCEDURE":
                    procedureNode.add(grantNode);
                    break;
                default:
                    databaseNode.add(grantNode);
            }
        }

        return rootNode.toString();
    }

    private String fetchOracleObjects(AssetCredential credential, ObjectNode rootNode) {
        JdbcTemplate jdbcTemplate = createJdbcTemplate(
                credential,
                "oracle.jdbc.OracleDriver",
                "jdbc:oracle:thin:@");

        ArrayNode[] nodes = new ArrayNode[4];
        initializeAccessObjectNodes(rootNode, nodes);
        ArrayNode databaseNode = nodes[0];
        ArrayNode tableNode = nodes[1];
        ArrayNode viewNode = nodes[2];
        ArrayNode procedureNode = nodes[3];

        // Query for system privileges
        List<Map<String, Object>> sysPrivs = jdbcTemplate.queryForList(
                "SELECT * FROM USER_SYS_PRIVS");

        // Query for object privileges
        List<Map<String, Object>> objPrivs = jdbcTemplate.queryForList(
                "SELECT * FROM USER_TAB_PRIVS WHERE GRANTEE = USER");

        // Handle system privileges
        for (Map<String, Object> priv : sysPrivs) {
            String privilege = (String) priv.get("PRIVILEGE");

            ObjectNode grantNode = objectMapper.createObjectNode();
            grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, privilege);
            grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT %s TO %s", privilege, credential.getUsername()));
            databaseNode.add(grantNode);
        }

        // Handle object privileges
        for (Map<String, Object> priv : objPrivs) {
            String objectType = (String) priv.get("TYPE");
            String privilege = (String) priv.get("PRIVILEGE");
            String owner = (String) priv.get("OWNER");
            String objectName = (String) priv.get(Constants.INFORMATION_SCHEMA_TABLE_NAME);

            ObjectNode grantNode = objectMapper.createObjectNode();
            grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE, privilege);
            grantNode.put(Constants.ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE,
                    String.format("GRANT %s ON %s.%s TO %s",
                            privilege, owner, objectName, credential.getUsername()));

            switch (objectType) {
                case "TABLE":
                    tableNode.add(grantNode);
                    break;
                case "VIEW":
                    viewNode.add(grantNode);
                    break;
                case "PROCEDURE":
                    procedureNode.add(grantNode);
                    break;
                default:
                    databaseNode.add(grantNode);
            }
        }

        return rootNode.toString();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateAccessRequestCredentialPassword(AssetCredential devCredential, String newPassword)
            throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
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
                    break;

                case POSTGRESQL:
                    alterUserSql = "ALTER USER ? WITH PASSWORD ?";
                    break;

                case ORACLE:
                    alterUserSql = "ALTER USER ? IDENTIFIED BY ?";
                    break;

                case SQLSERVER:
                    alterUserSql = "ALTER LOGIN ? WITH PASSWORD = ?";
                    break;

                default:
                    throw new DatabaseAccessException(
                            "Unsupported database type: " + devCredential.getAsset().getDatabaseType(), null);
            }
            statement = connection.prepareStatement(alterUserSql);
            statement.setString(1, devCredential.getUsername());
            statement.setString(2, newPassword);

            statement.execute();
            devCredential.setPassword(CommonUtils.encrypt(userKey, newPassword));
            devCredential.setIsTemporaryPassword(false);
            assetCredentialsRepository.save(devCredential);
        } catch (SQLException e) {
            log.error("Error updating password", e);
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
            if (accessRequest != null && accessRequest.getAccessSql() != null) {
                // Split SQL statements by semicolon and execute each one
                String[] sqlStatements = accessRequest.getAccessSql()
                        .replace("$USER", username)
                        .split(";");

                for (String sql : sqlStatements) {
                    sql = sql.trim();
                    if (!sql.isEmpty()) {
                        try (Statement stmt = connection.createStatement()) {
                            log.info("Executing SQL: {}", sql);
                            stmt.execute(sql);
                        }
                    }
                }
            }

        } catch (SQLException | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException
                | IllegalBlockSizeException | BadPaddingException | InvalidAlgorithmParameterException e) {
            log.error("Error connecting to database", e);
            throw new DatabaseAccessException("Error connecting to database", e);
        }
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
                return "SELECT * FROM sys.database_principals WHERE name = ?";
            default:
                throw new DatabaseAccessException("Unsupported database type: " + databaseType, null);
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
                throw new DatabaseAccessException("Unsupported database type: " + databaseType, null);
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
        return "temp" + password.toString();
    }

    private String getCredentialForAccess(
            Connection connection,
            AssetCredential credential,
            User requestor,
            String existUsername,
            Map<String, String> newCredMapper)
            throws SQLException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException,
            IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException {

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
                String createUserSql = getCreateUserSql(credential.getAsset().getDatabaseType());
                PreparedStatement createUserStmt = connection.prepareStatement(createUserSql);
                createUserStmt.setString(1, username);
                createUserStmt.setString(2, password);
                createUserStmt.executeUpdate();

                // Store user credentials in asset_credentials table
                AssetCredential userCredential = new AssetCredential();
                userCredential.setAsset(credential.getAsset());
                userCredential.setUsername(username);
                userCredential.setPassword(password);
                userCredential.setUserAccessType(Roles.DEVELOPER.getOriginalName()); // Set appropriate role
                userCredential.setUser(requestor);
                userCredential.setIsTemporaryPassword(true);
                assetCredentialsRepository.saveAndFlush(userCredential);
                newCredMapper.put("credentialID", userCredential.getId().toString());
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
                    // Revoke all privileges from all tables
                    revokeUserSql = "REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM ?";
                    statement = connection.prepareStatement(revokeUserSql);
                    statement.setString(1, credential.getUsername());
                    statement.execute();

                    // Revoke all privileges from all sequences
                    revokeUserSql = "REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM ?";
                    statement = connection.prepareStatement(revokeUserSql);
                    statement.setString(1, credential.getUsername());
                    statement.execute();

                    // Drop the user
                    revokeUserSql = "DROP USER IF EXISTS ?";
                    statement = connection.prepareStatement(revokeUserSql);
                    statement.setString(1, credential.getUsername());
                    statement.execute();
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
                    // Disable the login
                    revokeUserSql = "ALTER LOGIN ? DISABLE";
                    statement = connection.prepareStatement(revokeUserSql);
                    statement.setString(1, credential.getUsername());
                    statement.execute();

                    // Drop the login
                    revokeUserSql = "DROP LOGIN ?";
                    statement = connection.prepareStatement(revokeUserSql);
                    statement.setString(1, credential.getUsername());
                    statement.execute();
                    break;

                default:
                    throw new DatabaseAccessException(
                            "Unsupported database type: " + credential.getAsset().getDatabaseType(), null);
            }

            log.info("Successfully revoked access and dropped user: {}", credential.getUsername());
        } catch (SQLException e) {
            log.error("Error revoking access for user: " + credential.getUsername(), e);
            throw new DatabaseAccessException("Error revoking database access", e);
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
            log.error("Can not run query with temporary password: {}", query);
            throw new DatabaseAccessException("Can not run query with temporary password: " + query, null);
        }

        return databaseConnectionUtils.decryptCredentialPassword(credential);
    }

    private String prepareQueryForExecution(String query, boolean isChangeRequest, boolean isDryRun) {
        if (!isChangeRequest) {
            return query;
        }
        
        String modifiedQuery = "START TRANSACTION; " + query;
        if (isDryRun) {
            modifiedQuery += query.trim().endsWith(";") ? " ROLLBACK;" : "; ROLLBACK;";
            log.debug("Dry run query: {}", modifiedQuery);
        } else {
            modifiedQuery += query.trim().endsWith(";") ? " COMMIT;" : "; COMMIT;";
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
        return upperQuery.startsWith("START TRANSACTION") || 
               upperQuery.startsWith("COMMIT") || 
               upperQuery.startsWith("ROLLBACK") ||
               upperQuery.startsWith("BEGIN");
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
                throw new IllegalArgumentException("Unknown query type: " + queryType);
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
        List<String> headers = List.of("Status");
        List<Map<String, Object>> data = List.of(Map.of("Status", query.toUpperCase() + " executed successfully"));
        
        return createQueryResult(query, headers, data);
    }

    private Map<String, Object> executeDmlQuery(PreparedStatement statement, String query) throws SQLException {
        int affectedRows = statement.executeUpdate();
        List<String> headers = List.of("Affected Rows");
        List<Map<String, Object>> data = List.of(Map.of("Affected Rows", affectedRows));
        
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
        errorResult.put("headers", List.of("Error"));
        errorResult.put("data", List.of(Map.of("Error", e.getMessage())));
        errorResult.put("hasError", true);
        
        if (!isChangeRequest) {
            throw new DatabaseAccessException("Error executing query: " + e.getMessage(), e);
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
            throw new DatabaseAccessException("Asset cannot be null", null);
        }
        
        if (adminCredential == null) {
            throw new DatabaseAccessException("Admin credential cannot be null", null);
        }
        
        if (!databaseConnectionUtils.hasValidPassword(adminCredential)) {
            log.warn("Credential ID: {} has no password", adminCredential.getId());
            throw new DatabaseAccessException("User has no access to asset", null);
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
                        "Unsupported database type: " + asset.getDatabaseType(), null);
            };
            
            return fetcher.fetchUserAccess(connection, asset.getDatabaseName());
        } catch (SQLException e) {
            log.error("Database connection or query error for asset ID: {} - {}", 
                     asset.getId(), e.getMessage());
            throw new DatabaseAccessException("Failed to connect to database or execute query", e);
        }
    }




}