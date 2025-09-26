package com.verlake.dam.repository.unix;

import com.verlake.dam.entity.unix.UnixGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Unix group operations
 */
@Repository
public interface UnixGroupRepository extends JpaRepository<UnixGroup, Long>, JpaSpecificationExecutor<UnixGroup> {

    /**
     * Find all groups for a specific asset
     */
    List<UnixGroup> findByAssetIdOrderByGroupNameAsc(Long assetId);

    /**
     * Find all groups for a specific asset with pagination
     */
    Page<UnixGroup> findByAssetIdOrderByGroupNameAsc(Long assetId, Pageable pageable);

    /**
     * Find a group by name and asset
     */
    Optional<UnixGroup> findByGroupNameAndAssetId(String groupName, Long assetId);

    /**
     * Find all custom groups (non-system groups) for an asset
     */
    List<UnixGroup> findByAssetIdAndIsSystemGroupFalseOrderByGroupNameAsc(Long assetId);

    /**
     * Find all custom groups (non-system groups) for an asset with pagination
     */
    Page<UnixGroup> findByAssetIdAndIsSystemGroupFalseOrderByGroupNameAsc(Long assetId, Pageable pageable);

    /**
     * Find all system groups for an asset
     */
    List<UnixGroup> findByAssetIdAndIsSystemGroupTrueOrderByGroupNameAsc(Long assetId);

    /**
     * Find all system groups for an asset with pagination
     */
    Page<UnixGroup> findByAssetIdAndIsSystemGroupTrueOrderByGroupNameAsc(Long assetId, Pageable pageable);

    /**
     * Check if a group exists by name and asset
     */
    boolean existsByGroupNameAndAssetId(String groupName, Long assetId);

    /**
     * Find groups by asset and group name pattern
     */
    @Query("SELECT ug FROM UnixGroup ug WHERE ug.asset.id = :assetId AND LOWER(ug.groupName) LIKE LOWER(CONCAT('%', :pattern, '%')) ORDER BY ug.groupName ASC")
    List<UnixGroup> findByAssetIdAndGroupNameContainingIgnoreCase(@Param("assetId") Long assetId, @Param("pattern") String pattern);

    /**
     * Find groups that need synchronization (older than specified time)
     */
    @Query("SELECT ug FROM UnixGroup ug WHERE ug.asset.id = :assetId AND (ug.lastSyncedAt IS NULL OR ug.lastSyncedAt < :syncThreshold) ORDER BY ug.groupName ASC")
    List<UnixGroup> findGroupsNeedingSync(@Param("assetId") Long assetId, @Param("syncThreshold") java.time.LocalDateTime syncThreshold);

    /**
     * Count groups by asset
     */
    long countByAssetId(Long assetId);

    /**
     * Count custom groups by asset
     */
    long countByAssetIdAndIsSystemGroupFalse(Long assetId);

    /**
     * Count system groups by asset
     */
    long countByAssetIdAndIsSystemGroupTrue(Long assetId);

    /**
     * Find groups with folder access to a specific path
     */
    @Query("SELECT DISTINCT ug FROM UnixGroup ug JOIN ug.folderAccesses fa WHERE ug.asset.id = :assetId AND fa.folderPath = :folderPath")
    List<UnixGroup> findGroupsWithAccessToFolder(@Param("assetId") Long assetId, @Param("folderPath") String folderPath);

    /**
     * Find groups with folder access to a specific path pattern
     */
    @Query("SELECT DISTINCT ug FROM UnixGroup ug JOIN ug.folderAccesses fa WHERE ug.asset.id = :assetId AND fa.folderPath LIKE :folderPathPattern")
    List<UnixGroup> findGroupsWithAccessToFolderPattern(@Param("assetId") Long assetId, @Param("folderPathPattern") String folderPathPattern);

    /**
     * Delete all groups for an asset (used when asset is deleted)
     */
    void deleteByAssetId(Long assetId);

    /**
     * Find groups created by a specific user
     */
    List<UnixGroup> findByCreatedByIdOrderByCreatedAtDesc(Long userId);

    /**
     * Find groups created by a specific user for a specific asset
     */
    List<UnixGroup> findByCreatedByIdAndAssetIdOrderByCreatedAtDesc(Long userId, Long assetId);

    /**
     * Find a group by ID with folder accesses eagerly loaded
     */
    @Query("SELECT ug FROM UnixGroup ug LEFT JOIN FETCH ug.folderAccesses WHERE ug.id = :groupId")
    Optional<UnixGroup> findByIdWithFolderAccesses(@Param("groupId") Long groupId);
}
