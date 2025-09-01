package com.verlake.dam.entity.ai;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.verlake.dam.enums.SensitiveCategory;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldSuggestion {
    
    private String tableName;
    private String fieldName;
    private String dataType;
    private String suggestedStrategy; // "partial", "full", "hash", "tokenize"
    private double confidence;
    private String reason; // Why this field was identified as sensitive
    private String sampleValue; // Anonymized sample for context
    private String detectionMethod; // "field_name", "data_pattern", "schema_analysis"
    
    // Masking strategy details
    private String maskingPattern;
    private Integer preserveChars;
    private String maskChar;
    
    // Field metadata
    private boolean isPrimaryKey;
    private boolean isNullable;
    private String fieldDescription;
    private SensitivityLevel sensitivityLevel;
    private SensitiveCategory category;
    
    public enum SensitivityLevel {
        LOW,      // Basic personal info
        MEDIUM,   // Financial info
        HIGH,     // Medical/SSN
        CRITICAL  // Passwords, keys
    }

} 