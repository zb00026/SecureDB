package com.verlake.dam.service.impl;

import com.verlake.dam.enums.AuthProvider;
import com.verlake.dam.service.TokenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Service
@ConditionalOnProperty(name = "auth.provider", havingValue = "google")
public class GoogleTokenService implements TokenService {
    private static final String GOOGLE_TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo";

    /**
     * Verifies the Google token by calling Google's token info endpoint.
     * @param token The Google OAuth token to verify.
     * @return true if the token is valid, false otherwise.
     */
    @Override
    public boolean verifyToken(String token) {
        RestTemplate restTemplate = new RestTemplate();
        @SuppressWarnings("deprecation")
        String url = UriComponentsBuilder
                .fromHttpUrl(GOOGLE_TOKEN_INFO_URL)
                .queryParam("id_token", token)
                .toUriString();

        // Call Google's token info endpoint
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);

        // Validate the response (example: check the email_verified flag)
        return response != null && "true".equals(response.get("email_verified").toString());// Return false if the token is invalid or verification fails
    }

    /**
     * Extracts the email address from a verified Google token.
     * @param token The Google OAuth token.
     * @return The email address, or null if the token is invalid.
     */
    @Override
    public String getEmailFromToken(String token) {
        RestTemplate restTemplate = new RestTemplate();
        @SuppressWarnings("deprecation")
        String url = UriComponentsBuilder
                .fromHttpUrl(GOOGLE_TOKEN_INFO_URL)
                .queryParam("id_token", token)
                .toUriString();

        // Call Google's token info endpoint
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);

        // Return the email address if the response is valid
        if (response != null && response.containsKey("email")) {
            return response.get("email").toString();
        }

        return null; // Return null if the token is invalid or email is not found
    }

    @Override
    public AuthProvider getAuthProvider() {
        return AuthProvider.GOOGLE;
    }

}
