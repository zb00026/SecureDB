package com.verlake.dam.service.assets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.entity.assets.*;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.assets.dto.AccessQueryDTO;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.exception.QueryExecutionException;
import com.verlake.dam.service.audit_trail.AuditTrailService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.service.ai.DataMaskingService;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.yarn.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Shared service for query execution logic used by both Developer and Asset Owner controllers
 */
@Service
@Slf4j
public class QueryExecutionService {

    private final DatabaseAccessService databaseAccessService;
    private final UserService userService;
    private final DataMaskingService dataMaskingService;
    private final AuditTrailService auditTrailService;
    private final AssetService assetService;
    private final AccessRequestService accessRequestService;
    private final AssetQueryChangeRequestService assetQueryChangeRequestService;
    private final ObjectMapper objectMapper;

    public QueryExecutionService(DatabaseAccessService databaseAccessService,
                                UserService userService,
                                DataMaskingService dataMaskingService,
                                AuditTrailService auditTrailService,
                                AssetService assetService,
                                AccessRequestService accessRequestService,
                                AssetQueryChangeRequestService assetQueryChangeRequestService) {
        this.databaseAccessService = databaseAccessService;
        this.userService = userService;
        this.dataMaskingService = dataMaskingService;
        this.auditTrailService = auditTrailService;
        this.assetService = assetService;
        this.accessRequestService = accessRequestService;
        this.assetQueryChangeRequestService = assetQueryChangeRequestService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Execute query for Developer with access request validation
     */
    public Map<String, Object> executeQueryForDeveloper(AccessQueryDTO accessQueryDTO) {
        AccessRequest accessRequest = validateAccessRequest(accessQueryDTO);
        AssetCredential credential = validateAssetCredential(accessRequest);
        validateAccessRequestStatus(accessRequest);

        long startTime = System.currentTimeMillis();
        QueryExecutionContext context = QueryExecutionContext.forDeveloper(accessRequest, credential);

        try {
            Map<String, Object> result = executeQueryWithContext(accessQueryDTO, context);
            
            if (accessQueryDTO.isChangeRequest()) {
                assetQueryChangeRequestService.createChangeRequestForDeveloper(accessQueryDTO, accessRequest);
            }

            createQueryAuditLog(accessQueryDTO, context, true, null, result, startTime);
            return result;
            
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            log.error("Error executing query for developer: {}", accessQueryDTO.getRequestId(), e);
            createQueryAuditLog(accessQueryDTO, context, false, errorMessage, null, startTime);
            throw new IllegalArgumentException("Failed to execute query: " + e.getMessage(), e);
        }
    }

    /**
     * Execute query for Asset Owner with ownership validation
     */
    public Map<String, Object> executeQueryForAssetOwner(AccessQueryDTO accessQueryDTO) {
        Asset asset = validateAssetOwnership(accessQueryDTO.getAssetId());
        AssetCredential credential = validateAssetOwnerCredential(asset);
        
        long startTime = System.currentTimeMillis();
        QueryExecutionContext context = QueryExecutionContext.forAssetOwner(asset, credential);

        try {
            Map<String, Object> result = executeQueryWithContext(accessQueryDTO, context);
            
            if (accessQueryDTO.isChangeRequest()) {
                assetQueryChangeRequestService.createChangeRequestForAssetOwner(accessQueryDTO, asset);
            }

            createQueryAuditLog(accessQueryDTO, context, true, null, result, startTime);
            return result;
            
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            log.error("Error executing query for asset owner on asset: {}", accessQueryDTO.getAssetId(), e);
            createQueryAuditLog(accessQueryDTO, context, false, errorMessage, null, startTime);
            throw new IllegalArgumentException("Failed to execute query: " + e.getMessage(), e);
        }
    }

    /**
     * Execute query with the given context (shared logic)
     */
    private Map<String, Object> executeQueryWithContext(AccessQueryDTO accessQueryDTO, QueryExecutionContext context) {
        try {
            Map<String, Object> result = databaseAccessService.executeQueryWithCredentialsDryRun(
                    context.getCredential(), accessQueryDTO.getQuery(), accessQueryDTO.isChangeRequest());
            
            if (result != null && result.containsKey("data")) {
                applyDataMasking(result, context);
            }
            
            return result;
        } catch (Exception e) {
            throw new QueryExecutionException("Failed to execute query: " + e.getMessage(), e);
        }
    }

    /**
     * Apply data masking based on context
     */
    private void applyDataMasking(Map<String, Object> result, QueryExecutionContext context) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawResults = (List<Map<String, Object>>) result.get("data");
        
        if (rawResults != null && !rawResults.isEmpty()) {
            User currentUser = userService.getCurrentUser();
            String userEmail = currentUser.getEmail();
            String userRole = context.getUserRole(currentUser);
            
            List<Map<String, Object>> maskedResults = dataMaskingService.maskQueryResults(
                context.getAsset(), userRole, userEmail, rawResults);
            
            result.put("data", maskedResults);
            result.put("maskingApplied", !maskedResults.equals(rawResults));
            
            log.info("Applied masking to query results for user {} (role: {}) on asset {}", 
                    userEmail, userRole, context.getAsset().getId());
        }
    }

