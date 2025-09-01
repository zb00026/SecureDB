package com.verlake.dam.controller.admin;

import com.verlake.dam.entity.ai.AISensitivePattern;
import com.verlake.dam.exception.AISensitivePatternException;
import com.verlake.dam.service.ai.AISensitivePatternService;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai-patterns")
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AISensitivePatternController {
    
    private final AISensitivePatternService patternService;
    
    public AISensitivePatternController(AISensitivePatternService patternService) {
        this.patternService = patternService;
    }
    
    /**
     * Get all active patterns
     */
    @GetMapping
    public ResponseEntity<List<AISensitivePattern>> getAllPatterns() {
        try {
            List<AISensitivePattern> patterns = patternService.getAllActivePatterns();
            return ResponseEntity.ok(patterns);
        } catch (AISensitivePatternException e) {
            log.error("AI Sensitive Pattern error fetching all patterns: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error fetching all patterns: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get pattern by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<AISensitivePattern> getPatternById(@PathVariable Long id) {
        try {
            return patternService.getPatternById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (AISensitivePatternException e) {
            log.error("AI Sensitive Pattern error fetching pattern by id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error fetching pattern by id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get pattern by key
     */
    @GetMapping("/key/{patternKey}")
    public ResponseEntity<AISensitivePattern> getPatternByKey(@PathVariable String patternKey) {
        try {
            return patternService.getPatternByKey(patternKey)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (AISensitivePatternException e) {
            log.error("AI Sensitive Pattern error fetching pattern by key '{}': {}", patternKey, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error fetching pattern by key '{}': {}", patternKey, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get patterns by category
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<List<AISensitivePattern>> getPatternsByCategory(@PathVariable String category) {
        try {
            List<AISensitivePattern> patterns = patternService.getPatternsByCategory(category);
            return ResponseEntity.ok(patterns);
        } catch (AISensitivePatternException e) {
            log.error("AI Sensitive Pattern error fetching patterns by category '{}': {}", category, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error fetching patterns by category '{}': {}", category, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get patterns by sensitivity level
     */
    @GetMapping("/sensitivity/{level}")
    public ResponseEntity<List<AISensitivePattern>> getPatternsBySensitivityLevel(@PathVariable String level) {
        try {
            List<AISensitivePattern> patterns = patternService.getPatternsBySensitivityLevel(level);
            return ResponseEntity.ok(patterns);
        } catch (AISensitivePatternException e) {
            log.error("AI Sensitive Pattern error fetching patterns by sensitivity level '{}': {}", level, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error fetching patterns by sensitivity level '{}': {}", level, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get patterns by masking strategy
     */
    @GetMapping("/strategy/{strategy}")
    public ResponseEntity<List<AISensitivePattern>> getPatternsByMaskingStrategy(@PathVariable String strategy) {
        try {
            List<AISensitivePattern> patterns = patternService.getPatternsByMaskingStrategy(strategy);
            return ResponseEntity.ok(patterns);
        } catch (AISensitivePatternException e) {
            log.error("AI Sensitive Pattern error fetching patterns by masking strategy '{}': {}", strategy, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error fetching patterns by masking strategy '{}': {}", strategy, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Search patterns
     */
    @GetMapping("/search")
    public ResponseEntity<List<AISensitivePattern>> searchPatterns(@RequestParam String q) {
        try {
            List<AISensitivePattern> patterns = patternService.searchPatterns(q);
            return ResponseEntity.ok(patterns);
        } catch (AISensitivePatternException e) {
            log.error("AI Sensitive Pattern error searching patterns with query '{}': {}", q, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error searching patterns with query '{}': {}", q, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Create new pattern
     */
    @PostMapping
    public ResponseEntity<AISensitivePattern> createPattern(@RequestBody AISensitivePattern pattern) {
        try {
            AISensitivePattern createdPattern = patternService.createPattern(pattern);
            return ResponseEntity.ok(createdPattern);
        } catch (IllegalArgumentException e) {
            log.error("Invalid pattern data: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (AISensitivePatternException e) {
            log.error("AI Sensitive Pattern error creating pattern: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error creating pattern: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Update existing pattern
     */
    @PutMapping("/{id}")
    public ResponseEntity<AISensitivePattern> updatePattern(@PathVariable Long id, @RequestBody AISensitivePattern pattern) {
        try {
            AISensitivePattern updatedPattern = patternService.updatePattern(id, pattern);
            return ResponseEntity.ok(updatedPattern);
        } catch (IllegalArgumentException e) {
            log.error(Constants.ERROR_PATTERN_NOT_FOUND_FOR_ID, id, e.getMessage(), e);
            return ResponseEntity.notFound().build();
        } catch (AISensitivePatternException e) {
            log.error("AI Sensitive Pattern error updating pattern with id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error updating pattern with id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Toggle pattern status (activate/deactivate)
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<AISensitivePattern> togglePatternStatus(@PathVariable Long id, @RequestParam boolean active) {
        try {
            AISensitivePattern pattern = patternService.togglePatternStatus(id, active);
            return ResponseEntity.ok(pattern);
        } catch (IllegalArgumentException e) {
            log.error(Constants.ERROR_PATTERN_NOT_FOUND_FOR_ID, id, e.getMessage(), e);
            return ResponseEntity.notFound().build();
        } catch (AISensitivePatternException e) {
            log.error("AI Sensitive Pattern error toggling status for id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error toggling pattern status for id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Delete pattern (soft delete)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePattern(@PathVariable Long id) {
        try {
            patternService.deletePattern(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.error(Constants.ERROR_PATTERN_NOT_FOUND_FOR_ID, id, e.getMessage(), e);
            return ResponseEntity.notFound().build();
        } catch (AISensitivePatternException e) {
            log.error("AI Sensitive Pattern error deleting pattern with id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error deleting pattern with id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get pattern versions
     */
    @GetMapping("/{patternKey}/versions")
    public ResponseEntity<List<AISensitivePattern>> getPatternVersions(@PathVariable String patternKey) {
        try {
            List<AISensitivePattern> versions = patternService.getPatternVersions(patternKey);
            return ResponseEntity.ok(versions);
        } catch (AISensitivePatternException e) {
            log.error("AI Sensitive Pattern error fetching pattern versions for key '{}': {}", patternKey, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error fetching pattern versions for key '{}': {}", patternKey, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Bulk update patterns (for centralized management)
     */
    @PostMapping("/bulk-update")
    public ResponseEntity<List<AISensitivePattern>> bulkUpdatePatterns(@RequestBody List<AISensitivePattern> patterns) {
        try {
            List<AISensitivePattern> updatedPatterns = patternService.bulkUpdatePatterns(patterns);
            return ResponseEntity.ok(updatedPatterns);
        } catch (AISensitivePatternException e) {
            log.error("AI Sensitive Pattern error in bulk update: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error in bulk update: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    

    
    /**
     * Generate field suggestions for schema
     */
    @PostMapping("/generate-suggestions")
    public ResponseEntity<List<com.verlake.dam.entity.ai.FieldSuggestion>> generateSuggestions(@RequestParam String schemaInfo) {
        try {
            List<com.verlake.dam.entity.ai.FieldSuggestion> suggestions = patternService.generateFieldSuggestions(schemaInfo);
            return ResponseEntity.ok(suggestions);
        } catch (AISensitivePatternException e) {
            log.error("AI Sensitive Pattern error generating suggestions for schema '{}': {}", schemaInfo, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error generating suggestions for schema '{}': {}", schemaInfo, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
