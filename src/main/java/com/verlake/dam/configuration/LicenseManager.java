package com.verlake.dam.configuration;

import javax0.license3j.License;
import javax0.license3j.io.LicenseReader;
import com.verlake.dam.utils.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class LicenseManager {
    private static final Logger logger = LoggerFactory.getLogger(LicenseManager.class);
    private Map<String, String> licenseFeatures;
    private boolean isValid;

    @PostConstruct
    public void init() {
        try {
            validateLicense();
        } catch (IOException e) {
            logger.error("Failed to validate license", e);
            isValid = false;
        }
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.DAYS)
    public void scheduledLicenseCheck() {
        logger.info("Performing scheduled license validation");
        try {
            validateLicense();
        } catch (IOException e) {
            logger.error("Scheduled license validation failed", e);
            isValid = false;
        }
    }

    private void validateLicense() throws IOException {
        try (var inputStream = getClass().getClassLoader().getResourceAsStream("DevLicense.bin");
             var reader = new LicenseReader(inputStream)) {
            
            License license = reader.read();
            isValid = license.isOK(Constants.LIC_PUBLIC_KEY);
            
            if (!isValid) {
                logger.error("Could not validate license file");
                return;
            }

            licenseFeatures = new HashMap<>();
            license.getFeatures().entrySet().forEach(entry -> {
                licenseFeatures.put(entry.getKey(), entry.getValue().toString());
            });
            
            var expiryDate = license.getFeatures().get("expiry").getDate();
            if (expiryDate.before(new java.util.Date())) {
                logger.error("License has expired on {} ", expiryDate);
                isValid = false;
                return;
            }
            logger.debug("License features: {}", licenseFeatures);
            logger.info("License validated successfully");
        }
    }

    public boolean isLicenseValid() {
        return isValid;
    }

    public Map<String, String> getLicenseFeatures() {
        return new HashMap<>(licenseFeatures);
    }
} 