package com.verlake.dam.service.ai;

import com.verlake.dam.entity.ai.AICategory;
import com.verlake.dam.entity.ai.FieldSuggestion;
import com.verlake.dam.exception.AICategoryException;
import com.verlake.dam.repository.ai.AICategoryRepository;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Slf4j
@Transactional
public class AICategoryService {
    
    private final AICategoryRepository categoryRepository;
    
    @Autowired
    public AICategoryService(AICategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }
    
    /**
     * Get all active categories
     */
    public List<AICategory> getAllActiveCategories() {
        try {
            return categoryRepository.findByIsActiveTrueOrderByPriorityDescCategoryNameAsc();
        } catch (Exception e) {
            log.error("Failed to get active categories: {}", e.getMessage(), e);
            throw new AICategoryException("Failed to retrieve active categories", e);
        }
    }
    
    /**
     * Get category by key
     */
    public Optional<AICategory> getCategoryByKey(String categoryKey) {
        try {
            return categoryRepository.findByCategoryKeyAndIsActiveTrue(categoryKey);
        } catch (Exception e) {
            log.error("Failed to get category by key {}: {}", categoryKey, e.getMessage(), e);
            throw new AICategoryException("Failed to retrieve category: " + categoryKey, e);
        }
    }
    
    /**
     * Create a new category
     */
    public AICategory createCategory(AICategory category) {
        try {
            if (categoryRepository.existsByCategoryKey(category.getCategoryKey())) {
                throw new AICategoryException("Category with key '" + category.getCategoryKey() + "' already exists");
            }
            
            category.setVersion(1);
            category.setIsActive(true);
            return categoryRepository.save(category);
        } catch (Exception e) {
            log.error("Failed to create category: {}", e.getMessage(), e);
            throw new AICategoryException("Failed to create category", e);
        }
    }
    
    /**
     * Update an existing category
     */
    public AICategory updateCategory(Long id, AICategory categoryDetails) {
        try {
            AICategory existingCategory = categoryRepository.findById(id)
                .orElseThrow(() -> new AICategoryException("Category not found with id: " + id));
            
            // Increment version
            existingCategory.setVersion(existingCategory.getVersion() + 1);
            
            // Update fields
            existingCategory.setCategoryName(categoryDetails.getCategoryName());
            existingCategory.setDescription(categoryDetails.getDescription());
            existingCategory.setKeywords(categoryDetails.getKeywords());
            existingCategory.setFieldPatterns(categoryDetails.getFieldPatterns());
            existingCategory.setSensitivityLevel(categoryDetails.getSensitivityLevel());
            existingCategory.setPriority(categoryDetails.getPriority());
            existingCategory.setIsActive(categoryDetails.getIsActive());
            
            return categoryRepository.save(existingCategory);
        } catch (Exception e) {
            log.error("Failed to update category {}: {}", id, e.getMessage(), e);
            throw new AICategoryException("Failed to update category", e);
        }
    }
    
    /**
     * Delete a category (soft delete)
     */
    public void deleteCategory(Long id) {
        try {
            AICategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new AICategoryException("Category not found with id: " + id));
            
            category.setIsActive(false);
            categoryRepository.save(category);
        } catch (Exception e) {
            log.error("Failed to delete category {}: {}", id, e.getMessage(), e);
            throw new AICategoryException("Failed to delete category", e);
        }
    }
    
    /**
     * Infer category from user context using database-driven detection
     */
    public AICategory inferCategoryFromContext(String userContext) {
        if (userContext == null || userContext.trim().isEmpty()) {
            return null;
        }
        
        try {
            String normalizedContext = userContext.toLowerCase().trim();
            
            // Get all active categories
            List<AICategory> categories = getAllActiveCategories();
            
            // Find categories that match the context
            List<AICategory> matchingCategories = categories.stream()
                .filter(category -> matchesContext(category, normalizedContext))
                .sorted((a, b) -> {
                    // Sort by priority (higher first), then by sensitivity level
                    int priorityCompare = Integer.compare(b.getPriority(), a.getPriority());
                    if (priorityCompare != 0) return priorityCompare;
                    return b.getSensitivityLevel().compareTo(a.getSensitivityLevel());
                })
                .toList();
            
            return matchingCategories.isEmpty() ? null : matchingCategories.get(0);
        } catch (Exception e) {
            log.warn("Error inferring category from context: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Filter suggestions by category using database-driven detection
     */
    public List<FieldSuggestion> filterByCategory(List<FieldSuggestion> suggestions, AICategory category) {
        if (suggestions == null || category == null) {
            return new ArrayList<>();
        }
        
        return suggestions.stream()
            .filter(suggestion -> matchesFieldPattern(category, suggestion.getFieldName()))
            .toList();
    }
    
    /**
     * Filter suggestions by category using heuristics for AI-sourced suggestions
     */
    public List<FieldSuggestion> filterByCategoryHeuristics(List<FieldSuggestion> suggestions, AICategory category) {
        if (suggestions == null || category == null) {
            return new ArrayList<>();
        }
        
        return suggestions.stream()
            .filter(suggestion -> {
                String fieldName = suggestion.getFieldName() != null ? suggestion.getFieldName().toLowerCase() : "";
                return matchesFieldPattern(category, fieldName);
            })
            .toList();
    }
    
    /**
     * Check if category matches the given context
     */
    private boolean matchesContext(AICategory category, String normalizedContext) {
        List<String> keywords = category.getKeywordsList();
        if (keywords.isEmpty()) {
            return false;
        }
        
        return keywords.stream()
            .anyMatch(keyword -> normalizedContext.contains(keyword.toLowerCase().trim()));
    }
    
    /**
     * Check if field name matches category patterns
     */
    private boolean matchesFieldPattern(AICategory category, String fieldName) {
        if (fieldName == null || fieldName.trim().isEmpty()) {
            return false;
        }
        
        List<String> patterns = category.getFieldPatternsList();
        if (patterns.isEmpty()) {
            return false;
        }
        
        String normalizedFieldName = fieldName.toLowerCase().trim();
        return patterns.stream()
            .anyMatch(pattern -> normalizedFieldName.contains(pattern.toLowerCase().trim()));
    }
}
