package com.verlake.dam.service.impl;

import com.verlake.dam.configuration.ConditionalOnAuthProviderParam;
import com.verlake.dam.enums.AuthProvider;
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

    public KeycloakTokenService(@Lazy JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    public boolean verifyToken(String token) {
        try {
            jwtDecoder.decode(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getEmailFromToken(String token) {
        Jwt jwt = jwtDecoder.decode(token);
        return jwt.getClaimAsString("email");
    }

    @Override
    public AuthProvider getAuthProvider() {
        return AuthProvider.KEYCLOAK;
    }
} 