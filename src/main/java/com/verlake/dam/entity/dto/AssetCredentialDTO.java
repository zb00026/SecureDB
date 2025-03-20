package com.verlake.dam.entity.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AssetCredentialDTO {
    private Long assetId;
    private String username;
    private String password;
}