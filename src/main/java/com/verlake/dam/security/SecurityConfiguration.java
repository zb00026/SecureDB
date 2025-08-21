package com.verlake.dam.security;

import com.verlake.dam.enums.Roles;
import com.verlake.dam.utils.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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
        log.info("=== Configuring Security Filter Chain ===");
        log.info("Keycloak Issuer URI: {}", keycloakIssuerUri);
        log.info("Google JWKS URI: {}", jwksUri);
        
        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF for simplicity (optional)
                .cors(cors -> cors.disable()) // Disable CORS (optional, enable as per your requirements)
                .authorizeHttpRequests(authorize -> {
                    log.info("Configuring authorization rules:");
                    log.info("  - Permitting all: /public/**, /api/auth/**, /api/firebase/notifications/**, /api/license/status, /api/auth/validateResetToken");
                    log.info("  - Admin paths: /api{}", Roles.ADMIN.getAvailablePath());
                    log.info("  - Asset Owner paths: /api/asset_owner/assets/**");
                    log.info("  - Developer paths: /api{}", Roles.DEVELOPER.getAvailablePath());
                    log.info("  - Approver paths: /api{}", Roles.APPROVER.getAvailablePath());
                    log.info("  - Auditor paths: /api{}", Roles.AUDITOR.getAvailablePath());
                    log.info("  - Asset Owner paths: /api{}", Roles.ASSET_OWNER.getAvailablePath());
                    log.info("  - Audit trails: /api/audit-trails/**");
                    log.info("  - All other requests: DENY ALL");
                    
                    authorize
                            .requestMatchers("/public/**", "/api/auth/**", "/api/firebase/notifications/**",
                                    "/api/license/status", "/api/auth/validateResetToken").permitAll()
                            .requestMatchers("/api" + Roles.ADMIN.getAvailablePath()).hasAuthority(Constants.SECURITY_ROLE_PREFIX + Roles.ADMIN.name())
                            .requestMatchers("/api/asset_owner/assets/**").hasAnyAuthority(Constants.SECURITY_ROLE_PREFIX + Roles.ADMIN.name(), Constants.SECURITY_ROLE_PREFIX + Roles.ASSET_OWNER.name())
                            .requestMatchers("/api" + Roles.DEVELOPER.getAvailablePath()).hasAuthority(Constants.SECURITY_ROLE_PREFIX + Roles.DEVELOPER.name())
                            .requestMatchers("/api" + Roles.APPROVER.getAvailablePath()).hasAuthority(Constants.SECURITY_ROLE_PREFIX + Roles.APPROVER.name())
                            .requestMatchers("/api" + Roles.AUDITOR.getAvailablePath()).hasAuthority(Constants.SECURITY_ROLE_PREFIX + Roles.AUDITOR.name())
                            .requestMatchers("/api" + Roles.ASSET_OWNER.getAvailablePath()).hasAuthority(Constants.SECURITY_ROLE_PREFIX + Roles.ASSET_OWNER.name())
                            .requestMatchers("/api/audit-trails/**").hasAnyAuthority(Constants.SECURITY_ROLE_PREFIX + Roles.ADMIN.name(), Constants.SECURITY_ROLE_PREFIX + Roles.AUDITOR.name())
                            .anyRequest().denyAll();
                })
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Optional: Use stateless session
                .oauth2ResourceServer(oauth2 -> {
                    log.info("Configuring OAuth2 Resource Server with JWT support");
                    oauth2.jwt(jwtConfig -> jwtConfig
                            .jwtAuthenticationConverter(jwt -> {
                                log.info("=== JWT Authentication Converter Called ===");
                                String issuer = jwt.getClaimAsString("iss");
                                log.info("JWT Issuer: {}", issuer);
                                log.info("Expected Keycloak Issuer: {}", keycloakIssuerUri);
                                log.info("Expected Google JWKS URI: {}", jwksUri);
                                log.info("Expected Google Issuer: https://accounts.google.com");
                                
                                if (keycloakIssuerUri.equals(issuer) ||
                                        jwksUri.equals(issuer) || issuer.equals("https://accounts.google.com")) {
                                    log.info("Issuer matched, converting JWT with CustomJwtAuthenticationConverter");
                                    return jwtAuthenticationConverter.convert(jwt);
                                }
                                log.error("Unknown token issuer: {}", issuer);
                                throw new IllegalArgumentException("Unknown token issuer: " + issuer);
                            })
                    );
                }); // Disable OAuth2 resource server (if enabled)

        log.info("=== Security Filter Chain Configuration Complete ===");
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        log.info("=== Creating Composite JWT Decoder ===");
        log.info("Keycloak Issuer URI: {}", keycloakIssuerUri);
        log.info("Google JWKS URI: {}", jwksUri);
        
        return new CompositeJwtDecoder(
                JwtDecoders.fromIssuerLocation(keycloakIssuerUri),
                NimbusJwtDecoder.withJwkSetUri(jwksUri).build()
        );
    }

}