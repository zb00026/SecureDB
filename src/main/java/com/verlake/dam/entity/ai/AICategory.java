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
import java.util.List;

@Entity
@Table(name = "ai_categories")
@Audited(entity = "AI_CATEGORY")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AICategory extends BaseAIEntity {
    
    @Column(name = "category_key", nullable = false, unique = true, length = 100)
    private String categoryKey;
    
    @Column(name = "category_name", nullable = false, length = 200)
    private String categoryName;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "keywords", columnDefinition = "TEXT")
    private String keywords; // Comma-separated keywords for detection
    
    @Column(name = "field_patterns", columnDefinition = "TEXT")
    private String fieldPatterns; // Comma-separated field name patterns
    
    @Column(name = "sensitivity_level", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private SensitivityLevel sensitivityLevel;
    
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
    
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Integer priority = 0; // Higher number = higher priority
    
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AISensitivePattern> patterns;
    
    public enum SensitivityLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    /**
     * Get keywords as a list
     */
    public List<String> getKeywordsList() {
        if (keywords == null || keywords.trim().isEmpty()) {
            return List.of();
        }
        return List.of(keywords.split(","));
    }
    
    /**
     * Get field patterns as a list
     */
    public List<String> getFieldPatternsList() {
        if (fieldPatterns == null || fieldPatterns.trim().isEmpty()) {
            return List.of();
        }
        return List.of(fieldPatterns.split(","));
    }
}
