package com.verlake.dam.security;

import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AuthenticationEventListener {

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        boolean detailedLogging = Constants.getTechnicalPropertyAsBoolean(Constants.LOGGING_DETAILED_REQUEST_RESPONSE_ENABLED, false);
        
        if (detailedLogging) {
            log.info("=== AUTHENTICATION SUCCESS EVENT ===");
            log.info("Authentication Type: {}", authentication.getClass().getSimpleName());
            log.info("Principal: {}", authentication.getPrincipal());
            log.info("Authorities: {}", authentication.getAuthorities());
            log.info("Is Authenticated: {}", authentication.isAuthenticated());
            
            if (authentication instanceof JwtAuthenticationToken jwtToken) {
                log.info("JWT Token Details:");
                log.info("  - Subject: {}", jwtToken.getToken().getSubject());
                log.info("  - Issuer: {}", jwtToken.getToken().getIssuer());
                log.info("  - Email: {}", jwtToken.getToken().getClaimAsString("email"));
                log.info("  - Expires At: {}", jwtToken.getToken().getExpiresAt());
            }
            log.info("=== END AUTHENTICATION SUCCESS ===");
        } else {
            log.info("Authentication successful for: {}", authentication.getPrincipal());
        }
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        AuthenticationException exception = event.getException();
        boolean detailedLogging = Constants.getTechnicalPropertyAsBoolean(Constants.LOGGING_DETAILED_REQUEST_RESPONSE_ENABLED, false);
        
        if (detailedLogging) {
            log.error("=== AUTHENTICATION FAILURE EVENT ===");
            log.error("Exception Type: {}", exception.getClass().getSimpleName());
            log.error("Exception Message: {}", exception.getMessage());
            log.error("Authentication Object: {}", event.getAuthentication());
            if (event.getAuthentication() != null) {
                log.error("Authentication Principal: {}", event.getAuthentication().getPrincipal());
                log.error("Authentication Credentials: {}", event.getAuthentication().getCredentials() != null ? "[PRESENT]" : "[NULL]");
            }
            log.error("Full Exception: ", exception);
            log.error("=== END AUTHENTICATION FAILURE ===");
        } else {
            log.error("Authentication failed: {} - {}", exception.getClass().getSimpleName(), exception.getMessage());
        }
    }

    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        boolean detailedLogging = Constants.getTechnicalPropertyAsBoolean(Constants.LOGGING_DETAILED_REQUEST_RESPONSE_ENABLED, false);
        if (detailedLogging) {
            log.error("=== BAD CREDENTIALS EVENT ===");
            log.error("Bad credentials for: {}", event.getAuthentication().getPrincipal());
            log.error("Exception: {}", event.getException().getMessage());
            log.error("=== END BAD CREDENTIALS ===");
        } else {
            log.error("Bad credentials for: {}", event.getAuthentication().getPrincipal());
        }
    }

    @EventListener
    public void onCredentialsExpired(AuthenticationFailureCredentialsExpiredEvent event) {
        boolean detailedLogging = Constants.getTechnicalPropertyAsBoolean(Constants.LOGGING_DETAILED_REQUEST_RESPONSE_ENABLED, false);
        if (detailedLogging) {
            log.error("=== CREDENTIALS EXPIRED EVENT ===");
            log.error("Credentials expired for: {}", event.getAuthentication().getPrincipal());
            log.error("Exception: {}", event.getException().getMessage());
            log.error("=== END CREDENTIALS EXPIRED ===");
        } else {
            log.error("Credentials expired for: {}", event.getAuthentication().getPrincipal());
        }
    }

    @EventListener
    public void onDisabled(AuthenticationFailureDisabledEvent event) {
        boolean detailedLogging = Constants.getTechnicalPropertyAsBoolean(Constants.LOGGING_DETAILED_REQUEST_RESPONSE_ENABLED, false);
        if (detailedLogging) {
            log.error("=== ACCOUNT DISABLED EVENT ===");
            log.error("Account disabled for: {}", event.getAuthentication().getPrincipal());
            log.error("Exception: {}", event.getException().getMessage());
            log.error("=== END ACCOUNT DISABLED ===");
        } else {
            log.error("Account disabled for: {}", event.getAuthentication().getPrincipal());
        }
    }

    @EventListener
    public void onExpired(AuthenticationFailureExpiredEvent event) {
        boolean detailedLogging = Constants.getTechnicalPropertyAsBoolean(Constants.LOGGING_DETAILED_REQUEST_RESPONSE_ENABLED, false);
        if (detailedLogging) {
            log.error("=== ACCOUNT EXPIRED EVENT ===");
            log.error("Account expired for: {}", event.getAuthentication().getPrincipal());
            log.error("Exception: {}", event.getException().getMessage());
            log.error("=== END ACCOUNT EXPIRED ===");
        } else {
            log.error("Account expired for: {}", event.getAuthentication().getPrincipal());
        }
    }

    @EventListener
    public void onLocked(AuthenticationFailureLockedEvent event) {
        boolean detailedLogging = Constants.getTechnicalPropertyAsBoolean(Constants.LOGGING_DETAILED_REQUEST_RESPONSE_ENABLED, false);
        if (detailedLogging) {
            log.error("=== ACCOUNT LOCKED EVENT ===");
            log.error("Account locked for: {}", event.getAuthentication().getPrincipal());
            log.error("Exception: {}", event.getException().getMessage());
            log.error("=== END ACCOUNT LOCKED ===");
        } else {
            log.error("Account locked for: {}", event.getAuthentication().getPrincipal());
        }
    }

    @EventListener
    public void onProviderNotFound(AuthenticationFailureProviderNotFoundEvent event) {
        boolean detailedLogging = Constants.getTechnicalPropertyAsBoolean(Constants.LOGGING_DETAILED_REQUEST_RESPONSE_ENABLED, false);
        if (detailedLogging) {
            log.error("=== PROVIDER NOT FOUND EVENT ===");
            log.error("Provider not found for: {}", event.getAuthentication().getPrincipal());
            log.error("Exception: {}", event.getException().getMessage());
            log.error("=== END PROVIDER NOT FOUND ===");
        } else {
            log.error("Provider not found for: {}", event.getAuthentication().getPrincipal());
        }
    }

    @EventListener
    public void onProxyUntrusted(AuthenticationFailureProxyUntrustedEvent event) {
        boolean detailedLogging = Constants.getTechnicalPropertyAsBoolean(Constants.LOGGING_DETAILED_REQUEST_RESPONSE_ENABLED, false);
        if (detailedLogging) {
            log.error("=== PROXY UNTRUSTED EVENT ===");
            log.error("Proxy untrusted for: {}", event.getAuthentication().getPrincipal());
            log.error("Exception: {}", event.getException().getMessage());
            log.error("=== END PROXY UNTRUSTED ===");
        } else {
            log.error("Proxy untrusted for: {}", event.getAuthentication().getPrincipal());
        }
    }

    @EventListener
    public void onServiceException(AuthenticationFailureServiceExceptionEvent event) {
        boolean detailedLogging = Constants.getTechnicalPropertyAsBoolean(Constants.LOGGING_DETAILED_REQUEST_RESPONSE_ENABLED, false);
        if (detailedLogging) {
            log.error("=== SERVICE EXCEPTION EVENT ===");
            log.error("Service exception for: {}", event.getAuthentication().getPrincipal());
            log.error("Exception: {}", event.getException().getMessage());
            log.error("Full Exception: ", event.getException());
            log.error("=== END SERVICE EXCEPTION ===");
        } else {
            log.error("Service exception for: {} - {}", event.getAuthentication().getPrincipal(), event.getException().getMessage());
        }
    }
    
}