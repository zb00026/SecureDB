package com.verlake.dam.controller;

import com.verlake.dam.configuration.LicenseManager;
import com.verlake.dam.entity.dto.LicenseStatusDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@RestController
@RequestMapping("/api/license")
public class LicenseController {

    private final LicenseManager licenseManager;

    @Autowired
    public LicenseController(LicenseManager licenseManager) {
        this.licenseManager = licenseManager;
    }

    /**
     * Get the current license status including expiry warning information
     * This endpoint is accessible to all authenticated users
     */
    @GetMapping("/status")
    public ResponseEntity<LicenseStatusDTO> getLicenseStatus() {
        try {
            if (!licenseManager.isLicenseValid()) {
                return ResponseEntity.ok(LicenseStatusDTO.createInvalidStatus());
            }

            // For valid licenses, always include expiry information
            Date expiryDate = licenseManager.getLicenseExpiryDate();
            long daysUntilExpiry = licenseManager.getDaysUntilExpiry();
            
            if (licenseManager.isLicenseExpiringSoon()) {
                return ResponseEntity.ok(
                    LicenseStatusDTO.createExpiryWarning(expiryDate, daysUntilExpiry)
                );
            }

            // Create valid status with expiry information
            return ResponseEntity.ok(LicenseStatusDTO.createValidStatusWithExpiry(expiryDate, daysUntilExpiry));
        } catch (Exception e) {
            // In case of any error, return invalid status
            return ResponseEntity.ok(LicenseStatusDTO.createInvalidStatus());
        }
    }
} 