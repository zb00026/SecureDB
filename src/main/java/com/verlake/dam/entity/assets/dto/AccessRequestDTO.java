package com.verlake.dam.entity.assets.dto;

import com.verlake.dam.entity.assets.AccessLevelObject;
import com.verlake.dam.entity.assets.AccessRequest;
import lombok.Data;

import java.util.List;

@Data
public class AccessRequestDTO {
    private Long requestId;
    private Long assetId;
    private List<AccessLevelObject> accessLevelObjects;
    private String requestReason;
    private String rejectReason;
    private AccessRequest accessRequest;
    private Integer expirationHours;

} 