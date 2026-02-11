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
                return handleRedirectResponse(response, restTemplate, userEmail, adminToken);
            }

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("Received successful response (200 OK), extracting token...");
                String responseBody = response.getBody();

                if (responseBody == null) {
                    log.warn("Impersonation returned 200 OK but response body is null for user: {}", userEmail);
                    return null;
                }

                log.debug("Response body preview (first 300 chars): {}",
                        responseBody.length() > 300 ? responseBody.substring(0, 300) + "..." : responseBody);

                // Keycloak 26+ returns JSON with redirect field:
                // {"redirect":"http://...","sameRealm":false}
                // The redirect URL points to the account page. In Keycloak 26+, impersonation
                // is browser-based.
                // For programmatic access, we need Legacy Token Exchange (V1) which requires
                // enabling preview features.
                // Since that's not available, we'll use a workaround: use the admin client to
                // generate a token
                // directly using the user's credentials (if available) or use resource owner
                // password grant.
                String redirectUrl = extractRedirectUrlFromJson(responseBody);
                if (redirectUrl != null) {
                    log.debug("Found redirect URL in JSON response: {}", redirectUrl);
                    log.warn("Keycloak 26.1.0 impersonation returns browser redirect. Attempting workaround...");

                    // Workaround: Use admin client to generate token directly for the user
                    // This requires the user's password, but we can use a temporary password
                    // approach
                    // OR use the account console API if available
                    // For now, log the limitation and suggest enabling Legacy Token Exchange
                    log.error("=== KEYCLOAK IMPERSONATION LIMITATION ===");
                    log.error("Keycloak 26.1.0 impersonation endpoint returns a browser redirect URL: {}", redirectUrl);
                    log.error("This redirect is designed for browser-based flows, not programmatic API access.");
                    log.error("");
                    log.error("RECOMMENDED SOLUTION: Enable Legacy Token Exchange (V1) in Keycloak:");
                    log.error("1. Start Keycloak with preview features enabled:");
                    log.error("   Add to Keycloak startup: --features=preview,token-exchange");
                    log.error("   OR set environment variable: KC_FEATURES=preview,token-exchange");
                    log.error("");
                    log.error("2. Enable Legacy Token Exchange (V1) in realm settings:");
                    log.error("   Keycloak Admin Console → Realm Settings → Token Exchange");
                    log.error("   Enable 'Legacy Token Exchange (V1)'");
                    log.error("");
                    log.error(
                            "3. After enabling, token exchange will work and impersonation will be supported programmatically.");
                    log.error("==========================================");

                    // Try to use resource owner password credentials grant as a workaround
                    // This requires knowing the user's password, which we don't have
                    // So we'll return null and let the fallback handle it
                    return null; // Will trigger fallback to token exchange (which will also fail, but with
                                 // better error message)
                }

                // Fallback: Try direct URL extraction if response is a plain URL
                String trimmedBody = responseBody.trim();
                if (trimmedBody.startsWith("http://") || trimmedBody.startsWith("https://")) {
                    log.debug("Response body is a direct redirect URL, extracting token...");
                    String token = extractTokenFromUrl(trimmedBody);
                    if (token != null && !token.isEmpty()) {
                        log.info(
                                "Successfully obtained token via impersonation (from redirect URL in 200 response) for user: {}",
                                userEmail);
                        return token;
                    }
                }

                // Try JSON extraction as fallback
                return handleSuccessResponse(response, userEmail);
            }

            // Handle 403 Forbidden specifically
            if (statusCode == 403) {
                String responseBody = response.getBody();
                log.error("=== IMPERSONATION 403 FORBIDDEN ERROR ===");
                log.error("User: {}", userEmail);
                log.error("User ID: {}", keycloakUserId);
                log.error("Response status: {}", statusCode);
                log.error("Response body: {}", responseBody);
                log.error("Endpoint: {}", impersonationEndpoint);
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
                return null; // Will trigger fallback to token exchange
            }

            log.warn("Impersonation API returned unexpected status: {} for user: {}", statusCode, userEmail);
            return null;
        } catch (org.springframework.web.client.HttpClientErrorException.Forbidden e) {
            String errorBody = e.getResponseBodyAsString();
            log.error("=== IMPERSONATION 403 FORBIDDEN ERROR (Exception) ===");
            log.error("User: {}", userEmail);
            log.error("User ID: {}", keycloakUserId);
            log.error("Error response: {}", errorBody);
            log.error("Endpoint: {}", impersonationEndpoint);
            log.error("Exception message: {}", e.getMessage());
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
            return null; // Will trigger fallback to token exchange
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
        String redirectUri = keycloakAuthServerUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect/auth";

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
    private String handleRedirectResponse(ResponseEntity<String> response, RestTemplate restTemplate, String userEmail,
            String adminToken) {
        String redirectUrl = response.getHeaders().getFirst("Location");
        if (redirectUrl == null) {
            log.warn("Redirect response but no Location header found for user: {}", userEmail);
            // Sometimes the redirect URL is in the response body instead
            String responseBody = response.getBody();
            if (responseBody != null && (responseBody.startsWith("http://") || responseBody.startsWith("https://"))) {
                redirectUrl = responseBody.trim();
                log.debug("Found redirect URL in response body: {}", redirectUrl);
            } else {
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

        // Use followImpersonationRedirect which handles admin token authentication
        return followImpersonationRedirect(redirectUrl, restTemplate, userEmail, adminToken);
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
    private String followImpersonationRedirect(String redirectUrl, RestTemplate restTemplate, String userEmail,
            String adminToken) {
        if (redirectUrl == null || !redirectUrl.startsWith("http")) {
            log.warn("Invalid redirect URL: {}", redirectUrl);
            return null;
        }

        log.debug("Following impersonation redirect URL: {} (with admin token)", redirectUrl);

        try {
            // Create RestTemplate that doesn't automatically follow redirects
            // so we can capture the Location header
            RestTemplate noRedirectTemplate = new RestTemplate();
            noRedirectTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {
                @Override
                protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod)
                        throws java.io.IOException {
                    super.prepareConnection(connection, httpMethod);
                    connection.setInstanceFollowRedirects(false); // Don't follow redirects automatically
                }
            });

            // Add admin token to request headers for authentication
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(adminToken);
            HttpEntity<String> request = new HttpEntity<>(headers);

            // Make request to the redirect URL with admin token
            ResponseEntity<String> redirectResponse = noRedirectTemplate.exchange(
                    redirectUrl,
                    HttpMethod.GET,
                    request,
                    String.class);

            // Check for redirect status and Location header
            if (redirectResponse.getStatusCode().is3xxRedirection()) {
                String location = redirectResponse.getHeaders().getFirst("Location");
                if (location != null) {
                    log.debug("Found redirect Location header: {}",
                            location.length() > 200 ? location.substring(0, 200) + "..." : location);
                    String token = extractTokenFromUrl(location);
                    if (token != null && !token.isEmpty()) {
                        log.info("Successfully extracted token from Location header for user: {}", userEmail);
                        return token;
                    }
                    // If Location doesn't have token, follow it recursively with admin token
                    return followImpersonationRedirect(location, noRedirectTemplate, userEmail, adminToken);
                }
            }

            // If 200 OK, check response body for token URL or JavaScript redirects
            if (redirectResponse.getStatusCode().is2xxSuccessful()) {
                String responseBody = redirectResponse.getBody();
                if (responseBody != null) {
                    log.debug("Response body from redirect (first 500 chars): {}",
                            responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);

                    // Look for token in response body (might be in a script tag or meta refresh)
                    if (responseBody.contains("access_token=")) {
                        String token = extractTokenFromUrl(responseBody);
                        if (token != null && !token.isEmpty()) {
                            log.info("Successfully extracted token from response body for user: {}", userEmail);
                            return token;
                        }
                    }

                    // Try JSON extraction
                    String token = extractTokenFromResponse(responseBody);
                    if (token != null && !token.isEmpty()) {
                        return token;
                    }
                }
            }

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // Check redirect location in error response headers
            if (e.getResponseHeaders() != null) {
                String location = e.getResponseHeaders().getFirst("Location");
                if (location != null) {
                    log.debug("Found Location in error response: {}", location);
                    String token = extractTokenFromUrl(location);
                    if (token != null && !token.isEmpty()) {
                        return token;
                    }
                }
            }
            log.debug("HTTP error following redirect: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("Failed to follow impersonation redirect URL: {}", redirectUrl, e);
        }

        return null;
    }

    /**
     * Get token from authorization endpoint using impersonation session
     * This method uses the authorization endpoint with the redirect URL from
     * impersonation
     * to extract the token from the redirect response
     */
    private String getTokenFromAuthorizationEndpoint(String authUrl, String impersonationRedirectUrl,
            RestTemplate restTemplate, String userEmail, String adminToken) {
        try {
            // Create RestTemplate that doesn't automatically follow redirects
            RestTemplate noRedirectTemplate = new RestTemplate();
            noRedirectTemplate.setRequestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {
                @Override
                protected void prepareConnection(java.net.HttpURLConnection connection, String httpMethod)
                        throws java.io.IOException {
                    super.prepareConnection(connection, httpMethod);
                    connection.setInstanceFollowRedirects(false);
                }
            });

            // Add admin token and impersonation session cookie if available
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(adminToken);
            // Try to extract session from redirect URL if it contains one
            if (impersonationRedirectUrl.contains("?")) {
                String query = impersonationRedirectUrl.substring(impersonationRedirectUrl.indexOf("?") + 1);
                // Look for session-related parameters
                if (query.contains("session_state") || query.contains("code")) {
                    log.debug("Found session parameters in redirect URL");
                }
            }

            HttpEntity<String> request = new HttpEntity<>(headers);

            // Make request to authorization endpoint
            ResponseEntity<String> response = noRedirectTemplate.exchange(
                    authUrl,
                    HttpMethod.GET,
                    request,
                    String.class);

            // Check for redirect with token in Location header
            if (response.getStatusCode().is3xxRedirection()) {
                String location = response.getHeaders().getFirst("Location");
                if (location != null) {
                    log.debug("Found redirect Location: {}",
                            location.length() > 200 ? location.substring(0, 200) + "..." : location);
                    String token = extractTokenFromUrl(location);
                    if (token != null && !token.isEmpty()) {
                        log.info("Successfully extracted token from authorization redirect for user: {}", userEmail);
                        return token;
                    }
                }
            }

            // If 200 OK, check response body
            if (response.getStatusCode().is2xxSuccessful()) {
                String responseBody = response.getBody();
                if (responseBody != null) {
                    String token = extractTokenFromUrl(responseBody);
                    if (token != null && !token.isEmpty()) {
                        return token;
                    }
                }
            }

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // Check for redirect in error response
            if (e.getResponseHeaders() != null) {
                String location = e.getResponseHeaders().getFirst("Location");
                if (location != null) {
                    String token = extractTokenFromUrl(location);
                    if (token != null && !token.isEmpty()) {
                        return token;
                    }
                }
            }
            log.debug("HTTP error getting token from authorization endpoint: {}", e.getStatusCode());
        } catch (Exception e) {
            log.warn("Failed to get token from authorization endpoint", e);
        }

        return null;
    }

    /**
     * Follow redirect URL to extract token from response body (legacy method)
     */
    private String followRedirectUrl(String redirectUrl, RestTemplate restTemplate, String userEmail) {
        if (!redirectUrl.startsWith("http")) {
            return null;
        }

        try {
            ResponseEntity<String> redirectResponse = restTemplate.getForEntity(redirectUrl, String.class);
            String redirectBody = redirectResponse.getBody();
            if (redirectBody == null) {
                return null;
            }

            String token = extractTokenFromResponse(redirectBody);
            if (token != null && !token.isEmpty()) {
                log.debug("Successfully obtained token via impersonation redirect for user: {}", userEmail);
                return token;
            }
        } catch (Exception e) {
            log.debug("Failed to follow redirect URL: {}", redirectUrl, e);
        }

        return null;
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
        if (responseBody.startsWith("http://") || responseBody.startsWith("https://")) {
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
            log.error("All token generation methods failed for user: {}. " +
                    "Impersonation returned 403 (verify role assignment) and token exchange is not supported in Keycloak 26.1.0.",
                    userEmail);
            log.error("TROUBLESHOOTING: Verify impersonation role assignment:");
            log.error("1. Keycloak Admin Console → Clients → backend-client");
            log.error("2. Service Account Roles tab → Select 'realm-management' client");
            log.error("3. Ensure 'impersonation' role is in 'Assigned Roles' (not just Available)");
            log.error("4. If role is assigned, try restarting Keycloak or regenerating service account token");
            throw new JwtTokenException(IMPERSONATION_CONFIGURATION_ERROR_MESSAGE);
        } catch (JwtTokenException e) {
            throw e;
        } catch (Exception ex) {
            log.error("All token generation methods failed for user: {}", userEmail, ex);
            throw new JwtTokenException(IMPERSONATION_CONFIGURATION_ERROR_MESSAGE);
        }
    }

    /**
     * Fallback: Try token exchange (may not be enabled in all Keycloak versions)
     */
    private String generateTokenViaTokenExchange(String keycloakUserId, String userEmail, String serviceAccountToken) {
        try {
            String tokenExchangeEndpoint = keycloakAuthServerUrl + "/realms/" + keycloakRealm
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

            // URL format: ...?access_token=TOKEN&session_state=...&... or
            // ...#access_token=TOKEN&...
            // Try query parameter first
            int tokenStart = url.indexOf("access_token=");
            if (tokenStart == -1) {
                // Try fragment (hash)
                tokenStart = url.indexOf("#access_token=");
                if (tokenStart != -1) {
                    tokenStart += "#access_token=".length();
                }
            } else {
                tokenStart += "access_token=".length();
            }

            if (tokenStart != -1 && tokenStart < url.length()) {
                // Find the end of the token (either & or end of string)
                int tokenEnd = url.indexOf("&", tokenStart);
                if (tokenEnd == -1) {
                    tokenEnd = url.indexOf("#", tokenStart);
                    if (tokenEnd == -1) {
                        tokenEnd = url.length();
                    }
                }

                String token = url.substring(tokenStart, tokenEnd);
                if (token != null && !token.isEmpty()) {
                    log.debug("Successfully extracted token from URL (length: {})", token.length());
                    return token;
                }
            }

            log.warn("Could not find access_token in URL");
            return null;
        } catch (Exception e) {
            log.warn("Error extracting token from URL: {}", e.getMessage(), e);
            return null;
        }
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

        String tokenEndpoint = keycloakAuthServerUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect/token";

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
