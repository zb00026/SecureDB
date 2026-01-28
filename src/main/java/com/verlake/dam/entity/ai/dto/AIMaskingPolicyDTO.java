package com.verlake.dam.entity.ai.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.verlake.dam.entity.ai.AIMaskingPolicy;
import com.verlake.dam.entity.assets.dto.AssetDTO;
import com.verlake.dam.entity.user.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIMaskingPolicyDTO {
    
    private Long id;
    private String originalRequest;
    private String intentType;
    private String tableName;
    private String fieldName;
    private String maskingStrategy;
    private String maskingPattern;
    private Integer preserveChars;
    private String maskChar;
    private String targetRole;
    private List<String> roles; // Parsed from targetRole
    private Double aiConfidence;
    private String aiReasoning;
    private Boolean userConfirmed;
    private Boolean isActive;
    private AssetDTO asset;
    private User createdBy;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime appliedAt;
    
    /**
     * Convert AIMaskingPolicy entity to DTO
     */
    public static AIMaskingPolicyDTO fromEntity(AIMaskingPolicy policy) {
        if (policy == null) {
            return null;
        }
        
        return AIMaskingPolicyDTO.builder()
                .id(policy.getId())
                .originalRequest(policy.getOriginalRequest())
                .intentType(policy.getIntentType())
                .tableName(policy.getTableName())
                .fieldName(policy.getFieldName())
                .maskingStrategy(policy.getMaskingStrategy())
                .maskingPattern(policy.getMaskingPattern())
                .preserveChars(policy.getPreserveChars())
                .maskChar(policy.getMaskChar())
                .targetRole(policy.getTargetRole())
                .roles(parseRolesFromTargetRole(policy.getTargetRole()))
                .aiConfidence(policy.getAiConfidence())
                .aiReasoning(policy.getAiReasoning())
                .userConfirmed(policy.getUserConfirmed())
                .isActive(policy.getIsActive())
                .asset(AssetDTO.fromEntity(policy.getAsset()))
                .createdBy(policy.getCreatedBy())
                .createdAt(policy.getCreatedAt())
                .updatedAt(policy.getUpdatedAt())
                .appliedAt(policy.getAppliedAt())
                .build();
    }
    
    /**
     * Parse roles from targetRole field (comma-separated string)
     */
    private static List<String> parseRolesFromTargetRole(String targetRole) {
        if (targetRole == null || targetRole.trim().isEmpty()) {
            return List.of();
        }
        
        // Use simple comma split and trim each element to avoid ReDoS vulnerability
        return Arrays.stream(targetRole.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
