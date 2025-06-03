package com.verlake.dam.service.assets;

import com.verlake.dam.entity.assets.AssetQueryChangeRequest;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetApprover;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.firebase.NotificationMessage;
import com.verlake.dam.entity.firebase.NotificationTask;
import com.verlake.dam.repository.assets.AssetQueryChangeRequestRepository;
import com.verlake.dam.repository.assets.AssetApproversRepository;
import com.verlake.dam.repository.NotificationTaskRepository;
import com.verlake.dam.enums.EmailType;
import com.verlake.dam.utils.Constants;
import com.verlake.dam.exception.AssetQueryChangeRequestNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AssetQueryChangeRequestService {
    
    private final AssetQueryChangeRequestRepository assetQueryChangeRequestRepository;
    private final AssetService assetService;
    private final AssetApproversRepository assetApproversRepository;
    private final NotificationTaskRepository notificationTaskRepository;

    public AssetQueryChangeRequestService(AssetQueryChangeRequestRepository assetQueryChangeRequestRepository,
                                        AssetService assetService,
                                        AssetApproversRepository assetApproversRepository,
                                        NotificationTaskRepository notificationTaskRepository) {
        this.assetQueryChangeRequestRepository = assetQueryChangeRequestRepository;
        this.assetService = assetService;
        this.assetApproversRepository = assetApproversRepository;
        this.notificationTaskRepository = notificationTaskRepository;
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

    /**
     * Save asset query change request with asset and requestor, and send notifications
     */
    @Transactional
    public AssetQueryChangeRequest saveWithNotifications(AssetQueryChangeRequest assetQueryChangeRequest, 
                                                        Asset asset, User requestor) {
        log.debug("Saving asset query change request with notifications: {}", assetQueryChangeRequest);
        
        // Set asset and requestor
        assetQueryChangeRequest.setAsset(asset);
        assetQueryChangeRequest.setRequestor(requestor);
        
        // Save the request
        AssetQueryChangeRequest savedRequest = assetQueryChangeRequestRepository.save(assetQueryChangeRequest);
        
        // Send notifications to asset owners and approvers
        sendNotificationsToAssetStakeholders(savedRequest, asset, requestor);
        
        return savedRequest;
    }

    private void sendNotificationsToAssetStakeholders(AssetQueryChangeRequest changeRequest, 
                                                    Asset asset, User requestor) {
        log.debug("Sending notifications for asset query change request: {}", changeRequest.getId());
        
        // Get asset owners
        List<User> assetOwners = assetService.getAssetOwners(asset);
        
        // Get asset approvers
        List<User> assetApprovers = assetApproversRepository.findByAssetId(asset.getId())
                .stream()
                .map(AssetApprover::getUser)
                .toList();
        
        // Prepare notification data
        Map<String, Object> notificationData = new HashMap<>();
        notificationData.put("changeRequestId", changeRequest.getId().toString());
        notificationData.put("assetId", asset.getId().toString());
        notificationData.put("assetName", asset.getName());
        notificationData.put("assetDescription", asset.getDescription());
        notificationData.put("requestorName", requestor.getFirstName() + " " + requestor.getLastName());
        notificationData.put("ticketReference", changeRequest.getTicketReference() != null ? changeRequest.getTicketReference() : "");
        notificationData.put("changeDescription", changeRequest.getChangeDescription() != null ? changeRequest.getChangeDescription() : "");
        notificationData.put("query", changeRequest.getQuery() != null ? changeRequest.getQuery() : "");
        notificationData.put("messageType", "1"); // 1 : success, 0: fail
        
        // Send notifications to asset owners
        sendNotificationsToUsers(assetOwners, requestor, asset, notificationData);
        
        // Send notifications to asset approvers (if different from owners)
        List<User> uniqueApprovers = assetApprovers.stream()
                .filter(approver -> !assetOwners.contains(approver))
                .toList();
        sendNotificationsToUsers(uniqueApprovers, requestor, asset, notificationData);
    }

    private void sendNotificationsToUsers(List<User> users, User requestor, Asset asset, 
                                        Map<String, Object> notificationData) {
        for (User user : users) {
            sendNotificationToUser(user, requestor, asset, notificationData);
        }
    }

    private void sendNotificationToUser(User receiver, User requestor, Asset asset, 
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
            notificationMessage.setTopic("dam_notification");

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
            log.error("Failed to create notification for asset query change request to user: {}", receiver.getEmail(), e);
        }
    }
} 