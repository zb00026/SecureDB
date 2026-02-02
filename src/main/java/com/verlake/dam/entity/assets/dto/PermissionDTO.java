package com.verlake.dam.entity.assets.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PermissionDTO {
    private String type; // SELECT, INSERT, UPDATE, DELETE, etc.
    private String scope; // database, table name, or object name
    private String objectType; // DATABASE, TABLE, VIEW, PROCEDURE
    private Boolean grantable; // Whether user can grant this permission to others
} 