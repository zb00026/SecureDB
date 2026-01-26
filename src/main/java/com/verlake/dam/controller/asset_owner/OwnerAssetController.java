package com.verlake.dam.controller.asset_owner;

import com.fasterxml.jackson.core.JsonParseException;
import com.verlake.dam.controller.common.BaseAssetAccessController;
import com.verlake.dam.entity.Role;
import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.assets.dto.AccessRequestDTO;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.assets.dto.AssetCredentialDTO;
import com.verlake.dam.entity.assets.dto.AssetDTO;
import com.verlake.dam.entity.assets.dto.PingResult;
import com.verlake.dam.entity.assets.AssetQueryChangeRequest;
import com.verlake.dam.entity.assets.dto.AccessQueryDTO;
import com.verlake.dam.entity.assets.dto.NaturalLanguageQueryDTO;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.repository.assets.AccessRequestRepository;
import com.verlake.dam.repository.assets.AssetObjectRepository;
import com.verlake.dam.repository.RoleRepository;
import com.verlake.dam.repository.assets.AccessLevelObjectRepository;
import com.verlake.dam.service.assets.AccessLevelService;
import com.verlake.dam.service.assets.AccessRequestService;
import com.verlake.dam.service.assets.AssetQueryChangeRequestService;
import com.verlake.dam.service.assets.AssetService;
import com.verlake.dam.service.assets.DatabaseAccessService;
import com.verlake.dam.service.assets.QueryExecutionService;
import com.verlake.dam.service.assets.NaturalLanguageToSqlService;
import com.verlake.dam.enums.AssetType;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.aws.AWSSecretsManagerService;
import com.verlake.dam.service.email.EmailService;
import com.verlake.dam.service.settings.SystemSettingsService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.entity.firebase.NotificationTask;
import com.verlake.dam.enums.EmailType;
import com.verlake.dam.repository.NotificationTaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verlake.dam.service.unix.UnixAccessApprovalService;
import com.verlake.dam.service.unix.UnixAccessRequestService;
import com.verlake.dam.entity.dto.unix.UnixAccessApprovalDTO;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import com.verlake.dam.service.assets.mongodb.MongoDBConnectionUtils;
import com.verlake.dam.enums.DatabaseType;

import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@RestController
@RequestMapping("/api/asset_owner/assets")
public class OwnerAssetController extends BaseAssetAccessController {

    private final AccessRequestService accessRequestService;
    private final UserService userService;
    private final AssetQueryChangeRequestService assetQueryChangeRequestService;
    private static final Logger logger = LoggerFactory.getLogger(OwnerAssetController.class);

    @Autowired
    private EmailService emailService;

    @Autowired
    private KeycloakService keycloakService;

    @Autowired
    private AccessLevelService accessLevelService;

    @Value("${auth.provider}")
    private String authProvider;

    private AssetObjectRepository assetObjectRepository;
    @Autowired
    private AccessRequestRepository accessRequestRepository;

    @Autowired
    private AccessLevelObjectRepository accessLevelObjectRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DatabaseAccessService databaseAccessService;

    @Autowired
    private AWSSecretsManagerService awsSecretsManagerService;
    
    @Autowired
    private SystemSettingsService systemSettingsService;

    @Autowired
    private QueryExecutionService queryExecutionService;

    @Autowired
    private NaturalLanguageToSqlService naturalLanguageToSqlService;

    @Autowired
    private UnixAccessRequestService unixAccessRequestService;

    @Autowired
    private UnixAccessApprovalService unixAccessApprovalService;

    @Autowired
    private NotificationTaskRepository notificationTaskRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Constructor for OwnerAssetController.
     * 
     * This constructor is used by Spring's dependency injection framework to create
     * an instance of OwnerAssetController with all required dependencies.
     * 
     * @param assetService the service for managing assets
     * @param accessRequestService the service for managing access requests
     * @param userService the service for managing users
     * @param assetObjectRepository the repository for asset objects
     * @param databaseAccessService the service for database access operations
     * @param assetQueryChangeRequestService the service for asset query change requests
     * 
     * @implNote This constructor initializes all final fields and should only be called
     *           by Spring's dependency injection framework. The @RequiredArgsConstructor
     *           annotation from Lombok generates this constructor automatically for all
     *           final fields, ensuring that all dependencies are properly injected.
     * 
     * @see RequiredArgsConstructor
     * @since 1.0
     * 
     * @throws IllegalArgumentException if any of the required parameters are null
     * @throws UnsupportedOperationException if this constructor is called directly
     *         instead of being used by Spring's dependency injection
     */
    public OwnerAssetController(
            AssetService assetService,
            AccessRequestService accessRequestService,
            UserService userService,
            AssetObjectRepository assetObjectRepository,
            DatabaseAccessService databaseAccessService,
            AssetQueryChangeRequestService assetQueryChangeRequestService) {
        super(assetService);
        this.accessRequestService = accessRequestService;
        this.userService = userService;
        this.assetObjectRepository = assetObjectRepository;
        this.databaseAccessService = databaseAccessService;
        this.assetQueryChangeRequestService = assetQueryChangeRequestService;
    }

    @PostMapping("/{assetId}")
    public ResponseEntity<Map<String, Object>> updateAsset(@PathVariable long assetId, @RequestBody AssetDTO assetDTO) {
        assetService.updateAsset(assetId, assetDTO, false);
        return CommonUtils.getSuccessResponse();
    }

    @GetMapping("/new-credentials")
    public ResponseEntity<List<AssetCredential>> getNewAssignedCredentials() {
        return ResponseEntity.ok(assetService.getNewAssignedCredentials());
    }

