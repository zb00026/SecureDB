package com.verlake.dam.entity.assets.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.verlake.dam.enums.ApprovalStatus;
import lombok.Data;

@Data
public class AccessQueryDTO {
    private Long requestId;
    private Long assetId;
    private String query;
    private String ticketReference;
    private String changeDescription;
    private ApprovalStatus approvalStatus;
    private String rejectReason;
    
    @JsonProperty("isChangeRequest")
    private boolean isChangeRequest;
} 