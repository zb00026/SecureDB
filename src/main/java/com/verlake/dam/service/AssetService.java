package com.verlake.dam.service;

import com.verlake.dam.entity.dto.AssetDTO;
import com.verlake.dam.entity.dto.AssetOwnerUpdateDTO;
import com.verlake.dam.utils.Constants;
import org.apache.hadoop.yarn.exceptions.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.stream.Collectors;

import com.verlake.dam.repository.AssetRepository;
import com.verlake.dam.repository.AssetCredentialsRepository;
import com.verlake.dam.entity.Asset;
import com.verlake.dam.entity.AssetCredential;
import com.verlake.dam.entity.User;
@Service
@Slf4j
public class AssetService {
    private final AssetRepository assetRepository;
    private final AssetCredentialsRepository credentialsRepository;
    private final UserService userService;

    @Autowired
    public AssetService(AssetRepository assetRepository, 
                       AssetCredentialsRepository credentialsRepository,
                       UserService userService) {
        this.assetRepository = assetRepository;
        this.credentialsRepository = credentialsRepository;
        this.userService = userService;
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
    public void updateAssetOwners(AssetOwnerUpdateDTO updateDTO) {
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

    public List<Asset> getAllAssets() {
        return assetRepository.findByDeletedFalse();
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
} 