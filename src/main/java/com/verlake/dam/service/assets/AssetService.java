package com.verlake.dam.service.assets;

import com.fasterxml.jackson.core.JsonParseException;
import com.verlake.dam.entity.assets.AccessLevel;
import com.verlake.dam.entity.assets.AssetApprover;
import com.verlake.dam.entity.assets.dto.AccessRequestDTO;
import com.verlake.dam.entity.assets.dto.AssetDTO;
import com.verlake.dam.entity.assets.dto.AssetUpdateDTO;
import com.verlake.dam.entity.firebase.NotificationMessage;
import com.verlake.dam.entity.firebase.NotificationTask;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.enums.EmailType;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.exception.DatabaseAccessException;
import com.verlake.dam.repository.NotificationTaskRepository;
import com.verlake.dam.repository.assets.*;
import com.verlake.dam.service.UserService;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import jakarta.persistence.Access;
import org.apache.hadoop.yarn.exceptions.ResourceNotFoundException;
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
    private final NotificationTaskRepository notificationTaskRepository;

    @Autowired
    public AssetService(AssetRepository assetRepository,
            AssetCredentialsRepository credentialsRepository,
            AssetApproversRepository assetApproversRepository,
            AccessLevelRepository accessLevelRepository,
            UserService userService, AccessRequestRepository accessRequestRepository,
            AssetCredentialsRepository assetCredentialsRepository,
            AssetObjectRepository assetObjectRepository,
            KeycloakService keycloakService, DatabaseAccessService databaseAccessService,
            NotificationTaskRepository notificationTaskRepository) {
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
        this.notificationTaskRepository = notificationTaskRepository;
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
                credentials.forEach(assetObjectRepository::deleteByAssetCredential);
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
                .collect(Collectors.toList());
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

    public List<AccessRequest> getAssetRequestApprovals() {
        User currentUser = userService.getCurrentUser();
        List<AssetCredential> assetCredentials = credentialsRepository.findByUserId(currentUser.getId());
        return assetCredentials.stream()
                .map(credential -> accessRequestRepository.findByAsset(credential.getAsset()))
                .flatMap(List::stream)
                .sorted((a1, a2) -> a2.getRequestTime().compareTo(a1.getRequestTime()))
                .toList();
    }

    public AccessRequest setApprovalStatusOfAccessRequest(Long accessRequestId, AccessRequestDTO accessRequestDTO, ApprovalStatus approvalStatus)
            throws JsonParseException {
        AccessRequest accessRequest = accessRequestRepository.findById(accessRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Access Request Not Found"));

        Map<String, String> newCredMapper = new HashMap<>();
        if (approvalStatus == ApprovalStatus.APPROVED) {
            // Find existing credential or create new one
            Optional<AssetCredential> existingCredential = assetCredentialsRepository
                    .findByUserAndAssetAndUserAccessType(
                            accessRequest.getRequestor(),
                            accessRequest.getAsset(),
                            Roles.DEVELOPER.getOriginalName());

            String existUsername = "";

            if (existingCredential.isPresent()) {
                // Generate new username and password
                existUsername = existingCredential.get().getUsername();
            } else {
                // Set AccessRequest's temporary password flag to true if the username is not exist
                accessRequest.setIsTempPassword(true);
            }
            checkUserAndSetCredentials(accessRequest.getAsset().getId(), accessRequest.getRequestor(), accessRequest,
                    existUsername, newCredMapper);
            Optional<AssetCredential> devCredential = assetCredentialsRepository
                    .findByUserAndAssetAndUserAccessType(
                            accessRequest.getRequestor(),
                            accessRequest.getAsset(),
                            Roles.DEVELOPER.getOriginalName());
            devCredential.ifPresent(accessRequest::setAssetCredential);
        }
        accessRequest.setAssetApproverStatus(approvalStatus);

        // Set expiry hours (default to 3 months = 2160 hours if not provided)
        accessRequest.setExpiryHours(accessRequestDTO != null && accessRequestDTO.getExpirationHours() != null && accessRequestDTO.getExpirationHours() != 0 ? accessRequestDTO.getExpirationHours() : Constants.ACCESS_REQUEST_DEFAULT_EXPIRY_HOURS);
        // Calculate expiry date
        accessRequest.setExpiryDate(LocalDateTime.now().plusHours(accessRequest.getExpiryHours()));

        accessRequestRepository.save(accessRequest);
        User currentUser = userService.getCurrentUser();
        Map<String, String> notificationData = new HashMap<>();
        notificationData.put("requestId", accessRequest.getId().toString());
        notificationData.put("assetId", accessRequest.getAsset().getId().toString());
        notificationData.put("assetName", accessRequest.getAsset().getName());
        notificationData.put("assetDescription", accessRequest.getAsset().getDescription());
        notificationData.put("developerName",
                accessRequest.getRequestor().getFirstName() + " " + accessRequest.getRequestor().getLastName());
        notificationData.put("approverName",
                currentUser.getFirstName() + " " + currentUser.getLastName());
        notificationData.put("approvalStatus", approvalStatus.name());
        notificationData.put("messageType", "1"); //1 : success, 0: fail
        if (!newCredMapper.isEmpty()) {
            notificationData.put(Constants.EMAIL_VAR_DB_USERNAME, newCredMapper.get(Constants.EMAIL_VAR_DB_USERNAME));
            notificationData.put(Constants.EMAIL_VAR_DB_PASSWORD, newCredMapper.get(Constants.EMAIL_VAR_DB_PASSWORD));
        }
        sendApprovalNotificationAndEmail(currentUser, accessRequest.getRequestor(), accessRequest.getAsset(),
                notificationData, approvalStatus);
        return accessRequest;
    }

    private void sendApprovalNotificationAndEmail(User approver, User receiver, Asset asset,
            Map<String, String> notificationData, ApprovalStatus approvalStatus) throws JsonParseException {
        NotificationMessage notificationMessage = new NotificationMessage();
        notificationMessage.setTitle("Approval Result of Asset Access Request");
        if (approvalStatus == ApprovalStatus.APPROVED) {
            if (notificationData.get(Constants.EMAIL_VAR_DB_USERNAME) != null
                    && notificationData.get(Constants.EMAIL_VAR_DB_PASSWORD) != null) {
                notificationMessage.setBody(String.format("""
                        %s %s has approved your request access of asset '%s'
                        Database Username: %s
                        Database Password: %s
                        """,
                        approver.getFirstName(),
                        approver.getLastName(),
                        asset.getName(),
                        notificationData.get(Constants.EMAIL_VAR_DB_USERNAME),
                        notificationData.get(Constants.EMAIL_VAR_DB_PASSWORD)));
            } else {
                notificationMessage.setBody(String.format("%s %s has approved your request access of asset '%s'",
                        approver.getFirstName(), approver.getLastName(), asset.getName()));
            }
        } else if (approvalStatus == ApprovalStatus.REJECTED) {
            notificationMessage.setBody(String.format("%s %s has rejected your request access of asset '%s'",
                    approver.getFirstName(), approver.getLastName(), asset.getName()));
        }
        notificationData.put(Constants.NOTIFY_DATA_ATTR_RECEIVER_ID, receiver.getId().toString());
        notificationMessage.setData(notificationData);
        notificationMessage.setTopic("dam_notification");

        // Create and save notification task
        NotificationTask task = new NotificationTask();
        task.setReceiver(receiver);
        task.setSender(approver);
        task.setAsset(asset);
        task.setNotificationMessage(notificationMessage.toJson());
        task.setEmailType(EmailType.APPROVAL_ASSET_ACCESS_REQUEST);
        notificationTaskRepository.save(task);
    }

    private void checkUserAndSetCredentials(Long assetId, User requestor, AccessRequest accessRequest,
            String existUsername, Map<String, String> newCredMapper) {
        User currentUser = userService.getCurrentUser();
        final String userKey = keycloakService.getUserKey(CommonUtils.getKeycloakUserIdFromSession());

        //Asset Credential has user_access_type, get credentials which are only asset owner's
        final List<AssetCredential> credentials = assetCredentialsRepository.findByAssetIdAndUserAccessType(assetId, Roles.ASSET_OWNER.getOriginalName());

        List<AssetCredential> validCredentials = credentials.stream()
                .filter(cred -> {
                    // Check if credential has required fields
                    boolean hasValidCredentials = cred.getUsername() != null && !cred.getUsername().isEmpty()
                            && cred.getPassword() != null && !cred.getPassword().isEmpty();

                    // Check if user is the owner of the asset associated with this credential
                    boolean isAssetOwner = cred.getUser() != null && cred.getUser().getId().equals(currentUser.getId());

                    return hasValidCredentials && isAssetOwner;
                })
                .toList();

        if (validCredentials.isEmpty()) {
            throw new ResourceNotFoundException("No valid credentials found for the current user");
        }

        AssetCredential cred = validCredentials.get(0);
        try {
            String decryptedPassword = CommonUtils.decrypt(userKey, cred.getPassword());
            AssetCredential assetOwnerCred = new AssetCredential();
            assetOwnerCred.setUsername(cred.getUsername());
            assetOwnerCred.setPassword(decryptedPassword);
            assetOwnerCred.setAsset(cred.getAsset());
            assetOwnerCred.setUser(cred.getUser());

            databaseAccessService.checkAccessRequestorInAsset(assetOwnerCred, requestor, accessRequest, existUsername,
                    newCredMapper);
        } catch (Exception e) {
            throw new DatabaseAccessException(
                    "Failed to update database objects upon asset owner login due to credential errors. credential: "
                            + cred.getId(),
                    e);
        }
    }
}
