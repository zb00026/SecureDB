package com.verlake.dam.controller.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes non-sensitive runtime configuration for the frontend.
 * Keep this endpoint limited to public values only (no secrets).
 */
@RestController
@RequestMapping("/public")
public class PublicConfigController {

    @Value("${request.base-url:http://localhost:8080}")
    private String requestBaseUrl;

    @Value("${websocket.url:ws://localhost:8080}")
    private String websocketUrl;

    @Value("${keycloak.auth-server-url:http://localhost:8081}")
    private String keycloakUrl;

    @Value("${keycloak.realm:DAM}")
    private String keycloakRealm;

    @Value("${keycloak.frontend.client-id:frontend-client}")
    private String keycloakClientId;

    @Value("${auth.provider:google}")
    private String authProvider;

    @Value("${google.client-id:}")
    private String googleClientId;

    @Value("${firebase.api.key:}")
    private String firebaseApiKey;

    @Value("${firebase.auth.domain:}")
    private String firebaseAuthDomain;

    @Value("${firebase.project.id:}")
    private String firebaseProjectId;

    @Value("${firebase.storage.bucket:}")
    private String firebaseStorageBucket;

    @Value("${firebase.messaging.sender.id:}")
    private String firebaseMessagingSenderId;

    @Value("${firebase.app.id:}")
    private String firebaseAppId;

    @Value("${firebase.measurement.id:}")
    private String firebaseMeasurementId;

    @Value("${firebase.vapid.key:}")
    private String firebaseVapidKey;

    public record PublicConfig(
            String requestBaseUrl,
            String websocketUrl,
            String keycloakUrl,
            String keycloakRealm,
            String keycloakClientId,
            String authProvider,
            String googleClientId,
            String firebaseApiKey,
            String firebaseAuthDomain,
            String firebaseProjectId,
            String firebaseStorageBucket,
            String firebaseMessagingSenderId,
            String firebaseAppId,
            String firebaseMeasurementId,
            String firebaseVapidKey
    ) {}

    @GetMapping("/config")
    public ResponseEntity<PublicConfig> getConfig() {
        var cfg = new PublicConfig(
                requestBaseUrl,
                websocketUrl,
                keycloakUrl,
                keycloakRealm,
                keycloakClientId,
                authProvider,
                googleClientId,
                firebaseApiKey,
                firebaseAuthDomain,
                firebaseProjectId,
                firebaseStorageBucket,
                firebaseMessagingSenderId,
                firebaseAppId,
                firebaseMeasurementId,
                firebaseVapidKey
        );
        return ResponseEntity.ok(cfg);
    }
}


