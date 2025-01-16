package com.verlake.dam.service.manager;
import com.verlake.dam.enums.AuthProvider;
import com.verlake.dam.service.TokenService;
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
        if (!isProviderActive(provider)) {
            return null;
        }
        return tokenServiceMap.get(provider);
    }

    public boolean isProviderActive(AuthProvider provider) {
        return activeProviders.contains(provider.name().toLowerCase());
    }

    public List<TokenService> getActiveServices() {
        return tokenServiceMap.entrySet().stream()
                .filter(entry -> isProviderActive(entry.getKey()))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }
}