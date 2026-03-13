package com.verlake.dam.controller.jira;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.assets.dto.AccessRequestDTO;
import com.verlake.dam.entity.jira.dto.JiraAccessConfigRequest;
import com.verlake.dam.entity.jira.dto.JiraAssetOwnerRequest;
import com.verlake.dam.entity.jira.dto.JiraProvisionRequest;
import com.verlake.dam.entity.jira.dto.JiraRevokeRequest;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.service.assets.AccessRequestService;
import com.verlake.dam.service.assets.AssetService;
import com.verlake.dam.exception.JiraIntegrationException;
import com.verlake.dam.service.jira.JiraIntegrationService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.entity.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.verlake.dam.utils.Constants;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for Jira integration endpoints
 * Handles webhooks and API calls from Jira Forge app
 */
@RestController
@RequestMapping("/api/jira")
@Slf4j
public class JiraIntegrationController {

    private final JiraIntegrationService jiraIntegrationService;
    private final UserService userService;
    private final AccessRequestService accessRequestService;
    private final AssetService assetService;
    private final ObjectMapper objectMapper;

    public JiraIntegrationController(
            JiraIntegrationService jiraIntegrationService,
            UserService userService,
            AccessRequestService accessRequestService,
            AssetService assetService,
            ObjectMapper objectMapper) {
        this.jiraIntegrationService = jiraIntegrationService;
        this.userService = userService;
        this.accessRequestService = accessRequestService;
        this.assetService = assetService;
        this.objectMapper = objectMapper;
    }

