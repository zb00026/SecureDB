package com.verlake.dam.service.auth;
import com.verlake.dam.enums.AuthProvider;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class TokenServiceManager {

    private final Map<AuthProvider, TokenService> tokenServiceMap;

    @Value("${auth.provider}")
    private List<String> activeProviders;

    public TokenServiceManager(List<TokenService> tokenServices) {
        // Map each TokenService to its corresponding AuthProvider
        this.tokenServiceMap = tokenServices.stream()
                .collect(Collectors.toMap(TokenService::getAuthProvider, service -> service));
    }

    @PostConstruct
    public void init() {
        // Log or debug to verify that the map is populated
        log.info("TokenService Map initialized with: " + tokenServiceMap.keySet());
    }

    public TokenService getService(AuthProvider provider) {
        log.info("=== TokenServiceManager.getService START ===");
        log.info("Requested provider: {}", provider);
        log.info("Available providers: {}", tokenServiceMap.keySet());
        log.info("Active providers from config: {}", activeProviders);
        
        if (!isProviderActive(provider)) {
            log.error("Provider {} is NOT ACTIVE", provider);
            log.error("This will result in a BAD_REQUEST (400) response");
            return null;
        }
        
        TokenService service = tokenServiceMap.get(provider);
        log.info("Token service found for provider {}: {}", provider, service != null ? service.getClass().getSimpleName() : "null");
        log.info("=== TokenServiceManager.getService SUCCESS ===");
        return service;
    }

    public boolean isProviderActive(AuthProvider provider) {
        String providerName = provider.name().toLowerCase();
        
        // Check if the provider name directly matches
        if (activeProviders.contains(providerName)) {
            return true;
        }
        
        // Handle keycloak_sso variant for KEYCLOAK provider
        if (provider == AuthProvider.KEYCLOAK) {
            return activeProviders.stream()
                    .anyMatch(active -> active.equals("keycloak_sso") || 
                                       active.equals("keycloak-sso") ||
                                       active.equals("keycloak"));
        }
        
        return false;
    }

    public List<TokenService> getActiveServices() {
        return tokenServiceMap.entrySet().stream()
                .filter(entry -> isProviderActive(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
    }
}