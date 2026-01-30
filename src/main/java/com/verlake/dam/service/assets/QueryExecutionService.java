package com.verlake.dam.service.assets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.entity.Role;
import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.assets.dto.AccessQueryDTO;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.exception.QueryExecutionException;
import com.verlake.dam.service.ai.DataMaskingService;
import com.verlake.dam.service.assets.common.AssetValidationUtils;
import com.verlake.dam.service.audit_trail.AuditTrailService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import com.verlake.dam.utils.DatabaseQueryUtils;
import com.verlake.dam.utils.IpAddressUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Shared service for query execution logic used by both Accessor and Asset Owner controllers
 */
@Service
@Slf4j
public class QueryExecutionService {

    private final DatabaseAccessService databaseAccessService;
    private final UserService userService;
    private final DataMaskingService dataMaskingService;
    private final AuditTrailService auditTrailService;
    private final AssetQueryChangeRequestService assetQueryChangeRequestService;
    private final AssetValidationUtils assetValidationUtils;
    private final AssetService assetService;
    private final ObjectMapper objectMapper;
    private final AccessRequestService accessRequestService;

    public QueryExecutionService(DatabaseAccessService databaseAccessService,
                                 UserService userService,
                                 DataMaskingService dataMaskingService,
                                 AuditTrailService auditTrailService,
                                 AssetQueryChangeRequestService assetQueryChangeRequestService,
                                 AssetValidationUtils assetValidationUtils,
                                 AssetService assetService,
                                 AccessRequestService accessRequestService,
                                 ObjectMapper objectMapper) {
        this.databaseAccessService = databaseAccessService;
        this.userService = userService;
        this.dataMaskingService = dataMaskingService;
        this.auditTrailService = auditTrailService;
        this.assetQueryChangeRequestService = assetQueryChangeRequestService;
        this.assetValidationUtils = assetValidationUtils;
        this.assetService = assetService;
        this.objectMapper = objectMapper;
        this.accessRequestService = accessRequestService;
    }

    /**
     * Execute query for Accessor with access request validation
     */
    public Map<String, Object> executeQueryForAccessor(AccessQueryDTO accessQueryDTO) throws CommonUtils.CryptoException {
        AccessRequest accessRequest = assetValidationUtils.validateAccessRequest(accessQueryDTO.getRequestId());
        AssetCredential credential = assetValidationUtils.validateAssetCredential(accessRequest);
        
        // Check if asset is locked
        assetService.validateAssetNotLocked(accessRequest.getAsset(), true);
        
        // Encrypt temporary credential if needed
        accessRequestService.encryptTemporaryCredential(credential, accessRequest);
        
        assetValidationUtils.validateAccessRequestStatus(accessRequest);

        long startTime = System.currentTimeMillis();
        QueryExecutionContext context = QueryExecutionContext.forAccessor(accessRequest, credential);

        try {
            Map<String, Object> result = executeQueryWithContext(accessQueryDTO, context);
            
            if (accessQueryDTO.isChangeRequest()) {
                assetQueryChangeRequestService.createChangeRequestForAccessor(accessQueryDTO, accessRequest);
            }

            createQueryAuditLog(accessQueryDTO, context, true, null, result, startTime);
            return result;
            
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            log.error("Error executing query for accessor: {}", accessQueryDTO.getRequestId(), e);
            createQueryAuditLog(accessQueryDTO, context, false, errorMessage, null, startTime);
            throw new IllegalArgumentException("Failed to execute query: " + e.getMessage(), e);
        }
    }

