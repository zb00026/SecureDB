package com.verlake.dam.service.freshdesk;

import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.dto.AccessQueryDTO;
import com.verlake.dam.entity.assets.dto.AssetDTO;
import com.verlake.dam.entity.assets.dto.DatabaseSchemaDTO;
import com.verlake.dam.entity.assets.dto.NaturalLanguageQueryDTO;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.user.dto.UserDTO;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.enums.AssetType;
import com.verlake.dam.enums.AuthProvider;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.exception.*;
import com.verlake.dam.repository.assets.AccessRequestRepository;
import com.verlake.dam.service.assets.AssetService;
import com.verlake.dam.service.assets.DatabaseSchemaService;
import com.verlake.dam.service.assets.NaturalLanguageToSqlService;
import com.verlake.dam.service.assets.QueryExecutionService;
import com.verlake.dam.service.auth.AuthService;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.auth.TokenService;
import com.verlake.dam.service.auth.TokenServiceManager;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.CommonUtils;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for Freshdesk integration
 * Handles authentication and API calls from Freshdesk sidebar app
 */
@Service
@Slf4j
public class FreshdeskService {

    private final UserService userService;
    private final AuthService authService;
    private final TokenServiceManager tokenServiceManager;
    private final KeycloakService keycloakService;
    private final AssetService assetService;
    private final AccessRequestRepository accessRequestRepository;
    private final DatabaseSchemaService databaseSchemaService;
    private final QueryExecutionService queryExecutionService;
    private final NaturalLanguageToSqlService naturalLanguageToSqlService;
    private final SecureRandom secureRandom = new SecureRandom();

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

            After completing these steps, the Freshdesk integration will work without changing user passwords.

            Note: Without Legacy Token Exchange (V1), Keycloak 26.1.0's impersonation only works in browser flows.""";

    public FreshdeskService(
            UserService userService,
            AuthService authService,
            TokenServiceManager tokenServiceManager,
            KeycloakService keycloakService,
            AssetService assetService,
            AccessRequestRepository accessRequestRepository,
            DatabaseSchemaService databaseSchemaService,
            QueryExecutionService queryExecutionService,
            NaturalLanguageToSqlService naturalLanguageToSqlService) {
        this.userService = userService;
        this.authService = authService;
        this.tokenServiceManager = tokenServiceManager;
        this.keycloakService = keycloakService;
        this.assetService = assetService;
        this.accessRequestRepository = accessRequestRepository;
        this.databaseSchemaService = databaseSchemaService;
        this.queryExecutionService = queryExecutionService;
        this.naturalLanguageToSqlService = naturalLanguageToSqlService;
    }

    /**
     * Authenticate Freshdesk user as Hagrids accessor
     * 
     * @param email          Freshdesk user email
     * @param freshdeskToken Optional Freshdesk token for validation
     * @return Authentication response with Hagrids token
     */
    public Map<String, Object> authenticateFreshdeskUser(String email, String freshdeskToken) {
        log.info("Authenticating Freshdesk user: {}", email);

        // Find or create user in Hagrids
        User user = userService.findByEmail(email);

        if (user == null) {
            throw new UserNotFoundException(
                    "User not found in Hagrids. Please contact administrator to create an account.");
        }

        // Verify user has ACCESSOR role
        if (!userService.hasRole(user, Roles.ACCESSOR.getOriginalName())) {
            throw new AccessDeniedException("User does not have ACCESSOR role. Access denied.");
        }

        // Verify user is active
        if (user.getIsActive() == null || !user.getIsActive()) {
            throw new AuthenticationException("User account is not active. Please contact administrator.");
        }

        // Generate Hagrids authentication token
        // Use Keycloak token service to generate a token for the user
        String hagridsToken = generateHagridsToken(user);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("token", hagridsToken);
        response.put("user", createUserResponse(user));
        response.put("message", "Authentication successful");

        log.info("Successfully authenticated Freshdesk user: {}", email);
        return response;
    }