    /**
     * Authenticate Forge user and get session token (like Freshdesk auth).
     * When user has no valid Keycloak session, generates a token via impersonation
     * so the frontend can use it for subsequent API calls.
     * Uses Forge signature auth: X-User-Id, X-Timestamp, X-User-Email, X-Signature
     *
     * POST /api/jira/auth
     */
    @PostMapping("/auth")
    public ResponseEntity<Map<String, Object>> authenticateForgeUser(
            @RequestHeader(value = "X-User-Id", required = false) String accountId,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-Signature", required = false) String signature) {

        jiraIntegrationService.verifyForgeRequestSignature(accountId, timestamp, signature);

        try {
            Map<String, Object> response = jiraIntegrationService.authenticateForgeUser(accountId, userEmail);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to authenticate Forge user", e);
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication failed: " + e.getMessage()
            );
        }
    }

    /**
     * Save access configuration from Jira app panel
     * Called when user configures access in Jira issue panel
     * Uses Forge signature auth: X-User-Id, X-Timestamp, X-Signature
     * Signature = HMAC-SHA256(secret, accountId + timestamp + requestBody)
     *
     * POST /api/jira/config
     */
    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> saveAccessConfig(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-User-Id", required = false) String accountId,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Signature", required = false) String signature) {

        try {
            jiraIntegrationService.verifyForgeRequestSignature(accountId, timestamp, signature);
            JiraAccessConfigRequest request = objectMapper.readValue(rawBody, JiraAccessConfigRequest.class);
            log.info("Received access configuration from Jira for issue: {}", request.getIssueKey());

            Map<String, Object> response = jiraIntegrationService.saveAccessConfiguration(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to save access configuration", e);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Failed to save access configuration: " + e.getMessage()
            );
        }
    }

    /**
     * Provision access when Jira issue is approved
     * Called via webhook when workflow transitions to "Approved"
     * 
     * POST /api/jira/provision
     */
    @PostMapping("/provision")
    public ResponseEntity<Map<String, Object>> provisionAccess(
            @RequestBody JiraProvisionRequest request,
            @RequestHeader(value = "X-Jira-Signature", required = false) String signature) {
        
        log.info("Provisioning access for Jira issue: {}", request.getIssueKey());
        
        // Verify webhook signature
        jiraIntegrationService.verifyWebhookSignature(request, signature);
        
        try {
            Map<String, Object> response = jiraIntegrationService.provisionAccess(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to provision access for Jira issue: {}", request.getIssueKey(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to provision access: " + e.getMessage()
            );
        }
    }

    /**
     * Revoke access when Jira issue expires or is rejected
     * 
     * POST /api/jira/revoke
     */
    @PostMapping("/revoke")
    public ResponseEntity<Map<String, Object>> revokeAccess(
            @RequestBody JiraRevokeRequest request,
            @RequestHeader(value = "X-Jira-Signature", required = false) String signature) {
        
        log.info("Revoking access for Jira issue: {}", request.getIssueKey());
        
        jiraIntegrationService.verifyWebhookSignature(request, signature);
        
        try {
            Map<String, Object> response = jiraIntegrationService.revokeAccess(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to revoke access for Jira issue: {}", request.getIssueKey(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to revoke access: " + e.getMessage()
            );
        }
    }

    /**
     * Get access configuration for a Jira issue
     * Called by Forge app to display current configuration
     * Uses Forge signature: X-User-Id, X-Timestamp, X-User-Email, X-Signature
     *
     * Role-based behavior:
     * - User with only ASSET_OWNER role: returns null (no configuration to show)
     * - User with ACCESSOR role (with or without ASSET_OWNER): returns config if exists; 404 if not (frontend shows "no access configuration")
     * - When user has ASSET_OWNER and access request was created by another user (accessor): includes showApproveReject=true
     *
     * GET /api/jira/config/{issueKey}
     */
    @GetMapping("/config/{issueKey}")
    public ResponseEntity<?> getAccessConfig(
            @PathVariable String issueKey,
            @RequestHeader(value = "X-User-Id", required = false) String accountId,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-Signature", required = false) String signature) {

        jiraIntegrationService.verifyForgeRequestSignature(accountId, timestamp, signature);
        User user = jiraIntegrationService.resolveForgeUser(userEmail);

        try {
            Map<String, Object> config = jiraIntegrationService.getAccessConfiguration(issueKey, user);
            if (config == null) {
                config = new HashMap<>();
                config.put("notFound", true);
            }
            return ResponseEntity.ok(config);
        } catch (IllegalArgumentException e) {
            log.debug("Access configuration not found for Jira issue: {}", issueKey);
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Access configuration not found for issue: " + issueKey
            );
        } catch (Exception e) {
            log.error("Failed to get access configuration for Jira issue: {}", issueKey, e);
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Access configuration not found for issue: " + issueKey
            );
        }
    }

    /**
     * Get available assets for Jira app
     * Uses Forge signature auth: X-User-Id, X-Timestamp, X-User-Email, X-Signature
     * Signature = HMAC-SHA256(secret, accountId + timestamp + email)
     *
     * GET /api/jira/assets
     */
    @GetMapping("/assets")
    public ResponseEntity<?> getAvailableAssets(
            @RequestHeader(value = "X-User-Id", required = false) String accountId,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-Signature", required = false) String signature) {

        log.info("Getting available assets for authenticated Jira user");

        try {
            jiraIntegrationService.verifyForgeRequestSignature(accountId, timestamp, signature);
            User user = jiraIntegrationService.resolveForgeUser(userEmail);

            if (!userService.hasRole(user, Roles.ACCESSOR.getOriginalName())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "User does not have ACCESSOR role. Current roles: " +
                                user.getRoles().stream().map(r -> r.getName()).toList()
                );
            }

            log.info("User {} authenticated successfully via Forge signature, fetching assets", user.getEmail());
            return ResponseEntity.ok(jiraIntegrationService.getAvailableAssets(user));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get available assets", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to retrieve assets: " + e.getMessage()
            );
        }
    }

    /**
     * Approve access request
     * Uses Forge signature auth: X-User-Id, X-Timestamp, X-User-Email, X-Signature
     * Signature = HMAC-SHA256(secret, accountId + timestamp + requestBody)
     *
     * POST /api/jira/request/{accessRequestId}/approve
     */
    @PostMapping("/request/{accessRequestId}/approve")
    public ResponseEntity<Map<String, Object>> approveAccessRequest(
            @PathVariable Long accessRequestId,
            @RequestBody String rawBody,
            @RequestHeader(value = "X-User-Id", required = false) String accountId,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-Signature", required = false) String signature) {

        log.info("Asset owner approving access request {}", accessRequestId);

        try {
            jiraIntegrationService.verifyForgeRequestSignature(accountId, timestamp, signature);
            AccessRequestDTO accessRequestDTO = objectMapper.readValue(rawBody, AccessRequestDTO.class);
            User approver = jiraIntegrationService.resolveForgeUser(userEmail);
            
            // Check if user has ASSET_OWNER role
            if (!userService.hasRole(approver, Roles.ASSET_OWNER.getOriginalName())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "User does not have ASSET_OWNER role. Only asset owners can approve requests."
                );
            }
            
            // Check if user is active
            if (!approver.getIsActive()) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "User account is not active"
                );
            }
            
            // Verify user is asset owner of the asset in the request
            AccessRequest accessRequest = accessRequestService.findById(accessRequestId);
            
            // Check if approver is owner of the asset
            boolean isAssetOwner = assetService.isUserAssetOwner(approver, accessRequest.getAsset());
            if (!isAssetOwner) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "User is not the owner of asset: " + accessRequest.getAsset().getName()
                );
            }
            
            // Approve the request (run with Forge user context so getCurrentUser/getUserKey work)
            accessRequestDTO.setRequestId(accessRequestId);
            jiraIntegrationService.runWithForgeUserContext(approver, () -> {
                try {
                    accessRequestService.setApprovalStatusOfAccessRequest(
                            accessRequestId,
                            accessRequestDTO,
                            ApprovalStatus.APPROVED
                    );
                } catch (JsonParseException e) {
                    throw new JiraIntegrationException("Failed to process approval request", e);
                }
            });

            Map<String, Object> response = Map.of(
                    Constants.RESPONSE_SUCCESS, true,
                    Constants.RESPONSE_MESSAGE, "Access request approved successfully",
                    "accessRequestId", accessRequestId,
                    "status", "APPROVED"
            );
            
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to approve access request {}", accessRequestId, e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to approve access request: " + e.getMessage()
            );
        }
    }

    /**
     * Reject access request
     * Uses Forge signature auth: X-User-Id, X-Timestamp, X-User-Email, X-Signature
     * Signature = HMAC-SHA256(secret, accountId + timestamp + requestBody)
     *
     * POST /api/jira/request/{accessRequestId}/reject
     */
    @PostMapping("/request/{accessRequestId}/reject")
    public ResponseEntity<Map<String, Object>> rejectAccessRequest(
            @PathVariable Long accessRequestId,
            @RequestBody String rawBody,
            @RequestHeader(value = "X-User-Id", required = false) String accountId,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-Signature", required = false) String signature) {

        log.info("Asset owner rejecting access request {}", accessRequestId);

        try {
            jiraIntegrationService.verifyForgeRequestSignature(accountId, timestamp, signature);
            AccessRequestDTO accessRequestDTO = objectMapper.readValue(rawBody, AccessRequestDTO.class);
            User approver = jiraIntegrationService.resolveForgeUser(userEmail);

            if (!userService.hasRole(approver, Roles.ASSET_OWNER.getOriginalName())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "User does not have ASSET_OWNER role. Only asset owners can reject requests."
                );
            }

            AccessRequest accessRequest = accessRequestService.findById(accessRequestId);
            boolean isAssetOwner = assetService.isUserAssetOwner(approver, accessRequest.getAsset());
            if (!isAssetOwner) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "User is not the owner of asset: " + accessRequest.getAsset().getName()
                );
            }

            accessRequestDTO.setRequestId(accessRequestId);
            jiraIntegrationService.runWithForgeUserContext(approver, () -> {
                try {
                    accessRequestService.setApprovalStatusOfAccessRequest(
                            accessRequestId,
                            accessRequestDTO,
                            ApprovalStatus.REJECTED
                    );
                } catch (JsonParseException e) {
                    throw new JiraIntegrationException("Failed to process approval request", e);
                }
            });

            return ResponseEntity.ok(Map.of(
                    Constants.RESPONSE_SUCCESS, true,
                    Constants.RESPONSE_MESSAGE, "Access request rejected successfully",
                    "accessRequestId", accessRequestId,
                    "status", "REJECTED"
            ));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to reject access request {}", accessRequestId, e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to reject access request: " + e.getMessage()
            );
        }
    }

    /**
     * Resolve the asset owner from a list of Jira role member emails.
     * The Forge app sends the emails of users in the hagrids.assetowner project role;
     * the backend returns whichever one is actually assigned as owner of the given asset.
     * Uses Forge signature auth: X-User-Id, X-Timestamp, X-User-Email, X-Signature
     *
     * POST /api/jira/asset-owner
     */
    @PostMapping("/asset-owner")
    public ResponseEntity<Map<String, Object>> resolveAssetOwner(
            @RequestBody JiraAssetOwnerRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String accountId,
            @RequestHeader(value = "X-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-Signature", required = false) String signature) {

        try {
            jiraIntegrationService.verifyForgeRequestSignature(accountId, timestamp, signature);

            String ownerEmail = jiraIntegrationService.resolveAssetOwnerFromEmails(
                    request.getAssetId(), request.getEmails());

            return ResponseEntity.ok(Map.of("ownerEmail", ownerEmail != null ? ownerEmail : ""));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to resolve asset owner for assetId={}", request.getAssetId(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to resolve asset owner: " + e.getMessage()
            );
        }
    }
}
