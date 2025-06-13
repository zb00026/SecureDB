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
import com.verlake.dam.service.UserService;
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
     * Get real-time user access information for an asset by querying the target database directly
     */
    @GetMapping("/{id}/access")
    @Override
    public ResponseEntity<?> getAssetAccess(@PathVariable Long id) {
        return super.getAssetAccess(id);
    }

    @Override
    protected ResponseEntity<?> handleGenericError(RuntimeException e) {
        // Admin controller returns internal server error for generic errors
        return ResponseEntity.internalServerError().build();
    }
} 