    @GetMapping("/credentials")
    public ResponseEntity<List<AssetCredential>> getAllAssetCredentials() {
        return ResponseEntity.ok(assetService.getAssignedCredentials());
    }

    @GetMapping("/{assetId}")
    public ResponseEntity<AssetDTO> getAsset(@PathVariable long assetId) {
        return ResponseEntity.ok(assetService.findDTOById(assetId));
    }

    /**
     * Get real-time user access information for an asset by querying the target database directly
     */
    @GetMapping("/{id}/access")
    public ResponseEntity<?> getAssetAccess(@PathVariable Long id) {
        return super.getAssetAccess(id, Roles.ASSET_OWNER.getOriginalName());
    }

    /**
     * Ping an asset to test connectivity without credentials
     * This endpoint allows asset owners to verify that the asset's connection details are correct
     * 
     * @param id Asset ID to ping
     * @return PingResult containing success status and response time
     */
    @PostMapping("/{id}/ping")
    public ResponseEntity<PingResult> pingAsset(
            @PathVariable Long id) {
        String currentUserEmail = CommonUtils.getEmailFromSession();
        logger.info("Asset owner {} pinging asset ID: {}", currentUserEmail, id);
        
        // Check if asset is locked
        Asset asset = assetService.findById(id);
        assetService.validateAssetNotLocked(asset, false);
        
        PingResult result = assetService.pingAsset(id, true);
        
        logger.info("Asset ping completed for asset ID: {} by owner: {}. Success: {}", 
                   id, currentUserEmail, result.isSuccess());
        
        return ResponseEntity.ok(result);
    }

    /**
     * Lock out users in the asset database (Asset Owner only)
     * 
     * @param id           Asset ID
     */
    @PostMapping("/{id}/lockout")
    public ResponseEntity<Map<String, Object>> lockoutAssetUsers(
            @PathVariable Long id) {

        String currentUserEmail = CommonUtils.getEmailFromSession();
        logger.info("Asset owner {} initiating lockout for asset ID: {}, lockAllUsers: {}",
                currentUserEmail, id, true);

        Map<String, Object> result = assetService.lockoutAssetUsers(id, true);

        logger.info("Lockout completed successfully for asset ID: {} by owner: {}", id, currentUserEmail);
        return ResponseEntity.ok(result);
    }

    /**
     * Unlock users in the asset database (Asset Owner only)
     * 
     * @param id             Asset ID
     */
    @PostMapping("/{id}/unlock")
    public ResponseEntity<Map<String, Object>> unlockAssetUsers(
            @PathVariable Long id) {

        String currentUserEmail = CommonUtils.getEmailFromSession();
        logger.info("Asset owner {} initiating unlock for asset ID: {}, unlockAllUsers: {}",
                currentUserEmail, id, true);

        Map<String, Object> result = assetService.unlockAssetUsers(id, true);

        logger.info("Unlock completed successfully for asset ID: {} by owner: {}", id, currentUserEmail);
        return ResponseEntity.ok(result);
    }

    @Override
    protected ResponseEntity<?> handleGenericError(RuntimeException e) {
        // Asset owner controller returns internal server error for generic errors
        return ResponseEntity.internalServerError().build();
    }

    @PostMapping("/request/{accessRequestId}/approve")
    public ResponseEntity<AccessRequest> approveAccessRequest(@PathVariable long accessRequestId, @RequestBody AccessRequestDTO accessRequest) {
        try {
            return ResponseEntity.ok(accessRequestService.setApprovalStatusOfAccessRequest(accessRequestId, accessRequest, ApprovalStatus.APPROVED));
        } catch (JsonParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can not approve access request");
        }
    }

    @PostMapping("/request/{accessRequestId}/reject")
    public ResponseEntity<AccessRequest> rejectAccessRequest(@PathVariable long accessRequestId, @RequestBody AccessRequestDTO accessRequest) {
        try {
            return ResponseEntity.ok(accessRequestService.setApprovalStatusOfAccessRequest(accessRequestId, accessRequest, ApprovalStatus.REJECTED));
        } catch (JsonParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can not reject access request");
        }
    }

    @GetMapping("/approvals")
    public ResponseEntity<List<AccessRequest>> getMyAssetApprovals() {
        return ResponseEntity.ok(accessRequestService.getAssetRequestApprovals());
    }

    // Unix Access Request Management

    /**
     * Get pending Unix access requests for my assets
     */
    @GetMapping("/unix-requests/pending")
    public ResponseEntity<List<AccessRequest>> getPendingUnixRequests() {
        logger.info("Getting pending Unix access requests for my assets");
        List<AccessRequest> requests = unixAccessRequestService.getPendingRequestsForMyAssets();
        return ResponseEntity.ok(requests);
    }

    /**
     * Get all Unix access requests for a specific asset
     */
    @GetMapping("/{assetId}/unix-requests")
    public ResponseEntity<Page<AccessRequest>> getUnixRequestsForAsset(
            @PathVariable Long assetId,
            Pageable pageable) {
        logger.info("Getting Unix access requests for asset: {}", assetId);
        Page<AccessRequest> requests = unixAccessRequestService.getAccessRequestsForAsset(assetId, pageable);
        return ResponseEntity.ok(requests);
    }

    /**
     * Get a specific Unix access request by ID
     */
    @GetMapping("/unix-requests/{requestId}")
    public ResponseEntity<AccessRequest> getUnixAccessRequest(@PathVariable Long requestId) {
        logger.info("Getting Unix access request: {}", requestId);
        AccessRequest request = unixAccessRequestService.getAccessRequestById(requestId);
        return ResponseEntity.ok(request);
    }

