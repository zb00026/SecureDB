package com.verlake.dam.service.assets;

import com.verlake.dam.entity.assets.*;
import com.verlake.dam.entity.assets.dto.AccessRequestDTO;
import com.verlake.dam.entity.assets.dto.AssetCredentialDTO;
import com.verlake.dam.entity.assets.dto.AssetDTO;
import com.verlake.dam.entity.firebase.NotificationMessage;
import com.verlake.dam.entity.firebase.NotificationTask;
import com.verlake.dam.enums.AssetType;
import com.verlake.dam.enums.DatabaseType;
import com.verlake.dam.enums.EmailType;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.exception.DatabaseAccessException;
import com.verlake.dam.repository.assets.*;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import com.verlake.dam.utils.CommonUtils.CryptoException;

import org.apache.hadoop.yarn.exceptions.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;
import com.verlake.dam.repository.NotificationTaskRepository;
import com.fasterxml.jackson.core.JsonParseException;

@Service
@Slf4j
public class AccessRequestService {
    private final AccessRequestRepository accessRequestRepository;
    private final AssetService assetService;
    private final AccessLevelObjectRepository accessLevelObjectRepository;
    private final AssetApproversRepository assetApproversRepository;
    private final UserRepository userRepository;
    private final NotificationTaskRepository notificationTaskRepository;
    private final DatabaseAccessService databaseAccessService;
    private final UserService userService;
    private final AssetCredentialsRepository assetCredentialsRepository;
    private final AssetRepository assetRepository;
    private final KeycloakService keycloakService;

    public AccessRequestService(
            AccessRequestRepository accessRequestRepository,
            AssetService assetService,
            AccessLevelObjectRepository accessLevelObjectRepository,
            AssetApproversRepository assetApproversRepository,
            UserRepository userRepository,
            NotificationTaskRepository notificationTaskRepository,
            DatabaseAccessService databaseAccessService,
            UserService userService, AssetCredentialsRepository assetCredentialsRepository, AssetRepository assetRepository, KeycloakService keycloakService) {
        this.accessRequestRepository = accessRequestRepository;
        this.assetService = assetService;
        this.accessLevelObjectRepository = accessLevelObjectRepository;
        this.assetApproversRepository = assetApproversRepository;
        this.userRepository = userRepository;
        this.notificationTaskRepository = notificationTaskRepository;
        this.databaseAccessService = databaseAccessService;
        this.userService = userService;
        this.assetCredentialsRepository = assetCredentialsRepository;
        this.assetRepository = assetRepository;
        this.keycloakService = keycloakService;
    }

    private String generateSql(List<AccessLevelObject> accessLevelObjects) {
        if (accessLevelObjects == null || accessLevelObjects.isEmpty()) {
            throw new ResourceNotFoundException(Constants.getMessage("error.no.access.level.objects"));
        }

        StringBuilder sqlBuilder = new StringBuilder();
        
        // Get database type from the first access level object
        DatabaseType databaseType = accessLevelObjects.get(0).getAccessLevel().getDatabaseType();

        accessLevelObjects.forEach(obj -> {
            AccessLevel level = obj.getAccessLevel();
            String template = level.getAccessTemplate();
            String tableName = "";
            
            if (!obj.getAccessLevel().getObject().equals(Constants.ASSET_ACCESS_OBJECT_DATABASE)) {
                // For table objects like "schema.table" or "database.table"
                String[] parts = obj.getObjectName().split(Constants.DB_SPLIT_PATTERN);
                if (parts.length >= 2) {
                    tableName = parts[1];
            } else {
                    tableName = obj.getObjectName();
                }
            }
            
            // Apply database-specific placeholder fixes
            String sql = applyDatabaseSpecificFixes(template, databaseType);
            
            // Replace placeholders - convert old $DB to new $DATABASE for consistency
            sql = sql.replace("$DB", "$DATABASE")  // Convert to standardized placeholder
                    .replace("$TABLE", tableName)
                    .replace("$OBJECT", obj.getObjectName());

            sqlBuilder.append(sql).append(sql.endsWith(Constants.SQL_STATEMENT_SEPARATOR) ? Constants.SQL_NEWLINE_SEPARATOR : Constants.SQL_SEMICOLON_NEWLINE);
        });

        return sqlBuilder.toString();
    }

