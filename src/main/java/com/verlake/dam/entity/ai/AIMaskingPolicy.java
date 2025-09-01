package com.verlake.dam.entity.ai;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.entity.Role;
import com.verlake.dam.entity.assets.Asset;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "ai_masking_policies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AIMaskingPolicy {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;
    
    @Column(name = "original_request", columnDefinition = "TEXT")
    @JsonAlias("description")
    private String originalRequest;
    
    @Column(name = "intent_type", length = 50)
    private String intentType;
    
    @Column(name = "table_name", length = 255)
    private String tableName;
    
    @Column(name = "field_name", length = 255)
    private String fieldName;
    
    @Column(name = "masking_strategy", length = 50)
    private String maskingStrategy;
    
    @Column(name = "masking_pattern", length = 255)
    private String maskingPattern;
    
    @Column(name = "preserve_chars")
    private Integer preserveChars;
    
    @Column(name = "mask_char", length = 5)
    private String maskChar;
    
    @Column(name = "target_role", length = 50)
    private String targetRole;
    
    @Column(name = "ai_confidence")
    private Double aiConfidence;
    
    @Column(name = "ai_reasoning", columnDefinition = "TEXT")
    private String aiReasoning;
    
    @Column(name = "user_confirmed")
    @Builder.Default
    private Boolean userConfirmed = false;
    
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = false;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id")
    private Asset asset;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    // Transient inbound-only field to accept roles array from frontend
    @Transient
    @JsonDeserialize(using = RoleStringDeserializer.class)
    private List<Role> roles;
    
    @Column(name = "created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    
    @Column(name = "applied_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime appliedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) {
            isActive = false;
        }
        if (userConfirmed == null) {
            userConfirmed = false;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
} 