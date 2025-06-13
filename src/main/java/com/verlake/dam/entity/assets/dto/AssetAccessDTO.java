package com.verlake.dam.entity.assets.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AssetAccessDTO {
    private Long assetId;
    private String assetName;
    private String databaseType;
    private List<UserAccessDTO> users;
} 