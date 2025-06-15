package com.verlake.dam.service.users;

import com.verlake.dam.entity.Role;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.AuthProvider;
import com.verlake.dam.repository.RoleRepository;
import com.verlake.dam.repository.UserRepository;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.email.EmailService;
import com.verlake.dam.exception.EmailSendingException;
import com.verlake.dam.utils.Constants;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@Slf4j
public class UserCsvService {
    
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserService userService;

    @Autowired(required = false)
    private KeycloakService keycloakService;

    @Autowired
    private EmailService emailService;

    @Value("${auth.provider}")
    private String authProvider;

    public UserCsvService(UserRepository userRepository, RoleRepository roleRepository, UserService userService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userService = userService;
    }

    /**
     * Parses CSV file and returns list of user data maps
     */
    public List<Map<String, String>> parseCsvFile(MultipartFile file) throws Exception {
        List<Map<String, String>> userDataList = new ArrayList<>();
        
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
                    if (values.length != headers.length) {
                        throw new IllegalArgumentException(
                            String.format("Line %d has %d columns but expected %d", 
                            lineNumber, values.length, headers.length));
                    }
                    
                    Map<String, String> userData = new HashMap<>();
                    for (int i = 0; i < headers.length; i++) {
                        userData.put(headers[i].trim(), values[i].trim());
                    }
                    userData.put(Constants.USER_FIELD_LINE_NUMBER, String.valueOf(lineNumber));
                    userDataList.add(userData);
                }
            }
        }
        
        return userDataList;
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
     * Validates bulk user data and collects errors
     */
    public void validateBulkUsers(List<Map<String, String>> userDataList, List<String> errors) {
        Set<String> emails = new HashSet<>();
        
        for (Map<String, String> userData : userDataList) {
            String lineNumber = userData.get(Constants.USER_FIELD_LINE_NUMBER);
            String prefix = "Line " + lineNumber + ": ";
            
            validateRequiredFields(userData, prefix, errors);
            validateEmailField(userData, prefix, emails, errors);
            validateRoleField(userData, prefix, errors);
        }
    }

    /**
     * Validates required fields (firstName, lastName)
     */
    private void validateRequiredFields(Map<String, String> userData, String prefix, List<String> errors) {
        validateRequiredField(userData, Constants.USER_FIELD_FIRST_NAME, prefix, errors);
        validateRequiredField(userData, Constants.USER_FIELD_LAST_NAME, prefix, errors);
    }

    /**
     * Validates a single required field
     */
    private void validateRequiredField(Map<String, String> userData, String fieldName, String prefix, List<String> errors) {
        String fieldValue = userData.get(fieldName);
        if (fieldValue == null || fieldValue.trim().isEmpty()) {
            errors.add(prefix + String.format(Constants.ERROR_FIELD_REQUIRED, fieldName));
        }
    }

    /**
     * Validates email field with format, duplicates, and database existence checks
     */
    private void validateEmailField(Map<String, String> userData, String prefix, Set<String> emails, List<String> errors) {
        String email = userData.get(Constants.USER_FIELD_EMAIL);
        
        if (email == null || email.trim().isEmpty()) {
            errors.add(prefix + String.format(Constants.ERROR_FIELD_REQUIRED, Constants.USER_FIELD_EMAIL));
            return;
        }
        
        email = email.trim().toLowerCase();
        
        validateEmailFormat(email, prefix, errors);
        validateEmailDuplication(email, prefix, emails, errors);
        validateEmailExistence(email, prefix, errors);
    }

    /**
     * Validates email format
     */
    private void validateEmailFormat(String email, String prefix, List<String> errors) {
        if (!email.matches(Constants.EMAIL_REGEX)) {
            errors.add(prefix + String.format(Constants.ERROR_INVALID_EMAIL_FORMAT, email));
        }
    }

    /**
     * Validates email duplication within CSV
     */
    private void validateEmailDuplication(String email, String prefix, Set<String> emails, List<String> errors) {
        if (emails.contains(email)) {
            errors.add(prefix + String.format(Constants.ERROR_DUPLICATE_EMAIL_CSV, email));
        } else {
            emails.add(email);
        }
    }

    /**
     * Validates email existence in database
     */
    private void validateEmailExistence(String email, String prefix, List<String> errors) {
        if (userRepository.existsByEmail(email)) {
            errors.add(prefix + String.format(Constants.ERROR_EMAIL_ALREADY_EXISTS_SYSTEM, email));
        }
    }

    /**
     * Validates role field and individual role names
     */
    private void validateRoleField(Map<String, String> userData, String prefix, List<String> errors) {
        String roleName = userData.get(Constants.USER_FIELD_ROLE_NAME);
        
        if (roleName == null || roleName.trim().isEmpty()) {
            errors.add(prefix + String.format(Constants.ERROR_FIELD_REQUIRED, Constants.USER_FIELD_ROLE_NAME));
            return;
        }
        
        String[] roleNameArray = roleName.split(",");
        for (String name : roleNameArray) {
            validateIndividualRole(name.trim(), prefix, errors);
        }
    }

    /**
     * Validates individual role name exists in database
     */
    private void validateIndividualRole(String roleName, String prefix, List<String> errors) {
        if (!roleRepository.findByName(roleName).isPresent()) {
            errors.add(prefix + String.format(Constants.ERROR_INVALID_ROLE_NAME, roleName));
        }
    }
    
    /**
     * Creates users in bulk from parsed CSV data
     */
    @Transactional
    public List<User> createBulkUsers(List<Map<String, String>> userDataList) {
        List<User> createdUsers = new ArrayList<>();
        
        // Parse authProvider safely - take first value if multiple are provided
        String authProviderValue = authProvider.toUpperCase();
        if (authProviderValue.contains(",")) {
            authProviderValue = authProviderValue.split(",")[0].trim();
        }
        AuthProvider userAuthProvider = AuthProvider.valueOf(authProviderValue);
        
        for (Map<String, String> userData : userDataList) {
            try {
                User user = new User();
                user.setFirstName(userData.get(Constants.USER_FIELD_FIRST_NAME).trim());
                user.setLastName(userData.get(Constants.USER_FIELD_LAST_NAME).trim());
                user.setEmail(userData.get(Constants.USER_FIELD_EMAIL).trim().toLowerCase());
                user.setIsActive(false); // Will be activated when they set password via email
                user.setIsInitialPassword(true); // Mark as initial password since it's system-generated
                
                // Generate secure temporary password
                user.setPassword(userService.generateSecureTemporaryPassword());
                
                // Set roles using role names
                String[] roleNameArray = userData.get(Constants.USER_FIELD_ROLE_NAME).split(",");
                Set<Role> roles = new HashSet<>();
                for (String name : roleNameArray) {
                    Role role = roleRepository.findByName(name.trim())
                        .orElseThrow(() -> new IllegalArgumentException("Role not found: " + name));
                    roles.add(role);
                }
                user.setRoles(roles);
                
                // Check password complexity
                userService.checkPasswordComplexity(user.getPassword());
                
                // Create user in Keycloak if configured
                if (userAuthProvider == AuthProvider.KEYCLOAK && keycloakService != null) {
                    keycloakService.saveUser(user.getEmail(), user.getEmail(),
                            user.getFirstName(), user.getLastName(),
                            user.getPassword(), false);
                }
                
                // Save user to database
                User savedUser = userRepository.save(user);
                createdUsers.add(savedUser);
                
                log.debug("Created user: {} ({})", savedUser.getEmail(), savedUser.getId());
                
            } catch (Exception e) {
                log.error("Failed to create user from line {}: {}", userData.get(Constants.USER_FIELD_LINE_NUMBER), e.getMessage());
                throw new IllegalArgumentException("Failed to create user " + userData.get(Constants.USER_FIELD_EMAIL) + ": " + e.getMessage(), e);
            }
        }
        
        return createdUsers;
    }
    
    /**
     * Sends bulk email invites to created users
     */
    public void sendBulkEmailInvites(List<User> users) {
        // Parse authProvider safely - take first value if multiple are provided
        String authProviderValue = authProvider.toUpperCase();
        if (authProviderValue.contains(",")) {
            authProviderValue = authProviderValue.split(",")[0].trim();
        }
        AuthProvider userAuthProvider = AuthProvider.valueOf(authProviderValue);
        
        String emailTmplFile = Constants.EMAIL_TEMPLATE_GOOGLE_INVITE;
        if (userAuthProvider == AuthProvider.KEYCLOAK) {
            emailTmplFile = Constants.EMAIL_TEMPLATE_KEYCLOAK_INVITE;
        }
        
        List<String> emailErrors = new ArrayList<>();
        
        for (User user : users) {
            try {
                emailService.sendInvitationEmail(user, emailTmplFile);
                log.debug("Email invite sent to: {}", user.getEmail());
            } catch (Exception e) {
                String errorMsg = "Failed to send email to " + user.getEmail() + ": " + e.getMessage();
                emailErrors.add(errorMsg);
                log.error(errorMsg, e);
            }
        }
        
        if (!emailErrors.isEmpty()) {
            String combinedErrors = String.join("; ", emailErrors);
            throw new EmailSendingException(HttpStatus.INTERNAL_SERVER_ERROR, "Email sending failed for some users: " + combinedErrors);
        }
    }

    /**
     * Processes the complete bulk user upload workflow
     */
    @Transactional
    public List<User> processBulkUserUpload(MultipartFile file) throws Exception {
        // Parse CSV file
        List<Map<String, String>> userDataList = parseCsvFile(file);
        log.info("Parsed {} users from CSV file", userDataList.size());
        
        if (userDataList.isEmpty()) {
            throw new IllegalArgumentException(Constants.ERROR_NO_VALID_USER_DATA);
        }
        
        // Validate all users first
        List<String> errors = new ArrayList<>();
        validateBulkUsers(userDataList, errors);
        
        if (!errors.isEmpty()) {
            log.error("Validation errors found: {}", errors);
            throw new IllegalArgumentException("Validation errors: " + String.join("; ", errors));
        }
        
        // Create all users
        List<User> createdUsers = createBulkUsers(userDataList);
        log.info("Successfully created {} users", createdUsers.size());
        
        // Send email invites to all created users
        sendBulkEmailInvites(createdUsers);
        log.info("Email invites sent to {} users", createdUsers.size());
        
        return createdUsers;
    }

    /**
     * Generates sample CSV content for bulk user upload
     */
    public String generateSampleCsvContent() {
        StringBuilder csvContent = new StringBuilder();
        
        // CSV Header
        csvContent.append(Constants.CSV_HEADER + "\n");
        
        // Sample data with examples using role names
        csvContent.append("John,Doe,john.doe@example.com,\"" + Constants.ROLE_ADMIN + "," + Constants.ROLE_DEVELOPER + "\"\n");
        csvContent.append("Jane,Smith,jane.smith@example.com," + Constants.ROLE_DEVELOPER + "\n");
        csvContent.append("Bob,Johnson,bob.johnson@example.com,\"" + Constants.ROLE_ASSET_OWNER + "\"\n");
        csvContent.append("Alice,Brown,alice.brown@example.com," + Constants.ROLE_APPROVER + "\n");
        csvContent.append("Charlie,Wilson,charlie.wilson@example.com," + Constants.ROLE_AUDITOR + "\n");
        
        // Add comment lines explaining the format
        csvContent.append("# INSTRUCTIONS:\n");
        csvContent.append("# - firstName: Required, user's first name\n");
        csvContent.append("# - lastName: Required, user's last name\n");
        csvContent.append("# - email: Required, must be unique and valid email format\n");
        csvContent.append("# - roleName: Required, comma-separated role names (e.g., \"" + Constants.ROLE_ADMIN + "," + Constants.ROLE_DEVELOPER + "\")\n");
        csvContent.append("#\n");
        csvContent.append("# AVAILABLE ROLES:\n");
        csvContent.append("# - " + Constants.ROLE_ADMIN + ": Full system administration access\n");
        csvContent.append("# - " + Constants.ROLE_DEVELOPER + ": Access to development resources and asset requests\n");
        csvContent.append("# - " + Constants.ROLE_ASSET_OWNER + ": Manage and approve access to owned assets\n");
        csvContent.append("# - " + Constants.ROLE_APPROVER + ": Approve user access requests\n");
        csvContent.append("# - " + Constants.ROLE_AUDITOR + ": View audit logs and system activity\n");
        csvContent.append("#\n");
        csvContent.append("# NOTES:\n");
        csvContent.append("# - Role names are case-sensitive\n");
        csvContent.append("# - Multiple roles can be assigned using comma separation\n");
        csvContent.append("# - For multiple roles, wrap in quotes: \"" + Constants.ROLE_ADMIN + "," + Constants.ROLE_DEVELOPER + "\"\n");
        csvContent.append("# - Lines starting with " + Constants.CSV_COMMENT_PREFIX + " are ignored\n");
        csvContent.append("# - Remove sample data and add your users\n");
        
        return csvContent.toString();
    }

    /**
     * Processes bulk user upload and returns structured response
     */
    @Transactional
    public Map<String, Object> processBulkUserUploadWithResponse(MultipartFile file) {
        log.info("Starting bulk user upload. File: {}, Size: {} bytes", file.getOriginalFilename(), file.getSize());
        
        // Validate file
        if (file.isEmpty()) {
            throw new IllegalArgumentException(Constants.ERROR_UPLOADED_FILE_EMPTY);
        }
        
        if (!file.getOriginalFilename().toLowerCase().endsWith(Constants.CSV_EXTENSION)) {
            throw new IllegalArgumentException(Constants.ERROR_FILE_MUST_BE_CSV);
        }
        
        List<Map<String, String>> userDataList = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<User> createdUsers = new ArrayList<>();
        
        try {
            // Parse CSV file
            userDataList = parseCsvFile(file);
            log.info("Parsed {} users from CSV file", userDataList.size());
            
            if (userDataList.isEmpty()) {
                throw new IllegalArgumentException(Constants.ERROR_NO_VALID_USER_DATA);
            }
            
            // Validate all users first
            validateBulkUsers(userDataList, errors);
            
            if (!errors.isEmpty()) {
                log.error("Validation errors found: {}", errors);
                Map<String, Object> response = new HashMap<>();
                response.put(Constants.RESPONSE_SUCCESS, false);
                response.put(Constants.RESPONSE_MESSAGE, Constants.ERROR_VALIDATION_ERRORS_FOUND);
                response.put(Constants.RESPONSE_ERRORS, errors);
                response.put(Constants.RESPONSE_TOTAL_USERS, userDataList.size());
                response.put(Constants.RESPONSE_FAILED_USERS, userDataList.size());
                response.put(Constants.RESPONSE_SUCCESSFUL_USERS, 0);
                return response;
            }
            
            // Create all users
            createdUsers = createBulkUsers(userDataList);
            log.info("Successfully created {} users", createdUsers.size());
            
            // Send email invites to all created users
            sendBulkEmailInvites(createdUsers);
            log.info("Email invites sent to {} users", createdUsers.size());
            
            // Build success response
            Map<String, Object> response = new HashMap<>();
            response.put(Constants.RESPONSE_SUCCESS, true);
            response.put(Constants.RESPONSE_MESSAGE, String.format(Constants.SUCCESS_CREATED_USERS_AND_SENT_INVITES, createdUsers.size()));
            response.put(Constants.RESPONSE_TOTAL_USERS, createdUsers.size());
            response.put(Constants.RESPONSE_SUCCESSFUL_USERS, createdUsers.size());
            response.put(Constants.RESPONSE_FAILED_USERS, 0);
            response.put(Constants.RESPONSE_USERS, createdUsers.stream().map(user -> {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put(Constants.USER_FIELD_ID, user.getId());
                userInfo.put(Constants.USER_FIELD_EMAIL, user.getEmail());
                userInfo.put(Constants.USER_FIELD_FIRST_NAME, user.getFirstName());
                userInfo.put(Constants.USER_FIELD_LAST_NAME, user.getLastName());
                return userInfo;
            }).toList());
            
            return response;
            
        } catch (Exception e) {
            log.error("Bulk user upload failed: {}", e.getMessage(), e);
            
            // Clean up any created users if something failed after creation
            if (!createdUsers.isEmpty()) {
                log.info("Cleaning up {} created users due to failure", createdUsers.size());
                try {
                    for (User user : createdUsers) {
                        userRepository.delete(user);
                    }
                } catch (Exception cleanupError) {
                    log.error("Failed to cleanup users: {}", cleanupError.getMessage());
                }
            }
            
            // Build error response
            Map<String, Object> response = new HashMap<>();
            response.put(Constants.RESPONSE_SUCCESS, false);
            response.put(Constants.RESPONSE_MESSAGE, String.format(Constants.ERROR_BULK_UPLOAD_FAILED, e.getMessage()));
            response.put(Constants.RESPONSE_TOTAL_USERS, userDataList.size());
            response.put(Constants.RESPONSE_SUCCESSFUL_USERS, 0);
            response.put(Constants.RESPONSE_FAILED_USERS, userDataList.size());
            response.put(Constants.RESPONSE_ERRORS, Arrays.asList(e.getMessage()));
            
            return response;
        }
    }
} 