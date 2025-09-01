package com.verlake.dam.entity.ai;

import com.verlake.dam.annotation.Audited;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_sensitive_patterns")
@Audited(entity = "AI_SENSITIVE_PATTERN")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AISensitivePattern extends BaseAIEntity {
    
    @Column(name = "pattern_key", nullable = false, unique = true, length = 100)
    private String patternKey;
    
    @Column(name = "pattern_name", nullable = false, length = 200)
    private String patternName;
    
    @Column(name = "pattern_description", length = 500)
    private String patternDescription;
    
    @Column(name = "pattern_regex", nullable = false, length = 500)
    private String patternRegex;
    
    @Column(name = "masking_strategy", nullable = false, length = 50)
    private String maskingStrategy; // "partial", "full", "hash", etc.
    
    @Column(name = "detection_reason", nullable = false, length = 200)
    private String detectionReason;
    
    @Column(name = "sensitivity_level", nullable = false, length = 20)
    private String sensitivityLevel; // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    
    @Column(name = "pattern_category", length = 50)
    private String patternCategory; // "EMAIL", "SSN", "CREDIT_CARD", "NAME", etc.
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private AICategory category;
    
    @Column(name = "is_active", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    @Builder.Default
    private Boolean isActive = true;
    
    @Column(name = "confidence_score", nullable = false)
    @Builder.Default
    private Double confidenceScore = 0.7;
    
    @Column(name = "preserve_chars", nullable = false)
    @Builder.Default
    private Integer preserveChars = 4;
    
    @Column(name = "mask_char", nullable = false, length = 10)
    @Builder.Default
    private String maskChar = "*";
    
    @Column(name = "sample_value", length = 100)
    private String sampleValue;
}