    private String applyDatabaseSpecificFixes(String template, DatabaseType databaseType) {
        switch (databaseType) {
            case POSTGRESQL:
                return template
                    // Fix PostgreSQL-specific hardcoded values
                    .replace(Constants.SQL_PLACEHOLDER_DATABASE_PUBLIC, "DATABASE $DATABASE")
                    .replace(Constants.SQL_PLACEHOLDER_SCHEMA_PUBLIC, "SCHEMA $SCHEMA")
                    .replace("ON public.", Constants.SQL_TEMPLATE_ON_SCHEMA + ".")
                    .replace(Constants.SQL_PLACEHOLDER_IN_SCHEMA_PUBLIC, "IN SCHEMA $SCHEMA");
                    
            case SQLSERVER:
                return template
                    // Fix SQL Server-specific hardcoded values
                    .replace(Constants.SQL_PLACEHOLDER_ON_DBO_BRACKET, "ON [$SCHEMA].")
                    .replace("ON dbo.", Constants.SQL_TEMPLATE_ON_SCHEMA + ".")
                    .replace(Constants.SQL_PLACEHOLDER_DBO_BRACKET, Constants.SQL_PLACEHOLDER_SCHEMA_BRACKET);
                    
            case MYSQL:
                return template
                    // MySQL typically uses database.table format
                    // Most MySQL templates should already be correct, but handle common cases
                    .replace("@'%'", "@'%'"); // Keep MySQL user@host format as-is
                    
            case ORACLE:
                return template
                    // Oracle typically uses schema.object format
                    // Most Oracle templates should already be correct
                    .replace(Constants.SQL_PLACEHOLDER_ON_USERS, Constants.SQL_TEMPLATE_ON_SCHEMA + "."); // Fix common Oracle default tablespace/schema
                    
            default:
                // No database-specific fixes for unknown types
                return template;
        }
    }

    public AccessRequest saveAccessRequest(AccessRequestDTO requestDTO, User requestor)
            throws ResourceNotFoundException, JsonParseException, IllegalArgumentException {
        List<AccessLevelObject> accessLevelObjects = requestDTO.getAccessLevelObjects();
        if (accessLevelObjects.isEmpty()) {
            throw new ResourceNotFoundException(Constants.getMessage("error.no.access.level.objects"));
        }

        // Assuming all objects are for the same asset
        Asset asset = assetService.findById(requestDTO.getAssetId());

        AccessRequest request;

        // Check if this is an update to an existing request
        if (requestDTO.getRequestId() != null) {
            List<AccessRequest> requests = accessRequestRepository.findNotExpiredByAssetAndRequestor(asset, requestor);
            request = requests.isEmpty() ? null : requests.get(0);
        } else {
            request = new AccessRequest();
        }
        request.setAsset(asset);
        request.setRequestor(requestor);
        request.setRequestTime(LocalDateTime.now());
        request.setRequestReason(requestDTO.getRequestReason());
        request.setAccessSql(generateSql(accessLevelObjects));
        request.setDeveloperApproverStatus(ApprovalStatus.REQUESTED);
        request.setAssetApproverStatus(ApprovalStatus.REQUESTED);
        request.setIsTempPassword(true);

        // Set expiry hours (default to 3 months = 2160 hours if not provided)
        request.setExpiryHours(
                requestDTO != null && requestDTO.getExpirationHours() != null && requestDTO.getExpirationHours() != 0
                        ? requestDTO.getExpirationHours()
                        : Constants.getTechnicalPropertyAsInt("access.request.default.expiry.hours"));
        // Calculate expiry date
        request.setExpiryDate(request.getRequestTime().plusHours(request.getExpiryHours()));

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
        sendNotifications(savedRequest, requestor, asset, true);

        return savedRequest;
    }

