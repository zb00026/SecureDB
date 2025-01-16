package com.verlake.dam.service;

import com.verlake.dam.enums.AuthProvider;

public interface TokenService {
    boolean verifyToken(String token);
    String getEmailFromToken(String token);
    AuthProvider getAuthProvider();
}
