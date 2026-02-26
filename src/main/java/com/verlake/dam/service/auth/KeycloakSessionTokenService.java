package com.verlake.dam.service.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verlake.dam.configuration.ConditionalOnAuthProviderParam;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.exception.JwtTokenException;
import com.verlake.dam.exception.UserNotFoundException;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared service for generating Keycloak session tokens via impersonation or token exchange.
 * Used by Freshdesk and Jira integrations when users authenticate via external systems
 * and need a Hagrids/Keycloak token for subsequent API calls.
 */
@Service
@Slf4j
@ConditionalOnAuthProviderParam(field = "auth.provider", containProvider = "keycloak")
public class KeycloakSessionTokenService {

    private static final String IMPERSONATION_CONFIGURATION_ERROR_MESSAGE = """
            Failed to generate token without changing password.

            Keycloak 26.1.0's impersonation API is designed for browser-based flows, not programmatic API access.
            To enable programmatic impersonation, you MUST enable Legacy Token Exchange (V1) in Keycloak:

            STEP 1: Enable Preview Features in Keycloak (Server Startup)
            -------------------------------------------------------------
            Legacy Token Exchange V1 CANNOT be enabled via Admin Console - it must be set at server startup.

            Docker command (you've already done this):
              docker run ... -e KC_FEATURES=preview,token-exchange ...

            IMPORTANT: Also enable Fine-grained Admin Permissions V1 (required for token exchange):
              docker run ... -e KC_FEATURES=preview,token-exchange,admin-fine-grained-authz ...

            STEP 2: Verify Features Are Enabled
            ------------------------------------
            After starting Keycloak, verify features are enabled:
              docker exec -it keycloak /opt/keycloak/bin/kc.sh show-config | grep features

            You should see: preview,token-exchange,admin-fine-grained-authz

            STEP 3: Assign Impersonation Role (if not already done)
            --------------------------------------------------------
            1. Go to: Clients → backend-client
            2. Click on 'Service Account Roles' tab
            3. Under 'Client Roles', select 'realm-management'
            4. Find 'impersonation' in 'Available Roles' and click 'Add selected'
               OR assign 'realm-admin' role (which includes impersonation)
            5. Click 'Save'

            STEP 4: Restart Keycloak
            -------------------------
            After making these changes, restart Keycloak for them to take effect.

            NOTE: Legacy Token Exchange V1 settings are NOT in the Admin Console UI.
            They are automatically enabled when you start Keycloak with the feature flags above.

            After completing these steps, the integration will work without changing user passwords.

            Note: Without Legacy Token Exchange (V1), Keycloak 26.1.0's impersonation only works in browser flows.""";

    private final KeycloakService keycloakService;
    private final ObjectMapper objectMapper;

    @Value("${keycloak.auth-server-url}")
    private String keycloakAuthServerUrl;

    @Value("${keycloak.realm}")
    private String keycloakRealm;

