package com.verlake.dam.service.assets;

import com.verlake.dam.entity.assets.*;
import com.verlake.dam.entity.assets.dto.AccessQueryDTO;
import com.verlake.dam.entity.assets.dto.AccessRequestDTO;
import com.verlake.dam.entity.assets.dto.AssetCredentialDTO;
import com.verlake.dam.entity.assets.dto.AssetDTO;
import com.verlake.dam.entity.firebase.NotificationMessage;
import com.verlake.dam.entity.firebase.NotificationTask;
import com.verlake.dam.enums.EmailType;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.exception.DatabaseAccessException;
import com.verlake.dam.repository.assets.*;
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
    private final DatabaseAccessService databaseAccessService;
    private final UserService userService;
    private final AssetCredentialsRepository assetCredentialsRepository;
    private final AssetRepository assetRepository;
    private final KeycloakService keycloakService;

    public AccessRequestService(
            AccessRequestRepository accessRequestRepository,
            AssetService assetService,
            AccessLevelObjectRepository accessLevelObjectRepository,
            AssetApproversRepository assetApproversRepository,
            UserRepository userRepository,
            NotificationTaskRepository notificationTaskRepository,
            DatabaseAccessService databaseAccessService,
            UserService userService, AssetCredentialsRepository assetCredentialsRepository, AssetRepository assetRepository, KeycloakService keycloakService) {
        this.accessRequestRepository = accessRequestRepository;
        this.assetService = assetService;
        this.accessLevelObjectRepository = accessLevelObjectRepository;
        this.assetApproversRepository = assetApproversRepository;
        this.userRepository = userRepository;
        this.notificationTaskRepository = notificationTaskRepository;
        this.databaseAccessService = databaseAccessService;
        this.userService = userService;
        this.assetCredentialsRepository = assetCredentialsRepository;
        this.assetRepository = assetRepository;
        this.keycloakService = keycloakService;
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
            List<AccessRequest> requests = accessRequestRepository.findByAssetAndRequestor(asset, requestor);
            request = requests.isEmpty() ? null : requests.get(0);
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
                .toList();

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
        notificationMessage.setTopic(Constants.DAM_NOTIFICATION_TOPIC);

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



    public List<AccessRequest> getAssetRequestApprovals() {

        User currentUser = userService.getCurrentUser();
        List<AssetCredential> assetCredentials = assetCredentialsRepository.findByUserId(currentUser.getId());
        return assetCredentials.stream()
                .map(credential -> {
                    List<AccessRequest> lstAccessRequest = accessRequestRepository.findPendingsByAsset(credential.getAsset());
                    Asset fullAsset = assetRepository.findById(credential.getAsset().getId())
                            .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
                    lstAccessRequest.forEach(request -> {
                        AssetDTO assetDTO = assetService.convertToDTO(fullAsset);
                        request.setAssetDTO(assetDTO);
                    });
                    return lstAccessRequest;
                })
                .flatMap(List::stream)
                .sorted((a1, a2) -> a2.getRequestTime().compareTo(a1.getRequestTime()))
                .toList();
    }

    public AccessRequest setApprovalStatusOfAccessRequest(Long accessRequestId, AccessRequestDTO accessRequestDTO, ApprovalStatus approvalStatus)
            throws JsonParseException {
        AccessRequest accessRequest = accessRequestRepository.findById(accessRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Access Request Not Found"));

        Map<String, String> newCredMapper = new HashMap<>();
        if (approvalStatus == ApprovalStatus.APPROVED) {
            // Find existing credential or create new one
            List<AccessRequest> lstAccessRequests = accessRequestRepository.findByUserAndAssetAndUserAccessTypeAndNotExpired(
                    accessRequest.getRequestor(),
                    accessRequest.getAsset(),
                    Roles.DEVELOPER.getOriginalName());

            String existUsername = "";

            if (!lstAccessRequests.isEmpty()) {
                // Generate new username and password
                existUsername = lstAccessRequests.get(0).getAssetCredential().getUsername();
            } else {
                // Set AccessRequest's temporary password flag to true if the username is not exist
                accessRequest.setIsTempPassword(true);
            }
            checkUserAndSetCredentials(accessRequest.getAsset().getId(), accessRequest.getRequestor(), accessRequest,
                    existUsername, newCredMapper);
            if (newCredMapper.containsKey("credentialID")) {
                AssetCredential credential = assetCredentialsRepository.findById(Long.parseLong(newCredMapper.get("credentialID")))
                        .orElseThrow(() -> new ResourceNotFoundException("New Created Credential Not Found"));
                accessRequest.setAssetCredential(credential);
            }
        } else if (approvalStatus == ApprovalStatus.REJECTED) {
            accessRequest.setRejectReason(accessRequestDTO.getRejectReason());
        }
        accessRequest.setAssetApproverStatus(approvalStatus);

        // Set expiry hours (default to 3 months = 2160 hours if not provided)
        accessRequest.setExpiryHours(accessRequestDTO != null && accessRequestDTO.getExpirationHours() != null && accessRequestDTO.getExpirationHours() != 0 ? accessRequestDTO.getExpirationHours() : Constants.ACCESS_REQUEST_DEFAULT_EXPIRY_HOURS);
        // Calculate expiry date
        accessRequest.setExpiryDate(LocalDateTime.now().plusHours(accessRequest.getExpiryHours()));

        accessRequestRepository.save(accessRequest);
        User currentUser = userService.getCurrentUser();
        Map<String, String> notificationData = new HashMap<>();
        notificationData.put("requestId", accessRequest.getId().toString());
        notificationData.put("assetId", accessRequest.getAsset().getId().toString());
        notificationData.put("assetName", accessRequest.getAsset().getName());
        notificationData.put("assetDescription", accessRequest.getAsset().getDescription());
        notificationData.put("developerName",
                accessRequest.getRequestor().getFirstName() + " " + accessRequest.getRequestor().getLastName());
        notificationData.put("approverName",
                currentUser.getFirstName() + " " + currentUser.getLastName());
        notificationData.put("approvalStatus", approvalStatus.name());
        notificationData.put("messageType", "1"); //1 : success, 0: fail
        if (!newCredMapper.isEmpty()) {
            notificationData.put(Constants.EMAIL_VAR_DB_USERNAME, newCredMapper.get(Constants.EMAIL_VAR_DB_USERNAME));
            notificationData.put(Constants.EMAIL_VAR_DB_PASSWORD, newCredMapper.get(Constants.EMAIL_VAR_DB_PASSWORD));
        }
        sendApprovalNotificationAndEmail(currentUser, accessRequest.getRequestor(), accessRequest.getAsset(),
                notificationData, approvalStatus);
        return accessRequest;
    }

    private void sendApprovalNotificationAndEmail(User approver, User receiver, Asset asset,
            Map<String, String> notificationData, ApprovalStatus approvalStatus) throws JsonParseException {
        NotificationMessage notificationMessage = new NotificationMessage();
        notificationMessage.setTitle("Approval Result of Asset Access Request");
        if (approvalStatus == ApprovalStatus.APPROVED) {
            if (notificationData.get(Constants.EMAIL_VAR_DB_USERNAME) != null
                    && notificationData.get(Constants.EMAIL_VAR_DB_PASSWORD) != null) {
                notificationMessage.setBody(String.format("""
                        %s %s has approved your request access of asset '%s'
                        Database Username: %s
                        Database Password: %s
                        """,
                        approver.getFirstName(),
                        approver.getLastName(),
                        asset.getName(),
                        notificationData.get(Constants.EMAIL_VAR_DB_USERNAME),
                        notificationData.get(Constants.EMAIL_VAR_DB_PASSWORD)));
            } else {
                notificationMessage.setBody(String.format("%s %s has approved your request access of asset '%s'",
                        approver.getFirstName(), approver.getLastName(), asset.getName()));
            }
        } else if (approvalStatus == ApprovalStatus.REJECTED) {
            notificationMessage.setBody(String.format("%s %s has rejected your request access of asset '%s'",
                    approver.getFirstName(), approver.getLastName(), asset.getName()));
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
        task.setEmailType(EmailType.APPROVAL_ASSET_ACCESS_REQUEST);
        notificationTaskRepository.save(task);
    }

    private void checkUserAndSetCredentials(Long assetId, User requestor, AccessRequest accessRequest,
            String existUsername, Map<String, String> newCredMapper) {
        User currentUser = userService.getCurrentUser();
        final String userKey = keycloakService.getUserKey();

        //Asset Credential has user_access_type, get credentials which are only asset owner's
        final List<AssetCredential> credentials = assetCredentialsRepository.findByAssetIdAndUserAccessType(assetId, Roles.ASSET_OWNER.getOriginalName());

        List<AssetCredential> validCredentials = credentials.stream()
                .filter(cred -> {
                    // Check if credential has required fields
                    boolean hasValidCredentials = cred.getUsername() != null && !cred.getUsername().isEmpty()
                            && cred.getPassword() != null && !cred.getPassword().isEmpty();

                    // Check if user is the owner of the asset associated with this credential
                    boolean isAssetOwner = cred.getUser() != null && cred.getUser().getId().equals(currentUser.getId());

                    return hasValidCredentials && isAssetOwner;
                })
                .toList();

        if (validCredentials.isEmpty()) {
            throw new ResourceNotFoundException("No valid credentials found for the current user");
        }

        AssetCredential cred = validCredentials.get(0);
        try {
            AssetCredential assetOwnerCred = new AssetCredential();
            assetOwnerCred.setUsername(cred.getUsername());
            if (!cred.getIsTemporaryPassword()) {
                String decryptedPassword = CommonUtils.decrypt(userKey, cred.getPassword());
                assetOwnerCred.setPassword(decryptedPassword);
            } else {
                assetOwnerCred.setPassword(cred.getPassword());
            }
            assetOwnerCred.setAsset(cred.getAsset());
            assetOwnerCred.setUser(cred.getUser());

            databaseAccessService.checkAccessRequestorInAsset(assetOwnerCred, requestor, accessRequest, existUsername,
                    newCredMapper);
        } catch (Exception e) {
            throw new DatabaseAccessException(
                    "Failed to update database objects upon asset owner login due to credential errors. credential: "
                            + cred.getId(),
                    e);
        }
    }



}