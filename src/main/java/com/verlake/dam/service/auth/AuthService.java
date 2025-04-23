package com.verlake.dam.service.auth;

import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.user.dto.UserDTO;
import com.verlake.dam.service.UserService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserService userService;

    public AuthService(UserService userService) {
        this.userService = userService;
    }

    public User authenticateUser(UserDTO userDto, TokenService tokenService) {
        User user = getUserFromToken(userDto.getToken(), tokenService);
        validateUser(user, userDto.getInviteCode());
        handleInviteCode(userDto, user);
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
        if (inviteCode != null && !inviteCode.equals(user.getInviteEmail())) {
            throw new IllegalArgumentException("Invalid invite code");
        }
    }

    private void handleInviteCode(UserDTO userDto, User user) {
        if (userDto.getInviteCode() != null) {
            user.setInviteEmail(null);
            userService.saveUser(user);
        }
        userDto.setAuthorized(true);
        userDto.setUser(user);
    }
} 