package com.verlake.dam.service.assets.common;

import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.enums.DatabaseType;
import com.verlake.dam.exception.DatabaseAccessException;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.aws.AWSSecretsManagerService;
import com.verlake.dam.service.settings.SystemSettingsService;
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
    private final AWSSecretsManagerService awsSecretsManagerService;
    private final SystemSettingsService systemSettingsService;
    
    public DatabaseConnectionUtils(
            KeycloakService keycloakService,
            AWSSecretsManagerService awsSecretsManagerService,
            SystemSettingsService systemSettingsService) {
        this.keycloakService = keycloakService;
        this.awsSecretsManagerService = awsSecretsManagerService;
        this.systemSettingsService = systemSettingsService;
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
     * Decrypts credential password using the user's key from Keycloak or retrieves from AWS Secrets Manager
     * 
     * @param credential The credential containing encrypted password or AWS Secrets Manager key
     * @return Decrypted password
     * @throws DatabaseAccessException if decryption fails or credential is invalid
     */
    public String decryptCredentialPassword(AssetCredential credential) {
        if (credential == null) {
            throw new DatabaseAccessException(Constants.getMessage(Constants.ERROR_ADMIN_CREDENTIAL_CANNOT_BE_NULL), null);
        }
        
        if (shouldUseAwsSecretsManager(credential)) {
            return retrievePasswordFromAwsSecretsManager(credential);
        }
        
        return decryptTraditionalPassword(credential);
    }
    
    /**
     * Checks if AWS Secrets Manager should be used for this credential
     */
    private boolean shouldUseAwsSecretsManager(AssetCredential credential) {
        boolean awsSecretsManagerEnabled = Boolean.parseBoolean(
                systemSettingsService.getSettingValue(
                        Constants.AWS_SECRETS_MANAGER_ENABLED_KEY,
                        Constants.AWS_SECRETS_MANAGER_ENABLED_DEFAULT));
        
        return awsSecretsManagerEnabled 
                && credential.getAwsSecretsManagerKey() != null 
                && !credential.getAwsSecretsManagerKey().trim().isEmpty();
    }
    
    /**
     * Retrieves password from AWS Secrets Manager and updates username if needed
     */
    private String retrievePasswordFromAwsSecretsManager(AssetCredential credential) {
        log.debug("Retrieving password from AWS Secrets Manager for credential ID: {}", credential.getId());
        try {
            String password = awsSecretsManagerService.getPasswordFromSecret(credential.getAwsSecretsManagerKey());
            updateUsernameFromSecretIfNeeded(credential);
            return password;
        } catch (Exception e) {
            log.error("Failed to retrieve password from AWS Secrets Manager for credential ID: {}", 
                     credential.getId(), e);
            throw new DatabaseAccessException(
                    Constants.getMessage("error.aws.secrets.manager.retrieval.failed") + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Updates credential username from AWS Secrets Manager secret if username is not set
     */
    private void updateUsernameFromSecretIfNeeded(AssetCredential credential) {
        if (credential.getUsername() == null || credential.getUsername().trim().isEmpty()) {
            String username = awsSecretsManagerService.getUsernameFromSecret(credential.getAwsSecretsManagerKey());
            if (username != null && !username.trim().isEmpty()) {
                log.debug("Retrieved username from AWS Secrets Manager for credential ID: {}", credential.getId());
                credential.setUsername(username);
            }
        }
    }
    
    /**
     * Decrypts traditional encrypted password using user's key
     */
    private String decryptTraditionalPassword(AssetCredential credential) {
        validatePasswordExists(credential);
        
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
     * Validates that credential has a password
     */
    private void validatePasswordExists(AssetCredential credential) {
        if (credential.getPassword() == null || credential.getPassword().trim().isEmpty()) {
            log.warn("Credential ID: {} has no password and no AWS Secrets Manager key", credential.getId());
            throw new DatabaseAccessException(Constants.getMessage(Constants.ERROR_USER_NO_ACCESS_TO_ASSET), null);
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
     * Validates that a credential has a valid password or AWS Secrets Manager key
     * 
     * @param credential The credential to validate
     * @return true if credential has a valid password or AWS Secrets Manager key, false otherwise
     */
    public boolean hasValidPassword(AssetCredential credential) {
        if (credential == null) {
            return false;
        }
        
        // Check if AWS Secrets Manager is enabled
        boolean awsSecretsManagerEnabled = Boolean.parseBoolean(
                systemSettingsService.getSettingValue(
                        Constants.AWS_SECRETS_MANAGER_ENABLED_KEY,
                        Constants.AWS_SECRETS_MANAGER_ENABLED_DEFAULT));
        
        if (awsSecretsManagerEnabled && credential.getAwsSecretsManagerKey() != null 
                && !credential.getAwsSecretsManagerKey().trim().isEmpty()) {
            return true;
        }
        
        return credential.getPassword() != null && !credential.getPassword().trim().isEmpty();
    }
    
    /**
     * Decrypts SSH private key using the user's key from Keycloak or retrieves from AWS Secrets Manager
     * 
     * @param credential The credential containing encrypted SSH key or AWS Secrets Manager key
     * @return Decrypted SSH private key
     * @throws DatabaseAccessException if decryption fails or credential is invalid
     */
    public String decryptSSHPrivateKey(AssetCredential credential) {
        return decryptSSHPrivateKey(credential, null);
    }
    
    /**
     * Decrypts SSH private key using the user's key from Keycloak or retrieves from AWS Secrets Manager
     * 
     * @param credential The credential containing encrypted SSH key or AWS Secrets Manager key
     * @param userKey Optional user encryption key (if null, will be retrieved from KeycloakService)
     * @return Decrypted SSH private key
     * @throws DatabaseAccessException if decryption fails or credential is invalid
     */
    public String decryptSSHPrivateKey(AssetCredential credential, String userKey) {
        if (credential == null) {
            throw new DatabaseAccessException(Constants.getMessage("error.admin.credential.cannot.be.null"), null);
        }
        
        if (shouldUseAwsSecretsManager(credential)) {
            return retrieveSshKeyFromAwsSecretsManager(credential);
        }
        
        return decryptTraditionalSshKey(credential, userKey);
    }
    
    /**
     * Retrieves SSH private key from AWS Secrets Manager and updates username if needed
     */
    private String retrieveSshKeyFromAwsSecretsManager(AssetCredential credential) {
        log.debug("Retrieving SSH private key from AWS Secrets Manager for credential ID: {}", credential.getId());
        try {
            String sshPrivateKey = awsSecretsManagerService.getSSHPrivateKeyFromSecret(credential.getAwsSecretsManagerKey());
            updateUsernameFromSecretIfNeeded(credential);
            return sshPrivateKey;
        } catch (Exception e) {
            log.error("Failed to retrieve SSH private key from AWS Secrets Manager for credential ID: {}", 
                     credential.getId(), e);
            throw new DatabaseAccessException(
                    Constants.getMessage(Constants.ERROR_AWS_SECRETS_MANAGER_RETRIEVAL_FAILED) + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Decrypts traditional encrypted SSH key using user's key
     * 
     * @param credential The credential containing encrypted SSH key
     * @param userKey Optional user encryption key (if null, will be retrieved from KeycloakService)
     * @return Decrypted SSH private key
     */
    private String decryptTraditionalSshKey(AssetCredential credential, String userKey) {
        validateSshKeyExists(credential);
        
        // Use provided userKey if available, otherwise get from KeycloakService
        if (userKey == null || userKey.isEmpty()) {
            userKey = keycloakService.getUserKey();
        }
        
        if (userKey == null || userKey.isEmpty()) {
            log.error("User encryption key not available for credential ID: {}", credential.getId());
            throw new DatabaseAccessException(
                Constants.getMessage(Constants.ERROR_USER_NO_ACCESS_TO_ASSET) + ": User encryption key not available", null);
        }
        
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
     * Validates that credential has an SSH key file
     */
    private void validateSshKeyExists(AssetCredential credential) {
        if (credential.getSshKeyFile() == null || credential.getSshKeyFile().trim().isEmpty()) {
            log.warn("Credential ID: {} has no SSH key file and no AWS Secrets Manager key", credential.getId());
            throw new DatabaseAccessException(Constants.getMessage(Constants.ERROR_ADMIN_CREDENTIAL_CANNOT_BE_NULL), null);
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