package com.verlake.dam.entity.user.dto;

import lombok.Data;

@Data
public class ResetPasswordValidationDTO {
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
} 