    /**
     * Get assets available to the accessor
     * Returns only assets where the accessor has access requests approved by asset
     * owner
     */
    public List<AssetDTO> getAssetsForAccessor(String token) {
        log.debug("Getting assets for accessor");

        User user = authenticateToken(token);
        verifyAccessorRole(user);

        // Get all access requests for the accessor that are approved by asset owner
        List<AccessRequest> approvedRequests = accessRequestRepository.findByRequestor(user)
                .stream()
                .filter(req -> req.getAssetApproverStatus() == ApprovalStatus.APPROVED
                        && req.getAsset().getType() == AssetType.DATABASE)
                .toList();

        // Extract unique assets from approved requests
        List<Long> assetIds = approvedRequests.stream()
                .map(req -> req.getAsset().getId())
                .distinct().toList();

        if (assetIds.isEmpty()) {
            return List.of();
        }

        // Get asset DTOs for the approved assets
        return assetIds.stream()
                .map(assetService::findDTOById).toList();
    }

    /**
     * Get access requests for the accessor
     */
    public List<Map<String, Object>> getAccessRequestsForAccessor(String token, Long assetId) {
        log.debug("Getting access requests for accessor, assetId: {}", assetId);

        User user = authenticateToken(token);
        verifyAccessorRole(user);

        List<AccessRequest> requests;
        if (assetId != null) {
            // Find requests for specific asset
            Asset asset = assetService.findById(assetId);
            requests = accessRequestRepository.findByAssetAndRequestor(asset, user);
        } else {
            requests = accessRequestRepository.findByRequestor(user);
        }

        return requests.stream()
                .filter(req -> req.getAssetApproverStatus() == ApprovalStatus.APPROVED)
                .map(this::createAccessRequestResponse)
                .toList();
    }

