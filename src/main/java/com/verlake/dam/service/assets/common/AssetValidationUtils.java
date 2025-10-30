package com.verlake.dam.service.assets.common;

import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.service.assets.AccessRequestService;
import com.verlake.dam.service.assets.AssetService;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.yarn.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Utility class for common asset and access request validation operations
 * Eliminates code duplication between QueryExecutionService and DatabaseSchemaService
 */
@Component
@Slf4j
public class AssetValidationUtils {
    
    private final AccessRequestService accessRequestService;
    private final AssetService assetService;
    
    public AssetValidationUtils(AccessRequestService accessRequestService, AssetService assetService) {
        this.accessRequestService = accessRequestService;
        this.assetService = assetService;
    }
    
    /**
     * Validates and retrieves an access request by ID
     * 
     * @param requestId The access request ID
     * @return Validated access request
     * @throws ResourceNotFoundException if access request is not found
     */
    public AccessRequest validateAccessRequest(Long requestId) {
        AccessRequest accessRequest = accessRequestService.findById(requestId);
        if (accessRequest == null) {
            throw new ResourceNotFoundException("No access request found with ID: " + requestId);
        }
        return accessRequest;
    }
    
    /**
     * Validates that an access request has a valid asset credential
     * 
     * @param accessRequest The access request to validate
     * @return Validated asset credential
     * @throws ResourceNotFoundException if credential is not found
     */
    public AssetCredential validateAssetCredential(AccessRequest accessRequest) {
        AssetCredential credential = accessRequest.getAssetCredential();
        if (credential == null) {
            throw new ResourceNotFoundException("No asset credential found for this access request");
        }
        return credential;
    }
    
    /**
     * Validates that an access request is approved and not expired
     * 
     * @param accessRequest The access request to validate
     * @throws IllegalArgumentException if access request is not approved or expired
     */
    public void validateAccessRequestStatus(AccessRequest accessRequest) {
        if (!accessRequest.getDeveloperApproverStatus().equals(ApprovalStatus.APPROVED) &&
                !accessRequest.getAssetApproverStatus().equals(ApprovalStatus.APPROVED)) {
            throw new IllegalArgumentException("Access request is not approved");
        }

        if (accessRequest.getExpiryDate() != null && accessRequest.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Access request has expired");
        }
    }
    
    /**
     * Validates that the current user owns the specified asset
     * 
     * @param assetId The asset ID to validate ownership for
     * @return Validated asset
     * @throws ResourceNotFoundException if asset is not found
     * @throws IllegalArgumentException if user doesn't have ownership rights
     */
    public Asset validateAssetOwnership(Long assetId) {
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
    
    /**
     * Validates and retrieves the asset credential for the specified asset
     * 
     * @param asset The asset to get credential for
     * @return Validated asset credential
     * @throws ResourceNotFoundException if credential is not found
     */
    public AssetCredential validateAssetOwnerCredential(Asset asset) {
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
}