    private void sendNotifications(AccessRequest request,
            User requestor,
            Asset asset,
            boolean isAccessRequest) // true: Access Request, false: Relinquish Request
            throws ResourceNotFoundException, JsonParseException, IllegalArgumentException {
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
                .toList();

        // Prepare notification data
        Map<String, String> notificationData = new HashMap<>();
        notificationData.put(Constants.EMAIL_VAR_REQUEST_ID, request.getId().toString());
        notificationData.put(Constants.EMAIL_VAR_ASSET_ID, asset.getId().toString());
        notificationData.put(Constants.EMAIL_VAR_ASSET_NAME, asset.getName());
        notificationData.put(Constants.EMAIL_VAR_ASSET_DESCRIPTION, asset.getDescription());
        notificationData.put(Constants.EMAIL_VAR_REQUESTOR_NAME,
                requestor.getFirstName() + " " + requestor.getLastName());
        notificationData.put(Constants.EMAIL_VAR_MESSAGE_TYPE, "1"); // 1 : success, 0: fail
        notificationData.put(Constants.EMAIL_VAR_IS_ACCESS_REQUEST, isAccessRequest ? "1" : "0");

        if (developerApprover != null) {
            sendNotificationAndEmail(developerApprover, requestor, asset, notificationData);
        }

        sendNotificationsToUsers(assetOwners, requestor, asset, notificationData);
        sendNotificationsToUsers(assetApprovers, requestor, asset, notificationData);

    }

    private void sendNotificationsToUsers(List<User> users, User requestor, Asset asset,
            Map<String, String> notificationData) throws JsonParseException {
        for (User user : users) {
            sendNotificationAndEmail(user, requestor, asset, notificationData);
        }
    }

    private void sendNotificationAndEmail(User receiver,
            User requestor,
            Asset asset,
            Map<String, String> notificationData) throws JsonParseException {
        NotificationMessage notificationMessage = new NotificationMessage();
        if (notificationData.get(Constants.EMAIL_VAR_IS_ACCESS_REQUEST).equals("1")) {
            notificationMessage.setTitle(Constants.getMessage(Constants.NOTIFICATION_TITLE_NEW_ACCESS_REQUEST));
            notificationMessage.setBody(String.format("%s %s has requested access to %s",
                    requestor.getFirstName(), requestor.getLastName(), asset.getName()));
        } else {
            notificationMessage.setTitle(Constants.getMessage(Constants.NOTIFICATION_TITLE_RELINQUISHED_ACCESS_REQUEST));
            notificationMessage.setBody(String.format("%s %s has relinquished access to %s",
                    requestor.getFirstName(), requestor.getLastName(), asset.getName()));
        }

        notificationData.put(Constants.NOTIFY_DATA_ATTR_RECEIVER_ID, receiver.getId().toString());
        notificationMessage.setData(notificationData);
        notificationMessage.setTopic(Constants.DAM_NOTIFICATION_TOPIC);

        // Create and save notification task
        NotificationTask task = new NotificationTask();
        task.setReceiver(receiver);
        task.setSender(requestor);
        task.setAsset(asset);
        task.setNotificationMessage(notificationMessage.toJson());
        if (notificationData.get(Constants.EMAIL_VAR_IS_ACCESS_REQUEST).equals("1")) {
            task.setEmailType(EmailType.DEVELOPER_ASSET_REQUEST_NOTIFY);
        } else {
            task.setEmailType(EmailType.DEVELOPER_RELINQUISH_ASSET_NOTIFY);
        }
        notificationTaskRepository.save(task);
    }

    public AccessRequest createRequest(AccessRequest request) {
        request.setRequestTime(LocalDateTime.now());
        request.setDeveloperApproverStatus(ApprovalStatus.REQUESTED);
        request.setAssetApproverStatus(ApprovalStatus.REQUESTED);
        return accessRequestRepository.save(request);
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

    public List<AccessRequest> getTemporaryCredentialRequests(User user) {
        return accessRequestRepository
                .findByRequestorAndIsTempPasswordAndAssetApproverStatusAndIsDeletedFalseAndExpiryDateAfter(
                        user,
                        true,
                        ApprovalStatus.APPROVED,
                        LocalDateTime.now());
    }

    public List<AccessRequest> getAssetRequests(Asset asset) {
        return accessRequestRepository.findByAsset(asset);
    }

    public AccessRequest findById(Long id) {
        return accessRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(Constants.getMessage("error.access.request.not.found") + id));
    }