    @Value("${keycloak.realms-path:/realms/}")
    private String keycloakRealmsPath;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-id}")
    private String keycloakClientId;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-secret}")
    private String keycloakClientSecret;

    public KeycloakSessionTokenService(KeycloakService keycloakService, ObjectMapper objectMapper) {
        this.keycloakService = keycloakService;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate Hagrids/Keycloak authentication token for a user.
     * Uses Keycloak impersonation or token exchange to obtain a token without changing the user's password.
     *
     * @param user DAM user entity (must have valid email)
     * @return JWT access token for the user
     * @throws UserNotFoundException if user not found in Keycloak
     * @throws JwtTokenException     if token generation fails
     */
    public String generateTokenForUser(User user) {
        try {
            RealmResource realm = keycloakService.getRealmInstance();
            UsersResource usersResource = realm.users();

            List<UserRepresentation> users = usersResource.searchByEmail(user.getEmail(), true);
            if (users == null || users.isEmpty()) {
                log.error("Keycloak user not found for email: {}", user.getEmail());
                log.error("User exists in database (ID: {}) but not found in Keycloak realm '{}'",
                        user.getId(), realm.toRepresentation().getRealm());
                log.error("Possible causes:");
                log.error("1. User was not created in Keycloak");
                log.error("2. User exists in a different realm");
                log.error("3. User email mismatch between database and Keycloak");
                throw new UserNotFoundException("Keycloak user not found. Please contact administrator.");
            }

            UserRepresentation keycloakUser = users.get(0);
            String keycloakUserId = keycloakUser.getId();
            log.debug("Found Keycloak user: {} (ID: {})", user.getEmail(), keycloakUserId);

            String userToken = generateTokenViaImpersonation(keycloakUserId, user.getEmail());

            if (userToken == null || userToken.isEmpty()) {
                log.error("Failed to generate token for user: {}", user.getEmail());
                throw new JwtTokenException("Failed to generate authentication token. Please contact administrator.");
            }

            log.info("Successfully generated Keycloak token for user: {}", user.getEmail());
            return userToken;

        } catch (UserNotFoundException | JwtTokenException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error generating Keycloak token for user: {}", user.getEmail(), e);
            throw new JwtTokenException("Failed to generate authentication token: " + e.getMessage(), e);
        }
    }

    private String generateTokenViaImpersonation(String keycloakUserId, String userEmail) {
        String adminToken = getAdminTokenForImpersonation();

        try {
            String token = attemptImpersonation(keycloakUserId, userEmail, adminToken);
            if (token != null && !token.isEmpty()) {
                log.info("Successfully obtained token via impersonation for user: {}", userEmail);
                return token;
            }

            log.warn("Impersonation returned null (likely 403 error - check logs above). Trying token exchange as fallback.");
            return generateTokenViaTokenExchange(keycloakUserId, userEmail, adminToken);

        } catch (Exception e) {
            log.warn("Impersonation API failed with exception for user: {}. Trying token exchange as fallback.",
                    userEmail, e);
            return attemptTokenExchangeFallback(keycloakUserId, userEmail);
        }
    }

    private String getAdminTokenForImpersonation() {
        String adminToken = getAdminToken();
        if (adminToken == null || adminToken.isEmpty()) {
            log.error("Failed to get admin token for impersonation");
            throw new JwtTokenException("Failed to get admin token for impersonation");
        }
        return adminToken;
    }

    private String attemptImpersonation(String keycloakUserId, String userEmail, String adminToken) {
        String impersonationEndpoint = buildImpersonationEndpoint(keycloakUserId);
        RestTemplate restTemplate = createRestTemplateForImpersonation();
        HttpEntity<String> request = createImpersonationRequest(adminToken);

        log.debug("Attempting impersonation for user: {} at endpoint: {}", userEmail, impersonationEndpoint);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    impersonationEndpoint,
                    HttpMethod.POST,
                    request,
                    String.class);

            int statusCode = response.getStatusCode().value();
            log.debug("Impersonation API response status: {}", statusCode);

            if (response.getStatusCode().is3xxRedirection()) {
                log.debug("Received redirect response, handling redirect...");
                return handleRedirectResponse(response, userEmail, adminToken);
            }

            if (response.getStatusCode().is2xxSuccessful()) {
                return handleSuccessfulImpersonationResponse(response, userEmail, adminToken);
            }

            if (statusCode == 403) {
                logImpersonation403Error(userEmail, keycloakUserId, response.getBody(), impersonationEndpoint);
                return null;
            }

            log.warn("Impersonation API returned unexpected status: {} for user: {}", statusCode, userEmail);
            return null;
        } catch (org.springframework.web.client.HttpClientErrorException.Forbidden e) {
            log.error("=== IMPERSONATION 403 FORBIDDEN ERROR (Exception) ===");
            log.error("Exception message: {}", e.getMessage());
            logImpersonation403Error(userEmail, keycloakUserId, e.getResponseBodyAsString(), impersonationEndpoint);
            return null;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("HTTP error during impersonation for user: {}. Status: {}, Response: {}",
                    userEmail, e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error during impersonation for user: {}", userEmail, e);
            log.error("Exception type: {}, Message: {}", e.getClass().getName(), e.getMessage());
            return null;
        }
    }

    private String handleSuccessfulImpersonationResponse(ResponseEntity<String> response, String userEmail, String adminToken) {
        String responseBody = response.getBody();

        if (responseBody == null) {
            log.warn("Impersonation returned 200 OK but response body is null for user: {}", userEmail);
            return null;
        }

        log.debug("Response body preview (first 300 chars): {}",
                responseBody.length() > 300 ? responseBody.substring(0, 300) + "..." : responseBody);

        String redirectUrl = extractRedirectUrlFromJson(responseBody);
        if (redirectUrl != null) {
            log.debug("Found redirect URL in JSON response: {}", redirectUrl);
            log.info("Following redirect URL to extract token...");
            return followImpersonationRedirect(redirectUrl, userEmail, adminToken);
        }

        String token = extractTokenFromDirectUrl(responseBody, userEmail);
        if (token != null) {
            return token;
        }

        return handleSuccessResponse(response, userEmail);
    }

    private String extractTokenFromDirectUrl(String responseBody, String userEmail) {
        String trimmedBody = responseBody.trim();
        if (!trimmedBody.startsWith(Constants.HTTP_PROTOCOL_PREFIX)
                && !trimmedBody.startsWith(Constants.HTTPS_PROTOCOL_PREFIX)) {
            return null;
        }

        log.debug("Response body is a direct redirect URL, extracting token...");
        String token = extractTokenFromUrl(trimmedBody);
        if (token != null && !token.isEmpty()) {
            log.info("Successfully obtained token via impersonation (from redirect URL in 200 response) for user: {}",
                    userEmail);
            return token;
        }
        return null;
    }

    private String buildImpersonationEndpoint(String keycloakUserId) {
        String endpoint = keycloakAuthServerUrl + "/admin/realms/" + keycloakRealm
                + "/users/" + keycloakUserId + "/impersonation";
        log.debug("Built impersonation endpoint: {}", endpoint);
        return endpoint;
    }

    private RestTemplate createRestTemplateForImpersonation() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod)
                    throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        });
        return restTemplate;
    }

    private HttpEntity<String> createImpersonationRequest(String adminToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        String redirectUri = keycloakAuthServerUrl + keycloakRealmsPath + keycloakRealm + "/protocol/openid-connect/auth";

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("redirect_uri", redirectUri);

        try {
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            return new HttpEntity<>(jsonBody, headers);
        } catch (Exception e) {
            log.warn("Failed to create impersonation request body, using empty body", e);
            return new HttpEntity<>(headers);
        }
    }

    private String handleRedirectResponse(ResponseEntity<String> response, String userEmail, String adminToken) {
        String redirectUrl = response.getHeaders().getFirst(Constants.HTTP_HEADER_LOCATION);
        if (redirectUrl == null) {
            redirectUrl = extractRedirectUrlFromResponseBody(response.getBody());
            if (redirectUrl == null) {
                log.warn("Redirect response but no Location header found for user: {}", userEmail);
                return null;
            }
        }

        log.debug("Extracting token from redirect URL: {}",
                redirectUrl.length() > 100 ? redirectUrl.substring(0, 100) + "..." : redirectUrl);
        String token = extractTokenFromUrl(redirectUrl);
        if (token != null && !token.isEmpty()) {
            log.debug("Successfully obtained token via impersonation redirect for user: {}", userEmail);
            return token;
        }

        return followImpersonationRedirect(redirectUrl, userEmail, adminToken);
    }

    private String extractRedirectUrlFromResponseBody(String responseBody) {
        if (responseBody != null && (responseBody.startsWith(Constants.HTTP_PROTOCOL_PREFIX)
                || responseBody.startsWith(Constants.HTTPS_PROTOCOL_PREFIX))) {
            String redirectUrl = responseBody.trim();
            log.debug("Found redirect URL in response body: {}", redirectUrl);
            return redirectUrl;
        }
        return null;
    }

    private String extractRedirectUrlFromJson(String jsonResponse) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            JsonNode redirectNode = jsonNode.get("redirect");
            if (redirectNode != null && redirectNode.isTextual()) {
                String url = redirectNode.asText();
                // Keycloak returns /account which fails when followed with admin token (userSession is null, NPE).
                // Skip it so we fall back to token exchange.
                if (url != null && url.contains("/account")) {
                    log.debug("Skipping /account redirect (known to fail with admin token - Keycloak expects user session). Will use token exchange fallback.");
                    return null;
                }
                return url;
            }
        } catch (Exception e) {
            log.debug("Failed to parse JSON response for redirect URL: {}", e.getMessage());
        }
        return null;
    }

    private String followImpersonationRedirect(String redirectUrl, String userEmail, String adminToken) {
        if (!isValidRedirectUrl(redirectUrl)) {
            return null;
        }

        log.debug("Following impersonation redirect URL: {} (with admin token)", redirectUrl);

        try {
            RestTemplate noRedirectTemplate = createNoRedirectRestTemplate();
            HttpEntity<String> request = createAuthenticatedRequest(adminToken);
            ResponseEntity<String> redirectResponse = noRedirectTemplate.exchange(
                    redirectUrl, HttpMethod.GET, request, String.class);

            String token = extractTokenFromRedirectResponse(redirectResponse, userEmail, adminToken);
            if (token != null) {
                return token;
            }

        } catch (org.springframework.web.client.HttpServerErrorException e) {
            log.warn("Keycloak returned 5xx when following redirect (e.g. /account expects user session, not admin token): {}. Using token exchange fallback.", e.getStatusCode());
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String token = extractTokenFromErrorResponse(e);
            if (token != null) {
                return token;
            }
            log.debug("HTTP error following redirect: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Failed to follow impersonation redirect URL: {}", redirectUrl, e);
        }

        return null;
    }

    private boolean isValidRedirectUrl(String redirectUrl) {
        if (redirectUrl == null || (!redirectUrl.startsWith(Constants.HTTP_PROTOCOL_PREFIX)
                && !redirectUrl.startsWith(Constants.HTTPS_PROTOCOL_PREFIX))) {
            log.warn("Invalid redirect URL: {}", redirectUrl);
            return false;
        }
        return true;
    }

    private RestTemplate createNoRedirectRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod)
                    throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        });
        return restTemplate;
    }

    private HttpEntity<String> createAuthenticatedRequest(String adminToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        return new HttpEntity<>(headers);
    }

    private String extractTokenFromRedirectResponse(ResponseEntity<String> redirectResponse, String userEmail, String adminToken) {
        if (redirectResponse.getStatusCode().is3xxRedirection()) {
            return handleRedirectResponse(redirectResponse, userEmail, adminToken);
        }

        if (redirectResponse.getStatusCode().is2xxSuccessful()) {
            return extractTokenFromSuccessResponse(redirectResponse.getBody(), userEmail);
        }

        return null;
    }

    private String extractTokenFromSuccessResponse(String responseBody, String userEmail) {
        if (responseBody == null) {
            return null;
        }

        log.debug("Response body from redirect (first 500 chars): {}",
                responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);

        if (responseBody.contains(Constants.URL_ACCESS_TOKEN_PARAM)) {
            String token = extractTokenFromUrl(responseBody);
            if (token != null && !token.isEmpty()) {
                log.info("Successfully extracted token from response body for user: {}", userEmail);
                return token;
            }
        }

        return extractTokenFromResponse(responseBody);
    }

    private String extractTokenFromErrorResponse(org.springframework.web.client.HttpClientErrorException e) {
        if (e.getResponseHeaders() != null) {
            String location = e.getResponseHeaders().getFirst(Constants.HTTP_HEADER_LOCATION);
            if (location != null) {
                log.debug("Found Location in error response: {}", location);
                return extractTokenFromUrl(location);
            }
        }
        return null;
    }

    private void logImpersonation403Error(String userEmail, String keycloakUserId, String responseBody, String endpoint) {
        log.error("=== IMPERSONATION 403 FORBIDDEN ERROR ===");
        log.error("User: {}", userEmail);
        log.error("User ID: {}", keycloakUserId);
        log.error("Response status: 403");
        log.error("Response body: {}", responseBody);
        log.error("Endpoint: {}", endpoint);
        log.error("");
        log.error("TROUBLESHOOTING STEPS:");
        log.error("1. Verify role assignment in Keycloak Admin Console:");
        log.error("   - Go to: Clients → backend-client → Service Account Roles tab");
        log.error("   - Under 'Client Roles', select 'realm-management'");
        log.error("   - Check 'impersonation' is in 'Assigned Roles' (NOT just Available)");
        log.error("   - If missing, add it and click 'Save'");
        log.error("");
        log.error("2. After assigning role, wait 10-30 seconds for Keycloak to update");
        log.error("   OR restart Keycloak service: sudo systemctl restart keycloak");
        log.error("");
        log.error("3. Verify the role is actually assigned:");
        log.error("   - The 'impersonation' role should appear under 'Assigned Roles'");
        log.error("   - NOT just in 'Available Roles'");
        log.error("");
        log.error("4. Alternative: Assign 'realm-admin' role instead (includes impersonation)");
        log.error("5. Verify service account token includes the role - may need to regenerate token");
        log.error("==========================================");
    }

    private void logTokenExchangeFailure(String userEmail) {
        log.error("All token generation methods failed for user: {}. " +
                "Impersonation returned 403 (verify role assignment) and token exchange is not supported in Keycloak 26.1.0.",
                userEmail);
        log.error("TROUBLESHOOTING: Verify impersonation role assignment:");
        log.error("1. Keycloak Admin Console → Clients → backend-client");
        log.error("2. Service Account Roles tab → Select 'realm-management' client");
        log.error("3. Ensure 'impersonation' role is in 'Assigned Roles' (not just Available)");
        log.error("4. If role is assigned, try restarting Keycloak or regenerating service account token");
    }

    private String handleSuccessResponse(ResponseEntity<String> response, String userEmail) {
        String responseBody = response.getBody();
        if (responseBody == null) {
            log.warn("Impersonation returned 200 but response body is null for user: {}", userEmail);
            return null;
        }

        log.debug("Impersonation response body (first 500 chars): {}",
                responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);

        if (responseBody.startsWith(Constants.HTTP_PROTOCOL_PREFIX) || responseBody.startsWith(Constants.HTTPS_PROTOCOL_PREFIX)) {
            log.debug("Response appears to be a redirect URL, extracting token...");
            String token = extractTokenFromUrl(responseBody);
            if (token != null && !token.isEmpty()) {
                log.debug("Successfully obtained token from redirect URL for user: {}", userEmail);
                return token;
            }
        }

        String token = extractTokenFromResponse(responseBody);
        if (token != null && !token.isEmpty()) {
            log.debug("Successfully obtained token via impersonation for user: {}", userEmail);
            return token;
        }

        log.warn("Failed to extract token from impersonation response for user: {}. Response body: {}",
                userEmail, responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody);
        return null;
    }

    private String attemptTokenExchangeFallback(String keycloakUserId, String userEmail) {
        try {
            String adminToken = getAdminToken();
            String token = generateTokenViaTokenExchange(keycloakUserId, userEmail, adminToken);
            if (token != null && !token.isEmpty()) {
                return token;
            }
            logTokenExchangeFailure(userEmail);
            throw new JwtTokenException(IMPERSONATION_CONFIGURATION_ERROR_MESSAGE);
        } catch (JwtTokenException e) {
            throw e;
        } catch (Exception ex) {
            log.error("All token generation methods failed for user: {}", userEmail, ex);
            logTokenExchangeFailure(userEmail);
            throw new JwtTokenException(IMPERSONATION_CONFIGURATION_ERROR_MESSAGE);
        }
    }

    private String generateTokenViaTokenExchange(String keycloakUserId, String userEmail, String serviceAccountToken) {
        try {
            String tokenExchangeEndpoint = keycloakAuthServerUrl + keycloakRealmsPath + keycloakRealm
                    + "/protocol/openid-connect/token";

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
            params.add("client_id", keycloakClientId);
            params.add("client_secret", keycloakClientSecret);
            params.add("subject_token", serviceAccountToken);
            params.add("subject_token_type", "urn:ietf:params:oauth:token-type:access_token");
            params.add("requested_subject", keycloakUserId);
            params.add("audience", keycloakClientId);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    tokenExchangeEndpoint,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> tokenResponse = response.getBody();
                String accessToken = (String) tokenResponse.get("access_token");
                if (accessToken != null && !accessToken.isEmpty()) {
                    log.debug("Successfully obtained token via token exchange for user: {}", userEmail);
                    return accessToken;
                }
            }

            if (response.getBody() != null) {
                Map<String, Object> errorResponse = response.getBody();
                String error = (String) errorResponse.get("error");
                if ("unsupported_grant_type".equals(error)) {
                    log.warn("Token exchange is not supported in this Keycloak version. " +
                            "Please assign 'impersonation' role to backend-client service account instead.");
                }
            }

            return null;
        } catch (org.springframework.web.client.HttpClientErrorException.BadRequest e) {
            log.warn("Token exchange not supported in this Keycloak version (unsupported_grant_type). " +
                    "Please configure impersonation role in Keycloak instead.");
            return null;
        } catch (Exception e) {
            log.debug("Token exchange not available: {}", e.getMessage());
            return null;
        }
    }

    private String extractTokenFromUrl(String url) {
        try {
            if (url == null || url.isEmpty()) {
                return null;
            }

            log.debug("Extracting token from URL (first 200 chars): {}",
                    url.length() > 200 ? url.substring(0, 200) + "..." : url);

            int tokenStart = findTokenStartPosition(url);
            if (tokenStart == -1 || tokenStart >= url.length()) {
                log.warn("Could not find access_token in URL");
                return null;
            }

            int tokenEnd = findTokenEndPosition(url, tokenStart);
            String token = url.substring(tokenStart, tokenEnd);

            if (token != null && !token.isEmpty()) {
                log.debug("Successfully extracted token from URL (length: {})", token.length());
                return token;
            }

            log.warn("Could not find access_token in URL");
            return null;
        } catch (Exception e) {
            log.warn("Error extracting token from URL: {}", e.getMessage(), e);
            return null;
        }
    }

    private int findTokenStartPosition(String url) {
        int tokenStart = url.indexOf(Constants.URL_ACCESS_TOKEN_PARAM);
        if (tokenStart != -1) {
            return tokenStart + Constants.URL_ACCESS_TOKEN_PARAM.length();
        }

        String fragmentParam = "#" + Constants.URL_ACCESS_TOKEN_PARAM;
        tokenStart = url.indexOf(fragmentParam);
        if (tokenStart != -1) {
            return tokenStart + fragmentParam.length();
        }

        return -1;
    }

    private int findTokenEndPosition(String url, int tokenStart) {
        int tokenEnd = url.indexOf("&", tokenStart);
        if (tokenEnd == -1) {
            tokenEnd = url.indexOf("#", tokenStart);
            if (tokenEnd == -1) {
                tokenEnd = url.length();
            }
        }
        return tokenEnd;
    }

    private String extractTokenFromResponse(String responseBody) {
        try {
            if (responseBody.contains("\"access_token\"")) {
                int tokenStart = responseBody.indexOf("\"access_token\":\"") + "\"access_token\":\"".length();
                int tokenEnd = responseBody.indexOf("\"", tokenStart);
                if (tokenEnd != -1) {
                    return responseBody.substring(tokenStart, tokenEnd);
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting token from response", e);
        }
        return null;
    }

    private String getAdminToken() {
        RestTemplate restTemplate = new RestTemplate();

        String tokenEndpoint = keycloakAuthServerUrl + keycloakRealmsPath + keycloakRealm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "client_credentials");
        params.add("client_id", keycloakClientId);
        params.add("client_secret", keycloakClientSecret);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    tokenEndpoint,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> tokenResponse = response.getBody();
                String accessToken = (String) tokenResponse.get("access_token");

                if (accessToken != null) {
                    log.debug("Admin token obtained successfully. Token preview: {}...",
                            accessToken.substring(0, Math.min(50, accessToken.length())));
                }

                return accessToken;
            }

            log.error("Failed to get admin token. Status: {}", response.getStatusCode());
            return null;
        } catch (Exception e) {
            log.error("Error getting admin token", e);
            return null;
        }
    }
}
