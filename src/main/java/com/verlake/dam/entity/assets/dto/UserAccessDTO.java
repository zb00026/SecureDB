package com.verlake.dam.entity.assets.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserAccessDTO {
    private String username;
    private String grantee; // The full grantee string from database
    private List<PermissionDTO> permissions;
} 