    /**
     * Approve a Unix access request
     */
    @PostMapping("/unix-requests/{requestId}/approve")
    public ResponseEntity<Map<String, Object>> approveUnixAccessRequest(
            @PathVariable Long requestId,
            @RequestBody UnixAccessApprovalDTO approvalDTO) {
        logger.info("Approving Unix access request: {}", requestId);
        
        if (approvalDTO.getApprovedGroupIds() == null || approvalDTO.getApprovedGroupIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one group must be approved");
        }
        
        unixAccessApprovalService.approveAccessRequest(requestId, approvalDTO.getApprovedGroupIds());
        return CommonUtils.getSuccessResponse();
    }

    /**
     * Reject a Unix access request
     */
    @PostMapping("/unix-requests/{requestId}/reject")
    public ResponseEntity<Map<String, Object>> rejectUnixAccessRequest(
            @PathVariable Long requestId,
            @RequestBody UnixAccessApprovalDTO rejectionDTO) {
        logger.info("Rejecting Unix access request: {}", requestId);
        
        unixAccessApprovalService.rejectAccessRequest(requestId, rejectionDTO.getRejectReason());
        return CommonUtils.getSuccessResponse();
    }

    @GetMapping("/change_requests")
    public ResponseEntity<List<AssetQueryChangeRequest>> getMyAssetChangeRequests() {
        return ResponseEntity.ok(assetQueryChangeRequestService.getAssetChangeRequests());
    }

    @GetMapping("/change_requests/{changeRequestId}")
    public ResponseEntity<AssetQueryChangeRequest> getMyAssetChangeRequest(@PathVariable Long changeRequestId) {
        return ResponseEntity.ok(assetQueryChangeRequestService.getAssetChangeRequest(changeRequestId));
    }

    @PostMapping("/change_requests/set_approval")
    public ResponseEntity<AssetQueryChangeRequest> setApprovalAssetQueryChangeRequest(@RequestBody AccessQueryDTO changeRequestDTO) {
        return ResponseEntity.ok(assetQueryChangeRequestService.setApprovalAssetChangeRequest(changeRequestDTO));
    }

    @GetMapping("/{assetId}/asset_objects")
    public ResponseEntity<ArrayNode> getAvailableAssetObjects(@PathVariable Long assetId) {
        Asset asset = assetService.findById(assetId);
        return ResponseEntity.ok(accessLevelService.getAssetObjectsWithData(asset));
    }

    @GetMapping("/{accessRequestId}/access_level_objects")
    public ResponseEntity<AccessRequestDTO> getAccessLevelObjects(@PathVariable Long accessRequestId) {
        AccessRequest request = accessRequestService.findById(accessRequestId);
        AccessRequestDTO accessRequestDTO = new AccessRequestDTO();
        accessRequestDTO.setAccessLevelObjects(accessLevelService.getAccessLevelObjects(request));
        accessRequestDTO.setAccessRequest(request);
        return ResponseEntity.ok(accessRequestDTO);
    }

    @DeleteMapping("/credentials/{credentialId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteCredentialInfo(@PathVariable Long credentialId) {
        AssetCredential existingCredential = assetService.findCredentialById(credentialId);
        if (existingCredential == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset Credential not found in database");
        }
        //Needs to remove related asset objects
        assetObjectRepository.deleteByAssetCredential(existingCredential);
        //Remove existing developer's access request for this asset
        accessRequestRepository.findByAsset(existingCredential.getAsset()).forEach(accessRequest -> {
           accessLevelObjectRepository.deleteByAccessRequest(accessRequest);
        });
        accessRequestRepository.deleteByAsset(existingCredential.getAsset());
        
        // Remove temporary users created for this asset
        if (!existingCredential.getIsTemporaryPassword() && existingCredential.getPassword() != null) {
            databaseAccessService.cleanupTemporaryUsersForAsset(existingCredential.getAsset());
        }
        
        assetService.deleteAssetCredential(existingCredential);
        // Send notifications to admins using notification job
        List<User> admins = userService.getAdminRoleUsers();
        String myEmail = CommonUtils.getEmailFromSession();
        User currentUser = userService.findByEmail(myEmail);
        if (currentUser == null) {
            throw new AccessDeniedException("Current User not found");
        }
        
        if(admins != null) {
            for (User admin : admins) {
                createRelinquishNotificationTask(admin, currentUser, existingCredential, EmailType.RELINQUISH_ASSET_CREDENTIAL);
            }
        }
        return CommonUtils.getSuccessResponse();
    }

    @PostMapping("/credentials/{credentialId}")
    public ResponseEntity<Map<String, Object>> setCredentialInfo(@PathVariable Long credentialId, @RequestBody AssetCredentialDTO credentialInfo) {
        AssetCredential existingCredential = assetService.findCredentialById(credentialId);
        if (existingCredential == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset Credential not found in database");
        }

        Asset asset = existingCredential.getAsset();
        CredentialPair credentials = retrieveCredentials(credentialInfo);
        
        // Handle MongoDB separately since it doesn't use JDBC
        if (asset.getDatabaseType() == DatabaseType.MONGODB) {
            return setMongoDBCredentialInfo(existingCredential, asset, credentials.getUsername(), credentials.getPassword());
        }

        String jdbcUrl = buildJdbcUrl(asset);
        return validateAndSaveJdbcCredential(existingCredential, credentials, credentialInfo, jdbcUrl);
    }
    
