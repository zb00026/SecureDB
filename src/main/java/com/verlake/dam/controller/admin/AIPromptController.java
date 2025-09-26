package com.verlake.dam.controller.admin;

import com.verlake.dam.entity.ai.AIPrompt;
import com.verlake.dam.exception.AIPromptException;
import com.verlake.dam.service.ai.AIPromptService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/ai-prompts")
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AIPromptController {
    
    private final AIPromptService promptService;
    
    public AIPromptController(AIPromptService promptService) {
        this.promptService = promptService;
    }
    
    /**
     * Get all active prompts
     */
    @GetMapping
    public ResponseEntity<List<AIPrompt>> getAllPrompts() {
        try {
            List<AIPrompt> prompts = promptService.getAllActivePrompts();
            return ResponseEntity.ok(prompts);
        } catch (AIPromptException e) {
            log.error("AI Prompt error fetching all prompts: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error fetching all prompts: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get prompts by category
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<List<AIPrompt>> getPromptsByCategory(@PathVariable String category) {
        try {
            List<AIPrompt> prompts = promptService.getPromptsByCategory(category);
            return ResponseEntity.ok(prompts);
        } catch (AIPromptException e) {
            log.error("AI Prompt error fetching prompts by category '{}': {}", category, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error fetching prompts by category '{}': {}", category, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get prompt by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<AIPrompt> getPromptById(@PathVariable Long id) {
        try {
            Optional<AIPrompt> prompt = promptService.getPromptById(id);
            return prompt.map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build());
        } catch (AIPromptException e) {
            log.error("AI Prompt error fetching prompt by id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error fetching prompt by id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Search prompts
     */
    @GetMapping("/search")
    public ResponseEntity<List<AIPrompt>> searchPrompts(@RequestParam String query) {
        try {
            List<AIPrompt> prompts = promptService.searchPrompts(query);
            return ResponseEntity.ok(prompts);
        } catch (AIPromptException e) {
            log.error("AI Prompt error searching prompts with query '{}': {}", query, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error searching prompts with query '{}': {}", query, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get all versions of a prompt
     */
    @GetMapping("/versions/{promptKey}")
    public ResponseEntity<List<AIPrompt>> getPromptVersions(@PathVariable String promptKey) {
        try {
            List<AIPrompt> versions = promptService.getPromptVersions(promptKey);
            return ResponseEntity.ok(versions);
        } catch (AIPromptException e) {
            log.error("AI Prompt error fetching prompt versions for key '{}': {}", promptKey, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error fetching prompt versions for key '{}': {}", promptKey, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Create new prompt
     */
    @PostMapping
    public ResponseEntity<AIPrompt> createPrompt(@Valid @RequestBody AIPrompt prompt) {
        try {
            AIPrompt createdPrompt = promptService.createPrompt(prompt);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdPrompt);
        } catch (IllegalArgumentException e) {
            log.error("Invalid prompt data: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (AIPromptException e) {
            log.error("AI Prompt error creating prompt: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error creating prompt: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Update existing prompt
     */
    @PutMapping("/{id}")
    public ResponseEntity<AIPrompt> updatePrompt(@PathVariable Long id, 
                                                @Valid @RequestBody AIPrompt prompt) {
        try {
            AIPrompt updatedPrompt = promptService.updatePrompt(id, prompt);
            return ResponseEntity.ok(updatedPrompt);
        } catch (IllegalArgumentException e) {
            log.error("Invalid prompt data for id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (AIPromptException e) {
            log.error("AI Prompt error updating prompt with id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error updating prompt with id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Toggle prompt active status
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<AIPrompt> togglePromptStatus(@PathVariable Long id, 
                                                      @RequestBody Map<String, Boolean> statusRequest) {
        try {
            Boolean isActive = statusRequest.get("isActive");
            if (isActive == null) {
                return ResponseEntity.badRequest().build();
            }
            
            AIPrompt updatedPrompt = promptService.togglePromptStatus(id, isActive);
            return ResponseEntity.ok(updatedPrompt);
        } catch (IllegalArgumentException e) {
            log.error("Prompt not found for id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.notFound().build();
        } catch (AIPromptException e) {
            log.error("AI Prompt error toggling status for id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error toggling prompt status for id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Delete prompt (soft delete)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deletePrompt(@PathVariable Long id) {
        try {
            promptService.deletePrompt(id);
            return CommonUtils.getSuccessResponse();
        } catch (IllegalArgumentException e) {
            log.error("Prompt not found for id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(Constants.JSON_FIELD_STATUS, Constants.JSON_FIELD_ERROR, Constants.JSON_FIELD_MESSAGE, "Prompt not found"));
        } catch (AIPromptException e) {
            log.error("AI Prompt error deleting prompt with id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(Constants.JSON_FIELD_STATUS, Constants.JSON_FIELD_ERROR, Constants.JSON_FIELD_MESSAGE, e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error deleting prompt with id {}: {}", id, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(Constants.JSON_FIELD_STATUS, Constants.JSON_FIELD_ERROR, Constants.JSON_FIELD_MESSAGE, "Failed to delete prompt"));
        }
    }
    
    /**
     * Test prompt with parameters
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, String>> testPrompt(@RequestBody Map<String, Object> testRequest) {
        try {
            String promptKey = (String) testRequest.get("promptKey");
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) testRequest.get("parameters");
            
            if (promptKey == null) {
                return ResponseEntity.badRequest().build();
            }
            
            String result = promptService.getPrompt(promptKey, parameters);
            return ResponseEntity.ok(Map.of("result", result));
        } catch (AIPromptException e) {
            log.error("AI Prompt error testing prompt: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            log.error("Unexpected error testing prompt: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
