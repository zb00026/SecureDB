package com.verlake.dam.service.auth;

import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.user.dto.UserDTO;
import com.verlake.dam.service.users.UserService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AuthService {

    private final UserService userService;

    public AuthService(UserService userService) {
        this.userService = userService;
    }

    public User authenticateUser(UserDTO userDto, TokenService tokenService) {
        log.info("=== AuthService.authenticateUser START ===");
        log.info("Getting user from token using service: {}", tokenService.getClass().getSimpleName());
        
        User user = getUserFromToken(userDto.getToken(), tokenService);
        log.info("User retrieved from token - Email: {}, ID: {}, Active: {}", 
            user != null ? user.getEmail() : "null", 
            user != null ? user.getId() : "null",
            user != null ? user.getIsActive() : "null");
        
        log.info("Validating user and invite code");
        validateUser(user, userDto.getInviteCode());
        log.info("User validation successful");
        
        log.info("User {} authentication started. Current isActive: {}, Invite code provided: {}", 
                 user.getEmail(), user.getIsActive(), userDto.getInviteCode() != null);
        
        // Handle invite code first - this may activate the user
        log.info("Handling invite code processing");
        handleInviteCode(userDto, user);
        
        log.info("User {} after invite code handling. Final isActive: {}", 
                 user.getEmail(), user.getIsActive());
        
        // THEN check if user is active - after potential activation via invite code
        log.info("Performing final active status check");
        if (user.getIsActive() != null && !user.getIsActive()) {
            log.error("User {} is not active after all processing. Throwing FORBIDDEN exception", user.getEmail());
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "User account is not active. Either you have not activated your account or your account has been deactivated.");
        }
        
        log.info("User {} authentication completed successfully", user.getEmail());
        log.info("=== AuthService.authenticateUser SUCCESS ===");
        return user;
    }

    public void validateToken(UserDTO userDto, TokenService tokenService) {
        log.info("=== AuthService.validateToken START ===");
        log.info("Validating token with provider: {}", userDto.getAuthProvider());
        log.info("Token service: {}", tokenService.getClass().getSimpleName());
        
        try {
            boolean isValid = tokenService.verifyToken(userDto.getToken());
            log.info("Token verification result: {}", isValid);
            
            if (!isValid) {
                log.error("Token verification failed for provider: {}", userDto.getAuthProvider());
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid " + userDto.getAuthProvider() + " token");
            }
            
            log.info("Token validation successful");
            log.info("=== AuthService.validateToken SUCCESS ===");
        } catch (Exception e) {
            log.error("Exception during token validation: {}", e.getMessage());
            log.error("Exception type: {}", e.getClass().getSimpleName());
            log.error("Full stack trace: ", e);
            throw e; // Re-throw to maintain existing behavior
        }
    }

    private User getUserFromToken(String token, TokenService tokenService) {
        log.info("=== AuthService.getUserFromToken START ===");
        
        try {
            log.info("Extracting email from token using service: {}", tokenService.getClass().getSimpleName());
            String email = tokenService.getEmailFromToken(token);
            log.info("Email extracted from token: {}", email);
            
            log.info("Looking up user by email in database");
            User user = userService.findByEmail(email);
            
            if (user == null) {
                log.error("User not found in database for email: {}", email);
                log.error("This will result in a 403 FORBIDDEN response");
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Email " + email + " not registered");
            }
            
            log.info("User found in database - ID: {}, Email: {}, Active: {}, Roles: {}", 
                user.getId(), user.getEmail(), user.getIsActive(),
                user.getRoles().stream().map(role -> role.getName()).toList());
            log.info("=== AuthService.getUserFromToken SUCCESS ===");
            
            return user;
        } catch (Exception e) {
            log.error("Exception in getUserFromToken: {}", e.getMessage());
            log.error("Exception type: {}", e.getClass().getSimpleName());
            log.error("Full stack trace: ", e);
            throw e; // Re-throw to maintain existing behavior
        }
    }

    private void validateUser(User user, String inviteCode) {
        log.info("=== AuthService.validateUser START ===");
        log.info("Validating user: {}", user != null ? user.getEmail() : "null");
        log.info("Provided invite code: {}", inviteCode);
        log.info("User's stored invite code: {}", user != null ? user.getInviteCode() : "null");
        
        if (user == null) {
            log.error("User is null, throwing IllegalArgumentException");
            throw new IllegalArgumentException("Invalid token");
        }
        
        if (user.getInviteCode() != null && inviteCode != null && !inviteCode.equals(user.getInviteCode())) {
            log.error("Invite code mismatch for user {}. Provided: {}, Stored: {}", 
                     user.getEmail(), inviteCode, user.getInviteCode());
            throw new IllegalArgumentException("Invalid invite code");
        }
        
        log.info("User validation successful");
        log.info("=== AuthService.validateUser SUCCESS ===");
    }

    private void handleInviteCode(UserDTO userDto, User user) {
        if (userDto.getInviteCode() != null) {
            log.info("Processing invite code for user {}: {}", user.getEmail(), userDto.getInviteCode());
            
            // If user is accessing with an invite code and is not active, activate them
            if (user.getIsActive() != null && !user.getIsActive()) {
                log.info("Activating user {} via invite code", user.getEmail());
                user.setIsActive(true);
                // Clear invite-related fields once activated
                user.setInviteCode(null);
                user.setInviteEmail(null);
                userService.saveUser(user);
                log.info("User {} successfully activated", user.getEmail());
            } else {
                log.info("User {} already active, just clearing invite email", user.getEmail());
                // Just clear invite email for already active users
                user.setInviteEmail(null);
                userService.saveUser(user);
            }
        } else {
            log.info("No invite code provided for user {}", user.getEmail());
        }
        userDto.setAuthorized(true);
        userDto.setUser(user);
    }
} 