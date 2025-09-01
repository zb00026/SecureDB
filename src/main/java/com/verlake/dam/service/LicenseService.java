package com.verlake.dam.service;

import com.verlake.dam.entity.License;
import com.verlake.dam.exception.LicenseException;
import com.verlake.dam.repository.LicenseRepository;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class LicenseService {
    
    // Configuration constants
    @Value("${license.upload.max-size-mb:10}")
    private int maxFileSizeMB;
    
    @Value("${license.upload.allowed-extensions:.bin,.license}")
    private String allowedExtensions;
    
    private static final List<String> DEFAULT_ALLOWED_EXTENSIONS = Arrays.asList(".bin", ".license");
    
    private final LicenseRepository licenseRepository;
    
    @Autowired
    public LicenseService(LicenseRepository licenseRepository) {
        this.licenseRepository = licenseRepository;
    }
    
    /**
     * Upload a new license file, replacing any existing active license
     */
    @Transactional
    public License uploadLicense(MultipartFile file, String description) throws IOException {
        validateLicenseFile(file);
        
        // Get current user email
        String uploadedBy = CommonUtils.getEmailFromSession();
        if (uploadedBy == null || uploadedBy.trim().isEmpty()) {
            throw new IllegalStateException("Unable to determine current user for license upload");
        }
        
        try {
            // Deactivate all existing licenses
            licenseRepository.deactivateAllLicenses();
            
            // Create new license record
            License license = License.builder()
                    .licenseFile(file.getBytes())
                    .filename(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .uploadedBy(uploadedBy)
                    .description(description != null ? description.trim() : null)
                    .isActive(true)
                    .build();
            
            License savedLicense = licenseRepository.save(license);
            log.info("New license uploaded by user: {} with filename: {} (size: {} bytes)", 
                uploadedBy, file.getOriginalFilename(), file.getSize());
            
            return savedLicense;
        } catch (IOException e) {
            log.error("Failed to read license file during upload", e);
            throw new IOException("Failed to process license file: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            log.error("Failed to save license to database", e);
            throw new LicenseException("Failed to save license: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get the currently active license
     */
    public Optional<License> getActiveLicense() {
        try {
            return licenseRepository.findByIsActiveTrue();
        } catch (LicenseException e) {
            log.error("Failed to retrieve active license from database", e);
            return Optional.empty();
        }
    }
    
    /**
     * Get active license file bytes
     */
    public Optional<byte[]> getActiveLicenseFile() {
        try {
            return getActiveLicense().map(License::getLicenseFile);
        } catch (LicenseException e) {
            log.error("Failed to retrieve active license file bytes", e);
            return Optional.empty();
        }
    }
    
    /**
     * Check if a database license exists
     */
    public boolean hasActiveLicense() {
        try {
            return licenseRepository.existsByIsActiveTrue();
        } catch (LicenseException e) {
            log.error("Failed to check for active license existence", e);
            return false;
        }
    }
    
    /**
     * Delete the current active license (admin only)
     */
    @Transactional
    public void deleteActiveLicense() {
        try {
            Optional<License> activeLicense = getActiveLicense();
            if (activeLicense.isPresent()) {
                licenseRepository.delete(activeLicense.get());
                log.info("Active license deleted by user: {} (filename: {})", 
                    CommonUtils.getEmailFromSession(), activeLicense.get().getFilename());
            } else {
                log.warn("Attempted to delete active license but none found");
            }
        } catch (RuntimeException e) {
            log.error("Failed to delete active license", e);
            throw new LicenseException("Failed to delete license: " + e.getMessage(), e);
        }
    }
    
    /**
     * Validate uploaded license file
     */
    private void validateLicenseFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("License file cannot be empty");
        }
        
        // Check file size
        long maxFileSize = maxFileSizeMB * 1024L * 1024L; // Convert MB to bytes
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException(
                String.format("License file size cannot exceed %dMB (current: %.2fMB)", 
                    maxFileSizeMB, file.getSize() / (1024.0 * 1024.0)));
        }
        
        // Check file extension
        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("License file must have a valid filename");
        }
        
        // Get allowed extensions from configuration or use defaults
        List<String> extensions = getAllowedExtensions();
        boolean validExtension = extensions.stream()
            .anyMatch(ext -> filename.toLowerCase().endsWith(ext.toLowerCase()));
            
        if (!validExtension) {
            throw new IllegalArgumentException(
                String.format("License file must have one of the following extensions: %s", 
                    String.join(", ", extensions)));
        }
        
        // Additional validation: check if file content is not empty
        if (file.getSize() == 0) {
            throw new IllegalArgumentException("License file cannot be empty");
        }
        
        // Basic content validation - ensure it's not a text file
        try {
            String contentType = file.getContentType();
            if (contentType != null && contentType.startsWith("text/")) {
                throw new IllegalArgumentException("License file appears to be a text file, expected binary license file");
            }
        } catch (RuntimeException e) {
            log.debug("Could not determine content type for file validation: {}", e.getMessage());
        }
    }
    
    /**
     * Get allowed file extensions from configuration
     */
    private List<String> getAllowedExtensions() {
        if (allowedExtensions != null && !allowedExtensions.trim().isEmpty()) {
            return Arrays.stream(allowedExtensions.split(","))
                .map(String::trim)
                .filter(ext -> !ext.isEmpty())
                .toList();
        }
        return DEFAULT_ALLOWED_EXTENSIONS;
    }
    
    /**
     * Get license statistics for monitoring
     */
    public Map<String, Object> getLicenseStatistics() {
        try {
            long totalLicenses = licenseRepository.count();
            long activeLicenses = licenseRepository.countByIsActiveTrue();
            
            return Map.of(
                "totalLicenses", totalLicenses,
                "activeLicenses", activeLicenses,
                "hasActiveLicense", activeLicenses > 0
            );
        } catch (LicenseException e) {
            log.error("Failed to retrieve license statistics", e);
            return Map.of(
                "totalLicenses", 0L,
                "activeLicenses", 0L,
                "hasActiveLicense", false,
                Constants.ERROR_FIELD_ERROR, "Failed to retrieve statistics"
            );
        }
    }
} 