package com.verlake.dam.configuration;

import javax0.license3j.License;
import javax0.license3j.io.LicenseReader;
import com.verlake.dam.exception.LicenseException;
import com.verlake.dam.service.LicenseService;
import com.verlake.dam.service.SystemInfoService;
import com.verlake.dam.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class LicenseManager {
    private static final Logger logger = LoggerFactory.getLogger(LicenseManager.class);
    
    // Configuration constants
    @Value("${license.expiry.warning.days:10}")
    private int expiryWarningDays;
    
    @Value("${license.resource.filename:DevLicense.bin}")
    private String resourceLicenseFilename;
    
    private static final String SYSTEM_IDENTIFIER_FEATURE = "SystemIdentifier";
    private static final String EXPIRY_FEATURE = "expiry";
    
    // Thread-safe state management
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile Map<String, String> licenseFeatures;
    private volatile boolean isValid;
    private volatile Date expiryDate;
    private volatile boolean usingDatabaseLicense;
    private volatile boolean systemIdentifierMismatch;
    private volatile String licenseSystemIdentifier;
    private volatile String currentSystemIdentifier;
    private volatile long tenDaysInMillis;
    
    @Autowired
    private LicenseService licenseService;
    
    @Autowired
    private SystemInfoService systemInfoService;

    @PostConstruct
    public void init() {
        // Calculate warning period in milliseconds
        tenDaysInMillis = expiryWarningDays * 24 * 60 * 60 * 1000L;
        
        try {
            validateLicense();
        } catch (IOException e) {
            logger.error("Failed to validate license during initialization", e);
            resetToInvalidState();
        }
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.DAYS)
    public void scheduledLicenseCheck() {
        logger.info("Performing scheduled license validation");
        try {
            validateLicense();
        } catch (IOException e) {
            logger.error("Scheduled license validation failed", e);
            resetToInvalidState();
        }
    }

    private void validateLicense() throws IOException {
        lock.writeLock().lock();
        try {
            License license = null;
            
            // Initialize state
            resetValidationState();
            
            // First try to get license from database
            license = tryLoadDatabaseLicense();
            
            // If no database license, fall back to resource file
            if (license == null) {
                license = tryLoadResourceLicense();
            }
            
            // Validate the license
            if (license == null) {
                logger.error("No license available for validation");
                resetToInvalidState();
                return;
            }
            
            // Perform license validation
            performLicenseValidation(license);
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private void resetValidationState() {
        licenseFeatures = new HashMap<>();
        systemIdentifierMismatch = false;
        licenseSystemIdentifier = null;
        currentSystemIdentifier = null;
    }
    
    private void resetToInvalidState() {
        lock.writeLock().lock();
        try {
            isValid = false;
            expiryDate = null;
            licenseFeatures = new HashMap<>();
            systemIdentifierMismatch = false;
            licenseSystemIdentifier = null;
            currentSystemIdentifier = null;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private License tryLoadDatabaseLicense() {
        try {
            Optional<byte[]> dbLicenseFile = licenseService.getActiveLicenseFile();
            if (dbLicenseFile.isPresent()) {
                return readLicenseFromBytes(dbLicenseFile.get());
            } else {
                logger.info("No license found in database, falling back to resource file");
                return null;
            }
        } catch (Exception e) {
            logger.warn("Failed to access database for license lookup, falling back to resource file: {}", e.getMessage());
            return null;
        }
    }
    
    private License readLicenseFromBytes(byte[] licenseBytes) {
        try (var inputStream = new ByteArrayInputStream(licenseBytes);
             var reader = new LicenseReader(inputStream)) {
            License license = reader.read();
            usingDatabaseLicense = true;
            logger.info("Successfully loaded license from database");
            return license;
        } catch (IOException e) {
            logger.error("Database license exists but is invalid or corrupted: {}", e.getMessage());
            usingDatabaseLicense = true;
            throw new LicenseException("Invalid database license", e);
        }
    }
    
    private License tryLoadResourceLicense() {
        try (var inputStream = getClass().getClassLoader().getResourceAsStream(resourceLicenseFilename);
             var reader = new LicenseReader(inputStream)) {
            
            if (inputStream == null) {
                logger.error("{} not found in resources", resourceLicenseFilename);
                return null;
            }
            
            License license = reader.read();
            usingDatabaseLicense = false;
            logger.info("Successfully loaded default license from resources ({})", resourceLicenseFilename);
            return license;
        } catch (IOException e) {
            logger.error("Failed to read resource license file: {}", e.getMessage());
            return null;
        }
    }
    
    private void performLicenseValidation(License license) {
        // Validate signature
        isValid = license.isOK(Constants.LIC_PUBLIC_KEY);
        
        if (!isValid) {
            logger.error("License validation failed - invalid signature or corrupted license (Source: {})", 
                usingDatabaseLicense ? Constants.LICENSE_SOURCE_DATABASE : Constants.LICENSE_SOURCE_RESOURCES);
            resetToInvalidState();
            return;
        }

        // Extract features
        extractLicenseFeatures(license);
        
        // Validate system identifier for database licenses only
        if (usingDatabaseLicense && !validateSystemIdentifier()) {
            resetToInvalidState();
            return;
        }
        
        // Validate expiry
        if (!validateExpiry(license)) {
            resetToInvalidState();
            return;
        }
        
        // Check if license is expiring soon
        if (isLicenseExpiringSoon()) {
            logger.warn("License is expiring soon on {} (Source: {})", expiryDate, 
                usingDatabaseLicense ? Constants.LICENSE_SOURCE_DATABASE : Constants.LICENSE_SOURCE_RESOURCES);
        }
        
        logger.debug("License features: {}", licenseFeatures);
        logger.info("License validated successfully (Source: {}, Expiry: {}, Days until expiry: {})", 
            usingDatabaseLicense ? Constants.LICENSE_SOURCE_DATABASE : Constants.LICENSE_SOURCE_RESOURCES, expiryDate, getDaysUntilExpiry());
    }
    
    private void extractLicenseFeatures(License license) {
        licenseFeatures = new HashMap<>();
        license.getFeatures().entrySet().forEach(entry -> {
            licenseFeatures.put(entry.getKey(), entry.getValue().toString());
        });
    }
    
    private boolean validateSystemIdentifier() {
        licenseSystemIdentifier = licenseFeatures.get(SYSTEM_IDENTIFIER_FEATURE);
        if (licenseSystemIdentifier != null) {
            // Extract the actual value from the feature string (format: "SystemIdentifier=value")
            if (licenseSystemIdentifier.startsWith(SYSTEM_IDENTIFIER_FEATURE + "=")) {
                licenseSystemIdentifier = licenseSystemIdentifier.substring((SYSTEM_IDENTIFIER_FEATURE + "=").length());
            }
            
            currentSystemIdentifier = systemInfoService.getSystemIdentifier();
            if (!licenseSystemIdentifier.equals(currentSystemIdentifier)) {
                logger.error("This license does not belong to this system. License SystemIdentifier: {}, Current System: {}", 
                    licenseSystemIdentifier, currentSystemIdentifier);
                systemIdentifierMismatch = true;
                return false;
            }
            logger.info("System identifier validation passed for database license");
        } else {
            logger.warn("Database license does not contain SystemIdentifier feature - skipping system validation");
        }
        return true;
    }
    
    private boolean validateExpiry(License license) {
        try {
            expiryDate = license.getFeatures().get(EXPIRY_FEATURE).getDate();
            logger.info("License expiry date extracted: {}", expiryDate);
            
            if (expiryDate.before(new Date())) {
                logger.error("License has expired on {} (Source: {})", expiryDate, 
                    usingDatabaseLicense ? Constants.LICENSE_SOURCE_DATABASE : Constants.LICENSE_SOURCE_RESOURCES);
                return false;
            }
            return true;
        } catch (NullPointerException e) {
            logger.error("Failed to extract expiry date - expiry feature not found in license", e);
            return false;
        } catch (IllegalArgumentException e) {
            logger.error("Failed to validate expiry date - invalid date format", e);
            return false;
        }
    }

    public boolean isLicenseValid() {
        lock.readLock().lock();
        try {
            return isValid;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, String> getLicenseFeatures() {
        lock.readLock().lock();
        try {
            if (licenseFeatures == null) {
                return new HashMap<>();
            }
            return new HashMap<>(licenseFeatures);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Checks if the license is expiring within the configured warning period
     * @return true if license expires within warning period, false otherwise
     */
    public boolean isLicenseExpiringSoon() {
        lock.readLock().lock();
        try {
            if (expiryDate == null || !isValid) {
                return false;
            }
            
            Date currentDate = new Date();
            long timeDifference = expiryDate.getTime() - currentDate.getTime();
            
            return timeDifference > 0 && timeDifference <= tenDaysInMillis;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets the license expiry date
     * @return the expiry date of the license, or null if not available
     */
    public Date getLicenseExpiryDate() {
        lock.readLock().lock();
        try {
            return expiryDate != null ? new Date(expiryDate.getTime()) : null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Gets the number of days until license expires
     * @return number of days until expiry, or -1 if expired or invalid
     */
    public long getDaysUntilExpiry() {
        lock.readLock().lock();
        try {
            if (expiryDate == null) {
                return -1;
            }
            
            Date currentDate = new Date();
            long timeDifference = expiryDate.getTime() - currentDate.getTime();
            
            if (timeDifference <= 0) {
                return 0; // Already expired
            }
            
            return timeDifference / (24 * 60 * 60 * 1000L); // Convert to days
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Check if currently using database license
     * @return true if using database license, false if using resource file
     */
    public boolean isUsingDatabaseLicense() {
        lock.readLock().lock();
        try {
            return usingDatabaseLicense;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Force license revalidation (useful after license upload/deletion)
     */
    public void revalidateLicense() {
        try {
            validateLicense();
            logger.info("License revalidation completed");
        } catch (IOException e) {
            logger.error("License revalidation failed", e);
            resetToInvalidState();
        }
    }

    /**
     * Check if system identifier validation failed
     * @return true if license has system identifier mismatch
     */
    public boolean hasSystemIdentifierMismatch() {
        lock.readLock().lock();
        try {
            return systemIdentifierMismatch;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get the system identifier from the license
     * @return system identifier from license, or null if not available
     */
    public String getLicenseSystemIdentifier() {
        lock.readLock().lock();
        try {
            return licenseSystemIdentifier;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Get the current system identifier
     * @return current system identifier, or null if not available
     */
    public String getCurrentSystemIdentifier() {
        lock.readLock().lock();
        try {
            return currentSystemIdentifier;
        } finally {
            lock.readLock().unlock();
        }
    }
} 