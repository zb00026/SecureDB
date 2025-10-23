package com.verlake.dam.entity.assets.dto;

import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.enums.ApprovalStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Summary DTO for AccessRequest that can be safely included in AssetDTO
 * without causing circular references
 */
@Data
@Builder
public class AccessRequestSummaryDTO {
    private Long id;
    private String accessSql;
    private String requestReason;
    private String rejectReason;
    private ApprovalStatus developerApproverStatus;
    private ApprovalStatus assetApproverStatus;
    private LocalDateTime requestTime;
    private LocalDateTime expiryDate;
    private Integer expiryHours;
    private Boolean isTempPassword;
    
    // Unix access request fields
    private String requestedUsername;
    private String publicKey;
    
    /**
     * Convert AccessRequest entity to summary DTO without AssetDTO
     */
    public static AccessRequestSummaryDTO fromEntity(AccessRequest accessRequest) {
        if (accessRequest == null) {
            return null;
        }
        
        return AccessRequestSummaryDTO.builder()
                .id(accessRequest.getId())
                .accessSql(accessRequest.getAccessSql())
                .requestReason(accessRequest.getRequestReason())
                .rejectReason(accessRequest.getRejectReason())
                .developerApproverStatus(accessRequest.getDeveloperApproverStatus())
                .assetApproverStatus(accessRequest.getAssetApproverStatus())
                .requestTime(accessRequest.getRequestTime())
                .expiryDate(accessRequest.getExpiryDate())
                .expiryHours(accessRequest.getExpiryHours())
                .isTempPassword(accessRequest.getIsTempPassword())
                .requestedUsername(accessRequest.getRequestedUsername())
                .publicKey(accessRequest.getPublicKey())
                .build();
    }
}

