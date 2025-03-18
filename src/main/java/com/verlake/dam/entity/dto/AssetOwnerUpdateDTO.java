package com.verlake.dam.entity.dto;

import java.util.List;

import lombok.Data;

@Data
public class AssetOwnerUpdateDTO {
    private Long assetId;
    private List<Long> userIds;
    private String method; // "add" or "remove"
} 