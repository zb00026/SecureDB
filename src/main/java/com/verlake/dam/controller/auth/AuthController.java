package com.verlake.dam.controller.auth;

import com.verlake.dam.service.TokenService;
import com.verlake.dam.service.manager.TokenServiceManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.verlake.dam.entity.TokenDto;
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
    public AuthController(TokenServiceManager tokenServiceManager) {
        this.tokenServiceManager = tokenServiceManager;
    }

    @PostMapping("/api/auth/verifyToken")
    public ResponseEntity<TokenDto> verifyToken(@RequestBody TokenDto tokenDto) {
        AuthProvider authProvider = tokenDto.getAuthProvider();
        tokenDto.setAuthorized(false);
        TokenService tokenService = tokenServiceManager.getService(authProvider);
        if (tokenService == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, tokenDto.getAuthProvider() + " is unsupported auth provider");
        } else {
            if(tokenService.verifyToken(tokenDto.getToken())) {
                String email = tokenService.getEmailFromToken(tokenDto.getToken());
                User user = userService.findByEmail(email);
                if (user != null) {
                    tokenDto.setAuthorized(true);
                    tokenDto.setUser(user);
                    return ResponseEntity.ok().body(tokenDto);
                } else {
                    throw new ResponseStatusException(
                            HttpStatus.FORBIDDEN, "Email not registered");
                }
            } else {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid " + authProvider + " token");
            }
        }
    }
}