    /**
     * Retrieves credentials from AWS Secrets Manager or traditional input
     */
    private CredentialPair retrieveCredentials(AssetCredentialDTO credentialInfo) {
        boolean awsSecretsManagerEnabled = Boolean.parseBoolean(
                systemSettingsService.getSettingValue(
                        Constants.AWS_SECRETS_MANAGER_ENABLED_KEY,
                        Constants.AWS_SECRETS_MANAGER_ENABLED_DEFAULT));
        
        if (awsSecretsManagerEnabled && credentialInfo.getAwsSecretsManagerKey() != null 
                && !credentialInfo.getAwsSecretsManagerKey().trim().isEmpty()) {
            return retrieveCredentialsFromAwsSecretsManager(credentialInfo);
        } else {
            return retrieveTraditionalCredentials(credentialInfo);
        }
    }
    
    /**
     * Retrieves credentials from AWS Secrets Manager
     */
    private CredentialPair retrieveCredentialsFromAwsSecretsManager(AssetCredentialDTO credentialInfo) {
        try {
            String password = awsSecretsManagerService.getPasswordFromSecret(credentialInfo.getAwsSecretsManagerKey());
            String username = credentialInfo.getUsername();
            
            if (username == null || username.trim().isEmpty()) {
                String secretUsername = awsSecretsManagerService.getUsernameFromSecret(credentialInfo.getAwsSecretsManagerKey());
                if (secretUsername != null && !secretUsername.trim().isEmpty()) {
                    username = secretUsername;
                } else {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                            "Username is required when not found in AWS Secrets Manager secret");
                }
            }
            return new CredentialPair(username, password);
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = "Failed to retrieve credentials from AWS Secrets Manager";
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
    }
    
