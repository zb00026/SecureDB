package com.verlake.dam.entity.assets.dto;

import java.util.List;

import lombok.Data;

@Data
public class AssetUpdateDTO {
    private Long assetId;
    private List<Long> userIds;
    private String method; // "add" or "remove"
} 