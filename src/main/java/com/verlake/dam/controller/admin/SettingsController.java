package com.verlake.dam.controller.admin;

import com.verlake.dam.entity.S3BucketSettings;
import com.verlake.dam.service.S3Service;
import com.verlake.dam.service.S3SettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@RestController
@RequestMapping("/api/admin/settings")
@Slf4j
public class SettingsController {
    @Autowired
    private S3Service s3Service;

    @Autowired
    private S3SettingsService settingsService;

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
            S3BucketSettings settings = settingsService.updateS3BucketSettings(request.getBucketName());
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
}
