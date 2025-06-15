package com.verlake.dam.controller.developer;

import com.verlake.dam.controller.common.BaseAssetAccessController;
import com.verlake.dam.entity.assets.AccessLevelObject;
import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.assets.dto.AccessQueryDTO;
import com.verlake.dam.entity.assets.dto.AccessRequestDTO;
import com.verlake.dam.entity.assets.dto.AssetCredentialDTO;
import com.verlake.dam.entity.assets.dto.AssetDTO;
import com.verlake.dam.entity.assets.dto.AssetAccessDTO;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.service.assets.AccessLevelService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import jakarta.persistence.Access;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.verlake.dam.service.assets.AccessRequestService;
import com.verlake.dam.service.assets.AssetQueryChangeRequestService;
import com.verlake.dam.service.assets.AssetService;
import com.verlake.dam.service.users.UserService;

import org.apache.hadoop.yarn.exceptions.ResourceNotFoundException;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ArrayNode;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

@RestController
@RequestMapping("/api/developer/assets")
@Slf4j
public class AccessRequestController extends BaseAssetAccessController {

    private final AssetQueryChangeRequestService assetQueryChangeRequestService;
    private final AccessRequestService accessRequestService;
    private final AccessLevelService accessLevelService;
    private final UserService userService;

    @Autowired
    public AccessRequestController(AssetService assetService, 
                                 AssetQueryChangeRequestService assetQueryChangeRequestService,
                                 AccessRequestService accessRequestService,
                                 AccessLevelService accessLevelService,
                                 UserService userService) {
        super(assetService);
        this.assetQueryChangeRequestService = assetQueryChangeRequestService;
        this.accessRequestService = accessRequestService;
        this.accessLevelService = accessLevelService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<AssetDTO>> getAllAssets() {
        return ResponseEntity.ok(assetService.getAllAssetsWithFetchAccessTemplate());
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

    @Override
    protected ResponseEntity<?> handleGenericError(RuntimeException e) {
        // Developer controller returns 500 with error message for generic errors
        Map<String, String> errorResponse = Map.of("error", "Failed to fetch asset access information");
        return ResponseEntity.status(500).body(errorResponse);
    }

    /**
     * Retrieves available asset objects and their associated access grants for a specific asset.
     * The response is an array of objects, each representing a different type of database object
     * (DATABASE, PROCEDURE, TABLE, VIEW) with their respective access grants and available data.
     *
     * @param assetId The ID of the asset to retrieve objects for
     * @return ResponseEntity containing an ArrayNode with the following structure:
     * [
     *   {
     *     "name": "DATABASE",
     *     "grants": [
     *       {
     *         "id": 17,
     *         "templates": "FULL ACCESS",
     *         "accessTemplate": "GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, INDEX, CREATE VIEW, SHOW VIEW, TRIGGER, REFERENCES, EXECUTE ON $DB.* TO '$USER'@'%';",
     *         "object": "DATABASE"
     *       },
     *       // ... other grants
     *     ],
     *     "data": ["dam"] // List of available databases
     *   },
     *   {
     *     "name": "PROCEDURE",
     *     "grants": [
     *       {
     *         "id": 14,
     *         "templates": "EXECUTE",
     *         "accessTemplate": "GRANT EXECUTE ON PROCEDURE $DB.$PROCEDURE TO '$USER'@'%';",
     *         "object": "PROCEDURE"
     *       },
     *       // ... other grants
     *     ],
     *     "data": [] // List of available procedures
     *   },
     *   {
     *     "name": "TABLE",
     *     "grants": [
     *       {
     *         "id": 1,
     *         "templates": "SELECT",
     *         "accessTemplate": "GRANT SELECT ON $DB.$TABLE TO '$USER'@'%';",
     *         "object": "TABLE"
     *       },
     *       // ... other grants
     *     ],
     *     "data": ["dam.access_level_objects", "dam.access_levels", ...] // List of available tables
     *   },
     *   {
     *     "name": "VIEW",
     *     "grants": [
     *       {
     *         "id": 12,
     *         "templates": "SELECT",
     *         "accessTemplate": "GRANT SELECT ON $DB.$VIEW TO '$USER'@'%';",
     *         "object": "VIEW"
     *       },
     *       // ... other grants
     *     ],
     *     "data": [] // List of available views
     *   }
     * ]
     */
    @GetMapping("/{assetId}/asset_objects")
    public ResponseEntity<ArrayNode> getAvailableAssetObjects(@PathVariable Long assetId) {
        Asset asset = assetService.findById(assetId);
        return ResponseEntity.ok(accessLevelService.getAssetObjectsWithData(asset));
    }

    @GetMapping("/{accessRequestId}/access_level_objects")
    public ResponseEntity<List<AccessLevelObject>> getAccessLevelObjects(@PathVariable Long accessRequestId) {
        AccessRequest request = accessRequestService.findById(accessRequestId);
        return ResponseEntity.ok(accessLevelService.getAccessLevelObjects(request));
    }

    @PostMapping("/request")
    public AccessRequest saveAccessRequest(@RequestBody AccessRequestDTO accessRequest) {
        User requestor = userService.findByEmail(CommonUtils.getEmailFromSession());
        try {
            return accessRequestService.saveAccessRequest(accessRequest, requestor);
        } catch (Exception e) {
            log.error("Error saving access request: {}", e.getMessage(), e);
            throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping("/get_newly_approved_requests")
    public ResponseEntity<List<AccessRequest>> getNewlyApprovedRequests() {
        User requestor = userService.findByEmail(CommonUtils.getEmailFromSession());
        return ResponseEntity.ok(accessRequestService.getTemporaryCredentialRequests(requestor));
    }

    @PostMapping("/set_credential_password/{accessRequestId}")
    public ResponseEntity<AccessRequest> setCredentialPassword(@PathVariable Long accessRequestId, @RequestBody AssetCredentialDTO credentialInfo) {
        try {
            return ResponseEntity.ok(accessRequestService.setCredentialPassword(accessRequestId, credentialInfo));
        } catch (Exception e) {
            log.error("Error saving access request credential password: {}", e.getMessage(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping("/relinquish_access/{accessRequestId}")
    public ResponseEntity<Map<String, Object>> relinquishAccess(@PathVariable Long accessRequestId) {
        try {
            accessRequestService.relinquishAccess(accessRequestId);
            return CommonUtils.getSuccessResponse();
        } catch (Exception e) {
            log.error("Error relinquishing access: {}", e.getMessage(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping("/run_query")
    public ResponseEntity<Map<String, Object>> runAssetQuery(@RequestBody AccessQueryDTO queryDto) {
        try {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put(Constants.STATUS_NAME, Constants.STATUS_SUCCESS);
            response.put("results", assetQueryChangeRequestService.runQueryFromDeveloper(queryDto));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error relinquishing access: {}", e.getMessage(), e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

} 