package com.verlake.dam.service.auth;

import com.verlake.dam.enums.AuthProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service to get the global authentication provider for the application
 */
@Slf4j
@Service
public class GlobalAuthProviderService {

    private final String authProvider;
    
    @Autowired(required = false)
    private KeycloakService keycloakService;

    public GlobalAuthProviderService(@Value("${auth.provider}") String authProvider) {
        this.authProvider = authProvider;
        log.info("Global auth provider configured as: {}", authProvider);
    }

    /**
     * Get the current authentication provider for the application
     * Handles variants like keycloak_sso, keycloak-sso, and maps them to KEYCLOAK
     */
    public AuthProvider getCurrentAuthProvider() {
        try {
            String normalizedProvider = normalizeAuthProvider(authProvider);
            return AuthProvider.valueOf(normalizedProvider.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("Invalid auth provider configured: {}", authProvider);
            return AuthProvider.GOOGLE; // Default fallback
        }
    }
    
    /**
     * Normalize auth provider name to handle variants
     * Maps keycloak_sso, keycloak-sso to keycloak
     */
    private String normalizeAuthProvider(String provider) {
        if (provider == null) {
            return "google";
        }
        
        String normalized = provider.toLowerCase().trim();
        
        // Handle keycloak variants - all map to KEYCLOAK
        if (normalized.equals("keycloak_sso") || 
            normalized.equals("keycloak-sso") ||
            normalized.equals("keycloak")) {
            return "keycloak";
        }
        
        return normalized;
    }

    /**
     * Check if the current auth provider is SSO
     */
    public boolean isSSOProvider() {
        return getCurrentAuthProvider() == AuthProvider.KEYCLOAK_SSO;
    }

    /**
     * Check if the current auth provider is Keycloak
     */
    public boolean isKeycloakProvider() {
        return getCurrentAuthProvider() == AuthProvider.KEYCLOAK;
    }

    /**
     * Get the appropriate email template for the current auth provider
     * If Keycloak has SSO configured (identity providers), use sso-invite
     */
    public String getEmailTemplate() {
        AuthProvider currentProvider = getCurrentAuthProvider();
        
        return switch (currentProvider) {
            case KEYCLOAK_SSO -> "sso-invite";
            case KEYCLOAK -> {
                // Check if Keycloak has SSO configured
                if (keycloakService != null && keycloakService.isSSOEnabled()) {
                    yield "sso-invite"; // Use SSO template for Keycloak SSO
                } else {
                    yield "keycloak-invite"; // Use regular Keycloak template
                }
            }
            case GOOGLE -> "google-invite";
            default -> "google-invite"; // Default fallback
        };
    }

    /**
     * Check if users should be created without passwords (SSO or Keycloak SSO)
     */
    public boolean shouldCreateUsersWithoutPasswords() {
        if (isSSOProvider()) {
            return true;
        }
        // Check if Keycloak has SSO configured
        if (isKeycloakProvider() && keycloakService != null) {
            try {
                return keycloakService.isSSOEnabled();
            } catch (Exception e) {
                log.warn("Failed to check SSO status in Keycloak, assuming no SSO: {}", e.getMessage());
                return false;
            }
        }
        return false;
    }

    /**
     * Check if users should be created as active by default
     * SSO users are active immediately (no email verification needed)
     * Regular users are inactive until they verify their email
     */
    public boolean shouldCreateUsersAsActive() {
        AuthProvider currentProvider = getCurrentAuthProvider();
        
        // SSO providers: users are active immediately
        if (currentProvider == AuthProvider.KEYCLOAK_SSO) {
            return true;
        }
        
        // Keycloak with SSO: users are active immediately
        if (currentProvider == AuthProvider.KEYCLOAK && keycloakService != null) {
            try {
                return keycloakService.isSSOEnabled();
            } catch (Exception e) {
                log.warn("Failed to check SSO status in Keycloak, assuming no SSO: {}", e.getMessage());
                return false; // Regular Keycloak users need email verification
            }
        }
        
        // Google and other providers: users need email verification
        return false;
    }
}
