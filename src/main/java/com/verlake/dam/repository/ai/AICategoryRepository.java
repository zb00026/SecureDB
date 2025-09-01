package com.verlake.dam.repository.ai;

import com.verlake.dam.entity.ai.AICategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AICategoryRepository extends JpaRepository<AICategory, Long> {
    
    /**
     * Find category by key
     */
    Optional<AICategory> findByCategoryKeyAndIsActiveTrue(String categoryKey);
    
    /**
     * Find all active categories ordered by priority
     */
    List<AICategory> findByIsActiveTrueOrderByPriorityDescCategoryNameAsc();
    
    /**
     * Find categories by sensitivity level
     */
    List<AICategory> findBySensitivityLevelAndIsActiveTrueOrderByPriorityDesc(AICategory.SensitivityLevel sensitivityLevel);
    
    /**
     * Check if category key exists
     */
    boolean existsByCategoryKey(String categoryKey);
    
    /**
     * Find categories by keyword match (for context detection)
     */
    @Query("SELECT c FROM AICategory c WHERE c.isActive = true AND " +
           "LOWER(:text) LIKE CONCAT('%', LOWER(c.keywords), '%') " +
           "ORDER BY c.priority DESC, c.categoryName ASC")
    List<AICategory> findCategoriesByKeywordMatch(@Param("text") String text);
    
    /**
     * Find categories by field pattern match
     */
    @Query("SELECT c FROM AICategory c WHERE c.isActive = true AND " +
           "LOWER(:fieldName) LIKE CONCAT('%', LOWER(c.fieldPatterns), '%') " +
           "ORDER BY c.priority DESC, c.categoryName ASC")
    List<AICategory> findCategoriesByFieldPatternMatch(@Param("fieldName") String fieldName);
    
    /**
     * Get latest version of a category
     */
    @Query("SELECT c FROM AICategory c WHERE c.categoryKey = :categoryKey ORDER BY c.version DESC")
    List<AICategory> findByCategoryKeyOrderByVersionDesc(@Param("categoryKey") String categoryKey);
}
