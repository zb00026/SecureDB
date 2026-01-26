package com.verlake.dam.service.aws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verlake.dam.exception.DatabaseAccessException;
import com.verlake.dam.utils.Constants;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Service for interacting with AWS Secrets Manager
 * Uses IAM role credentials when available, falls back to access key/secret key
 */
@Service
@Slf4j
public class AWSSecretsManagerService {
    
    private final SecretsManagerClient secretsManagerClient;
    private final ObjectMapper objectMapper;
    
    public AWSSecretsManagerService(
            @Value("${aws.accessKeyId:#{null}}") String accessKey,
            @Value("${aws.secretKey:#{null}}") String secretKey,
            @Value("${aws.region}") String region) {
        this.objectMapper = new ObjectMapper();
        
        // Use IAM role credentials if access key/secret key are not provided
        AwsCredentialsProvider credentialsProvider = accessKey != null && secretKey != null
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
                : DefaultCredentialsProvider.builder().build();
        
        this.secretsManagerClient = SecretsManagerClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider)
                .build();
        
        log.info("AWS Secrets Manager service initialized with region: {}", region);
    }
    
    /**
     * Retrieves a secret value from AWS Secrets Manager
     * 
     * @param secretKey The secret key/ARN in AWS Secrets Manager
     * @return The secret value as a string
     * @throws DatabaseAccessException if the secret cannot be retrieved
     */
    public String getSecretValue(String secretKey) {
        if (secretKey == null || secretKey.trim().isEmpty()) {
            throw new DatabaseAccessException("AWS Secrets Manager key cannot be null or empty", null);
        }
        
        try {
            log.debug("Retrieving secret from AWS Secrets Manager with key: {}", secretKey);
            
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretKey)
                    .build();
            
            GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);
            
            // Handle both string and binary secrets (for database credentials, it should be string)
            String secretString = response.secretString();
            if (secretString == null) {
                // If secretString is null, check if it's a binary secret (unlikely for our use case)
                if (response.secretBinary() != null) {
                    log.warn("Secret from AWS Secrets Manager is binary, not string. This is unexpected for database credentials.");
                    throw new DatabaseAccessException(
                            Constants.getMessage(Constants.ERROR_AWS_SECRETS_MANAGER_RETRIEVAL_FAILED) + 
                            ": Secret is stored as binary, but string format is expected for database credentials", null);
                }
                throw new DatabaseAccessException(
                        Constants.getMessage(Constants.ERROR_AWS_SECRETS_MANAGER_RETRIEVAL_FAILED) + 
                        ": Secret value is null", null);
            }
            
            log.debug("Successfully retrieved secret from AWS Secrets Manager");
            
            return secretString;
            
        } catch (SecretsManagerException e) {
            log.error("Error retrieving secret from AWS Secrets Manager with key: {}", secretKey, e);
            String errorMessage = buildErrorMessage(e, secretKey);
            throw new DatabaseAccessException(errorMessage, e);
        } catch (Exception e) {
            log.error("Unexpected error retrieving secret from AWS Secrets Manager with key: {}", secretKey, e);
            throw new DatabaseAccessException(
                    Constants.getMessage("error.aws.secrets.manager.unexpected.error") + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Retrieves a password from AWS Secrets Manager
     * Supports both plain string secrets and JSON secrets with "password" field
     * 
     * @param secretKey The secret key/ARN in AWS Secrets Manager
     * @return The password as a string
     * @throws DatabaseAccessException if the secret cannot be retrieved or parsed
     */
    public String getPasswordFromSecret(String secretKey) {
        String secretValue = getSecretValue(secretKey);
        
        try {
            // Try to parse as JSON first
            JsonNode jsonNode = objectMapper.readTree(secretValue);
            if (jsonNode.has("password")) {
                return jsonNode.get("password").asText();
            } else if (jsonNode.has("Password")) {
                return jsonNode.get("Password").asText();
            } else if (jsonNode.has("PASSWORD")) {
                return jsonNode.get("PASSWORD").asText();
            }
            // If JSON but no password field, return the whole JSON as string
            log.warn("Secret from AWS Secrets Manager is JSON but has no 'password' field. Returning full JSON.");
            return secretValue;
        } catch (Exception e) {
            // Not JSON, return as plain string
            log.debug("Secret from AWS Secrets Manager is not JSON, returning as plain string");
            return secretValue;
        }
    }
    
    /**
     * Retrieves a username from AWS Secrets Manager (if stored in JSON format)
     * 
     * @param secretKey The secret key/ARN in AWS Secrets Manager
     * @return The username as a string, or null if not found
     */
    public String getUsernameFromSecret(String secretKey) {
        String secretValue = getSecretValue(secretKey);
        
        try {
            JsonNode jsonNode = objectMapper.readTree(secretValue);
            if (jsonNode.has("username")) {
                String username = jsonNode.get("username").asText();
                log.debug("Retrieved username '{}' from AWS Secrets Manager secret", username);
                return username;
            } else if (jsonNode.has("Username")) {
                String username = jsonNode.get("Username").asText();
                log.debug("Retrieved username '{}' from AWS Secrets Manager secret (capitalized)", username);
                return username;
            } else if (jsonNode.has("USERNAME")) {
                String username = jsonNode.get("USERNAME").asText();
                log.debug("Retrieved username '{}' from AWS Secrets Manager secret (uppercase)", username);
                return username;
            } else {
                Iterator<String> fieldNames = jsonNode.fieldNames();
                List<String> fields = new ArrayList<>();
                fieldNames.forEachRemaining(fields::add);
                log.warn("Secret from AWS Secrets Manager is JSON but has no username field. Available fields: {}", 
                        fields.isEmpty() ? "none" : String.join(", ", fields));
            }
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            log.warn("Failed to parse secret from AWS Secrets Manager as JSON. Secret value starts with: {}", 
                    secretValue != null && secretValue.length() > 100 ? secretValue.substring(0, 100) + "..." : secretValue);
            log.debug("JSON parsing error: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Unexpected error parsing secret from AWS Secrets Manager as JSON: {}", e.getMessage());
            log.debug("Full error: ", e);
        }
        
        return null;
    }
    
    /**
     * Retrieves an SSH private key from AWS Secrets Manager
     * Supports both plain string secrets and JSON secrets with "privateKey", "sshPrivateKey", or "key" field
     * 
     * @param secretKey The secret key/ARN in AWS Secrets Manager
     * @return The SSH private key as a string
     * @throws DatabaseAccessException if the secret cannot be retrieved or parsed
     */
    public String getSSHPrivateKeyFromSecret(String secretKey) {
        String secretValue = getSecretValue(secretKey);
        
        try {
            // Try to parse as JSON first
            JsonNode jsonNode = objectMapper.readTree(secretValue);
            if (jsonNode.has("privateKey")) {
                return jsonNode.get("privateKey").asText();
            } else if (jsonNode.has("PrivateKey")) {
                return jsonNode.get("PrivateKey").asText();
            } else if (jsonNode.has("PRIVATE_KEY")) {
                return jsonNode.get("PRIVATE_KEY").asText();
            } else if (jsonNode.has("sshPrivateKey")) {
                return jsonNode.get("sshPrivateKey").asText();
            } else if (jsonNode.has("ssh_private_key")) {
                return jsonNode.get("ssh_private_key").asText();
            } else if (jsonNode.has("key")) {
                return jsonNode.get("key").asText();
            } else if (jsonNode.has("Key")) {
                return jsonNode.get("Key").asText();
            }
            // If JSON but no SSH key field, return the whole JSON as string
            Iterator<String> fieldNames = jsonNode.fieldNames();
            List<String> fields = new ArrayList<>();
            fieldNames.forEachRemaining(fields::add);
            log.warn("Secret from AWS Secrets Manager is JSON but has no SSH private key field. Available fields: {}. Returning full JSON.", 
                    fields.isEmpty() ? "none" : String.join(", ", fields));
            return secretValue;
        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            // Not JSON, return as plain string (assume it's the SSH private key)
            log.warn("Failed to parse secret from AWS Secrets Manager as JSON. Error: {}. Secret value starts with: {}", 
                    e.getMessage(), 
                    secretValue != null && secretValue.length() > 200 ? secretValue.substring(0, 200) + "..." : secretValue);
            log.debug("Returning as plain string (SSH private key)");
            return secretValue;
        } catch (Exception e) {
            // Not JSON, return as plain string (assume it's the SSH private key)
            log.warn("Error parsing secret from AWS Secrets Manager as JSON: {}. Returning as plain string (SSH private key)", e.getMessage());
            log.debug("Full error: ", e);
            return secretValue;
        }
    }
    
    /**
     * Builds an appropriate error message based on the type of AWS Secrets Manager exception
     * 
     * @param e The SecretsManagerException
     * @param secretKey The secret key that was being accessed
     * @return A user-friendly error message
     */
    private String buildErrorMessage(SecretsManagerException e, String secretKey) {
        String message = e.getMessage();
        int statusCode = e.statusCode();
        
        // Check for authorization/permission errors
        if (isAuthorizationError(message, statusCode)) {
            return String.format(
                "AWS Secrets Manager authorization error: The configured AWS IAM user/role does not have permission to access the secret '%s'. " +
                "Please ensure the IAM user/role has the 'secretsmanager:GetSecretValue' permission for this secret. " +
                "Original error: %s", 
                secretKey, message);
        }
        
        // Check for resource not found errors
        if (statusCode == 404 || (message != null && message.contains("ResourceNotFoundException"))) {
            return String.format(
                "AWS Secrets Manager secret not found: The secret '%s' does not exist in AWS Secrets Manager. " +
                "Please verify the secret name/ARN is correct. Original error: %s",
                secretKey, message);
        }
        
        // Check for access denied (different from authorization - usually means secret doesn't exist or wrong region)
        if (statusCode == 403 || (message != null && message.contains("AccessDeniedException"))) {
            return String.format(
                "AWS Secrets Manager access denied: Access to secret '%s' was denied. " +
                "This may indicate the secret doesn't exist, is in a different region, or the IAM user/role lacks permissions. " +
                "Original error: %s",
                secretKey, message);
        }
        
        // Generic error message
        return Constants.getMessage(Constants.ERROR_AWS_SECRETS_MANAGER_RETRIEVAL_FAILED) + ": " + message;
    }
    
    /**
     * Checks if the error is an authorization/permission error
     * 
     * @param message The error message
     * @param statusCode The HTTP status code
     * @return true if this is an authorization error
     */
    private boolean isAuthorizationError(String message, int statusCode) {
        if (message == null) {
            return false;
        }
        
        // Check for authorization-related keywords
        boolean hasAuthorizationKeywords = message.contains("not authorized") ||
                                         message.contains("does not have permission") ||
                                         message.contains("no identity-based policy allows") ||
                                         message.contains("AccessDenied") ||
                                         message.contains("UnauthorizedOperation");
        
        // Status code 400 with authorization keywords typically means permission issue
        // Status code 403 also indicates permission/access issues
        return hasAuthorizationKeywords && (statusCode == 400 || statusCode == 403);
    }
}




