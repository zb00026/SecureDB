package com.verlake.dam.controller.admin;

import com.verlake.dam.entity.ai.AICategory;
import com.verlake.dam.exception.AICategoryException;
import com.verlake.dam.service.ai.AICategoryService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai-categories")
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AICategoryController {
    
    private final AICategoryService categoryService;
    
    @Autowired
    public AICategoryController(AICategoryService categoryService) {
        this.categoryService = categoryService;
    }
    
    /**
     * Get all active categories
     */
    @GetMapping
    public ResponseEntity<List<AICategory>> getAllCategories() {
        try {
            List<AICategory> categories = categoryService.getAllActiveCategories();
            return ResponseEntity.ok(categories);
        } catch (AICategoryException e) {
            log.error("Failed to get categories: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected error getting categories: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get category by key
     */
    @GetMapping("/{categoryKey}")
    public ResponseEntity<AICategory> getCategoryByKey(@PathVariable String categoryKey) {
        try {
            return categoryService.getCategoryByKey(categoryKey)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
        } catch (AICategoryException e) {
            log.error("Failed to get category by key {}: {}", categoryKey, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected error getting category by key {}: {}", categoryKey, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Create a new category
     */
    @PostMapping
    public ResponseEntity<AICategory> createCategory(@RequestBody AICategory category) {
        try {
            AICategory createdCategory = categoryService.createCategory(category);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdCategory);
        } catch (AICategoryException e) {
            log.error("Failed to create category: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error creating category: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Update an existing category
     */
    @PutMapping("/{id}")
    public ResponseEntity<AICategory> updateCategory(@PathVariable Long id, @RequestBody AICategory categoryDetails) {
        try {
            AICategory updatedCategory = categoryService.updateCategory(id, categoryDetails);
            return ResponseEntity.ok(updatedCategory);
        } catch (AICategoryException e) {
            log.error("Failed to update category {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error updating category {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Delete a category (soft delete)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteCategory(@PathVariable Long id) {
        try {
            categoryService.deleteCategory(id);
            return CommonUtils.getSuccessResponse();
        } catch (AICategoryException e) {
            log.error("Failed to delete category {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of(Constants.JSON_FIELD_STATUS, Constants.JSON_FIELD_ERROR, Constants.JSON_FIELD_MESSAGE, e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error deleting category {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(Constants.JSON_FIELD_STATUS, Constants.JSON_FIELD_ERROR, Constants.JSON_FIELD_MESSAGE, "Failed to delete category"));
        }
    }
    
    /**
     * Infer category from context (for testing)
     */
    @PostMapping("/infer")
    public ResponseEntity<AICategory> inferCategoryFromContext(@RequestBody Map<String, String> request) {
        try {
            String userContext = request.get("userContext");
            if (userContext == null || userContext.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            AICategory category = categoryService.inferCategoryFromContext(userContext);
            return ResponseEntity.ok(category);
        } catch (AICategoryException e) {
            log.error("Failed to infer category from context: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            log.error("Unexpected error inferring category from context: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
