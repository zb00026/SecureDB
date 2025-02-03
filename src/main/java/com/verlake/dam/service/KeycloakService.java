package com.verlake.dam.service;


import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

@Slf4j
@Service
@ConditionalOnProperty(name = "auth.provider", havingValue = "keycloak")
public class KeycloakService {

    private final String authServerUrl;
    private final String clientId;
    private final String clientSecret;
    private final String realmName;

    @Autowired
    public KeycloakService(@Value("${keycloak.auth-server-url}") String keycloakAuthServerUrl,
                           @Value("${spring.security.oauth2.client.registration.keycloak.client-id}") String clientId,
                           @Value("${spring.security.oauth2.client.registration.keycloak.client-secret}") String clientSecret,
                           @Value("${keycloak.realm}") String realmName) {
        this.realmName = realmName;
        this.authServerUrl = keycloakAuthServerUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    // Method to create a new user in Keycloak
    public void createUser(String username, String email, String firstName, String lastName, String password) {
        Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl(authServerUrl)
                .realm(realmName)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build();
        RealmResource realmResource = keycloak.realm(realmName);

        // Create user representation
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);

        // Create CredentialRepresentation for password
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);  // Temporary is false since this is the user's permanent password

        user.setCredentials(Arrays.asList(credential));  // Set the credentials (password) for the user

        UsersResource usersResource = realmResource.users();
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
}