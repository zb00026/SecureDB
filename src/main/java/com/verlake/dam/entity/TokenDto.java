package com.verlake.dam.entity;

import com.verlake.dam.enums.AuthProvider;
import lombok.Data;

@Data
public class TokenDto {
    private String token;
    private AuthProvider authProvider;
    private boolean isAuthorized;
}