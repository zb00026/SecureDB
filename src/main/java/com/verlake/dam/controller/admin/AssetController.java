package com.verlake.dam.controller.admin;

import com.verlake.dam.controller.common.BaseAssetAccessController;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.dto.AssetDTO;
import com.verlake.dam.entity.assets.dto.AssetUpdateDTO;
import com.verlake.dam.entity.assets.dto.PingResult;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.service.assets.AssetCsvService;
import com.verlake.dam.service.assets.AssetService;
import com.verlake.dam.service.email.EmailService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/assets")
public class AssetController extends BaseAssetAccessController {
    private final UserService userService;
    private final EmailService emailService;
    private final AssetCsvService assetCsvService;
    private static final Logger logger = LoggerFactory.getLogger(AssetController.class);

    @Autowired
    public AssetController(AssetService assetService, UserService userService, EmailService emailService, AssetCsvService assetCsvService) {
        super(assetService);
        this.userService = userService;
        this.emailService = emailService;
        this.assetCsvService = assetCsvService;
    }

    @GetMapping
    public ResponseEntity<List<AssetDTO>> getAllAssets() {
        return ResponseEntity.ok(assetService.getAllAssets());
    }

    @PostMapping
    public ResponseEntity<Asset> createAsset(@RequestBody AssetDTO assetDTO) {
        return ResponseEntity.ok(assetService.createAsset(assetDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateAsset(@PathVariable Long id, @RequestBody AssetDTO updateDTO) {
        assetService.updateAsset(id, updateDTO, true);
        return CommonUtils.getSuccessResponse();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteAsset(@PathVariable Long id) {
        assetService.deleteAsset(id);
        return CommonUtils.getSuccessResponse();
    }

    @PostMapping("/{id}/owners")
    public ResponseEntity<Map<String, Object>> updateAssetOwners(
            @PathVariable Long id,
            @RequestBody AssetUpdateDTO updateDTO) {
        updateDTO.setAssetId(id);
        assetService.updateAssetOwners(updateDTO);
        return CommonUtils.getSuccessResponse();
    }

    @PostMapping("/{id}/approvers")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateAssetApprovers(
            @PathVariable Long id,
            @RequestBody AssetUpdateDTO updateDTO) {
        updateDTO.setAssetId(id);
        assetService.updateAssetApprovers(updateDTO);
        List<User> newApprovers = updateDTO.getUserIds().stream()
                .map(userId -> userService.findById(userId))
                .filter(user -> user != null)
                .toList();
        String emailTmplFile = "asset-approver-notify";
        Asset asset = assetService.findById(updateDTO.getAssetId());
        for (User approver : newApprovers) {
            emailService.sendAssetApproveNotifyEmail(asset, approver, updateDTO.getMethod(), emailTmplFile);
        }
        return CommonUtils.getSuccessResponse();
    }

    /**
     * Get real-time user access information for an asset by querying the target
     * database directly
     */
    @GetMapping("/{id}/access")
    public ResponseEntity<?> getAssetAccess(@PathVariable Long id) {
        return super.getAssetAccess(id, Roles.ASSET_OWNER.getOriginalName());
    }

    /**
     * Ping an asset to test connectivity without credentials
     * This endpoint allows admins to verify that the asset's connection details are correct
     * 
     * @param id Asset ID to ping
     * @return PingResult containing success status and response time
     */
    @PostMapping("/{id}/ping")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public ResponseEntity<PingResult> pingAsset(
            @PathVariable Long id) {
        String currentAdminEmail = CommonUtils.getEmailFromSession();
        logger.info("Admin {} pinging asset ID: {}", currentAdminEmail, id);
        
        PingResult result = assetService.pingAsset(id, true);
        
        logger.info("Asset ping completed for asset ID: {} by admin: {}. Success: {}", 
                   id, currentAdminEmail, result.isSuccess());
        
        return ResponseEntity.ok(result);
    }

    /**
     * Lock out users in the asset database (Admin only)
     * 
     * @param id           Asset ID
     *                     If false, only locks Hagrids users.
     */
    @PostMapping("/{id}/lockout")
    public ResponseEntity<Map<String, Object>> lockoutAssetUsers(
            @PathVariable Long id) {

        String currentAdminEmail = CommonUtils.getEmailFromSession();
        logger.info("Admin {} initiating lockout for asset ID: {}, lockAllUsers: {}",
                currentAdminEmail, id, false);

        Map<String, Object> result = assetService.lockoutAssetUsers(id, false);

        logger.info("Lockout completed successfully for asset ID: {} by admin: {}", id, currentAdminEmail);
        return ResponseEntity.ok(result);
    }

    /**
     * Unlock users in the asset database (Admin only)
     * 
     * @param id             Asset ID
     *                       unlocks Hagrids users.
     */
    @PostMapping("/{id}/unlock")
    public ResponseEntity<Map<String, Object>> unlockAssetUsers(
            @PathVariable Long id) {

        String currentAdminEmail = CommonUtils.getEmailFromSession();
        logger.info("Admin {} initiating unlock for asset ID: {}, unlockAllUsers: {}",
                currentAdminEmail, id, false);

        Map<String, Object> result = assetService.unlockAssetUsers(id, false);

        logger.info("Unlock completed successfully for asset ID: {} by admin: {}", id, currentAdminEmail);
        return ResponseEntity.ok(result);
    }

    /**
     * Download sample CSV template for bulk asset upload
     */
    @GetMapping("/download-sample-csv")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public ResponseEntity<String> downloadSampleCSV() {
        logger.info("Generating sample CSV for bulk asset creation");

        String csvContent = assetCsvService.generateSampleCsvContent();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, Constants.ASSET_CSV_ATTACHMENT_HEADER);
        headers.add(HttpHeaders.CONTENT_TYPE, Constants.CSV_CONTENT_TYPE);

        logger.info("Sample CSV generated successfully");
        return ResponseEntity.ok()
                .headers(headers)
                .body(csvContent);
    }

    /**
     * Export all assets to CSV
     */
    @GetMapping("/export-csv")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public ResponseEntity<String> exportAssetsCSV() {
        logger.info("Exporting assets to CSV");

        String csvContent = assetCsvService.exportAssetsToCsv();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, Constants.ASSET_CSV_EXPORT_ATTACHMENT_HEADER);
        headers.add(HttpHeaders.CONTENT_TYPE, Constants.CSV_CONTENT_TYPE);

        logger.info("Assets exported successfully");
        return ResponseEntity.ok()
                .headers(headers)
                .body(csvContent);
    }

    /**
     * Bulk upload assets from CSV file
     */
    @PostMapping(value = "/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> bulkUploadAssets(@RequestParam("file") MultipartFile file) {

        Map<String, Object> response = assetCsvService.processBulkAssetUploadWithResponse(file);

        // Determine HTTP status based on success
        boolean success = (Boolean) response.get(Constants.RESPONSE_SUCCESS);
        if (success) {
            return buildJsonResponse(ResponseEntity.ok(), response);
        } else {
            // Check if it's a validation error (400) or server error (500)
            String message = (String) response.get(Constants.RESPONSE_MESSAGE);
            if (message.contains(Constants.getMessage(Constants.ERROR_VALIDATION_ERRORS_FOUND)) ||
                message.contains(Constants.getMessage(Constants.ERROR_UPLOADED_FILE_EMPTY)) ||
                message.contains(Constants.getMessage(Constants.ERROR_FILE_MUST_BE_CSV)) ||
                message.contains(Constants.getMessage(Constants.ERROR_NO_VALID_ASSET_DATA))) {
                return buildJsonResponse(ResponseEntity.badRequest(), response);
            } else {
                return buildJsonResponse(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR), response);
            }
        }
    }

    /**
     * Helper method to build ResponseEntity with consistent JSON headers
     */
    private ResponseEntity<Map<String, Object>> buildJsonResponse(
            ResponseEntity.BodyBuilder responseBuilder, 
            Map<String, Object> body) {
        return responseBuilder
                .header("Content-Type", Constants.CONTENT_TYPE_JSON)
                .header("Cache-Control", Constants.CACHE_CONTROL_NO_CACHE)
                .body(body);
    }

    @Override
    protected ResponseEntity<?> handleGenericError(RuntimeException e) {
        // Admin controller returns internal server error for generic errors
        return ResponseEntity.internalServerError().build();
    }
}