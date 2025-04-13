package com.verlake.dam.repository.assets;

import com.verlake.dam.entity.assets.AssetApprover;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetApproversRepository extends JpaRepository<AssetApprover, Long> {
    List<AssetApprover> findByAssetId(Long assetId);
    void deleteByAssetId(Long assetId);
    void deleteByAssetIdAndUserId(Long assetId, Long userId);
    boolean existsByAssetIdAndUserId(Long assetId, Long userId);
} 