    /**
     * Create comprehensive audit log
     */
    private void createQueryAuditLog(AccessQueryDTO accessQueryDTO, QueryExecutionContext context, 
                                   boolean success, String errorMessage, Map<String, Object> result, long startTime) {
        try {
            User currentUser = userService.getCurrentUser();
            long executionTime = System.currentTimeMillis() - startTime;
            
            // Add query details to audit trail
            Map<String, Object> auditDetails = new HashMap<>();
            auditDetails.put("assetId", context.getAsset().getId());
            auditDetails.put("assetName", context.getAsset().getName());
            auditDetails.put("query", accessQueryDTO.getQuery());
            auditDetails.put("success", success);
            auditDetails.put("executionTimeMs", executionTime);
            auditDetails.put("userRole", context.getUserRole(currentUser));
            auditDetails.put("executionType", context.getExecutionType());
            auditDetails.put("ipAddress", getCurrentIpAddress());
            
            // Add context-specific details
            context.addAuditDetails(auditDetails, accessQueryDTO);
            
            if (success && result != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> resultsList = (List<Map<String, Object>>) result.get("results");
                if (resultsList != null) {
                    int totalRows = resultsList.stream()
                            .mapToInt(r -> {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> data = (List<Map<String, Object>>) r.get("data");
                                return data != null ? data.size() : 0;
                            })
                            .sum();
                    auditDetails.put("rowsReturned", totalRows);
                }
            }
            
            if (!success && errorMessage != null) {
                auditDetails.put("errorMessage", errorMessage);
            }
            
            if (accessQueryDTO.isChangeRequest()) {
                auditDetails.put("isChangeRequest", true);
                auditDetails.put("ticketReference", accessQueryDTO.getTicketReference());
                auditDetails.put("changeDescription", accessQueryDTO.getChangeDescription());
            }
            
            AuditTrail auditTrail = AuditTrail.builder()
                    .timestamp(LocalDateTime.now())
                    .user(currentUser.getEmail())
                    .action(context.getAuditAction())
                    .instanceId(String.format("ASSET(%s)", context.getAsset().getId()))
                    .actionMetadata(objectMapper.writeValueAsString(auditDetails))
                    .newValue(success ? "Query executed successfully" : "Query execution failed")
                    .ipAddress(getCurrentIpAddress())
                    .asset(context.getAsset())
                    .build();
            
            auditTrailService.save(auditTrail);
            
            log.info("Created audit log for {} query execution - User: {}, Asset: {}, Success: {}, Time: {}ms", 
                    context.getExecutionType(), currentUser.getEmail(), context.getAsset().getName(), success, executionTime);
                    
        } catch (Exception e) {
            log.error("Failed to create audit log for query execution", e);
        }
    }

    // Validation methods (shared logic)
    
    private AccessRequest validateAccessRequest(AccessQueryDTO accessQueryDTO) {
        AccessRequest accessRequest = accessRequestService.findById(accessQueryDTO.getRequestId());
        if (accessRequest == null) {
            throw new ResourceNotFoundException("No access request provided");
        }
        return accessRequest;
    }
    
