package com.verlake.dam.entity;

import com.verlake.dam.enums.AuthProvider;
import lombok.Data;

@Data
public class UserDto {
    private String token;
    private AuthProvider authProvider;
    private User user;
    private boolean isAuthorized;
    private String inviteCode;
}