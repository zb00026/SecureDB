package com.verlake.dam.controller.auth;

import com.verlake.dam.entity.UserDto;
import com.verlake.dam.repository.EmailRepository;
import com.verlake.dam.service.TokenService;
import com.verlake.dam.service.manager.TokenServiceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.verlake.dam.entity.User;
import com.verlake.dam.enums.AuthProvider;
import com.verlake.dam.service.UserService;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuthController {

    @Autowired
    private UserService userService;

    private final TokenServiceManager tokenServiceManager;
    @Autowired
    private EmailRepository emailRepository;

    @Autowired
    public AuthController(TokenServiceManager tokenServiceManager) {
        this.tokenServiceManager = tokenServiceManager;
    }

    @PostMapping("/api/auth/verifyToken")
    public ResponseEntity<UserDto> verifyToken(@RequestBody UserDto userDto) {
        TokenService tokenService = getTokenService(userDto.getAuthProvider());
        validateToken(userDto, tokenService);
        User user = getUserFromToken(userDto.getToken(), tokenService);
        validateUser(user, userDto.getInviteCode());
        handleInviteCode(userDto, user);
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

    private void validateToken(UserDto userDto, TokenService tokenService) {
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
                    HttpStatus.FORBIDDEN, "Email not registered");
        }
        return user;
    }

    private void validateUser(User user, String inviteCode) {
        if (inviteCode == null && (user.getIsActive() == null || !user.getIsActive())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "User is not activated");
        }
    }

    private void handleInviteCode(UserDto userDto, User user) {
        if (userDto.getInviteCode() != null) {
            user.setIsActive(true);
            userService.saveUser(user);
        }
        userDto.setAuthorized(true);
        userDto.setUser(user);
    }
}
