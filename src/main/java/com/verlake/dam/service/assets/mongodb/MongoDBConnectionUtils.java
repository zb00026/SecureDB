package com.verlake.dam.service.assets.mongodb;

import com.mongodb.MongoSecurityException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.exception.DatabaseAccessException;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for MongoDB connection and operations
 */
@Slf4j
public class MongoDBConnectionUtils {
    
    /**
     * Private constructor to prevent instantiation
     */
    private MongoDBConnectionUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    /**
     * Creates a MongoDB connection string with credentials
     * Format: mongodb://username:password@host:port/database?authSource=database
     * 
     * Username and password are URL-encoded to handle special characters (e.g., #, @, etc.)
     * authSource is set to the database name from the asset, or 'admin' if not specified
     * 
     * @param asset The asset containing MongoDB connection information
     * @param username MongoDB username (will be URL-encoded)
     * @param password MongoDB password (will be URL-encoded)
     * @return MongoDB connection string with proper URL encoding and authSource parameter
     */
    public static String buildMongoConnectionString(Asset asset, String username, String password) {
        String authSource = (asset.getDatabaseName() != null && !asset.getDatabaseName().isEmpty()) 
            ? asset.getDatabaseName() 
            : Constants.MONGODB_ADMIN_DATABASE;
        return buildMongoConnectionString(asset, username, password, authSource);
    }
    
