package com.verlake.dam.entity.ai;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyPolicyRequest {
    
    @NotBlank(message = "Session ID is required")
    private String sessionId;
    
    @NotNull(message = "Masking intent is required")
    private MaskingIntent intent;
    
    @NotNull(message = "Field suggestions are required")
    private List<FieldSuggestion> suggestions;
}
