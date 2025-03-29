package com.verlake.dam.entity.user.dto;

import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.AuthProvider;
import lombok.Data;

@Data
public class UserDTO {
    private String token;
    private AuthProvider authProvider;
    private User user;
    private boolean isAuthorized;
    private String inviteCode;
}