    private AssetCredential validateAssetCredential(AccessRequest accessRequest) {
        AssetCredential credential = accessRequest.getAssetCredential();
        if (credential == null) {
            throw new ResourceNotFoundException("No asset credential found for this access request");
        }
        return credential;
    }
    
    private void validateAccessRequestStatus(AccessRequest accessRequest) {
        if (!accessRequest.getDeveloperApproverStatus().equals(ApprovalStatus.APPROVED) &&
                !accessRequest.getAssetApproverStatus().equals(ApprovalStatus.APPROVED)) {
            throw new IllegalArgumentException("Access request is not approved");
        }

        if (accessRequest.getExpiryDate() != null && accessRequest.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Access request has expired");
        }
    }
    
    private Asset validateAssetOwnership(Long assetId) {
        Asset asset = assetService.findById(assetId);
        if (asset == null) {
            throw new ResourceNotFoundException("Asset not found: " + assetId);
        }
        
        List<Asset> ownedAssets = assetService.getAssetsOwnedByCurrentUser();
        
        boolean isOwner = ownedAssets.stream()
                .anyMatch(ownedAsset -> ownedAsset.getId().equals(assetId));
        
        if (!isOwner) {
            throw new IllegalArgumentException("User does not have ownership rights to asset: " + assetId);
        }
        
        return asset;
    }
    
    private AssetCredential validateAssetOwnerCredential(Asset asset) {
        List<AssetCredential> credentials = assetService.getAssignedCredentials();
        AssetCredential credential = credentials.stream()
                .filter(cred -> cred.getAsset().getId().equals(asset.getId()))
                .findFirst()
                .orElse(null);
        
        if (credential == null) {
            throw new ResourceNotFoundException("No asset credential found for asset: " + asset.getId());
        }
        
        return credential;
    }

    private String getCurrentIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                    .currentRequestAttributes();
            return attributes.getRequest().getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Context class to hold execution-specific information
     */
    public static class QueryExecutionContext {
        private final Asset asset;
        private final AssetCredential credential;
        private final AccessRequest accessRequest; // null for Asset Owner
        private final String executionType;

        private QueryExecutionContext(Asset asset, AssetCredential credential, AccessRequest accessRequest, String executionType) {
            this.asset = asset;
            this.credential = credential;
            this.accessRequest = accessRequest;
            this.executionType = executionType;
        }

        public static QueryExecutionContext forDeveloper(AccessRequest accessRequest, AssetCredential credential) {
            return new QueryExecutionContext(accessRequest.getAsset(), credential, accessRequest, Constants.QUERY_EXECUTION_TYPE_DEVELOPER);
        }

        public static QueryExecutionContext forAssetOwner(Asset asset, AssetCredential credential) {
            return new QueryExecutionContext(asset, credential, null, Constants.QUERY_EXECUTION_TYPE_ASSET_OWNER);
        }

        public Asset getAsset() {
            return asset;
        }

        public AssetCredential getCredential() {
            return credential;
        }

        public AccessRequest getAccessRequest() {
            return accessRequest;
        }

        public String getExecutionType() {
            return executionType;
        }

        public String getUserRole(User currentUser) {
            if (Constants.QUERY_EXECUTION_TYPE_DEVELOPER.equals(executionType)) {
                return currentUser.getRoles().isEmpty() ? "Developer" : 
                       currentUser.getRoles().iterator().next().getName();
            } else {
                return currentUser.getRoles().isEmpty() ? "Asset Owner" : 
                       currentUser.getRoles().iterator().next().getName();
            }
        }

        public String getAuditAction() {
            return Constants.QUERY_EXECUTION_TYPE_DEVELOPER.equals(executionType) ? 
                   Constants.AUDIT_ACTION_DEVELOPER_QUERY_EXECUTION : 
                   Constants.AUDIT_ACTION_ASSET_OWNER_QUERY_EXECUTION;
        }

        public void addAuditDetails(Map<String, Object> auditDetails, AccessQueryDTO accessQueryDTO) {
            if (Constants.QUERY_EXECUTION_TYPE_DEVELOPER.equals(executionType) && accessRequest != null) {
                auditDetails.put("accessRequestId", accessRequest.getId());
                auditDetails.put("requestId", accessQueryDTO.getRequestId());
            }
        }
    }
}
