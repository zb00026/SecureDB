package com.verlake.dam.controller.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verlake.dam.entity.Role;
import com.verlake.dam.entity.firebase.NotificationTask;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.user.dto.UserDTO;
import com.verlake.dam.entity.user.dto.UserFilter;
import com.verlake.dam.enums.AuthProvider;
import com.verlake.dam.enums.EmailType;
import com.verlake.dam.repository.NotificationTaskRepository;
import com.verlake.dam.repository.RoleRepository;
import com.verlake.dam.repository.UserRepository;
import com.verlake.dam.service.auth.GlobalAuthProviderService;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.users.UserCsvService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
@Slf4j
public class UserController {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private UserCsvService userCsvService;

    @Autowired(required = false)
    private KeycloakService keycloakService;

    @Autowired
    private GlobalAuthProviderService globalAuthProviderService;

    @Autowired
    private NotificationTaskRepository notificationTaskRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${auth.provider}")
    private String authProvider;

    public UserController(UserRepository userRepository, RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public Object getAllUsers(UserFilter filter) {
        return userService.getAllUsers(filter);
    }

    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, Constants.getMessage(Constants.USER_NOT_FOUND, id));
        }
        return user;
    }

    @PostMapping("/createUserAndSendInvite")
    public ResponseEntity<Object> createUserAndSendInvite(@RequestBody UserDTO userDto) {
        User user = userDto.getUser();
        AuthProvider userAuthProvider = userDto.getAuthProvider();
        
        // Validate email uniqueness
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    Constants.getMessage("user.email.already.exists", user.getEmail()));
        }
        
        // Note: Names can be duplicated, only email must be unique

        // Handle password generation based on global auth provider
        boolean shouldCreateWithoutPasswords = globalAuthProviderService.shouldCreateUsersWithoutPasswords();
        boolean isSSOProvider = globalAuthProviderService.isSSOProvider();
        
        // Additional safety check: if auth provider is SSO but shouldCreateUsersWithoutPasswords returns false,
        // it means there was an error checking SSO status, so we should still treat it as SSO
        if (isSSOProvider || shouldCreateWithoutPasswords) {
            // SSO users don't need passwords
            user.setPassword(null);
            user.setIsActive(true); // SSO users are active by default
            log.info("Creating user without password (SSO mode)");
        } else {
            // Generate temporary password for non-SSO users
            if (user.getPassword() == null || user.getPassword().isEmpty()) {
                user.setPassword(userService.generateSecureTemporaryPassword());
            }
            // Check password complexity for non-SSO users
            userService.checkPasswordComplexity(user.getPassword());
            user.setIsActive(false);
            log.info("Creating user with temporary password (non-SSO mode)");
        }

        // Handle Keycloak user creation
        if (userAuthProvider == AuthProvider.KEYCLOAK && keycloakService != null) {
            // Check if SSO is enabled in Keycloak (has identity providers)
            boolean isSSOEnabled = keycloakService.isSSOEnabled();
            
            if (isSSOEnabled) {
                // For Keycloak SSO, don't create user in Keycloak or set password
                // Users will authenticate through identity providers
                log.info("Keycloak SSO is enabled - skipping Keycloak user creation for: {}", user.getEmail());
            } else {
                // Regular Keycloak users get created with password
                keycloakService.saveUser(user.getEmail(), user.getEmail(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getPassword(), false); // Set as temporary password
            }
        }
        
        userRepository.save(user);
        
        // Create notification task for invitation email (processed asynchronously by notification job)
        createInvitationNotificationTask(user);
        
        return ResponseEntity.status(HttpStatus.OK).body(user);
    }

    @PostMapping
    public User createUser(@RequestBody User user) {

        if (userRepository.existsByEmail(user.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    Constants.getMessage("user.email.already.exists", user.getEmail()));
        }
        
        // Note: Names can be duplicated, only email must be unique

        // Check password complexity
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            userService.checkPasswordComplexity(user.getPassword());
        }

        if (authProvider.contains(AuthProvider.KEYCLOAK.toString().toLowerCase())) {
            keycloakService.saveUser(user.getEmail(),
                    user.getEmail(),
                    user.getFirstName(),
                    user.getLastName(),
                    user.getPassword(), false);
        }
        // Save the new user
        user.setIsActive(true);
        return userRepository.save(user);
    }

    @PutMapping("/{id}")
    public User updateUser(@PathVariable Long id, @RequestBody User userDetails) {
        return userRepository.findById(id)
                .map(user -> {
                    // Note: Names can be duplicated, only email must be unique
                    
                    user.setFirstName(userDetails.getFirstName());
                    user.setLastName(userDetails.getLastName());
                    user.setEmail(userDetails.getEmail());

                    // Check password complexity if password is being updated
                    if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
                        userService.checkPasswordComplexity(userDetails.getPassword());
                        user.setPassword(userDetails.getPassword());
                    }

                    if (userDetails.getRoles() != null && !userDetails.getRoles().isEmpty()) {
                        Set<Long> roleIds = userDetails.getRoles().stream()
                                .map(Role::getId)
                                .collect(Collectors.toSet());

                        // Fetch roles from the database
                        Set<Role> roles = new HashSet<>(roleRepository.findAllById(roleIds));

                        // Ensure all requested roles exist
                        if (roles.size() != roleIds.size()) {
                            throw new ResponseStatusException(HttpStatus.NOT_FOUND, Constants.getMessage("roles.not.found"));
                        }

                        // Assign new roles to user
                        user.setRoles(roles);
                    }

                    // If Keycloak Auth provider is provided, Needs to update Keycloak user's
                    // information when updating user
                    if (authProvider.contains(AuthProvider.KEYCLOAK.toString().toLowerCase())) {
                        keycloakService.saveUser(user.getEmail(),
                                user.getEmail(),
                                user.getFirstName(),
                                user.getLastName(),
                                user.getPassword(), false);
                    }
                    return userRepository.save(user);
                })
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, Constants.getMessage(Constants.USER_NOT_FOUND, id)));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(user -> {
                    // Call the service method to handle cascade deletion
                    userService.deleteUserWithCascade(id);
                    
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("status", Constants.getMessage("status.success"));
                    response.put("error_message", "");
                    return ResponseEntity.ok(response);
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        Constants.getMessage("user.not.found.dot", id)));
    }

    @PutMapping("/setApprover/{id}/{approverId}")
    public User setApproverOfUser(@PathVariable Long id, @PathVariable Long approverId) {
        return userService.setApproverForUser(id, approverId);
    }

    @PutMapping("/unsetApprover/{id}")
    public User unsetApproverOfUser(@PathVariable Long id) {
        return userService.unsetApproverForUser(id);
    }

    /**
     * Activate a user account
     */
    @PutMapping("/activate/{id}")
    public ResponseEntity<User> activateUser(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, Constants.getMessage(Constants.USER_NOT_FOUND, id));
        }
        
        User activatedUser = userService.activateUser(user);
        return ResponseEntity.ok(activatedUser);
    }

    /**
     * Deactivate a user account
     */
    @PutMapping("/deactivate/{id}")
    public ResponseEntity<User> deactivateUser(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, Constants.getMessage(Constants.USER_NOT_FOUND, id));
        }
        
        User deactivatedUser = userService.deactivateUser(user);
        return ResponseEntity.ok(deactivatedUser);
    }

    @GetMapping("/download-sample-csv")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public ResponseEntity<String> downloadSampleCSV() {
        log.info(Constants.getMessage("success.generating.csv"));

        String csvContent = userCsvService.generateSampleCsvContent();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, Constants.CSV_ATTACHMENT_HEADER);
        headers.add(HttpHeaders.CONTENT_TYPE, Constants.CSV_CONTENT_TYPE);

        log.info(Constants.getMessage("success.csv.generated"));
        return ResponseEntity.ok()
                .headers(headers)
                .body(csvContent);
    }

    @PostMapping(value = "/bulk-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> bulkUploadUsers(@RequestParam("file") MultipartFile file) {

        Map<String, Object> response = userCsvService.processBulkUserUploadWithResponse(file);

        // Determine HTTP status based on success
        boolean success = (Boolean) response.get(Constants.RESPONSE_SUCCESS);
        if (success) {
            return buildJsonResponse(ResponseEntity.ok(), response);
        } else {
            // Check if it's a validation error (400) or server error (500)
            String message = (String) response.get(Constants.RESPONSE_MESSAGE);
            if (message.contains(Constants.getMessage("error.validation.errors.found")) ||
                message.contains(Constants.getMessage("error.uploaded.file.empty")) ||
                message.contains(Constants.getMessage("error.file.must.be.csv")) ||
                message.contains(Constants.getMessage("error.no.valid.user.data"))) {
                return buildJsonResponse(ResponseEntity.badRequest(), response);
            } else {
                return buildJsonResponse(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR), response);
            }
        }
    }


    /**
     * Helper method to build ResponseEntity with consistent JSON headers
     */
    private ResponseEntity<Map<String, Object>> buildJsonResponse(
            ResponseEntity.BodyBuilder responseBuilder, 
            Map<String, Object> body) {
        return responseBuilder
                .header("Content-Type", Constants.CONTENT_TYPE_JSON)
                .header("Cache-Control", Constants.CACHE_CONTROL_NO_CACHE)
                .body(body);
    }

    /**
     * Creates a NotificationTask for invitation email
     * This ensures user creation succeeds even if email sending fails
     */
    private void createInvitationNotificationTask(User user) {
        try {
            // Get auth provider from global auth provider service
            AuthProvider userAuthProvider = globalAuthProviderService.getCurrentAuthProvider();
            
            // Create notification data with auth provider information
            ObjectNode notificationData = objectMapper.createObjectNode();
            ObjectNode dataNode = objectMapper.createObjectNode();
            dataNode.put("authProvider", userAuthProvider.toString());
            
            // Only include password for non-SSO users
            if (user.getPassword() != null) {
                dataNode.put("tempPassword", user.getPassword());
            }
            
            notificationData.set(Constants.ACCESS_OBJECT_ATTR_DATA, dataNode);
            
            // Create the notification task
            NotificationTask task = new NotificationTask();
            task.setReceiver(user);
            task.setSender(null); // No specific sender for invitation emails
            task.setAsset(null); // No asset associated with invitation emails
            task.setEmailType(EmailType.INVITATION);
            task.setNotificationMessage(objectMapper.writeValueAsString(notificationData));
            task.setSent(false);
            
            // Save the notification task - it will be picked up by the batch job
            notificationTaskRepository.save(task);
            log.debug("Created invitation notification task for user: {}", user.getEmail());
        } catch (Exception e) {
            // Log error but don't fail user creation
            log.error("Failed to create invitation notification task for user {}: {}", 
                    user.getEmail(), e.getMessage(), e);
        }
    }

}
