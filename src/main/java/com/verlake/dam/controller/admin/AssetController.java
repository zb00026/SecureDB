package com.verlake.dam.controller.admin;

import com.verlake.dam.controller.common.BaseAssetAccessController;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.assets.dto.AssetDTO;
import com.verlake.dam.entity.assets.dto.AssetUpdateDTO;
import com.verlake.dam.entity.assets.dto.AssetAccessDTO;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.service.assets.AssetService;
import com.verlake.dam.service.email.EmailService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.CommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/admin/assets")
public class AssetController extends BaseAssetAccessController {
    private final UserService userService;
    private final EmailService emailService;
    private static final Logger logger = LoggerFactory.getLogger(AssetController.class);

    @Autowired
    public AssetController(AssetService assetService, UserService userService, EmailService emailService) {
        super(assetService);
        this.userService = userService;
        this.emailService = emailService;
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
        assetService.updateAsset(id, updateDTO);
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
    @Override
    public ResponseEntity<?> getAssetAccess(@PathVariable Long id) {
        return super.getAssetAccess(id);
    }

    /**
     * Lock out users in the asset database (Admin only)
     * 
     * @param id           Asset ID
     * @param lockAllUsers If true, locks all database users including applications.
     *                     If false, only locks Hagrid users.
     */
    @PostMapping("/{id}/lockout")
    public ResponseEntity<Map<String, Object>> lockoutAssetUsers(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean lockAllUsers) {

        String currentAdminEmail = CommonUtils.getEmailFromSession();
        logger.info("Admin {} initiating lockout for asset ID: {}, lockAllUsers: {}",
                currentAdminEmail, id, lockAllUsers);

        Map<String, Object> result = assetService.lockoutAssetUsers(id, lockAllUsers);

        logger.info("Lockout completed successfully for asset ID: {} by admin: {}", id, currentAdminEmail);
        return ResponseEntity.ok(result);
    }

    /**
     * Unlock users in the asset database (Admin only)
     * 
     * @param id             Asset ID
     * @param unlockAllUsers If true, unlocks all database users. If false, only
     *                       unlocks Hagrid users.
     */
    @PostMapping("/{id}/unlock")
    public ResponseEntity<Map<String, Object>> unlockAssetUsers(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean unlockAllUsers) {

        String currentAdminEmail = CommonUtils.getEmailFromSession();
        logger.info("Admin {} initiating unlock for asset ID: {}, unlockAllUsers: {}",
                currentAdminEmail, id, unlockAllUsers);

        Map<String, Object> result = assetService.unlockAssetUsers(id, unlockAllUsers);

        logger.info("Unlock completed successfully for asset ID: {} by admin: {}", id, currentAdminEmail);
        return ResponseEntity.ok(result);
    }

    @Override
    protected ResponseEntity<?> handleGenericError(RuntimeException e) {
        // Admin controller returns internal server error for generic errors
        return ResponseEntity.internalServerError().build();
    }
}