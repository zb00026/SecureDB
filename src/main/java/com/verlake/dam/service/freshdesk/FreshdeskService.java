package com.verlake.dam.service.freshdesk;

import com.verlake.dam.configuration.ConditionalOnAuthProviderParam;
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
import com.verlake.dam.service.auth.KeycloakSessionTokenService;
import com.verlake.dam.service.auth.TokenService;
import com.verlake.dam.service.auth.TokenServiceManager;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
@ConditionalOnAuthProviderParam(field = "auth.provider", containProvider = "keycloak")
public class FreshdeskService {

    private final UserService userService;
    private final AuthService authService;
    private final TokenServiceManager tokenServiceManager;
    private final KeycloakSessionTokenService keycloakSessionTokenService;
    private final AssetService assetService;
    private final AccessRequestRepository accessRequestRepository;
    private final DatabaseSchemaService databaseSchemaService;
    private final QueryExecutionService queryExecutionService;
    private final NaturalLanguageToSqlService naturalLanguageToSqlService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${freshdesk.app.secret.key:}")
    private String freshdeskAppSecretKey;

    public FreshdeskService(
            UserService userService,
            AuthService authService,
            TokenServiceManager tokenServiceManager,
            KeycloakSessionTokenService keycloakSessionTokenService,
            AssetService assetService,
            AccessRequestRepository accessRequestRepository,
            DatabaseSchemaService databaseSchemaService,
            QueryExecutionService queryExecutionService,
            NaturalLanguageToSqlService naturalLanguageToSqlService) {
        this.userService = userService;
        this.authService = authService;
        this.tokenServiceManager = tokenServiceManager;
        this.keycloakSessionTokenService = keycloakSessionTokenService;
        this.assetService = assetService;
        this.accessRequestRepository = accessRequestRepository;
        this.databaseSchemaService = databaseSchemaService;
        this.queryExecutionService = queryExecutionService;
        this.naturalLanguageToSqlService = naturalLanguageToSqlService;
    }

    /**
     * Verify Freshdesk app secret key from frontend requests
     * This secret key is configured in Freshdesk app settings when installing the app
     * Frontend sends this key in X-Freshdesk-App-Secret-Key header
     * 
     * @param secretKey Secret key from request header
     * @throws SecurityException if secret key is invalid or missing
     */
    public void verifyAppSecretKey(String secretKey) {
        if (!org.springframework.util.StringUtils.hasText(freshdeskAppSecretKey)) {
            log.warn("Freshdesk app secret key not configured. Skipping secret key verification.");
            return; // Allow in development, require in production
        }
        
        if (!org.springframework.util.StringUtils.hasText(secretKey)) {
            throw new SecurityException("Missing Freshdesk app secret key in request header");
        }
        
        // Use constant-time comparison to prevent timing attacks
        if (!MessageDigest.isEqual(
                secretKey.getBytes(StandardCharsets.UTF_8),
                freshdeskAppSecretKey.getBytes(StandardCharsets.UTF_8))) {
            log.error("Invalid Freshdesk app secret key provided");
            throw new SecurityException("Invalid Freshdesk app secret key");
        }
        
        log.debug("Freshdesk app secret key verified successfully");
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

        // Generate Hagrids authentication token via shared Keycloak session token service
        String hagridsToken = keycloakSessionTokenService.generateTokenForUser(user);

        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESPONSE_SUCCESS, true);
        response.put("token", hagridsToken);
        response.put("user", createUserResponse(user));
        response.put(Constants.RESPONSE_MESSAGE, "Authentication successful");

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
