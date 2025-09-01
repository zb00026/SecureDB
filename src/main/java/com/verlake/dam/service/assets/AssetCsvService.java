package com.verlake.dam.service.assets;

import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.AssetType;
import com.verlake.dam.enums.DatabaseType;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.repository.assets.AssetRepository;
import com.verlake.dam.repository.assets.AssetCredentialsRepository;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.Constants;
import com.verlake.dam.utils.I18nUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AssetCsvService {
    
    private final AssetRepository assetRepository;
    private final AssetCredentialsRepository assetCredentialsRepository;
    private final UserService userService;

    public AssetCsvService(AssetRepository assetRepository, 
                          AssetCredentialsRepository assetCredentialsRepository,
                          UserService userService) {
        this.assetRepository = assetRepository;
        this.assetCredentialsRepository = assetCredentialsRepository;
        this.userService = userService;
    }

    /**
     * Parses CSV file and returns list of asset data maps
     */
    public List<Map<String, String>> parseCsvFile(MultipartFile file) throws Exception {
        List<Map<String, String>> assetDataList = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            
            String line;
            String[] headers = null;
            int lineNumber = 0;
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                // Skip empty lines and comment lines
                if (line.trim().isEmpty() || line.trim().startsWith(Constants.CSV_COMMENT_PREFIX)) {
                    continue;
                }
                
                String[] values = parseCsvLine(line);
                
                if (headers == null) {
                    // First non-comment line should be headers
                    headers = values;
                    log.debug("CSV headers: {}", Arrays.toString(headers));
                } else {
                    // Process data line
                    Map<String, String> assetData = processDataLine(values, lineNumber);
                    assetDataList.add(assetData);
                }
            }
        }
        
        return assetDataList;
    }
    
    /**
     * Processes a single data line from CSV and creates asset data map
     */
    private Map<String, String> processDataLine(String[] values, int lineNumber) {
        if (values.length < 6) {
            throw new IllegalArgumentException(
                String.format("Line %d has %d columns but expected 6", 
                lineNumber, values.length));
        }
        
        Map<String, String> assetData = new HashMap<>();
        assetData.put(Constants.ASSET_FIELD_LINE_NUMBER, String.valueOf(lineNumber));
        assetData.put(Constants.ASSET_FIELD_NAME, values[0].trim());
        assetData.put(Constants.ASSET_FIELD_DESCRIPTION, values[1].trim());
        assetData.put(Constants.ASSET_FIELD_TYPE, values[2].trim());
        assetData.put(Constants.ASSET_FIELD_DATABASE_TYPE, values[3].trim());
        assetData.put(Constants.ASSET_FIELD_HOST_ADDRESS, values[4].trim());
        assetData.put(Constants.ASSET_FIELD_PORT_NUMBER, values[5].trim());
        assetData.put(Constants.ASSET_FIELD_DATABASE_NAME, values.length > 6 ? values[6].trim() : "");
        assetData.put(Constants.ASSET_FIELD_OWNER_EMAIL, values.length > 7 ? values[7].trim() : "");
        
        return assetData;
    }
    
    /**
     * Parses a single CSV line handling quoted fields
     */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder field = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(field.toString());
                field = new StringBuilder();
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        
        return fields.toArray(new String[0]);
    }
    
    /**
     * Validates bulk asset data and collects errors
     */
    public void validateBulkAssets(List<Map<String, String>> assetDataList, List<String> errors) {
        Set<String> assetNames = new HashSet<>();
        List<String> nonExistentOwners = new ArrayList<>();
        
        for (Map<String, String> assetData : assetDataList) {
            String lineNumber = assetData.get(Constants.ASSET_FIELD_LINE_NUMBER);
            String prefix = "Line " + lineNumber + ": ";
            
            validateRequiredFields(assetData, prefix, errors);
            validateAssetNameField(assetData, prefix, assetNames, errors);
            validateAssetTypeField(assetData, prefix, errors);
            validateDatabaseTypeField(assetData, prefix, errors);
            validateOwnerEmailField(assetData, prefix, nonExistentOwners, errors);
        }
        
        // Add summary message for non-existent owners
        if (!nonExistentOwners.isEmpty()) {
            errors.add("Warning: The following asset owners do not exist and will be skipped: " + 
                      String.join(", ", nonExistentOwners));
        }
    }

    /**
     * Validates required fields
     */
    private void validateRequiredFields(Map<String, String> assetData, String prefix, List<String> errors) {
        validateRequiredField(assetData, Constants.ASSET_FIELD_NAME, prefix, errors);
        validateRequiredField(assetData, Constants.ASSET_FIELD_TYPE, prefix, errors);
        validateRequiredField(assetData, Constants.ASSET_FIELD_DATABASE_TYPE, prefix, errors);
        validateRequiredField(assetData, Constants.ASSET_FIELD_HOST_ADDRESS, prefix, errors);
    }

    /**
     * Validates a single required field
     */
    private void validateRequiredField(Map<String, String> assetData, String fieldName, String prefix, List<String> errors) {
        String fieldValue = assetData.get(fieldName);
        if (fieldValue == null || fieldValue.trim().isEmpty()) {
            errors.add(prefix + String.format(Constants.ERROR_FIELD_REQUIRED, fieldName));
        }
    }

    /**
     * Validates asset name field with duplicates and database existence checks
     */
    private void validateAssetNameField(Map<String, String> assetData, String prefix, Set<String> assetNames, List<String> errors) {
        String assetName = assetData.get(Constants.ASSET_FIELD_NAME);
        
        if (assetName == null || assetName.trim().isEmpty()) {
            errors.add(prefix + String.format(Constants.ERROR_FIELD_REQUIRED, Constants.ASSET_FIELD_NAME));
            return;
        }
        
        assetName = assetName.trim();
        
        // Check for duplicates within CSV
        if (assetNames.contains(assetName)) {
            errors.add(prefix + String.format(Constants.ERROR_DUPLICATE_ASSET_NAME_CSV, assetName));
        } else {
            assetNames.add(assetName);
        }
        
        // Check if asset already exists in database
        if (assetRepository.existsByName(assetName)) {
            errors.add(prefix + String.format(Constants.ERROR_ASSET_NAME_ALREADY_EXISTS_SYSTEM, assetName));
        }
    }

    /**
     * Validates asset type field
     */
    private void validateAssetTypeField(Map<String, String> assetData, String prefix, List<String> errors) {
        String assetType = assetData.get(Constants.ASSET_FIELD_TYPE);
        
        if (assetType == null || assetType.trim().isEmpty()) {
            errors.add(prefix + String.format(Constants.ERROR_FIELD_REQUIRED, Constants.ASSET_FIELD_TYPE));
            return;
        }
        
        try {
            AssetType.valueOf(assetType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add(prefix + String.format(Constants.ERROR_INVALID_ASSET_TYPE, assetType));
        }
    }

    /**
     * Validates database type field
     */
    private void validateDatabaseTypeField(Map<String, String> assetData, String prefix, List<String> errors) {
        String databaseType = assetData.get(Constants.ASSET_FIELD_DATABASE_TYPE);
        
        if (databaseType == null || databaseType.trim().isEmpty()) {
            errors.add(prefix + String.format(Constants.ERROR_FIELD_REQUIRED, Constants.ASSET_FIELD_DATABASE_TYPE));
            return;
        }
        
        try {
            DatabaseType.valueOf(databaseType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            errors.add(prefix + String.format(Constants.ERROR_INVALID_DATABASE_TYPE, databaseType));
        }
    }

    /**
     * Validates owner email field
     */
    private void validateOwnerEmailField(Map<String, String> assetData, String prefix, List<String> nonExistentOwners, List<String> errors) {
        String ownerEmail = assetData.get(Constants.ASSET_FIELD_OWNER_EMAIL);
        
        if (ownerEmail != null && !ownerEmail.trim().isEmpty()) {
            ownerEmail = ownerEmail.trim().toLowerCase();
            
            // Validate email format
            if (!ownerEmail.matches(Constants.EMAIL_REGEX)) {
                errors.add(prefix + String.format(Constants.ERROR_INVALID_EMAIL_FORMAT, ownerEmail));
                return;
            }
            
            // Check if user exists and has ASSET_OWNER role
            User owner = userService.findByEmail(ownerEmail);
            if (owner == null) {
                nonExistentOwners.add(ownerEmail);
            } else {
                boolean hasAssetOwnerRole = owner.getRoles().stream()
                    .anyMatch(role -> Roles.ASSET_OWNER.getOriginalName().equals(role.getName()));
                if (!hasAssetOwnerRole) {
                    errors.add(prefix + String.format(Constants.ERROR_USER_NOT_ASSET_OWNER, ownerEmail));
                }
            }
        }
    }
    
    /**
     * Creates assets in bulk from parsed CSV data
     */
    @Transactional
    public List<Asset> createBulkAssets(List<Map<String, String>> assetDataList) {
        List<Asset> createdAssets = new ArrayList<>();
        
        for (Map<String, String> assetData : assetDataList) {
            try {
                Asset asset = new Asset();
                asset.setName(assetData.get(Constants.ASSET_FIELD_NAME).trim());
                asset.setDescription(assetData.get(Constants.ASSET_FIELD_DESCRIPTION).trim());
                asset.setType(AssetType.valueOf(assetData.get(Constants.ASSET_FIELD_TYPE).trim().toUpperCase()));
                asset.setDatabaseType(DatabaseType.valueOf(assetData.get(Constants.ASSET_FIELD_DATABASE_TYPE).trim().toUpperCase()));
                asset.setHostAddress(assetData.get(Constants.ASSET_FIELD_HOST_ADDRESS).trim());
                asset.setPortNumber(assetData.get(Constants.ASSET_FIELD_PORT_NUMBER).trim());
                asset.setDatabaseName(assetData.get(Constants.ASSET_FIELD_DATABASE_NAME).trim());
                asset.setDeleted(false);
                asset.setLocked(false);
                
                // Save asset
                Asset savedAsset = assetRepository.save(asset);
                createdAssets.add(savedAsset);
                
                log.debug("Created asset: {} ({})", savedAsset.getName(), savedAsset.getId());
                
                // Create asset owner credential if owner email is provided
                String ownerEmail = assetData.get(Constants.ASSET_FIELD_OWNER_EMAIL);
                if (ownerEmail != null && !ownerEmail.trim().isEmpty()) {
                    User owner = userService.findByEmail(ownerEmail.trim().toLowerCase());
                    if (owner != null) {
                        AssetCredential credential = AssetCredential.builder()
                            .asset(savedAsset)
                            .user(owner)
                            .username(owner.getEmail())
                            .password("") // Will be set by owner later
                            .userAccessType(Roles.ASSET_OWNER.getOriginalName())
                            .isDeleted(false)
                            .isTemporaryPassword(false)
                            .build();
                        
                        assetCredentialsRepository.save(credential);
                        log.debug("Created asset owner credential for: {} on asset: {}", ownerEmail, savedAsset.getName());
                    }
                }
                
            } catch (Exception e) {
                log.error("Failed to create asset from line {}: {}", assetData.get(Constants.ASSET_FIELD_LINE_NUMBER), e.getMessage());
                throw new IllegalArgumentException("Failed to create asset " + assetData.get(Constants.ASSET_FIELD_NAME) + ": " + e.getMessage(), e);
            }
        }
        
        return createdAssets;
    }

    /**
     * Generates sample CSV content for bulk asset upload
     */
    public String generateSampleCsvContent() {
        StringBuilder csvContent = new StringBuilder();
        
        // CSV Header
        csvContent.append(Constants.ASSET_CSV_HEADER + "\n");
        
        // Sample data
        csvContent.append("Production Database,Main production database for customer data,DATABASE,MYSQL,db.example.com,3306,customer_db,admin@example.com\n");
        csvContent.append("Analytics Database,Analytics and reporting database,DATABASE,POSTGRESQL,analytics.example.com,5432,analytics_db,jane.smith@example.com\n");
        csvContent.append("Test Database,Development and testing database,DATABASE,MYSQL,test.example.com,3306,test_db,jane@example.com\n");
        csvContent.append("Legacy System,Legacy SQL Server database,DATABASE,SQLSERVER,legacy.example.com,1433,legacy_db,bob.johnson@example.com\n");
        
        // Add comment lines explaining the format
        csvContent.append("# INSTRUCTIONS:\n");
        csvContent.append("# - " + Constants.ASSET_FIELD_NAME + ": Required, unique asset name\n");
        csvContent.append("# - " + Constants.ASSET_FIELD_DESCRIPTION + ": Optional, asset description\n");
        csvContent.append("# - " + Constants.ASSET_FIELD_TYPE + ": Required, asset type\n");
        csvContent.append("# - " + Constants.ASSET_FIELD_DATABASE_TYPE + ": Required, database type\n");
        csvContent.append("# - " + Constants.ASSET_FIELD_HOST_ADDRESS + ": Required, database host address\n");
        csvContent.append("# - " + Constants.ASSET_FIELD_PORT_NUMBER + ": Required, database port number\n");
        csvContent.append("# - " + Constants.ASSET_FIELD_DATABASE_NAME + ": Optional, database name\n");
        csvContent.append("# - " + Constants.ASSET_FIELD_OWNER_EMAIL + ": Optional, asset owner email (must have ASSET_OWNER role)\n");
        csvContent.append("#\n");
        csvContent.append("# AVAILABLE ASSET TYPES:\n");
        csvContent.append("# - " + AssetType.DATABASE + ": Database assets\n");
        csvContent.append("#\n");
        csvContent.append("# AVAILABLE DATABASE TYPES:\n");
        csvContent.append("# - " + DatabaseType.MYSQL + ": MySQL database\n");
        csvContent.append("# - " + DatabaseType.POSTGRESQL + ": PostgreSQL database\n");
        csvContent.append("# - " + DatabaseType.SQLSERVER + ": Microsoft SQL Server\n");
        csvContent.append("# - " + DatabaseType.ORACLE + ": Oracle database\n");
        csvContent.append("#\n");
        csvContent.append("# NOTES:\n");
        csvContent.append("# - Asset names must be unique\n");
        csvContent.append("# - Owner email is optional but must be a valid user with ASSET_OWNER role\n");
        csvContent.append("# - If owner email is not provided, no asset owner will be assigned\n");
        csvContent.append("# - Lines starting with " + Constants.CSV_COMMENT_PREFIX + " are ignored\n");
        csvContent.append("# - Remove sample data and add your assets\n");
        
        return csvContent.toString();
    }

    /**
     * Exports all assets to CSV format
     */
    public String exportAssetsToCsv() {
        List<Asset> assets = assetRepository.findAll();
        
        StringBuilder csvContent = new StringBuilder();
        
        // CSV Header
        csvContent.append(Constants.ASSET_CSV_HEADER + "\n");
        
        // Export each asset
        for (Asset asset : assets) {
            // Get asset owner email if exists
            String ownerEmail = "";
            List<AssetCredential> ownerCredentials = assetCredentialsRepository
                .findByAssetAndUserAccessType(asset, Roles.ASSET_OWNER.getOriginalName());
            if (!ownerCredentials.isEmpty()) {
                ownerEmail = ownerCredentials.get(0).getUser().getEmail();
            }
            
            csvContent.append(String.format("\"%s\",\"%s\",%s,%s,\"%s\",\"%s\",\"%s\",\"%s\"\n",
                asset.getName(),
                asset.getDescription() != null ? asset.getDescription() : "",
                asset.getType(),
                asset.getDatabaseType(),
                asset.getHostAddress(),
                asset.getPortNumber(),
                asset.getDatabaseName() != null ? asset.getDatabaseName() : "",
                ownerEmail
            ));
        }
        
        return csvContent.toString();
    }

    /**
     * Processes bulk asset upload and returns structured response
     */
    @Transactional
    public Map<String, Object> processBulkAssetUploadWithResponse(MultipartFile file) {
        log.info("Starting bulk asset upload. File: {}, Size: {} bytes", file.getOriginalFilename(), file.getSize());
        
        // Validate file
        if (file.isEmpty()) {
            throw new IllegalArgumentException(Constants.getMessage(Constants.ERROR_UPLOADED_FILE_EMPTY));
        }
        
        if (!file.getOriginalFilename().toLowerCase().endsWith(Constants.CSV_EXTENSION)) {
            throw new IllegalArgumentException(Constants.getMessage(Constants.ERROR_FILE_MUST_BE_CSV));
        }
        
        List<Map<String, String>> assetDataList = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<Asset> createdAssets = new ArrayList<>();
        
        try {
            // Parse CSV file
            assetDataList = parseCsvFile(file);
            log.info("Parsed {} assets from CSV file", assetDataList.size());
            
            if (assetDataList.isEmpty()) {
                throw new IllegalArgumentException(Constants.getMessage(Constants.ERROR_NO_VALID_ASSET_DATA));
            }
            
            // Validate all assets first
            validateBulkAssets(assetDataList, errors);
            
            if (!errors.isEmpty()) {
                log.error("Validation errors found: {}", errors);
                Map<String, Object> response = new HashMap<>();
                response.put(Constants.RESPONSE_SUCCESS, false);
                response.put(Constants.RESPONSE_MESSAGE, Constants.getMessage(Constants.ERROR_VALIDATION_ERRORS_FOUND));
                response.put(Constants.RESPONSE_ERRORS, errors);
                response.put(Constants.RESPONSE_TOTAL_ASSETS, assetDataList.size());
                response.put(Constants.RESPONSE_FAILED_ASSETS, assetDataList.size());
                response.put(Constants.RESPONSE_SUCCESSFUL_ASSETS, 0);
                return response;
            }
            
            // Create all assets
            createdAssets = createBulkAssets(assetDataList);
            log.info("Successfully created {} assets", createdAssets.size());
            
            // Build success response
            Map<String, Object> response = new HashMap<>();
            response.put(Constants.RESPONSE_SUCCESS, true);
            response.put(Constants.RESPONSE_MESSAGE, String.format(Constants.SUCCESS_CREATED_ASSETS, createdAssets.size()));
            response.put(Constants.RESPONSE_TOTAL_ASSETS, createdAssets.size());
            response.put(Constants.RESPONSE_SUCCESSFUL_ASSETS, createdAssets.size());
            response.put(Constants.RESPONSE_FAILED_ASSETS, 0);
            response.put(Constants.RESPONSE_ASSETS, createdAssets.stream().map(asset -> {
                Map<String, Object> assetInfo = new HashMap<>();
                assetInfo.put(Constants.ASSET_FIELD_ID, asset.getId());
                assetInfo.put(Constants.ASSET_FIELD_NAME, asset.getName());
                assetInfo.put(Constants.ASSET_FIELD_DESCRIPTION, asset.getDescription());
                assetInfo.put(Constants.ASSET_FIELD_TYPE, asset.getType());
                assetInfo.put(Constants.ASSET_FIELD_DATABASE_TYPE, asset.getDatabaseType());
                return assetInfo;
            }).toList());
            
            return response;
            
        } catch (Exception e) {
            log.error("Bulk asset upload failed: {}", e.getMessage(), e);
            
            // Clean up any created assets if something failed after creation
            if (!createdAssets.isEmpty()) {
                log.info("Cleaning up {} created assets due to failure", createdAssets.size());
                try {
                    for (Asset asset : createdAssets) {
                        assetRepository.delete(asset);
                    }
                } catch (Exception cleanupError) {
                    log.error("Failed to cleanup assets: {}", cleanupError.getMessage());
                }
            }
            
            // Build error response
            Map<String, Object> response = new HashMap<>();
            response.put(Constants.RESPONSE_SUCCESS, false);
            response.put(Constants.RESPONSE_MESSAGE, String.format(Constants.ERROR_BULK_UPLOAD_FAILED, e.getMessage()));
            response.put(Constants.RESPONSE_TOTAL_ASSETS, assetDataList.size());
            response.put(Constants.RESPONSE_SUCCESSFUL_ASSETS, 0);
            response.put(Constants.RESPONSE_FAILED_ASSETS, assetDataList.size());
            response.put(Constants.RESPONSE_ERRORS, Arrays.asList(e.getMessage()));
            
            return response;
        }
    }
} 