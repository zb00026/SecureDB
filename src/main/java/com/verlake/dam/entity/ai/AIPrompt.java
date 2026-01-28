package com.verlake.dam.entity.ai;

import com.verlake.dam.annotation.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "ai_prompts")
@Audited(entity = "AI_PROMPT")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AIPrompt extends BaseAIEntity {
    
    @Column(name = "prompt_key", nullable = false, unique = true, length = 100)
    private String promptKey;
    
    @Column(name = "prompt_name", nullable = false, length = 200)
    private String promptName;
    
    @Column(name = "prompt_description", length = 500)
    private String promptDescription;
    
    @Column(name = "prompt_content", nullable = false, columnDefinition = "TEXT")
    private String promptContent;
    
    @Column(name = "prompt_category", length = 50)
    private String promptCategory; // WELCOME, INTENT_ANALYSIS, FIELD_SUGGESTION, etc.
    
    @Column(name = "is_active", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 1")
    @Builder.Default
    private Boolean isActive = true;
}
