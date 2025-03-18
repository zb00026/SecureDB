package com.verlake.dam.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.verlake.dam.entity.AssetCredential;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface AssetCredentialsRepository extends JpaRepository<AssetCredential, Long> {
    List<AssetCredential> findByAssetId(Long assetId);
    void deleteByAssetId(Long assetId);
    void deleteByAssetIdAndUserId(Long assetId, Long userId);
    boolean existsByAssetIdAndUserId(Long assetId, Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE AssetCredential ac SET ac.username = null, ac.password = null WHERE ac.asset.id = :assetId")
    void resetCredentialsByAssetId(Long assetId);
} 