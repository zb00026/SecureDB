package com.verlake.dam.service.assets;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.verlake.dam.entity.assets.*;
import com.verlake.dam.entity.assets.dto.*;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.AssetType;
import com.verlake.dam.enums.DatabaseType;
import com.verlake.dam.enums.LockType;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.exception.AssetLockedException;
import com.verlake.dam.exception.DatabaseAccessException;
import com.verlake.dam.repository.assets.*;
import com.verlake.dam.service.assets.common.DatabaseConnectionUtils;
import com.verlake.dam.service.assets.mongodb.MongoDBConnectionUtils;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AssetService {
    private final AssetRepository assetRepository;
    private final AssetApproversRepository assetApproversRepository;
    private final AccessLevelRepository accessLevelRepository;
    private final UserService userService;
    private final AccessRequestRepository accessRequestRepository;
    private final AssetCredentialsRepository assetCredentialsRepository;
    private final AssetObjectRepository assetObjectRepository;
    private final KeycloakService keycloakService;
    private final DatabaseAccessService databaseAccessService;
    private final DatabaseConnectionUtils databaseConnectionUtils;
    private final AccessLevelObjectRepository accessLevelObjectRepository;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    private static final Logger logger = LoggerFactory.getLogger(AssetService.class);

    @Autowired
    public AssetService(AssetRepository assetRepository,
                        AssetApproversRepository assetApproversRepository,
                        AccessLevelRepository accessLevelRepository,
                        UserService userService, AccessRequestRepository accessRequestRepository,
                        AssetCredentialsRepository assetCredentialsRepository,
                        AssetObjectRepository assetObjectRepository,
                        KeycloakService keycloakService, DatabaseAccessService databaseAccessService,
                        DatabaseConnectionUtils databaseConnectionUtils,
                        AccessLevelObjectRepository accessLevelObjectRepository) {
        this.assetRepository = assetRepository;
        this.assetApproversRepository = assetApproversRepository;
        this.accessLevelRepository = accessLevelRepository;
        this.userService = userService;
        this.accessRequestRepository = accessRequestRepository;
        this.assetCredentialsRepository = assetCredentialsRepository;
        this.assetObjectRepository = assetObjectRepository;
        this.keycloakService = keycloakService;
        this.databaseAccessService = databaseAccessService;
        this.databaseConnectionUtils = databaseConnectionUtils;
        this.accessLevelObjectRepository = accessLevelObjectRepository;
    }

    @Transactional
    public Asset createAsset(AssetDTO assetDTO) {
        Asset asset = Asset.builder()
                .name(assetDTO.getName())
                .description(assetDTO.getDescription())
                .type(assetDTO.getType())
                .databaseType(assetDTO.getDatabaseType())
                .unixServerType(assetDTO.getUnixServerType())
                .hostAddress(assetDTO.getHostAddress())
                .portNumber(assetDTO.getPortNumber())
                .databaseName(assetDTO.getDatabaseName())
                .deleted(false)
                .build();

        // Ping the asset to test connectivity and catch any copy-paste errors
        PingResult pingResult = pingAsset(asset);
        
        if (!pingResult.isSuccess()) {
            String errorMessage = cleanErrorMessage(pingResult.getMessage());
            String detailedError = String.format("Failed to connect to asset '%s'. %s", asset.getName(), errorMessage);
            throw new DatabaseAccessException(detailedError, null);
        } else {
            log.info("Asset ping successful during creation. Asset: {}, Response: {}ms", 
                    asset.getName(), pingResult.getResponseTimeMs());
        }

        // Save the asset first to get the ID
        asset = assetRepository.save(asset);

        // Create asset owners if provided
        if (assetDTO.getOwners() != null && !assetDTO.getOwners().isEmpty()) {
            createAssetOwners(asset, assetDTO.getOwners());
        }

        // Create asset approvers if provided
        if (assetDTO.getApprovers() != null && !assetDTO.getApprovers().isEmpty()) {
            createAssetApprovers(asset, assetDTO.getApprovers());
        }

        return asset;
    }

    /**
     * Create asset owners (credentials) for the given users
     */
    private void createAssetOwners(Asset asset, List<User> owners) {
        for (User owner : owners) {
            AssetCredential credentials = AssetCredential.builder()
                    .asset(asset)
                    .user(owner)
                    .username(null)
                    .password(null)
                    .isTemporaryPassword(true)  // New asset owners start with temporary password
                    .userAccessType(Roles.ASSET_OWNER.getOriginalName())
                    .build();
            assetCredentialsRepository.save(credentials);
        }
    }

    /**
     * Create asset approvers for the given users
     */
    private void createAssetApprovers(Asset asset, List<User> approvers) {
        for (User approver : approvers) {
            AssetApprover assetApprover = AssetApprover.builder()
                    .asset(asset)
                    .user(approver)
                    .build();
            assetApproversRepository.save(assetApprover);
        }
    }

    @Transactional
    public void updateAssetOwners(AssetUpdateDTO updateDTO) {
        Asset asset = assetRepository.findByIdAndDeletedFalse(updateDTO.getAssetId())
                .orElseThrow(() -> new IllegalArgumentException(Constants.getMessage(Constants.ASSET_NOT_FOUND)));
        if (updateDTO.getMethod().equals(Constants.ASSET_ADD_NAME)) {
            // Delete existing credentials for these users if they exist
            updateDTO.getUserIds().forEach(userId -> {
                List<AssetCredential> credentials = assetCredentialsRepository.findByAssetIdAndUserId(asset.getId(), userId);
                credentials.forEach(assetObjectRepository::deleteByAssetCredential);
                assetCredentialsRepository.deleteByAssetIdAndUserId(asset.getId(), userId);
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
                        .isTemporaryPassword(true)  // New asset owners start with temporary password
                        .userAccessType(Roles.ASSET_OWNER.getOriginalName())
                        .build();
                assetCredentialsRepository.save(credentials);
            }
        } else if (updateDTO.getMethod().equals(Constants.getMessage("asset.remove.name"))) {
            // Delete credentials for provided user IDs
            updateDTO.getUserIds().forEach(userId -> {
                List<AssetCredential> credentials = assetCredentialsRepository.findByAssetIdAndUserId(asset.getId(), userId);
                credentials.forEach(assetCredential -> {
                    assetObjectRepository.deleteByAssetCredential(assetCredential);
                    //Remove existing accessor's access request for this asset
                    accessRequestRepository.findByAsset(assetCredential.getAsset()).forEach(accessLevelObjectRepository::deleteByAccessRequest);
                    accessRequestRepository.deleteByAsset(assetCredential.getAsset());
                } );
                assetCredentialsRepository.deleteByAssetIdAndUserId(asset.getId(), userId);
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
                .orElseThrow(() -> new IllegalArgumentException(Constants.getMessage(Constants.ASSET_NOT_FOUND)));
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
        } else if (updateDTO.getMethod().equals(Constants.getMessage("asset.remove.name"))) {
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
                .orElseThrow(() -> new IllegalArgumentException(Constants.getMessage(Constants.ASSET_NOT_FOUND)));

        // Get all credentials for this asset
        List<AssetCredential> credentials = assetCredentialsRepository.findByAssetId(asset.getId());
        
        // Delete asset objects for each credential
        credentials.forEach(assetObjectRepository::deleteByAssetCredential);
        
        // Find all access requests for this asset
        List<AccessRequest> accessRequests = accessRequestRepository.findByAsset(asset);
        
        // Delete access level objects for each access request
        accessRequests.forEach(accessLevelObjectRepository::deleteByAccessRequest);
        
        // Delete all access requests for this asset
        accessRequestRepository.deleteByAsset(asset);
        
        // Now safe to delete all credentials
        assetCredentialsRepository.deleteByAssetId(asset.getId());

        // Soft delete the asset
        asset.setDeleted(true);
        assetRepository.save(asset);
    }

    public Asset findById(Long id) {
        return assetRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException(Constants.getMessage(Constants.ASSET_NOT_FOUND)));
    }

    @Transactional(readOnly = true)
    public AssetDTO findDTOById(Long id) {
        Asset asset = assetRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException(Constants.getMessage(Constants.ASSET_NOT_FOUND)));
        return convertToDTO(asset);
    }

    public List<AssetDTO> getAllAssets() {
        List<Asset> lstAssets = assetRepository.findByDeletedFalse();
        return lstAssets.stream()
                .map(this::convertToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AssetDTO> getAllAssetsWithFetchAccessTemplate() {
        List<Asset> lstAssets = assetRepository.findByDeletedFalse();
        User requestor = userService.findByEmail(CommonUtils.getEmailFromSession());
        
        // Fetch all AccessRequests upfront in a single query to avoid N+1 and ResultSet closure issues
        Map<Asset, LocalDateTime> assetToLatestRequestTime = accessRequestRepository
                .findByRequestor(requestor)
                .stream()
                .filter(ar -> ar.getRequestTime() != null)
                .collect(Collectors.groupingBy(
                    AccessRequest::getAsset,
                    Collectors.mapping(
                        AccessRequest::getRequestTime,
                        Collectors.maxBy(Comparator.naturalOrder())
                    )
                ))
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().orElse(null)
                ));
        
        // Sort assets by AccessRequest requestTime before converting to DTO
        return lstAssets.stream()
                .sorted((asset1, asset2) -> {
                    LocalDateTime time1 = assetToLatestRequestTime.get(asset1);
                    LocalDateTime time2 = assetToLatestRequestTime.get(asset2);
                    
                    if (time1 == null && time2 == null) {
                        return 0;
                    }
                    if (time1 == null) {
                        return 1; // nulls go to the end
                    }
                    if (time2 == null) {
                        return -1; // nulls go to the end
                    }
                    return time2.compareTo(time1); // Descending order (newest first)
                })
                .map(this::convertToDTOWithFetchAccessTemplate)
                .toList();
    }

    public List<Asset> getAssetsOwnedByCurrentUser() {
        User currentUser = userService.getCurrentUser();
        List<AssetCredential> credentials = assetCredentialsRepository.findByUserId(currentUser.getId());
        return credentials.stream()
                .map(AssetCredential::getAsset)
                .toList();
    }

    @Transactional(readOnly = true)
    public AssetDTO convertToDTOByUserType(Asset asset, Roles role) {
        // Fetch all data upfront to avoid ResultSet closure issues
        List<AssetCredential> credentials = assetCredentialsRepository.findByAssetAndUserAccessType(asset, role.getOriginalName());
        List<User> owners = credentials.stream()
                .map(AssetCredential::getUser)
                .toList();

        List<AssetApprover> assetApprovers = assetApproversRepository.findByAssetId(asset.getId());
        List<User> approvers = assetApprovers.stream()
                .map(AssetApprover::getUser)
                .toList();
        
        User requestor = userService.findByEmail(CommonUtils.getEmailFromSession());
        // Fetch access requests upfront before processing
        List<AccessRequest> requests = accessRequestRepository.findNotExpiredByAssetAndRequestor(asset, requestor);

        AssetDTO dto = AssetDTO.fromEntity(asset);
        dto.setOwners(owners);
        dto.setApprovers(approvers);
        dto.setAccessRequest(requests.isEmpty() ? null : AccessRequestSummaryDTO.fromEntity(requests.get(0)));
        return dto;
    }

    public AssetDTO convertToDTO(Asset asset) {
        return convertToDTOByUserType(asset, Roles.ASSET_OWNER);
    }

    public AssetApprovalsDTO convertToApprovalsDTO(Asset asset) {
        return AssetApprovalsDTO.fromEntity(asset);
    }

    private AssetDTO convertToDTOWithFetchAccessTemplate(Asset asset) {
        AssetDTO dto = convertToDTO(asset);
        AccessLevel fetchAccess = accessLevelRepository.findFetchAccessTemplate(asset.getType(),
                asset.getDatabaseType());
        User requestor = userService.findByEmail(CommonUtils.getEmailFromSession());
        List<AccessRequest> requests = accessRequestRepository.findByAssetAndRequestor(asset, requestor);
        dto.setAccessRequest(requests.isEmpty() ? null : AccessRequestSummaryDTO.fromEntity(requests.get(0)));
        dto.setFetchTemplate(fetchAccess != null ? fetchAccess.getAccessTemplate() : null);
        dto.setLocked(asset.isLocked());
        dto.setLockType(asset.getLockType());
        return dto;
    }

    @Transactional
    public void updateAsset(Long id, AssetDTO updateDTO, boolean needSkipDBName) {
        Asset asset = assetRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new IllegalArgumentException(Constants.getMessage(Constants.ASSET_NOT_FOUND)));

        asset.setName(updateDTO.getName());
        asset.setDescription(updateDTO.getDescription());
        asset.setType(updateDTO.getType());
        asset.setDatabaseType(updateDTO.getDatabaseType());
        asset.setUnixServerType(updateDTO.getUnixServerType());
        asset.setHostAddress(updateDTO.getHostAddress());
        asset.setPortNumber(updateDTO.getPortNumber());
        if (!needSkipDBName) {
            asset.setDatabaseName(updateDTO.getDatabaseName());
        }

        // Ping the asset to test connectivity and catch any copy-paste errors in updated details
        PingResult pingResult = pingAsset(asset, needSkipDBName);
        
        if (!pingResult.isSuccess()) {
            String errorMessage = cleanErrorMessage(pingResult.getMessage());
            String detailedError = String.format("Failed to connect to asset '%s'. %s", asset.getName(), errorMessage);
            throw new DatabaseAccessException(detailedError, null);
        } else {
            log.info("Asset ping successful during update. Asset: {} (ID: {}), Response: {}ms", 
                    asset.getName(), id, pingResult.getResponseTimeMs());
        }

        assetRepository.save(asset);
    }

    /**
     * Gets asset credentials that need to be set up for the current user
     *
     * @return List of asset credentials that need setup
     */
    public List<AssetCredential> getNewAssignedCredentials() {
        User currentUser = userService.getCurrentUser();

        return assetCredentialsRepository.findNewAssignedCredentials(currentUser.getId());
    }

    /**
     * Gets asset credentials that need to be set up for the current user
     *
     * @return List of asset credentials that need setup
     */
    public List<AssetCredential> getAssignedCredentials() {

        User currentUser = userService.getCurrentUser();

        List<AssetCredential> credentials = assetCredentialsRepository
                .findByUserAndUserAccessType(currentUser, Roles.ASSET_OWNER.getOriginalName());
        credentials.sort((c1, c2) -> {
            boolean c1Null = c1.getUsername() == null && c1.getPassword() == null;
            boolean c2Null = c2.getUsername() == null && c2.getPassword() == null;
            return Boolean.compare(c2Null, c1Null);
        });
        return credentials;
    }

    public AssetCredential findCredentialByAssetId(Long assetId) {
        List<AssetCredential> credentials = assetCredentialsRepository.findByAssetId(assetId);
        if (credentials.isEmpty()) {
            return null;
        }
        return credentials.get(0);
    }

    public AssetCredential findOwnerCredentialByAssetId(Long assetId) {
        User assetOwner = userService.getCurrentUser();
        return assetCredentialsRepository.findByUserIdAndAssetIdAndUserAccessType(assetOwner.getId(), assetId, Roles.ASSET_OWNER.getOriginalName())
                                        .orElse(null);
    }

    public AssetCredential findCredentialById(Long credentialId) {
        return assetCredentialsRepository.findById(credentialId)
                .orElse(null);
    }

    public AssetCredential saveCredential(AssetCredential assetCredential) {
        return assetCredentialsRepository.save(assetCredential);
    }

    public void deleteAssetCredential(AssetCredential credential) {
        assetCredentialsRepository.delete(credential);
    }

    /**
     * Retrieves access information for a specific asset, showing current database users and their permissions
     * @param assetId The ID of the asset
     * @return AssetAccessDTO containing asset and user access information
     * @throws AccessDeniedException if user has no valid credentials for the asset
     * @throws RuntimeException for technical errors
     */
    public AssetAccessDTO getAssetAccess(Long assetId, String userAccessType) {
        logger.info("=== STARTING getAssetAccess for asset ID: {} ===", assetId);
        
        try {
            Asset asset = findById(assetId);
            
            // Check if asset is locked
            validateAssetNotLocked(asset, false);
            
            User currentUser = userService.getCurrentUser();
            logger.info("Current user: {} (ID: {})", currentUser.getEmail(), currentUser.getId());

            AssetCredential userCredential = findUserCredentialForAsset(assetId, currentUser, userAccessType);
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
    private AssetCredential findUserCredentialForAsset(Long assetId, User currentUser, String userAccessType) {
        // First, try to find user's own credential
        AssetCredential userCredential = assetCredentialsRepository.findByUserIdAndAssetIdAndUserAccessType(currentUser.getId(), assetId, userAccessType).orElse(null);
        
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

    /**
     * Lock out users in the asset database
     * This is a critical security operation - coded defensively
     * 
     * @param assetId The asset ID
     * @param lockAllUsers If true, locks all database users. If false, only locks Hagrids users.
     * @return Map containing operation results
     * @throws SecurityException if current user is not admin or asset owner
     * @throws IllegalArgumentException if asset not found
     */
    @Transactional
    public Map<String, Object> lockoutAssetUsers(Long assetId, boolean lockAllUsers) {
        log.warn("=== CRITICAL SECURITY OPERATION: Asset lockout initiated ===");
        log.warn("Asset ID: {}, Lock all users: {}, User: {}", 
                assetId, lockAllUsers, CommonUtils.getEmailFromSession());
        
        // Defensive validation
        if (assetId == null || assetId <= 0) {
            throw new IllegalArgumentException("Invalid asset ID");
        }
        
        Asset asset = validateAssetAndAccess(assetId, "lockout");
        AssetCredential adminCredential = findAdminCredentialForAsset(asset);
        
        User currentUser = userService.getCurrentUser();
        boolean isAdmin = userService.hasRole(currentUser, Roles.ADMIN.getOriginalName());
        
        try {
            Map<String, Object> result = new HashMap<>();
            // For asset owner lockout, always lock all users (lockAllUsers parameter is ignored)
            
            
            if (isAdmin && !lockAllUsers) {
                // Admin lockout: set is_locked = true, lock_type = LOCK_HAGRID_ONLY
                // Use update query to avoid cascading saves to related entities
                assetRepository.updateLockStatus(assetId, true, LockType.LOCK_HAGRID_ONLY);
            } else if (lockAllUsers) {
                validateAssetNotLocked(asset, false);
                result = databaseAccessService.lockoutAssetUsers(asset, adminCredential, true);
                // Detach credential from persistence context to prevent saving decrypted password
                entityManager.detach(adminCredential);
                // Asset owner lockout: don't update is_locked, set lock_type = LOCK_ALL_DB_USERS
                // Use update query to avoid cascading saves to related entities
                assetRepository.updateLockType(assetId, LockType.LOCK_ALL_DB_USERS);
            }
            
            log.warn("Asset lockout completed successfully for asset: {} (ID: {}) by {}", 
                    asset.getName(), assetId, isAdmin ? "admin" : "asset owner");
            
            return result;
            
        } catch (Exception e) {
            log.error("CRITICAL: Asset lockout failed for asset ID: {} - {}", assetId, e.getMessage(), e);
            throw new DatabaseAccessException("Asset lockout failed: " + e.getMessage(), e);
        }
    }

    /**
     * Unlock users in the asset database
     * This is a critical security operation - coded defensively
     * 
     * @param assetId The asset ID
     * @param unlockAllUsers If true, unlocks all database users. If false, only unlocks Hagrids users.
     * @return Map containing operation results
     * @throws SecurityException if current user is not admin or asset owner
     * @throws IllegalArgumentException if asset not found
     */
    @Transactional
    public Map<String, Object> unlockAssetUsers(Long assetId, boolean unlockAllUsers) {
        log.warn("=== CRITICAL SECURITY OPERATION: Asset unlock initiated ===");
        log.warn("Asset ID: {}, Unlock all users: {}, User: {}", 
                assetId, unlockAllUsers, CommonUtils.getEmailFromSession());
        
        // Defensive validation
        if (assetId == null || assetId <= 0) {
            throw new IllegalArgumentException("Invalid asset ID");
        }
        
        Asset asset = validateAssetAndAccess(assetId, "unlock");
        AssetCredential adminCredential = findAdminCredentialForAsset(asset);
        
        User currentUser = userService.getCurrentUser();
        boolean isAdmin = userService.hasRole(currentUser, Roles.ADMIN.getOriginalName());
        
        try {
            Map<String, Object> result = new HashMap<>();
            // For asset owner unlock, always unlock all users (unlockAllUsers parameter is ignored)
            if (isAdmin && !unlockAllUsers) {
                // Admin unlock: set is_locked = false, clear lock_type
                // Use update query to avoid cascading saves to related entities
                assetRepository.updateLockStatus(assetId, false, null);
            } else if (unlockAllUsers) {
                validateAssetNotLocked(asset, false);
                result = databaseAccessService.unlockAssetUsers(asset, adminCredential, true);
                // Detach credential from persistence context to prevent saving decrypted password
                entityManager.detach(adminCredential);
                // Asset owner unlock: don't update is_locked, clear lock_type
                // Use update query to avoid cascading saves to related entities
                assetRepository.updateLockType(assetId, null);
            }
            
            log.warn("Asset unlock completed successfully for asset: {} (ID: {}) by {}", 
                    asset.getName(), assetId, isAdmin ? "admin" : "asset owner");
            
            return result;
            
        } catch (Exception e) {
            log.error("CRITICAL: Asset unlock failed for asset ID: {} - {}", assetId, e.getMessage(), e);
            throw new DatabaseAccessException("Asset unlock failed: " + e.getMessage(), e);
        }
    }

    /**
     * Pings a Unix server by testing SSH port connectivity
     * 
     * @param asset The Unix server asset to ping
     * @return PingResult containing success status and response time
     */
    private PingResult pingUnixServer(Asset asset) {
        String host = asset.getHostAddress();
        int port = 22; // Default SSH port
        
        // Use custom port if provided
        if (asset.getPortNumber() != null && !asset.getPortNumber().isEmpty()) {
            try {
                port = Integer.parseInt(asset.getPortNumber());
            } catch (NumberFormatException e) {
                return PingResult.failure("Invalid port number: " + asset.getPortNumber());
            }
        }
        
        Instant startTime = Instant.now();
        
        try (Socket socket = new Socket()) {
            // Set connection timeout to 5 seconds
            socket.connect(new InetSocketAddress(host, port), 5000);
            
            Duration responseTime = Duration.between(startTime, Instant.now());
            log.info("Unix server {}:{} is reachable (response time: {}ms)", host, port, responseTime.toMillis());
            
            return PingResult.success(
                String.format("SSH server reachable on %s:%d", host, port), 
                responseTime.toMillis()
            );
            
        } catch (IOException e) {
            Duration responseTime = Duration.between(startTime, Instant.now());
            String errorMessage = String.format("Cannot connect to SSH server %s:%d - %s", host, port, e.getMessage());
            log.warn("Unix server connectivity test failed: {}", errorMessage);
            
            return PingResult.failure(errorMessage, responseTime.toMillis());
        }
    }

    /**
     * Validates that an asset is not locked
     * 
     * @param asset The asset to validate
     * @throws AssetLockedException if the asset is locked
     */
    public void validateAssetNotLocked(Asset asset, boolean isAccessor) {
        if (asset == null) {
            throw new IllegalArgumentException("Asset cannot be null");
        }
        
        // Check if asset is locked by admin (is_locked = true)
        if (asset.isLocked()) {
            String message = Constants.getMessage(Constants.ERROR_ASSET_LOCKED) + 
                           " (Asset: " + asset.getName() + ", ID: " + asset.getId() + ")";
            throw new AssetLockedException(message);
        }
        
        // Check if asset owner has locked all users (lock_type = LOCK_ALL_DB_USERS) and current user is accessor
        if (isAccessor && asset.getLockType() == LockType.LOCK_ALL_DB_USERS) {
            String message = Constants.getMessage(Constants.ERROR_ASSET_LOCKED) + 
                           " (Asset: " + asset.getName() + ", ID: " + asset.getId() + 
                           " - Locked by asset owner)";
            throw new AssetLockedException(message);
        }
    }

    /**
     * Validates asset exists and current user has admin access
     * This is defensive validation for critical security operations
     */
    private Asset validateAssetAndAccess(Long assetId, String operation) {
        Asset asset = assetRepository.findByIdAndDeletedFalse(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found with ID: " + assetId));
        
        User currentUser = userService.getCurrentUser();
        
        // Additional security check - ensure user has admin role or is asset owner
        if (!hasOwnerAccess(currentUser, asset)) {
            String errorMsg = String.format("User %s does not have asset owner role for %s operation on asset ID: %s", 
                                          currentUser.getEmail(), operation, assetId);
            log.error("SECURITY VIOLATION: {}", errorMsg);
            throw new SecurityException(errorMsg);
        }
        
        return asset;
    }

    /**
     * Checks if user has admin access (is admin or asset owner)
     */
    private boolean hasOwnerAccess(User user, Asset asset) {
        // Check if user is system admin
        if (userService.isAssetOwner(user)) {
            return true;
        }
        
        // Check if user is asset owner
        List<AssetCredential> ownerCredentials = assetCredentialsRepository
                .findByAssetAndUserAccessType(asset, Roles.ASSET_OWNER.getOriginalName());
        
        return ownerCredentials.stream()
                .anyMatch(cred -> cred.getUser() != null && 
                                cred.getUser().getId().equals(user.getId()));
    }

    /**
     * Finds admin credential for the asset (owner credential with admin privileges)
     * This is used for critical database operations
     */
    private AssetCredential findAdminCredentialForAsset(Asset asset) {
        User curUser = userService.getCurrentUser();
        List<AssetCredential> ownerCredentials = assetCredentialsRepository
                .findByAssetIdAndUserId(asset.getId(), curUser.getId());
        
        AssetCredential adminCredential = ownerCredentials.stream()
                .filter(databaseConnectionUtils::hasValidPassword)
                .findFirst()
                .orElse(null);
        
        if (adminCredential == null) {
            throw new SecurityException("No valid admin credential found for asset: " + asset.getName());
        }
        
        log.debug("Using admin credential: {} for asset: {}", 
                 adminCredential.getUsername(), asset.getName());
        
        return adminCredential;
    }

    /**
     * Pings an asset to test connectivity (backward compatibility - defaults to skip DB name)
     * 
     * @param asset The asset to ping
     * @return PingResult containing success status and response time
     */
    public PingResult pingAsset(Asset asset) {
        return pingAsset(asset, true);
    }

    /**
     * Pings an asset to test connectivity
     * This method can be used to verify that the asset's connection details are correct
     * 
     * @param asset The asset to ping
     * @param needSkipDBName If true, skips database name validation. If false and ASSET_OWNER credential exists,
     *                       will test actual database connection to verify database name is correct.
     * @return PingResult containing success status and response time
     */
    public PingResult pingAsset(Asset asset, boolean needSkipDBName) {
        if (asset == null) {
            return PingResult.failure("Asset is null");
        }

        if (asset.getHostAddress() == null || asset.getHostAddress().isEmpty()) {
            return PingResult.failure("Invalid host address: " + asset.getHostAddress());
        }

        // Validate port number if provided
        String portValidationError = validatePortNumber(asset.getPortNumber());
        if (portValidationError != null) {
            return PingResult.failure(portValidationError);
        }

        // For Unix Server assets, test SSH connectivity
        if (asset.getType() == AssetType.UNIX_SERVER) {
            return pingUnixServer(asset);
        }

        if (asset.getDatabaseType() == null) {
            return PingResult.failure("Database type is not specified");
        }

        // If needSkipDBName is false, try to use ASSET_OWNER credentials to test actual connection
        if (!needSkipDBName) {
            AssetCredential ownerCredential = findOwnerCredentialByAssetId(asset.getId());
            
            // Check if credential exists and belongs to current user
            if (ownerCredential != null &&
                databaseConnectionUtils.hasValidPassword(ownerCredential)) {
                
                return pingDatabaseWithCredentials(asset, ownerCredential);
            }
        }

        // Default behavior: ping without credentials (just test server reachability)
        return pingDatabaseWithoutCredentials(asset);
    }

    /**
     * Pings a database asset with credentials to verify database name and connection
     * 
     * @param asset The database asset to ping
     * @param credential The credential to use for connection
     * @return PingResult containing success status and response time
     */
    private PingResult pingDatabaseWithCredentials(Asset asset, AssetCredential credential) {
        // Handle MongoDB separately since it doesn't use JDBC
        if (asset.getDatabaseType() == DatabaseType.MONGODB) {
            return pingMongoDBWithCredentials(asset, credential);
        }
        
        String jdbcUrl = buildJdbcUrl(asset);
        Instant startTime = Instant.now();
        
        try {
            // Decrypt password
            String decryptedPassword = databaseConnectionUtils.decryptCredentialPassword(credential);
            
            // Try to establish a connection with credentials
            try (Connection connection = DriverManager.getConnection(jdbcUrl, credential.getUsername(), decryptedPassword)) {
                // Connection successful - database name is correct and credentials are valid
                Duration responseTime = Duration.between(startTime, Instant.now());
                
                // Verify we can access the database by checking if it's valid
                boolean isValid = connection.isValid(5); // 5 second timeout
                
                if (isValid) {
                    log.info("Database connection successful for asset: {} (database: {})", 
                            asset.getName(), asset.getDatabaseName());
                    return PingResult.success(
                        String.format("Database connection successful (database: %s)", asset.getDatabaseName()), 
                        responseTime.toMillis()
                    );
                } else {
                    return PingResult.failure("Connection established but database validation failed", 
                            responseTime.toMillis());
                }
            }
        } catch (SQLException e) {
            Duration responseTime = Duration.between(startTime, Instant.now());
            String errorMessage = e.getMessage();
            
            // Check for database name errors
            if (isDatabaseNameError(errorMessage)) {
                return PingResult.failure("Database name error: " + errorMessage, responseTime.toMillis());
            } else if (isAuthenticationError(errorMessage)) {
                return PingResult.failure("Authentication failed: " + errorMessage, responseTime.toMillis());
            } else if (isConnectionError(errorMessage)) {
                return PingResult.failure("Connection failed: " + errorMessage, responseTime.toMillis());
            } else {
                return PingResult.failure("Database connection error: " + errorMessage, responseTime.toMillis());
            }
        } catch (Exception e) {
            Duration responseTime = Duration.between(startTime, Instant.now());
            return PingResult.failure(Constants.ERROR_PREFIX_UNEXPECTED + e.getMessage(), responseTime.toMillis());
        }
    }

    /**
     * Pings a database asset without credentials (just tests server reachability)
     * 
     * @param asset The database asset to ping
     * @return PingResult containing success status and response time
     */
    private PingResult pingDatabaseWithoutCredentials(Asset asset) {
        // Handle MongoDB separately since it doesn't use JDBC
        if (asset.getDatabaseType() == DatabaseType.MONGODB) {
            return pingMongoDBWithoutCredentials(asset);
        }
        
        String jdbcUrl = buildJdbcUrl(asset);
        Instant startTime = Instant.now();
        
        try {
            // Try to establish a connection without credentials
            // This will fail with authentication error, but we can catch it
            // to verify the URL is valid and the server is reachable
            try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
                // If we get here, it means the connection was successful
                // This is unexpected since we didn't provide credentials
                Duration responseTime = Duration.between(startTime, Instant.now());
                return PingResult.success("Connection successful", responseTime.toMillis());
            }
        } catch (SQLException e) {
            Duration responseTime = Duration.between(startTime, Instant.now());
            String errorMessage = e.getMessage();
            
            // Check if the error is authentication-related (which is expected)
            // or if it's a connection/URL issue (which indicates a problem)
            if (isAuthenticationError(errorMessage)) {
                // Authentication error is expected when no credentials provided
                // This means the URL is valid and the server is reachable
                return PingResult.success("Server reachable (authentication required)", responseTime.toMillis());
            } else if (isPortError(errorMessage)) {
                // Port-specific error
                return PingResult.failure("Invalid port number: " + errorMessage, responseTime.toMillis());
            } else if (isConnectionError(errorMessage)) {
                // Connection error indicates URL or network issues
                return PingResult.failure("Connection failed: " + errorMessage, responseTime.toMillis());
            } else if (isConfigurationError(errorMessage)) {
                // Configuration errors like max_allowed_packet - these shouldn't block ping
                // but we should report them as warnings
                return PingResult.failure("Server configuration issue: " + errorMessage, responseTime.toMillis());
            } else {
                // Other errors
                return PingResult.failure(Constants.ERROR_PREFIX_UNEXPECTED + errorMessage, responseTime.toMillis());
            }
        } catch (Exception e) {
            Duration responseTime = Duration.between(startTime, Instant.now());
            return PingResult.failure(Constants.ERROR_PREFIX_UNEXPECTED + e.getMessage(), responseTime.toMillis());
        }
    }

    /**
     * Pings an existing asset to test connectivity
     * This method can be used to verify that the asset's connection details are correct
     * 
     * @param assetId The ID of the asset to ping
     * @param needSkipDBName If true, skips database name validation. If false and ASSET_OWNER credential exists,
     *                       will test actual database connection to verify database name is correct.
     * @return PingResult containing success status and response time
     * @throws IllegalArgumentException if asset not found
     */
    public PingResult pingAsset(Long assetId, boolean needSkipDBName) {
        Asset asset = findById(assetId);
        log.info("Pinging asset: {} (ID: {}), needSkipDBName: {}", asset.getName(), assetId, needSkipDBName);
        
        PingResult result = pingAsset(asset, needSkipDBName);
        
        if (result.isSuccess()) {
            log.info("Asset ping successful: {} - {} ({}ms)", 
                    asset.getName(), result.getMessage(), result.getResponseTimeMs());
        } else {
            log.warn("Asset ping failed: {} - {} ({}ms)", 
                    asset.getName(), result.getMessage(), result.getResponseTimeMs());
        }
        
        return result;
    }

    /**
     * Pings an existing asset to test connectivity without credentials (backward compatibility)
     * This method can be used to verify that the asset's connection details are correct
     * 
     * @param assetId The ID of the asset to ping
     * @return PingResult containing success status and response time
     * @throws IllegalArgumentException if asset not found
     */
    public PingResult pingAsset(Long assetId) {
        return pingAsset(assetId, true);
    }

    /**
     * Pings a MongoDB asset with credentials to verify database name and connection
     * 
     * @param asset The MongoDB asset to ping
     * @param credential The credential to use for connection
     * @return PingResult containing success status and response time
     */
    private PingResult pingMongoDBWithCredentials(Asset asset, AssetCredential credential) {
        Instant startTime = Instant.now();
        
        try {
            // Check if MongoDB driver classes are available
            PingResult driverCheckResult = checkMongoDBDriverAvailabilityWithErrorHandling(startTime);
            if (driverCheckResult != null) {
                return driverCheckResult;
            }
            
            // Decrypt password
            String decryptedPassword = databaseConnectionUtils.decryptCredentialPassword(credential);
            
            // Build MongoDB connection string with credentials
            String connectionString = MongoDBConnectionUtils.buildMongoConnectionString(
                asset, 
                credential.getUsername(), 
                decryptedPassword
            );
            
            // Try to establish a connection with credentials
            try (MongoClient client = MongoClients.create(connectionString)) {
                // Get the database instance
                String databaseName = asset.getDatabaseName();
                if (databaseName == null || databaseName.isEmpty()) {
                    databaseName = Constants.MONGODB_ADMIN_DATABASE; // Default to admin database
                }
                
                MongoDatabase database = client.getDatabase(databaseName);
                
                // Test connection by running a simple command (ping)
                database.runCommand(new Document(Constants.MONGODB_COMMAND_PING, 1));
                
                Duration responseTime = Duration.between(startTime, Instant.now());
                log.info("MongoDB connection successful for asset: {} (database: {})", 
                        asset.getName(), databaseName);
                return PingResult.success(
                    String.format("MongoDB connection successful (database: %s)", databaseName), 
                    responseTime.toMillis()
                );
            }
        } catch (NoClassDefFoundError e) {
            Duration responseTime = Duration.between(startTime, Instant.now());
            String errorMsg = "MongoDB driver classes not found. Please rebuild the project: " + e.getMessage();
            log.error(errorMsg, e);
            return PingResult.failure(errorMsg, responseTime.toMillis());
        } catch (Exception e) {
            return handleMongoDBPingError(e, startTime);
        }
    }
    
    /**
     * Checks if MongoDB driver classes are available
     * 
     * @throws ClassNotFoundException if MongoDB driver is not found
     */
    private void checkMongoDBDriverAvailability() throws ClassNotFoundException {
        Class.forName("com.mongodb.client.MongoClients");
    }
    
    /**
     * Checks MongoDB driver availability and returns failure result if driver is not found
     * 
     * @param startTime The start time for response time calculation
     * @return PingResult with failure if driver not found, null if driver is available
     */
    private PingResult checkMongoDBDriverAvailabilityWithErrorHandling(Instant startTime) {
        try {
            checkMongoDBDriverAvailability();
            return null; // Driver is available
        } catch (ClassNotFoundException e) {
            Duration responseTime = Duration.between(startTime, Instant.now());
            String errorMsg = "MongoDB driver not found on classpath. Please rebuild the project after adding MongoDB dependencies.";
            log.error(errorMsg);
            return PingResult.failure(errorMsg, responseTime.toMillis());
        }
    }
    
    /**
     * Handles MongoDB ping errors and returns appropriate PingResult
     * 
     * @param e The exception that occurred
     * @param startTime The start time for response time calculation
     * @return PingResult with error details
     */
    private PingResult handleMongoDBPingError(Exception e, Instant startTime) {
        Duration responseTime = Duration.between(startTime, Instant.now());
        String errorMessage = e.getMessage();
        
        if (errorMessage != null && errorMessage.contains(Constants.MONGODB_ERROR_AUTHENTICATION)) {
            return PingResult.failure("MongoDB authentication failed: " + errorMessage, responseTime.toMillis());
        } else if (errorMessage != null && (errorMessage.contains(Constants.MONGODB_ERROR_CONNECTION) || errorMessage.contains(Constants.MONGODB_ERROR_TIMEOUT))) {
            return PingResult.failure("MongoDB connection failed: " + errorMessage, responseTime.toMillis());
        } else {
            return PingResult.failure("MongoDB connection error: " + errorMessage, responseTime.toMillis());
        }
    }

    /**
     * Pings a MongoDB asset without credentials (just tests server reachability)
     * 
     * @param asset The MongoDB asset to ping
     * @return PingResult containing success status and response time
     */
    private PingResult pingMongoDBWithoutCredentials(Asset asset) {
        Instant startTime = Instant.now();
        
        try {
            // Check if MongoDB driver classes are available
            PingResult driverCheckResult = checkMongoDBDriverAvailabilityWithErrorHandling(startTime);
            if (driverCheckResult != null) {
                return driverCheckResult;
            }
            
            // Build MongoDB connection string without credentials
            String connectionString = buildMongoConnectionString(asset);
            
            // Try to establish a connection without credentials
            return establishMongoDBConnectionWithoutCredentials(connectionString, startTime);
        } catch (NoClassDefFoundError e) {
            Duration responseTime = Duration.between(startTime, Instant.now());
            String errorMsg = "MongoDB driver classes not found. Please rebuild the project: " + e.getMessage();
            log.error(errorMsg, e);
            return PingResult.failure(errorMsg, responseTime.toMillis());
        } catch (Exception e) {
            Duration responseTime = Duration.between(startTime, Instant.now());
            String errorMessage = e.getMessage();
            
            // If it's an authentication error, that's expected - server is reachable
            if (errorMessage != null && errorMessage.contains(Constants.MONGODB_ERROR_AUTHENTICATION)) {
                return PingResult.success("MongoDB server reachable (authentication required)", responseTime.toMillis());
            } else if (errorMessage != null && (errorMessage.contains(Constants.MONGODB_ERROR_CONNECTION) || errorMessage.contains(Constants.MONGODB_ERROR_TIMEOUT))) {
                return PingResult.failure("MongoDB connection failed: " + errorMessage, responseTime.toMillis());
            } else {
                // For other errors, still consider it as server reachable if we got a response
                return PingResult.success("MongoDB server reachable", responseTime.toMillis());
            }
        }
    }
    
    /**
     * Establishes MongoDB connection without credentials and pings the server
     * 
     * @param connectionString The MongoDB connection string
     * @param startTime The start time for response time calculation
     * @return PingResult containing success status and response time
     */
    private PingResult establishMongoDBConnectionWithoutCredentials(String connectionString, Instant startTime) {
        // MongoDB allows connections without auth for testing server reachability
        try (MongoClient client = MongoClients.create(connectionString)) {
            // Try to ping the server (this works even without authentication on some MongoDB setups)
            // Use admin database for ping test
            MongoDatabase adminDb = client.getDatabase(Constants.MONGODB_ADMIN_DATABASE);
            adminDb.runCommand(new Document(Constants.MONGODB_COMMAND_PING, 1));
            
            Duration responseTime = Duration.between(startTime, Instant.now());
            return PingResult.success("MongoDB server reachable", responseTime.toMillis());
        }
    }

    /**
     * Builds the JDBC URL for an asset
     * 
     * @param asset The asset to build URL for
     * @return Complete JDBC URL string
     */
    private String buildJdbcUrl(Asset asset) {
        return switch (asset.getDatabaseType()) {
            case MYSQL -> Constants.JDBC_MYSQL_URL + asset.getHostUrl();
            case POSTGRESQL -> Constants.JDBC_POSTGRESQL_URL + asset.getHostUrl();
            case ORACLE -> Constants.JDBC_ORACLE_URL + asset.getHostUrl();
            case SQLSERVER -> Constants.JDBC_SQLSERVER_URL + asset.getHostUrl() + Constants.JDBC_SQLSERVER_SSL_PARAMS;
            case MONGODB -> buildMongoConnectionString(asset);
            default -> throw new IllegalArgumentException("Unsupported database type: " + asset.getDatabaseType());
        };
    }
    
    /**
     * Builds MongoDB connection string without credentials (for ping tests)
     * Format: mongodb://host[:port][/database]?authSource=database
     * authSource is set to the database name from the asset, or 'admin' if not specified
     * 
     * @param asset The asset containing MongoDB connection information
     * @return MongoDB connection string
     */
    private String buildMongoConnectionString(Asset asset) {
        StringBuilder connectionString = new StringBuilder(Constants.MONGODB_CONNECTION_URL);
        
        if (asset.getHostAddress() != null && !asset.getHostAddress().isEmpty()) {
            connectionString.append(asset.getHostAddress());
        }
        
        if (asset.getPortNumber() != null && !asset.getPortNumber().isEmpty()) {
            connectionString.append(":").append(asset.getPortNumber());
        }
        
        if (asset.getDatabaseName() != null && !asset.getDatabaseName().isEmpty()) {
            connectionString.append("/").append(asset.getDatabaseName());
        }
        
        // Add authSource parameter (use database name from asset, or default to admin)
        // This is important for MongoDB authentication - authSource specifies which database contains the user
        String authSource = (asset.getDatabaseName() != null && !asset.getDatabaseName().isEmpty()) 
            ? asset.getDatabaseName() 
            : Constants.MONGODB_ADMIN_DATABASE;
        connectionString.append("?authSource=").append(authSource);
        
        return connectionString.toString();
    }

    /**
     * Checks if the SQL exception is authentication-related
     */
    private boolean isAuthenticationError(String errorMessage) {
        if (errorMessage == null) return false;
        
        String message = errorMessage.toLowerCase();
        return message.contains("access denied") ||
               message.contains(Constants.MONGODB_ERROR_AUTHENTICATION) ||
               message.contains("login failed") ||
               message.contains("invalid credentials") ||
               message.contains("user") && message.contains("password") ||
               message.contains("authentication failed") ||
               message.contains("access denied for user") ||
               message.contains("password authentication failed") ||
               message.contains("login failed for user");
    }

    /**
     * Validates port number format
     */
    private String validatePortNumber(String portNumber) {
        if (portNumber == null || portNumber.isEmpty()) {
            return null; // Port is optional
        }
        
        try {
            int port = Integer.parseInt(portNumber);
            if (port < 1 || port > 65535) {
                return String.format("Port number must be between 1 and 65535, got: %s", portNumber);
            }
            return null; // Valid port
        } catch (NumberFormatException e) {
            return String.format("Invalid port number format: '%s'. Port must be a number between 1 and 65535", portNumber);
        }
    }

    /**
     * Checks if the SQL exception is port-related
     */
    private boolean isPortError(String errorMessage) {
        if (errorMessage == null) return false;
        
        String message = errorMessage.toLowerCase();
        return message.contains("invalid port") ||
               message.contains("port number") ||
               message.contains("bad port") ||
               message.contains("port out of range") ||
               (message.contains("connection refused") && message.contains("port"));
    }

    /**
     * Checks if the error is a server configuration issue (like max_allowed_packet)
     */
    private boolean isConfigurationError(String errorMessage) {
        if (errorMessage == null) return false;
        
        String message = errorMessage.toLowerCase();
        return message.contains("max_allowed_packet") ||
               message.contains("packet too large") ||
               message.contains("configuration") ||
               message.contains("server variable");
    }

    /**
     * Checks if the error message indicates a database name error
     */
    private boolean isDatabaseNameError(String errorMessage) {
        if (errorMessage == null) {
            return false;
        }
        
        String lowerMessage = errorMessage.toLowerCase();
        return lowerMessage.contains("unknown database") ||
               (lowerMessage.contains("database") && lowerMessage.contains("does not exist")) ||
               (lowerMessage.contains("database") && lowerMessage.contains("not found")) ||
               (lowerMessage.contains("catalog") && lowerMessage.contains("does not exist")) ||
               lowerMessage.contains("invalid database name") ||
               (lowerMessage.contains("database name") && lowerMessage.contains("invalid"));
    }

    /**
     * Checks if the SQL exception is connection-related
     */
    private boolean isConnectionError(String errorMessage) {
        if (errorMessage == null) return false;
        
        String message = errorMessage.toLowerCase();
        return message.contains("connection refused") ||
               message.contains("connection timed out") ||
               message.contains("no route to host") ||
               message.contains("unknown host") ||
               message.contains("network is unreachable") ||
               message.contains("connection reset") ||
               message.contains("host not found") ||
               message.contains("connection failed") ||
               message.contains(Constants.MONGODB_ERROR_TIMEOUT) ||
               message.contains("unable to connect");
    }

    /**
     * Cleans error message by removing redundant prefixes
     */
    private String cleanErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            return "Unknown error occurred";
        }
        
        String cleaned = errorMessage;
        // Remove common prefixes that add no value
        if (cleaned.startsWith(Constants.ERROR_PREFIX_UNEXPECTED)) {
            cleaned = cleaned.substring(Constants.ERROR_PREFIX_UNEXPECTED.length());
        } else if (cleaned.startsWith("Connection failed: ")) {
            cleaned = cleaned.substring("Connection failed: ".length());
        }
        
        return cleaned.trim();
    }
    
    /**
     * Create SSH credentials for a Unix Server asset
     */
    @Transactional
    public AssetCredential createSSHCredential(Long assetId, AssetCredentialDTO createDTO) {
        Asset asset = validateUnixServerAsset(assetId);
        User currentUser = validateAssetOwner();
        
        AssetCredential.AssetCredentialBuilder credentialBuilder = buildBaseCredentialBuilder(asset, currentUser, createDTO);
        configureSshKey(credentialBuilder, createDTO);
        
        return assetCredentialsRepository.save(credentialBuilder.build());
    }
    
    /**
     * Validates that the asset exists and is a Unix Server type
     */
    private Asset validateUnixServerAsset(Long assetId) {
        Asset asset = findById(assetId);
        if (asset.getType() != AssetType.UNIX_SERVER) {
            throw new IllegalArgumentException("Asset must be of type UNIX_SERVER");
        }
        return asset;
    }
    
    /**
     * Validates that the current user is an asset owner
     */
    private User validateAssetOwner() {
        User currentUser = userService.getCurrentUser();
        if (!userService.isAssetOwner(currentUser)) {
            throw new SecurityException("User is not an asset owner for this asset");
        }
        return currentUser;
    }
    
    /**
     * Builds the base credential builder with common fields
     */
    private AssetCredential.AssetCredentialBuilder buildBaseCredentialBuilder(
            Asset asset, User currentUser, AssetCredentialDTO createDTO) {
        return AssetCredential.builder()
                .asset(asset)
                .user(currentUser)
                .username(createDTO.getUsername())
                .isTemporaryPassword(false)
                .userAccessType(Roles.ASSET_OWNER.getOriginalName());
    }
    
    /**
     * Configures SSH key in the credential builder based on source (AWS Secrets Manager or traditional)
     */
    private void configureSshKey(AssetCredential.AssetCredentialBuilder credentialBuilder, AssetCredentialDTO createDTO) {
        if (isAwsSecretsManagerKeyProvided(createDTO)) {
            configureAwsSecretsManagerSshKey(credentialBuilder, createDTO);
        } else if (isSshKeyFileProvided(createDTO)) {
            configureTraditionalSshKey(credentialBuilder, createDTO);
        } else {
            throw new IllegalArgumentException("Either sshKeyFile or awsSecretsManagerKey must be provided");
        }
    }
    
    /**
     * Checks if AWS Secrets Manager key is provided
     */
    private boolean isAwsSecretsManagerKeyProvided(AssetCredentialDTO createDTO) {
        return createDTO.getAwsSecretsManagerKey() != null 
                && !createDTO.getAwsSecretsManagerKey().trim().isEmpty();
    }
    
    /**
     * Checks if SSH key file is provided
     */
    private boolean isSshKeyFileProvided(AssetCredentialDTO createDTO) {
        return createDTO.getSshKeyFile() != null 
                && !createDTO.getSshKeyFile().trim().isEmpty();
    }
    
    /**
     * Configures credential builder with AWS Secrets Manager SSH key
     */
    private void configureAwsSecretsManagerSshKey(
            AssetCredential.AssetCredentialBuilder credentialBuilder, AssetCredentialDTO createDTO) {
        credentialBuilder.awsSecretsManagerKey(createDTO.getAwsSecretsManagerKey());
        if (isSshKeyFileProvided(createDTO)) {
            credentialBuilder.sshKeyFile(createDTO.getSshKeyFile());
        } else {
            throw new IllegalArgumentException("SSH key file must be provided when using AWS Secrets Manager");
        }
    }
    
    /**
     * Configures credential builder with traditional encrypted SSH key
     */
    private void configureTraditionalSshKey(
            AssetCredential.AssetCredentialBuilder credentialBuilder, AssetCredentialDTO createDTO) {
        String userKey = keycloakService.getUserKey();
        if (userKey == null || userKey.isEmpty()) {
            throw new SecurityException("User encryption key not available");
        }
        
        try {
            String encryptedSSHKey = CommonUtils.encrypt(userKey, createDTO.getSshKeyFile());
            credentialBuilder.sshKeyFile(encryptedSSHKey);
            credentialBuilder.awsSecretsManagerKey(null);
        } catch (CommonUtils.CryptoException e) {
            throw new SecurityException("Failed to encrypt SSH key", e);
        }
    }
    
    
    /**
     * Get SSH credentials for a specific asset and user
     */
    public AssetCredential getSSHCredentialsForAsset(Long assetId, User user) {
        return assetCredentialsRepository.findByAssetIdAndUserId(assetId, user.getId())
                .stream()
                .filter(cred -> cred.getAsset().getType() == AssetType.UNIX_SERVER && cred.getSshKeyFile() != null)
                .findFirst()
                .orElse(null);
    }

    public AssetCredential getSSHCredentialsForAsset(Long assetId, User user, String userAccessType) {
        Asset asset = assetRepository.findById(assetId).orElse(null);
        if (asset == null) {
            return null;
        }
        return assetCredentialsRepository.findByUserAndAssetAndUserAccessType(user, asset, userAccessType)
                .stream()
                .filter(cred -> cred.getAsset().getType() == AssetType.UNIX_SERVER && cred.getSshKeyFile() != null)
                .findFirst()
                .orElse(null);
    }
}
