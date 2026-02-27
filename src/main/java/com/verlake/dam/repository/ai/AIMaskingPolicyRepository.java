package com.verlake.dam.repository.ai;

import com.verlake.dam.entity.ai.AIMaskingPolicy;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AIMaskingPolicyRepository extends JpaRepository<AIMaskingPolicy, Long> {
    
    /**
     * Find active policies for an asset
     */
    List<AIMaskingPolicy> findByAssetAndIsActiveTrue(Asset asset);
    
    /**
     * Find policies created by a user
     */
    List<AIMaskingPolicy> findByCreatedBy(User user);
    
    /**
     * Find policies by asset
     */
    List<AIMaskingPolicy> findByAsset(Asset asset);
    
    /**
     * Find policies by intent type
     */
    List<AIMaskingPolicy> findByIntentType(String intentType);
    
    /**
     * Find policies by table and field
     */
    List<AIMaskingPolicy> findByTableNameAndFieldName(String tableName, String fieldName);
    
    /**
     * Find active policies by table and field
     */
    List<AIMaskingPolicy> findByTableNameAndFieldNameAndIsActiveTrue(String tableName, String fieldName);

    /**
     * Find existing policies for asset + table + field by creator (for replace-on-create)
     */
    List<AIMaskingPolicy> findByAssetAndTableNameAndFieldNameAndCreatedBy(
            Asset asset, String tableName, String fieldName, User createdBy);

    /**
     * Find existing policy for exact asset + table + field + strategy (active or not)
     */
    List<AIMaskingPolicy> findByAssetAndTableNameAndFieldNameAndMaskingStrategy(
            Asset asset, String tableName, String fieldName, String maskingStrategy);
    
    /**
     * Find existing policy for exact asset + table + field + strategy + creator (active or not)
     */
    List<AIMaskingPolicy> findByAssetAndTableNameAndFieldNameAndMaskingStrategyAndCreatedBy(
            Asset asset, String tableName, String fieldName, String maskingStrategy, User createdBy);
    
    /**
     * Find active policies for asset that apply to a specific user role
     */
    List<AIMaskingPolicy> findByAssetAndIsActiveTrueAndTargetRoleContaining(Asset asset, String roleName);
    
    /**
     * Find policies by asset ID with pagination
     */
    Page<AIMaskingPolicy> findByAssetId(Long assetId, Pageable pageable);
    
    /**
     * Find active policies by asset ID with pagination
     */
    Page<AIMaskingPolicy> findByAssetIdAndIsActiveTrue(Long assetId, Pageable pageable);
    
    /**
     * Find inactive policies by asset ID with pagination
     */
    Page<AIMaskingPolicy> findByAssetIdAndIsActiveFalse(Long assetId, Pageable pageable);
    
    /**
     * Find policies by creator with pagination
     */
    Page<AIMaskingPolicy> findByCreatedBy(User createdBy, Pageable pageable);
    
    /**
     * Find active policies by creator with pagination
     */
    Page<AIMaskingPolicy> findByCreatedByAndIsActiveTrue(User createdBy, Pageable pageable);
    
    /**
     * Find inactive policies by creator with pagination
     */
    Page<AIMaskingPolicy> findByCreatedByAndIsActiveFalse(User createdBy, Pageable pageable);
    
    /**
     * Count policies by creator
     */
    long countByCreatedBy(User createdBy);
    
    /**
     * Count active policies by creator
     */
    long countByCreatedByAndIsActiveTrue(User createdBy);
    
    /**
     * Count inactive policies by creator
     */
    long countByCreatedByAndIsActiveFalse(User createdBy);
} 