package com.verlake.dam.service.impl;

import com.verlake.dam.configuration.ConditionalOnAuthProviderParam;
import com.verlake.dam.enums.AuthProvider;
import com.verlake.dam.service.auth.TokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Service
@ConditionalOnAuthProviderParam(field = "auth.provider", containProvider = "google")
@Slf4j
public class GoogleTokenService implements TokenService {
    private static final String GOOGLE_TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo";

    /**
     * Verifies the Google token by calling Google's token info endpoint.
     * @param token The Google OAuth token to verify.
     * @return true if the token is valid, false otherwise.
     */
    @Override
    public boolean verifyToken(String token) {
        log.info("=== GoogleTokenService.verifyToken START ===");
        log.info("Verifying Google OAuth token");
        log.info("Token preview: {}...", token != null ? token.substring(0, Math.min(50, token.length())) : "null");
        
        try {
            RestTemplate restTemplate = new RestTemplate();
            @SuppressWarnings("deprecation")
            String url = UriComponentsBuilder
                    .fromHttpUrl(GOOGLE_TOKEN_INFO_URL)
                    .queryParam("id_token", token)
                    .toUriString();
            
            log.info("Calling Google token info endpoint: {}", GOOGLE_TOKEN_INFO_URL);

            // Call Google's token info endpoint
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            log.info("Google API response received");
            log.info("Response keys: {}", response != null ? response.keySet() : "null");
            
            if (response != null) {
                log.info("Email from response: {}", response.get("email"));
                log.info("Email verified flag: {}", response.get("email_verified"));
                log.info("Audience: {}", response.get("aud"));
                log.info("Issuer: {}", response.get("iss"));
            }

            // Validate the response (example: check the email_verified flag)
            boolean isValid = response != null && "true".equals(response.get("email_verified").toString());
            log.info("Token verification result: {}", isValid);
            
            if (isValid) {
                log.info("=== GoogleTokenService.verifyToken SUCCESS ===");
            } else {
                log.error("=== GoogleTokenService.verifyToken FAILED ===");
                log.error("Token verification failed - email not verified or invalid response");
            }
            
            return isValid;
        } catch (Exception e) {
            log.error("=== GoogleTokenService.verifyToken FAILED ===");
            log.error("Exception during Google token verification: {}", e.getMessage());
            log.error("Exception type: {}", e.getClass().getSimpleName());
            log.error("Full stack trace: ", e);
            return false; // Return false if the token is invalid or verification fails
        }
    }

    /**
     * Extracts the email address from a verified Google token.
     * @param token The Google OAuth token.
     * @return The email address, or null if the token is invalid.
     */
    @Override
    public String getEmailFromToken(String token) {
        log.info("=== GoogleTokenService.getEmailFromToken START ===");
        
        try {
            RestTemplate restTemplate = new RestTemplate();
            @SuppressWarnings("deprecation")
            String url = UriComponentsBuilder
                    .fromHttpUrl(GOOGLE_TOKEN_INFO_URL)
                    .queryParam("id_token", token)
                    .toUriString();
            
            log.info("Calling Google token info endpoint to extract email");

            // Call Google's token info endpoint
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            log.info("Google API response received for email extraction");

            // Return the email address if the response is valid
            if (response != null && response.containsKey("email")) {
                String email = response.get("email").toString();
                log.info("Email extracted from Google token: {}", email);
                log.info("=== GoogleTokenService.getEmailFromToken SUCCESS ===");
                return email;
            }

            log.error("No email found in Google token response");
            log.error("Response: {}", response);
            log.error("=== GoogleTokenService.getEmailFromToken FAILED ===");
            return null; // Return null if the token is invalid or email is not found
        } catch (Exception e) {
            log.error("=== GoogleTokenService.getEmailFromToken FAILED ===");
            log.error("Exception during email extraction: {}", e.getMessage());
            log.error("Exception type: {}", e.getClass().getSimpleName());
            log.error("Full stack trace: ", e);
            return null;
        }
    }

    @Override
    public AuthProvider getAuthProvider() {
        return AuthProvider.GOOGLE;
    }

}
