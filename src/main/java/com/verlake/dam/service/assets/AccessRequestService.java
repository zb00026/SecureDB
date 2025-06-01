package com.verlake.dam.service.assets;

import com.verlake.dam.entity.assets.*;
import com.verlake.dam.entity.assets.dto.AccessQueryDTO;
import com.verlake.dam.entity.assets.dto.AccessRequestDTO;
import com.verlake.dam.entity.assets.dto.AssetCredentialDTO;
import com.verlake.dam.entity.firebase.NotificationMessage;
import com.verlake.dam.entity.firebase.NotificationTask;
import com.verlake.dam.enums.EmailType;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.repository.assets.AccessLevelObjectRepository;
import com.verlake.dam.repository.assets.AccessRequestRepository;
import com.verlake.dam.repository.assets.AssetCredentialsRepository;
import com.verlake.dam.service.UserService;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.audit_trail.AuditTrailService;
import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;

import org.apache.hadoop.yarn.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.repository.assets.AssetApproversRepository;
import com.verlake.dam.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;
import com.verlake.dam.repository.NotificationTaskRepository;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

@Service
@Slf4j
public class AccessRequestService {
    private final AccessRequestRepository accessRequestRepository;
    private final AssetService assetService;
    private final AccessLevelObjectRepository accessLevelObjectRepository;
    private final AssetApproversRepository assetApproversRepository;
    private final UserRepository userRepository;
    private final NotificationTaskRepository notificationTaskRepository;
    private final KeycloakService keycloakService;
    private final AssetCredentialsRepository assetCredentialsRepository;
    private final DatabaseAccessService databaseAccessService;
    private final AuditTrailService auditTrailService;
    private final ObjectMapper objectMapper;

    public AccessRequestService(
            AccessRequestRepository accessRequestRepository,
            AssetService assetService,
            AccessLevelObjectRepository accessLevelObjectRepository,
            AssetApproversRepository assetApproversRepository,
            UserRepository userRepository,
            NotificationTaskRepository notificationTaskRepository,
            KeycloakService keycloakService, AssetCredentialsRepository assetCredentialsRepository,
            DatabaseAccessService databaseAccessService,
            AuditTrailService auditTrailService) {
        this.accessRequestRepository = accessRequestRepository;
        this.assetService = assetService;
        this.accessLevelObjectRepository = accessLevelObjectRepository;
        this.assetApproversRepository = assetApproversRepository;
        this.userRepository = userRepository;
        this.notificationTaskRepository = notificationTaskRepository;
        this.keycloakService = keycloakService;
        this.assetCredentialsRepository = assetCredentialsRepository;
        this.databaseAccessService = databaseAccessService;
        this.auditTrailService = auditTrailService;
        this.objectMapper = new ObjectMapper();
    }

    private String generateSql(List<AccessLevelObject> accessLevelObjects) {
        if (accessLevelObjects == null || accessLevelObjects.isEmpty()) {
            throw new ResourceNotFoundException("No access level objects provided");
        }

        StringBuilder sqlBuilder = new StringBuilder();

        accessLevelObjects.forEach(obj -> {
            AccessLevel level = obj.getAccessLevel();
            String template = level.getAccessTemplate();
            String dbName = "";
            String tableName = "";
            if (obj.getAccessLevel().getObject().equals(Constants.ASSET_ACCESS_OBJECT_DATABASE)) {
                dbName = obj.getObjectName();
            } else {
                dbName = obj.getObjectName().split("\\.")[0];
                tableName = obj.getObjectName().split("\\.")[1];
            }
            // Replace placeholders in template
            String sql = template
                    .replace("$DB", dbName)
                    .replace("$TABLE", tableName)
                    .replace("$OBJECT", obj.getObjectName());

            sqlBuilder.append(sql).append(sql.endsWith(";") ? "\n" : ";\n");
        });

        return sqlBuilder.toString();
    }

