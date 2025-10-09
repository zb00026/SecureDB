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
import org.keycloak.representations.idm.UserProfileAttributeMetadata;
import org.keycloak.representations.idm.UserProfileMetadata;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.userprofile.config.UPConfig;
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
    
    // Cache SSO status to avoid repeated API calls
    private Boolean ssoEnabled = null;

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
            user.setCredentials(Arrays.asList(credential));
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
            realmResource.users().get(userId).roles().clientLevel(accountClientId).add(Arrays.asList(manageAccountRole));
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
        try {
            ResponseEntity<Map<String, Object>> response = getAccountData(jwtToken);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }

            Map<String, Object> userData = response.getBody();
            
            // Get attributes
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
        } catch (Exception e) {
            log.error("Failed to get user key via Account API. Error: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Failed to get user key via Account API: " + e.getMessage());
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
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); // Random 20-character key
    }

    public String getUserKey() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String jwtToken = null;

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            jwtToken = jwt.getTokenValue();
        }
        if (jwtToken == null || jwtToken.isEmpty()) {
            return null;
        }
        return getUserKeyViaAccountApi(jwtToken);
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