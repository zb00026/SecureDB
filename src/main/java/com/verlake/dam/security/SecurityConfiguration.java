package com.verlake.dam.security;

import com.verlake.dam.enums.Roles;
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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
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
        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF for simplicity (optional)
                .cors(cors -> cors.disable()) // Disable CORS (optional, enable as per your requirements)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/public/**", "/api/auth/**", "/api/firebase/notifications/**").permitAll()
                        .requestMatchers("/api" + Roles.ADMIN.getAvailablePath()).hasAuthority(Roles.ADMIN.name())
                        .requestMatchers("/api/asset_owner/assets/**").hasAnyAuthority(Roles.ADMIN.name(), Roles.ASSET_OWNER.name())
                        .requestMatchers("/api" + Roles.DEVELOPER.getAvailablePath()).hasAuthority(Roles.DEVELOPER.name())
                        .requestMatchers("/api" + Roles.APPROVER.getAvailablePath()).hasAuthority(Roles.APPROVER.name())
                        .requestMatchers("/api" + Roles.AUDITOR.getAvailablePath()).hasAuthority(Roles.AUDITOR.name())
                        .requestMatchers("/api" + Roles.ASSET_OWNER.getAvailablePath()).hasAuthority(Roles.ASSET_OWNER.name())
                        .requestMatchers("/api/audit-trails/**").hasAnyAuthority(Roles.ADMIN.name(), Roles.AUDITOR.name())
                        .anyRequest().denyAll()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // Optional: Use stateless session
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwtConfig -> jwtConfig
                                .jwtAuthenticationConverter(jwt -> {
                                    String issuer = jwt.getClaimAsString("iss");
                                    if (keycloakIssuerUri.equals(issuer) ||
                                            jwksUri.equals(issuer) || issuer.equals("https://accounts.google.com")) {
                                        return jwtAuthenticationConverter.convert(jwt);
                                    }
                                    throw new IllegalArgumentException("Unknown token issuer: " + issuer);
                                })
                        )
                ); // Disable OAuth2 resource server (if enabled)

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return new CompositeJwtDecoder(
                JwtDecoders.fromIssuerLocation(keycloakIssuerUri),
                NimbusJwtDecoder.withJwkSetUri(jwksUri).build()
        );
    }

}
