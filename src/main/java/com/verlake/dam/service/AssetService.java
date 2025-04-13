package com.verlake.dam.service;

import com.verlake.dam.entity.assets.AccessLevel;
import com.verlake.dam.entity.assets.AssetApprover;
import com.verlake.dam.entity.assets.dto.AssetDTO;
import com.verlake.dam.entity.assets.dto.AssetUpdateDTO;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.repository.assets.*;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import org.apache.hadoop.yarn.exceptions.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.assets.AccessRequest;

@Service
@Slf4j
public class AssetService {
    private final AssetRepository assetRepository;
    private final AssetCredentialsRepository credentialsRepository;
    private final AssetApproversRepository assetApproversRepository;
    private final AccessLevelRepository accessLevelRepository;
    private final UserService userService;
    private final AccessRequestRepository accessRequestRepository;

    @Autowired
    public AssetService(AssetRepository assetRepository,
                        AssetCredentialsRepository credentialsRepository,
                        AssetApproversRepository assetApproversRepository,
                        AccessLevelRepository accessLevelRepository,
                        UserService userService, AccessRequestRepository accessRequestRepository) {
        this.assetRepository = assetRepository;
        this.credentialsRepository = credentialsRepository;
        this.assetApproversRepository = assetApproversRepository;
        this.accessLevelRepository = accessLevelRepository;
        this.userService = userService;
        this.accessRequestRepository = accessRequestRepository;
    }

    @Transactional
    public Asset createAsset(AssetDTO assetDTO) {
        Asset asset = Asset.builder()
                .name(assetDTO.getName())
                .description(assetDTO.getDescription())
                .type(assetDTO.getType())
                .databaseType(assetDTO.getDatabaseType())
                .hostAddress(assetDTO.getHostAddress())
                .deleted(false)
                .build();

        return assetRepository.save(asset);
    }

    @Transactional
    public void updateAssetOwners(AssetUpdateDTO updateDTO) {
        Asset asset = assetRepository.findByIdAndDeletedFalse(updateDTO.getAssetId())
                .orElseThrow(() -> new ResourceNotFoundException(Constants.ASSET_NOT_FOUND));
        if (updateDTO.getMethod().equals(Constants.ASSET_ADD_NAME)) {
            // Delete existing credentials for these users if they exist
            updateDTO.getUserIds().forEach(userId -> {
                credentialsRepository.deleteByAssetIdAndUserId(asset.getId(), userId);
            });

            // Create new credentials for provided user IDs
            List<User> newOwners = updateDTO.getUserIds().stream()
                    .map(userId -> userService.findById(userId))
                    .filter(user -> user != null)
                    .toList();

            // Create credentials
            for (User owner : newOwners) {
                AssetCredential credentials = AssetCredential.builder()
                        .asset(asset)
                        .user(owner)
                        .username(null)
                        .password(null)
                        .build();
                credentialsRepository.save(credentials);
            }
        } else if (updateDTO.getMethod().equals(Constants.ASSET_REMOVE_NAME)) {
            // Delete credentials for provided user IDs
            updateDTO.getUserIds().forEach(userId -> {
                credentialsRepository.deleteByAssetIdAndUserId(asset.getId(), userId);
            });
        }

        assetRepository.save(asset);
    }

    @Transactional
    public void updateAssetApprovers(AssetUpdateDTO updateDTO) {
        Asset asset = assetRepository.findByIdAndDeletedFalse(updateDTO.getAssetId())
                .orElseThrow(() -> new ResourceNotFoundException(Constants.ASSET_NOT_FOUND));
        if (updateDTO.getMethod().equals(Constants.ASSET_ADD_NAME)) {
            // Delete existing asset approvers for these users if they exist
            updateDTO.getUserIds().forEach(userId -> {
                assetApproversRepository.deleteByAssetIdAndUserId(asset.getId(), userId);
            });

            // Create new approvers for provided user IDs
            List<User> newApprovers = updateDTO.getUserIds().stream()
                    .map(userId -> userService.findById(userId))
                    .filter(user -> user != null)
                    .toList();

            // Create Asset Approver
            for (User newApprover : newApprovers) {
                AssetApprover approver = AssetApprover.builder()
                        .asset(asset)
                        .user(newApprover)
                        .build();
                assetApproversRepository.save(approver);
            }
        } else if (updateDTO.getMethod().equals(Constants.ASSET_REMOVE_NAME)) {
            // Delete Approvers for provided user IDs
            updateDTO.getUserIds().forEach(userId -> {
                assetApproversRepository.deleteByAssetIdAndUserId(asset.getId(), userId);
            });
        }

        assetRepository.save(asset);
    }

    @Transactional
    public void deleteAsset(Long id) {
        Asset asset = assetRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(Constants.ASSET_NOT_FOUND));

        // Soft delete the asset
        asset.setDeleted(true);

