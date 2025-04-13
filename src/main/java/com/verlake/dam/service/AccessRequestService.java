package com.verlake.dam.service;

import com.verlake.dam.entity.assets.*;
import com.verlake.dam.entity.assets.dto.AccessRequestDTO;
import com.verlake.dam.repository.assets.AccessLevelObjectRepository;
import com.verlake.dam.repository.assets.AccessRequestRepository;
import com.verlake.dam.repository.assets.AssetCredentialsRepository;
import com.verlake.dam.utils.Constants;

import jakarta.persistence.EntityManager;

import org.apache.hadoop.yarn.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.ApprovalStatus;

@Service
@Transactional
public class AccessRequestService {
    private final AccessRequestRepository accessRequestRepository;
    private final AssetService assetService;
    private final AssetCredentialsRepository assetCredentialsRepository;
    private final AccessLevelObjectRepository accessLevelObjectRepository;
    private final EntityManager entityManager;

    public AccessRequestService(
                                AccessRequestRepository accessRequestRepository, 
                                AssetService assetService, 
                                AssetCredentialsRepository assetCredentialsRepository,
                                AccessLevelObjectRepository accessLevelObjectRepository,
                                EntityManager entityManager) {
        this.accessRequestRepository = accessRequestRepository;
        this.assetService = assetService;
        this.assetCredentialsRepository = assetCredentialsRepository;
        this.accessLevelObjectRepository = accessLevelObjectRepository;
        this.entityManager = entityManager;
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

    @Transactional
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
            // Find the existing request
            request = findById(requestDTO.getRequestId());

            // Update the request properties
            request.setAsset(asset);
            request.setRequestor(requestor);
            request.setRequestTime(LocalDateTime.now());
            request.setRequestReason(requestDTO.getRequestReason());
            request.setAccessSql(generateSql(accessLevelObjects, asset));
        } else {
            // Create a new request
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
            entityManager.flush();
        }

        // Save all AccessLevelObjects with the saved request ID
        accessLevelObjects.forEach(obj -> {
            obj.setAccessRequest(savedRequest);
            obj.setRequestor(requestor);
            obj.setId(null);
            accessLevelObjectRepository.save(obj);
        });

        return savedRequest;
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