    /**
     * Get database schema for a specific access request
     */
    public DatabaseSchemaDTO getSchemaForRequest(String token, Long requestId) {
        log.debug("Getting schema for requestId: {}", requestId);

        User user = authenticateToken(token);
        verifyAccessorRole(user);

        // Verify user owns this access request
        AccessRequest request = accessRequestRepository.findById(requestId)
                .orElseThrow(() -> new AccessRequestNotFoundException(Constants.ACCESS_REQUEST_NOT_FOUND));

        if (!request.getRequestor().getId().equals(user.getId())) {
            throw new AccessDeniedException("Access denied: You don't have permission to access this request");
        }

        try {
            return databaseSchemaService.getSchemaForCurrentUser(null, requestId, false);
        } catch (CommonUtils.CryptoException e) {
            log.error("Failed to get schema for requestId: {}", requestId, e);
            throw new QueryExecutionException("Failed to fetch schema: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to get schema for requestId: {}", requestId, e);
            throw new QueryExecutionException("Failed to fetch schema: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a database query
     */
    public Map<String, Object> executeQuery(String token, AccessQueryDTO queryDto) {
        log.info("Executing query for accessor, assetId: {}, requestId: {}",
                queryDto.getAssetId(), queryDto.getRequestId());

        User user = authenticateToken(token);
        verifyAccessorRole(user);

        // Verify user owns this access request
        if (queryDto.getRequestId() != null) {
            AccessRequest request = accessRequestRepository.findById(queryDto.getRequestId())
                    .orElseThrow(() -> new AccessRequestNotFoundException(Constants.ACCESS_REQUEST_NOT_FOUND));

            if (!request.getRequestor().getId().equals(user.getId())) {
                throw new AccessDeniedException("Access denied: You don't have permission to access this request");
            }
        }

        try {
            return queryExecutionService.executeQueryForAccessor(queryDto);
        } catch (Exception e) {
            log.error("Failed to execute query", e);
            throw new QueryExecutionException("Failed to execute query: " + e.getMessage(), e);
        }
    }

    /**
     * Convert natural language to SQL
     */
    public Map<String, Object> convertNaturalLanguageToSql(String token, Map<String, Object> request) {
        log.info("Converting natural language to SQL");

        User user = authenticateToken(token);
        verifyAccessorRole(user);

        // Extract request parameters
        Long requestId = request.get("requestId") != null ? Long.valueOf(request.get("requestId").toString()) : null;
        String naturalLanguageQuery = (String) request.get("naturalLanguageQuery");

        if (naturalLanguageQuery == null || naturalLanguageQuery.trim().isEmpty()) {
            throw new InvalidRequestException("naturalLanguageQuery is required");
        }

        if (requestId == null) {
            throw new InvalidRequestException("requestId is required for accessor queries");
        }

        // Verify user owns this access request
        AccessRequest accessRequest = accessRequestRepository.findById(requestId)
                .orElseThrow(() -> new AccessRequestNotFoundException(Constants.ACCESS_REQUEST_NOT_FOUND));

        if (!accessRequest.getRequestor().getId().equals(user.getId())) {
            throw new AccessDeniedException("Access denied: You don't have permission to access this request");
        }

        // Create NaturalLanguageQueryDTO
        NaturalLanguageQueryDTO queryDto = new NaturalLanguageQueryDTO();
        queryDto.setRequestId(requestId);
        queryDto.setNaturalLanguageQuery(naturalLanguageQuery);

        try {
            return naturalLanguageToSqlService.convertNaturalLanguageToSqlForAccessor(queryDto);
        } catch (Exception e) {
            log.error("Failed to convert natural language to SQL", e);
            throw new QueryExecutionException("Failed to convert query: " + e.getMessage(), e);
        }
    }

    /**
     * Generate Hagrids authentication token for user
     * For Freshdesk integration, since users are already authenticated via
     * Freshdesk,
     * we use Keycloak's token exchange API to generate a token without changing the
     * user's password.
     * This preserves the user's original password so they can still log in
     * directly.
     */
    private String generateHagridsToken(User user) {
        try {
            // Get Keycloak user ID using KeycloakService
            RealmResource realm = keycloakService.getRealmInstance();
            UsersResource usersResource = realm.users();

            // Find user by email - use searchByEmail instead of search (which searches by username)
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

            // Use impersonation API to generate token without changing password
            // This preserves the user's original password
            String userToken = generateTokenViaImpersonation(keycloakUserId, user.getEmail());

            if (userToken == null || userToken.isEmpty()) {
                log.error("Failed to generate token for user: {}", user.getEmail());
                throw new JwtTokenException("Failed to generate authentication token. Please contact administrator.");
            }

            log.info("Successfully generated Keycloak token for Freshdesk user: {}", user.getEmail());
            return userToken;

        } catch (UserNotFoundException | JwtTokenException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error generating Keycloak token for user: {}", user.getEmail(), e);
            throw new JwtTokenException("Failed to generate authentication token: " + e.getMessage(), e);
        }
    }

    /**
     * Generate token using Keycloak impersonation API
     * This approach generates a token for the user without changing their password
     * Uses the admin impersonation endpoint which returns a redirect URL with token
     */
    private String generateTokenViaImpersonation(String keycloakUserId, String userEmail) {
        String adminToken = getAdminTokenForImpersonation();

        try {
            String token = attemptImpersonation(keycloakUserId, userEmail, adminToken);
            if (token != null && !token.isEmpty()) {
                log.info("Successfully obtained token via impersonation for user: {}", userEmail);
                return token;
            }

            log.warn(
                    "Impersonation returned null (likely 403 error - check logs above). Trying token exchange as fallback.");
            return generateTokenViaTokenExchange(keycloakUserId, userEmail, adminToken);

        } catch (Exception e) {
            log.warn("Impersonation API failed with exception for user: {}. Trying token exchange as fallback.",
                    userEmail, e);
            return attemptTokenExchangeFallback(keycloakUserId, userEmail);
        }
    }

    /**
     * Get admin token for impersonation, throwing exception if unavailable
     */
    private String getAdminTokenForImpersonation() {
        String adminToken = getAdminToken();
        if (adminToken == null || adminToken.isEmpty()) {
            log.error("Failed to get admin token for impersonation");
            throw new JwtTokenException("Failed to get admin token for impersonation");
        }
        return adminToken;
    }

    /**
     * Attempt to get token via impersonation endpoint
     */
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
                return handleSuccessfulImpersonationResponse(response, userEmail);
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

    private String handleSuccessfulImpersonationResponse(ResponseEntity<String> response, String userEmail) {
        log.debug("Received successful response (200 OK), extracting token...");
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
            log.warn("Keycloak 26.1.0 impersonation returns browser redirect. Attempting workaround...");
            return null;
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

    /**
     * Build impersonation endpoint URL
     * Note: In Keycloak 26+, the endpoint format is:
     * /admin/realms/{realm}/users/{userId}/impersonation
     */
    private String buildImpersonationEndpoint(String keycloakUserId) {
        String endpoint = keycloakAuthServerUrl + "/admin/realms/" + keycloakRealm
                + "/users/" + keycloakUserId + "/impersonation";
        log.debug("Built impersonation endpoint: {}", endpoint);
        return endpoint;
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
     * In Keycloak 26+, we need to provide a redirect_uri parameter
     */
    private HttpEntity<String> createImpersonationRequest(String adminToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        // Construct redirect URI - use a callback URL that we can intercept
        // The redirect_uri should be a URL where Keycloak will redirect with the token
        String redirectUri = keycloakAuthServerUrl + keycloakRealmsPath + keycloakRealm + "/protocol/openid-connect/auth";

        // Create request body with redirect_uri
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("redirect_uri", redirectUri);

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonBody = objectMapper.writeValueAsString(requestBody);
            return new HttpEntity<>(jsonBody, headers);
        } catch (Exception e) {
            log.warn("Failed to create impersonation request body, using empty body", e);
            return new HttpEntity<>(headers);
        }
    }

    /**
     * Handle redirect response from impersonation endpoint
     */
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

    /**
     * Extract redirect URL from JSON response
     * Keycloak 26+ returns: {"redirect":"http://...","sameRealm":false}
     */
    private String extractRedirectUrlFromJson(String jsonResponse) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);
            JsonNode redirectNode = jsonNode.get("redirect");
            if (redirectNode != null && redirectNode.isTextual()) {
                return redirectNode.asText();
            }
        } catch (Exception e) {
            log.debug("Failed to parse JSON response for redirect URL: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Follow impersonation redirect URL to get the token
     * Keycloak redirects to account page which then redirects to a URL with the
     * token
     * We need to use the admin token to authenticate the redirect request
     */
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

    /**
     * Handle successful response from impersonation endpoint
     */
    private String handleSuccessResponse(ResponseEntity<String> response, String userEmail) {
        String responseBody = response.getBody();
        if (responseBody == null) {
            log.warn("Impersonation returned 200 but response body is null for user: {}", userEmail);
            return null;
        }

        log.debug("Impersonation response body (first 500 chars): {}",
                responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);

        // Check if response is a redirect URL
        if (responseBody.startsWith(Constants.HTTP_PROTOCOL_PREFIX) || responseBody.startsWith(Constants.HTTPS_PROTOCOL_PREFIX)) {
            log.debug("Response appears to be a redirect URL, extracting token...");
            String token = extractTokenFromUrl(responseBody);
            if (token != null && !token.isEmpty()) {
                log.debug("Successfully obtained token from redirect URL for user: {}", userEmail);
                return token;
            }
        }

        // Try to extract token from JSON response
        String token = extractTokenFromResponse(responseBody);
        if (token != null && !token.isEmpty()) {
            log.debug("Successfully obtained token via impersonation for user: {}", userEmail);
            return token;
        }

        log.warn("Failed to extract token from impersonation response for user: {}. Response body: {}",
                userEmail, responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody);
        return null;
    }

    /**
     * Attempt token exchange as fallback when impersonation fails
     */
    private String attemptTokenExchangeFallback(String keycloakUserId, String userEmail) {
        try {
            String adminToken = getAdminToken();
            String token = generateTokenViaTokenExchange(keycloakUserId, userEmail, adminToken);
            if (token != null && !token.isEmpty()) {
                return token;
            }
            // Both impersonation and token exchange failed
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

    /**
     * Fallback: Try token exchange (may not be enabled in all Keycloak versions)
     */
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

            // Check for unsupported grant type error
            if (response.getBody() != null) {
                Map<String, Object> errorResponse = response.getBody();
                String error = (String) errorResponse.get("error");
                if ("unsupported_grant_type".equals(error)) {
                    log.warn("Token exchange is not supported in this Keycloak version. " +
                            "Please assign 'impersonation' role to backend-client service account instead.");
                }
            }

            return null; // Token exchange not available
        } catch (org.springframework.web.client.HttpClientErrorException.BadRequest e) {
            // Handle 400 Bad Request - usually means unsupported grant type
            log.warn("Token exchange not supported in this Keycloak version (unsupported_grant_type). " +
                    "Please configure impersonation role in Keycloak instead.");
            return null;
        } catch (Exception e) {
            log.debug("Token exchange not available: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract access token from redirect URL
     * Keycloak impersonation returns URL format:
     * ...?access_token=TOKEN&session_state=...&...
     */
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
        
        // Try fragment (hash)
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

    /**
     * Extract token from JSON response
     */
    private String extractTokenFromResponse(String responseBody) {
        try {
            // Try to parse as JSON if possible
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

    /**
     * Get admin token using client credentials grant
     */
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

                // Log token info for debugging (first 50 chars only for security)
                if (accessToken != null) {
                    log.debug("Admin token obtained successfully. Token preview: {}...",
                            accessToken.substring(0, Math.min(50, accessToken.length())));
                    // Note: To verify roles in token, you would need to decode the JWT
                    // The roles should be in the 'realm_access' or 'resource_access' claims
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

    /**
     * Authenticate token and return user
     */
    private User authenticateToken(String token) {
        // Validate token and get user
        // This should use your existing token validation logic
        UserDTO userDto = new UserDTO();
        userDto.setToken(token);
        userDto.setAuthProvider(AuthProvider.KEYCLOAK);

        try {
            TokenService tokenService = tokenServiceManager.getService(AuthProvider.KEYCLOAK);
            authService.validateToken(userDto, tokenService);
            User user = authService.authenticateUser(userDto, tokenService);

            if (user == null) {
                throw new AuthenticationException("Authentication failed: User not found");
            }

            return user;
        } catch (AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Token authentication failed", e);
            throw new AuthenticationException("Authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Verify user has ACCESSOR role
     */
    private void verifyAccessorRole(User user) {
        if (!userService.hasRole(user, Roles.ACCESSOR.getOriginalName())) {
            throw new AccessDeniedException("Access denied: ACCESSOR role required");
        }
    }

    /**
     * Create user response DTO
     */
    private Map<String, Object> createUserResponse(User user) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("email", user.getEmail());
        userMap.put("firstName", user.getFirstName());
        userMap.put("lastName", user.getLastName());
        userMap.put("isActive", user.getIsActive());
        return userMap;
    }

    /**
     * Create access request response DTO
     */
    private Map<String, Object> createAccessRequestResponse(AccessRequest request) {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("id", request.getId());
        requestMap.put("assetId", request.getAsset().getId());
        requestMap.put("assetName", request.getAsset().getName());
        requestMap.put("status", request.getAssetApproverStatus().name());
        requestMap.put("expiryDate", request.getExpiryDate());
        requestMap.put("requestedUsername", request.getRequestedUsername());
        return requestMap;
    }
}
