package com.verlake.dam.service.ai;

import com.verlake.dam.entity.ai.AISensitivePattern;
import com.verlake.dam.entity.ai.FieldSuggestion;
import com.verlake.dam.exception.AISensitivePatternException;
import com.verlake.dam.repository.ai.AISensitivePatternRepository;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@Transactional
public class AISensitivePatternService {
    
    private final AISensitivePatternRepository patternRepository;
    private final UserService userService;
    
    public AISensitivePatternService(AISensitivePatternRepository patternRepository, UserService userService) {
        this.patternRepository = patternRepository;
        this.userService = userService;
    }
    
    /**
     * Get all active patterns
     */
    @Transactional(readOnly = true)
    public List<AISensitivePattern> getAllActivePatterns() {
        return patternRepository.findByIsActiveTrueOrderByPatternCategoryAscPatternNameAsc();
    }
    
    /**
     * Get patterns by category
     */
    @Transactional(readOnly = true)
    public List<AISensitivePattern> getPatternsByCategory(String category) {
        return patternRepository.findByPatternCategoryAndIsActiveTrueOrderByPatternName(category);
    }
    
    /**
     * Get patterns by sensitivity level
     */
    @Transactional(readOnly = true)
    public List<AISensitivePattern> getPatternsBySensitivityLevel(String sensitivityLevel) {
        return patternRepository.findBySensitivityLevelAndIsActiveTrueOrderByPatternName(sensitivityLevel);
    }
    
    /**
     * Get patterns by masking strategy
     */
    @Transactional(readOnly = true)
    public List<AISensitivePattern> getPatternsByMaskingStrategy(String maskingStrategy) {
        return patternRepository.findByMaskingStrategyAndIsActiveTrueOrderByPatternName(maskingStrategy);
    }
    
    /**
     * Search patterns
     */
    @Transactional(readOnly = true)
    public List<AISensitivePattern> searchPatterns(String searchTerm) {
        return patternRepository.searchActivePatterns(searchTerm);
    }
    
    /**
     * Get pattern by ID
     */
    @Transactional(readOnly = true)
    public Optional<AISensitivePattern> getPatternById(Long id) {
        return patternRepository.findById(id);
    }
    
    /**
     * Get pattern by key
     */
    @Transactional(readOnly = true)
    public Optional<AISensitivePattern> getPatternByKey(String patternKey) {
        return patternRepository.findByPatternKeyAndIsActiveTrue(patternKey);
    }
    
    /**
     * Create new pattern
     */
    public AISensitivePattern createPattern(AISensitivePattern pattern) {
        // Check if pattern key already exists
        if (patternRepository.existsByPatternKey(pattern.getPatternKey())) {
            throw new AISensitivePatternException("Pattern key already exists: " + pattern.getPatternKey());
        }
        
        pattern.setCreatedBy(getCurrentUserEmail());
        pattern.setCreatedAt(LocalDateTime.now());
        pattern.setUpdatedAt(LocalDateTime.now());
        pattern.setIsActive(true);
        pattern.setVersion(1);
        
        log.info("Creating new sensitive pattern: {} by {}", pattern.getPatternKey(), getCurrentUserEmail());
        return patternRepository.save(pattern);
    }
    
    /**
     * Update existing pattern
     */
    public AISensitivePattern updatePattern(Long id, AISensitivePattern updatedPattern) {
        AISensitivePattern existingPattern = patternRepository.findById(id)
            .orElseThrow(() -> new AISensitivePatternException(Constants.ERROR_PATTERN_NOT_FOUND_WITH_ID + id));
        
        // Update fields
        existingPattern.setPatternName(updatedPattern.getPatternName());
        existingPattern.setPatternDescription(updatedPattern.getPatternDescription());
        existingPattern.setPatternRegex(updatedPattern.getPatternRegex());
        existingPattern.setMaskingStrategy(updatedPattern.getMaskingStrategy());
        existingPattern.setDetectionReason(updatedPattern.getDetectionReason());
        existingPattern.setSensitivityLevel(updatedPattern.getSensitivityLevel());
        existingPattern.setPatternCategory(updatedPattern.getPatternCategory());
        existingPattern.setConfidenceScore(updatedPattern.getConfidenceScore());
        existingPattern.setPreserveChars(updatedPattern.getPreserveChars());
        existingPattern.setMaskChar(updatedPattern.getMaskChar());
        existingPattern.setSampleValue(updatedPattern.getSampleValue());
        existingPattern.setUpdatedBy(getCurrentUserEmail());
        existingPattern.setUpdatedAt(LocalDateTime.now());
        
        // Increment version if content changed
        if (!existingPattern.getPatternRegex().equals(updatedPattern.getPatternRegex()) ||
            !existingPattern.getMaskingStrategy().equals(updatedPattern.getMaskingStrategy())) {
            existingPattern.setVersion(existingPattern.getVersion() + 1);
        }
        
        log.info("Updating pattern: {} by {}", existingPattern.getPatternKey(), getCurrentUserEmail());
        return patternRepository.save(existingPattern);
    }
    
