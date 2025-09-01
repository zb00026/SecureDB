package com.verlake.dam.repository.ai;

import com.verlake.dam.entity.ai.AISensitivePattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AISensitivePatternRepository extends JpaRepository<AISensitivePattern, Long> {
    
    /**
     * Find active pattern by key
     */
    Optional<AISensitivePattern> findByPatternKeyAndIsActiveTrue(String patternKey);
    
    /**
     * Find all active patterns
     */
    List<AISensitivePattern> findByIsActiveTrueOrderByPatternCategoryAscPatternNameAsc();
    
    /**
     * Find patterns by category
     */
    List<AISensitivePattern> findByPatternCategoryAndIsActiveTrueOrderByPatternName(String category);
    
    /**
     * Find patterns by sensitivity level
     */
    List<AISensitivePattern> findBySensitivityLevelAndIsActiveTrueOrderByPatternName(String sensitivityLevel);
    
    /**
     * Find patterns by masking strategy
     */
    List<AISensitivePattern> findByMaskingStrategyAndIsActiveTrueOrderByPatternName(String maskingStrategy);
    
    /**
     * Search patterns by name or description
     */
    @Query("SELECT p FROM AISensitivePattern p WHERE p.isActive = true AND " +
           "(LOWER(p.patternName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.patternDescription) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.patternKey) LIKE LOWER(CONCAT('%', :searchTerm, '%'))) " +
           "ORDER BY p.patternCategory ASC, p.patternName ASC")
    List<AISensitivePattern> searchActivePatterns(@Param("searchTerm") String searchTerm);
    
    /**
     * Check if pattern key exists
     */
    boolean existsByPatternKey(String patternKey);
    
    /**
     * Find all versions of a pattern
     */
    List<AISensitivePattern> findByPatternKeyOrderByVersionDesc(String patternKey);
    

}
