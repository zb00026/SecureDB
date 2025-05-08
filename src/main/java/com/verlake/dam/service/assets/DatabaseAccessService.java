package com.verlake.dam.service.assets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.assets.AssetObject;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.repository.assets.AssetCredentialsRepository;
import com.verlake.dam.repository.assets.AssetObjectRepository;
import com.verlake.dam.service.UserService;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.enums.DatabaseType;
import com.verlake.dam.exception.DatabaseAccessException;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.verlake.dam.enums.Roles;

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

    public DatabaseAccessService(AssetObjectRepository assetObjectRepository,
                                 AssetCredentialsRepository assetCredentialsRepository,
                                 KeycloakService keycloakService, UserService userService) {
        this.assetObjectRepository = assetObjectRepository;
        this.assetCredentialsRepository = assetCredentialsRepository;
        this.keycloakService = keycloakService;
        this.objectMapper = new ObjectMapper();
        this.userService = userService;
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
        String jdbcUrl = "jdbc:mysql://" + credential.getAsset().getHostUrl();

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
        return grantStr.contains("SELECT") || grantStr.contains("INSERT") ||
                grantStr.contains("UPDATE") || grantStr.contains("DELETE") ||
                grantStr.contains("CREATE") || grantStr.contains("ALTER");
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
            case "SELECT":
                return "READ ACCESS";
            case "EXECUTE":
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

    private Connection getConnectionFromAssetCredential(AssetCredential credential) throws SQLException {
        String jdbcUrl;
        switch (credential.getAsset().getDatabaseType()) {
            case MYSQL:
                jdbcUrl = "jdbc:mysql://" + credential.getAsset().getHostUrl();
                break;
            case POSTGRESQL:
                jdbcUrl = "jdbc:postgresql://" + credential.getAsset().getHostUrl();
                break;
            case ORACLE:
                jdbcUrl = "jdbc:oracle:thin:@" + credential.getAsset().getHostUrl();
                break;
            case SQLSERVER:
                jdbcUrl = "jdbc:sqlserver://" + credential.getAsset().getHostUrl();
                break;
            default:
                throw new DatabaseAccessException(
                        "Unsupported database type: " + credential.getAsset().getDatabaseType(), null);
        }
        return DriverManager.getConnection(jdbcUrl, credential.getUsername(),
                credential.getPassword());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateAccessRequestCredentialPassword(AssetCredential devCredential, String newPassword) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        String userKey = keycloakService.getUserKey(CommonUtils.getKeycloakUserIdFromSession());
        String decPsd = CommonUtils.decrypt(userKey, devCredential.getPassword());
        devCredential.setPassword(decPsd);
        try (Connection connection = getConnectionFromAssetCredential(devCredential)) {
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
                    throw new DatabaseAccessException("Unsupported database type: " + devCredential.getAsset().getDatabaseType(), null);
            }
            statement = connection.prepareStatement(alterUserSql);
            statement.setString(1, devCredential.getUsername());
            statement.setString(2, newPassword);

            statement.execute();
            devCredential.setPassword(CommonUtils.encrypt(userKey, newPassword));
            assetCredentialsRepository.save(devCredential);
        } catch (SQLException e) {
            log.error("Error updating password", e);
            throw new DatabaseAccessException("Error updating password", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAccessRequestorInAsset(AssetCredential credential, User requestor, AccessRequest accessRequest,
            String existUsername, Map<String, String> newCredMapper) {

        try (Connection connection = getConnectionFromAssetCredential(credential)) {
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


                String userKey = keycloakService.getUserKeyByEmail(requestor.getEmail());
                if (userKey != null && !userKey.isEmpty()) {
                    String encryptedPassword = CommonUtils.encrypt(userKey, password);
                    // Store user credentials in asset_credentials table
                    AssetCredential userCredential = new AssetCredential();
                    userCredential.setAsset(credential.getAsset());
                    userCredential.setUsername(username);
                    userCredential.setPassword(encryptedPassword);
                    userCredential.setUserAccessType(Roles.DEVELOPER.getOriginalName()); // Set appropriate role
                    userCredential.setUser(requestor);
                    assetCredentialsRepository.saveAndFlush(userCredential);
                    newCredMapper.put(Constants.EMAIL_VAR_DB_USERNAME, username);
                    newCredMapper.put(Constants.EMAIL_VAR_DB_PASSWORD, password);
                }
            }
        } else {
            username = existUsername;
        }
        return username;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeCredentialAccess(AssetCredential credential, AssetCredential ownerCredential) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {

        try (Connection connection = getConnectionFromAssetCredential(ownerCredential)) { // Use owner's connection
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
                    throw new DatabaseAccessException("Unsupported database type: " + credential.getAsset().getDatabaseType(), null);
            }

            log.info("Successfully revoked access and dropped user: {}", credential.getUsername());
        } catch (SQLException e) {
            log.error("Error revoking access for user: " + credential.getUsername(), e);
            throw new DatabaseAccessException("Error revoking database access", e);
        }
    }

}