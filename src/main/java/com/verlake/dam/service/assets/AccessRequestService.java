package com.verlake.dam.service.assets;

import com.verlake.dam.entity.assets.*;
import com.verlake.dam.entity.assets.dto.AccessRequestDTO;
import com.verlake.dam.entity.firebase.NotificationMessage;
import com.verlake.dam.entity.firebase.NotificationTask;
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
    private final AssetCredentialsRepository assetCredentialsRepository;
    private final AccessLevelObjectRepository accessLevelObjectRepository;
    private final AssetApproversRepository assetApproversRepository;
    private final UserRepository userRepository;
    private final NotificationTaskRepository notificationTaskRepository;

    public AccessRequestService(
                                AccessRequestRepository accessRequestRepository, 
                                AssetService assetService, 
                                AssetCredentialsRepository assetCredentialsRepository,
                                AccessLevelObjectRepository accessLevelObjectRepository,
                                AssetApproversRepository assetApproversRepository,
                                UserRepository userRepository,
                                NotificationTaskRepository notificationTaskRepository) {
        this.accessRequestRepository = accessRequestRepository;
        this.assetService = assetService;
        this.assetCredentialsRepository = assetCredentialsRepository;
        this.accessLevelObjectRepository = accessLevelObjectRepository;
        this.assetApproversRepository = assetApproversRepository;
        this.userRepository = userRepository;
        this.notificationTaskRepository = notificationTaskRepository;
    }

    private String generateSql(List<AccessLevelObject> accessLevelObjects, Asset asset) {
        if (accessLevelObjects == null || accessLevelObjects.isEmpty()) {
            throw new ResourceNotFoundException("No access level objects provided");
        }

        List<AssetCredential> lstCredentials = assetCredentialsRepository.findByAssetId(asset.getId());
        if(lstCredentials == null || lstCredentials.isEmpty()) {
            throw new ResourceNotFoundException("No asset credentials provided");
        }
        AssetCredential credential = lstCredentials.get(0);

        StringBuilder sqlBuilder = new StringBuilder();
        
        accessLevelObjects.forEach(obj -> {
            AccessLevel level = obj.getAccessLevel();
            String template = level.getAccessTemplate();
            String dbName = "";
            if (obj.getAccessLevel().getObject().equals(Constants.ASSET_ACCESS_OBJECT_DATABASE)) {
                dbName = obj.getObjectName();
            } else {
                dbName = obj.getObjectName().split("\\.")[0];
            }
            // Replace placeholders in template
            String sql = template
                    .replace("$USER", credential.getUsername())
                    .replace("$DB", dbName)
                    .replace("$OBJECT", obj.getObjectName());

            sqlBuilder.append(sql).append(";\n");
        });

        return sqlBuilder.toString();
    }

    public AccessRequest saveAccessRequest(AccessRequestDTO requestDTO, User requestor) {
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
            request.setAsset(asset);
            request.setRequestor(requestor);
            request.setRequestTime(LocalDateTime.now());
            request.setRequestReason(requestDTO.getRequestReason());
            request.setAccessSql(generateSql(accessLevelObjects, asset));
        } else {
            request = new AccessRequest();
            request.setAsset(asset);
            request.setRequestor(requestor);
            request.setRequestTime(LocalDateTime.now());
            request.setRequestReason(requestDTO.getRequestReason());
            request.setAccessSql(generateSql(accessLevelObjects, asset));
            request.setDeveloperApproverStatus(ApprovalStatus.PENDING);
            request.setAssetApproverStatus(ApprovalStatus.PENDING);
        }

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

    private void sendNotifications(AccessRequest request, User requestor, Asset asset) {
        try {
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

            if (developerApprover != null) {
                sendNotificationAndEmail(developerApprover, requestor, asset, notificationData);
            }

            sendNotificationsToUsers(assetOwners, requestor, asset, notificationData, "asset owner");
            sendNotificationsToUsers(assetApprovers, requestor, asset, notificationData, "asset approver");
        } catch (ResourceNotFoundException | JsonParseException | IllegalArgumentException e) {
            log.error("Failed to send notifications: {}", e.getMessage(), e);
        }
    }

    private void sendNotificationsToUsers(List<User> users, User requestor, Asset asset, 
                                        Map<String, String> notificationData, String userType) {
        for (User user : users) {
            try {
                sendNotificationAndEmail(user, requestor, asset, notificationData);
            } catch (JsonParseException e) {
                log.error("Failed to send notification to {}: {}", userType, user.getId(), e);
            }
        }
    }

    private void sendNotificationAndEmail(User receiver, User requestor, Asset asset, Map<String, String> notificationData) throws JsonParseException {
        NotificationMessage notificationMessage = new NotificationMessage();
        notificationMessage.setTitle("New Asset Access Request");
        notificationMessage.setBody(String.format("%s %s has requested access to %s", 
            requestor.getFirstName(), requestor.getLastName(), asset.getName()));
        notificationData.put("receiverId", receiver.getId().toString());
        notificationMessage.setData(notificationData);
        notificationMessage.setTopic("dam_notification");

        // Create and save notification task
        NotificationTask task = new NotificationTask();
        task.setReceiver(receiver);
        task.setRequestor(requestor);
        task.setAsset(asset);
        task.setNotificationMessage(notificationMessage.toJson());
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