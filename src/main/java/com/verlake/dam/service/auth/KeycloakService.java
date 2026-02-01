package com.verlake.dam.service.auth;


import com.verlake.dam.configuration.ConditionalOnAuthProviderParam;
import com.verlake.dam.utils.Constants;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.*;

@Slf4j
@Service
@ConditionalOnAuthProviderParam(field = "auth.provider", containProvider = "keycloak")
public class KeycloakService {

    private final String authServerUrl;
    private final String clientId;
    private final String clientSecret;
    private final String realmName;
    private final SecureRandom secureRandom = new SecureRandom();
    
    // Cache SSO status to avoid repeated API calls
    private Boolean ssoEnabled = null;
    
    // Thread-local cache for user-key to avoid multiple calls in the same request
    private static final ThreadLocal<String> userKeyCache = new ThreadLocal<>();
    private static final ThreadLocal<Long> userKeyCacheTime = new ThreadLocal<>();
    private static final long CACHE_TTL_MS = 5000; // Cache for 5 seconds

    public KeycloakService(@Value("${keycloak.auth-server-url}") String keycloakAuthServerUrl,
                           @Value("${spring.security.oauth2.client.registration.keycloak.client-id}") String clientId,
                           @Value("${spring.security.oauth2.client.registration.keycloak.client-secret}") String clientSecret,
                           @Value("${keycloak.realm}") String realmName) {
        this.realmName = realmName;
        this.authServerUrl = keycloakAuthServerUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }
    
    /**
     * Check if SSO is enabled in Keycloak by checking for identity providers
     */
    public boolean isSSOEnabled() {
        if (ssoEnabled != null) {
            return ssoEnabled;
        }
        
        try {
            log.debug("Checking if SSO is enabled in Keycloak");
            RealmResource realmResource = getRealmInstance();
            
            // Check for identity providers
            var identityProviders = realmResource.identityProviders().findAll();
            
            ssoEnabled = identityProviders != null && !identityProviders.isEmpty();
            log.info("SSO status: {} (Identity providers found: {})", 
                ssoEnabled, identityProviders != null ? identityProviders.size() : 0);
            
            return ssoEnabled;
            
        } catch (Exception e) {
            log.warn("Error checking SSO status in Keycloak (likely missing view-identity-providers role): {}", e.getMessage());
            log.debug("Full error details:", e);
            ssoEnabled = false;
            return false;
        }
    }
    
    /**
     * Get the appropriate email template for Keycloak users
     * Returns "sso-invite" if SSO is enabled, "keycloak-invite" otherwise
     */
    public String getEmailTemplateForKeycloakUser() {
        if (isSSOEnabled()) {
            log.debug("Using SSO email template (Keycloak has identity providers)");
            return "sso-invite";
        } else {
            log.debug("Using standard Keycloak email template");
            return "keycloak-invite";
        }
    }

    public RealmResource getRealmInstance() {
        Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl(authServerUrl)
                .realm(realmName)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build();
        return keycloak.realm(realmName);
    }

    public RealmResource getRealmInstance(String jwtToken) {
        Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl(authServerUrl)
                .realm(realmName)
                .authorization("Bearer " + jwtToken) // Use the user's JWT token here
                .build();
        return keycloak.realm(realmName);
    }

    UserRepresentation getKeycloakUser(String username) {
        RealmResource realmResource = getRealmInstance();

        // Check if user exists by username or email
        UsersResource usersResource = realmResource.users();
        return findUserByUsernameOrEmail(usersResource, username);
    }

    public void saveUser(String username, String email, String firstName, String lastName, String password, boolean isTemporaryPsd) {
        RealmResource realmResource = getRealmInstance();

        // Check if user exists by username or email
        UsersResource usersResource = realmResource.users();
        UserRepresentation existingUser = getKeycloakUser(username);

        if (existingUser != null) {
            // User exists, so update the user
            updateUser(usersResource, existingUser.getId(), firstName, lastName, password, isTemporaryPsd);
        } else {
            // User doesn't exist, create new user
            createNewUser(usersResource, username, email, firstName, lastName, password, isTemporaryPsd);
        }
    }

    private UserRepresentation findUserByUsernameOrEmail(UsersResource usersResource, String username) {
        // Find user by username first
        List<UserRepresentation> users = usersResource.search(username);
        if (users != null && !users.isEmpty()) {
            return users.get(0);  // Return the first matched user
        } else {
            users = usersResource.searchByEmail(username, true);
            if (users != null && !users.isEmpty()) {
                return users.get(0);
            }
        }
        return null;  // No user found
    }