    public AccessRequest setCredentialPassword(Long accessRequestId, AssetCredentialDTO credentialInfo)
            throws CryptoException {
        AccessRequest accessRequest = findById(accessRequestId);
        if (accessRequest == null) {
            throw new ResourceNotFoundException(Constants.getMessage("error.no.access.request"));
        }

        // Get the developer (requestor) to find their credential
        User developer = accessRequest.getRequestor();
        if (developer == null) {
            throw new ResourceNotFoundException("Developer not found for access request");
        }

        // Find and validate developer credential
        AssetCredential devCredential = findDeveloperCredential(accessRequest, developer);

        // Get developer's encryption key
        String developerKey = getDeveloperEncryptionKey();

        // Encrypt credential if it's temporary
        if (Boolean.TRUE.equals(devCredential.getIsTemporaryPassword())) {
            encryptCredential(devCredential, credentialInfo, accessRequest.getAsset(), developerKey);
        }

        // Save updated credential and access request
        saveCredentialAndAccessRequest(devCredential, accessRequest);
        return accessRequest;
    }

    /**
     * Find and validate developer credential for the access request
     */
    private AssetCredential findDeveloperCredential(AccessRequest accessRequest, User developer) {
        AssetCredential devCredential = assetCredentialsRepository
                .findByAssetIdAndUserId(accessRequest.getAsset().getId(), developer.getId())
                .stream()
                .filter(cred -> Roles.DEVELOPER.getOriginalName().equals(cred.getUserAccessType()))
                .findFirst()
                .orElse(null);

        if (devCredential == null) {
            throw new ResourceNotFoundException("Developer credential not found for this access request");
        }
        return devCredential;
    }

    /**
     * Get developer's encryption key
     */
    private String getDeveloperEncryptionKey() throws CryptoException {
        String developerKey = keycloakService.getUserKey();
        if (developerKey == null || developerKey.isEmpty()) {
            throw new CryptoException("Developer encryption key not available");
        }
        return developerKey;
    }

    /**
     * Encrypt credential based on asset type
     */
    private void encryptCredential(AssetCredential devCredential, AssetCredentialDTO credentialInfo, 
                                   Asset asset, String developerKey) throws CryptoException {
        if (asset.getType() == AssetType.UNIX_SERVER) {
            encryptUnixCredential(devCredential, credentialInfo, developerKey);
        } else {
            encryptDatabaseCredential(devCredential, credentialInfo, developerKey);
        }
    }

    /**
     * Encrypt Unix SSH key file
     */
    private void encryptUnixCredential(AssetCredential devCredential, AssetCredentialDTO credentialInfo, 
                                      String developerKey) throws CryptoException {
        if (credentialInfo.getSshKeyFile() == null || credentialInfo.getSshKeyFile().isEmpty()) {
            throw new IllegalArgumentException("SSH key file is required for Unix assets");
        }
        String encryptedSshKey = CommonUtils.encrypt(developerKey, credentialInfo.getSshKeyFile());
        devCredential.setSshKeyFile(encryptedSshKey);
    }

    /**
     * Encrypt database password
     */
    private void encryptDatabaseCredential(AssetCredential devCredential, AssetCredentialDTO credentialInfo, 
                                          String developerKey) throws CryptoException {
        if (credentialInfo.getPassword() == null || credentialInfo.getPassword().isEmpty()) {
            throw new IllegalArgumentException("Password is required for database assets");
        }
        String encryptedPassword = CommonUtils.encrypt(developerKey, credentialInfo.getPassword());
        devCredential.setPassword(encryptedPassword);
    }

    /**
     * Save credential and update access request flag
     */
    private void saveCredentialAndAccessRequest(AssetCredential devCredential, AccessRequest accessRequest) {
        assetCredentialsRepository.save(devCredential);
        accessRequest.setIsTempPassword(false);
        accessRequestRepository.save(accessRequest);
    }

    public void relinquishAccess(Long accessRequestId)
            throws JsonParseException, ResourceNotFoundException, IllegalArgumentException {
        AccessRequest accessRequest = findById(accessRequestId);
        if (accessRequest == null) {
            throw new ResourceNotFoundException(Constants.getMessage("error.no.access.request"));
        }

        // Determine if relinquished after approval or before approval
        boolean wasApproved = accessRequest.getAssetApproverStatus() == ApprovalStatus.APPROVED;
        
        if (wasApproved) {
            accessRequest.setAssetApproverStatus(ApprovalStatus.RELINQUISHED_AFTER_APPROVED);
        } else {
            accessRequest.setAssetApproverStatus(ApprovalStatus.RELINQUISHED_BEFORE_APPROVAL);
        }

        accessRequest.setExpiryDate(LocalDateTime.now());
        accessRequest.setExpiryHours(0);
        accessRequestRepository.save(accessRequest);

        sendNotifications(accessRequest, accessRequest.getRequestor(), accessRequest.getAsset(), false);
    }



