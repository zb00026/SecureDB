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
        User user = getUserFromToken(userDto.getToken(), tokenService);
        validateUser(user, userDto.getInviteCode());
        
        log.info("User {} authentication started. Current isActive: {}, Invite code provided: {}", 
                 user.getEmail(), user.getIsActive(), userDto.getInviteCode() != null);
        
        // Handle invite code first - this may activate the user
        handleInviteCode(userDto, user);
        
        log.info("User {} after invite code handling. Final isActive: {}", 
                 user.getEmail(), user.getIsActive());
        
        // THEN check if user is active - after potential activation via invite code
        if (user.getIsActive() != null && !user.getIsActive()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "User account is not active. Either you have not activated your account or your account has been deactivated.");
        }
        
        return user;
    }

    public void validateToken(UserDTO userDto, TokenService tokenService) {
        if (!tokenService.verifyToken(userDto.getToken())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid " + userDto.getAuthProvider() + " token");
        }
    }

    private User getUserFromToken(String token, TokenService tokenService) {
        String email = tokenService.getEmailFromToken(token);
        User user = userService.findByEmail(email);
        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Email " + email + " not registered");
        }
        return user;
    }

    private void validateUser(User user, String inviteCode) {
        if (user == null) {
            throw new IllegalArgumentException("Invalid token");
        }
        if (inviteCode != null && !inviteCode.equals(user.getInviteCode())) {
            log.error("Invite code mismatch for user {}. Provided: {}, Stored: {}", 
                     user.getEmail(), inviteCode, user.getInviteCode());
            throw new IllegalArgumentException("Invalid invite code");
        }
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