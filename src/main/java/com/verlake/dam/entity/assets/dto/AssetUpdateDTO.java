package com.verlake.dam.entity.assets.dto;

import lombok.Data;

import java.util.List;

@Data
public class AssetUpdateDTO {
    private Long assetId;
    private List<Long> userIds;
    private String method; // "add" or "remove"
} 