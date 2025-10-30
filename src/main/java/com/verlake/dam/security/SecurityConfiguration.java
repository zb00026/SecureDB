package com.verlake.dam.security;

import com.verlake.dam.enums.Roles;
import com.verlake.dam.utils.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Slf4j
public class SecurityConfiguration {

    @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri}")
    private String keycloakIssuerUri;

    @Value("${google.oauth2.jwks-uri}")
    private String jwksUri;

    private final CustomJwtAuthenticationConverter jwtAuthenticationConverter;

    public SecurityConfiguration(CustomJwtAuthenticationConverter jwtAuthenticationConverter) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        boolean detailedLogging = Constants.getTechnicalPropertyAsBoolean(Constants.LOGGING_DETAILED_REQUEST_RESPONSE_ENABLED, false);
        
        logSecurityConfigurationStart(detailedLogging);
        
        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF for simplicity (optional)
                .cors(cors -> cors.disable()) // Disable CORS (optional, enable as per your requirements)
                .authorizeHttpRequests(authorize -> configureAuthorizationRules(authorize, detailedLogging))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Optional: Use stateless session
                .oauth2ResourceServer(oauth2 -> configureOAuth2ResourceServer(oauth2, detailedLogging));

        logSecurityConfigurationComplete(detailedLogging);
        return http.build();
    }
    
    private void logSecurityConfigurationStart(boolean detailedLogging) {
        if (detailedLogging) {
            log.info("=== Configuring Security Filter Chain ===");
            log.info("Keycloak Issuer URI: {}", keycloakIssuerUri);
            log.info("Google JWKS URI: {}", jwksUri);
        } else {
            log.info("Configuring security filter chain...");
        }
    }
    
    private void logSecurityConfigurationComplete(boolean detailedLogging) {
        if (detailedLogging) {
            log.info("=== Security Filter Chain Configuration Complete ===");
        } else {
            log.info("Security filter chain configuration completed");
        }
    }
    
    private void configureAuthorizationRules(org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer<org.springframework.security.config.annotation.web.builders.HttpSecurity>.AuthorizationManagerRequestMatcherRegistry authorize, boolean detailedLogging) {
        if (detailedLogging) {
            logAuthorizationRules();
        }
        
        authorize
                .requestMatchers("/public/**", "/api/auth/**", "/api/firebase/notifications/**",
                        "/api/license/status", "/api/auth/validateResetToken").permitAll()
                .requestMatchers("/ws/terminal/connect").permitAll() // Allow WebSocket connections
                .requestMatchers("/ws/unix-groups").permitAll() // Allow Unix group WebSocket connections
                .requestMatchers("/api" + Roles.ADMIN.getAvailablePath()).hasAuthority(Constants.SECURITY_ROLE_PREFIX + Roles.ADMIN.name())
                .requestMatchers("/api" + Roles.DEVELOPER.getAvailablePath()).hasAuthority(Constants.SECURITY_ROLE_PREFIX + Roles.DEVELOPER.name())
                .requestMatchers("/api" + Roles.APPROVER.getAvailablePath()).hasAuthority(Constants.SECURITY_ROLE_PREFIX + Roles.APPROVER.name())
                .requestMatchers("/api" + Roles.AUDITOR.getAvailablePath()).hasAuthority(Constants.SECURITY_ROLE_PREFIX + Roles.AUDITOR.name())
                .requestMatchers("/api" + Roles.ASSET_OWNER.getAvailablePath(), "/api/ai/chat/**").hasAnyAuthority(
                    Constants.SECURITY_ROLE_PREFIX + Roles.ASSET_OWNER.name(),
                    Constants.SECURITY_ROLE_PREFIX + Roles.ADMIN.name())
                .requestMatchers("/api/audit-trails/**").hasAnyAuthority(Constants.SECURITY_ROLE_PREFIX + Roles.ADMIN.name(), Constants.SECURITY_ROLE_PREFIX + Roles.AUDITOR.name())
                .requestMatchers("/api/schema/**").hasAnyAuthority(
                    Constants.SECURITY_ROLE_PREFIX + Roles.DEVELOPER.name(),
                    Constants.SECURITY_ROLE_PREFIX + Roles.ASSET_OWNER.name(),
                    Constants.SECURITY_ROLE_PREFIX + Roles.ADMIN.name())
                .anyRequest().denyAll();
    }
    
    private void logAuthorizationRules() {
        log.info("Configuring authorization rules:");
        log.info("  - Permitting all: /public/**, /api/auth/**, /api/firebase/notifications/**, /api/license/status, /api/auth/validateResetToken");
        log.info("  - Admin paths: /api{}", Roles.ADMIN.getAvailablePath());
        log.info("  - AI Chat paths: /api/ai/chat/** (Admin only)");
        log.info("  - Asset Owner paths: /api/asset_owner/assets/**");
        log.info("  - Developer paths: /api{}", Roles.DEVELOPER.getAvailablePath());
        log.info("  - Approver paths: /api{}", Roles.APPROVER.getAvailablePath());
        log.info("  - Auditor paths: /api{}", Roles.AUDITOR.getAvailablePath());
        log.info("  - Asset Owner paths: /api{}", Roles.ASSET_OWNER.getAvailablePath());
        log.info("  - Audit trails: /api/audit-trails/**");
        log.info("  - All other requests: DENY ALL");
    }
    
    private void configureOAuth2ResourceServer(OAuth2ResourceServerConfigurer<HttpSecurity> oauth2, boolean detailedLogging) {
        if (detailedLogging) {
            log.info("Configuring OAuth2 Resource Server with JWT support");
        }
        oauth2.jwt(jwtConfig -> jwtConfig
                .jwtAuthenticationConverter(jwt -> createJwtAuthenticationConverter(jwt, detailedLogging))
        );
    }
    
    private AbstractAuthenticationToken createJwtAuthenticationConverter(org.springframework.security.oauth2.jwt.Jwt jwt, boolean detailedLogging) {
        if (detailedLogging) {
            return createJwtAuthenticationConverterWithDetailedLogging(jwt);
        } else {
            return createJwtAuthenticationConverterSimple(jwt);
        }
    }
    
    private AbstractAuthenticationToken createJwtAuthenticationConverterWithDetailedLogging(org.springframework.security.oauth2.jwt.Jwt jwt) {
        log.info("=== JWT Authentication Converter Called ===");
        String issuer = jwt.getClaimAsString("iss");
        log.info("JWT Issuer: {}", issuer);
        log.info("Expected Keycloak Issuer: {}", keycloakIssuerUri);
        log.info("Expected Google JWKS URI: {}", jwksUri);
        log.info("Expected Google Issuer: https://accounts.google.com");
        
        if (isValidIssuer(issuer)) {
            log.info("Issuer matched, converting JWT with CustomJwtAuthenticationConverter");
            return jwtAuthenticationConverter.convert(jwt);
        }
        log.error("Unknown token issuer: {}", issuer);
        throw new IllegalArgumentException("Unknown token issuer: " + issuer);
    }
    
    private AbstractAuthenticationToken createJwtAuthenticationConverterSimple(org.springframework.security.oauth2.jwt.Jwt jwt) {
        String issuer = jwt.getClaimAsString("iss");
        if (isValidIssuer(issuer)) {
            return jwtAuthenticationConverter.convert(jwt);
        }
        throw new IllegalArgumentException("Unknown token issuer: " + issuer);
    }
    
    private boolean isValidIssuer(String issuer) {
        return keycloakIssuerUri.equals(issuer) ||
               jwksUri.equals(issuer) || 
               issuer.equals("https://accounts.google.com");
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        boolean detailedLogging = Constants.getTechnicalPropertyAsBoolean(Constants.LOGGING_DETAILED_REQUEST_RESPONSE_ENABLED, false);
        
        if (detailedLogging) {
            log.info("=== Creating Composite JWT Decoder ===");
            log.info("Keycloak Issuer URI: {}", keycloakIssuerUri);
            log.info("Google JWKS URI: {}", jwksUri);
        } else {
            log.info("Creating JWT decoder...");
        }
        
        return new CompositeJwtDecoder(
                JwtDecoders.fromIssuerLocation(keycloakIssuerUri),
                NimbusJwtDecoder.withJwkSetUri(jwksUri).build()
        );
    }
    

}