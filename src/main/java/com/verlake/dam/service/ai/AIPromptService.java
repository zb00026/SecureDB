package com.verlake.dam.service.ai;

import com.verlake.dam.entity.ai.AIPrompt;
import com.verlake.dam.repository.ai.AIPromptRepository;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@Transactional
public class AIPromptService {
    
    private final AIPromptRepository promptRepository;
    private final UserService userService;
    public AIPromptService(AIPromptRepository promptRepository, UserService userService) {
        this.promptRepository = promptRepository;
        this.userService = userService;
    }
    
    /**
     * Get active prompt by key with parameter substitution
     */
    public String getPrompt(String promptKey, Map<String, Object> parameters) {
        Optional<AIPrompt> promptOpt = promptRepository.findByPromptKeyAndIsActiveTrue(promptKey);
        
        if (promptOpt.isEmpty()) {
            log.warn("Prompt not found for key: {}", promptKey);
            return "❌ Error: Prompt not found for key: " + promptKey;
        }
        
        String content = promptOpt.get().getPromptContent();
        
        // Check if prompt content is empty
        if (content == null || content.trim().isEmpty()) {
            log.warn("Prompt content is empty for key: {}", promptKey);
            return "❌ Error: Prompt content is empty for key: " + promptKey;
        }
        
        // Replace parameters in the prompt
        if (parameters != null && !parameters.isEmpty()) {
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                content = content.replace(placeholder, value);
            }
        }
        
        return content;
    }
    
    /**
     * Get active prompt by key without parameters
     */
    public String getPrompt(String promptKey) {
        return getPrompt(promptKey, null);
    }
    
    /**
     * Get all active prompts
     */
    @Transactional(readOnly = true)
    public List<AIPrompt> getAllActivePrompts() {
        return promptRepository.findByIsActiveTrueOrderByPromptCategoryAscPromptNameAsc();
    }
    
    /**
     * Get prompts by category
     */
    @Transactional(readOnly = true)
    public List<AIPrompt> getPromptsByCategory(String category) {
        return promptRepository.findByPromptCategoryAndIsActiveTrueOrderByPromptName(category);
    }
    
    /**
     * Create new prompt
     */
    public AIPrompt createPrompt(AIPrompt prompt) {
        // Check if prompt key already exists
        if (promptRepository.existsByPromptKey(prompt.getPromptKey())) {
            throw new IllegalArgumentException("Prompt key already exists: " + prompt.getPromptKey());
        }
        
        prompt.setCreatedBy(getCurrentUserEmail());
        prompt.setCreatedAt(LocalDateTime.now());
        prompt.setUpdatedAt(LocalDateTime.now());
        prompt.setIsActive(true);
        prompt.setVersion(1);
        
        log.info("Creating new prompt: {} by {}", prompt.getPromptKey(), getCurrentUserEmail());
        return promptRepository.save(prompt);
    }
    
    /**
     * Update existing prompt
     */
    public AIPrompt updatePrompt(Long id, AIPrompt updatedPrompt) {
        AIPrompt existingPrompt = promptRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Prompt not found with id: " + id));
        
        // Update fields
        existingPrompt.setPromptName(updatedPrompt.getPromptName());
        existingPrompt.setPromptDescription(updatedPrompt.getPromptDescription());
        existingPrompt.setPromptContent(updatedPrompt.getPromptContent());
        existingPrompt.setPromptCategory(updatedPrompt.getPromptCategory());
        existingPrompt.setUpdatedBy(getCurrentUserEmail());
        existingPrompt.setUpdatedAt(LocalDateTime.now());
        
        // Increment version if content changed
        if (!existingPrompt.getPromptContent().equals(updatedPrompt.getPromptContent())) {
            existingPrompt.setVersion(existingPrompt.getVersion() + 1);
        }
        
        log.info("Updating prompt: {} by {}", existingPrompt.getPromptKey(), getCurrentUserEmail());
        return promptRepository.save(existingPrompt);
    }
    
    /**
     * Activate/deactivate prompt
     */
    public AIPrompt togglePromptStatus(Long id, boolean isActive) {
        AIPrompt prompt = promptRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Prompt not found with id: " + id));
        
        prompt.setIsActive(isActive);
        prompt.setUpdatedBy(getCurrentUserEmail());
        prompt.setUpdatedAt(LocalDateTime.now());
        
        log.info("Setting prompt {} status to {} by {}", prompt.getPromptKey(), isActive, getCurrentUserEmail());
        return promptRepository.save(prompt);
    }
    
    /**
     * Delete prompt (soft delete by setting inactive)
     */
    public void deletePrompt(Long id) {
        togglePromptStatus(id, false);
    }
    
    /**
     * Search prompts
     */
    @Transactional(readOnly = true)
    public List<AIPrompt> searchPrompts(String searchTerm) {
        return promptRepository.searchActivePrompts(searchTerm);
    }
    
    /**
     * Get prompt by ID
     */
    @Transactional(readOnly = true)
    public Optional<AIPrompt> getPromptById(Long id) {
        return promptRepository.findById(id);
    }
    
    /**
     * Get all versions of a prompt
     */
    @Transactional(readOnly = true)
    public List<AIPrompt> getPromptVersions(String promptKey) {
        return promptRepository.findByPromptKeyOrderByVersionDesc(promptKey);
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
