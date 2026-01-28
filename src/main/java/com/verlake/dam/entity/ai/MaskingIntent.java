package com.verlake.dam.entity.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MaskingIntent {
    
    private String intentType; // "mask_pii", "mask_credit_cards", "mask_names", "custom"
    private List<String> targetFields;
    private List<String> targetTables;
    private String maskingStrategy; // "partial", "full", "hash", "tokenize", "format_preserving"
    private String userRole; // "admin", "user", "all", "non-admin"
    private double confidence;
    private String originalRequest;
    private String reasoning;
    private Map<String, Object> additionalParameters;
    
    // Specific masking parameters
    private Integer preserveChars; // For partial masking
    private String maskChar; // Character to use for masking (default: *)
    private String pattern; // For format-preserving masking
    private Boolean caseSensitive;
    
    // Validation flags
    private boolean requiresConfirmation;
    private boolean hasAmbiguity;
    private String clarificationNeeded;
} 