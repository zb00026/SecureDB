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
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
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

    public KeycloakService(@Value("${keycloak.auth-server-url}") String keycloakAuthServerUrl,
                           @Value("${spring.security.oauth2.client.registration.keycloak.client-id}") String clientId,
                           @Value("${spring.security.oauth2.client.registration.keycloak.client-secret}") String clientSecret,
                           @Value("${keycloak.realm}") String realmName) {
        this.realmName = realmName;
        this.authServerUrl = keycloakAuthServerUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
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

        // Set password credentials
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(isTemporaryPsd);

        user.setCredentials(Arrays.asList(credential));

        // Create the user in Keycloak
        Response response = usersResource.create(user);
        if (response.getStatus() == Response.Status.CREATED.getStatusCode()) {
            log.info("User created successfully in Keycloak: {}", username);
        } else {
            String errorMessage = response.readEntity(String.class);
            log.error("Failed to create user in Keycloak: {}", errorMessage);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create user in Keycloak : " + errorMessage);
        }
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
        // Fetch user by userId
        RealmResource realmResource = getRealmInstance(jwtToken);
        UsersResource usersResource = realmResource.users();
        UserResource userResource = usersResource.get(userId);

        // Get user representation
        UserRepresentation userRepresentation = userResource.toRepresentation();
        Map<String, List<String>> attributes = userRepresentation.getAttributes();
        if(attributes == null) {
            attributes = new HashMap<>();
        }
        // Check if user has 'user-key' attribute and if it is null or empty
        String userKey = attributes.get(Constants.KEYCLOAK_USER_KEY) != null ? attributes.get(Constants.KEYCLOAK_USER_KEY).get(0) : null;

        if (userKey == null || userKey.isEmpty()) {
            // Generate a random 20-character alphanumeric key2
            String newUserKey = generateRandomUserKey();

            // Update the user's 'user-key' attribute
            attributes.put(Constants.KEYCLOAK_USER_KEY, Collections.singletonList(newUserKey));
            userRepresentation.setAttributes(attributes);
            userResource.update(userRepresentation);

            log.info("User key updated for userId: {}", userId);
        }
    }

    private String generateRandomUserKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); // Random 20-character key
    }

    private String getUserKeyFromUserResource(UserResource userResource) {
        if (userResource == null) {
            return null;
        }
        
        UserRepresentation userRepresentation = userResource.toRepresentation();
        Map<String, List<String>> attributes = userRepresentation.getAttributes();
        if (attributes != null && attributes.containsKey(Constants.KEYCLOAK_USER_KEY)) {
            return attributes.get(Constants.KEYCLOAK_USER_KEY).get(0);
        }
        return null;
    }

    public String getUserKey(String userId) {
        RealmResource realmResource = getRealmInstance();
        UsersResource usersResource = realmResource.users();
        UserResource userResource = usersResource.get(userId);
        return getUserKeyFromUserResource(userResource);
    }

    public String getUserKeyByEmail(String email) {
        RealmResource realmResource = getRealmInstance();
        UsersResource usersResource = realmResource.users();
        
        // Search for user by email
        List<UserRepresentation> users = usersResource.searchByEmail(email, true);
        if (users.isEmpty()) {
            return null;
        }
        
        // Get the first matching user (email should be unique)
        UserResource userResource = usersResource.get(users.get(0).getId());
        return getUserKeyFromUserResource(userResource);
    }
}