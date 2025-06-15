package com.verlake.dam.service.assets;

import com.fasterxml.jackson.core.JsonParseException;
import com.verlake.dam.entity.assets.AccessLevel;
import com.verlake.dam.entity.assets.AssetApprover;
import com.verlake.dam.entity.assets.dto.AccessRequestDTO;
import com.verlake.dam.entity.assets.dto.AssetDTO;
import com.verlake.dam.entity.assets.dto.AssetUpdateDTO;
import com.verlake.dam.entity.assets.dto.AssetAccessDTO;
import com.verlake.dam.entity.firebase.NotificationMessage;
import com.verlake.dam.entity.firebase.NotificationTask;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.enums.EmailType;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.exception.DatabaseAccessException;
import com.verlake.dam.repository.NotificationTaskRepository;
import com.verlake.dam.repository.assets.*;
import com.verlake.dam.service.assets.common.DatabaseConnectionUtils;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.email.EmailService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import jakarta.persistence.Access;
import org.apache.hadoop.yarn.exceptions.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.security.Key;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private final AssetCredentialsRepository assetCredentialsRepository;
    private final AssetObjectRepository assetObjectRepository;
    private final KeycloakService keycloakService;
    private final DatabaseAccessService databaseAccessService;
    private final DatabaseConnectionUtils databaseConnectionUtils;
    private final NotificationTaskRepository notificationTaskRepository;
    private final AccessLevelObjectRepository accessLevelObjectRepository;
    private static final Logger logger = LoggerFactory.getLogger(AssetService.class);

    @Autowired
    public AssetService(AssetRepository assetRepository,
                        AssetCredentialsRepository credentialsRepository,
                        AssetApproversRepository assetApproversRepository,
                        AccessLevelRepository accessLevelRepository,
                        UserService userService, AccessRequestRepository accessRequestRepository,
                        AssetCredentialsRepository assetCredentialsRepository,
                        AssetObjectRepository assetObjectRepository,
                        KeycloakService keycloakService, DatabaseAccessService databaseAccessService,
                        DatabaseConnectionUtils databaseConnectionUtils,
                        NotificationTaskRepository notificationTaskRepository, AccessLevelObjectRepository accessLevelObjectRepository) {
        this.assetRepository = assetRepository;
        this.credentialsRepository = credentialsRepository;
        this.assetApproversRepository = assetApproversRepository;
        this.accessLevelRepository = accessLevelRepository;
        this.userService = userService;
        this.accessRequestRepository = accessRequestRepository;
        this.assetCredentialsRepository = assetCredentialsRepository;
        this.assetObjectRepository = assetObjectRepository;
        this.keycloakService = keycloakService;
        this.databaseAccessService = databaseAccessService;
        this.databaseConnectionUtils = databaseConnectionUtils;
        this.notificationTaskRepository = notificationTaskRepository;
        this.accessLevelObjectRepository = accessLevelObjectRepository;
    }

    @Transactional
    public Asset createAsset(AssetDTO assetDTO) {
        Asset asset = Asset.builder()
                .name(assetDTO.getName())
                .description(assetDTO.getDescription())
                .type(assetDTO.getType())
                .databaseType(assetDTO.getDatabaseType())
                .hostAddress(assetDTO.getHostAddress())
                .portNumber(assetDTO.getPortNumber())
                .databaseName(assetDTO.getDatabaseName())
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
                List<AssetCredential> credentials = credentialsRepository.findByAssetIdAndUserId(asset.getId(), userId);
                credentials.forEach(assetObjectRepository::deleteByAssetCredential);
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
                        .userAccessType(Roles.ASSET_OWNER.getOriginalName())
                        .build();
                credentialsRepository.save(credentials);
            }
        } else if (updateDTO.getMethod().equals(Constants.ASSET_REMOVE_NAME)) {
            // Delete credentials for provided user IDs
            updateDTO.getUserIds().forEach(userId -> {
                List<AssetCredential> credentials = credentialsRepository.findByAssetIdAndUserId(asset.getId(), userId);
                credentials.forEach(assetCredential -> {
                    assetObjectRepository.deleteByAssetCredential(assetCredential);
                    //Remove existing developer's access request for this asset
                    accessRequestRepository.findByAsset(assetCredential.getAsset()).forEach(accessLevelObjectRepository::deleteByAccessRequest);
                    accessRequestRepository.deleteByAsset(assetCredential.getAsset());
                } );
                credentialsRepository.deleteByAssetIdAndUserId(asset.getId(), userId);
            });
        }

        assetRepository.save(asset);
    }

    public List<User> getAssetOwners(Asset asset) {
        List<AssetCredential> credentials = assetCredentialsRepository.findByAssetAndUserAccessType(asset, Roles.ASSET_OWNER.getOriginalName());
        return credentials.stream()
                .map(AssetCredential::getUser)
                .distinct()
                .toList();
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

    public List<Asset> getAssetsOwnedByCurrentUser() {
        User currentUser = userService.getCurrentUser();
        List<AssetCredential> credentials = credentialsRepository.findByUserId(currentUser.getId());
        return credentials.stream()
                .map(AssetCredential::getAsset)
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
                .portNumber(asset.getPortNumber())
                .databaseName(asset.getDatabaseName())
                .owners(owners)
                .accessRequest(requests.isEmpty() ? null : requests.get(0))
                .approvers(approvers)
                .build();
    }

    private AssetDTO convertToDTOWithFetchAccessTemplate(Asset asset) {
        AssetDTO dto = convertToDTO(asset);
        AccessLevel fetchAccess = accessLevelRepository.findFetchAccessTemplate(asset.getType().name(),
                asset.getDatabaseType().name());
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
        asset.setPortNumber(updateDTO.getPortNumber());
        asset.setDatabaseName(updateDTO.getDatabaseName());

        assetRepository.save(asset);
    }

    /**
     * Gets asset credentials that need to be set up for the current user
     *
     * @return List of asset credentials that need setup
     */
    public List<AssetCredential> getNewAssignedCredentials() {
        User currentUser = userService.getCurrentUser();

        return credentialsRepository.findNewAssignedCredentials(currentUser.getId());
    }

    /**
     * Gets asset credentials that need to be set up for the current user
     *
     * @return List of asset credentials that need setup
     */
    public List<AssetCredential> getAssignedCredentials() {

        User currentUser = userService.getCurrentUser();

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

    public AssetCredential findOwnerCredentialByAssetId(Long assetId) {
        User assetOwner = userService.getCurrentUser();
        List<AssetCredential> credentials = credentialsRepository.findByAssetIdAndUserId(assetId, assetOwner.getId());
        return credentials.isEmpty() ? null : credentials.get(0);
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

    /**
     * Retrieves access information for a specific asset, showing current database users and their permissions
     * @param assetId The ID of the asset
     * @return AssetAccessDTO containing asset and user access information
     * @throws AccessDeniedException if user has no valid credentials for the asset
     * @throws RuntimeException for technical errors
     */
    public AssetAccessDTO getAssetAccess(Long assetId) {
        logger.info("=== STARTING getAssetAccess for asset ID: {} ===", assetId);
        
        try {
            Asset asset = findById(assetId);
            User currentUser = userService.getCurrentUser();
            logger.info("Current user: {} (ID: {})", currentUser.getEmail(), currentUser.getId());

            AssetCredential userCredential = findUserCredentialForAsset(assetId, currentUser);
            logger.info("Using credential: {} for asset access query", userCredential.getUsername());

            return databaseAccessService.fetchAssetUserAccess(asset, userCredential);
            
        } catch (AccessDeniedException e) {
            logger.warn("Access denied for user and asset ID: {}. Reason: {}", assetId, e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("ERROR in getAssetAccess for asset ID: {}. Exception: {}", assetId, e.getClass().getSimpleName());
            logger.error("Error message: {}", e.getMessage());
            logger.error("Full stack trace:", e);
            
            // Convert credential-related errors to access denied
            if (databaseConnectionUtils.isCredentialRelatedError(e)) {
                throw new AccessDeniedException("User has no access to asset");
            }
            
            throw new AccessDeniedException("Failed to fetch asset access information", e);
        }
    }

    /**
     * Finds the appropriate credential for the current user to access the specified asset
     * @param assetId The asset ID
     * @param currentUser The current user
     * @return AssetCredential to use for database access
     * @throws AccessDeniedException if no valid credentials are found
     */
    private AssetCredential findUserCredentialForAsset(Long assetId, User currentUser) {
        // First, try to find user's own credential
        AssetCredential userCredential = findUserOwnCredential(assetId, currentUser);
        
        if (userCredential != null) {
            logger.info("Found user's own credential for asset ID: {}", assetId);
            return userCredential;
        }

        // If no personal credential, check if user is an asset owner
        AssetCredential ownerCredential = findAssetOwnerCredential(assetId, currentUser);
        
        if (ownerCredential != null) {
            logger.info("Using asset owner credential for user: {}", currentUser.getEmail());
            return ownerCredential;
        }

        logger.warn("No valid credentials found for user {} and asset ID: {}", 
                   currentUser.getEmail(), assetId);
        throw new AccessDeniedException("User has no access to asset");
    }

    /**
     * Finds the user's own credential for the asset
     */
    private AssetCredential findUserOwnCredential(Long assetId, User currentUser) {
        return assetCredentialsRepository
            .findByAssetIdAndUserId(assetId, currentUser.getId())
            .stream()
            .filter(databaseConnectionUtils::hasValidPassword)
            .findFirst()
            .orElse(null);
    }

    /**
     * Finds the asset owner credential if the current user is an asset owner
     */
    private AssetCredential findAssetOwnerCredential(Long assetId, User currentUser) {
        return assetCredentialsRepository
            .findByAssetIdAndUserAccessType(assetId, "Owner")
            .stream()
            .filter(cred -> isValidOwnerCredential(cred, currentUser))
            .findFirst()
            .orElse(null);
    }

    /**
     * Checks if the credential is valid for the owner and belongs to the current user
     */
    private boolean isValidOwnerCredential(AssetCredential credential, User currentUser) {
        return credential.getUser() != null && 
               credential.getUser().getId().equals(currentUser.getId()) &&
               databaseConnectionUtils.hasValidPassword(credential);
    }
}
