package com.verlake.dam.entity.assets.dto;

import lombok.Data;

/**
 * DTO for natural language to SQL conversion request
 */
@Data
public class NaturalLanguageQueryDTO {
    /**
     * Natural language query (e.g., "get me a count of all employees")
     */
    private String naturalLanguageQuery;
    
    /**
     * Asset ID (required for asset owners)
     */
    private Long assetId;
    
    /**
     * Access request ID (required for developers)
     */
    private Long requestId;
}


