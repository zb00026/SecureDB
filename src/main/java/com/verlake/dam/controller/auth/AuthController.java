package com.verlake.dam.controller.auth;

import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.user.dto.UserDTO;
import com.verlake.dam.entity.user.dto.ForgotPasswordRequestDTO;
import com.verlake.dam.entity.user.dto.ResetPasswordRequestDTO;
import com.verlake.dam.entity.user.dto.ResetPasswordValidationDTO;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.repository.assets.AccessRequestRepository;
import com.verlake.dam.repository.assets.AssetCredentialsRepository;
import com.verlake.dam.service.auth.ForgotPasswordService;
import com.verlake.dam.utils.CommonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.verlake.dam.enums.AuthProvider;

import org.springframework.web.server.ResponseStatusException;
import com.verlake.dam.service.assets.DatabaseAccessService;
import com.verlake.dam.service.auth.AuthService;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.auth.TokenService;
import com.verlake.dam.service.auth.TokenServiceManager;
import com.verlake.dam.service.firebase.FirebaseMessagingService;
import com.verlake.dam.service.users.UserService;
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

    public AuthController(TokenServiceManager tokenServiceManager) {
        this.tokenServiceManager = tokenServiceManager;
    }

    @PostMapping("/api/auth/verifyToken")
    public ResponseEntity<UserDTO> verifyToken(@RequestBody UserDTO userDto) {
        log.info("Received authentication request for provider: {}, inviteCode: {}", 
                 userDto.getAuthProvider(), userDto.getInviteCode());
        
        TokenService tokenService = getTokenService(userDto.getAuthProvider());
        authService.validateToken(userDto, tokenService);
        User user = authService.authenticateUser(userDto, tokenService);

        if (isAssetOwner(user)) {
            processAssetOwnerCredentials(user);
        }
        userDto.setUser(user);

        return ResponseEntity.ok().body(userDto);
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

    private boolean isAssetOwner(User user) {
        return user.getRoles().stream()
                .anyMatch(role -> Roles.ASSET_OWNER.getOriginalName().equals(role.getName()) || Roles.ADMIN.getOriginalName().equals(role.getName()));
    }

    private void processAssetOwnerCredentials(User user) {
        final String userKey = keycloakService.getUserKey();
        final List<AssetCredential> credentials = assetService.getAssignedCredentials();

        taskExecutor.execute(() -> processCredentialsAsync(user, userKey, credentials));
    }

    private void processCredentialsAsync(User user, String userKey, List<AssetCredential> credentials) {
        try {
            credentials.stream()
                    .filter(cred -> isValidCredential(cred, user))
                    .forEach(cred -> processCredential(cred, userKey));
        } catch (Exception e) {
            log.error(
                    "Failed to update database objects upon asset owner login. Error processing asset owner credentials",
                    e);
        }
    }

    private boolean isValidCredential(AssetCredential cred, User user) {
        boolean hasValidCredentials = cred.getUsername() != null && !cred.getUsername().isEmpty()
                && cred.getPassword() != null && !cred.getPassword().isEmpty();

        boolean isAssetOwner = cred.getUser() != null && cred.getUser().getId().equals(user.getId());

        return hasValidCredentials && isAssetOwner;
    }

    private void processCredential(AssetCredential cred, String userKey) {
        try {
            if (!cred.getIsTemporaryPassword()) {
                String decryptedPassword = CommonUtils.decrypt(userKey, cred.getPassword());
                cred.setPassword(decryptedPassword);
            }
            databaseAccessService.updateAssetObjects(cred);
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
