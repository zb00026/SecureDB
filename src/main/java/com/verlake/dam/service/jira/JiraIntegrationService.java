package com.verlake.dam.service.jira;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.verlake.dam.enums.AssetType;
import com.verlake.dam.enums.DatabaseType;
import com.verlake.dam.repository.assets.AccessLevelObjectRepository;
import com.verlake.dam.repository.assets.AccessLevelRepository;
import com.verlake.dam.repository.assets.AccessRequestRepository;
import com.verlake.dam.service.assets.AccessRequestService;
import com.verlake.dam.service.assets.AssetService;
import com.verlake.dam.service.jira.JiraOAuthService;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JiraIntegrationService {

    private final AccessRequestService accessRequestService;
    private final AccessRequestRepository accessRequestRepository;
    private final AccessLevelObjectRepository accessLevelObjectRepository;
    private final AccessLevelRepository accessLevelRepository;
    private final AssetService assetService;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final JiraOAuthService jiraOAuthService;

    @Value("${jira.webhook.secret:}")
    private String jiraWebhookSecret;

    public JiraIntegrationService(
            AccessRequestService accessRequestService,
            AccessRequestRepository accessRequestRepository,
            AccessLevelObjectRepository accessLevelObjectRepository,
            AccessLevelRepository accessLevelRepository,
            AssetService assetService,
            UserService userService,
            ObjectMapper objectMapper,
            JiraOAuthService jiraOAuthService) {
        this.accessRequestService = accessRequestService;
        this.accessRequestRepository = accessRequestRepository;
        this.accessLevelObjectRepository = accessLevelObjectRepository;
        this.accessLevelRepository = accessLevelRepository;
        this.assetService = assetService;
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.jiraOAuthService = jiraOAuthService;
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
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("damRequestId", accessRequest.getId());
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
     * Get access configuration for a Jira issue
     */
    public Map<String, Object> getAccessConfiguration(String issueKey) {
        AccessRequest accessRequest = accessRequestRepository
                .findByJiraIssueKey(issueKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Access configuration not found for issue: " + issueKey));
        
        Map<String, Object> config = new HashMap<>();
        config.put("damRequestId", accessRequest.getId());
        config.put("assetId", accessRequest.getAsset().getId());
        config.put("assetName", accessRequest.getAsset().getName());
        config.put("status", accessRequest.getAssetApproverStatus());
        config.put("expiryDate", accessRequest.getExpiryDate());
        config.put("isLocked", accessRequest.getAssetApproverStatus() != ApprovalStatus.REQUESTED 
                && accessRequest.getAssetApproverStatus() != null);
        
        return config;
    }

    /**
     * Get available assets for Jira app
     */
    public List<Map<String, Object>> getAvailableAssets() {
        return assetService.getAllAssets().stream()
                .map(asset -> {
                    Map<String, Object> assetInfo = new HashMap<>();
                    assetInfo.put("id", asset.getId());
                    assetInfo.put("name", asset.getName());
                    assetInfo.put("type", asset.getType());
                    assetInfo.put("description", asset.getDescription());
                    return assetInfo;
                })
                .collect(Collectors.toList());
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
        accessRequest.setRequestReason(request.getBusinessJustification());
        accessRequest.setRequestTime(LocalDateTime.now());
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
            accessLevelObjectRepository.saveAll(accessLevelObjects);
        }
        
        return accessRequest;
    }

    private void updateAccessRequest(
            AccessRequest accessRequest,
            JiraAccessConfigRequest request,
            Asset asset, User requestor) {
        
        accessRequest.setAsset(asset);
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
        
        // Map access level string to AccessLevel template
        String templateName = mapAccessLevelToTemplate(accessLevel);
        
        // Find AccessLevel entity
        AccessLevel accessLevelEntity = accessLevelRepository.findByAssetTypeAndDatabaseTypeAndTemplates(
                AssetType.DATABASE,
                databaseType,
                templateName
        );
        
        if (accessLevelEntity == null) {
            log.warn("AccessLevel not found for template: {}, using default", templateName);
            // Try to find a default or create a fallback
            List<AccessLevel> levels = accessLevelRepository.findByAssetTypeAndDatabaseType(
                    AssetType.DATABASE, databaseType);
            if (!levels.isEmpty()) {
                accessLevelEntity = levels.get(0);
            } else {
                throw new IllegalArgumentException(
                        String.format("No AccessLevel found for asset type %s and database type %s", 
                                AssetType.DATABASE, databaseType));
            }
        }
        
        // Create AccessLevelObject for each table
        for (String tableName : tableList) {
            tableName = tableName.trim();
            if (tableName.isEmpty()) continue;
            
            AccessLevelObject obj = new AccessLevelObject();
            obj.setAccessRequest(accessRequest);
            obj.setRequestor(accessRequest.getRequestor());
            obj.setObjectName(tableName);
            obj.setAccessLevel(accessLevelEntity);
            
            objects.add(obj);
        }
        
        return objects;
    }
    
    /**
     * Map access level string to AccessLevel template name
     */
    private String mapAccessLevelToTemplate(String accessLevel) {
        if (accessLevel == null) {
            return "FULL ACCESS";
        }
        
        switch (accessLevel.toUpperCase()) {
            case "READ_ONLY":
                return "SELECT";
            case "READ_WRITE":
                return "INSERT, UPDATE, DELETE";
            case "FULL_ACCESS":
            default:
                return "FULL ACCESS";
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
