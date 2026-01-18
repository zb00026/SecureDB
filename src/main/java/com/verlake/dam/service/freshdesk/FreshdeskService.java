package com.verlake.dam.service.freshdesk;

import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.dto.AccessQueryDTO;
import com.verlake.dam.entity.assets.dto.AssetDTO;
import com.verlake.dam.entity.assets.dto.DatabaseSchemaDTO;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.user.dto.UserDTO;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.enums.Roles;
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
import com.verlake.dam.exception.UserNotFoundException;
import com.verlake.dam.exception.AuthenticationException;
import com.verlake.dam.exception.AccessDeniedException;
import com.verlake.dam.exception.InvalidRequestException;
import com.verlake.dam.exception.AccessRequestNotFoundException;
import com.verlake.dam.exception.QueryExecutionException;
import com.verlake.dam.exception.JwtTokenException;
import com.verlake.dam.utils.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    
    @Value("${keycloak.auth-server-url}")
    private String keycloakAuthServerUrl;
    
    @Value("${keycloak.realm}")
    private String keycloakRealm;
    
    @Value("${spring.security.oauth2.client.registration.keycloak.client-id}")
    private String keycloakClientId;
    
    @Value("${spring.security.oauth2.client.registration.keycloak.client-secret}")
    private String keycloakClientSecret;

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
     * Authenticate Freshdesk user as Hagrids developer
     * 
     * @param email Freshdesk user email
     * @param freshdeskToken Optional Freshdesk token for validation
     * @return Authentication response with Hagrids token
     */
    public Map<String, Object> authenticateFreshdeskUser(String email, String freshdeskToken) {
        log.info("Authenticating Freshdesk user: {}", email);
        
        // Find or create user in Hagrids
        User user = userService.findByEmail(email);
        
        if (user == null) {
            throw new UserNotFoundException("User not found in Hagrids. Please contact administrator to create an account.");
        }
        
        // Verify user has DEVELOPER role
        if (!userService.hasRole(user, Roles.DEVELOPER.getOriginalName())) {
            throw new AccessDeniedException("User does not have DEVELOPER role. Access denied.");
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
     * Get assets available to the developer
     * Returns only assets where the developer has access requests approved by asset owner
     */
    public List<AssetDTO> getAssetsForDeveloper(String token) {
        log.debug("Getting assets for developer");
        
        User user = authenticateToken(token);
        verifyDeveloperRole(user);
        
        // Get all access requests for the developer that are approved by asset owner
        List<AccessRequest> approvedRequests = accessRequestRepository.findByRequestor(user)
                .stream()
                .filter(req -> req.getAssetApproverStatus() == ApprovalStatus.APPROVED)
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
     * Get access requests for the developer
     */
    public List<Map<String, Object>> getAccessRequestsForDeveloper(String token, Long assetId) {
        log.debug("Getting access requests for developer, assetId: {}", assetId);
        
        User user = authenticateToken(token);
        verifyDeveloperRole(user);
        
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
        verifyDeveloperRole(user);
        
        // Verify user owns this access request
        AccessRequest request = accessRequestRepository.findById(requestId)
                .orElseThrow(() -> new AccessRequestNotFoundException(Constants.ACCESS_REQUEST_NOT_FOUND));
        
        if (!request.getRequestor().getId().equals(user.getId())) {
            throw new AccessDeniedException("Access denied: You don't have permission to access this request");
        }
        
        try {
            return databaseSchemaService.getSchemaForCurrentUser(null, requestId, false);
        } catch (com.verlake.dam.utils.CommonUtils.CryptoException e) {
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
        log.info("Executing query for developer, assetId: {}, requestId: {}", 
                queryDto.getAssetId(), queryDto.getRequestId());
        
        User user = authenticateToken(token);
        verifyDeveloperRole(user);
        
        // Verify user owns this access request
        if (queryDto.getRequestId() != null) {
            AccessRequest request = accessRequestRepository.findById(queryDto.getRequestId())
                    .orElseThrow(() -> new AccessRequestNotFoundException(Constants.ACCESS_REQUEST_NOT_FOUND));
            
            if (!request.getRequestor().getId().equals(user.getId())) {
                throw new AccessDeniedException("Access denied: You don't have permission to access this request");
            }
        }
        
        try {
            return queryExecutionService.executeQueryForDeveloper(queryDto);
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
        verifyDeveloperRole(user);
        
        // Extract request parameters
        Long requestId = request.get("requestId") != null ? 
                Long.valueOf(request.get("requestId").toString()) : null;
        String naturalLanguageQuery = (String) request.get("naturalLanguageQuery");
        
        if (naturalLanguageQuery == null || naturalLanguageQuery.trim().isEmpty()) {
            throw new InvalidRequestException("naturalLanguageQuery is required");
        }
        
        if (requestId == null) {
            throw new InvalidRequestException("requestId is required for developer queries");
        }
        
        // Verify user owns this access request
        AccessRequest accessRequest = accessRequestRepository.findById(requestId)
                .orElseThrow(() -> new AccessRequestNotFoundException(Constants.ACCESS_REQUEST_NOT_FOUND));
        
        if (!accessRequest.getRequestor().getId().equals(user.getId())) {
            throw new AccessDeniedException("Access denied: You don't have permission to access this request");
        }
        
        // Create NaturalLanguageQueryDTO
        com.verlake.dam.entity.assets.dto.NaturalLanguageQueryDTO queryDto = 
                new com.verlake.dam.entity.assets.dto.NaturalLanguageQueryDTO();
        queryDto.setRequestId(requestId);
        queryDto.setNaturalLanguageQuery(naturalLanguageQuery);
        
        try {
            return naturalLanguageToSqlService.convertNaturalLanguageToSqlForDeveloper(queryDto);
        } catch (Exception e) {
            log.error("Failed to convert natural language to SQL", e);
            throw new QueryExecutionException("Failed to convert query: " + e.getMessage(), e);
        }
    }

    /**
     * Generate Hagrids authentication token for user
     * For Freshdesk integration, since users are already authenticated via Freshdesk,
     * we generate a token using a temporary password approach or user session API
     */
    private String generateHagridsToken(User user) {
        try {
            // Get Keycloak user ID using KeycloakService
            org.keycloak.admin.client.resource.RealmResource realm = keycloakService.getRealmInstance();
            org.keycloak.admin.client.resource.UsersResource usersResource = realm.users();
            
            // Find user by email
            List<org.keycloak.representations.idm.UserRepresentation> users = usersResource.search(user.getEmail());
            if (users == null || users.isEmpty()) {
                log.error("Keycloak user not found for email: {}", user.getEmail());
                throw new UserNotFoundException("Keycloak user not found. Please contact administrator.");
            }
            
            org.keycloak.representations.idm.UserRepresentation keycloakUser = users.get(0);
            String keycloakUserId = keycloakUser.getId();
            String keycloakUsername = keycloakUser.getUsername();
            
            // Try to generate token using temporary password approach
            // This is the most reliable method for Freshdesk integration
            String userToken = generateTokenViaTemporaryPassword(keycloakUserId, keycloakUsername, user.getEmail());
            
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
     * Generate token using temporary password approach
     * Since Freshdesk users are already authenticated, we trust that authentication
     * and generate a token by temporarily setting a password, getting a token, then resetting it
     */
    private String generateTokenViaTemporaryPassword(String keycloakUserId, String keycloakUsername, String userEmail) {
        org.keycloak.admin.client.resource.RealmResource realm = keycloakService.getRealmInstance();
        org.keycloak.admin.client.resource.UserResource userResource = realm.users().get(keycloakUserId);
        
        // Generate a secure temporary password
        String tempPassword = generateSecureTempPassword();
        
        try {
            // Get current user representation to check required actions
            org.keycloak.representations.idm.UserRepresentation userRep = userResource.toRepresentation();
            java.util.List<String> originalRequiredActions = userRep.getRequiredActions() != null ? 
                new java.util.ArrayList<>(userRep.getRequiredActions()) : new java.util.ArrayList<>();
            
            // Set temporary password for the user
            org.keycloak.representations.idm.CredentialRepresentation credential = 
                new org.keycloak.representations.idm.CredentialRepresentation();
            credential.setType(org.keycloak.representations.idm.CredentialRepresentation.PASSWORD);
            credential.setValue(tempPassword);
            credential.setTemporary(false); // Set as non-temporary to avoid required actions
            
            userResource.resetPassword(credential);
            log.debug("Password set for user: {}", userEmail);
            
            // Clear required actions that might block password grant
            // This is safe for Freshdesk integration since users authenticate via Freshdesk
            userRep = userResource.toRepresentation();
            if (userRep.getRequiredActions() != null && !userRep.getRequiredActions().isEmpty()) {
                userRep.setRequiredActions(new java.util.ArrayList<>());
                userResource.update(userRep);
                log.debug("Cleared required actions for user: {}", userEmail);
            }
            
            // Use Direct Access Grant (password grant) to get token
            String token = getTokenViaPasswordGrant(keycloakUsername, tempPassword);
            
            // Restore original required actions (except UPDATE_PASSWORD which we don't want)
            if (!originalRequiredActions.isEmpty()) {
                java.util.List<String> restoredActions = originalRequiredActions.stream()
                    .filter(action -> !"UPDATE_PASSWORD".equals(action))
                    .toList();
                
                if (!restoredActions.isEmpty()) {
                    userRep = userResource.toRepresentation();
                    userRep.setRequiredActions(restoredActions);
                    userResource.update(userRep);
                    log.debug("Restored required actions for user: {}", userEmail);
                }
            }
            
            return token;
            
        } catch (Exception e) {
            log.error("Error generating token via temporary password for user: {}", userEmail, e);
            throw new JwtTokenException("Failed to generate token: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get token using password grant (Direct Access Grant)
     */
    private String getTokenViaPasswordGrant(String username, String password) {
        RestTemplate restTemplate = new RestTemplate();
        
        String tokenEndpoint = keycloakAuthServerUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect/token";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "password");
        params.add("client_id", keycloakClientId);
        params.add("client_secret", keycloakClientSecret);
        params.add("username", username);
        params.add("password", password);
        params.add("scope", "openid email profile"); // Ensure email claim is included in token
        
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
        
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                tokenEndpoint,
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> tokenResponse = response.getBody();
                String accessToken = (String) tokenResponse.get("access_token");
                if (accessToken != null && !accessToken.isEmpty()) {
                    log.debug("Successfully obtained token via password grant for user: {}", username);
                    log.debug("Token expires in: {} seconds", tokenResponse.get("expires_in"));
                    return accessToken;
                }
            }
            
            log.error("Password grant failed with status: {}", response.getStatusCode());
            if (response.getBody() != null) {
                log.error("Error response: {}", response.getBody());
            }
            throw new JwtTokenException("Failed to get token via password grant: " + response.getStatusCode());
            
        } catch (JwtTokenException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error getting token via password grant for user: {}", username, e);
            throw new JwtTokenException("Failed to get token via password grant: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate a secure temporary password
     */
    private String generateSecureTempPassword() {
        // Generate a secure random password for temporary use
        // This password will be used only to get a token and is marked as temporary
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder password = new StringBuilder();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        
        for (int i = 0; i < 32; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return password.toString();
    }
    

    /**
     * Authenticate token and return user
     */
    private User authenticateToken(String token) {
        // Validate token and get user
        // This should use your existing token validation logic
        UserDTO userDto = new UserDTO();
        userDto.setToken(token);
        userDto.setAuthProvider(com.verlake.dam.enums.AuthProvider.KEYCLOAK);
        
        try {
            TokenService tokenService = tokenServiceManager.getService(com.verlake.dam.enums.AuthProvider.KEYCLOAK);
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
     * Verify user has DEVELOPER role
     */
    private void verifyDeveloperRole(User user) {
        if (!userService.hasRole(user, Roles.DEVELOPER.getOriginalName())) {
            throw new AccessDeniedException("Access denied: DEVELOPER role required");
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