        // Wipe all credentials
        credentialsRepository.resetCredentialsByAssetId(asset.getId());

        assetRepository.save(asset);
    }

    public Asset findById(Long id) {
        return assetRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(Constants.ASSET_NOT_FOUND));
    }

    public AssetDTO findDTOById(Long id) {
        Asset asset = assetRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(Constants.ASSET_NOT_FOUND));
        return convertToDTO(asset);
    }

    public List<AssetDTO> getAllAssets() {
        List<Asset> lstAssets = assetRepository.findByDeletedFalse();
        return lstAssets.stream()
                .map(this::convertToDTO)
                .toList();
    }

    public List<AssetDTO> getAllAssetsWithFetchAccessTemplate() {
        List<Asset> lstAssets = assetRepository.findByDeletedFalse();
        return lstAssets.stream()
                .map(this::convertToDTOWithFetchAccessTemplate)
                .toList();
    }

    public AssetDTO convertToDTO(Asset asset) {
        List<AssetCredential> credentials = credentialsRepository.findByAssetId(asset.getId());
        List<User> owners = credentials.stream()
                .map(AssetCredential::getUser)
                .toList();

        List<AssetApprover> assetApprovers = assetApproversRepository.findByAssetId(asset.getId());
        List<User> approvers = assetApprovers.stream()
                .map(AssetApprover::getUser)
                .toList();
        User requestor = userService.findByEmail(CommonUtils.getEmailFromSession());
        List<AccessRequest> requests = accessRequestRepository.findByAssetAndRequestor(asset, requestor);

        return AssetDTO.builder()
                .id(asset.getId())
                .name(asset.getName())
                .description(asset.getDescription())
                .type(asset.getType())
                .databaseType(asset.getDatabaseType())
                .hostAddress(asset.getHostAddress())
                .owners(owners)
                .accessRequest(requests.isEmpty() ? null : requests.get(0))
                .approvers(approvers)
                .build();
    }

    private AssetDTO convertToDTOWithFetchAccessTemplate(Asset asset) {
        AssetDTO dto = convertToDTO(asset);
        AccessLevel fetchAccess = accessLevelRepository.findFetchAccessTemplate(asset.getType().name(), asset.getDatabaseType().name());
        User requestor = userService.findByEmail(CommonUtils.getEmailFromSession());
        List<AccessRequest> requests = accessRequestRepository.findByAssetAndRequestor(asset, requestor);
        dto.setAccessRequest(requests.isEmpty() ? null : requests.get(0));
        dto.setFetchTemplate(fetchAccess != null ? fetchAccess.getAccessTemplate() : null);
        return dto;
    }

    @Transactional
    public void updateAsset(Long id, AssetDTO updateDTO) {
        Asset asset = assetRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(Constants.ASSET_NOT_FOUND));

        asset.setName(updateDTO.getName());
        asset.setDescription(updateDTO.getDescription());
        asset.setType(updateDTO.getType());
        asset.setDatabaseType(updateDTO.getDatabaseType());
        asset.setHostAddress(updateDTO.getHostAddress());

        assetRepository.save(asset);
    }

    /**
     * Gets asset credentials that need to be set up for the current user
     * @return List of asset credentials that need setup
     */
    public List<AssetCredential> getNewAssignedCredentials() {
        String email = CommonUtils.getEmailFromSession();

        User currentUser = userService.findByEmail(email);
        if (currentUser == null) {
            throw new AccessDeniedException("User not found");
        }

        return credentialsRepository.findNewAssignedCredentials(currentUser.getId());
    }

    /**
     * Gets asset credentials that need to be set up for the current user
     * @return List of asset credentials that need setup
     */
    public List<AssetCredential> getAssignedCredentials() {
        String email = CommonUtils.getEmailFromSession();

        User currentUser = userService.findByEmail(email);
        if (currentUser == null) {
            throw new AccessDeniedException("User not found");
        }

        List<AssetCredential> credentials = credentialsRepository.findByUserId(currentUser.getId());
        credentials.sort((c1, c2) -> {
            boolean c1Null = c1.getUsername() == null && c1.getPassword() == null;
            boolean c2Null = c2.getUsername() == null && c2.getPassword() == null;
            return Boolean.compare(c2Null, c1Null);
        });
        return credentials;
    }

    public AssetCredential findCredentialByAssetId(Long assetId) {
        List<AssetCredential> credentials = credentialsRepository.findByAssetId(assetId);
        if (credentials.isEmpty()) {
            return null;
        }
        return credentials.get(0);
    }

    public AssetCredential findCredentialById(Long credentialId) {
        return credentialsRepository.findById(credentialId)
                .orElse(null);
    }

    public void saveCredential(AssetCredential assetCredential) {
        credentialsRepository.save(assetCredential);
    }

    public void deleteAssetCredential(AssetCredential credential) {
        credentialsRepository.delete(credential);
    }
}
