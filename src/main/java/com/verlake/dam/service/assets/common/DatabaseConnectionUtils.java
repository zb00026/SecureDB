package com.verlake.dam.service.assets.common;

import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.exception.DatabaseAccessException;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
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
     * 
     * @param asset The asset containing database connection information
     * @return Complete JDBC URL string
     * @throws DatabaseAccessException if database type is not supported
     */
    public String buildJdbcUrl(Asset asset) {
        if (asset == null) {
            throw new DatabaseAccessException("Asset cannot be null", null);
        }
        
        return switch (asset.getDatabaseType()) {
            case MYSQL -> Constants.JDBC_MYSQL_URL + asset.getHostUrl();
            case POSTGRESQL -> Constants.JDBC_POSTGRESQL_URL + asset.getHostUrl();
            case ORACLE -> Constants.JDBC_ORACLE_URL + asset.getHostUrl();
            case SQLSERVER -> Constants.JDBC_SQLSERVER_URL + asset.getHostUrl();
            default -> throw new DatabaseAccessException(
                    "Unsupported database type: " + asset.getDatabaseType(), null);
        };
    }
    
    /**
     * Creates a database connection using the provided credential
     * 
     * @param credential The credential containing connection information
     * @return Database connection
     * @throws SQLException if connection fails
     * @throws DatabaseAccessException if credential or asset is invalid
     */
    public Connection getConnectionFromAssetCredential(AssetCredential credential) throws SQLException {
        if (credential == null) {
            throw new DatabaseAccessException("Credential cannot be null", null);
        }
        
        if (credential.getAsset() == null) {
            throw new DatabaseAccessException("Asset in credential cannot be null", null);
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
            throw new DatabaseAccessException("Credential cannot be null", null);
        }
        
        if (credential.getPassword() == null || credential.getPassword().trim().isEmpty()) {
            log.warn("Credential ID: {} has no password", credential.getId());
            throw new DatabaseAccessException("User has no access to asset", null);
        }
        
        String userKey = keycloakService.getUserKey();
        
        try {
            log.debug("Decrypting password for credential ID: {}", credential.getId());
            return CommonUtils.decrypt(userKey, credential.getPassword());
        } catch (CommonUtils.CryptoException e) {
            log.warn("Failed to decrypt password for credential ID: {} - {}", 
                     credential.getId(), e.getClass().getSimpleName());
            throw new DatabaseAccessException("User has no access to asset", e);
        } catch (Exception e) {
            log.error("Unexpected error during password decryption for credential ID: {}", 
                      credential.getId(), e);
            throw new DatabaseAccessException("User has no access to asset", e);
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
        return credential != null && 
               credential.getPassword() != null && 
               !credential.getPassword().trim().isEmpty();
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