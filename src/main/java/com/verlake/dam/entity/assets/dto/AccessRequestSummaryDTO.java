package com.verlake.dam.entity.assets.dto;

import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.enums.ApprovalStatus;
import com.verlake.dam.utils.SpringContext;
import com.verlake.dam.utils.TimezoneConverter;
import lombok.Builder;
import lombok.Data;

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
    private ApprovalStatus accessorApproverStatus;
    private ApprovalStatus assetApproverStatus;
    private String requestTime; // Converted to system timezone string
    private String expiryDate; // Converted to system timezone string
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
        
        TimezoneConverter timezoneConverter = SpringContext.getBean(TimezoneConverter.class);
        
        return AccessRequestSummaryDTO.builder()
                .id(accessRequest.getId())
                .accessSql(accessRequest.getAccessSql())
                .requestReason(accessRequest.getRequestReason())
                .rejectReason(accessRequest.getRejectReason())
                .accessorApproverStatus(accessRequest.getAccessorApproverStatus())
                .assetApproverStatus(accessRequest.getAssetApproverStatus())
                .requestTime(timezoneConverter.convertToSystemTimezoneString(accessRequest.getRequestTime()))
                .expiryDate(timezoneConverter.convertToSystemTimezoneString(accessRequest.getExpiryDate()))
                .expiryHours(accessRequest.getExpiryHours())
                .isTempPassword(accessRequest.getIsTempPassword())
                .requestedUsername(accessRequest.getRequestedUsername())
                .publicKey(accessRequest.getPublicKey())
                .build();
    }
}

