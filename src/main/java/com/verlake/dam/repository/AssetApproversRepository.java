package com.verlake.dam.repository;

import com.verlake.dam.entity.assets.AssetApprover;
import com.verlake.dam.entity.assets.AssetCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface AssetApproversRepository extends JpaRepository<AssetApprover, Long> {
    List<AssetApprover> findByAssetId(Long assetId);
    void deleteByAssetId(Long assetId);
    void deleteByAssetIdAndUserId(Long assetId, Long userId);
    boolean existsByAssetIdAndUserId(Long assetId, Long userId);
} 