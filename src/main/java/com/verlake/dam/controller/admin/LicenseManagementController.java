package com.verlake.dam.controller.admin;

import com.verlake.dam.configuration.LicenseManager;
import com.verlake.dam.entity.License;
import com.verlake.dam.entity.dto.LicenseStatusDTO;
import com.verlake.dam.exception.LicenseException;
import com.verlake.dam.service.LicenseService;
import com.verlake.dam.service.SystemInfoService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;

@RestController
@RequestMapping("/api/admin/license")
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class LicenseManagementController {
    
    private static final String SUCCESS_KEY = "success";
    private static final String MESSAGE_KEY = "message";
    
    private final LicenseService licenseService;
    private final LicenseManager licenseManager;
    private final SystemInfoService systemInfoService;
    
    @Autowired
    public LicenseManagementController(LicenseService licenseService, LicenseManager licenseManager, SystemInfoService systemInfoService) {
        this.licenseService = licenseService;
        this.licenseManager = licenseManager;
        this.systemInfoService = systemInfoService;
    }
    
    /**
     * Upload a new license file (Admin only)
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadLicense(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description) {
        
        try {
            License license = licenseService.uploadLicense(file, description);
            
            // Trigger license revalidation to immediately use the new license
            licenseManager.revalidateLicense();
            
            Map<String, Object> response = Map.of(
                SUCCESS_KEY, true,
                MESSAGE_KEY, "License uploaded successfully",
                "license", Map.of(
                    "id", license.getId(),
                    "filename", license.getFilename(),
                    "fileSize", license.getFileSize(),
                    "uploadedBy", license.getUploadedBy(),
                    "uploadedAt", license.getCreatedAt()
                )
            );
            
            log.info("License uploaded successfully by admin: {}", CommonUtils.getEmailFromSession());
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid license file upload attempt: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                SUCCESS_KEY, false,
                MESSAGE_KEY, e.getMessage()
            ));
        } catch (LicenseException e) {
            log.error("License processing error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                SUCCESS_KEY, false,
                MESSAGE_KEY, "License processing failed: " + e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error uploading license", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                SUCCESS_KEY, false,
                MESSAGE_KEY, "Failed to upload license: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get current license information (Admin only)
     */
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentLicense() {
        try {
            Optional<License> activeLicense = licenseService.getActiveLicense();
            boolean usingDatabaseLicense = licenseManager.isUsingDatabaseLicense();
            
            if (activeLicense.isPresent()) {
                License license = activeLicense.get();
                Map<String, Object> response = Map.of(
                    "hasLicense", true,
                    Constants.LICENSE_USING_DATABASE_LICENSE, usingDatabaseLicense,
                    "license", Map.of(
                        "id", license.getId(),
                        "filename", license.getFilename(),
                        "fileSize", license.getFileSize(),
                        "uploadedBy", license.getUploadedBy(),
                        "uploadedAt", license.getCreatedAt(),
                        "description", license.getDescription() != null ? license.getDescription() : ""
                    )
                );
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.ok(Map.of(
                    "hasLicense", false,
                    Constants.LICENSE_USING_DATABASE_LICENSE, false,
                    MESSAGE_KEY, "No license found in database. Using default license from resources."
                ));
            }
            
        } catch (LicenseException e) {
            log.error("License retrieval error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                SUCCESS_KEY, false,
                MESSAGE_KEY, "License retrieval failed: " + e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error retrieving current license", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                SUCCESS_KEY, false,
                MESSAGE_KEY, "Failed to retrieve license information"
            ));
        }
    }
    
    /**
     * Delete current license (Admin only)
     */
    @DeleteMapping("/current")
    public ResponseEntity<?> deleteCurrentLicense() {
        try {
            if (!licenseService.hasActiveLicense()) {
                return ResponseEntity.badRequest().body(Map.of(
                    SUCCESS_KEY, false,
                    MESSAGE_KEY, "No active license found to delete"
                ));
            }
            
            licenseService.deleteActiveLicense();
            
            // Trigger license revalidation to fall back to resource license
            licenseManager.revalidateLicense();
            
            log.info("License deleted by admin: {}", CommonUtils.getEmailFromSession());
            return ResponseEntity.ok(Map.of(
                SUCCESS_KEY, true,
                MESSAGE_KEY, "License deleted successfully. System will now use default license from resources."
            ));
            
        } catch (LicenseException e) {
            log.error("License deletion error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                SUCCESS_KEY, false,
                MESSAGE_KEY, "License deletion failed: " + e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Error deleting license", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                SUCCESS_KEY, false,
                MESSAGE_KEY, "Failed to delete license"
            ));
        }
    }
    
    /**
     * Get license features (Admin only)
     */
    @GetMapping("/features")
    public ResponseEntity<?> getLicenseFeatures() {
        try {
            Map<String, String> features = licenseManager.getLicenseFeatures();
            Date expiryDate = licenseManager.getLicenseExpiryDate();
            boolean isValid = licenseManager.isLicenseValid();
            boolean usingDatabaseLicense = licenseManager.isUsingDatabaseLicense();
            
            Map<String, Object> response = new HashMap<>();
            response.put("isValid", isValid);
            response.put(Constants.LICENSE_USING_DATABASE_LICENSE, usingDatabaseLicense);
            response.put("expiryDate", expiryDate != null ? expiryDate : "");
            response.put("features", features);
            
            // Add system identifier mismatch information if applicable
            if (licenseManager.hasSystemIdentifierMismatch()) {
                response.put("systemIdentifierMismatch", true);
                response.put("licenseSystemIdentifier", licenseManager.getLicenseSystemIdentifier());
                response.put("currentSystemIdentifier", licenseManager.getCurrentSystemIdentifier());
                response.put("errorMessage", "This license does not belong to this system");
            } else {
                response.put("systemIdentifierMismatch", false);
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving license features", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                SUCCESS_KEY, false,
                MESSAGE_KEY, "Failed to retrieve license features"
            ));
        }
    }
    
    /**
     * Check if database license exists (Admin only)
     */
    @GetMapping("/exists")
    public ResponseEntity<Map<String, Boolean>> checkLicenseExists() {
        boolean hasLicense = licenseService.hasActiveLicense();
        return ResponseEntity.ok(Map.of("exists", hasLicense));
    }
    
    /**
     * Get system information for node-locked licensing (Admin only)
     */
    @GetMapping("/system-info")
    public ResponseEntity<?> getSystemInfo() {
        try {
            Map<String, String> systemInfo = systemInfoService.getSystemInfo();
            String systemIdentifier = systemInfoService.getSystemIdentifier();
            
            Map<String, Object> response = Map.of(
                "systemIdentifier", systemIdentifier,
                "systemInfo", systemInfo
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving system information", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                SUCCESS_KEY, false,
                MESSAGE_KEY, "Failed to retrieve system information"
            ));
        }
    }
    
    /**
     * Clear cached system identifier and regenerate (Admin only)
     * Useful for testing or when system configuration changes
     */
    @PostMapping("/system-info/refresh")
    public ResponseEntity<?> refreshSystemIdentifier() {
        try {
            String oldIdentifier = systemInfoService.getSystemIdentifier();
            systemInfoService.clearCachedIdentifier();
            String newIdentifier = systemInfoService.getSystemIdentifier();
            
            Map<String, Object> response = Map.of(
                SUCCESS_KEY, true,
                MESSAGE_KEY, "System identifier refreshed successfully",
                "oldIdentifier", oldIdentifier,
                "newIdentifier", newIdentifier,
                "changed", !oldIdentifier.equals(newIdentifier)
            );
            
            log.info("System identifier refreshed by admin: {} -> {}", oldIdentifier, newIdentifier);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error refreshing system identifier", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                SUCCESS_KEY, false,
                MESSAGE_KEY, "Failed to refresh system identifier"
            ));
        }
    }
    
    /**
     * Get license statistics for monitoring (Admin only)
     */
    @GetMapping("/statistics")
    public ResponseEntity<?> getLicenseStatistics() {
        try {
            Map<String, Object> statistics = licenseService.getLicenseStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("Error retrieving license statistics", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                SUCCESS_KEY, false,
                MESSAGE_KEY, "Failed to retrieve license statistics"
            ));
        }
    }
} 