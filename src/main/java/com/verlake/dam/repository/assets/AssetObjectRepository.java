package com.verlake.dam.repository.assets;

import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.assets.AssetObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AssetObjectRepository extends JpaRepository<AssetObject, Long> {
    Optional<AssetObject> findByAssetCredential(AssetCredential assetCredential);
    
    // If you need to find by asset
    List<AssetObject> findByAsset(Asset asset);
    
    // If you still need to query by credential ID
    Optional<AssetObject> findByAssetCredential_Id(Long credentialId);

    void deleteByAssetCredential(AssetCredential assetCredential);

    /**
     * Returns asset IDs that have at least one synced AssetObject (schema has been fetched).
     * Used to filter asset lists to only show assets with synced schema.
     */
    @Query("SELECT DISTINCT ao.asset.id FROM AssetObject ao WHERE ao.asset IS NOT NULL")
    Set<Long> findDistinctAssetIdsWithObjects();

    boolean existsByAsset_Id(Long assetId);
} 