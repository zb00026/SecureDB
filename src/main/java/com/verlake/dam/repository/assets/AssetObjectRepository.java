package com.verlake.dam.repository.assets;

import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.assets.AssetObject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetObjectRepository extends JpaRepository<AssetObject, Long> {
    Optional<AssetObject> findByAssetCredential(AssetCredential assetCredential);
    
    // If you need to find by asset
    List<AssetObject> findByAsset(Asset asset);
    
    // If you still need to query by credential ID
    Optional<AssetObject> findByAssetCredential_Id(Long credentialId);

    void deleteByAssetCredential(AssetCredential assetCredential);
} 