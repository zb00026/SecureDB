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
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.ApprovalStatus;
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
import com.verlake.dam.enums.AssetType;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.email.EmailService;
import com.verlake.dam.service.users.UserService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ArrayNode;

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
    private QueryExecutionService queryExecutionService;

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
        assetService.updateAsset(assetId, assetDTO);
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
    @Override
    public ResponseEntity<?> getAssetAccess(@PathVariable Long id) {
        return super.getAssetAccess(id);
    }

    /**
     * Ping an asset to test connectivity without credentials
     * This endpoint allows asset owners to verify that the asset's connection details are correct
     * 
     * @param id Asset ID to ping
     * @return PingResult containing success status and response time
     */
    @PostMapping("/{id}/ping")
    public ResponseEntity<PingResult> pingAsset(@PathVariable Long id) {
        String currentUserEmail = CommonUtils.getEmailFromSession();
        logger.info("Asset owner {} pinging asset ID: {}", currentUserEmail, id);
        
        PingResult result = assetService.pingAsset(id);
        
        logger.info("Asset ping completed for asset ID: {} by owner: {}. Success: {}", 
                   id, currentUserEmail, result.isSuccess());
        
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
        assetService.deleteAssetCredential(existingCredential);
        List<User> admins = userService.getAdminRoleUsers();
        String myEmail = CommonUtils.getEmailFromSession();
        User currentUser = userService.findByEmail(myEmail);
        if (currentUser == null) {
            throw new AccessDeniedException("Current User not found");
        }
        String emailTmplFile = "relinquish-asset-credential";
        if(admins != null) {
            for (User admin : admins) {
                emailService.sendAssetRelinquishEmail(admin, currentUser, existingCredential, emailTmplFile);
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
        String host = asset.getHostUrl();
        String username = credentialInfo.getUsername();
        String password = credentialInfo.getPassword();

        String jdbcUrl;
        switch (asset.getDatabaseType()) {
            case MYSQL:
                jdbcUrl = "jdbc:mysql://" + host;
                break;
            case POSTGRESQL:
                jdbcUrl = "jdbc:postgresql://" + host;
                break;
            case ORACLE:
                jdbcUrl = "jdbc:oracle:thin:@" + host;
                break;
            case SQLSERVER:
                // Add SSL parameters for MSSQL to match application configuration
                jdbcUrl = "jdbc:sqlserver://" + host + ";encrypt=true;trustServerCertificate=true;characterEncoding=UTF-8";
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported database type");
        }

        // Validate the database connection
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            if (connection != null) {
                if (authProvider.contains(Constants.AUTH_PROVIDER_KEYCLOAK.toLowerCase())) {
                    existingCredential.setUsername(username);
                    existingCredential.setPassword(password);
                    databaseAccessService.updateAssetObjects(existingCredential);
                    String userKey = keycloakService.getUserKey();
                    if(userKey != null && !userKey.isEmpty()) {
                        password = encryptPasswordWithKey(userKey, password);
                    }
                }
                existingCredential.setUsername(username);
                existingCredential.setPassword(password);
                assetService.saveCredential(existingCredential);
                return CommonUtils.getSuccessResponse();
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Connection failed: Unknown error");
            }
        } catch (SQLException e) {
            // Connection failed
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Connection failed: " + e.getMessage());
        }
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
        AssetCredential credential = assetService.createSSHCredential(assetId, createDTO);
        
        AssetCredentialDTO result = AssetCredentialDTO.builder()
                .assetId(credential.getAsset().getId())
                .username(credential.getUsername())
                .sshKeyFile(credential.getSshKeyFile())
                .userAccessType(credential.getUserAccessType())
                .assetType(credential.getAsset().getType())
                .build();
        
        return ResponseEntity.ok(result);
    }

    /**
     * Update existing SSH credentials
     */
    @PutMapping("/ssh-credentials/{id}")
    public ResponseEntity<AssetCredentialDTO> updateSSHCredentials(@PathVariable Long id, 
                                                               @RequestBody AssetCredentialDTO updateDTO) {
        AssetCredential existingCredential = assetService.findCredentialById(id);
        if (existingCredential == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SSH credentials not found");
        }
        
        // Update fields
        if (updateDTO.getUsername() != null) {
            existingCredential.setUsername(updateDTO.getUsername());
        }
        
        if (updateDTO.getSshKeyFile() != null) {
            // Encrypt the new SSH key file
            String userKey = keycloakService.getUserKey();
            if (userKey == null || userKey.isEmpty()) {
                throw new SecurityException("User encryption key not available");
            }
            try {
                String encryptedSSHKey = CommonUtils.encrypt(userKey, updateDTO.getSshKeyFile());
                existingCredential.setSshKeyFile(encryptedSSHKey);
            } catch (CommonUtils.CryptoException e) {
                throw new SecurityException("Failed to encrypt SSH key", e);
            }
        }
        
        assetService.saveCredential(existingCredential);
        
        AssetCredentialDTO result = AssetCredentialDTO.builder()
                .assetId(existingCredential.getAsset().getId())
                .username(existingCredential.getUsername())
                .sshKeyFile(existingCredential.getSshKeyFile())
                .userAccessType(existingCredential.getUserAccessType())
                .assetType(existingCredential.getAsset().getType())
                .build();
        
        return ResponseEntity.ok(result);
    }

    /**
     * Delete SSH credentials
     */
    @DeleteMapping("/ssh-credentials/{id}")
    public ResponseEntity<Map<String, Object>> deleteSSHCredentials(@PathVariable Long id) {
        AssetCredential existingCredential = assetService.findCredentialById(id);
        if (existingCredential == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SSH credentials not found");
        }
        
        assetService.deleteAssetCredential(existingCredential);
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
}