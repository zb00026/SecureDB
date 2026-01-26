package com.verlake.dam.controller.auth;

import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.user.dto.UserDTO;
import com.verlake.dam.entity.user.dto.ForgotPasswordRequestDTO;
import com.verlake.dam.entity.user.dto.ResetPasswordRequestDTO;
import com.verlake.dam.entity.user.dto.ResetPasswordValidationDTO;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.repository.UserRepository;
import com.verlake.dam.repository.assets.AccessRequestRepository;
import com.verlake.dam.repository.assets.AssetCredentialsRepository;
import com.verlake.dam.service.auth.*;
import com.verlake.dam.utils.CommonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.verlake.dam.enums.AuthProvider;

import org.springframework.web.server.ResponseStatusException;
import com.verlake.dam.service.assets.DatabaseAccessService;
import com.verlake.dam.service.firebase.FirebaseMessagingService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.service.assets.AccessRequestService;
import com.verlake.dam.service.assets.AssetService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.core.task.AsyncTaskExecutor;

@RestController
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Value("${auth.provider}")
    private String authProvider;

    @Autowired
    private DatabaseAccessService databaseAccessService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private KeycloakService keycloakService;

    @Autowired
    private FirebaseMessagingService firebaseMessagingService;

    @Autowired
    private AsyncTaskExecutor taskExecutor;

    @Autowired
    private AuthService authService;

    @Autowired
    private ForgotPasswordService forgotPasswordService;

    private final TokenServiceManager tokenServiceManager;
    @Autowired
    private AccessRequestRepository accessRequestRepository;
    @Autowired
    private AssetCredentialsRepository assetCredentialsRepository;
    @Autowired
    private UserService userService;
    
    @Autowired
    private AccessRequestService accessRequestService;
    @Autowired
    private GlobalAuthProviderService globalAuthProviderService;
    @Autowired
    private UserRepository userRepository;

    public AuthController(TokenServiceManager tokenServiceManager) {
        this.tokenServiceManager = tokenServiceManager;
    }

    @PostMapping("/api/auth/verifyToken")
    public ResponseEntity<UserDTO> verifyToken(@RequestBody UserDTO userDto) {
        log.info("=== VERIFY TOKEN REQUEST START ===");
        log.info("Auth Provider: {}", userDto.getAuthProvider());
        log.info("Has Token: {}", userDto.getToken() != null);
        log.info("Token Length: {}", userDto.getToken() != null ? userDto.getToken().length() : 0);
        log.info("Token Preview: {}", userDto.getToken() != null ? userDto.getToken().substring(0, Math.min(50, userDto.getToken().length())) + "..." : "null");
        log.info("Has Invite Code: {}", userDto.getInviteCode() != null);
        log.info("Invite Code: {}", userDto.getInviteCode());
        
        try {
            log.info("Step 1: Getting token service for provider: {}", userDto.getAuthProvider());
            TokenService tokenService = getTokenService(userDto.getAuthProvider());
            log.info("Token service obtained: {}", tokenService.getClass().getSimpleName());
            
            log.info("Step 2: Validating token with token service");
            authService.validateToken(userDto, tokenService);
            log.info("Token validation successful");
            
            log.info("Step 3: Authenticating user with token service");
            User user = authService.authenticateUser(userDto, tokenService);
            log.info("User authentication successful for email: {}", user != null ? user.getEmail() : "null");
            
            if (user != null) {
                log.info("User details - ID: {}, Email: {}, Active: {}, Roles: {}", 
                    user.getId(), user.getEmail(), user.getIsActive(), 
                    user.getRoles().stream().map(role -> role.getName()).toList());
                if (globalAuthProviderService.isSSOProvider()) {
                    user.setIsInitialPassword(false);
                    userRepository.save(user);
                }
            }

            log.info("Step 4: Checking if user is asset owner");
            if (userService.isAssetOwner(user)) {
                log.info("User is asset owner, processing credentials asynchronously");
                processAssetOwnerCredentials();
            } else {
                log.info("User is not asset owner, skipping credential processing");
            }
            
            // Update expired access requests to EXPIRED status if user has developer role
            if (userService.hasRole(user, Roles.DEVELOPER.getOriginalName())) {
                accessRequestService.updateExpiredAccessRequests(user);
            }
            
            log.info("Step 5: Setting user in response DTO");
            userDto.setUser(user);
            
            log.info("=== VERIFY TOKEN REQUEST SUCCESS ===");
            return ResponseEntity.ok().body(userDto);
            
        } catch (Exception e) {
            log.error("=== VERIFY TOKEN REQUEST FAILED ===");
            log.error("Error Type: {}", e.getClass().getSimpleName());
            log.error("Error Message: {}", e.getMessage());
            log.error("Full Stack Trace: ", e);
            throw e; // Re-throw to maintain existing error handling
        }
    }

    @PostMapping("/api/auth/updatePassword")
    public ResponseEntity<User> updatePassword(@RequestBody User userDto) {
        if (userDto.getPassword() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Password can not be empty.");
        }
        
        // Check password complexity
        userService.checkPasswordComplexity(userDto.getPassword());
        
        User curUser = userService.getCurrentUser();
        if (curUser != null) {
            curUser.setPassword(userDto.getPassword());
            if (authProvider.contains(AuthProvider.KEYCLOAK.toString().toLowerCase())) {
                keycloakService.saveUser(curUser.getEmail(),
                        curUser.getEmail(),
                        curUser.getFirstName(),
                        curUser.getLastName(),
                        userDto.getPassword(), false);
            }
            curUser.setIsInitialPassword(false);
            
            // Activate user when they set their permanent password
            // This completes the invitation/activation process
            curUser = userService.completeUserActivation(curUser);
        }
        userService.saveUser(curUser);
        return ResponseEntity.ok().body(userDto);
    }

    @PostMapping("/api/auth/forgotPassword")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody ForgotPasswordRequestDTO request) {
        log.info("Received forgot password request for email: {}", request.getEmail());
        
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        forgotPasswordService.initiateForgotPassword(request.getEmail().trim());
        return CommonUtils.getSuccessResponse();
    }

    @PostMapping("/api/auth/validateResetToken")
    public ResponseEntity<ResetPasswordValidationDTO> validateResetToken(@RequestBody ResetPasswordRequestDTO tokenDto) {
        log.info("Validating reset token: {}", tokenDto.getToken());
        
        if (tokenDto.getToken() == null || tokenDto.getToken().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token is required");
        }

        try {
            User user = forgotPasswordService.validateResetToken(tokenDto.getToken().trim());
            
            ResetPasswordValidationDTO response = new ResetPasswordValidationDTO();
            response.setEmail(user.getEmail());
            response.setFirstName(user.getFirstName());
            response.setLastName(user.getLastName());
            response.setFullName(user.getFirstName() + " " + user.getLastName());
            
            return ResponseEntity.ok().body(response);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error validating reset token: {}", tokenDto.getToken(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "An error occurred while validating the token. Please try again later.");
        }
    }

    @PostMapping("/api/auth/updateFromForgotPassword")
    public ResponseEntity<Map<String, Object>> updateFromForgotPassword(@RequestBody ResetPasswordRequestDTO request) {
        log.info("Received password update request for token: {}", request.getToken());
        
        if (request.getToken() == null || request.getToken().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token is required");
        }

        try {
            forgotPasswordService.updatePasswordFromToken(request.getToken().trim(), request.getPassword());
            return CommonUtils.getSuccessResponse();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating password for token: {}", request.getToken(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "An error occurred while updating the password. Please try again later.");
        }
    }


    private void processAssetOwnerCredentials() {
        final String userKey = keycloakService.getUserKey();
        final List<AssetCredential> credentials = assetService.getAssignedCredentials();

        taskExecutor.execute(() -> processCredentialsAsync(userKey, credentials));
    }

    private void processCredentialsAsync(String userKey, List<AssetCredential> credentials) {
        try {
            credentials.stream()
                    .filter(this::isValidCredential)
                    .forEach(cred -> processCredential(cred, userKey));
        } catch (Exception e) {
            log.error(
                    "Failed to update database objects upon asset owner login. Error processing asset owner credentials",
                    e);
        }
    }

    private boolean isValidCredential(AssetCredential cred) {
        boolean hasValidCredentials = cred.getUsername() != null && !cred.getUsername().isEmpty()
                && cred.getPassword() != null && !cred.getPassword().isEmpty();
        return hasValidCredentials && cred.getUser() != null && userService.isAssetOwner(cred.getUser());
    }

    private void processCredential(AssetCredential cred, String userKey) {
        try {
            // Create a temporary credential with decrypted password instead of modifying the original
            // This prevents JPA from tracking the credential as dirty and saving decrypted password
            AssetCredential tempCredential = new AssetCredential();
            tempCredential.setId(cred.getId()); // Set ID so updateAssetObjects can find the AssetObject
            tempCredential.setAsset(cred.getAsset());
            tempCredential.setUsername(cred.getUsername());
            tempCredential.setUser(cred.getUser());
            tempCredential.setUserAccessType(cred.getUserAccessType());
            tempCredential.setIsTemporaryPassword(cred.getIsTemporaryPassword());
            tempCredential.setIsDeleted(cred.getIsDeleted());

            if (!cred.getIsTemporaryPassword()) {
                String decryptedPassword = CommonUtils.decrypt(userKey, cred.getPassword());
                tempCredential.setPassword(decryptedPassword);
            } else {
                tempCredential.setPassword(cred.getPassword());
            }

            if (cred.getUserAccessType().equals(Roles.ASSET_OWNER.getOriginalName())) {
                databaseAccessService.updateAssetObjects(tempCredential);
            }
            processExpiredDeveloperCredential(cred);
        } catch (Exception e) {
            handleCredentialProcessingError(cred, e);
        }
    }

    private void processExpiredDeveloperCredential(AssetCredential ownerCred) {
        List<AccessRequest> accessRequests = accessRequestRepository
                .findByExpiryDateBeforeAndAssetCredentialIsDeletedFalse(LocalDateTime.now());
        accessRequests.stream()
                .filter(accessRequest -> accessRequest.getAsset().getId().equals(ownerCred.getAsset().getId()))
                .forEach(accessRequest -> {
                    AssetCredential devCred = accessRequest.getAssetCredential();
                    try {
                        databaseAccessService.revokeCredentialAccess(devCred, ownerCred);
                    } catch (Exception e) {
                        log.error("Failed to revoke credential access for user: {} due to error: {}",
                                devCred.getUsername(), e.getMessage());
                        handleCredentialProcessingError(ownerCred, e);
                    }
                    devCred.setIsDeleted(true);
                    accessRequest.setAssetApproverStatus(ApprovalStatus.EXPIRED);
                    accessRequest.setDeveloperApproverStatus(ApprovalStatus.EXPIRED);
                    accessRequestRepository.save(accessRequest);
                    assetCredentialsRepository.save(devCred);
                    log.info("Marked credential as deleted for user: {} due to expiration",
                            devCred.getUsername());
                });
    }

    private void handleCredentialProcessingError(AssetCredential cred, Exception e) {
        try {
            firebaseMessagingService.setAssetObjectsFailureNotification(cred.getUser(), cred.getAsset());
        } catch (Exception notifyException) {
            log.error("Failed to set notification task", notifyException);
        }

        log.error("Failed to update database objects upon asset owner login due to credential errors. credential: "
                + cred.getId(), e);
    }

    private TokenService getTokenService(AuthProvider authProvider) {
        TokenService tokenService = tokenServiceManager.getService(authProvider);
        if (tokenService == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, authProvider + " is unsupported auth provider");
        }
        return tokenService;
    }
}
