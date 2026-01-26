package com.verlake.dam.service.assets.common;

import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.enums.DatabaseType;
import com.verlake.dam.exception.DatabaseAccessException;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Utility class for common database connection and credential operations
 * Eliminates code duplication between AssetService and DatabaseAccessService
 */
@Component
@Slf4j
public class DatabaseConnectionUtils {
    
    private final KeycloakService keycloakService;
    
    public DatabaseConnectionUtils(KeycloakService keycloakService) {
        this.keycloakService = keycloakService;
    }
    
    /**
     * Builds JDBC URL based on asset database type and host information
     * Note: MongoDB uses connection strings, not JDBC URLs
     * 
     * @param asset The asset containing database connection information
     * @return Complete JDBC URL string (or MongoDB connection string format)
     * @throws DatabaseAccessException if database type is not supported
     */
    public String buildJdbcUrl(Asset asset) {
        if (asset == null) {
            throw new DatabaseAccessException(Constants.getMessage("error.asset.cannot.be.null"), null);
        }
        
        return switch (asset.getDatabaseType()) {
            case MYSQL -> Constants.JDBC_MYSQL_URL + asset.getHostUrl();
            case POSTGRESQL -> Constants.JDBC_POSTGRESQL_URL + asset.getHostUrl();
            case ORACLE -> Constants.JDBC_ORACLE_URL + asset.getHostUrl();
            case SQLSERVER -> Constants.JDBC_SQLSERVER_URL + asset.getHostUrl() + Constants.JDBC_SQLSERVER_SSL_PARAMS;
            case MONGODB -> buildMongoConnectionString(asset);
            default -> throw new DatabaseAccessException(
                    Constants.getMessage(Constants.ERROR_UNSUPPORTED_DATABASE_TYPE) + asset.getDatabaseType(), null);
        };
    }
    
    /**
     * Builds MongoDB connection string
     * Format: mongodb://[username:password@]host[:port][/database]?authSource=database
     * authSource is set to the database name from the asset, or 'admin' if not specified
     * 
     * @param asset The asset containing MongoDB connection information
     * @return MongoDB connection string
     */
    private String buildMongoConnectionString(Asset asset) {
        StringBuilder connectionString = new StringBuilder(Constants.MONGODB_CONNECTION_URL);
        
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
        
        // Add authSource parameter (use database name from asset, or default to admin)
        // This is important for MongoDB authentication - authSource specifies which database contains the user
        String authSource = (asset.getDatabaseName() != null && !asset.getDatabaseName().isEmpty()) 
            ? asset.getDatabaseName() 
            : "admin";
        connectionString.append("?authSource=").append(authSource);
        
        return connectionString.toString();
    }
    
    /**
     * Creates a database connection using the provided credential
     * Note: MongoDB is not supported by this method - use MongoDBConnectionUtils instead
     * 
     * @param credential The credential containing connection information
     * @return Database connection (JDBC Connection)
     * @throws SQLException if connection fails
     * @throws DatabaseAccessException if credential or asset is invalid, or if MongoDB is used
     */
    public Connection getConnectionFromAssetCredential(AssetCredential credential) throws SQLException {
        if (credential == null) {
            throw new DatabaseAccessException(Constants.getMessage(Constants.ERROR_ADMIN_CREDENTIAL_CANNOT_BE_NULL), null);
        }
        
        if (credential.getAsset() == null) {
            throw new DatabaseAccessException(Constants.getMessage(Constants.ERROR_ASSET_CANNOT_BE_NULL), null);
        }
        
        // MongoDB doesn't use JDBC - throw exception to indicate this method cannot be used
        if (credential.getAsset().getDatabaseType() == DatabaseType.MONGODB) {
            throw new DatabaseAccessException(
                "MongoDB connections must use MongoDBConnectionUtils.createMongoClient() instead of JDBC Connection", 
                null);
        }
        
        String jdbcUrl = buildJdbcUrl(credential.getAsset());
        return DriverManager.getConnection(jdbcUrl, credential.getUsername(), credential.getPassword());
    }
    
