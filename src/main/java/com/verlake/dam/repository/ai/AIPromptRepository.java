package com.verlake.dam.repository.ai;

import com.verlake.dam.entity.ai.AIPrompt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AIPromptRepository extends JpaRepository<AIPrompt, Long> {
    
    /**
     * Find active prompt by key
     */
    Optional<AIPrompt> findByPromptKeyAndIsActiveTrue(String promptKey);
    
    /**
     * Find all active prompts by category
     */
    List<AIPrompt> findByPromptCategoryAndIsActiveTrueOrderByPromptName(String promptCategory);
    
    /**
     * Find all active prompts
     */
    List<AIPrompt> findByIsActiveTrueOrderByPromptCategoryAscPromptNameAsc();
    
    /**
     * Find all prompts by category (including inactive)
     */
    List<AIPrompt> findByPromptCategoryOrderByIsActiveDescVersionDesc(String promptCategory);
    
    /**
     * Check if prompt key exists
     */
    boolean existsByPromptKey(String promptKey);
    
    /**
     * Get latest version of a prompt by key
     */
    @Query("SELECT p FROM AIPrompt p WHERE p.promptKey = :promptKey ORDER BY p.version DESC, p.id DESC")
    List<AIPrompt> findLatestVersionByPromptKey(@Param("promptKey") String promptKey);
    
    /**
     * Get all versions of a prompt by key
     */
    List<AIPrompt> findByPromptKeyOrderByVersionDesc(String promptKey);
    
    /**
     * Find prompts by content containing search term
     */
    @Query("SELECT p FROM AIPrompt p WHERE p.isActive = true AND " +
           "(LOWER(p.promptName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.promptContent) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.promptDescription) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<AIPrompt> searchActivePrompts(@Param("searchTerm") String searchTerm);
}
