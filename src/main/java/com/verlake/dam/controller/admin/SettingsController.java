package com.verlake.dam.controller.admin;

import com.verlake.dam.entity.S3BucketSettings;
import com.verlake.dam.entity.SystemSettings;
import com.verlake.dam.service.s3.S3Service;
import com.verlake.dam.service.s3.S3SettingsService;
import com.verlake.dam.service.settings.SystemSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
@Slf4j
@RefreshScope
public class SettingsController {
    @Autowired
    private S3Service s3Service;

    @Autowired
    private S3SettingsService settingsService;
    
    @Autowired
    private SystemSettingsService systemSettingsService;

    public SettingsController() {

    }

    @PostMapping("/update-audit-log-storage")
    public ResponseEntity<?> updateAuditLogStorage(@RequestBody S3BucketSettings request) {
        log.info("Updating audit log storage for bucket: {}", request.getBucketName());
        
        try {
            // Validate versioning
            s3Service.checkVersioning(request.getBucketName());
            
            // Validate encryption
            s3Service.checkEncryption(request.getBucketName());
            
            // Validate permissions
            s3Service.checkPermissions(request.getBucketName());
            
            // If all validations pass, update settings
            S3BucketSettings settings = settingsService.updateS3BucketSettings(request.getBucketName(), request.getLocalRetentionDays());
            return ResponseEntity.ok(settings);
            
        } catch (S3Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/get-current-audit-log-storage")
    public ResponseEntity<?> getCurrentAuditLogStorage() {
        return settingsService.getCurrentSettings()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get AWS Secrets Manager enabled setting
     */
    @GetMapping("/aws-secrets-manager-enabled")
    public ResponseEntity<Map<String, Object>> getAWSSecretsManagerEnabled() {
        boolean enabled = systemSettingsService.isAWSSecretsManagerEnabled();
        Map<String, Object> response = new HashMap<>();
        response.put("enabled", enabled);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Update AWS Secrets Manager enabled setting
     */
    @PostMapping("/aws-secrets-manager-enabled")
    public ResponseEntity<SystemSettings> updateAWSSecretsManagerEnabled(@RequestBody Map<String, Boolean> request) {
        Boolean enabled = request.get("enabled");
        if (enabled == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "enabled field is required");
        }
        
        log.info("Updating AWS Secrets Manager enabled setting to: {}", enabled);
        SystemSettings setting = systemSettingsService.updateAWSSecretsManagerEnabled(enabled);
        return ResponseEntity.ok(setting);
    }

}
