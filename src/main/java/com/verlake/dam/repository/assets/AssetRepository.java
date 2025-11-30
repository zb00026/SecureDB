package com.verlake.dam.repository.assets;

import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.enums.LockType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByDeletedFalse();
    Optional<Asset> findByIdAndDeletedFalse(Long id);
    boolean existsByName(String name);
    
    /**
     * Update only the lock status and lock type fields to avoid cascading saves to related entities
     */
    @Modifying
    @Query("UPDATE Asset a SET a.locked = :locked, a.lockType = :lockType WHERE a.id = :assetId")
    void updateLockStatus(@Param("assetId") Long assetId, @Param("locked") boolean locked, @Param("lockType") LockType lockType);
    
    /**
     * Update only the lock type field (for asset owner lockout/unlock)
     */
    @Modifying
    @Query("UPDATE Asset a SET a.lockType = :lockType WHERE a.id = :assetId")
    void updateLockType(@Param("assetId") Long assetId, @Param("lockType") LockType lockType);
} 