package com.verlake.dam.entity.dto;

import com.verlake.dam.entity.AuditTrail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for AuditTrail to avoid Hibernate proxy serialization issues
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditTrailDTO {
    private Long id;
    private LocalDateTime timestamp;
    private String instanceId;
    private String user;
    private String action;
    private String previousValue;
    private String newValue;
    private String actionMetadata;
    private boolean synced;
    private String ipAddress;
    private Long assetId; // Only the ID, not the full Asset object

    /**
     * Convert AuditTrail entity to DTO
     */
    public static AuditTrailDTO fromEntity(AuditTrail auditTrail) {
        return AuditTrailDTO.builder()
                .id(auditTrail.getId())
                .timestamp(auditTrail.getTimestamp())
                .instanceId(auditTrail.getInstanceId())
                .user(auditTrail.getUser())
                .action(auditTrail.getAction())
                .previousValue(auditTrail.getPreviousValue())
                .newValue(auditTrail.getNewValue())
                .actionMetadata(auditTrail.getActionMetadata())
                .synced(auditTrail.isSynced())
                .ipAddress(auditTrail.getIpAddress())
                .assetId(auditTrail.getAsset() != null ? auditTrail.getAsset().getId() : null)
                .build();
    }
} 