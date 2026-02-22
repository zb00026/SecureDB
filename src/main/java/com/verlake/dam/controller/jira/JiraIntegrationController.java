package com.verlake.dam.controller.jira;

import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.assets.dto.AccessRequestDTO;
import com.verlake.dam.entity.jira.dto.JiraAccessConfigRequest;
import com.verlake.dam.entity.jira.dto.JiraProvisionRequest;
import com.verlake.dam.entity.jira.dto.JiraRevokeRequest;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.service.assets.AccessRequestService;
import com.verlake.dam.service.assets.AssetService;
import com.verlake.dam.service.jira.JiraIntegrationService;
import com.verlake.dam.service.jira.JiraOAuthService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.entity.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
    private final JiraOAuthService jiraOAuthService;

    public JiraIntegrationController(
            JiraIntegrationService jiraIntegrationService,
            UserService userService,
            AccessRequestService accessRequestService,
            AssetService assetService,
            JiraOAuthService jiraOAuthService) {
        this.jiraIntegrationService = jiraIntegrationService;
        this.userService = userService;
        this.accessRequestService = accessRequestService;
        this.assetService = assetService;
        this.jiraOAuthService = jiraOAuthService;
    }

    /**
     * Save access configuration from Jira app panel
     * Called when user configures access in Jira issue panel
     * 
     * POST /api/jira/config
     */
    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> saveAccessConfig(
            @RequestBody JiraAccessConfigRequest request,
            @RequestHeader(value = "X-Jira-Signature", required = false) String signature) {
        
        log.info("Received access configuration from Jira for issue: {}", request.getIssueKey());
        
        // Verify webhook signature (HMAC)
        jiraIntegrationService.verifyWebhookSignature(request, signature);
        
        try {
            Map<String, Object> response = jiraIntegrationService.saveAccessConfiguration(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to save access configuration for Jira issue: {}", request.getIssueKey(), e);
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
     * 
     * GET /api/jira/config/{issueKey}
     */
    @GetMapping("/config/{issueKey}")
    public ResponseEntity<Map<String, Object>> getAccessConfig(@PathVariable String issueKey) {
        log.debug("Getting access configuration for Jira issue: {}", issueKey);
        
        try {
            Map<String, Object> config = jiraIntegrationService.getAccessConfiguration(issueKey);
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            log.error("Failed to get access configuration for Jira issue: {}", issueKey, e);
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Access configuration not found for issue: " + issueKey
            );
        }
    }

    /**
     * OAuth 2.0 callback endpoint
     * Exchanges authorization code for access token and creates user mapping
     * 
     * GET /api/jira/oauth/callback
     */
    @GetMapping("/oauth/callback")
    public ResponseEntity<Map<String, Object>> oauthCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error) {
        
        log.info("OAuth callback received - Code: {}, State: {}, Error: {}", code, state, error);
        
        if (error != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "OAuth authorization failed: " + error
            );
        }
        
        if (code == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Authorization code is required"
            );
        }
        
        try {
            // Exchange code for access token and get user info
            Map<String, Object> tokenResult = jiraOAuthService.exchangeCodeForToken(code);
            String accessToken = (String) tokenResult.get("accessToken");
            
            // Authenticate user and get DAM Keycloak token (follows Freshdesk pattern)
            Map<String, Object> authResult = jiraOAuthService.authenticateJiraUser(accessToken);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("token", authResult.get("token")); // DAM Keycloak token
            response.put("user", authResult.get("user"));
            response.put("jiraAccountId", authResult.get("jiraAccountId"));
            response.put("message", "Authentication successful");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("OAuth callback failed", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "OAuth authentication failed: " + e.getMessage()
            );
        }
    }

    /**
     * Get available assets for Jira app
     * Requires: OAuth 2.0 authentication via Bearer token
     * 
     * GET /api/jira/assets
     */
    @GetMapping("/assets")
    public ResponseEntity<?> getAvailableAssets(
            @RequestHeader(value = "Authorization", required = true) String authorization) {
        
        log.info("Getting available assets for authenticated Jira user");
        
        try {
            // Extract Bearer token
            if (!authorization.startsWith("Bearer ")) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid authorization header. Must be: Bearer <token>"
                );
            }
            
            String accessToken = authorization.substring(7);
            
            // Authenticate user using OAuth token (returns DAM Keycloak token and user info)
            Map<String, Object> authResult = jiraOAuthService.authenticateJiraUser(accessToken);
            Map<String, Object> userMap = (Map<String, Object>) authResult.get("user");
            Long userId = Long.valueOf(userMap.get("id").toString());
            User user = userService.findById(userId);
            
            // Check if user has ACCESSOR role
            if (!userService.hasRole(user, Roles.ACCESSOR.getOriginalName())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "User does not have ACCESSOR role. Current roles: " + 
                        user.getRoles().stream().map(r -> r.getName()).toList()
                );
            }
            
            // Check if user is active
            if (!user.getIsActive()) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "User account is not active"
                );
            }
            
            log.info("User {} authenticated successfully via OAuth, fetching assets", user.getEmail());
            return ResponseEntity.ok(jiraIntegrationService.getAvailableAssets());
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
     * Requires: OAuth 2.0 authentication via Bearer token and ASSET_OWNER role
     * 
     * POST /api/jira/request/{accessRequestId}/approve
     */
    @PostMapping("/request/{accessRequestId}/approve")
    public ResponseEntity<Map<String, Object>> approveAccessRequest(
            @PathVariable Long accessRequestId,
            @RequestBody AccessRequestDTO accessRequestDTO,
            @RequestHeader(value = "Authorization", required = true) String authorization) {
        
        log.info("Asset owner approving access request {}", accessRequestId);
        
        try {
            // Extract Bearer token
            if (!authorization.startsWith("Bearer ")) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid authorization header. Must be: Bearer <token>"
                );
            }
            
            String accessToken = authorization.substring(7);
            
            // Authenticate user using OAuth token (maps Account ID → Email → DAM User ID)
            Map<String, Object> authResult = jiraOAuthService.authenticateJiraUser(accessToken);
            Map<String, Object> userMap = (Map<String, Object>) authResult.get("user");
            Long userId = Long.valueOf(userMap.get("id").toString());
            User approver = userService.findById(userId);
            
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
            
            // Approve the request
            accessRequestDTO.setRequestId(accessRequestId);
            accessRequestService.setApprovalStatusOfAccessRequest(
                    accessRequestId,
                    accessRequestDTO,
                    ApprovalStatus.APPROVED
            );
            
            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Access request approved successfully",
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
     * Requires: OAuth 2.0 authentication via Bearer token and ASSET_OWNER role
     * 
     * POST /api/jira/request/{accessRequestId}/reject
     */
    @PostMapping("/request/{accessRequestId}/reject")
    public ResponseEntity<Map<String, Object>> rejectAccessRequest(
            @PathVariable Long accessRequestId,
            @RequestBody AccessRequestDTO accessRequestDTO,
            @RequestHeader(value = "Authorization", required = true) String authorization) {
        
        log.info("Asset owner rejecting access request {}", accessRequestId);
        
        try {
            // Extract Bearer token
            if (!authorization.startsWith("Bearer ")) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid authorization header. Must be: Bearer <token>"
                );
            }
            
            String accessToken = authorization.substring(7);
            
            // Authenticate user using OAuth token (maps Account ID → Email → DAM User ID)
            Map<String, Object> authResult = jiraOAuthService.authenticateJiraUser(accessToken);
            Map<String, Object> userMap = (Map<String, Object>) authResult.get("user");
            Long userId = Long.valueOf(userMap.get("id").toString());
            User approver = userService.findById(userId);
            
            // Check if user has ASSET_OWNER role
            if (!userService.hasRole(approver, Roles.ASSET_OWNER.getOriginalName())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "User does not have ASSET_OWNER role. Only asset owners can reject requests."
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
            
            // Reject the request
            accessRequestDTO.setRequestId(accessRequestId);
            accessRequestService.setApprovalStatusOfAccessRequest(
                    accessRequestId,
                    accessRequestDTO,
                    ApprovalStatus.REJECTED
            );
            
            Map<String, Object> response = Map.of(
                    "success", true,
                    "message", "Access request rejected successfully",
                    "accessRequestId", accessRequestId,
                    "status", "REJECTED"
            );
            
            return ResponseEntity.ok(response);
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
}
