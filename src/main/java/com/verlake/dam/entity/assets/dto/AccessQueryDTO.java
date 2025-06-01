package com.verlake.dam.entity.assets.dto;

import com.verlake.dam.entity.assets.AccessLevelObject;
import com.verlake.dam.entity.assets.AccessRequest;
import lombok.Data;

import java.util.List;

@Data
public class AccessQueryDTO {
    private Long requestId;
    private Long assetId;
    private String query;
} 