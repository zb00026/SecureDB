package com.verlake.dam.service.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.users.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for OAuth 2.0 authentication with Jira
 * Follows Freshdesk pattern: Jira OAuth → Extract Account ID/Email → Find DAM User → Generate Keycloak Token
 */
@Service
@Slf4j
public class JiraOAuthService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final KeycloakService keycloakService;
    
    @Value("${jira.api.url}")
    private String jiraApiUrl;
    
    @Value("${jira.oauth.client.id}")
    private String clientId;
    
    @Value("${jira.oauth.client.secret}")
    private String clientSecret;
    
    @Value("${jira.oauth.redirect.uri}")
    private String redirectUri;
    
    // Keycloak configuration (same as FreshdeskService)
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
    
    public JiraOAuthService(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            UserService userService,
            KeycloakService keycloakService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.userService = userService;
        this.keycloakService = keycloakService;
    }
    
    /**
     * Exchange authorization code for access token
     * 
     * @param code Authorization code from Jira
     * @return Access token and user info
     */
    public Map<String, Object> exchangeCodeForToken(String code) {
        log.info("Exchanging authorization code for access token");
        
        String tokenUrl = jiraApiUrl + "/plugins/servlet/oauth/access-token";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(clientId, clientSecret);
        
        String body = String.format(
            "grant_type=authorization_code&code=%s&redirect_uri=%s",
            code, redirectUri
        );
        
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                tokenUrl, request, String.class
            );
            
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new SecurityException("Failed to exchange code for token: " + response.getStatusCode());
            }
            
            JsonNode tokenResponse = objectMapper.readTree(response.getBody());
            String accessToken = tokenResponse.get("access_token").asText();
            
            // Get user info using access token
            Map<String, Object> userInfo = getUserInfo(accessToken);
            
            Map<String, Object> result = new HashMap<>();
            result.put("accessToken", accessToken);
            result.put("userInfo", userInfo);
            
            return result;
        } catch (Exception e) {
            log.error("Failed to exchange code for token", e);
            throw new SecurityException("OAuth token exchange failed", e);
        }
    }
    
    /**
     * Get user information from Jira using access token
     * Returns: Account ID, Email, Display Name
     */
    public Map<String, Object> getUserInfo(String accessToken) {
        log.info("Getting user info from Jira");
        
        String userInfoUrl = jiraApiUrl + "/rest/api/3/myself";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                userInfoUrl, HttpMethod.GET, request, String.class
            );
            
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new SecurityException("Failed to get user info: " + response.getStatusCode());
            }
            
            JsonNode userInfo = objectMapper.readTree(response.getBody());
            
            Map<String, Object> result = new HashMap<>();
            result.put("accountId", userInfo.get("accountId").asText());
            result.put("emailAddress", userInfo.has("emailAddress") ? userInfo.get("emailAddress").asText() : null);
            result.put("displayName", userInfo.has("displayName") ? userInfo.get("displayName").asText() : null);
            
            return result;
        } catch (Exception e) {
            log.error("Failed to get user info from Jira", e);
            throw new SecurityException("Failed to get user info", e);
        }
    }
    
    /**
     * Authenticate Jira user and generate DAM Keycloak token
     * Follows Freshdesk pattern:
     * 1. Extract Account ID and Email from Jira OAuth token
     * 2. Find DAM User by email (from existing users table)
     * 3. Generate Keycloak token using impersonation
     * 
     * Security: Never trusts free text fields - Account ID and Email come from Jira API
     * 
     * @param jiraAccessToken OAuth access token from Jira
     * @return Map with DAM Keycloak token and user info
     */
    public Map<String, Object> authenticateJiraUser(String jiraAccessToken) {
        log.info("Authenticating Jira user with OAuth token");
        
        // Step 1: Get user info from Jira using access token (secure - from Jira API)
        Map<String, Object> userInfo = getUserInfo(jiraAccessToken);
        String jiraAccountId = (String) userInfo.get("accountId");
        String email = (String) userInfo.get("emailAddress");
        
        if (!StringUtils.hasText(jiraAccountId)) {
            throw new SecurityException("Jira Account ID not found in user info");
        }
        
        if (!StringUtils.hasText(email)) {
            throw new SecurityException("Email not found in Jira user info");
        }
        
        log.info("Extracted from Jira token - Account ID: {}, Email: {}", jiraAccountId, email);
        
        // Step 2: Find DAM user by email (from existing users table - no mapping table needed)
        User damUser = userService.findByEmail(email);
        if (damUser == null) {
            throw new SecurityException(
                "DAM user not found for email: " + email + ". Please register in DAM system first."
            );
        }
        
        // Step 3: Generate Keycloak token using impersonation (same as Freshdesk)
        String damToken = generateDamToken(damUser);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("token", damToken);
        response.put("user", createUserResponse(damUser));
        response.put("jiraAccountId", jiraAccountId); // Return Account ID for reference
        response.put("message", "Authentication successful");
        
        log.info("Successfully authenticated Jira user: {} (Account ID: {})", email, jiraAccountId);
        return response;
    }
    
    /**
     * Generate DAM Keycloak token for user
     * Reuses FreshdeskService pattern: Get Keycloak user ID → Impersonate → Extract token
     */
    private String generateDamToken(User user) {
        try {
            // Get Keycloak user ID using KeycloakService (same as FreshdeskService)
            org.keycloak.admin.client.resource.RealmResource realm = keycloakService.getRealmInstance();
            org.keycloak.admin.client.resource.UsersResource usersResource = realm.users();
            
            java.util.List<org.keycloak.representations.idm.UserRepresentation> users = 
                usersResource.searchByEmail(user.getEmail(), true);
            
            if (users == null || users.isEmpty()) {
                log.error("Keycloak user not found for email: {}", user.getEmail());
                throw new RuntimeException("Keycloak user not found. Please contact administrator.");
            }
            
            String keycloakUserId = users.get(0).getId();
            log.debug("Found Keycloak user: {} (ID: {})", user.getEmail(), keycloakUserId);
            
            // Use impersonation API to generate token (same as FreshdeskService)
            String userToken = generateTokenViaImpersonation(keycloakUserId, user.getEmail());
            
            if (userToken == null || userToken.isEmpty()) {
                log.error("Failed to generate token for user: {}", user.getEmail());
                throw new RuntimeException("Failed to generate authentication token. Please contact administrator.");
            }
            
            log.info("Successfully generated Keycloak token for Jira user: {}", user.getEmail());
            return userToken;
            
        } catch (Exception e) {
            log.error("Error generating Keycloak token for user: {}", user.getEmail(), e);
            throw new RuntimeException("Failed to generate authentication token: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate token using Keycloak impersonation API (same logic as FreshdeskService)
     */
    private String generateTokenViaImpersonation(String keycloakUserId, String userEmail) {
        String adminToken = getAdminTokenForImpersonation();
        
        try {
            String token = attemptImpersonation(keycloakUserId, userEmail, adminToken);
            if (token != null && !token.isEmpty()) {
                log.info("Successfully obtained token via impersonation for user: {}", userEmail);
                return token;
            }
            
            log.warn("Impersonation returned null. Trying token exchange as fallback.");
            return generateTokenViaTokenExchange(keycloakUserId, userEmail, adminToken);
            
        } catch (Exception e) {
            log.warn("Impersonation API failed. Trying token exchange as fallback.", e);
            return attemptTokenExchangeFallback(keycloakUserId, userEmail);
        }
    }
    
    /**
     * Get admin token for impersonation
     */
    private String getAdminTokenForImpersonation() {
        String adminToken = getAdminToken();
        if (adminToken == null || adminToken.isEmpty()) {
            log.error("Failed to get admin token for impersonation");
            throw new RuntimeException("Failed to get admin token for impersonation");
        }
        return adminToken;
    }
    
    /**
     * Get admin token from Keycloak (same as FreshdeskService)
     */
    private String getAdminToken() {
        String tokenEndpoint = keycloakAuthServerUrl + keycloakRealmsPath + keycloakRealm + 
            "/protocol/openid-connect/token";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        String body = String.format(
            "grant_type=client_credentials&client_id=%s&client_secret=%s",
            keycloakClientId, keycloakClientSecret
        );
        
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                tokenEndpoint, request, Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return (String) response.getBody().get("access_token");
            }
            
            return null;
        } catch (Exception e) {
            log.error("Failed to get admin token", e);
            return null;
        }
    }
    
    /**
     * Attempt impersonation (same logic as FreshdeskService)
     */
    private String attemptImpersonation(String keycloakUserId, String userEmail, String adminToken) {
        String impersonationEndpoint = keycloakAuthServerUrl + "/admin/realms/" + keycloakRealm
            + "/users/" + keycloakUserId + "/impersonation";
        
        RestTemplate restTemplate = createRestTemplateForImpersonation();
        HttpEntity<String> request = createImpersonationRequest(adminToken);
        
        log.debug("Attempting impersonation for user: {} at endpoint: {}", userEmail, impersonationEndpoint);
        
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                impersonationEndpoint, request, String.class
            );
            
            int statusCode = response.getStatusCode().value();
            log.debug("Impersonation API response status: {}", statusCode);
            
            if (statusCode == 200) {
                return handleSuccessfulImpersonationResponse(response, userEmail, adminToken);
            } else if (statusCode == 403) {
                log.error("Impersonation 403 Forbidden - check impersonation role assignment");
                return null;
            } else {
                log.warn("Impersonation API returned unexpected status: {} for user: {}", statusCode, userEmail);
                return null;
            }
        } catch (org.springframework.web.client.HttpClientErrorException.Forbidden e) {
            log.error("Impersonation 403 Forbidden (Exception)");
            return null;
        } catch (Exception e) {
            log.error("HTTP error during impersonation for user: {}", userEmail, e);
            return null;
        }
    }
    
    /**
     * Create RestTemplate configured for impersonation (manual redirect handling)
     */
    private RestTemplate createRestTemplateForImpersonation() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod)
                    throws java.io.IOException {
                super.prepareConnection(connection, httpMethod);
                connection.setInstanceFollowRedirects(false);
            }
        });
        return restTemplate;
    }
    
    /**
     * Create HTTP request entity for impersonation
     */
    private HttpEntity<String> createImpersonationRequest(String adminToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);
        
        String redirectUri = keycloakAuthServerUrl + keycloakRealmsPath + keycloakRealm + 
            "/protocol/openid-connect/auth";
        
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
    
    /**
     * Handle successful impersonation response
     */
    private String handleSuccessfulImpersonationResponse(ResponseEntity<String> response, String userEmail, String adminToken) {
        String responseBody = response.getBody();
        if (responseBody == null || responseBody.trim().isEmpty()) {
            log.warn("Impersonation returned 200 OK but response body is null for user: {}", userEmail);
            return null;
        }
        
        // Check if response is a redirect URL
        String trimmedBody = responseBody.trim();
        if (trimmedBody.startsWith("http://") || trimmedBody.startsWith("https://")) {
            String token = extractTokenFromUrl(trimmedBody);
            if (token != null && !token.isEmpty()) {
                log.info("Successfully obtained token via impersonation (from redirect URL) for user: {}", userEmail);
                return token;
            }
        }
        
        // Try parsing as JSON
        try {
            JsonNode json = objectMapper.readTree(responseBody);
            if (json.has("redirect_uri")) {
                String redirectUrl = json.get("redirect_uri").asText();
                return followImpersonationRedirect(redirectUrl, userEmail, adminToken);
            }
        } catch (Exception e) {
            log.debug("Failed to parse impersonation response as JSON", e);
        }
        
        return null;
    }
    
    /**
     * Extract token from URL (same as FreshdeskService)
     */
    private String extractTokenFromUrl(String url) {
        try {
            if (url == null || url.isEmpty()) {
                return null;
            }
            
            int tokenStart = url.indexOf("access_token=");
            if (tokenStart == -1) {
                return null;
            }
            
            tokenStart += "access_token=".length();
            int tokenEnd = url.indexOf("&", tokenStart);
            if (tokenEnd == -1) {
                tokenEnd = url.length();
            }
            
            return url.substring(tokenStart, tokenEnd);
        } catch (Exception e) {
            log.warn("Error extracting token from URL: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Follow impersonation redirect URL to get the token
     */
    private String followImpersonationRedirect(String redirectUrl, String userEmail, String adminToken) {
        log.debug("Following impersonation redirect URL: {}", redirectUrl);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(adminToken);
            HttpEntity<String> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> redirectResponse = restTemplate.exchange(
                redirectUrl, HttpMethod.GET, request, String.class
            );
            
            if (redirectResponse.getStatusCode() == HttpStatus.OK && redirectResponse.getBody() != null) {
                String token = extractTokenFromUrl(redirectResponse.getBody());
                if (token != null && !token.isEmpty()) {
                    log.info("Successfully obtained token via impersonation redirect for user: {}", userEmail);
                    return token;
                }
            }
            
            return null;
        } catch (Exception e) {
            log.warn("Failed to follow impersonation redirect URL: {}", redirectUrl, e);
            return null;
        }
    }
    
    /**
     * Attempt token exchange as fallback
     */
    private String attemptTokenExchangeFallback(String keycloakUserId, String userEmail) {
        try {
            String adminToken = getAdminToken();
            if (adminToken == null || adminToken.isEmpty()) {
                throw new RuntimeException("Failed to get admin token for token exchange");
            }
            
            String token = generateTokenViaTokenExchange(keycloakUserId, userEmail, adminToken);
            if (token != null && !token.isEmpty()) {
                return token;
            }
            
            throw new RuntimeException("Both impersonation and token exchange failed");
        } catch (Exception e) {
            log.error("Token exchange fallback failed", e);
            throw new RuntimeException("Failed to generate token via impersonation or token exchange", e);
        }
    }
    
    /**
     * Generate token via token exchange (fallback method)
     */
    private String generateTokenViaTokenExchange(String keycloakUserId, String userEmail, String serviceAccountToken) {
        try {
            String tokenExchangeEndpoint = keycloakAuthServerUrl + keycloakRealmsPath + keycloakRealm
                + "/protocol/openid-connect/token";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            String body = String.format(
                "grant_type=urn:ietf:params:oauth:grant-type:token-exchange&client_id=%s&client_secret=%s&subject_token=%s&subject_token_type=urn:ietf:params:oauth:token-type:access_token&requested_subject=%s&audience=%s",
                keycloakClientId, keycloakClientSecret, serviceAccountToken, keycloakUserId, keycloakClientId
            );
            
            HttpEntity<String> request = new HttpEntity<>(body, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                tokenExchangeEndpoint, request, Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String accessToken = (String) response.getBody().get("access_token");
                if (accessToken != null && !accessToken.isEmpty()) {
                    log.debug("Successfully obtained token via token exchange for user: {}", userEmail);
                    return accessToken;
                }
            }
            
            return null;
        } catch (Exception e) {
            log.debug("Token exchange not available: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Create user response DTO
     */
    private Map<String, Object> createUserResponse(User user) {
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put("id", user.getId());
        userResponse.put("email", user.getEmail());
        userResponse.put("firstName", user.getFirstName());
        userResponse.put("lastName", user.getLastName());
        userResponse.put("roles", user.getRoles().stream()
            .map(r -> r.getName())
            .toList());
        return userResponse;
    }
    
    /**
     * Verify OAuth access token is valid
     */
    public boolean verifyAccessToken(String accessToken) {
        try {
            getUserInfo(accessToken);
            return true;
        } catch (Exception e) {
            log.debug("Access token verification failed", e);
            return false;
        }
    }
}