    /**
     * Retrieves traditional credentials from DTO
     */
    private CredentialPair retrieveTraditionalCredentials(AssetCredentialDTO credentialInfo) {
        String username = credentialInfo.getUsername();
        String password = credentialInfo.getPassword();
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Username and password are required when AWS Secrets Manager is not enabled");
        }
        return new CredentialPair(username, password);
    }
    
    /**
     * Builds JDBC URL based on database type
     */
    private String buildJdbcUrl(Asset asset) {
        String host = asset.getHostUrl();
        switch (asset.getDatabaseType()) {
            case MYSQL:
                return "jdbc:mysql://" + host;
            case POSTGRESQL:
                return "jdbc:postgresql://" + host;
            case ORACLE:
                return "jdbc:oracle:thin:@" + host;
            case SQLSERVER:
                return "jdbc:sqlserver://" + host + ";encrypt=true;trustServerCertificate=true;characterEncoding=UTF-8";
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported database type");
        }
    }
    
    /**
     * Validates JDBC connection and saves credential
     */
    private ResponseEntity<Map<String, Object>> validateAndSaveJdbcCredential(
            AssetCredential existingCredential, CredentialPair credentials, 
            AssetCredentialDTO credentialInfo, String jdbcUrl) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, credentials.getUsername(), credentials.getPassword())) {
            if (connection == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Connection failed: Unknown error");
            }
            
            existingCredential.setUsername(credentials.getUsername());
            boolean awsSecretsManagerEnabled = Boolean.parseBoolean(
                    systemSettingsService.getSettingValue(
                            Constants.AWS_SECRETS_MANAGER_ENABLED_KEY,
                            Constants.AWS_SECRETS_MANAGER_ENABLED_DEFAULT));
            
            if (awsSecretsManagerEnabled && credentialInfo.getAwsSecretsManagerKey() != null 
                    && !credentialInfo.getAwsSecretsManagerKey().trim().isEmpty()) {
                saveAwsSecretsManagerCredential(existingCredential, credentials, credentialInfo);
            } else {
                saveTraditionalCredential(existingCredential, credentials);
            }
            
            existingCredential.setIsTemporaryPassword(false);
            assetService.saveCredential(existingCredential);
            return saveCredentialAfterValidation(existingCredential, credentials.getUsername(), credentials.getPassword());
        } catch (SQLException e) {
            // Connection failed
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Connection failed: " + e.getMessage());
        }
    }

    /**
     * Saves credential using AWS Secrets Manager
     */
    private void saveAwsSecretsManagerCredential(AssetCredential existingCredential, 
            CredentialPair credentials, AssetCredentialDTO credentialInfo) {
        existingCredential.setAwsSecretsManagerKey(credentialInfo.getAwsSecretsManagerKey());
        existingCredential.setPassword(null);
        
        AssetCredential tempCredential = new AssetCredential();
        tempCredential.setId(existingCredential.getId());
        tempCredential.setAsset(existingCredential.getAsset());
        tempCredential.setUsername(credentials.getUsername());
        tempCredential.setPassword(credentials.getPassword());
        tempCredential.setUser(existingCredential.getUser());
        tempCredential.setUserAccessType(existingCredential.getUserAccessType());
        try {
            databaseAccessService.updateAssetObjects(tempCredential);
        } catch (SQLException e) {
            logger.error(Constants.LOG_ERROR_UPDATE_ASSET_OBJECTS_FOR_CREDENTIAL, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "Failed to update asset objects: " + e.getMessage());
        }
    }
    
    /**
     * Saves traditional encrypted credential
     */
    private void saveTraditionalCredential(AssetCredential existingCredential, CredentialPair credentials) {
        String password = credentials.getPassword();
        if (authProvider.contains(Constants.AUTH_PROVIDER_KEYCLOAK.toLowerCase())) {
            existingCredential.setPassword(password);
            try {
                databaseAccessService.updateAssetObjects(existingCredential);
            } catch (SQLException e) {
                logger.error(Constants.LOG_ERROR_UPDATE_ASSET_OBJECTS_FOR_CREDENTIAL, e.getMessage(), e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to update asset objects: " + e.getMessage());
            }
            String userKey = keycloakService.getUserKey();
            if (userKey != null && !userKey.isEmpty()) {
                password = encryptPasswordWithKey(userKey, password);
            }
        }
        existingCredential.setPassword(password);
        existingCredential.setAwsSecretsManagerKey(null);
    }
    
    /**
     * Helper class to hold username and password pair
     */
    private static class CredentialPair {
        private final String username;
        private final String password;
        
        public CredentialPair(String username, String password) {
            this.username = username;
            this.password = password;
        }
        
        public String getUsername() {
            return username;
        }
        
        public String getPassword() {
            return password;
        }
    }

    /**
     * Sets MongoDB credential information and validates the connection
     */
    private ResponseEntity<Map<String, Object>> setMongoDBCredentialInfo(
            AssetCredential existingCredential, Asset asset, String username, String password) {
        
        try {
            // Build MongoDB connection string with proper URL encoding
            String connectionString = buildMongoConnectionStringWithAuth(asset, username, password);
            
            // Validate the MongoDB connection
            try (MongoClient client = MongoClients.create(connectionString)) {
                // Get the database instance
                String databaseName = asset.getDatabaseName();
                if (databaseName == null || databaseName.isEmpty()) {
                    databaseName = Constants.MONGODB_ADMIN_DATABASE; // Default to admin database
                }
                
                MongoDatabase database = client.getDatabase(databaseName);
                
                // Test connection by running a simple command (ping)
                database.runCommand(new Document(Constants.MONGODB_COMMAND_PING, 1));
                
                // Connection successful - save credentials
                return saveCredentialAfterValidation(existingCredential, username, password);
            }
        } catch (Exception e) {
            // Connection failed
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MongoDB connection failed: " + e.getMessage());
        }
    }

    /**
     * Builds MongoDB connection string with proper URL encoding and authSource parameter
     * Handles special characters in username and password by URL encoding them
     */
    private String buildMongoConnectionStringWithAuth(Asset asset, String username, String password) {
        StringBuilder connectionString = new StringBuilder("mongodb://");
        
        // URL encode username and password to handle special characters (e.g., #, @, etc.)
        String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8);
        String encodedPassword = URLEncoder.encode(password, StandardCharsets.UTF_8);
        
        // Add credentials
        connectionString.append(encodedUsername).append(":").append(encodedPassword).append("@");
        
        // Add host address
        String hostAddress = asset.getHostAddress();
        if (hostAddress != null && !hostAddress.isEmpty()) {
            connectionString.append(hostAddress);
        } else {
            // Fallback to hostUrl if hostAddress is not set
            String hostUrl = asset.getHostUrl();
            if (hostUrl != null && !hostUrl.isEmpty()) {
                // Remove protocol prefix if present
                hostUrl = hostUrl.replaceFirst("^mongodb://", "").replaceFirst("^mongodb\\+srv://", "");
                // Extract host part (before / or ?)
                String host = hostUrl.split("/")[0].split("\\?")[0];
                connectionString.append(host);
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MongoDB host address is required");
            }
        }
        
        // Add port if present
        String portNumber = asset.getPortNumber();
        if (portNumber != null && !portNumber.isEmpty() && !hasPortInConnectionString(connectionString.toString())) {
            // Check if port is already in hostAddress (avoid ReDoS by using simple string operations)
            connectionString.append(":").append(portNumber);
            
        }
        
        // Add database name if present
        String databaseName = asset.getDatabaseName();
        if (databaseName != null && !databaseName.isEmpty()) {
            connectionString.append("/").append(databaseName);
        }
        
        // Add authSource parameter (use database name from asset, or default to admin)
        // This is important for MongoDB authentication - authSource specifies which database contains the user
        String authSource = (databaseName != null && !databaseName.isEmpty()) ? databaseName : "admin";
        connectionString.append("?authSource=").append(authSource);
        
        return connectionString.toString();
    }

    /**
     * Checks if a MongoDB connection string already contains a port number.
     * Uses simple string operations to avoid ReDoS vulnerabilities.
     * 
     * @param connectionString The connection string to check (format: mongodb://user:pass@host[:port])
     * @return true if a port number is already present, false otherwise
     */
    private boolean hasPortInConnectionString(String connectionString) {
        int atIndex = connectionString.indexOf('@');
        if (atIndex < 0) {
            return false;
        }
        
        // Check if there's a colon after @ followed by digits (port number)
        String afterAt = connectionString.substring(atIndex + 1);
        int colonIndex = afterAt.indexOf(':');
        if (colonIndex < 0 || colonIndex >= afterAt.length() - 1) {
            return false;
        }
        
        // Extract the potential port part (between : and / or ? or end)
        String afterColon = afterAt.substring(colonIndex + 1);
        int slashIndex = afterColon.indexOf('/');
        int questionIndex = afterColon.indexOf('?');
        int endIndex = afterColon.length();
        if (slashIndex >= 0) {
            endIndex = Math.min(endIndex, slashIndex);
        }
        if (questionIndex >= 0) {
            endIndex = Math.min(endIndex, questionIndex);
        }
        
        if (endIndex <= 0) {
            return false;
        }
        
        // Check if the potential port is all digits
        String potentialPort = afterColon.substring(0, endIndex);
        return !potentialPort.isEmpty() && potentialPort.chars().allMatch(Character::isDigit);
    }

    /**
     * Saves credential after successful database connection validation
     * Handles Keycloak authentication provider encryption if needed
     * 
     * @param existingCredential The credential to save
     * @param username The username to set
     * @param password The password to set (will be encrypted if Keycloak provider)
     * @return Success response
     */
    private ResponseEntity<Map<String, Object>> saveCredentialAfterValidation(
            AssetCredential existingCredential, String username, String password) {
        if (authProvider.contains(Constants.AUTH_PROVIDER_KEYCLOAK.toLowerCase())) {
            existingCredential.setUsername(username);
            existingCredential.setPassword(password);
            try {
                databaseAccessService.updateAssetObjects(existingCredential);
            } catch (SQLException e) {
                logger.error(Constants.LOG_ERROR_UPDATE_ASSET_OBJECTS_FOR_CREDENTIAL, e.getMessage(), e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to update asset objects: " + e.getMessage());
            }
            String userKey = keycloakService.getUserKey();
            if (userKey != null && !userKey.isEmpty()) {
                password = encryptPasswordWithKey(userKey, password);
            }
        }
        existingCredential.setUsername(username);
        existingCredential.setPassword(password);
        existingCredential.setIsTemporaryPassword(false);
        assetService.saveCredential(existingCredential);
        return CommonUtils.getSuccessResponse();
    }

    private String encryptPasswordWithKey(String userKey, String password) {
        try {
            return CommonUtils.encrypt(userKey, password);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to encrypt password", e);
        }
    }

    @GetMapping("/roles")
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    // SSH Credential Management for Unix Server assets

    /**
     * Create SSH credentials for a Unix Server asset
     */
    @PostMapping("/{assetId}/ssh-credentials")
    public ResponseEntity<AssetCredentialDTO> createSSHCredentials(@PathVariable Long assetId, 
                                                               @RequestBody AssetCredentialDTO createDTO) {
        createDTO.setAssetId(assetId);
        
        boolean awsSecretsManagerEnabled = Boolean.parseBoolean(
                systemSettingsService.getSettingValue(
                        Constants.AWS_SECRETS_MANAGER_ENABLED_KEY,
                        Constants.AWS_SECRETS_MANAGER_ENABLED_DEFAULT));
        
        if (awsSecretsManagerEnabled && createDTO.getAwsSecretsManagerKey() != null 
                && !createDTO.getAwsSecretsManagerKey().trim().isEmpty()) {
            processAwsSecretsManagerSshKey(createDTO);
        } else {
            validateSshKeyFile(createDTO);
        }
        
        AssetCredential credential = assetService.createSSHCredential(assetId, createDTO);
        return ResponseEntity.ok(buildSshCredentialDto(credential));
    }
    
    /**
     * Processes SSH key from AWS Secrets Manager: retrieves, extracts username, and encrypts
     */
    private void processAwsSecretsManagerSshKey(AssetCredentialDTO createDTO) {
        try {
            String sshPrivateKey = awsSecretsManagerService.getSSHPrivateKeyFromSecret(createDTO.getAwsSecretsManagerKey());
            extractUsernameFromSecret(createDTO);
            encryptAndStoreSshKey(createDTO, sshPrivateKey);
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = "Failed to retrieve SSH private key from AWS Secrets Manager";
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
    }
    
    /**
     * Extracts username from AWS Secrets Manager secret if not provided
     */
    private void extractUsernameFromSecret(AssetCredentialDTO createDTO) {
        if (createDTO.getUsername() == null || createDTO.getUsername().trim().isEmpty()) {
            String secretUsername = awsSecretsManagerService.getUsernameFromSecret(createDTO.getAwsSecretsManagerKey());
            if (secretUsername != null && !secretUsername.trim().isEmpty()) {
                createDTO.setUsername(secretUsername);
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        "Username is required when not found in AWS Secrets Manager secret");
            }
        }
    }
    
    /**
     * Encrypts SSH key with user's key and stores it in DTO
     */
    private void encryptAndStoreSshKey(AssetCredentialDTO createDTO, String sshPrivateKey) {
        String userKey = keycloakService.getUserKey();
        if (userKey == null || userKey.isEmpty()) {
            throw new SecurityException("User encryption key not available");
        }
        try {
            String encryptedSSHKey = CommonUtils.encrypt(userKey, sshPrivateKey);
            createDTO.setSshKeyFile(encryptedSSHKey);
            logger.debug("Encrypted SSH key from AWS Secrets Manager and stored in credential");
        } catch (CommonUtils.CryptoException e) {
            throw new SecurityException("Failed to encrypt SSH key from AWS Secrets Manager", e);
        }
    }
    
    /**
     * Validates that SSH key file is provided when not using AWS Secrets Manager
     */
    private void validateSshKeyFile(AssetCredentialDTO createDTO) {
        if (createDTO.getSshKeyFile() == null || createDTO.getSshKeyFile().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Either sshKeyFile or awsSecretsManagerKey is required");
        }
    }
    
    /**
     * Builds AssetCredentialDTO from AssetCredential entity
     */
    private AssetCredentialDTO buildSshCredentialDto(AssetCredential credential) {
        return AssetCredentialDTO.builder()
                .assetId(credential.getAsset().getId())
                .username(credential.getUsername())
                .sshKeyFile(credential.getSshKeyFile())
                .awsSecretsManagerKey(credential.getAwsSecretsManagerKey())
                .userAccessType(credential.getUserAccessType())
                .assetType(credential.getAsset().getType())
                .build();
    }

    /**
     * Update existing SSH credentials
     */
    @PutMapping("/ssh-credentials/{id}")
    @Transactional
    public ResponseEntity<AssetCredentialDTO> updateSSHCredentials(@PathVariable Long id, 
                                                               @RequestBody AssetCredentialDTO updateDTO) {
        AssetCredential existingCredential = validateSshCredentialExists(id);
        updateUsernameIfProvided(existingCredential, updateDTO);
        
        boolean awsSecretsManagerEnabled = isAwsSecretsManagerEnabled();
        
        if (shouldUseAwsSecretsManagerForUpdate(awsSecretsManagerEnabled, updateDTO)) {
            updateSshKeyFromAwsSecretsManager(existingCredential, updateDTO, id);
        } else if (updateDTO.getSshKeyFile() != null) {
            updateSshKeyFromFile(existingCredential, updateDTO);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Either sshKeyFile or awsSecretsManagerKey is required");
        }
        
        AssetCredential savedCredential = assetService.saveCredential(existingCredential);
        logSshCredentialUpdateSuccess(id, savedCredential);
        
        return ResponseEntity.ok(buildSshCredentialDto(existingCredential));
    }
    
    /**
     * Validates that SSH credential exists
     */
    private AssetCredential validateSshCredentialExists(Long id) {
        AssetCredential existingCredential = assetService.findCredentialById(id);
        if (existingCredential == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SSH credentials not found");
        }
        return existingCredential;
    }
    
    /**
     * Updates username if provided in DTO
     */
    private void updateUsernameIfProvided(AssetCredential existingCredential, AssetCredentialDTO updateDTO) {
        if (updateDTO.getUsername() != null) {
            existingCredential.setUsername(updateDTO.getUsername());
        }
    }
    
    /**
     * Checks if AWS Secrets Manager is enabled
     */
    private boolean isAwsSecretsManagerEnabled() {
        return Boolean.parseBoolean(
                systemSettingsService.getSettingValue(
                        Constants.AWS_SECRETS_MANAGER_ENABLED_KEY,
                        Constants.AWS_SECRETS_MANAGER_ENABLED_DEFAULT));
    }
    
    /**
     * Checks if AWS Secrets Manager should be used for update
     */
    private boolean shouldUseAwsSecretsManagerForUpdate(boolean awsSecretsManagerEnabled, AssetCredentialDTO updateDTO) {
        return awsSecretsManagerEnabled 
                && updateDTO.getAwsSecretsManagerKey() != null 
                && !updateDTO.getAwsSecretsManagerKey().trim().isEmpty();
    }
    
    /**
     * Updates SSH key from AWS Secrets Manager
     */
    private void updateSshKeyFromAwsSecretsManager(AssetCredential existingCredential, 
            AssetCredentialDTO updateDTO, Long id) {
        try {
            String sshPrivateKey = awsSecretsManagerService.getSSHPrivateKeyFromSecret(updateDTO.getAwsSecretsManagerKey());
            extractAndSetUsernameFromSecret(existingCredential, updateDTO);
            encryptAndStoreSshKeyForUpdate(existingCredential, sshPrivateKey, id);
            existingCredential.setAwsSecretsManagerKey(updateDTO.getAwsSecretsManagerKey());
            existingCredential.setIsTemporaryPassword(false);
            logger.info("Updated SSH credential ID {} with AWS Secrets Manager key: {}, SSH key encrypted and stored", 
                    id, updateDTO.getAwsSecretsManagerKey());
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.trim().isEmpty()) {
                errorMessage = "Failed to retrieve SSH private key from AWS Secrets Manager";
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, errorMessage);
        }
    }
    
    /**
     * Extracts and sets username from AWS Secrets Manager secret if not provided
     */
    private void extractAndSetUsernameFromSecret(AssetCredential existingCredential, AssetCredentialDTO updateDTO) {
        if (updateDTO.getUsername() == null || updateDTO.getUsername().trim().isEmpty()) {
            String secretUsername = awsSecretsManagerService.getUsernameFromSecret(updateDTO.getAwsSecretsManagerKey());
            if (secretUsername != null && !secretUsername.trim().isEmpty()) {
                existingCredential.setUsername(secretUsername);
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                        "Username is required when not found in AWS Secrets Manager secret");
            }
        }
    }
    
    /**
     * Encrypts SSH key with user's key and stores it in credential
     */
    private void encryptAndStoreSshKeyForUpdate(AssetCredential existingCredential, String sshPrivateKey, Long id) {
        String userKey = keycloakService.getUserKey();
        if (userKey == null || userKey.isEmpty()) {
            throw new SecurityException("User encryption key not available");
        }
        try {
            String encryptedSSHKey = CommonUtils.encrypt(userKey, sshPrivateKey);
            existingCredential.setSshKeyFile(encryptedSSHKey);
            logger.debug("Encrypted SSH key from AWS Secrets Manager and stored in credential ID {}", id);
        } catch (CommonUtils.CryptoException e) {
            throw new SecurityException("Failed to encrypt SSH key from AWS Secrets Manager", e);
        }
    }
    
    /**
     * Updates SSH key from traditional file input
     */
    private void updateSshKeyFromFile(AssetCredential existingCredential, AssetCredentialDTO updateDTO) {
        String userKey = keycloakService.getUserKey();
        if (userKey == null || userKey.isEmpty()) {
            throw new SecurityException("User encryption key not available");
        }
        try {
            String encryptedSSHKey = CommonUtils.encrypt(userKey, updateDTO.getSshKeyFile());
            existingCredential.setSshKeyFile(encryptedSSHKey);
            existingCredential.setAwsSecretsManagerKey(null);
            existingCredential.setIsTemporaryPassword(false);
        } catch (CommonUtils.CryptoException e) {
            throw new SecurityException("Failed to encrypt SSH key", e);
        }
    }
    
    /**
     * Logs successful SSH credential update
     */
    private void logSshCredentialUpdateSuccess(Long id, AssetCredential savedCredential) {
        logger.info("Successfully saved SSH credential ID {} with username: {}, AWS Secrets Manager key: {}", 
                id, savedCredential.getUsername(), savedCredential.getAwsSecretsManagerKey());
    }

    /**
     * Delete SSH credentials
     */
    @DeleteMapping("/ssh-credentials/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteSSHCredentials(@PathVariable Long id) {
        AssetCredential existingCredential = assetService.findCredentialById(id);
        if (existingCredential == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SSH credentials not found");
        }
        
        assetService.deleteAssetCredential(existingCredential);
        
        // Send notifications to admins using notification job
        List<User> admins = userService.getAdminRoleUsers();
        String myEmail = CommonUtils.getEmailFromSession();
        User currentUser = userService.findByEmail(myEmail);
        if (currentUser == null) {
            throw new AccessDeniedException("Current User not found");
        }
        
        if(admins != null) {
            for (User admin : admins) {
                createRelinquishNotificationTask(admin, currentUser, existingCredential, EmailType.SSH_CREDENTIAL_RELINQUISH);
            }
        }
        
        return CommonUtils.getSuccessResponse();
    }

    /**
     * Get current user's SSH credentials
     */
    @GetMapping("/ssh-credentials")
    public ResponseEntity<List<AssetCredentialDTO>> getSSHCredentials() {
        List<AssetCredential> allCredentials = assetService.getAssignedCredentials();
        
        List<AssetCredentialDTO> sshCredentials = allCredentials.stream()
                .filter(cred -> cred.getAsset().getType() == AssetType.UNIX_SERVER && cred.getSshKeyFile() != null)
                .map(cred -> AssetCredentialDTO.builder()
                        .assetId(cred.getAsset().getId())
                        .username(cred.getUsername())
                        .sshKeyFile(cred.getSshKeyFile())
                        .userAccessType(cred.getUserAccessType())
                        .assetType(cred.getAsset().getType())
                        .build())
                .toList();
        
        return ResponseEntity.ok(sshCredentials);
    }

    /**
     * Execute a query on an asset as an Asset Owner
     * This endpoint allows Asset Owners to run queries on their assets for validation and management purposes
     * 
     * @param queryDto Query details including asset ID and SQL query
     * @return Query results with appropriate masking applied
     */
    @PostMapping("/run_query")
    public ResponseEntity<Map<String, Object>> runAssetQuery(@RequestBody AccessQueryDTO queryDto) {
        String currentUserEmail = CommonUtils.getEmailFromSession();
        logger.info("Asset Owner {} executing query on asset ID: {}", currentUserEmail, queryDto.getAssetId());
        
        try {
            // Use the shared query execution service
            Map<String, Object> queryResult = queryExecutionService.executeQueryForAssetOwner(queryDto);
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put(Constants.STATUS_NAME, Constants.getMessage("status.success"));
            response.put("results", queryResult);
            
            logger.info("Asset Owner {} successfully executed query on asset ID: {} - results returned", 
                       currentUserEmail, queryDto.getAssetId());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error running query for Asset Owner {}: {}", currentUserEmail, e.getMessage(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Convert natural language query to SQL for Asset Owner
     * POST /api/asset_owner/assets/convert_nl_to_sql
     */
    @PostMapping("/convert_nl_to_sql")
    public ResponseEntity<Map<String, Object>> convertNaturalLanguageToSql(@RequestBody NaturalLanguageQueryDTO request) {
        String currentUserEmail = CommonUtils.getEmailFromSession();
        logger.info("Asset Owner {} converting NL to SQL - assetId: {}, query: {}", 
                currentUserEmail, request.getAssetId(), request.getNaturalLanguageQuery());
        
        try {
            Map<String, Object> result = naturalLanguageToSqlService.convertNaturalLanguageToSqlForAssetOwner(request);
            
            Map<String, Object> response = new LinkedHashMap<>();
            response.put(Constants.STATUS_NAME, Constants.getMessage("status.success"));
            response.putAll(result);
            
            logger.info("Asset Owner {} successfully converted NL to SQL for asset ID: {}", 
                    currentUserEmail, request.getAssetId());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error converting NL to SQL for Asset Owner {}: {}", currentUserEmail, e.getMessage(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Create a notification task for relinquish emails
     */
    private void createRelinquishNotificationTask(User admin, User currentUser, AssetCredential assetCredential, EmailType emailType) {
        try {
            // Create notification data
            ObjectNode notificationData = objectMapper.createObjectNode();
            ObjectNode dataNode = objectMapper.createObjectNode();
            dataNode.put(Constants.EMAIL_VAR_ASSET_NAME, assetCredential.getAsset().getName());
            dataNode.put(Constants.EMAIL_VAR_ASSET_CREDENTIAL_ID, assetCredential.getId().toString());
            dataNode.put(Constants.EMAIL_VAR_OWNER_NAME, currentUser.getFirstName() + " " + currentUser.getLastName());
            dataNode.put(Constants.EMAIL_VAR_ADMIN_NAME, admin.getFirstName() + " " + admin.getLastName());
            
            notificationData.set(Constants.ACCESS_OBJECT_ATTR_DATA, dataNode);
            
            // Create the notification task
            NotificationTask task = new NotificationTask();
            task.setReceiver(admin);
            task.setSender(currentUser);
            task.setAsset(assetCredential.getAsset());
            task.setEmailType(emailType);
            task.setNotificationMessage(objectMapper.writeValueAsString(notificationData));
            task.setSent(false);
            
            // Save the notification task - it will be picked up by the batch job
            notificationTaskRepository.save(task);
            
        } catch (Exception e) {
            logger.error("Failed to create notification task for admin {}: {}", admin.getEmail(), e.getMessage(), e);
        }
    }

    /**
     * Get AWS Secrets Manager enabled setting
     * Accessible by both admin and asset owner
     */
    @GetMapping("/settings/aws-secrets-manager-enabled")
    public ResponseEntity<Map<String, Object>> getAWSSecretsManagerEnabled() {
        boolean enabled = systemSettingsService.isAWSSecretsManagerEnabled();
        Map<String, Object> response = new HashMap<>();
        response.put("enabled", enabled);
        return ResponseEntity.ok(response);
    }
}