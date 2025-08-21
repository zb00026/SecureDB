package com.verlake.dam.security;


import com.verlake.dam.entity.Role;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.service.users.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Autowired
    private UserService userService;

    private final JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter;

    public CustomJwtAuthenticationConverter() {
        this.jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
    }

    public AbstractAuthenticationToken convert(Jwt jwt) {
        log.info("=== CustomJwtAuthenticationConverter.convert START ===");
        log.info("JWT Subject: {}", jwt.getSubject());
        log.info("JWT Issuer: {}", jwt.getIssuer());
        log.info("JWT Audience: {}", jwt.getAudience());
        log.info("JWT Expires At: {}", jwt.getExpiresAt());
        log.info("JWT Issued At: {}", jwt.getIssuedAt());
        log.info("JWT Not Before: {}", jwt.getNotBefore());
        log.info("JWT ID: {}", jwt.getId());
        log.info("All JWT Claims: {}", jwt.getClaims());
        
        Collection<? extends GrantedAuthority> authorities = extractAuthorities(jwt);
        
        JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, authorities);
        log.info("Created JwtAuthenticationToken with authorities: {}", authorities);
        log.info("=== CustomJwtAuthenticationConverter.convert SUCCESS ===");
        
        return token;
    }

    protected Collection<? extends GrantedAuthority> extractAuthorities(Jwt jwt) {
        log.info("=== CustomJwtAuthenticationConverter.extractAuthorities START ===");
        
        // Extract the user's email from the JWT token
        String email = jwt.getClaimAsString("email");
        log.info("Email claim from JWT: {}", email);
        
        // Check for other common email claims
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        String username = jwt.getClaimAsString("username");
        String sub = jwt.getClaimAsString("sub");
        log.info("Other identity claims - preferred_username: {}, username: {}, sub: {}", 
            preferredUsername, username, sub);
        
        // Log JWT scopes and roles if present
        Object scopesClaim = jwt.getClaim("scope");
        Object rolesClaim = jwt.getClaim("roles");
        Object realmAccessClaim = jwt.getClaim("realm_access");
        Object resourceAccessClaim = jwt.getClaim("resource_access");
        
        log.info("JWT scope claim: {}", scopesClaim);
        log.info("JWT roles claim: {}", rolesClaim);
        log.info("JWT realm_access claim: {}", realmAccessClaim);
        log.info("JWT resource_access claim: {}", resourceAccessClaim);

        if (email == null || email.trim().isEmpty()) {
            log.warn("No email claim found in JWT, falling back to JWT authorities");
            Collection<? extends GrantedAuthority> jwtAuthorities = jwtGrantedAuthoritiesConverter.convert(jwt);
            log.info("Fallback JWT authorities: {}", jwtAuthorities);
            log.info("=== CustomJwtAuthenticationConverter.extractAuthorities FALLBACK (NO EMAIL) ===");
            return jwtAuthorities;
        }
        
        // Fetch user details from the database
        log.info("Fetching user from database by email: {}", email);
        User user = userService.findByEmail(email);
        
        if (user != null) {
            log.info("User found in database: ID={}, Email={}, Active={}", 
                user.getId(), user.getEmail(), user.getIsActive());
            
            if (user.getRoles() != null && !user.getRoles().isEmpty()) {
                Set<Role> roles = user.getRoles();
                log.info("User has {} roles: {}", roles.size(), 
                    roles.stream().map(Role::getName).collect(Collectors.toList()));
                
                Collection<GrantedAuthority> authorities = roles.stream()
                    .map(role -> {
                        String authorityName = "ROLE_" + role.getName().toUpperCase().replace(" ", "_");
                        log.info("Mapping role '{}' to authority '{}'", role.getName(), authorityName);
                        return new SimpleGrantedAuthority(authorityName);
                    })
                    .collect(Collectors.toList());
                
                log.info("Generated {} authorities from database: {}", authorities.size(), authorities);
                log.info("=== CustomJwtAuthenticationConverter.extractAuthorities SUCCESS (DB ROLES) ===");
                return authorities;
            } else {
                log.warn("User found but has no roles assigned");
            }
        } else {
            log.warn("User not found in database for email: {}", email);
        }

        // Fallback to JWT roles if no roles found in the database
        log.info("Falling back to JWT-based authorities");
        Collection<? extends GrantedAuthority> jwtAuthorities = jwtGrantedAuthoritiesConverter.convert(jwt);
        log.info("Fallback JWT authorities: {}", jwtAuthorities);
        log.info("=== CustomJwtAuthenticationConverter.extractAuthorities FALLBACK (JWT ROLES) ===");
        return jwtAuthorities;
    }
}