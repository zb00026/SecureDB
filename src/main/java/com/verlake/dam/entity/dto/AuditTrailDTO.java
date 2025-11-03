package com.verlake.dam.entity.dto;

import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.utils.SpringContext;
import com.verlake.dam.utils.TimezoneConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for AuditTrail to avoid Hibernate proxy serialization issues
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditTrailDTO {
    private Long id;
    private String timestamp; // Converted to system timezone string
    private String instanceId;
    private String user;
    private String action;
    private String previousValue;
    private String newValue;
    private String actionMetadata;
    private boolean synced;
    private String ipAddress;
    private Long assetId; // Only the ID, not the full Asset object
    private String description; // Simple description stored in DB
    private String readableDescription; // Human-readable description stored in DB

    /**
     * Convert AuditTrail entity to DTO
     */
    public static AuditTrailDTO fromEntity(AuditTrail auditTrail) {
        TimezoneConverter timezoneConverter = SpringContext.getBean(TimezoneConverter.class);
        
        return AuditTrailDTO.builder()
                .id(auditTrail.getId())
                .timestamp(timezoneConverter.convertToSystemTimezoneString(auditTrail.getTimestamp()))
                .instanceId(auditTrail.getInstanceId())
                .user(auditTrail.getUser())
                .action(auditTrail.getAction())
                .previousValue(auditTrail.getPreviousValue())
                .newValue(auditTrail.getNewValue())
                .actionMetadata(auditTrail.getActionMetadata())
                .synced(auditTrail.isSynced())
                .ipAddress(auditTrail.getIpAddress())
                .assetId(auditTrail.getAsset() != null ? auditTrail.getAsset().getId() : null)
                .description(auditTrail.getDescription())
                .readableDescription(buildReadableDescription(auditTrail, timezoneConverter))
                .build();
    }

    private static String buildReadableDescription(AuditTrail auditTrail, com.verlake.dam.utils.TimezoneConverter timezoneConverter) {
        String base = com.verlake.dam.utils.AuditDescriptionUtils.generateReadableBase(
                auditTrail.getAction(),
                auditTrail.getActionMetadata(),
                auditTrail.getInstanceId(),
                auditTrail.getUser()
        );
        String timeStr = timezoneConverter.convertToSystemTimezoneString(auditTrail.getTimestamp());
        return base + " at " + timeStr;
    }
} 