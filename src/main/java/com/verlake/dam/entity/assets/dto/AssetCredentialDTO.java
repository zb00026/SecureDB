package com.verlake.dam.entity.assets.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AssetCredentialDTO {
    private Long assetId;
    private String username;
    private String password;
}