    @Transactional(readOnly = true)
    public List<AccessRequest> getAssetRequestApprovals() {
        User currentUser = userService.getCurrentUser();
        List<AssetCredential> assetCredentials = assetCredentialsRepository.findByUserAndUserAccessType(currentUser, Roles.ASSET_OWNER.getOriginalName());
        
        if (assetCredentials.isEmpty()) {
            return List.of();
        }
        
        // Collect all asset IDs and separate by type
        List<Long> unixAssetIds = assetCredentials.stream()
                .filter(cred -> cred.getAsset().getType() == AssetType.UNIX_SERVER)
                .map(cred -> cred.getAsset().getId())
                .toList();
        
        List<Asset> databaseAssets = assetCredentials.stream()
                .filter(cred -> cred.getAsset().getType() != AssetType.UNIX_SERVER)
                .map(AssetCredential::getAsset)
                .toList();
        
        // Fetch all access requests upfront
        List<AccessRequest> allAccessRequests = new java.util.ArrayList<>();
        
        if (!unixAssetIds.isEmpty()) {
            List<AccessRequest> unixRequests = accessRequestRepository.findPendingUnixRequestsForAssets(
                    unixAssetIds, 
                    ApprovalStatus.REQUESTED);
            allAccessRequests.addAll(unixRequests);
        }
        
        // Fetch database asset requests (batch query)
        if (!databaseAssets.isEmpty()) {
            List<Long> databaseAssetIds = databaseAssets.stream()
                    .map(Asset::getId)
                    .toList();
            List<AccessRequest> dbRequests = accessRequestRepository.findPendingRequestsByAssetIds(databaseAssetIds);
            allAccessRequests.addAll(dbRequests);
        }
        
        // Fetch all assets upfront to avoid N+1 queries
        List<Long> allAssetIds = assetCredentials.stream()
                .map(cred -> cred.getAsset().getId())
                .toList();
        Map<Long, Asset> assetMap = assetRepository.findAllById(allAssetIds).stream()
                .collect(java.util.stream.Collectors.toMap(Asset::getId, asset -> asset));
        
        // Process all access requests
        for (AccessRequest request : allAccessRequests) {
            Asset fullAsset = assetMap.get(request.getAsset().getId());
            if (fullAsset == null) {
                log.warn("Asset not found for access request ID: {}, asset ID: {}", request.getId(), request.getAsset().getId());
                continue;
            }
            
            AssetDTO assetDTO = assetService.convertToDTO(fullAsset);
            request.setAssetDTO(assetDTO);
            request.setAssetApprovalsDTO(assetService.convertToApprovalsDTO(fullAsset));
            
            // Mask sensitive Unix data if this is a Unix access request
            if (request.getRequestedUsername() != null) {
                if (request.getPublicKey() != null) {
                    request.setPublicKey(null);
                }
                if (request.getEncryptedPrivateKey() != null) {
                    request.setEncryptedPrivateKey(null);
                }
            }
        }
        
        // Sort by request time (descending)
        return allAccessRequests.stream()
                .sorted((a1, a2) -> a2.getRequestTime().compareTo(a1.getRequestTime()))
                .toList();
    }

