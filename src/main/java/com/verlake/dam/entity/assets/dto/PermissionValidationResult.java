package com.verlake.dam.entity.assets.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result class for permission validation operations
 * Contains information about whether a user has sufficient permissions
 * and any warnings or missing permissions identified
 */
@Data
@NoArgsConstructor
public class PermissionValidationResult {
    
    /**
     * Whether the user has sufficient permissions
     */
    private boolean sufficient;
    
    /**
     * Main warning message describing permission issues
     */
    private String warningMessage;
    
    /**
     * List of specific warnings identified during validation
     */
    private List<String> warnings;
    
    /**
     * Constructor for creating a result with just sufficient status and warning message
     */
    public PermissionValidationResult(boolean sufficient, String warningMessage) {
        this.sufficient = sufficient;
        this.warningMessage = warningMessage;
        this.warnings = List.of();
    }
    
    /**
     * Constructor for creating a result with sufficient status, warning message, and warnings list
     */
    public PermissionValidationResult(boolean sufficient, String warningMessage, List<String> warnings) {
        this.sufficient = sufficient;
        this.warningMessage = warningMessage;
        this.warnings = warnings != null ? warnings : List.of();
    }
    
    /**
     * Check if there are any warnings
     */
    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }
    
    /**
     * Get the number of warnings
     */
    public int getWarningCount() {
        return warnings != null ? warnings.size() : 0;
    }
}