    /**
     * Execute query for Asset Owner with ownership validation
     */
    public Map<String, Object> executeQueryForAssetOwner(AccessQueryDTO accessQueryDTO) {
        Asset asset = assetValidationUtils.validateAssetOwnership(accessQueryDTO.getAssetId());
        
        // Check if asset is locked
        assetService.validateAssetNotLocked(asset, false);
        
        AssetCredential credential = assetValidationUtils.validateAssetOwnerCredential(asset);
        
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
            
            // Apply masking to the result structure
            if (result != null) {
                applyDataMaskingToResult(result, context);
            }
            
            return result;
        } catch (Exception e) {
            throw new QueryExecutionException("Failed to execute query: " + e.getMessage(), e);
        }
    }

    /**
     * Apply data masking to result structure (handles both flat and nested formats)
     */
    private void applyDataMaskingToResult(Map<String, Object> result, QueryExecutionContext context) {
        // Check if result has Constants.QUERY_RESULT_FIELD_RESULTS array (nested format)
        if (result.containsKey(Constants.QUERY_RESULT_FIELD_RESULTS)) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resultsList = (List<Map<String, Object>>) result.get(Constants.QUERY_RESULT_FIELD_RESULTS);
            
            if (resultsList != null && !resultsList.isEmpty()) {
                log.debug("Applying masking to nested result structure with {} queries", resultsList.size());
                
                // Apply masking to each query result
                for (Map<String, Object> queryResult : resultsList) {
                    if (queryResult.containsKey(Constants.QUERY_RESULT_FIELD_DATA)) {
                        applyDataMasking(queryResult, context);
                    }
                }
            }
        } 
        // Check if result has direct Constants.QUERY_RESULT_FIELD_DATA field (flat format)
        else if (result.containsKey(Constants.QUERY_RESULT_FIELD_DATA)) {
            log.debug("Applying masking to flat result structure");
            applyDataMasking(result, context);
        } else {
            log.debug("No data field found in result structure, skipping masking");
        }
    }
    
    /**
     * Apply data masking based on context
     */
    private void applyDataMasking(Map<String, Object> result, QueryExecutionContext context) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawResults = (List<Map<String, Object>>) result.get(Constants.QUERY_RESULT_FIELD_DATA);
        
        if (rawResults != null && !rawResults.isEmpty()) {
            User currentUser = userService.getCurrentUser();
            String userEmail = currentUser.getEmail();
            String userRole = context.getUserRole(currentUser);
            List<String> userRoles = context.getUserRoles(currentUser);
            
            log.debug("Applying data masking for user {} (roles: {}) on asset {} with {} rows", 
                    userEmail, userRoles, context.getAsset().getId(), rawResults.size());
            
            List<Map<String, Object>> maskedResults = dataMaskingService.maskQueryResults(
                context.getAsset(), userRole, userEmail, rawResults);
            
            result.put(Constants.QUERY_RESULT_FIELD_DATA, maskedResults);
            boolean maskingApplied = !maskedResults.equals(rawResults);
            result.put("maskingApplied", maskingApplied);
            result.put("maskingInfo", maskingApplied ? 
                "Data has been masked according to security policies" : 
                "No masking policies applied to this data");
            
            if (maskingApplied) {
                log.info("✅ Data masking successfully applied to query results for user {} (role: {}) on asset {}", 
                        userEmail, userRole, context.getAsset().getId());
            } else {
                log.info("ℹ️ No masking policies applicable for user {} (role: {}) on asset {}", 
                        userEmail, userRole, context.getAsset().getId());
            }
        } else {
            log.debug("No data to mask - empty or null results");
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
                List<Map<String, Object>> resultsList = (List<Map<String, Object>>) result.get(Constants.QUERY_RESULT_FIELD_RESULTS);
                if (resultsList != null) {
                    int totalRows = resultsList.stream()
                            .mapToInt(r -> {
                                @SuppressWarnings("unchecked")
                                List<Map<String, Object>> data = (List<Map<String, Object>>) r.get(Constants.QUERY_RESULT_FIELD_DATA);
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
            
            // Create detailed newValue with query and results
            String newValue = DatabaseQueryUtils.createDetailedNewValue(accessQueryDTO.getQuery(), success, result, errorMessage);
            
            AuditTrail auditTrail = AuditTrail.builder()
                    .timestamp(LocalDateTime.now())
                    .user(currentUser.getEmail())
                    .action(context.getAuditAction())
                    .instanceId(String.format("ASSET(%s)", context.getAsset().getName()))
                    .actionMetadata(objectMapper.writeValueAsString(auditDetails))
                    .newValue(newValue)
                    .ipAddress(getCurrentIpAddress())
                    .asset(context.getAsset())
                    .description(accessQueryDTO.getQuery())
                    // readableDescription will be computed at read-time (DTO)
                    .build();
            
            auditTrailService.save(auditTrail);
            
            log.info("Created audit log for {} query execution - User: {}, Asset: {}, Success: {}, Time: {}ms", 
                    context.getExecutionType(), currentUser.getEmail(), context.getAsset().getName(), success, executionTime);
                    
        } catch (Exception e) {
            log.error("Failed to create audit log for query execution", e);
        }
    }

    private String getCurrentIpAddress() {
        return IpAddressUtils.getCurrentIpAddress();
    }

    /**
     * Context class to hold execution-specific information
     */
    @Getter
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

        public static QueryExecutionContext forAccessor(AccessRequest accessRequest, AssetCredential credential) {
            return new QueryExecutionContext(accessRequest.getAsset(), credential, accessRequest, Constants.QUERY_EXECUTION_TYPE_ACCESSOR);
        }

        public static QueryExecutionContext forAssetOwner(Asset asset, AssetCredential credential) {
            return new QueryExecutionContext(asset, credential, null, Constants.QUERY_EXECUTION_TYPE_ASSET_OWNER);
        }

        public String getUserRole(User currentUser) {
            if (currentUser.getRoles().isEmpty()) {
                return Constants.QUERY_EXECUTION_TYPE_ACCESSOR.equals(executionType) ? "Accessor" : "Asset Owner";
            }
            
            // Get all roles as a comma-separated string for masking policy matching
            return currentUser.getRoles().stream()
                    .map(Role::getName)
                    .collect(Collectors.joining(","));
        }
        
        /**
         * Get user roles as a list for more detailed processing
         */
        public List<String> getUserRoles(User currentUser) {
            if (currentUser.getRoles().isEmpty()) {
                return List.of(Constants.QUERY_EXECUTION_TYPE_ACCESSOR.equals(executionType) ? "Accessor" : "Asset Owner");
            }
            
            return currentUser.getRoles().stream()
                    .map(Role::getName)
                    .collect(Collectors.toList());
        }

        public String getAuditAction() {
            return Constants.QUERY_EXECUTION_TYPE_ACCESSOR.equals(executionType) ? 
                   Constants.AUDIT_ACTION_ACCESSOR_QUERY_EXECUTION : 
                   Constants.AUDIT_ACTION_ASSET_OWNER_QUERY_EXECUTION;
        }

        public void addAuditDetails(Map<String, Object> auditDetails, AccessQueryDTO accessQueryDTO) {
            if (Constants.QUERY_EXECUTION_TYPE_ACCESSOR.equals(executionType) && accessRequest != null) {
                auditDetails.put("accessRequestId", accessRequest.getId());
                auditDetails.put("requestId", accessQueryDTO.getRequestId());
            }
        }
    }
}
