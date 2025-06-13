package com.verlake.dam.controller.common;

import com.verlake.dam.entity.assets.dto.AssetAccessDTO;
import com.verlake.dam.service.assets.AssetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;

/**
 * Base controller providing common asset access functionality
 * to eliminate code duplication across admin, asset owner, and developer controllers
 */
@Slf4j
public abstract class BaseAssetAccessController {
    
    protected final AssetService assetService;
    
    protected BaseAssetAccessController(AssetService assetService) {
        this.assetService = assetService;
    }
    
    /**
     * Get real-time user access information for an asset by querying the target database directly
     * This method is shared across admin, asset owner, and developer controllers
     * 
     * @param id The asset ID
     * @return ResponseEntity containing AssetAccessDTO or error response
     */
    protected ResponseEntity<?> getAssetAccess(Long id) {
        try {
            AssetAccessDTO accessInfo = assetService.getAssetAccess(id);
            return ResponseEntity.ok(accessInfo);
        } catch (RuntimeException e) {
            // Handle access denied scenarios
            if (e.getCause() instanceof AccessDeniedException ||
                e.getMessage().contains("User has no access to asset")) {
                Map<String, String> errorResponse = Map.of("error", "User has no access to asset");
                return ResponseEntity.status(403).body(errorResponse);
            }
            
            // Log the error for debugging
            log.error("Error fetching asset access for asset ID: {}", id, e);
            
            // Return appropriate error response based on controller type
            return handleGenericError(e);
        }
    }
    
    /**
     * Handle generic errors - can be overridden by subclasses for specific error handling
     * 
     * @param e The exception that occurred
     * @return ResponseEntity with appropriate error response
     */
    protected ResponseEntity<?> handleGenericError(RuntimeException e) {
        // Default implementation returns 500 with error message
        Map<String, String> errorResponse = Map.of("error", "Failed to fetch asset access information");
        return ResponseEntity.status(500).body(errorResponse);
    }
} 