    /**
     * Creates a MongoDB connection string with credentials and specified authSource
     * Format: mongodb://username:password@host:port/database?authSource=authSource
     * 
     * Username and password are URL-encoded to handle special characters (e.g., #, @, etc.)
     * 
     * @param asset The asset containing MongoDB connection information
     * @param username MongoDB username (will be URL-encoded)
     * @param password MongoDB password (will be URL-encoded)
     * @param authSource The database to use for authentication
     * @return MongoDB connection string with proper URL encoding and authSource parameter
     */
    private static String buildMongoConnectionString(Asset asset, String username, String password, String authSource) {
        StringBuilder connectionString = new StringBuilder(Constants.MONGODB_CONNECTION_URL);
        
        // Add credentials if provided (with URL encoding for special characters)
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8);
            String encodedPassword = URLEncoder.encode(password, StandardCharsets.UTF_8);
            connectionString.append(encodedUsername).append(":").append(encodedPassword).append("@");
        }
        
        // Add host
        if (asset.getHostAddress() != null && !asset.getHostAddress().isEmpty()) {
            connectionString.append(asset.getHostAddress());
        }
        
        // Add port if present
        if (asset.getPortNumber() != null && !asset.getPortNumber().isEmpty()) {
            connectionString.append(":").append(asset.getPortNumber());
        }
        
        // Add database name if present
        if (asset.getDatabaseName() != null && !asset.getDatabaseName().isEmpty()) {
            connectionString.append("/").append(asset.getDatabaseName());
        }
        
        // Add authSource parameter
        // This is important for MongoDB authentication - authSource specifies which database contains the user
        connectionString.append("?authSource=").append(authSource);
        
        return connectionString.toString();
    }
    
    /**
     * Creates a MongoDB client connection
     * Tries authentication with the database name first, then falls back to 'admin' if authentication fails.
     * This handles both regular users (in their database) and admin users (in admin database).
     */
    public static MongoClient createMongoClient(AssetCredential credential) {
        if (credential == null || credential.getAsset() == null) {
            throw new DatabaseAccessException(Constants.getMessage("error.admin.credential.cannot.be.null"), null);
        }
        
        String username = credential.getUsername();
        String password = credential.getPassword();
        Asset asset = credential.getAsset();
        
        // Determine initial authSource (try database name first)
        String initialAuthSource = (asset.getDatabaseName() != null && !asset.getDatabaseName().isEmpty()) 
            ? asset.getDatabaseName() 
            : Constants.MONGODB_ADMIN_DATABASE;
        
        // Log connection attempt (without password)
        log.debug("Creating MongoDB client for user: {}, host: {}, database: {}, initial authSource: {}", 
                  username, 
                  asset.getHostAddress(),
                  asset.getDatabaseName() != null ? asset.getDatabaseName() : Constants.MONGODB_ADMIN_DATABASE,
                  initialAuthSource);
        
        // First attempt: try with database name as authSource (for regular users)
        String connectionString = buildMongoConnectionString(asset, username, password, initialAuthSource);
        
        try {
            return MongoClients.create(connectionString);
        } catch (com.mongodb.MongoSecurityException e) {
            // If authentication failed and we haven't tried 'admin' yet, retry with 'admin' as authSource
            // This handles admin users like 'root' that exist in the 'admin' database
            if (!Constants.MONGODB_ADMIN_DATABASE.equals(initialAuthSource)) {
                log.debug("MongoDB authentication failed with authSource: {}. Retrying with '{}' database for user: {}", 
                          initialAuthSource, Constants.MONGODB_ADMIN_DATABASE, username);
                
                try {
                    String adminConnectionString = buildMongoConnectionString(asset, username, password, Constants.MONGODB_ADMIN_DATABASE);
                    MongoClient client = MongoClients.create(adminConnectionString);
                    log.info("MongoDB authentication succeeded with '{}' database for user: {}", Constants.MONGODB_ADMIN_DATABASE, username);
                    return client;
                } catch (com.mongodb.MongoSecurityException adminException) {
                    // Both attempts failed - log both errors
            log.error("MongoDB authentication failed for user: {} on host: {}. " +
                              "Tried authSource: {} and '{}'. " +
                              "Possible causes: 1) Wrong password, 2) User does not exist in either database, " +
                              "3) Password decryption issue. " +
                              "First error: {}, Second error: {}", 
                              username, asset.getHostAddress(), initialAuthSource, Constants.MONGODB_ADMIN_DATABASE,
                              e.getMessage(), adminException.getMessage());
                    throw new DatabaseAccessException(
                        "MongoDB authentication failed for user '" + username + "'. " +
                        "Tried authenticating against '" + initialAuthSource + "' and '" + Constants.MONGODB_ADMIN_DATABASE + "' databases. " +
                        "Please verify the credentials are correct. Error: " + adminException.getMessage(), 
                        adminException);
                }
            } else {
                // Already tried 'admin', so authentication really failed
                log.error("MongoDB authentication failed for user: {} on host: {} (authSource: {}). " +
                          "Possible causes: 1) Wrong password, 2) User does not exist in {} database, " +
                      "3) Password decryption issue. Error: {}", 
                          username, asset.getHostAddress(), initialAuthSource, initialAuthSource, e.getMessage());
            throw new DatabaseAccessException(
                "MongoDB authentication failed for user '" + username + "'. " +
                    "Please verify the credentials are correct and the user exists in the '" + initialAuthSource + "' database. " +
                "Error: " + e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("Failed to create MongoDB client for user: {} on host: {}: {}", 
                      username, asset.getHostAddress(), e.getMessage(), e);
            throw new DatabaseAccessException("Failed to connect to MongoDB: " + e.getMessage(), e);
        }
    }
    
    /**
     * Gets MongoDB database instance
     */
    public static MongoDatabase getMongoDatabase(AssetCredential credential) {
        MongoClient client = createMongoClient(credential);
        String databaseName = credential.getAsset().getDatabaseName();
        
        if (databaseName == null || databaseName.isEmpty()) {
            databaseName = Constants.MONGODB_ADMIN_DATABASE; // Default to admin database
        }
        
        return client.getDatabase(databaseName);
    }
    
    /**
     * Lists all users in MongoDB
     */
    public static List<String> listMongoDBUsers(MongoDatabase database) {
        List<String> users = new ArrayList<>();
        try {
            Document result = database.runCommand(new Document(Constants.MONGODB_COMMAND_USERS_INFO, 1));
            List<Document> userList = result.getList(Constants.MONGODB_FIELD_USERS, Document.class);
            
            if (userList != null) {
                for (Document user : userList) {
                    String username = user.getString("user");
                    if (username != null && !username.isEmpty()) {
                        users.add(username);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to list MongoDB users: {}", e.getMessage(), e);
            throw new DatabaseAccessException("Failed to list MongoDB users: " + e.getMessage(), e);
        }
        return users;
    }
    
    /**
     * Checks if a user exists in MongoDB
     */
    public static boolean userExists(MongoDatabase database, String username) {
        try {
            Document result = database.runCommand(new Document(Constants.MONGODB_COMMAND_USERS_INFO, username));
            List<Document> userList = result.getList(Constants.MONGODB_FIELD_USERS, Document.class);
            return userList != null && !userList.isEmpty();
        } catch (Exception e) {
            log.error("Failed to check MongoDB user existence: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Creates a user in MongoDB
     */
    public static void createUser(MongoDatabase database, String username, String password) {
        try {
            Document command = new Document(Constants.MONGODB_COMMAND_CREATE_USER, username)
                .append(Constants.MONGODB_FIELD_PWD, password)
                .append(Constants.MONGODB_FIELD_ROLES, new ArrayList<>());
            
            database.runCommand(command);
            log.info("Successfully created MongoDB user: {}", username);
        } catch (Exception e) {
            log.error("Failed to create MongoDB user: {}", e.getMessage(), e);
            throw new DatabaseAccessException("Failed to create MongoDB user: " + e.getMessage(), e);
        }
    }
    
    /**
     * Updates a user's password in MongoDB
     */
    public static void updateUserPassword(MongoDatabase database, String username, String newPassword) {
        try {
            Document command = new Document(Constants.MONGODB_COMMAND_UPDATE_USER, username)
                .append(Constants.MONGODB_FIELD_PWD, newPassword);
            
            database.runCommand(command);
            log.info("Successfully updated password for MongoDB user: {}", username);
        } catch (Exception e) {
            log.error("Failed to update MongoDB user password: {}", e.getMessage(), e);
            throw new DatabaseAccessException("Failed to update MongoDB user password: " + e.getMessage(), e);
        }
    }
    
    /**
     * Drops a user from MongoDB
     */
    public static void dropUser(MongoDatabase database, String username) {
        try {
            Document command = new Document(Constants.MONGODB_COMMAND_DROP_USER, username);
            database.runCommand(command);
            log.info("Successfully dropped MongoDB user: {}", username);
        } catch (Exception e) {
            log.error("Failed to drop MongoDB user: {}", e.getMessage(), e);
            throw new DatabaseAccessException("Failed to drop MongoDB user: " + e.getMessage(), e);
        }
    }
    
    /**
     * Lists all collections in MongoDB database
     */
    public static List<String> listCollections(MongoDatabase database) {
        List<String> collections = new ArrayList<>();
        try {
            MongoIterable<String> collectionNames = database.listCollectionNames();
            for (String name : collectionNames) {
                collections.add(name);
            }
        } catch (Exception e) {
            log.error("Failed to list MongoDB collections: {}", e.getMessage(), e);
            throw new DatabaseAccessException("Failed to list MongoDB collections: " + e.getMessage(), e);
        }
        return collections;
    }
    
    /**
     * Locks a MongoDB user by removing all roles (effectively disabling access)
     */
    public static void lockUser(MongoDatabase database, String username) {
        try {
            Document command = new Document(Constants.MONGODB_COMMAND_UPDATE_USER, username)
                .append(Constants.MONGODB_FIELD_ROLES, new ArrayList<>());
            
            database.runCommand(command);
            log.info("Successfully locked MongoDB user: {}", username);
        } catch (Exception e) {
            log.error("Failed to lock MongoDB user: {}", e.getMessage(), e);
            throw new DatabaseAccessException("Failed to lock MongoDB user: " + e.getMessage(), e);
        }
    }
    
    /**
     * Unlocks a MongoDB user by granting read role (basic access)
     */
    public static void unlockUser(MongoDatabase database, String username) {
        try {
            String dbName = database.getName();
            Document command = new Document(Constants.MONGODB_COMMAND_UPDATE_USER, username)
                .append(Constants.MONGODB_FIELD_ROLES, List.of(new Document(Constants.MONGODB_FIELD_ROLE, Constants.MONGODB_ROLE_READ).append(Constants.MONGODB_FIELD_DB, dbName)));
            
            database.runCommand(command);
            log.info("Successfully unlocked MongoDB user: {}", username);
        } catch (Exception e) {
            log.error("Failed to unlock MongoDB user: {}", e.getMessage(), e);
            throw new DatabaseAccessException("Failed to unlock MongoDB user: " + e.getMessage(), e);
        }
    }
    
    /**
     * Checks if a MongoDB user is locked (has no roles)
     */
    public static boolean isUserLocked(MongoDatabase database, String username) {
        try {
            Document command = new Document(Constants.MONGODB_COMMAND_USERS_INFO, username);
            Document result = database.runCommand(command);
            List<Document> userList = result.getList(Constants.MONGODB_FIELD_USERS, Document.class);
            
            if (userList != null && !userList.isEmpty()) {
                Document user = userList.get(0);
                List<Document> roles = user.getList(Constants.MONGODB_FIELD_ROLES, Document.class);
                return roles == null || roles.isEmpty();
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to check if MongoDB user is locked: {}", e.getMessage(), e);
            return false;
        }
    }
}

