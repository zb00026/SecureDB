package com.verlake.dam.security;


import com.verlake.dam.entity.Role;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
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
        boolean detailedLogging = Constants.getTechnicalPropertyAsBoolean(Constants.LOGGING_DETAILED_REQUEST_RESPONSE_ENABLED, false);
        
        if (detailedLogging) {
            log.info("=== CustomJwtAuthenticationConverter.convert START ===");
            log.info("JWT Subject: {}", jwt.getSubject());
            log.info("JWT Issuer: {}", jwt.getIssuer());
            log.info("JWT Audience: {}", jwt.getAudience());
            log.info("JWT Expires At: {}", jwt.getExpiresAt());
            log.info("JWT Issued At: {}", jwt.getIssuedAt());
            log.info("JWT Not Before: {}", jwt.getNotBefore());
            log.info("JWT ID: {}", jwt.getId());
            log.info("All JWT Claims: {}", jwt.getClaims());
        } else {
            log.info("Converting JWT for subject: {}", jwt.getSubject());
        }
        
        try {
            Collection<? extends GrantedAuthority> authorities = extractAuthorities(jwt);
            
            if (authorities == null) {
                log.warn("extractAuthorities returned null, using empty authorities");
                authorities = Collections.emptyList();
            }
            
            JwtAuthenticationToken token = new JwtAuthenticationToken(jwt, authorities);
            
            if (detailedLogging) {
                log.info("Created JwtAuthenticationToken with {} authorities: {}", authorities.size(), authorities);
                log.info("=== CustomJwtAuthenticationConverter.convert SUCCESS ===");
            } else {
                log.info("JWT conversion completed with {} authorities", authorities.size());
            }
            
            return token;
        } catch (Exception e) {
            log.error("Error converting JWT token: {}", e.getMessage(), e);
            // Return token with empty authorities to allow authorization check to happen
            // This ensures we get 403 (Forbidden) instead of 401 (Unauthorized) if user lacks permissions
            return new JwtAuthenticationToken(jwt, Collections.emptyList());
        }
    }

    protected Collection<? extends GrantedAuthority> extractAuthorities(Jwt jwt) {
        boolean detailedLogging = Constants.getTechnicalPropertyAsBoolean(Constants.LOGGING_DETAILED_REQUEST_RESPONSE_ENABLED, false);
        
        if (detailedLogging) {
            log.info("=== CustomJwtAuthenticationConverter.extractAuthorities START ===");
        }
        
        // Extract the user's email from the JWT token
        String email = extractEmailFromJwt(jwt, detailedLogging);
        
        if (email == null || email.trim().isEmpty()) {
            return getFallbackJwtAuthorities(jwt, detailedLogging, "NO EMAIL");
        }
        
        // Fetch user details from the database
        if (detailedLogging) {
            log.info("Fetching user from database by email: {}", email);
        }
        
        try {
            User user = userService.findByEmail(email);
            
            if (user != null) {
                if (detailedLogging) {
                    log.info("User found in database: ID={}, Email={}, Active={}", 
                        user.getId(), user.getEmail(), user.getIsActive());
                }
                
                Collection<? extends GrantedAuthority> dbAuthorities = extractAuthoritiesFromUser(user, detailedLogging);
                if (dbAuthorities != null && !dbAuthorities.isEmpty()) {
                    if (detailedLogging) {
                        log.info("Returning {} authorities from database", dbAuthorities.size());
                    }
                    return dbAuthorities;
                } else {
                    log.warn("User found but has no roles assigned. Email: {}", email);
                }
            } else {
                log.warn("User not found in database for email: {}", email);
            }
        } catch (Exception e) {
            log.error("Error fetching user from database for email: {}", email, e);
        }

        // Fallback to JWT roles if no roles found in the database
        // Always return at least an empty collection to ensure authentication succeeds
        // Authorization will then check roles and return 403 if needed
        Collection<? extends GrantedAuthority> fallbackAuthorities = getFallbackJwtAuthorities(jwt, detailedLogging, "JWT ROLES");
        if (detailedLogging) {
            log.info("Using fallback JWT authorities: {}", fallbackAuthorities);
        }
        return fallbackAuthorities;
    }
    
    private String extractEmailFromJwt(Jwt jwt, boolean detailedLogging) {
        String email = jwt.getClaimAsString("email");
        if (detailedLogging) {
            log.info("Email claim from JWT: {}", email);
            logOtherJwtClaims(jwt);
        }
        return email;
    }
    
    private void logOtherJwtClaims(Jwt jwt) {
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
    }
    
    private Collection<? extends GrantedAuthority> getFallbackJwtAuthorities(Jwt jwt, boolean detailedLogging, String reason) {
        if (detailedLogging) {
            log.warn("No email claim found in JWT, falling back to JWT authorities");
        }
        Collection<? extends GrantedAuthority> jwtAuthorities = jwtGrantedAuthoritiesConverter.convert(jwt);
        if (detailedLogging) {
            log.info("Fallback JWT authorities: {}", jwtAuthorities);
            log.info("=== CustomJwtAuthenticationConverter.extractAuthorities FALLBACK ({}) ===", reason);
        }
        return jwtAuthorities;
    }
    
    private Collection<? extends GrantedAuthority> extractAuthoritiesFromUser(User user, boolean detailedLogging) {
        if (detailedLogging) {
            log.info("User found in database: ID={}, Email={}, Active={}", 
                user.getId(), user.getEmail(), user.getIsActive());
        }
        
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            if (detailedLogging) {
                log.warn("User found but has no roles assigned");
            }
            return null;
        }
        
        Set<Role> roles = user.getRoles();
        if (detailedLogging) {
            log.info("User has {} roles: {}", roles.size(), 
                roles.stream().map(Role::getName).collect(Collectors.toList()));
        }
        
        Collection<GrantedAuthority> authorities = roles.stream()
            .map(role -> {
                String authorityName = "ROLE_" + role.getName().toUpperCase().replace(" ", "_");
                if (detailedLogging) {
                    log.info("Mapping role '{}' to authority '{}'", role.getName(), authorityName);
                }
                return new SimpleGrantedAuthority(authorityName);
            })
            .collect(Collectors.toList());
        
        if (detailedLogging) {
            log.info("Generated {} authorities from database: {}", authorities.size(), authorities);
            log.info("=== CustomJwtAuthenticationConverter.extractAuthorities SUCCESS (DB ROLES) ===");
        }
        return authorities;
    }
    
}