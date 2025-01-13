package com.verlake.dam.security;

import com.verlake.dam.service.KeycloakUserMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

@Component
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final KeycloakUserMapper keycloakUserMapper;
    private final JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter;

    public KeycloakJwtAuthenticationConverter(KeycloakUserMapper keycloakUserMapper) {
        this.keycloakUserMapper = keycloakUserMapper;
        this.jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        // Map the Keycloak user to our internal user
        keycloakUserMapper.createOrUpdateUser(jwt);
        
        return new JwtAuthenticationToken(
            jwt,
            jwtGrantedAuthoritiesConverter.convert(jwt)
        );
    }
} 