    private void createNewUser(UsersResource usersResource, String username, String email, String firstName, String lastName, String password, boolean isTemporaryPsd) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);

        // Set password credentials - avoid deprecated credentials format
        if (password != null && !password.isEmpty()) {
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(isTemporaryPsd);
            user.setCredentials(List.of(credential));
        }

        Response response = usersResource.create(user);
        if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
            String errorMessage = response.readEntity(String.class);
            log.error("Failed to create user in Keycloak: {}", errorMessage);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create user in Keycloak : " + errorMessage);
        }

        // Get the user ID from the response
        String userId = response.getLocation().getPath().replaceAll(".*/([^/]+)$", "$1");

        // Set password after user creation to avoid deprecated format warning
        if (password != null && !password.isEmpty()) {
            UserResource userResource = usersResource.get(userId);
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(isTemporaryPsd);
            userResource.resetPassword(credential);
        }

        // Assign manage-account role
        RealmResource realmResource = getRealmInstance();
        List<ClientRepresentation> clients = realmResource.clients().findAll();
        log.info("Available clients:");
        for (ClientRepresentation client : clients) {
            log.info("Client ID: {}, Client Name: {}, Internal ID: {}", client.getClientId(), client.getName(), client.getId());
        }

        // Then try to find the account client
        List<ClientRepresentation> accountClients = realmResource.clients().findByClientId("account");
        if (accountClients.isEmpty()) {
            log.error("No 'account' client found in realm");
            // Handle the error case
        } else {
            String accountClientId = accountClients.get(0).getId();
            log.info("Found account client with ID: {}", accountClientId);
            RoleRepresentation manageAccountRole = realmResource.clients().get(accountClientId).roles().get("manage-account").toRepresentation();
            realmResource.users().get(userId).roles().clientLevel(accountClientId).add(Collections.singletonList(manageAccountRole));
        }

        log.info("User created successfully in Keycloak: {}", username);
    }

    private void updateUser(UsersResource usersResource, String userId, String firstName, String lastName, String password, boolean isTemporaryPsd) {
        // Retrieve existing user
        UserResource userResource = usersResource.get(userId);
        UserRepresentation user = userResource.toRepresentation();

        // Update user information
        user.setFirstName(firstName);
        user.setLastName(lastName);

        // First update user info without credentials
        userResource.update(user);

        if (password != null && !password.isEmpty()) {
            // Update password separately using the proper method
            CredentialRepresentation credential = new CredentialRepresentation();
            credential.setType(CredentialRepresentation.PASSWORD);
            credential.setValue(password);
            credential.setTemporary(isTemporaryPsd);

            // Use resetPassword instead of setting credentials directly
            userResource.resetPassword(credential);
        }
    }

    public void updateUserKey(String userId, String jwtToken) {
        String userKey = getUserKeyViaAccountApi(jwtToken);

        if (userKey == null || userKey.isEmpty()) {
            String newUserKey = generateRandomUserKey();
            updateUserKeyViaAccountApi(userId, newUserKey, jwtToken);
        } else {
            log.debug("User key already exists for userId: {}", userId);
        }
    }

    public String getUserKeyViaAccountApi(String jwtToken) {
        int maxRetries = 3;
        int retryDelayMs = 100;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            String result = attemptGetUserKey(jwtToken, attempt, maxRetries);
            if (result != null) {
                return result;
            }
            
            if (attempt < maxRetries && !handleRetryDelay(attempt, retryDelayMs)) {
                return null;
            }
        }
        
        return null;
    }
    
    /**
     * Attempt to get user key from Account API
     */
    private String attemptGetUserKey(String jwtToken, int attempt, int maxRetries) {
        try {
            ResponseEntity<Map<String, Object>> response = getAccountData(jwtToken);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                logNon2xxResponse(response.getStatusCode(), attempt, maxRetries);
                return null;
            }
            return extractUserKeyFromResponse(response.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            return handleUnauthorizedException(e, attempt, maxRetries);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            return handleHttpClientException(e, attempt, maxRetries);
        } catch (Exception e) {
            return handleGenericException(e, attempt, maxRetries);
        }
    }
    
    /**
     * Extract user key from response body
     */
    private String extractUserKeyFromResponse(Map<String, Object> userData) {
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) userData.get(Constants.KEYCLOAK_CLIENT_ATTRIBUTES);
        if (attributes != null) {
            @SuppressWarnings("unchecked")
            List<String> userKeyList = (List<String>) attributes.get(Constants.KEYCLOAK_USER_KEY);
            if (userKeyList != null && !userKeyList.isEmpty()) {
                return userKeyList.get(0);
            }
        }
        return null;
    }
    
    /**
     * Handle 401 Unauthorized exception
     */
    private String handleUnauthorizedException(org.springframework.web.client.HttpClientErrorException.Unauthorized e, 
                                               int attempt, int maxRetries) {
        if (attempt < maxRetries) {
            log.warn("Unauthorized access to Account API (401). Retrying (attempt {}/{}). Error: {}", 
                    attempt, maxRetries, e.getMessage());
            return null; // Signal to retry
        }
        log.warn("Unauthorized access to Account API (401) after {} attempts. Token may be expired or invalid. Error: {}", 
                maxRetries, e.getMessage());
        return null;
    }
    
    /**
     * Handle HttpClientException (4xx/5xx errors)
     */
    private String handleHttpClientException(org.springframework.web.client.HttpClientErrorException e, 
                                             int attempt, int maxRetries) {
        if (attempt < maxRetries && e.getStatusCode().value() >= 500) {
            log.warn("Server error accessing Account API ({}). Retrying (attempt {}/{}). Error: {}", 
                    e.getStatusCode(), attempt, maxRetries, e.getMessage());
            return null; // Signal to retry
        }
        log.warn("Client error accessing Account API ({}). Error: {}", e.getStatusCode(), e.getMessage());
        return null;
    }
    
    /**
     * Handle generic exception
     */
    private String handleGenericException(Exception e, int attempt, int maxRetries) {
        if (attempt < maxRetries) {
            log.warn("Failed to get user key via Account API (attempt {}/{}). Retrying. Error: {}", 
                    attempt, maxRetries, e.getMessage());
            return null; // Signal to retry
        }
        log.error("Failed to get user key via Account API after {} attempts. Error: {}", maxRetries, e.getMessage());
        return null;
    }
    
    /**
     * Handle retry delay with exponential backoff
     */
    private boolean handleRetryDelay(int attempt, int retryDelayMs) {
        try {
            Thread.sleep((long) retryDelayMs * attempt);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.error(Constants.ERROR_THREAD_INTERRUPTED_DURING_RETRY_DELAY);
            return false;
        }
    }
    
    /**
     * Log non-2xx response
     */
    private void logNon2xxResponse(org.springframework.http.HttpStatusCode status, int attempt, int maxRetries) {
        if (attempt < maxRetries) {
            log.warn("Account API returned non-2xx status: {}. Retrying (attempt {}/{})", 
                    status, attempt, maxRetries);
        }
    }
    

    public void updateUserKeyViaAccountApi(String userId, String userKey, String jwtToken) {
        try {
            ResponseEntity<Map<String, Object>> response = getAccountData(jwtToken);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User data not found");
            }

            Map<String, Object> userData = response.getBody();

            // Update the user-key attribute while preserving other attributes
            @SuppressWarnings("unchecked")
            Map<String, Object> attributes = (Map<String, Object>) userData.getOrDefault(Constants.KEYCLOAK_CLIENT_ATTRIBUTES, new HashMap<>());
            attributes.put(Constants.KEYCLOAK_USER_KEY, Collections.singletonList(userKey));
            userData.put(Constants.KEYCLOAK_CLIENT_ATTRIBUTES, attributes);

            // Remove userProfileMetadata from the payload as it's read-only
            userData.remove("userProfileMetadata");

            // Send update request
            RestTemplate restTemplate = new RestTemplate();
            String accountApiUrl = authServerUrl + "/realms/" + realmName + "/account/";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(jwtToken);

            restTemplate.exchange(
                accountApiUrl,
                HttpMethod.POST,
                new HttpEntity<>(userData, headers),
                Void.class
            );

            log.info("User key updated via Account API for userId: {}", userId);
        } catch (Exception e) {
            log.error("Failed to update user key via Account API for userId: {}. Error: {}", userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Failed to update user key via Account API: " + e.getMessage());
        }
    }

    private String generateRandomUserKey() {
        byte[] bytes = new byte[20];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); // Random 20-character key
    }

    public String getUserKey() {
        // Check thread-local cache first to avoid multiple calls in the same request
        String cachedKey = userKeyCache.get();
        Long cacheTime = userKeyCacheTime.get();
        
        if (cachedKey != null && cacheTime != null) {
            long age = System.currentTimeMillis() - cacheTime;
            if (age < CACHE_TTL_MS) {
                log.debug("Returning cached user-key (age: {}ms)", age);
                return cachedKey;
            } else {
                // Cache expired, clear it
                userKeyCache.remove();
                userKeyCacheTime.remove();
            }
        }
        
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String jwtToken = null;

            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                jwtToken = jwt.getTokenValue();
            }
            if (jwtToken == null || jwtToken.isEmpty()) {
                log.debug("JWT token not available in security context");
                return null;
            }
            
            String userKey = getUserKeyViaAccountApi(jwtToken);
            
            // Cache the result if successful
            if (userKey != null) {
                userKeyCache.set(userKey);
                userKeyCacheTime.set(System.currentTimeMillis());
            }
            
            return userKey;
        } catch (Exception e) {
            // Handle any unexpected errors gracefully
            log.warn("Failed to get user key. Error: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Clear the thread-local cache (useful for testing or when token changes)
     */
    public void clearUserKeyCache() {
        userKeyCache.remove();
        userKeyCacheTime.remove();
    }

    private ResponseEntity<Map<String, Object>> getAccountData(String jwtToken) {
        RestTemplate restTemplate = new RestTemplate();
        String accountApiUrl = authServerUrl + "/realms/" + realmName + "/account/";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwtToken);
        
        return restTemplate.exchange(
            accountApiUrl,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            new ParameterizedTypeReference<Map<String, Object>>() {}
        );
    }
}