    public AccessRequest saveAccessRequest(AccessRequestDTO requestDTO, User requestor)
            throws ResourceNotFoundException, JsonParseException, IllegalArgumentException {
        List<AccessLevelObject> accessLevelObjects = requestDTO.getAccessLevelObjects();
        if (accessLevelObjects.isEmpty()) {
            throw new ResourceNotFoundException("No access level objects provided");
        }

        // Assuming all objects are for the same asset
        Asset asset = assetService.findById(requestDTO.getAssetId());

        AccessRequest request;

        // Check if this is an update to an existing request
        if (requestDTO.getRequestId() != null) {
            request = findById(requestDTO.getRequestId());
        } else {
            request = new AccessRequest();
        }
        request.setAsset(asset);
        request.setRequestor(requestor);
        request.setRequestTime(LocalDateTime.now());
        request.setRequestReason(requestDTO.getRequestReason());
        request.setAccessSql(generateSql(accessLevelObjects));
        request.setDeveloperApproverStatus(ApprovalStatus.PENDING);
        request.setAssetApproverStatus(ApprovalStatus.PENDING);
        request.setIsTempPassword(true);

        // Set expiry hours (default to 3 months = 2160 hours if not provided)
        request.setExpiryHours(
                requestDTO != null && requestDTO.getExpirationHours() != null && requestDTO.getExpirationHours() != 0
                        ? requestDTO.getExpirationHours()
                        : Constants.ACCESS_REQUEST_DEFAULT_EXPIRY_HOURS);
        // Calculate expiry date
        request.setExpiryDate(request.getRequestTime().plusHours(request.getExpiryHours()));

        AccessRequest savedRequest = accessRequestRepository.save(request);

        // Delete existing access level objects if this is an update
        if (requestDTO.getRequestId() != null) {
            accessLevelObjectRepository.deleteByAccessRequest(savedRequest);
        }

        // Save all AccessLevelObjects with the saved request ID
        accessLevelObjects.forEach(obj -> {
            obj.setAccessRequest(savedRequest);
            obj.setRequestor(requestor);
            obj.setId(null);
            accessLevelObjectRepository.save(obj);
        });

        // Send notifications
        sendNotifications(savedRequest, requestor, asset, true);

        return savedRequest;
    }

    private void sendNotifications(AccessRequest request,
            User requestor,
            Asset asset,
            boolean isAccessRequest) // true: Access Request, false: Relinquish Request
            throws ResourceNotFoundException, JsonParseException, IllegalArgumentException {
        // Get developer approver
        User developerApprover = null;
        if (requestor.getApprover() != null) {
            developerApprover = userRepository.findById(requestor.getApprover().getId())
                    .orElse(null);
        }

        // Get asset owners
        List<User> assetOwners = assetService.getAssetOwners(asset);

        // Get asset approvers
        List<User> assetApprovers = assetApproversRepository.findByAssetId(asset.getId())
                .stream()
                .map(AssetApprover::getUser)
                .collect(Collectors.toList());

        // Prepare notification data
        Map<String, String> notificationData = new HashMap<>();
        notificationData.put(Constants.EMAIL_VAR_REQUEST_ID, request.getId().toString());
        notificationData.put(Constants.EMAIL_VAR_ASSET_ID, asset.getId().toString());
        notificationData.put(Constants.EMAIL_VAR_ASSET_NAME, asset.getName());
        notificationData.put(Constants.EMAIL_VAR_ASSET_DESCRIPTION, asset.getDescription());
        notificationData.put(Constants.EMAIL_VAR_REQUESTOR_NAME,
                requestor.getFirstName() + " " + requestor.getLastName());
        notificationData.put(Constants.EMAIL_VAR_MESSAGE_TYPE, "1"); // 1 : success, 0: fail
        notificationData.put(Constants.EMAIL_VAR_IS_ACCESS_REQUEST, isAccessRequest ? "1" : "0");

        if (developerApprover != null) {
            sendNotificationAndEmail(developerApprover, requestor, asset, notificationData);
        }

        sendNotificationsToUsers(assetOwners, requestor, asset, notificationData);
        sendNotificationsToUsers(assetApprovers, requestor, asset, notificationData);

    }

