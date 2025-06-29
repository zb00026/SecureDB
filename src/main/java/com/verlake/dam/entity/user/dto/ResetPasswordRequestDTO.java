package com.verlake.dam.entity.user.dto;

import lombok.Data;

@Data
public class ResetPasswordRequestDTO {
    private String token;
    private String password;
    private String confirmPassword;
} 