package com.verlake.dam.service.assets;

import com.verlake.dam.entity.assets.*;
import com.verlake.dam.entity.assets.dto.AccessRequestDTO;
import com.verlake.dam.entity.firebase.NotificationMessage;
import com.verlake.dam.entity.firebase.NotificationTask;
import com.verlake.dam.enums.EmailType;
import com.verlake.dam.repository.assets.AccessLevelObjectRepository;
import com.verlake.dam.repository.assets.AccessRequestRepository;
import com.verlake.dam.repository.assets.AssetCredentialsRepository;
import com.verlake.dam.utils.Constants;

import org.apache.hadoop.yarn.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;

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

@Service
@Slf4j
public class AccessRequestService {
    private final AccessRequestRepository accessRequestRepository;
    private final AssetService assetService;
    private final AccessLevelObjectRepository accessLevelObjectRepository;
    private final AssetApproversRepository assetApproversRepository;
    private final UserRepository userRepository;
    private final NotificationTaskRepository notificationTaskRepository;

    public AccessRequestService(
                                AccessRequestRepository accessRequestRepository,
                                AssetService assetService,
                                AccessLevelObjectRepository accessLevelObjectRepository,
                                AssetApproversRepository assetApproversRepository,
                                UserRepository userRepository,
                                NotificationTaskRepository notificationTaskRepository) {
        this.accessRequestRepository = accessRequestRepository;
        this.assetService = assetService;
        this.accessLevelObjectRepository = accessLevelObjectRepository;
        this.assetApproversRepository = assetApproversRepository;
        this.userRepository = userRepository;
        this.notificationTaskRepository = notificationTaskRepository;
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

    public AccessRequest saveAccessRequest(AccessRequestDTO requestDTO, User requestor)  throws ResourceNotFoundException, JsonParseException, IllegalArgumentException {
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
        sendNotifications(savedRequest, requestor, asset);

        return savedRequest;
    }

    private void sendNotifications(AccessRequest request, User requestor, Asset asset) throws ResourceNotFoundException, JsonParseException, IllegalArgumentException {
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
            notificationData.put("requestId", request.getId().toString());
            notificationData.put("assetId", asset.getId().toString());
            notificationData.put("assetName", asset.getName());
            notificationData.put("assetDescription", asset.getDescription());
            notificationData.put("requestorName", requestor.getFirstName() + " " + requestor.getLastName());
            notificationData.put("messageType", "1"); //1 : success, 0: fail

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

    private void sendNotificationAndEmail(User receiver, User requestor, Asset asset, Map<String, String> notificationData) throws JsonParseException {
        NotificationMessage notificationMessage = new NotificationMessage();
        notificationMessage.setTitle("New Asset Access Request");
        notificationMessage.setBody(String.format("%s %s has requested access to %s",
            requestor.getFirstName(), requestor.getLastName(), asset.getName()));
        notificationData.put(Constants.NOTIFY_DATA_ATTR_RECEIVER_ID, receiver.getId().toString());
        notificationMessage.setData(notificationData);
        notificationMessage.setTopic("dam_notification");

        // Create and save notification task
        NotificationTask task = new NotificationTask();
        task.setReceiver(receiver);
        task.setSender(requestor);
        task.setAsset(asset);
        task.setNotificationMessage(notificationMessage.toJson());
        task.setEmailType(EmailType.DEVELOPER_ASSET_REQUEST_NOTIFY);
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

    public List<AccessRequest> getAssetRequests(Asset asset) {
        return accessRequestRepository.findByAsset(asset);
    }

    public AccessRequest findById(Long id) {
        return accessRequestRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Access Request not found with id: " + id));
    }
}