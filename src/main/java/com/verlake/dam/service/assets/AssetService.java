package com.verlake.dam.service.assets;

import com.fasterxml.jackson.core.JsonParseException;
import com.verlake.dam.entity.assets.AccessLevel;
import com.verlake.dam.entity.assets.AssetApprover;
import com.verlake.dam.entity.assets.dto.*;
import com.verlake.dam.entity.firebase.NotificationMessage;
import com.verlake.dam.entity.firebase.NotificationTask;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.enums.EmailType;
import com.verlake.dam.enums.LockType;
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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.enums.AssetType;
import com.verlake.dam.enums.UnixServerType;

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
                .unixServerType(assetDTO.getUnixServerType())
                .hostAddress(assetDTO.getHostAddress())
                .portNumber(assetDTO.getPortNumber())
                .databaseName(assetDTO.getDatabaseName())
                .deleted(false)
                .build();

        // Ping the asset to test connectivity and catch any copy-paste errors
        PingResult pingResult = pingAsset(asset);
        
        if (!pingResult.isSuccess()) {
            throw new ResourceNotFoundException(String.format("Asset ping failed during creation. Asset: %s, Error: %s", asset.getName(), pingResult.getMessage()));
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
                    .userAccessType(Roles.ASSET_OWNER.getOriginalName())
                    .build();
            credentialsRepository.save(credentials);
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
                .orElseThrow(() -> new ResourceNotFoundException(Constants.getMessage(Constants.ASSET_NOT_FOUND)));
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
        } else if (updateDTO.getMethod().equals(Constants.getMessage("asset.remove.name"))) {
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
                .orElseThrow(() -> new ResourceNotFoundException(Constants.getMessage(Constants.ASSET_NOT_FOUND)));
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
                .orElseThrow(() -> new ResourceNotFoundException(Constants.getMessage(Constants.ASSET_NOT_FOUND)));

        // Soft delete the asset
        asset.setDeleted(true);

        // Wipe all credentials
        credentialsRepository.deleteByAssetId(asset.getId());

        assetRepository.save(asset);
    }

    public Asset findById(Long id) {
        return assetRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(Constants.getMessage(Constants.ASSET_NOT_FOUND)));
    }

    public AssetDTO findDTOById(Long id) {
        Asset asset = assetRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(Constants.getMessage(Constants.ASSET_NOT_FOUND)));
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
                .locked(asset.isLocked())
                .lockType(asset.getLockType())
                .owners(owners)
                .accessRequest(requests.isEmpty() ? null : requests.get(0))
                .approvers(approvers)
                .build();
    }

    private AssetDTO convertToDTOWithFetchAccessTemplate(Asset asset) {
        AssetDTO dto = convertToDTO(asset);
        AccessLevel fetchAccess = accessLevelRepository.findFetchAccessTemplate(asset.getType(),
                asset.getDatabaseType());
        User requestor = userService.findByEmail(CommonUtils.getEmailFromSession());
        List<AccessRequest> requests = accessRequestRepository.findByAssetAndRequestor(asset, requestor);
        dto.setAccessRequest(requests.isEmpty() ? null : requests.get(0));
        dto.setFetchTemplate(fetchAccess != null ? fetchAccess.getAccessTemplate() : null);
        dto.setLocked(asset.isLocked());
        dto.setLockType(asset.getLockType());
        return dto;
    }

    @Transactional
    public void updateAsset(Long id, AssetDTO updateDTO) {
        Asset asset = assetRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(Constants.getMessage(Constants.ASSET_NOT_FOUND)));

        asset.setName(updateDTO.getName());
        asset.setDescription(updateDTO.getDescription());
        asset.setType(updateDTO.getType());
        asset.setDatabaseType(updateDTO.getDatabaseType());
        asset.setUnixServerType(updateDTO.getUnixServerType());
        asset.setHostAddress(updateDTO.getHostAddress());
        asset.setPortNumber(updateDTO.getPortNumber());
        asset.setDatabaseName(updateDTO.getDatabaseName());

        // Ping the asset to test connectivity and catch any copy-paste errors in updated details
        PingResult pingResult = pingAsset(asset);
        
        if (!pingResult.isSuccess()) {
            throw new ResourceNotFoundException(String.format("Asset ping failed during update. Asset: %s, Error: %s", asset.getName(), pingResult.getMessage()));
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

    /**
     * Lock out users in the asset database
     * This is a critical security operation - coded defensively
     * 
     * @param assetId The asset ID
     * @param lockAllUsers If true, locks all database users. If false, only locks Hagrid users.
     * @return Map containing operation results
     * @throws SecurityException if current user is not admin or asset owner
     * @throws IllegalArgumentException if asset not found
     */
    @Transactional
    public Map<String, Object> lockoutAssetUsers(Long assetId, boolean lockAllUsers) {
        log.warn("=== CRITICAL SECURITY OPERATION: Asset lockout initiated ===");
        log.warn("Asset ID: {}, Lock all users: {}, Admin: {}", 
                assetId, lockAllUsers, CommonUtils.getEmailFromSession());
        
        // Defensive validation
        if (assetId == null || assetId <= 0) {
            throw new IllegalArgumentException("Invalid asset ID");
        }
        
        Asset asset = validateAssetAndAccess(assetId, "lockout");
        AssetCredential adminCredential = findAdminCredentialForAsset(asset);
        
        try {
            Map<String, Object> result = databaseAccessService.lockoutAssetUsers(asset, adminCredential, lockAllUsers);
            
            // Update asset locked status and lock type
            asset.setLocked(true);
            asset.setLockType(lockAllUsers ? LockType.LOCK_ALL_DB_USERS : LockType.LOCK_HAGRID_ONLY);
            assetRepository.save(asset);
            
            log.warn("Asset lockout completed successfully for asset: {} (ID: {})", 
                    asset.getName(), assetId);
            
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
     * @param unlockAllUsers If true, unlocks all database users. If false, only unlocks Hagrid users.
     * @return Map containing operation results
     * @throws SecurityException if current user is not admin or asset owner
     * @throws IllegalArgumentException if asset not found
     */
    @Transactional
    public Map<String, Object> unlockAssetUsers(Long assetId, boolean unlockAllUsers) {
        log.warn("=== CRITICAL SECURITY OPERATION: Asset unlock initiated ===");
        log.warn("Asset ID: {}, Unlock all users: {}, Admin: {}", 
                assetId, unlockAllUsers, CommonUtils.getEmailFromSession());
        
        // Defensive validation
        if (assetId == null || assetId <= 0) {
            throw new IllegalArgumentException("Invalid asset ID");
        }
        
        Asset asset = validateAssetAndAccess(assetId, "unlock");
        AssetCredential adminCredential = findAdminCredentialForAsset(asset);
        
        try {
            Map<String, Object> result = databaseAccessService.unlockAssetUsers(asset, adminCredential, unlockAllUsers);
            
            // Update asset locked status and clear lock type
            asset.setLocked(false);
            asset.setLockType(null);
            assetRepository.save(asset);
            
            log.warn("Asset unlock completed successfully for asset: {} (ID: {})", 
                    asset.getName(), assetId);
            
            return result;
            
        } catch (Exception e) {
            log.error("CRITICAL: Asset unlock failed for asset ID: {} - {}", assetId, e.getMessage(), e);
            throw new DatabaseAccessException("Asset unlock failed: " + e.getMessage(), e);
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
        if (!hasAdminAccess(currentUser, asset)) {
            String errorMsg = String.format("User %s does not have admin access for %s operation on asset ID: %s", 
                                          currentUser.getEmail(), operation, assetId);
            log.error("SECURITY VIOLATION: {}", errorMsg);
            throw new SecurityException(errorMsg);
        }
        
        return asset;
    }

    /**
     * Checks if user has admin access (is admin or asset owner)
     */
    private boolean hasAdminAccess(User user, Asset asset) {
        // Check if user is system admin
        if (user.getRoles().stream().anyMatch(role -> role.getName().equals(Roles.ADMIN.getOriginalName()))) {
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
     * Pings an asset to test connectivity without credentials
     * This method can be used to verify that the asset's connection details are correct
     * 
     * @param asset The asset to ping
     * @return PingResult containing success status and response time
     */
    public PingResult pingAsset(Asset asset) {
        if (asset == null) {
            return PingResult.failure("Asset is null");
        }

        if (asset.getHostAddress() == null || asset.getHostAddress().isEmpty()) {
            return PingResult.failure("Invalid host address: " + asset.getHostAddress());
        }

        // For Unix Server assets, we don't need to ping database
        if (asset.getType() == com.verlake.dam.enums.AssetType.UNIX_SERVER) {
            // For Unix servers, just check if host is reachable via basic connectivity
            // This is a simplified check - in production you might want to implement
            // actual SSH connectivity testing
            return PingResult.success("Unix server host address validated", 0);
        }

        if (asset.getDatabaseType() == null) {
            return PingResult.failure("Database type is not specified");
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
            } else if (isConnectionError(errorMessage)) {
                // Connection error indicates URL or network issues
                return PingResult.failure("Connection failed: " + errorMessage, responseTime.toMillis());
            } else {
                // Other errors
                return PingResult.failure("Unexpected error: " + errorMessage, responseTime.toMillis());
            }
        } catch (Exception e) {
            Duration responseTime = Duration.between(startTime, Instant.now());
            return PingResult.failure("Unexpected error: " + e.getMessage(), responseTime.toMillis());
        }
    }

    /**
     * Pings an existing asset to test connectivity without credentials
     * This method can be used to verify that the asset's connection details are correct
     * 
     * @param assetId The ID of the asset to ping
     * @return PingResult containing success status and response time
     * @throws ResourceNotFoundException if asset not found
     */
    public PingResult pingAsset(Long assetId) {
        Asset asset = findById(assetId);
        log.info("Pinging asset: {} (ID: {})", asset.getName(), assetId);
        
        PingResult result = pingAsset(asset);
        
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
            default -> throw new IllegalArgumentException("Unsupported database type: " + asset.getDatabaseType());
        };
    }

    /**
     * Checks if the SQL exception is authentication-related
     */
    private boolean isAuthenticationError(String errorMessage) {
        if (errorMessage == null) return false;
        
        String message = errorMessage.toLowerCase();
        return message.contains("access denied") ||
               message.contains("authentication") ||
               message.contains("login failed") ||
               message.contains("invalid credentials") ||
               message.contains("user") && message.contains("password") ||
               message.contains("authentication failed") ||
               message.contains("access denied for user") ||
               message.contains("password authentication failed") ||
               message.contains("login failed for user");
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
               message.contains("timeout") ||
               message.contains("unable to connect");
    }
    
    /**
     * Create SSH credentials for a Unix Server asset
     */
    @Transactional
    public AssetCredential createSSHCredential(Long assetId, AssetCredentialDTO createDTO) {
        Asset asset = findById(assetId);
        
        if (asset.getType() != AssetType.UNIX_SERVER) {
            throw new IllegalArgumentException("Asset must be of type UNIX_SERVER");
        }
        
        User currentUser = userService.getCurrentUser();
        
        // Check if user is asset owner
        if (!userService.isAssetOwner(currentUser)) {
            throw new SecurityException("User is not an asset owner for this asset");
        }
        
        // Encrypt the SSH key file
        String userKey = keycloakService.getUserKey();
        if (userKey == null || userKey.isEmpty()) {
            throw new SecurityException("User encryption key not available");
        }
        
        String encryptedSSHKey;
        try {
            encryptedSSHKey = CommonUtils.encrypt(userKey, createDTO.getSshKeyFile());
        } catch (CommonUtils.CryptoException e) {
            throw new SecurityException("Failed to encrypt SSH key", e);
        }
        
        AssetCredential credential = AssetCredential.builder()
                .asset(asset)
                .user(currentUser)
                .username(createDTO.getUsername())
                .sshKeyFile(encryptedSSHKey)
                .userAccessType(Roles.ASSET_OWNER.getOriginalName())
                .build();
        
        return assetCredentialsRepository.save(credential);
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
}
