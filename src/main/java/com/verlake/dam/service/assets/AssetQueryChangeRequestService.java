package com.verlake.dam.service.assets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.assets.AssetQueryChangeRequest;
import com.verlake.dam.entity.assets.dto.AccessQueryDTO;
import com.verlake.dam.entity.assets.dto.AssetQueryChangeRequestDTO;
import com.verlake.dam.entity.firebase.NotificationMessage;
import com.verlake.dam.entity.firebase.NotificationTask;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.enums.EmailType;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.exception.AccessRequestExpiredException;
import com.verlake.dam.exception.AssetQueryChangeRequestNotFoundException;
import com.verlake.dam.exception.QueryExecutionException;
import com.verlake.dam.repository.NotificationTaskRepository;
import com.verlake.dam.repository.assets.AssetApproversRepository;
import com.verlake.dam.repository.assets.AssetQueryChangeRequestRepository;
import com.verlake.dam.service.ai.DataMaskingService;
import com.verlake.dam.service.audit_trail.AuditTrailService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.Constants;
import com.verlake.dam.utils.DatabaseQueryUtils;
import com.verlake.dam.utils.IpAddressUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.yarn.exceptions.ResourceNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class AssetQueryChangeRequestService {

    private final AssetQueryChangeRequestRepository assetQueryChangeRequestRepository;
    private final AssetService assetService;
    private final AssetApproversRepository assetApproversRepository;
    private final NotificationTaskRepository notificationTaskRepository;
    private final DatabaseAccessService databaseAccessService;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final AccessRequestService accessRequestService;
    private final AuditTrailService auditTrailService;
    private final DataMaskingService dataMaskingService;

    public AssetQueryChangeRequestService(AssetQueryChangeRequestRepository assetQueryChangeRequestRepository,
            AssetService assetService,
            AssetApproversRepository assetApproversRepository,
            NotificationTaskRepository notificationTaskRepository, DatabaseAccessService databaseAccessService,
            UserService userService, AccessRequestService accessRequestService, AuditTrailService auditTrailService,
            DataMaskingService dataMaskingService,
            ObjectMapper objectMapper) {
        this.assetQueryChangeRequestRepository = assetQueryChangeRequestRepository;
        this.assetService = assetService;
        this.assetApproversRepository = assetApproversRepository;
        this.notificationTaskRepository = notificationTaskRepository;
        this.databaseAccessService = databaseAccessService;
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.accessRequestService = accessRequestService;
        this.auditTrailService = auditTrailService;
        this.dataMaskingService = dataMaskingService;
    }

    /**
     * Find all asset query change requests
     */
    public List<AssetQueryChangeRequest> findAll() {
        log.debug("Finding all asset query change requests");
        return assetQueryChangeRequestRepository.findAll();
    }

    /**
     * Find asset query change request by ID
     */
    public Optional<AssetQueryChangeRequest> findById(Long id) {
        log.debug("Finding asset query change request by id: {}", id);
        return assetQueryChangeRequestRepository.findById(id);
    }

    /**
     * Find asset query change request by ID or throw exception
     */
    public AssetQueryChangeRequest findByIdOrThrow(Long id) {
        log.debug("Finding asset query change request by id or throw: {}", id);
        return assetQueryChangeRequestRepository.findById(id)
                .orElseThrow(() -> new AssetQueryChangeRequestNotFoundException(id));
    }

    /**
     * Find asset query change request by ticket reference
     */
    public Optional<AssetQueryChangeRequest> findByTicketReference(String ticketReference) {
        log.debug("Finding asset query change request by ticket reference: {}", ticketReference);
        return assetQueryChangeRequestRepository.findByTicketReference(ticketReference);
    }

    /**
     * Search asset query change requests by ticket reference pattern
     */
    public List<AssetQueryChangeRequest> findByTicketReferenceContaining(String pattern) {
        log.debug("Finding asset query change requests by ticket reference containing: {}", pattern);
        return assetQueryChangeRequestRepository.findByTicketReferenceContaining(pattern);
    }

    /**
     * Search asset query change requests by change description pattern
     */
    public List<AssetQueryChangeRequest> findByChangeDescriptionContaining(String pattern) {
        log.debug("Finding asset query change requests by change description containing: {}", pattern);
        return assetQueryChangeRequestRepository.findByChangeDescriptionContaining(pattern);
    }

    /**
     * Search asset query change requests by query pattern
     */
    public List<AssetQueryChangeRequest> findByQueryContaining(String pattern) {
        log.debug("Finding asset query change requests by query containing: {}", pattern);
        return assetQueryChangeRequestRepository.findByQueryContaining(pattern);
    }

    /**
     * Search across multiple fields
     */
    public List<AssetQueryChangeRequest> searchByFields(String ticketReference, String description, String query) {
        log.debug("Searching asset query change requests by fields - ticket: {}, description: {}, query: {}",
                ticketReference, description, query);
        return assetQueryChangeRequestRepository.searchByFields(ticketReference, description, query);
    }

    @Transactional(readOnly = true)
    public List<AssetQueryChangeRequestDTO> getAssetChangeRequests() {
        log.debug("Getting asset query change requests for current user");
        List<Asset> assets = assetService.getAssetsOwnedByCurrentUser();
        List<AssetQueryChangeRequest> requests = assetQueryChangeRequestRepository.findByAssetIn(assets);
        return requests.stream()
                .map(AssetQueryChangeRequestDTO::fromEntity)
                .toList();
    }

    public AssetQueryChangeRequest getAssetChangeRequest(Long changeRequestId) {
        log.debug("Getting asset query change request by id: {}", changeRequestId);
        return assetQueryChangeRequestRepository.findById(changeRequestId)
                .orElseThrow(() -> new AssetQueryChangeRequestNotFoundException(changeRequestId));
    }

    /**
     * Save asset query change request
     */
    @Transactional
    public AssetQueryChangeRequest save(AssetQueryChangeRequest assetQueryChangeRequest) {
        log.debug("Saving asset query change request: {}", assetQueryChangeRequest);
        return assetQueryChangeRequestRepository.save(assetQueryChangeRequest);
    }

    /**
     * Update asset query change request
     */
    @Transactional
    public AssetQueryChangeRequest update(Long id, AssetQueryChangeRequest updatedRequest) {
        log.debug("Updating asset query change request with id: {}", id);

        AssetQueryChangeRequest existingRequest = findByIdOrThrow(id);

        // Update fields
        if (updatedRequest.getTicketReference() != null) {
            existingRequest.setTicketReference(updatedRequest.getTicketReference());
        }
        if (updatedRequest.getChangeDescription() != null) {
            existingRequest.setChangeDescription(updatedRequest.getChangeDescription());
        }
        if (updatedRequest.getQuery() != null) {
            existingRequest.setQuery(updatedRequest.getQuery());
        }

        return assetQueryChangeRequestRepository.save(existingRequest);
    }

    /**
     * Delete asset query change request by ID
     */
    @Transactional
    public void deleteById(Long id) {
        log.debug("Deleting asset query change request with id: {}", id);
        if (!assetQueryChangeRequestRepository.existsById(id)) {
            throw new AssetQueryChangeRequestNotFoundException(id);
        }
        assetQueryChangeRequestRepository.deleteById(id);
    }

    /**
     * Check if asset query change request exists by ticket reference
     */
    public boolean existsByTicketReference(String ticketReference) {
        log.debug("Checking if asset query change request exists by ticket reference: {}", ticketReference);
        return assetQueryChangeRequestRepository.existsByTicketReference(ticketReference);
    }

    /**
     * Count all asset query change requests
     */
    public long count() {
        return assetQueryChangeRequestRepository.count();
    }



    public AssetQueryChangeRequest setApprovalAssetChangeRequest(AccessQueryDTO changeRequestDTO) {
        log.debug("Setting approval for asset query change request by id: {}", changeRequestDTO.getRequestId());
        AssetQueryChangeRequest assetQueryChangeRequest = getAssetChangeRequest(changeRequestDTO.getRequestId());
        assetQueryChangeRequest.setApprovalStatus(changeRequestDTO.getApprovalStatus());
        assetQueryChangeRequest.setRejectReason(changeRequestDTO.getRejectReason());
        
        User currentUser = userService.getCurrentUser();
        
        if (changeRequestDTO.getApprovalStatus() == ApprovalStatus.REJECTED) {
            assetQueryChangeRequest.setApprovalStatus(ApprovalStatus.REJECTED);
        } else {
            AssetCredential myCredential = assetService
                    .findOwnerCredentialByAssetId(assetQueryChangeRequest.getAsset().getId());
            long startTime = System.currentTimeMillis();
            boolean querySuccess = true;
            if (myCredential == null) {
                throw new ResourceNotFoundException("No asset credential found for this asset");
            }
            try {
                Map<String, Object> result = databaseAccessService.executeQueryWithCredentials(myCredential,
                        changeRequestDTO.getQuery(), changeRequestDTO.isChangeRequest());
                
                // Apply data masking to the result
                if (result != null) {
                    applyDataMaskingToResultForAsset(result, assetQueryChangeRequest.getAsset());
                }
                
                assetQueryChangeRequest.setQuery(changeRequestDTO.getQuery());
                // Create successful audit log
                createQueryAuditLog(changeRequestDTO, assetQueryChangeRequest.getAsset(), myCredential, querySuccess,
                        null, result, startTime);

            } catch (Exception e) {
                String errorMessage = e.getMessage();
                log.error("Error executing query for access request: {}", changeRequestDTO.getRequestId(), e);

                // Create failed audit log
                createQueryAuditLog(changeRequestDTO, assetQueryChangeRequest.getAsset(), myCredential, querySuccess,
                        errorMessage, null,
                        startTime);

                throw new IllegalArgumentException("Failed to execute query: " + e.getMessage(), e);
            }
        }
        
        AssetQueryChangeRequest savedRequest = assetQueryChangeRequestRepository.save(assetQueryChangeRequest);
        
        // Send approval/rejection notification to the requestor
        Map<String, String> notificationData = new HashMap<>();
        notificationData.put(Constants.EMAIL_VAR_CHANGE_REQUEST_ID, savedRequest.getId().toString());
        notificationData.put(Constants.EMAIL_VAR_ASSET_ID, savedRequest.getAsset().getId().toString());
        notificationData.put(Constants.EMAIL_VAR_ASSET_NAME, savedRequest.getAsset().getName());
        notificationData.put(Constants.EMAIL_VAR_ASSET_DESCRIPTION, savedRequest.getAsset().getDescription());
        notificationData.put(Constants.EMAIL_VAR_REQUESTOR_NAME,
                savedRequest.getRequestor().getFirstName() + " " + savedRequest.getRequestor().getLastName());
        notificationData.put(Constants.EMAIL_VAR_APPROVER_NAME,
                currentUser.getFirstName() + " " + currentUser.getLastName());
        notificationData.put(Constants.EMAIL_VAR_APPROVAL_STATUS, changeRequestDTO.getApprovalStatus().name());
        notificationData.put(Constants.EMAIL_VAR_TICKET_REFERENCE, savedRequest.getTicketReference() != null ? savedRequest.getTicketReference() : "");
        notificationData.put(Constants.EMAIL_VAR_CHANGE_DESCRIPTION, savedRequest.getChangeDescription() != null ? savedRequest.getChangeDescription() : "");
        notificationData.put(Constants.EMAIL_VAR_QUERY, savedRequest.getQuery() != null ? savedRequest.getQuery() : "");
        notificationData.put(Constants.EMAIL_VAR_REJECT_REASON, savedRequest.getRejectReason() != null ? savedRequest.getRejectReason() : "");
        notificationData.put(Constants.EMAIL_VAR_MESSAGE_TYPE, "1"); //1 : success, 0: fail
        
        try {
            sendChangeRequestApprovalNotifications(currentUser, savedRequest.getRequestor(), savedRequest.getAsset(),
                    notificationData, changeRequestDTO.getApprovalStatus());
        } catch (Exception e) {
            log.error("Failed to send approval notification for change request: {}", savedRequest.getId(), e);
        }
        
        return savedRequest;
    }

    public Map<String, Object> runQueryFromAccessor(AccessQueryDTO accessQueryDTO) {
        AccessRequest accessRequest = validateAccessRequest(accessQueryDTO);
        AssetCredential devCredential = validateAssetCredential(accessRequest);
        validateAccessRequestStatus(accessRequest);

        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> result = executeQueryWithMasking(accessQueryDTO, accessRequest, devCredential);
            
            if (accessQueryDTO.isChangeRequest()) {
                createChangeRequestForAccessor(accessQueryDTO, accessRequest);
            }

            createQueryAuditLog(accessQueryDTO, accessRequest.getAsset(), devCredential, true, null, result, startTime);
            return result;
            
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            log.error("Error executing query for access request: {}", accessQueryDTO.getRequestId(), e);
            createQueryAuditLog(accessQueryDTO, accessRequest.getAsset(), devCredential, false, errorMessage, null, startTime);
            throw new IllegalArgumentException("Failed to execute query: " + e.getMessage(), e);
        }
    }

    /**
     * Validate access request
     */
    private AccessRequest validateAccessRequest(AccessQueryDTO accessQueryDTO) {
        AccessRequest accessRequest = accessRequestService.findById(accessQueryDTO.getRequestId());
        if (accessRequest == null) {
            throw new ResourceNotFoundException("No access request provided");
        }
        return accessRequest;
    }
    
    /**
     * Validate asset credential
     */
    private AssetCredential validateAssetCredential(AccessRequest accessRequest) {
        AssetCredential devCredential = accessRequest.getAssetCredential();
        if (devCredential == null) {
            throw new ResourceNotFoundException("No asset credential found for this access request");
        }
        return devCredential;
    }
    
    /**
     * Validate access request status
     */
    private void validateAccessRequestStatus(AccessRequest accessRequest) {
        if (!accessRequest.getAccessorApproverStatus().equals(ApprovalStatus.APPROVED) &&
                !accessRequest.getAssetApproverStatus().equals(ApprovalStatus.APPROVED)) {
            throw new IllegalArgumentException("Access request is not approved");
        }

        if (accessRequest.getExpiryDate() != null && accessRequest.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new AccessRequestExpiredException(
                    "Access request has expired. Please create a new access request.");
        }
    }
    
    /**
     * Execute query with masking
     */
    private Map<String, Object> executeQueryWithMasking(AccessQueryDTO accessQueryDTO, AccessRequest accessRequest, AssetCredential devCredential) {
        try {
            Map<String, Object> result = databaseAccessService.executeQueryWithCredentialsDryRun(devCredential, accessQueryDTO.getQuery(), accessQueryDTO.isChangeRequest());
            
            if (result != null) {
                applyDataMaskingToResult(result, accessRequest);
            }
            
            return result;
        } catch (Exception e) {
            throw new QueryExecutionException("Failed to execute query: " + e.getMessage(), e);
        }
    }
    
    /**
     * Apply data masking to result structure (handles both flat and nested formats)
     */
    private void applyDataMaskingToResult(Map<String, Object> result, AccessRequest accessRequest) {
        // Check if result has Constants.QUERY_RESULT_FIELD_RESULTS array (nested format)
        if (result.containsKey(Constants.QUERY_RESULT_FIELD_RESULTS)) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resultsList = (List<Map<String, Object>>) result.get(Constants.QUERY_RESULT_FIELD_RESULTS);
            
            if (resultsList != null && !resultsList.isEmpty()) {
                log.debug("Applying masking to nested result structure with {} queries", resultsList.size());
                
                // Apply masking to each query result
                for (Map<String, Object> queryResult : resultsList) {
                    if (queryResult.containsKey(Constants.QUERY_RESULT_FIELD_DATA)) {
                        applyDataMasking(queryResult, accessRequest);
                    }
                }
            }
        } 
        // Check if result has direct Constants.QUERY_RESULT_FIELD_DATA field (flat format)
        else if (result.containsKey(Constants.QUERY_RESULT_FIELD_DATA)) {
            log.debug("Applying masking to flat result structure");
            applyDataMasking(result, accessRequest);
        } else {
            log.debug("No data field found in result structure, skipping masking");
        }
    }
    
    /**
     * Apply data masking to query results
     */
    private void applyDataMasking(Map<String, Object> result, AccessRequest accessRequest) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawResults = (List<Map<String, Object>>) result.get(Constants.QUERY_RESULT_FIELD_DATA);
        
        if (rawResults != null && !rawResults.isEmpty()) {
            User currentUser = userService.getCurrentUser();
            String userEmail = currentUser.getEmail();
            
            // Get all user roles as comma-separated string
            String userRole = currentUser.getRoles().isEmpty() ? "Accessor" : 
                            currentUser.getRoles().stream()
                                    .map(role -> role.getName())
                                    .collect(java.util.stream.Collectors.joining(","));
            
            List<Map<String, Object>> maskedResults = dataMaskingService.maskQueryResults(
                accessRequest.getAsset(), userRole, userEmail, rawResults);
            
            result.put(Constants.QUERY_RESULT_FIELD_DATA, maskedResults);
            result.put("maskingApplied", !maskedResults.equals(rawResults));
            
            log.info("Applied masking to query results for user {} (role: {}) on asset {}", 
                    userEmail, userRole, accessRequest.getAsset().getId());
        }
    }
    
    /**
     * Apply data masking to result structure for Asset (handles both flat and nested formats)
     */
    private void applyDataMaskingToResultForAsset(Map<String, Object> result, Asset asset) {
        // Check if result has Constants.QUERY_RESULT_FIELD_RESULTS array (nested format)
        if (result.containsKey(Constants.QUERY_RESULT_FIELD_RESULTS)) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resultsList = (List<Map<String, Object>>) result.get(Constants.QUERY_RESULT_FIELD_RESULTS);
            
            if (resultsList != null && !resultsList.isEmpty()) {
                log.debug("Applying masking to nested result structure with {} queries", resultsList.size());
                
                // Apply masking to each query result
                for (Map<String, Object> queryResult : resultsList) {
                    if (queryResult.containsKey(Constants.QUERY_RESULT_FIELD_DATA)) {
                        applyDataMaskingForAsset(queryResult, asset);
                    }
                }
            }
        } 
        // Check if result has direct Constants.QUERY_RESULT_FIELD_DATA field (flat format)
        else if (result.containsKey(Constants.QUERY_RESULT_FIELD_DATA)) {
            log.debug("Applying masking to flat result structure");
            applyDataMaskingForAsset(result, asset);
        } else {
            log.debug("No data field found in result structure, skipping masking");
        }
    }
    
    /**
     * Apply data masking to query results using Asset directly
     */
    private void applyDataMaskingForAsset(Map<String, Object> result, Asset asset) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawResults = (List<Map<String, Object>>) result.get(Constants.QUERY_RESULT_FIELD_DATA);
        
        if (rawResults != null && !rawResults.isEmpty()) {
            User currentUser = userService.getCurrentUser();
            String userEmail = currentUser.getEmail();
            
            // Get all user roles as comma-separated string
            String userRole = currentUser.getRoles().isEmpty() ? "Asset Owner" : 
                            currentUser.getRoles().stream()
                                    .map(role -> role.getName())
                                    .collect(java.util.stream.Collectors.joining(","));
            
            List<Map<String, Object>> maskedResults = dataMaskingService.maskQueryResults(
                asset, userRole, userEmail, rawResults);
            
            result.put(Constants.QUERY_RESULT_FIELD_DATA, maskedResults);
            result.put("maskingApplied", !maskedResults.equals(rawResults));
            
            log.info("Applied masking to query results for user {} (role: {}) on asset {}", 
                    userEmail, userRole, asset.getId());
        }
    }
    
    /**
     * Create change request for Accessor
     */
    public void createChangeRequestForAccessor(AccessQueryDTO accessQueryDTO, AccessRequest accessRequest) {
        AssetQueryChangeRequest changeRequest = new AssetQueryChangeRequest();
        changeRequest.setTicketReference(accessQueryDTO.getTicketReference());
        changeRequest.setChangeDescription(accessQueryDTO.getChangeDescription());
        changeRequest.setQuery(accessQueryDTO.getQuery());

        User currentUser = userService.getCurrentUser();
        Asset asset = accessRequest.getAsset();

        saveChangeRequestWithNotifications(changeRequest, asset, currentUser);
    }

    private String getCurrentIpAddress() {
        return IpAddressUtils.getCurrentIpAddress();
    }

    private void createQueryAuditLog(AccessQueryDTO accessQueryDTO, Asset asset,
            AssetCredential credential, boolean success, String errorMessage,
            Map<String, Object> result, long startTime) {
        try {
            long executionTime = System.currentTimeMillis() - startTime;
            String username = getCurrentUsername();
            String ipAddress = getCurrentIpAddress();

            // Check if this is a DELETE query for notifications
            String trimmedQuery = accessQueryDTO.getQuery().trim().toUpperCase();
            boolean isDeleteQuery = trimmedQuery.startsWith("DELETE");

            // Create audit metadata
            Map<String, Object> auditMetadata = new HashMap<>();
            auditMetadata.put(Constants.AUDIT_FIELD_REQUEST_ID, accessQueryDTO.getRequestId());
            auditMetadata.put(Constants.AUDIT_FIELD_ASSET_ID, asset.getId());
            auditMetadata.put(Constants.EMAIL_VAR_ASSET_NAME, asset.getName());
            auditMetadata.put(Constants.EMAIL_VAR_DATABASE_TYPE, asset.getDatabaseType().toString());
            auditMetadata.put(Constants.EMAIL_VAR_HOST_URL, asset.getHostUrl());
            auditMetadata.put(Constants.AUDIT_FIELD_USERNAME, credential.getUsername());
            auditMetadata.put(Constants.EMAIL_VAR_QUERY, accessQueryDTO.getQuery());
            auditMetadata.put(Constants.AUDIT_FIELD_EXECUTION_TIME_MS, executionTime);
            auditMetadata.put(Constants.AUDIT_FIELD_SUCCESS, success);

            if (!success && errorMessage != null) {
                auditMetadata.put(Constants.AUDIT_FIELD_ERROR_MESSAGE, errorMessage);
            }

            if (success && result != null) {
                Object dataObj = result.get(Constants.QUERY_RESULT_FIELD_DATA);
                if (dataObj instanceof List<?> data) {
                    auditMetadata.put(Constants.AUDIT_FIELD_ROW_COUNT, data.size());
                }
                
                Object headersObj = result.get("headers");
                if (headersObj instanceof List<?> headers) {
                    auditMetadata.put(Constants.AUDIT_FIELD_COLUMN_COUNT, headers.size());
                }
            }

            // Create instance ID for query execution
            String instanceId = String.format("QUERY_EXECUTION(%s)", accessQueryDTO.getRequestId());

            // Create detailed newValue with query and results
            String newValue = DatabaseQueryUtils.createDetailedNewValue(accessQueryDTO.getQuery(), success, result, errorMessage);
            
                AuditTrail audit = AuditTrail.builder()
                    .timestamp(LocalDateTime.now())
                    .user(username)
                    .action(success ? Constants.AUDIT_ACTION_TYPE_QUERY_EXECUTED : Constants.AUDIT_ACTION_TYPE_QUERY_FAILED)
                    .instanceId(instanceId)
                    .actionMetadata(objectMapper.writeValueAsString(auditMetadata))
                    .previousValue(null) // No previous value for query execution
                    .newValue(newValue)
                    .ipAddress(ipAddress)
                    .asset(asset)
                    .description(accessQueryDTO.getQuery())
                    // readableDescription will be computed at read-time (DTO)
                    .build();

            auditTrailService.save(audit);

            // Send DELETE query alert to asset owners if it's a successful DELETE operation
            if (success && isDeleteQuery && !credential.getUser().getRoles().stream()
                    .anyMatch(role -> role.getName().equals(Roles.ASSET_OWNER.getOriginalName()))) {
                sendDeleteQueryAlert(asset, accessQueryDTO.getQuery(), result);
            }

            log.info("Audit log created for query execution: requestId={}, success={}, executionTime={}ms",
                    accessQueryDTO.getRequestId(), success, executionTime);

        } catch (Exception e) {
            log.error("Failed to create audit log for query execution: {}", e.getMessage(), e);
        }
    }

    private void sendDeleteQueryAlert(Asset asset,
            String query, Map<String, Object> result) {
        try {
            // Get asset owners
            List<User> assetOwners = assetService.getAssetOwners(asset);

            // Extract table name from DELETE query (basic parsing)
            String tableName = extractTableNameFromDeleteQuery(query);

            // Get affected rows count
            String affectedRows = "Unknown";
            if (result != null && result.get(Constants.QUERY_RESULT_FIELD_DATA) != null) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) result.get(Constants.QUERY_RESULT_FIELD_DATA);
                if (!data.isEmpty() && data.get(0).containsKey("Affected Rows")) {
                    affectedRows = String.valueOf(data.get(0).get("Affected Rows"));
                }
            }

            // Prepare notification data for DELETE alert
            Map<String, String> notificationData = new HashMap<>();
            notificationData.put(Constants.EMAIL_VAR_ASSET_NAME, asset.getName());
            notificationData.put(Constants.EMAIL_VAR_DATABASE_TYPE,
                    asset.getDatabaseType().toString());
            notificationData.put(Constants.EMAIL_VAR_HOST_URL, asset.getHostUrl());
            notificationData.put(Constants.EMAIL_VAR_EXECUTOR_NAME, getCurrentUsername());
            notificationData.put(Constants.EMAIL_VAR_EXECUTION_TIME, LocalDateTime.now().toString());
            notificationData.put(Constants.EMAIL_VAR_TABLE_NAME, tableName);
            notificationData.put(Constants.EMAIL_VAR_AFFECTED_ROWS, affectedRows);
            notificationData.put(Constants.EMAIL_VAR_QUERY, query);

            // Send notification to each asset owner using NotificationTask
            for (User owner : assetOwners) {
                notificationData.put(Constants.EMAIL_VAR_OWNER_NAME, owner.getFirstName() + " " + owner.getLastName());

                createDeleteQueryNotificationTask(asset, owner, notificationData);
            }

            log.info("DELETE query alert notification tasks created for asset: {}, to {} owners",
                    asset.getName(), assetOwners.size());

        } catch (Exception e) {
            log.error("Failed to create DELETE query alert notifications: {}", e.getMessage(), e);
        }
    }

    private String extractTableNameFromDeleteQuery(String query) {
        try {
            // Basic parsing to extract table name from DELETE query
            // Example: "DELETE FROM users WHERE id = 1" -> "users"
            String upperQuery = query.trim().toUpperCase();

            if (upperQuery.startsWith("DELETE FROM")) {
                String afterFrom = query.substring(upperQuery.indexOf("FROM") + 4).trim();
                String[] parts = afterFrom.split("\\s+");
                if (parts.length > 0) {
                    // Remove any schema prefix (e.g., "database.table" -> "table")
                    String tableName = parts[0];
                    if (tableName.contains(".")) {
                        String[] tableParts = tableName.split("\\.");
                        return tableParts[tableParts.length - 1];
                    }
                    return tableName;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract table name from DELETE query: {}", e.getMessage());
        }
        return "Unknown";
    }

    private String getCurrentUsername() {
        try {
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
                if (principal instanceof Jwt) {
                    return ((Jwt) principal).getClaimAsString("email");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get current username: {}", e.getMessage());
        }
        return "system";
    }

    private void createDeleteQueryNotificationTask(Asset asset, User owner,
            Map<String, String> notificationData) throws Exception {
        NotificationMessage notificationMessage = new NotificationMessage();
        notificationMessage.setTitle("DELETE Query Alert - " + asset.getName());
        notificationMessage.setBody(String.format("DELETE operation executed on %s by %s",
                asset.getName(), getCurrentUsername()));
        notificationMessage.setData(notificationData);
        notificationMessage.setTopic(Constants.DAM_NOTIFICATION_TOPIC);

        // Create and save notification task
        NotificationTask task = new NotificationTask();
        task.setReceiver(owner);
        task.setSender(userService.getCurrentUser());
        task.setAsset(asset);
        task.setNotificationMessage(notificationMessage.toJson());
        task.setEmailType(EmailType.DELETE_QUERY_ALERT);
        notificationTaskRepository.save(task);
    }

    /**
     * Save asset query change request with asset and requestor, and send
     * notifications
     */
    @Transactional
    public AssetQueryChangeRequest saveChangeRequestWithNotifications(AssetQueryChangeRequest assetQueryChangeRequest,
            Asset asset, User requestor) {
        log.debug("Saving asset query change request with notifications: {}", assetQueryChangeRequest);

        // Set asset and requestor
        assetQueryChangeRequest.setAsset(asset);
        assetQueryChangeRequest.setRequestor(requestor);

        // Save the request
        AssetQueryChangeRequest savedRequest = assetQueryChangeRequestRepository.save(assetQueryChangeRequest);

        // Send notifications to asset owners and approvers
        sendChangeReqNotifyToAssetOwners(savedRequest, asset, requestor);

        return savedRequest;
    }

    private void sendChangeReqNotifyToAssetOwners(AssetQueryChangeRequest changeRequest,
            Asset asset, User requestor) {
        log.debug("Sending notifications for asset query change request: {}", changeRequest.getId());

        // Get asset owners
        List<User> assetOwners = assetService.getAssetOwners(asset);

        // Prepare notification data
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put(Constants.EMAIL_VAR_CHANGE_REQUEST_ID, changeRequest.getId().toString());
        notificationData.put(Constants.EMAIL_VAR_ASSET_ID, asset.getId().toString());
        notificationData.put(Constants.EMAIL_VAR_ASSET_NAME, asset.getName());
        notificationData.put(Constants.EMAIL_VAR_ASSET_DESCRIPTION, asset.getDescription());
        notificationData.put(Constants.EMAIL_VAR_REQUESTOR_NAME, requestor.getFirstName() + " " + requestor.getLastName());
        notificationData.put(Constants.EMAIL_VAR_TICKET_REFERENCE,
                changeRequest.getTicketReference() != null ? changeRequest.getTicketReference() : "");
        notificationData.put(Constants.EMAIL_VAR_CHANGE_DESCRIPTION,
                changeRequest.getChangeDescription() != null ? changeRequest.getChangeDescription() : "");
        notificationData.put(Constants.EMAIL_VAR_QUERY, changeRequest.getQuery() != null ? changeRequest.getQuery() : "");
        notificationData.put(Constants.EMAIL_VAR_MESSAGE_TYPE, "1"); // 1 : success, 0: fail

        // Send notifications to asset owners
        for (User user : assetOwners) {
            sendChangeRequestNotificationToAssetOwner(user, requestor, asset, notificationData);
        }

    }

    private void sendChangeRequestNotificationToAssetOwner(User receiver, User requestor, Asset asset,
            Map<String, Object> notificationData) {
        try {
            NotificationMessage notificationMessage = new NotificationMessage();
            notificationMessage.setTitle("Asset Query Change Request");
            notificationMessage.setBody(String.format("%s %s has executed a change request query on %s",
                    requestor.getFirstName(), requestor.getLastName(), asset.getName()));

            // Convert Map<String, Object> to Map<String, String>
            Map<String, String> messageData = new HashMap<>();
            for (Map.Entry<String, Object> entry : notificationData.entrySet()) {
                messageData.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
            }
            messageData.put(Constants.NOTIFY_DATA_ATTR_RECEIVER_ID, receiver.getId().toString());

            notificationMessage.setData(messageData);
            notificationMessage.setTopic(Constants.DAM_NOTIFICATION_TOPIC);

            // Create and save notification task
            NotificationTask task = new NotificationTask();
            task.setReceiver(receiver);
            task.setSender(requestor);
            task.setAsset(asset);
            task.setNotificationMessage(notificationMessage.toJson());
            task.setEmailType(EmailType.ASSET_QUERY_CHANGE_REQUEST_NOTIFY);
            notificationTaskRepository.save(task);

            log.debug("Created notification task for asset query change request to user: {}", receiver.getEmail());
        } catch (Exception e) {
            log.error("Failed to create notification for asset query change request to user: {}", receiver.getEmail(),
                    e);
        }
    }

    private void sendChangeRequestApprovalNotifications(User approver, User receiver, Asset asset,
            Map<String, String> notificationData, ApprovalStatus approvalStatus) throws Exception {
        NotificationMessage notificationMessage = new NotificationMessage();
        notificationMessage.setTitle("Approval Result of Asset Query Change Request");
        
        if (approvalStatus == ApprovalStatus.APPROVED) {
            notificationMessage.setBody(String.format("""
                    %s %s has approved your query change request for asset '%s'
                    Ticket Reference: %s
                    Change Description: %s
                    Your query has been executed successfully.
                    """,
                    approver.getFirstName(),
                    approver.getLastName(),
                    asset.getName(),
                    notificationData.get(Constants.EMAIL_VAR_TICKET_REFERENCE),
                    notificationData.get(Constants.EMAIL_VAR_CHANGE_DESCRIPTION)));
        } else if (approvalStatus == ApprovalStatus.REJECTED) {
            String rejectReason = notificationData.get(Constants.EMAIL_VAR_REJECT_REASON);
            notificationMessage.setBody(String.format("""
                    %s %s has rejected your query change request for asset '%s'
                    Ticket Reference: %s
                    Change Description: %s
                    %s
                    """,
                    approver.getFirstName(),
                    approver.getLastName(),
                    asset.getName(),
                    notificationData.get(Constants.EMAIL_VAR_TICKET_REFERENCE),
                    notificationData.get(Constants.EMAIL_VAR_CHANGE_DESCRIPTION),
                    rejectReason != null && !rejectReason.isEmpty() ? "Reason: " + rejectReason : ""));
        }
        
        notificationData.put(Constants.NOTIFY_DATA_ATTR_RECEIVER_ID, receiver.getId().toString());
        notificationMessage.setData(notificationData);
        notificationMessage.setTopic(Constants.DAM_NOTIFICATION_TOPIC);

        // Create and save notification task
        NotificationTask task = new NotificationTask();
        task.setReceiver(receiver);
        task.setSender(approver);
        task.setAsset(asset);
        task.setNotificationMessage(notificationMessage.toJson());
        task.setEmailType(EmailType.ASSET_QUERY_CHANGE_REQUEST_APPROVAL_NOTIFY);
        notificationTaskRepository.save(task);
        
        log.debug("Created approval notification task for asset query change request to user: {}", receiver.getEmail());
    }

    
    /**
     * Create change request for Asset Owner
     */
    public void createChangeRequestForAssetOwner(AccessQueryDTO accessQueryDTO, Asset asset) {
        AssetQueryChangeRequest changeRequest = new AssetQueryChangeRequest();
        changeRequest.setTicketReference(accessQueryDTO.getTicketReference());
        changeRequest.setChangeDescription(accessQueryDTO.getChangeDescription());
        changeRequest.setQuery(accessQueryDTO.getQuery());

        User currentUser = userService.getCurrentUser();
        
        // Asset Owner creates change request for their own asset
        saveChangeRequestWithNotifications(changeRequest, asset, currentUser);
    }
}