    public AccessRequest setApprovalStatusOfAccessRequest(Long accessRequestId, AccessRequestDTO accessRequestDTO, ApprovalStatus approvalStatus)
            throws JsonParseException {
        AccessRequest accessRequest = accessRequestRepository.findById(accessRequestId)
                .orElseThrow(() -> new ResourceNotFoundException(Constants.getMessage("error.access.request.not.found.msg")));

        Map<String, String> newCredMapper = new HashMap<>();
        if (approvalStatus == ApprovalStatus.APPROVED) {
            // Find existing credential or create new one
            List<AccessRequest> lstAccessRequests = accessRequestRepository.findByUserAndAssetAndUserAccessTypeAndNotExpired(
                    accessRequest.getRequestor(),
                    accessRequest.getAsset(),
                    Roles.DEVELOPER.getOriginalName());

            String existUsername = "";

            if (!lstAccessRequests.isEmpty()) {
                // Generate new username and password
                existUsername = lstAccessRequests.get(0).getAssetCredential().getUsername();
            } else {
                // Set AccessRequest's temporary password flag to true if the username is not exist
                accessRequest.setIsTempPassword(true);
            }
            checkUserAndSetCredentials(accessRequest.getAsset().getId(), accessRequest.getRequestor(), accessRequest,
                    existUsername, newCredMapper);
            if (newCredMapper.containsKey(Constants.CREDENTIAL_ID_KEY)) {
                AssetCredential credential = assetCredentialsRepository.findById(Long.parseLong(newCredMapper.get(Constants.CREDENTIAL_ID_KEY)))
                        .orElseThrow(() -> new ResourceNotFoundException(Constants.getMessage("error.new.credential.not.found")));
                accessRequest.setAssetCredential(credential);
            }
        } else if (approvalStatus == ApprovalStatus.REJECTED) {
            accessRequest.setRejectReason(accessRequestDTO.getRejectReason());
        }
        
        // Set asset approver status
        // If approving, check if both approvers need to approve
        if (approvalStatus == ApprovalStatus.APPROVED) {
            accessRequest.setAssetApproverStatus(ApprovalStatus.APPROVED);
        } else {
            accessRequest.setAssetApproverStatus(approvalStatus);
        }

        // Set expiry hours (default to 3 months = 2160 hours if not provided)
        accessRequest.setExpiryHours(accessRequestDTO != null && accessRequestDTO.getExpirationHours() != null && accessRequestDTO.getExpirationHours() != 0 ? accessRequestDTO.getExpirationHours() : Constants.getTechnicalPropertyAsInt("access.request.default.expiry.hours"));
        // Calculate expiry date
        accessRequest.setExpiryDate(LocalDateTime.now().plusHours(accessRequest.getExpiryHours()));

        accessRequestRepository.save(accessRequest);
        User currentUser = userService.getCurrentUser();
        Map<String, String> notificationData = new HashMap<>();
        notificationData.put(Constants.NOTIFICATION_KEY_REQUEST_ID, accessRequest.getId().toString());
        notificationData.put(Constants.NOTIFICATION_KEY_ASSET_ID, accessRequest.getAsset().getId().toString());
        notificationData.put(Constants.NOTIFICATION_KEY_ASSET_NAME, accessRequest.getAsset().getName());
        notificationData.put(Constants.NOTIFICATION_KEY_ASSET_DESCRIPTION, accessRequest.getAsset().getDescription());
        notificationData.put(Constants.NOTIFICATION_KEY_DEVELOPER_NAME,
                accessRequest.getRequestor().getFirstName() + " " + accessRequest.getRequestor().getLastName());
        notificationData.put(Constants.NOTIFICATION_KEY_APPROVER_NAME,
                currentUser.getFirstName() + " " + currentUser.getLastName());
        notificationData.put(Constants.NOTIFICATION_KEY_APPROVAL_STATUS, approvalStatus.name());
        notificationData.put(Constants.EMAIL_VAR_MESSAGE_TYPE, "1"); //1 : success, 0: fail
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
        notificationMessage.setTitle(Constants.getMessage("notification.title.approval.result"));
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
        notificationMessage.setTopic(Constants.DAM_NOTIFICATION_TOPIC);

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
        final String userKey = keycloakService.getUserKey();

        //Asset Credential has user_access_type, get credentials which are only asset owner's
        final List<AssetCredential> credentials = assetCredentialsRepository.findByAssetIdAndUserAccessType(assetId, Roles.ASSET_OWNER.getOriginalName());

        List<AssetCredential> validCredentials = credentials.stream()
                .filter(cred -> {
                    // Check if credential has required fields
                    boolean hasValidCredentials = cred.getUsername() != null && !cred.getUsername().isEmpty()
                            && cred.getPassword() != null && !cred.getPassword().isEmpty();

                    // Check if user is the owner of the asset associated with this credential
                    boolean isAssetOwner = cred.getUser() != null && userService.isAssetOwner(cred.getUser());

                    return hasValidCredentials && isAssetOwner;
                })
                .toList();

        if (validCredentials.isEmpty()) {
            throw new ResourceNotFoundException(Constants.getMessage("error.no.valid.credentials"));
        }

        AssetCredential cred = validCredentials.get(0);
        try {
            AssetCredential assetOwnerCred = new AssetCredential();
            assetOwnerCred.setUsername(cred.getUsername());
            if (!cred.getIsTemporaryPassword()) {
                String decryptedPassword = CommonUtils.decrypt(userKey, cred.getPassword());
                assetOwnerCred.setPassword(decryptedPassword);
            } else {
                assetOwnerCred.setPassword(cred.getPassword());
            }
            assetOwnerCred.setAsset(cred.getAsset());
            assetOwnerCred.setUser(cred.getUser());

            databaseAccessService.checkAccessRequestorInAsset(assetOwnerCred, requestor, accessRequest, existUsername,
                    newCredMapper);
        } catch (Exception e) {
            throw new DatabaseAccessException(
                    Constants.getMessage("error.credential.errors")
                            + cred.getId(),
                    e);
        }
    }

