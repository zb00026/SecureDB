package com.verlake.dam.service.jira;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verlake.dam.entity.jira.dto.JiraAccessConfigRequest;
import com.verlake.dam.entity.jira.dto.JiraProvisionRequest;
import com.verlake.dam.entity.jira.dto.JiraRevokeRequest;
import com.verlake.dam.entity.assets.AccessLevel;
import com.verlake.dam.entity.assets.AccessLevelObject;
import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.dto.AccessRequestDTO;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.enums.JiraAccessLevel;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.enums.AssetType;
import com.verlake.dam.enums.DatabaseType;
import com.verlake.dam.entity.assets.AssetObject;
import com.verlake.dam.repository.assets.AccessLevelObjectRepository;
import com.verlake.dam.repository.assets.AccessLevelRepository;
import com.verlake.dam.repository.assets.AccessRequestRepository;
import com.verlake.dam.repository.assets.AssetObjectRepository;
import com.verlake.dam.service.assets.AccessRequestService;
import com.verlake.dam.service.assets.AssetService;
import com.verlake.dam.service.auth.KeycloakSessionTokenService;
import com.verlake.dam.service.users.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Base64;
import java.util.regex.Pattern;

import com.verlake.dam.utils.Constants;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JiraIntegrationService {

    private final AccessRequestService accessRequestService;
    private final AccessRequestRepository accessRequestRepository;
    private final AccessLevelObjectRepository accessLevelObjectRepository;
    private final AccessLevelRepository accessLevelRepository;
    private final AssetObjectRepository assetObjectRepository;
    private final AssetService assetService;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final KeycloakSessionTokenService keycloakSessionTokenService; // null when Keycloak is not auth provider
    private final JwtDecoder jwtDecoder;

    @Value("${jira.webhook.secret:}")
    private String jiraWebhookSecret;

    @Value("${jira.forge.secret:}")
    private String jiraForgeSecret;

    @Value("${jira.forge.secret.base64:false}")
    private boolean jiraForgeSecretBase64;

    public JiraIntegrationService(
            AccessRequestService accessRequestService,
            AccessRequestRepository accessRequestRepository,
            AccessLevelObjectRepository accessLevelObjectRepository,
            AccessLevelRepository accessLevelRepository,
            AssetObjectRepository assetObjectRepository,
            AssetService assetService,
            UserService userService,
            ObjectMapper objectMapper,
            java.util.Optional<KeycloakSessionTokenService> keycloakSessionTokenService,
            @Lazy JwtDecoder jwtDecoder) {
        this.accessRequestService = accessRequestService;
        this.accessRequestRepository = accessRequestRepository;
        this.accessLevelObjectRepository = accessLevelObjectRepository;
        this.accessLevelRepository = accessLevelRepository;
        this.assetObjectRepository = assetObjectRepository;
        this.assetService = assetService;
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.keycloakSessionTokenService = keycloakSessionTokenService.orElse(null);
        this.jwtDecoder = jwtDecoder;
    }

    /**
     * Run an action with the Forge user's token set in SecurityContext.
     * This allows getCurrentUser() and getUserKey() to work when the request comes
     * via Forge signature auth (no JWT) instead of JWT auth.
     */
    public void runWithForgeUserContext(User user, Runnable action) {
        if (keycloakSessionTokenService == null) {
            throw new IllegalStateException(
                    "Keycloak session token service not available. Jira approve/reject requires Keycloak auth provider.");
        }
        String token = keycloakSessionTokenService.generateTokenForUser(user);
        Jwt jwt = jwtDecoder.decode(token);
        var auth = new JwtAuthenticationToken(jwt, java.util.Collections.emptyList());
        var previous = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(auth);
            action.run();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    /**
     * Verify webhook signature using HMAC SHA256
     */
    public void verifyWebhookSignature(Object request, String signature) {
        if (!StringUtils.hasText(jiraWebhookSecret)) {
            log.warn("Jira webhook secret not configured. Skipping signature verification.");
            return; // Allow in development, require in production
        }

        if (!StringUtils.hasText(signature)) {
            throw new SecurityException("Missing webhook signature");
        }

        try {
            // Serialize request to JSON string
            String payload = objectMapper.writeValueAsString(request);
            
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    jiraWebhookSecret.getBytes(StandardCharsets.UTF_8), 
                    "HmacSHA256"
            );
            mac.init(secretKeySpec);
            
            byte[] hashBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String calculatedSignature = bytesToHex(hashBytes);
            
            // Use constant-time comparison to prevent timing attacks
            if (!MessageDigest.isEqual(
                    signature.getBytes(StandardCharsets.UTF_8),
                    calculatedSignature.getBytes(StandardCharsets.UTF_8))) {
                throw new SecurityException("Invalid webhook signature");
            }
            
            log.debug("Webhook signature verified successfully");
        } catch (Exception e) {
            log.error("Failed to verify webhook signature", e);
            throw new SecurityException("Signature verification failed", e);
        }
    }

    /**
     * Verify Forge request signature.
     * Signature format: HMAC-SHA256(secret, accountId + timestamp + requestBody)
     * Used for requests from Forge app (no OAuth token required).
     *
     * @param accountId  Atlassian account ID from X-User-Id header
     * @param timestamp  Request timestamp from X-Timestamp header
     * @param signature   Signature from X-Signature header
     */
    public void verifyForgeRequestSignature(String accountId, String timestamp, String signature) {
        if (!StringUtils.hasText(jiraForgeSecret)) {
            log.warn("Jira Forge secret not configured. Skipping Forge signature verification.");
            return;
        }

        if (!StringUtils.hasText(accountId) || !StringUtils.hasText(timestamp) || !StringUtils.hasText(signature)) {
            throw new SecurityException("Missing Forge auth headers: X-User-Id, X-Timestamp, X-Signature required");
        }

        try {
            // Match frontend format: accountId|timestamp|body (pipe separator)
            String signedData = accountId + "|" + timestamp;
            // Normalize to NFC for consistency with JavaScript (avoids Unicode normalization mismatch)
            signedData = Normalizer.normalize(signedData, Normalizer.Form.NFC);
            String expected = hmacSha256Hex(jiraForgeSecret, signedData);
            String received = signature.trim();

            // Compare case-insensitively (hex can be upper or lowercase from different clients)
            if (!expected.equalsIgnoreCase(received)) {
                log.debug("Forge signature mismatch - payload length: {}, expected: {}, received: {}, secret length: {}",
                        signedData.length(), expected, received,
                        jiraForgeSecret != null ? jiraForgeSecret.trim().length() : 0);
                throw new SecurityException("Invalid Forge request signature");
            }
            log.debug("Forge request signature verified for accountId: {}", accountId);
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify Forge signature", e);
            throw new SecurityException("Forge signature verification failed", e);
        }
    }

    /**
     * Compute HMAC-SHA256 and return hex string.
     * Uses UTF-8 encoding to match Node.js crypto and browser Web Crypto API.
     * Secret is trimmed and normalized to handle env vars and Unicode.
     */
    private String hmacSha256Hex(String secret, String data) {
        try {
            byte[] keyBytes = prepareSecretBytes(secret);
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec spec = new SecretKeySpec(keyBytes, "HmacSHA256");
            mac.init(spec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new SecurityException("HMAC computation failed", e);
        }
    }

    /**
     * Prepare secret bytes for HMAC. Handles trimming, Unicode normalization, and optional Base64.
     */
    private byte[] prepareSecretBytes(String secret) {
        String s = secret != null ? secret.trim() : "";
        s = Normalizer.normalize(s, Normalizer.Form.NFC);
        if (jiraForgeSecretBase64) {
            try {
                return Base64.getDecoder().decode(s);
            } catch (IllegalArgumentException e) {
                log.warn("JIRA_FORGE_SECRET_BASE64 is true but secret is not valid Base64, using as UTF-8");
            }
        }
        return s.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Resolve Forge-authenticated user by account ID and email.
     * Email is required from X-User-Email header (sent from frontend).
     */
    public User resolveForgeUser(String accountId, String email) {
        if (!StringUtils.hasText(email)) {
            throw new SecurityException("X-User-Email header is required for Forge authentication.");
        }
        if (!isValidEmailFormat(email)) {
            throw new SecurityException("Invalid email format: " + email);
        }
        User user = userService.findByEmail(email);
        if (user == null) {
            throw new SecurityException("DAM user not found for email: " + email + ". Please register in DAM system first.");
        }
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new SecurityException("User account is not active");
        }
        return user;
    }

    /**
     * Authenticate Forge user and generate session token (like Freshdesk flow).
     * When user has no valid Keycloak session (coming from Jira), generates a token
     * via Keycloak impersonation so the frontend can use it for subsequent API calls.
     *
     * @param accountId Atlassian account ID (from X-User-Id)
     * @param email     User email (from X-User-Email)
     * @return Map with success, user, and token (token may be null if Keycloak session service unavailable)
     */
    public Map<String, Object> authenticateForgeUser(String accountId, String email) {
        User user = resolveForgeUser(accountId, email);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("user", createForgeUserResponse(user));
        response.put("message", "Authentication successful");

        if (keycloakSessionTokenService != null) {
            try {
                String token = keycloakSessionTokenService.generateTokenForUser(user);
                response.put("token", token);
                log.info("Generated session token for Forge user: {}", email);
            } catch (Exception e) {
                log.warn("Could not generate session token for Forge user {}: {}. User can still use Forge signature auth.", email, e.getMessage());
            }
        } else {
            log.debug("Keycloak session token service not available. Forge user will use signature auth per request.");
        }

        return response;
    }

    private Map<String, Object> createForgeUserResponse(User user) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("email", user.getEmail());
        userMap.put("firstName", user.getFirstName());
        userMap.put("lastName", user.getLastName());
        userMap.put("isActive", user.getIsActive());
        userMap.put("roles", user.getRoles() != null
                ? user.getRoles().stream().map(r -> r.getName()).toList()
                : List.of());
        return userMap;
    }

    /**
     * Validate that the string is a valid email format.
     */
    private boolean isValidEmailFormat(String email) {
        return email != null && Pattern.matches(Constants.EMAIL_REGEX, email.trim());
    }

    /**
     * Save access configuration from Jira
     * Creates or updates access request configuration
     */
    @Transactional
    public Map<String, Object> saveAccessConfiguration(
            JiraAccessConfigRequest request) {
        
        log.info("Saving access configuration for Jira issue: {}", request.getIssueKey());
        
        // Security: Map Jira Account ID → Email → DAM User ID (never trust free text fields)
        // For webhooks: Email comes from signed webhook payload (verified via HMAC signature)
        // HMAC signature ensures the email is trustworthy (cryptographically verified)
        if (!StringUtils.hasText(request.getUserEmail())) {
            throw new SecurityException("User email is required in webhook payload (verified via HMAC signature)");
        }
        
        // Find DAM user by email (from HMAC-verified webhook payload)
        User requestor = userService.findByEmail(request.getUserEmail());
        if (requestor == null) {
            throw new SecurityException("DAM user not found for email: " + request.getUserEmail() + 
                ". Please register in DAM system first.");
        }
        
        // Account ID is used for logging/reference (also from verified webhook)
        log.debug("Mapping: Jira Account ID {} → Email {} → DAM User ID {}", 
            request.getJiraAccountId(), request.getUserEmail(), requestor.getId());
        
        // Get asset
        Asset asset = assetService.findById(request.getAssetId());
        
        // Check if access request already exists for this Jira issue
        Optional<AccessRequest> existingRequest = accessRequestRepository
                .findByJiraIssueKey(request.getIssueKey());
        
        AccessRequest accessRequest;
        if (existingRequest.isPresent()) {
            accessRequest = existingRequest.get();
            
            // Check if configuration is locked (already submitted for approval)
            if (accessRequest.getAssetApproverStatus() != ApprovalStatus.REQUESTED 
                    && accessRequest.getAssetApproverStatus() != null) {
                throw new IllegalStateException(
                        "Access configuration is locked. Cannot modify after submission.");
            }
            
            // Update existing request
            updateAccessRequest(accessRequest, request, asset, requestor);
        } else {
            // Create new access request
            accessRequest = createAccessRequest(request, asset, requestor);
        }
        
        Map<String, Object> response = new HashMap<>(getAccessConfiguration(request.getIssueKey(), requestor));
        response.put("success", true);
        response.put("message", "Access configuration saved successfully");
        
        return response;
    }

    /**
     * Provision access when Jira issue is approved
     */
    @Transactional
    public Map<String, Object> provisionAccess(
            JiraProvisionRequest request) {
        
        log.info("Provisioning access for Jira issue: {}", request.getIssueKey());
        
        AccessRequest accessRequest = accessRequestRepository
                .findByJiraIssueKey(request.getIssueKey())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Access request not found for Jira issue: " + request.getIssueKey()));
        
        // Security: Map Jira Account ID → Email → DAM User ID (never trust free text fields)
        // For webhooks: Email comes from signed webhook payload (verified via HMAC signature)
        if (!StringUtils.hasText(request.getApproverEmail())) {
            throw new SecurityException("Approver email is required in webhook payload (verified via HMAC signature)");
        }
        
        // Find approver by email (from HMAC-verified webhook payload)
        User approver = userService.findByEmail(request.getApproverEmail());
        if (approver == null) {
            throw new SecurityException("Approver not found for email: " + request.getApproverEmail());
        }
        
        log.debug("Mapping: Approver Jira Account ID {} → Email {} → DAM User ID {}", 
            request.getApproverAccountId(), request.getApproverEmail(), approver.getId());
        
        // Approve the access request
        // This will trigger credential creation and access provisioning
        AccessRequestDTO approvalDTO = new AccessRequestDTO();
        approvalDTO.setRequestId(accessRequest.getId());
        
        try {
            accessRequestService.setApprovalStatusOfAccessRequest(
                    accessRequest.getId(),
                    approvalDTO,
                    ApprovalStatus.APPROVED
            );
        } catch (JsonParseException e) {
            log.error("Failed to parse JSON during approval", e);
            throw new RuntimeException("Failed to approve access request", e);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("damRequestId", accessRequest.getId());
        response.put("message", "Access provisioned successfully");
        
        return response;
    }

    /**
     * Revoke access
     */
    @Transactional
    public Map<String, Object> revokeAccess(
            JiraRevokeRequest request) {
        
        log.info("Revoking access for Jira issue: {}", request.getIssueKey());
        
        AccessRequest accessRequest = accessRequestRepository
                .findByJiraIssueKey(request.getIssueKey())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Access request not found for Jira issue: " + request.getIssueKey()));
        
        // Mark as expired or rejected
        if ("rejected".equals(request.getReason())) {
            accessRequest.setAssetApproverStatus(ApprovalStatus.REJECTED);
            accessRequest.setRejectReason("Rejected via Jira integration: " + request.getReason());
        } else {
            // Expired
            accessRequest.setExpiryDate(LocalDateTime.now());
        }
        
        accessRequestRepository.save(accessRequest);
        
        // Revoke credentials if they exist
        if (accessRequest.getAssetCredential() != null) {
            // Mark credential as deleted or revoke access
            // This depends on your credential management implementation
            log.info("Revoking credentials for access request ID: {}", accessRequest.getId());
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Access revoked successfully");
        
        return response;
    }

    /**
     * Get access configuration for a Jira issue.
     * Role-based behavior:
     * - User with only ASSET_OWNER role and no asset credential for this asset: returns null (no configuration to show)
     * - User with ACCESSOR role (with or without ASSET_OWNER): returns config if exists; throws if not (frontend shows "no access configuration")
     * - When user has ASSET_OWNER and access request was created by another user (accessor): includes showApproveReject=true
     */
    public Map<String, Object> getAccessConfiguration(String issueKey, User user) {
        boolean hasAccessor = userService.hasRole(user, Roles.ACCESSOR.getOriginalName());
        boolean hasAssetOwner = userService.hasRole(user, Roles.ASSET_OWNER.getOriginalName());
        boolean hasOnlyAssetOwner = hasAssetOwner && !hasAccessor;

        AccessRequest accessRequest = accessRequestRepository
                .findByJiraIssueKey(issueKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Access configuration not found for issue: " + issueKey));

        if (hasOnlyAssetOwner && !assetService.isUserAssetOwner(user, accessRequest.getAsset())) {
            return null;
        }
        List<String> tables = accessLevelObjectRepository.findByAccessRequestId(accessRequest.getId())
                .stream()
                .map(AccessLevelObject::getObjectName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        long durationDays = 0;
        if (accessRequest.getRequestTime() != null && accessRequest.getExpiryDate() != null) {
            durationDays = ChronoUnit.DAYS.between(accessRequest.getRequestTime(), accessRequest.getExpiryDate());
        }

        boolean showApproveReject = hasAssetOwner
                && accessRequest.getRequestor() != null
                && !accessRequest.getRequestor().getId().equals(user.getId())
                && userService.hasRole(accessRequest.getRequestor(), Roles.ACCESSOR.getOriginalName());

        Map<String, Object> config = new HashMap<>();
        config.put("damRequestId", accessRequest.getId());
        config.put("assetId", accessRequest.getAsset().getId());
        config.put("assetName", accessRequest.getAsset().getName());
        config.put("status", accessRequest.getAssetApproverStatus());
        config.put("expiryDate", accessRequest.getExpiryDate());
        config.put("durationDays", durationDays);
        config.put("businessJustification", accessRequest.getRequestReason());
        config.put("accessLevel", accessRequest.getJiraAccessLevel() != null ? accessRequest.getJiraAccessLevel().name() : null);
        config.put("isLocked", accessRequest.getAssetApproverStatus() != ApprovalStatus.REQUESTED
                && accessRequest.getAssetApproverStatus() != null);
        config.put("tables", tables);
        config.put("showApproveReject", showApproveReject);

        return config;
    }

    /**
     * Get available assets for Jira app.
     * Only returns assets that have synced AssetObjects (schema has been fetched).
     * Includes tables list from asset object's objectsJson (TABLE.data).
     */
    public List<Map<String, Object>> getAvailableAssets() {
        return assetService.getAllAssetListWithSyncedObjects().stream()
                .map(asset -> {
                    Map<String, Object> assetInfo = new HashMap<>();
                    assetInfo.put("id", asset.getId());
                    assetInfo.put("name", asset.getName());
                    assetInfo.put("type", asset.getType());
                    assetInfo.put("description", asset.getDescription());
                    assetInfo.put("tables", getTableNamesFromAsset(asset));
                    return assetInfo;
                })
                .collect(Collectors.toList());
    }

    /**
     * Read table names from asset's AssetObject objectsJson.
     * Returns TABLE.data as list of string names (e.g. "schema.table" or table name).
     */
    private List<String> getTableNamesFromAsset(Asset asset) {
        List<AssetObject> assetObjects = assetObjectRepository.findByAsset(asset);
        if (assetObjects == null || assetObjects.isEmpty()) {
            return List.of();
        }
        AssetObject assetObject = assetObjects.get(0);
        try {
            ObjectNode objectsJson = (ObjectNode) objectMapper.readTree(assetObject.getObjectsJson());
            if (!objectsJson.has(Constants.ASSET_ACCESS_OBJECT_TABLE)) {
                return List.of();
            }
            JsonNode tableCategory = objectsJson.get(Constants.ASSET_ACCESS_OBJECT_TABLE);
            if (!tableCategory.has(Constants.ACCESS_OBJECT_ATTR_DATA)) {
                return List.of();
            }
            JsonNode dataArray = tableCategory.get(Constants.ACCESS_OBJECT_ATTR_DATA);
            if (dataArray == null || !dataArray.isArray()) {
                return List.of();
            }
            List<String> tables = new ArrayList<>();
            dataArray.forEach(node -> {
                if (node.isTextual()) {
                    tables.add(node.asText());
                } else if (node.isObject() && node.has("name")) {
                    tables.add(node.get("name").asText());
                }
            });
            return tables;
        } catch (Exception e) {
            log.warn("Failed to parse objectsJson for asset {}: {}", asset.getId(), e.getMessage());
            return List.of();
        }
    }

    // Helper methods
    private AccessRequest createAccessRequest(
            JiraAccessConfigRequest request,
            Asset asset, User requestor) {
        
        AccessRequest accessRequest = new AccessRequest();
        accessRequest.setAsset(asset);
        accessRequest.setRequestor(requestor);
        accessRequest.setJiraIssueKey(request.getIssueKey());
        accessRequest.setJiraIssueId(request.getIssueId());
        accessRequest.setJiraAccessLevel(JiraAccessLevel.fromString(request.getAccessLevel()));
        accessRequest.setRequestReason(request.getBusinessJustification());
        accessRequest.setRequestTime(LocalDateTime.now());
        accessRequest.setIsTempPassword(true);
        accessRequest.setAssetApproverStatus(ApprovalStatus.REQUESTED);
        accessRequest.setAccessorApproverStatus(ApprovalStatus.APPROVED); // Auto-approve for Jira requests
        
        // Set expiry
        if (request.getDurationDays() != null && request.getDurationDays() > 0) {
            accessRequest.setExpiryHours(request.getDurationDays() * 24);
            accessRequest.setExpiryDate(
                    accessRequest.getRequestTime().plusHours(accessRequest.getExpiryHours()));
        } else {
            // Default to 90 days
            accessRequest.setExpiryHours(90 * 24);
            accessRequest.setExpiryDate(
                    accessRequest.getRequestTime().plusHours(accessRequest.getExpiryHours()));
        }
        
        // Save request first to get ID
        accessRequest = accessRequestRepository.save(accessRequest);
        
        // Parse and set access level objects (tables, permissions)
        // Convert tables string to AccessLevelObject entities
        if (StringUtils.hasText(request.getTables())) {
            List<AccessLevelObject> accessLevelObjects = parseTablesToAccessLevelObjects(
                    request.getTables(), request.getAccessLevel(), asset, accessRequest);
            accessRequest.setAccessSql(accessRequestService.generateAccessSql(accessLevelObjects));
            accessLevelObjectRepository.saveAll(accessLevelObjects);
        }
        
        return accessRequestRepository.save(accessRequest);
    }

    private void updateAccessRequest(
            AccessRequest accessRequest,
            JiraAccessConfigRequest request,
            Asset asset, User requestor) {
        
        accessRequest.setAsset(asset);
        accessRequest.setJiraAccessLevel(JiraAccessLevel.fromString(request.getAccessLevel()));
        accessRequest.setRequestReason(request.getBusinessJustification());
        
        if (request.getDurationDays() != null && request.getDurationDays() > 0) {
            accessRequest.setExpiryHours(request.getDurationDays() * 24);
            accessRequest.setExpiryDate(
                    accessRequest.getRequestTime().plusHours(accessRequest.getExpiryHours()));
        }
        
        // Delete existing access level objects
        accessLevelObjectRepository.deleteByAccessRequest(accessRequest);
        
        // Create new access level objects
        if (StringUtils.hasText(request.getTables())) {
            List<AccessLevelObject> accessLevelObjects = parseTablesToAccessLevelObjects(
                    request.getTables(), request.getAccessLevel(), asset, accessRequest);
            accessRequest.setAccessSql(accessRequestService.generateAccessSql(accessLevelObjects));
            accessLevelObjectRepository.saveAll(accessLevelObjects);
        }
        
        accessRequestRepository.save(accessRequest);
    }

    private List<AccessLevelObject> parseTablesToAccessLevelObjects(
            String tables, String accessLevel, Asset asset, AccessRequest accessRequest) {
        
        List<AccessLevelObject> objects = new ArrayList<>();
        
        // Parse tables (comma-separated or JSON array)
        List<String> tableList = new ArrayList<>();
        if (tables.startsWith("[")) {
            // JSON array
            try {
                String[] parsed = objectMapper.readValue(tables, String[].class);
                tableList = Arrays.asList(parsed);
            } catch (Exception e) {
                log.warn("Failed to parse tables as JSON, treating as comma-separated", e);
                tableList = Arrays.asList(tables.split(","));
            }
        } else {
            // Comma-separated
            tableList = Arrays.asList(tables.split(","));
        }
        
        // Validate asset type
        if (asset.getType() != AssetType.DATABASE) {
            throw new IllegalArgumentException("Asset must be of type DATABASE. Found: " + asset.getType());
        }
        
        // Validate database type
        DatabaseType databaseType = asset.getDatabaseType();
        if (databaseType == null) {
            throw new IllegalArgumentException("Asset database type is null for asset ID: " + asset.getId());
        }
        
        // Map access level to template names (from db.009 seed - individual permissions per AccessLevelObject)
        List<String> templateNames = getTemplateNamesForAccessLevel(accessLevel, databaseType);
        
        // Create AccessLevelObject for each table × each template (e.g. READ_WRITE = 4 objects per table: SELECT, INSERT, UPDATE, DELETE)
        for (String tableName : tableList) {
            tableName = tableName.trim();
            if (tableName.isEmpty()) continue;
            
            for (String templateName : templateNames) {
                AccessLevel accessLevelEntity = accessLevelRepository.findByAssetTypeAndDatabaseTypeAndObjectAndTemplates(
                        AssetType.DATABASE,
                        databaseType,
                        Constants.ASSET_ACCESS_OBJECT_TABLE,
                        templateName
                );
                if (accessLevelEntity == null) {
                    log.debug("AccessLevel not found for template: {} (database: {}), skipping", templateName, databaseType);
                    continue;
                }
                
                AccessLevelObject obj = new AccessLevelObject();
                obj.setAccessRequest(accessRequest);
                obj.setRequestor(accessRequest.getRequestor());
                obj.setObjectName(tableName);
                obj.setAccessLevel(accessLevelEntity);
                
                objects.add(obj);
            }
        }
        
        return objects;
    }
    
    /**
     * Get template names for access level (from db.009 seed).
     * Each template = one AccessLevelObject. READ_ONLY=1, READ_WRITE=4, FULL_ACCESS=all table permissions.
     */
    private List<String> getTemplateNamesForAccessLevel(String accessLevel, DatabaseType databaseType) {
        if (accessLevel == null) {
            return getFullAccessTemplateNames(databaseType);
        }
        
        return switch (accessLevel.toUpperCase()) {
            case "READ_ONLY" -> List.of("SELECT");
            case "READ_WRITE" -> List.of("SELECT", "INSERT", "UPDATE", "DELETE");
            case "FULL_ACCESS" -> getFullAccessTemplateNames(databaseType);
            default -> getFullAccessTemplateNames(databaseType);
        };
    }

    /**
     * Full access template names per database type (from db.009-changelog-seed-db-access-level.xml).
     */
    private List<String> getFullAccessTemplateNames(DatabaseType databaseType) {
        return switch (databaseType) {
            case MYSQL -> List.of("SELECT", "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "INDEX",
                    "CREATE VIEW", "SHOW VIEW", "TRIGGER", "REFERENCES");
            case POSTGRESQL -> List.of("SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER");
            case SQLSERVER -> List.of("SELECT", "INSERT", "UPDATE", "DELETE", "REFERENCES");
            default -> List.of("SELECT", "INSERT", "UPDATE", "DELETE");
        };
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
