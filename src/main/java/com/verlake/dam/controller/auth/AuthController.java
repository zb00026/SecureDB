package com.verlake.dam.controller.auth;

import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.user.dto.UserDTO;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.utils.CommonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.verlake.dam.enums.AuthProvider;
import com.verlake.dam.service.UserService;
import org.springframework.web.server.ResponseStatusException;
import com.verlake.dam.service.assets.DatabaseAccessService;
import com.verlake.dam.service.auth.AuthService;
import com.verlake.dam.service.auth.KeycloakService;
import com.verlake.dam.service.auth.TokenService;
import com.verlake.dam.service.auth.TokenServiceManager;
import com.verlake.dam.service.assets.AssetService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import org.springframework.core.task.AsyncTaskExecutor;

@RestController
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private DatabaseAccessService databaseAccessService;

    @Autowired
    private AssetService assetService;

    @Autowired
    private KeycloakService keycloakService;

    @Autowired
    private AsyncTaskExecutor taskExecutor;

    @Autowired
    private AuthService authService;

    private final TokenServiceManager tokenServiceManager;

    public AuthController(TokenServiceManager tokenServiceManager) {
        this.tokenServiceManager = tokenServiceManager;
    }

    @PostMapping("/api/auth/verifyToken")
    public ResponseEntity<UserDTO> verifyToken(@RequestBody UserDTO userDto) {
        TokenService tokenService = getTokenService(userDto.getAuthProvider());
        authService.validateToken(userDto, tokenService);
        User user = authService.authenticateUser(userDto, tokenService);


        // Check if user has ASSET_OWNER role
        if (user.getRoles().stream().anyMatch(role -> Roles.ASSET_OWNER.getOriginalName().equals(role.getName()))) {
            // Get user key and credentials before starting thread
            final String userKey = keycloakService.getUserKey(CommonUtils.getKeycloakUserIdFromSession());
            final List<AssetCredential> credentials = assetService.getAssignedCredentials();

            // Process credentials in separate thread
            taskExecutor.execute(() -> {
                try {
                    credentials.stream()
                        .filter(cred -> {
                            // Check if credential has required fields
                            boolean hasValidCredentials = cred.getUsername() != null && !cred.getUsername().isEmpty()
                                    && cred.getPassword() != null && !cred.getPassword().isEmpty();
                            
                            // Check if user is the owner of the asset associated with this credential
                            boolean isAssetOwner = cred.getUser() != null && cred.getUser().getId().equals(user.getId());
                            
                            return hasValidCredentials && isAssetOwner;
                        })
                        .forEach(cred -> {
                            try {
                                String decryptedPassword = CommonUtils.decrypt(userKey, cred.getPassword());
                                cred.setPassword(decryptedPassword);
                                databaseAccessService.updateAssetObjects(cred);
                            } catch (Exception e) {
                                log.error("Failed to update database objects upon asset owner login due to credential errors. credential: " + cred.getId(), e);
                            }
                        });
                } catch (Exception e) {
                    log.error("Failed to update database objects upon asset owner login. Error processing asset owner credentials", e);
                }
            });
        }

        return ResponseEntity.ok().body(userDto);
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