    /**
     * Encrypt temporary password or SSH key file and update both credential and access request flags
     * This method handles the common logic for encrypting temporary credentials when first accessed
     * 
     * Uses REQUIRES_NEW propagation to ensure writes are persisted even when called from read-only transactions
     * 
     * @param credential The asset credential with temporary password/SSH key
     * @param accessRequest The access request associated with this credential
     * @throws CryptoException if encryption fails
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void encryptTemporaryCredential(AssetCredential credential, AccessRequest accessRequest) throws CommonUtils.CryptoException {
        if (!Boolean.TRUE.equals(credential.getIsTemporaryPassword())) {
            return; // Not a temporary password, nothing to do
        }
        
        String userKey = keycloakService.getUserKey();
        if (userKey == null || userKey.isEmpty()) {
            throw new CryptoException("User encryption key not available");
        }
        
        // Encrypt password or SSH key file based on what's available
        if (credential.getPassword() != null && !credential.getPassword().isEmpty()) {
            // Database credential - encrypt password
            String encryptedPassword = CommonUtils.encrypt(userKey, credential.getPassword());
            credential.setPassword(encryptedPassword);
        } else if (credential.getSshKeyFile() != null && !credential.getSshKeyFile().isEmpty()) {
            // Unix SSH credential - encrypt SSH key file
            String encryptedSshKey = CommonUtils.encrypt(userKey, credential.getSshKeyFile());
            credential.setSshKeyFile(encryptedSshKey);
        } else {
            log.warn("Temporary credential found but neither password nor sshKeyFile is set for credential ID: {}", credential.getId());
            return;
        }
        
        // Update credential flags
        credential.setIsTemporaryPassword(false);
        assetCredentialsRepository.save(credential);
        
        // Update access request flag
        if (accessRequest != null) {
            accessRequest.setIsTempPassword(false);
            accessRequestRepository.save(accessRequest);
        }
        
        log.info("Encrypted temporary credential for credential ID: {}, accessRequest ID: {}", 
                credential.getId(), accessRequest != null ? accessRequest.getId() : "N/A");
    }

    /**
     * Update expired access requests to EXPIRED status for developers
     * This is called when a developer logs in to ensure their expired requests are marked
     * 
     * @param user The developer user whose expired access requests should be updated
     */
    @Transactional
    public void updateExpiredAccessRequests(User user) {
        try {
            LocalDateTime now = LocalDateTime.now();
            String developerAccessType = Roles.DEVELOPER.getOriginalName();
            
            List<AccessRequest> expiredRequests = accessRequestRepository
                    .findExpiredByRequestorAndUserAccessType(user, now, developerAccessType);
            
            expiredRequests.forEach(ar -> {
                ar.setDeveloperApproverStatus(ApprovalStatus.EXPIRED);
                ar.setAssetApproverStatus(ApprovalStatus.EXPIRED);
                accessRequestRepository.save(ar);
                log.info("Updated access request ID: {} to EXPIRED status for developer: {}", 
                        ar.getId(), user.getEmail());
            });
            
            if (!expiredRequests.isEmpty()) {
                log.info("Updated {} expired access requests to EXPIRED status for developer: {}", 
                        expiredRequests.size(), user.getEmail());
            }
        } catch (Exception e) {
            log.error("Failed to update expired access requests for developer {}: {}", 
                    user != null ? user.getEmail() : "unknown", e.getMessage(), e);
        }
    }

}