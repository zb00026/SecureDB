package com.verlake.dam.service.impl;

import com.verlake.dam.configuration.ConditionalOnAuthProviderParam;
import com.verlake.dam.enums.AuthProvider;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.auth.TokenService;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@ConditionalOnAuthProviderParam(field = "auth.provider", containProvider = "keycloak")
@Slf4j
public class KeycloakTokenService implements TokenService {
    
    private final JwtDecoder jwtDecoder;

    private final KeycloakService keycloakService;

    public KeycloakTokenService(@Lazy JwtDecoder jwtDecoder, KeycloakService keycloakService) {
        this.jwtDecoder = jwtDecoder;
        this.keycloakService = keycloakService;
    }

    public boolean verifyToken(String token) {
        log.info("=== KeycloakTokenService.verifyToken START ===");
        log.info("Verifying Keycloak JWT token");
        log.info("Token preview: {}...", token != null ? token.substring(0, Math.min(50, token.length())) : "null");
        
        try {
            log.info("Decoding JWT token with JwtDecoder");
            Jwt decodedJwt = jwtDecoder.decode(token);
            log.info("JWT decoded successfully");
            log.info("JWT subject: {}", decodedJwt.getSubject());
            log.info("JWT issuer: {}", decodedJwt.getIssuer());
            log.info("JWT expires at: {}", decodedJwt.getExpiresAt());
            log.info("JWT email claim: {}", decodedJwt.getClaimAsString("email"));
            
            log.info("Checking user key definition");
            checkUserKeyDefined(token);
            log.info("User key check completed");
            
            log.info("Keycloak token verification successful");
            log.info("=== KeycloakTokenService.verifyToken SUCCESS ===");
            return true;
        } catch (Exception e) {
            log.error("=== KeycloakTokenService.verifyToken FAILED ===");
            log.error("JWT verification failed: {}", e.getMessage());
            log.error("Exception type: {}", e.getClass().getSimpleName());
            log.error("Full stack trace: ", e);
            throw e; // Re-throw to maintain existing behavior
        }
    }

    public String getEmailFromToken(String token) {
        log.info("=== KeycloakTokenService.getEmailFromToken START ===");
        
        try {
            log.info("Decoding JWT to extract email");
            Jwt jwt = jwtDecoder.decode(token);
            String email = jwt.getClaimAsString("email");
            log.info("Email extracted from JWT: {}", email);
            log.info("=== KeycloakTokenService.getEmailFromToken SUCCESS ===");
            return email;
        } catch (Exception e) {
            log.error("=== KeycloakTokenService.getEmailFromToken FAILED ===");
            log.error("Failed to extract email from JWT: {}", e.getMessage());
            log.error("Exception type: {}", e.getClass().getSimpleName());
            log.error("Full stack trace: ", e);
            throw e; // Re-throw to maintain existing behavior
        }
    }

    private void checkUserKeyDefined(String token) {
        Jwt jwt = jwtDecoder.decode(token);
        String userId = jwt.getClaimAsString("sub");
        keycloakService.updateUserKey(userId, token);

    }

    @Override
    public AuthProvider getAuthProvider() {
        return AuthProvider.KEYCLOAK;
    }
} 