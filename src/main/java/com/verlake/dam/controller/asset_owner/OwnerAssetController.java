package com.verlake.dam.controller.asset_owner;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.verlake.dam.entity.assets.AccessLevelObject;
import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.assets.dto.AccessRequestDTO;
import com.verlake.dam.entity.assets.dto.AssetCredentialDTO;
import com.verlake.dam.entity.assets.dto.AssetDTO;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.repository.assets.AccessLevelObjectRepository;
import com.verlake.dam.repository.assets.AccessRequestRepository;
import com.verlake.dam.repository.assets.AssetObjectRepository;
import com.verlake.dam.service.assets.AccessLevelService;
import com.verlake.dam.service.assets.AccessRequestService;
import com.verlake.dam.service.assets.AssetService;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.email.EmailService;
import com.verlake.dam.service.UserService;
import com.verlake.dam.utils.CommonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

import static com.verlake.dam.utils.Constants.AUTH_PROVIDER_KEYCLOAK;


@RestController
@RequestMapping("/api/asset_owner/assets")
public class OwnerAssetController {
    private final AssetService assetService;
    private final AccessRequestService accessRequestService;
    private final UserService userService;


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

    /**
     * Constructor for OwnerAssetController.
     * 
     * This constructor is intentionally empty as it's used by Spring's dependency injection.
     * Spring will automatically inject the required dependencies through constructor injection.
     * The @RequiredArgsConstructor annotation from Lombok will generate the actual constructor
     * with all final fields.
     * 
     * @throws UnsupportedOperationException if this constructor is called directly
     *         instead of being used by Spring's dependency injection
     */
    public OwnerAssetController(
            AssetService assetService,
            AccessRequestService accessRequestService,
            UserService userService,
            AssetObjectRepository assetObjectRepository) {
        this.assetService = assetService;
        this.accessRequestService = accessRequestService;
        this.userService = userService;
        this.assetObjectRepository = assetObjectRepository;
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

    @PostMapping("/request/{accessRequestId}/approve")
    public ResponseEntity<AccessRequest> approveAccessRequest(@PathVariable long accessRequestId, @RequestBody AccessRequestDTO accessRequest) {
        try {
            return ResponseEntity.ok(assetService.setApprovalStatusOfAccessRequest(accessRequestId, accessRequest, ApprovalStatus.APPROVED));
        } catch (JsonParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can not approve access request");
        }
    }

    @PostMapping("/request/{accessRequestId}/reject")
    public ResponseEntity<AccessRequest> rejectAccessRequest(@PathVariable long accessRequestId) {
        try {
            return ResponseEntity.ok(assetService.setApprovalStatusOfAccessRequest(accessRequestId, null, ApprovalStatus.REJECTED));
        } catch (JsonParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Can not reject access request");
        }
    }

    @GetMapping("/approvals")
    public ResponseEntity<List<AccessRequest>> getMyAssetApprovals() {
        return ResponseEntity.ok(assetService.getAssetRequestApprovals());
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

        String jdbcUrlPrefix;
        switch (asset.getDatabaseType()) {
            case MYSQL:
                jdbcUrlPrefix = "jdbc:mysql://";
                break;
            case POSTGRESQL:
                jdbcUrlPrefix = "jdbc:postgresql://";
                break;
            case ORACLE:
                jdbcUrlPrefix = "jdbc:oracle:thin:@";
                break;
            case SQLSERVER:
                jdbcUrlPrefix = "jdbc:sqlserver://";
                break;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported database type");
        }

        // Validate the database connection
        try (Connection connection = DriverManager.getConnection(jdbcUrlPrefix + host, username, password)) {
            if (connection != null) {
                if (authProvider.contains(AUTH_PROVIDER_KEYCLOAK.toLowerCase())) {
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
}