    private void sendNotificationsToUsers(List<User> users, User requestor, Asset asset,
            Map<String, String> notificationData) throws JsonParseException {
        for (User user : users) {
            sendNotificationAndEmail(user, requestor, asset, notificationData);
        }
    }

    private void sendNotificationAndEmail(User receiver,
            User requestor,
            Asset asset,
            Map<String, String> notificationData) throws JsonParseException {
        NotificationMessage notificationMessage = new NotificationMessage();
        if (notificationData.get(Constants.EMAIL_VAR_IS_ACCESS_REQUEST).equals("1")) {
            notificationMessage.setTitle("New Asset Access Request");
            notificationMessage.setBody(String.format("%s %s has requested access to %s",
                    requestor.getFirstName(), requestor.getLastName(), asset.getName()));
        } else {
            notificationMessage.setTitle("Relinquished Asset Access Request");
            notificationMessage.setBody(String.format("%s %s has relinquished access to %s",
                    requestor.getFirstName(), requestor.getLastName(), asset.getName()));
        }

        notificationData.put(Constants.NOTIFY_DATA_ATTR_RECEIVER_ID, receiver.getId().toString());
        notificationMessage.setData(notificationData);
        notificationMessage.setTopic("dam_notification");

        // Create and save notification task
        NotificationTask task = new NotificationTask();
        task.setReceiver(receiver);
        task.setSender(requestor);
        task.setAsset(asset);
        task.setNotificationMessage(notificationMessage.toJson());
        if (notificationData.get(Constants.EMAIL_VAR_IS_ACCESS_REQUEST).equals("1")) {
            task.setEmailType(EmailType.DEVELOPER_ASSET_REQUEST_NOTIFY);
        } else {
            task.setEmailType(EmailType.DEVELOPER_RELINQUISH_ASSET_NOTIFY);
        }
        notificationTaskRepository.save(task);
    }

    public AccessRequest createRequest(AccessRequest request) {
        request.setRequestTime(LocalDateTime.now());
        request.setDeveloperApproverStatus(ApprovalStatus.PENDING);
        request.setAssetApproverStatus(ApprovalStatus.PENDING);
        return accessRequestRepository.save(request);
    }

    public List<AccessRequest> getPendingRequests() {
        return accessRequestRepository.findPendingRequests();
    }

    public List<AccessRequest> getRequestsNeedingDeveloperApproval() {
        return accessRequestRepository.findRequestsNeedingDeveloperApproval();
    }

    public List<AccessRequest> getRequestsNeedingAssetApproval() {
        return accessRequestRepository.findRequestsNeedingAssetApproval();
    }

    public AccessRequest updateRequest(AccessRequest request) {
        return accessRequestRepository.save(request);
    }

    public List<AccessRequest> getUserRequests(User user) {
        return accessRequestRepository.findByRequestor(user);
    }

    public List<AccessRequest> getTemporaryCredentialRequests(User user) {
        return accessRequestRepository
                .findByRequestorAndIsTempPasswordAndAssetApproverStatusAndIsDeletedFalseAndExpiryDateAfter(
                        user,
                        true,
                        ApprovalStatus.APPROVED,
                        LocalDateTime.now());
    }

    public List<AccessRequest> getAssetRequests(Asset asset) {
        return accessRequestRepository.findByAsset(asset);
    }

    public AccessRequest findById(Long id) {
        return accessRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Access Request not found with id: " + id));
    }

    public AccessRequest setCredentialPassword(Long accessRequestId, AssetCredentialDTO credentialInfo)
            throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException,
            NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        AccessRequest accessRequest = findById(accessRequestId);
        if (accessRequest == null) {
            throw new ResourceNotFoundException("No access request provided");
        }

        AssetCredential devCredential = accessRequest.getAssetCredential();

