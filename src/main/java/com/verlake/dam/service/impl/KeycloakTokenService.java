package com.verlake.dam.service.impl;

import com.verlake.dam.configuration.ConditionalOnAuthProviderParam;
import com.verlake.dam.enums.AuthProvider;
import com.verlake.dam.service.KeycloakService;
import com.verlake.dam.service.TokenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnAuthProviderParam(field = "auth.provider", containProvider = "keycloak")
public class KeycloakTokenService implements TokenService {
    
    private final JwtDecoder jwtDecoder;

    private final KeycloakService keycloakService;

    public KeycloakTokenService(@Lazy JwtDecoder jwtDecoder, KeycloakService keycloakService) {
        this.jwtDecoder = jwtDecoder;
        this.keycloakService = keycloakService;
    }

    public boolean verifyToken(String token) {
        jwtDecoder.decode(token);
        checkUserKeyDefined(token);
        return true;
    }

    public String getEmailFromToken(String token) {
        Jwt jwt = jwtDecoder.decode(token);
        return jwt.getClaimAsString("email");
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