    /**
     * Decrypts credential password using the user's key from Keycloak
     * 
     * @param credential The credential containing encrypted password
     * @return Decrypted password
     * @throws DatabaseAccessException if decryption fails or credential is invalid
     */
    public String decryptCredentialPassword(AssetCredential credential) {
        if (credential == null) {
            throw new DatabaseAccessException(Constants.getMessage(Constants.ERROR_ADMIN_CREDENTIAL_CANNOT_BE_NULL), null);
        }
        
        if (credential.getPassword() == null || credential.getPassword().trim().isEmpty()) {
            log.warn("Credential ID: {} has no password", credential.getId());
            throw new DatabaseAccessException(Constants.getMessage(Constants.ERROR_USER_NO_ACCESS_TO_ASSET), null);
        }
        
        String userKey = keycloakService.getUserKey();
        
        try {
            log.debug("Decrypting password for credential ID: {}", credential.getId());
            return CommonUtils.decrypt(userKey, credential.getPassword());
        } catch (CommonUtils.CryptoException e) {
            log.warn("Failed to decrypt password for credential ID: {} - {}", 
                     credential.getId(), e.getClass().getSimpleName());
            throw new DatabaseAccessException(Constants.getMessage(Constants.ERROR_USER_NO_ACCESS_TO_ASSET), e);
        } catch (Exception e) {
            log.error("Unexpected error during password decryption for credential ID: {}", 
                      credential.getId(), e);
            throw new DatabaseAccessException(Constants.getMessage(Constants.ERROR_USER_NO_ACCESS_TO_ASSET), e);
        }
    }
    
    /**
     * Creates a temporary credential with decrypted password for database operations
     * 
     * @param asset The asset for the credential
     * @param sourceCredential The source credential to copy from
     * @param decryptedPassword The decrypted password to use
     * @return Temporary credential with decrypted password
     */
    public AssetCredential createTempCredential(Asset asset, AssetCredential sourceCredential, String decryptedPassword) {
        AssetCredential tempCredential = new AssetCredential();
        tempCredential.setAsset(asset);
        tempCredential.setUsername(sourceCredential.getUsername());
        tempCredential.setPassword(decryptedPassword);
        tempCredential.setUser(sourceCredential.getUser());
        tempCredential.setUserAccessType(sourceCredential.getUserAccessType());
        return tempCredential;
    }
    
    /**
     * Decrypts credential password and creates a temporary credential for database operations
     * 
     * @param credential The credential to decrypt and create temp credential from
     * @return Temporary credential with decrypted password
     * @throws DatabaseAccessException if decryption fails
     */
    public AssetCredential createDecryptedTempCredential(AssetCredential credential) {
        String decryptedPassword = decryptCredentialPassword(credential);
        return createTempCredential(credential.getAsset(), credential, decryptedPassword);
    }
    
    /**
     * Validates that a credential has a valid password
     * 
     * @param credential The credential to validate
     * @return true if credential has a valid password, false otherwise
     */
    public boolean hasValidPassword(AssetCredential credential) {
        if (credential == null) {
            return false;
        }
        
        return credential.getPassword() != null && !credential.getPassword().trim().isEmpty();
    }
    
    /**
     * Decrypts SSH private key using the user's key from Keycloak
     * 
     * @param credential The credential containing encrypted SSH key
     * @return Decrypted SSH private key
     * @throws DatabaseAccessException if decryption fails or credential is invalid
     */
    public String decryptSSHPrivateKey(AssetCredential credential) {
        if (credential == null) {
            throw new DatabaseAccessException(Constants.getMessage(Constants.ERROR_ADMIN_CREDENTIAL_CANNOT_BE_NULL), null);
        }
        
        if (credential.getSshKeyFile() == null || credential.getSshKeyFile().trim().isEmpty()) {
            log.warn("Credential ID: {} has no SSH key file", credential.getId());
            throw new DatabaseAccessException(Constants.getMessage(Constants.ERROR_USER_NO_ACCESS_TO_ASSET), null);
        }
        
        String userKey = keycloakService.getUserKey();
        
        try {
            log.debug("Decrypting SSH private key for credential ID: {}", credential.getId());
            return CommonUtils.decrypt(userKey, credential.getSshKeyFile());
        } catch (CommonUtils.CryptoException e) {
            log.warn("Failed to decrypt SSH private key for credential ID: {} - {}", 
                     credential.getId(), e.getClass().getSimpleName());
            throw new DatabaseAccessException(Constants.getMessage(Constants.ERROR_USER_NO_ACCESS_TO_ASSET), e);
        } catch (Exception e) {
            log.error("Unexpected error during SSH private key decryption for credential ID: {}", 
                      credential.getId(), e);
            throw new DatabaseAccessException(Constants.getMessage(Constants.ERROR_USER_NO_ACCESS_TO_ASSET), e);
        }
    }
    
    /**
     * Determines if an exception is related to credential or access issues
     * 
     * @param e The exception to check
     * @return true if the exception is credential-related, false otherwise
     */
    public boolean isCredentialRelatedError(Exception e) {
        if (e.getMessage() == null) {
            return false;
        }
        
        String message = e.getMessage().toLowerCase();
        return message.contains("credential") || 
               message.contains("password") || 
               message.contains("access") ||
               message.contains("user has no access to asset") ||
               message.contains("tag mismatch") ||
               message.contains("badpadding") ||
               message.contains("crypto");
    }
} 