        databaseAccessService.updateAccessRequestCredentialPassword(devCredential, credentialInfo.getPassword());
        accessRequest.setIsTempPassword(false);
        accessRequestRepository.save(accessRequest);
        return accessRequest;
    }

    public void relinquishAccess(Long accessRequestId)
            throws JsonParseException, ResourceNotFoundException, IllegalArgumentException {
        AccessRequest accessRequest = findById(accessRequestId);
        if (accessRequest == null) {
            throw new ResourceNotFoundException("No access request provided");
        }

        accessRequest.setExpiryDate(LocalDateTime.now());
        accessRequest.setExpiryHours(0);
        accessRequestRepository.save(accessRequest);

        sendNotifications(accessRequest, accessRequest.getRequestor(), accessRequest.getAsset(), false);
    }

    public Map<String, Object> runQuery(AccessQueryDTO accessQueryDTO) {
        AccessRequest accessRequest = findById(accessQueryDTO.getRequestId());
        if (accessRequest == null) {
            throw new ResourceNotFoundException("No access request provided");
        }

        AssetCredential devCredential = accessRequest.getAssetCredential();
        if (devCredential == null) {
            throw new ResourceNotFoundException("No asset credential found for this access request");
        }

        // Validate that the access request is approved and not expired
        if (!accessRequest.getDeveloperApproverStatus().equals(ApprovalStatus.APPROVED) &&
            !accessRequest.getAssetApproverStatus().equals(ApprovalStatus.APPROVED)) {
            throw new IllegalArgumentException("Access request is not approved");
        }

        if (accessRequest.getExpiryDate() != null && accessRequest.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Access request has expired");
        }

        Map<String, Object> result = null;
        boolean querySuccess = false;
        String errorMessage = null;
        long startTime = System.currentTimeMillis();

        try {
            result = databaseAccessService.executeQueryWithCredentials(devCredential, accessQueryDTO.getQuery());
            querySuccess = true;
            // Create successful audit log
            createQueryAuditLog(accessQueryDTO, accessRequest, devCredential, querySuccess, null, result, startTime);

            // Create successful audit log
            createQueryAuditLog(accessQueryDTO, accessRequest, devCredential, querySuccess, null, result, startTime);
            return result;
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("Error executing query for access request: {}", accessQueryDTO.getRequestId(), e);
            // Create failed audit log
            createQueryAuditLog(accessQueryDTO, accessRequest, devCredential, querySuccess, errorMessage, null,
                    startTime);
            throw new IllegalArgumentException("Failed to execute query: " + e.getMessage(), e);
        }
    }

    private void createQueryAuditLog(AccessQueryDTO accessQueryDTO, AccessRequest accessRequest,
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
            auditMetadata.put(Constants.AUDIT_FIELD_ASSET_ID, accessRequest.getAsset().getId());
            auditMetadata.put(Constants.EMAIL_VAR_ASSET_NAME, accessRequest.getAsset().getName());
            auditMetadata.put(Constants.EMAIL_VAR_DATABASE_TYPE, accessRequest.getAsset().getDatabaseType().toString());
            auditMetadata.put(Constants.EMAIL_VAR_HOST_URL, accessRequest.getAsset().getHostUrl());
            auditMetadata.put(Constants.AUDIT_FIELD_USERNAME, credential.getUsername());
            auditMetadata.put(Constants.EMAIL_VAR_QUERY, accessQueryDTO.getQuery());
            auditMetadata.put(Constants.AUDIT_FIELD_EXECUTION_TIME_MS, executionTime);
            auditMetadata.put(Constants.AUDIT_FIELD_SUCCESS, success);

            if (!success && errorMessage != null) {
                auditMetadata.put(Constants.AUDIT_FIELD_ERROR_MESSAGE, errorMessage);
            }

            if (success && result != null) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
                auditMetadata.put(Constants.AUDIT_FIELD_ROW_COUNT, data != null ? data.size() : 0);
                List<String> headers = (List<String>) result.get("headers");
                auditMetadata.put(Constants.AUDIT_FIELD_COLUMN_COUNT, headers != null ? headers.size() : 0);

            }

            // Create instance ID for query execution
            String instanceId = String.format("QUERY_EXECUTION(%s)", accessQueryDTO.getRequestId());

            AuditTrail audit = AuditTrail.builder()
                    .timestamp(LocalDateTime.now())
                    .user(username)
                    .action(success ? "QUERY_EXECUTED" : "QUERY_FAILED")
                    .instanceId(instanceId)
                    .actionMetadata(objectMapper.writeValueAsString(auditMetadata))
                    .previousValue(null) // No previous value for query execution
                    .newValue(success ? "Query executed successfully" : "Query execution failed")
                    .ipAddress(ipAddress)
                    .build();

            auditTrailService.save(audit);

            // Send DELETE query alert to asset owners if it's a successful DELETE operation
            if (success && isDeleteQuery) {
                sendDeleteQueryAlert(accessRequest, accessQueryDTO.getQuery(), result);
            }

            log.info("Audit log created for query execution: requestId={}, success={}, executionTime={}ms",
                    accessQueryDTO.getRequestId(), success, executionTime);
        } catch (Exception e) {
            log.error("Failed to create audit log for query execution: {}", e.getMessage(), e);
        }
    }

    private void sendDeleteQueryAlert(AccessRequest accessRequest,
            String query, Map<String, Object> result) {
        try {
            // Get asset owners
            List<User> assetOwners = assetService.getAssetOwners(accessRequest.getAsset());

            // Extract table name from DELETE query (basic parsing)
            String tableName = extractTableNameFromDeleteQuery(query);

            // Get affected rows count
            String affectedRows = "Unknown";
            if (result != null && result.get("data") != null) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
                if (!data.isEmpty() && data.get(0).containsKey("Affected Rows")) {
                    affectedRows = String.valueOf(data.get(0).get("Affected Rows"));
                }
            }

            // Prepare notification data for DELETE alert
            Map<String, String> notificationData = new HashMap<>();
            notificationData.put(Constants.EMAIL_VAR_ASSET_NAME, accessRequest.getAsset().getName());
            notificationData.put(Constants.EMAIL_VAR_DATABASE_TYPE,
                    accessRequest.getAsset().getDatabaseType().toString());
            notificationData.put(Constants.EMAIL_VAR_HOST_URL, accessRequest.getAsset().getHostUrl());
            notificationData.put(Constants.EMAIL_VAR_EXECUTOR_NAME, getCurrentUsername());
            notificationData.put(Constants.EMAIL_VAR_EXECUTION_TIME, LocalDateTime.now().toString());
            notificationData.put(Constants.EMAIL_VAR_TABLE_NAME, tableName);
            notificationData.put(Constants.EMAIL_VAR_AFFECTED_ROWS, affectedRows);
            notificationData.put(Constants.EMAIL_VAR_QUERY, query);

            // Send notification to each asset owner using NotificationTask
            for (User owner : assetOwners) {
                notificationData.put(Constants.EMAIL_VAR_OWNER_NAME, owner.getFirstName() + " " + owner.getLastName());

                createDeleteQueryNotificationTask(accessRequest, owner, notificationData);
            }

            log.info("DELETE query alert notification tasks created for asset: {}, to {} owners",
                    accessRequest.getAsset().getName(), assetOwners.size());

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

    private void createDeleteQueryNotificationTask(AccessRequest accessRequest, User owner,
            Map<String, String> notificationData) throws Exception {
        NotificationMessage notificationMessage = new NotificationMessage();
        notificationMessage.setTitle("DELETE Query Alert - " + accessRequest.getAsset().getName());
        notificationMessage.setBody(String.format("DELETE operation executed on %s by %s",
                accessRequest.getAsset().getName(), getCurrentUsername()));
        notificationMessage.setData(notificationData);
        notificationMessage.setTopic("dam_notification");

        // Create and save notification task
        NotificationTask task = new NotificationTask();
        task.setReceiver(owner);
        task.setSender(accessRequest.getRequestor());
        task.setAsset(accessRequest.getAsset());
        task.setNotificationMessage(notificationMessage.toJson());
        task.setEmailType(EmailType.DELETE_QUERY_ALERT);
        notificationTaskRepository.save(task);
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
}