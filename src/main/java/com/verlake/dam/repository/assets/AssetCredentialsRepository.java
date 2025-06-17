package com.verlake.dam.repository.assets;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.verlake.dam.entity.assets.AssetCredential;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.user.User;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssetCredentialsRepository extends JpaRepository<AssetCredential, Long> {
    List<AssetCredential> findByAssetId(Long assetId);
    void deleteByAssetId(Long assetId);
    void deleteByAssetIdAndUserId(Long assetId, Long userId);
    
    List<AssetCredential> findByAssetIdAndUserId(Long assetId, Long userId);

    boolean existsByAssetIdAndUserId(Long assetId, Long userId);

    @Query("SELECT ac FROM AssetCredential ac " +
           "LEFT JOIN FETCH ac.asset a " +
           "LEFT JOIN FETCH ac.user u " +
           "WHERE ac.user.id = :userId " +
           "AND ac.username IS NULL " +
           "AND ac.password IS NULL " +
           "AND ac.asset.deleted = false")
    List<AssetCredential> findNewAssignedCredentials(@Param("userId") Long userId);

    List<AssetCredential> findByUserId(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE AssetCredential ac SET ac.username = null, ac.password = null WHERE ac.asset.id = :assetId")
    void resetCredentialsByAssetId(Long assetId);

    Optional<AssetCredential> findByUserAndAssetAndUserAccessType(User user, Asset asset, String userAccessType);

    List<AssetCredential> findByAssetAndUserAccessType(Asset asset, String userAccessType);

    List<AssetCredential> findByAssetIdAndUserAccessType(Long assetId, String userAccessType);
    
    List<AssetCredential> findByUserAndUserAccessType(User user, String userAccessType);
    
    boolean existsByUsername(String username);
} 