    /**
     * Activate/deactivate pattern
     */
    public AISensitivePattern togglePatternStatus(Long id, boolean isActive) {
        AISensitivePattern pattern = patternRepository.findById(id)
            .orElseThrow(() -> new AISensitivePatternException(Constants.ERROR_PATTERN_NOT_FOUND_WITH_ID + id));
        
        pattern.setIsActive(isActive);
        pattern.setUpdatedBy(getCurrentUserEmail());
        pattern.setUpdatedAt(LocalDateTime.now());
        
        log.info("Setting pattern {} status to {} by {}", pattern.getPatternKey(), isActive, getCurrentUserEmail());
        return patternRepository.save(pattern);
    }
    
    /**
     * Delete pattern (soft delete by setting inactive)
     */
    public void deletePattern(Long id) {
        togglePatternStatus(id, false);
    }
    
    /**
     * Get all versions of a pattern
     */
    @Transactional(readOnly = true)
    public List<AISensitivePattern> getPatternVersions(String patternKey) {
        return patternRepository.findByPatternKeyOrderByVersionDesc(patternKey);
    }
    
    /**
     * Generate field suggestions based on database patterns
     */
    @Transactional(readOnly = true)
    public List<FieldSuggestion> generateFieldSuggestions(String schemaInfo) {
        List<FieldSuggestion> suggestions = new ArrayList<>();
        List<AISensitivePattern> patterns = getAllActivePatterns();
        
        String lowerSchema = schemaInfo.toLowerCase();
        
        for (AISensitivePattern pattern : patterns) {
            // Check if the pattern regex matches any part of the schema
            if (lowerSchema.contains(pattern.getPatternRegex().toLowerCase())) {
                FieldSuggestion.SensitivityLevel sensitivityLevel = 
                    FieldSuggestion.SensitivityLevel.valueOf(pattern.getSensitivityLevel());
                
                FieldSuggestion suggestion = FieldSuggestion.builder()
                        .tableName("detected_table")
                        .fieldName("detected_" + pattern.getPatternKey())
                        .dataType("VARCHAR")
                        .suggestedStrategy(pattern.getMaskingStrategy())
                        .confidence(pattern.getConfidenceScore())
                        .reason(pattern.getDetectionReason())
                        .sampleValue(pattern.getSampleValue() != null ? pattern.getSampleValue() : "***")
                        .detectionMethod("database_pattern")
                        .maskingPattern("***")
                        .preserveChars(pattern.getPreserveChars())
                        .maskChar(pattern.getMaskChar())
                        .sensitivityLevel(sensitivityLevel)
                        .build();
                
                suggestions.add(suggestion);
            }
        }
        
        return suggestions;
    }
    

    
    /**
     * Bulk update patterns (for centralized management)
     */
    public List<AISensitivePattern> bulkUpdatePatterns(List<AISensitivePattern> patterns) {
        List<AISensitivePattern> updatedPatterns = new ArrayList<>();
        
        for (AISensitivePattern pattern : patterns) {
            try {
                if (pattern.getId() != null) {
                    // Update existing pattern
                    AISensitivePattern existing = patternRepository.findById(pattern.getId())
                        .orElseThrow(() -> new IllegalArgumentException("Pattern not found with id: " + pattern.getId()));
                    
                    existing.setPatternName(pattern.getPatternName());
                    existing.setPatternDescription(pattern.getPatternDescription());
                    existing.setPatternRegex(pattern.getPatternRegex());
                    existing.setMaskingStrategy(pattern.getMaskingStrategy());
                    existing.setDetectionReason(pattern.getDetectionReason());
                    existing.setSensitivityLevel(pattern.getSensitivityLevel());
                    existing.setPatternCategory(pattern.getPatternCategory());
                    existing.setConfidenceScore(pattern.getConfidenceScore());
                    existing.setPreserveChars(pattern.getPreserveChars());
                    existing.setMaskChar(pattern.getMaskChar());
                    existing.setSampleValue(pattern.getSampleValue());
                    existing.setUpdatedBy(getCurrentUserEmail());
                    existing.setUpdatedAt(LocalDateTime.now());
                    existing.setVersion(existing.getVersion() + 1);
                    
                    updatedPatterns.add(patternRepository.save(existing));
                } else {
                    // Create new pattern
                    pattern.setCreatedBy(getCurrentUserEmail());
                    pattern.setCreatedAt(LocalDateTime.now());
                    pattern.setUpdatedAt(LocalDateTime.now());
                    pattern.setIsActive(true);
                    pattern.setVersion(1);
                    
                    updatedPatterns.add(patternRepository.save(pattern));
                }
            } catch (Exception e) {
                log.error("Error updating pattern {}: {}", pattern.getPatternKey(), e.getMessage());
                throw new AISensitivePatternException("Failed to update pattern: " + pattern.getPatternKey(), e);
            }
        }
        
        log.info("Bulk updated {} patterns by {}", updatedPatterns.size(), getCurrentUserEmail());
        return updatedPatterns;
    }
    
    private String getCurrentUserEmail() {
        try {
            return userService.getCurrentUser().getEmail();
        } catch (Exception e) {
            log.warn(Constants.ERROR_COULD_NOT_GET_CURRENT_USER_EMAIL, e.getMessage());
            return Constants.ERROR_SYSTEM_SENDER;
        }
    }
}
