package com.verlake.dam.repository.unix;

import com.verlake.dam.entity.unix.UnixGroupFolderAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Unix group folder access operations
 */
@Repository
public interface UnixGroupFolderAccessRepository extends JpaRepository<UnixGroupFolderAccess, Long>, JpaSpecificationExecutor<UnixGroupFolderAccess> {

    /**
     * Find all folder accesses for a specific group
     */
    List<UnixGroupFolderAccess> findByUnixGroupIdOrderByFolderPathAsc(Long groupId);

    /**
     * Find folder access by group and folder path
     */
    Optional<UnixGroupFolderAccess> findByUnixGroupIdAndFolderPath(Long groupId, String folderPath);

    /**
     * Check if folder access exists for a group and path
     */
    boolean existsByUnixGroupIdAndFolderPath(Long groupId, String folderPath);

    /**
     * Find all folder accesses for a specific asset
     */
    @Query("SELECT fa FROM UnixGroupFolderAccess fa JOIN fa.unixGroup ug WHERE ug.asset.id = :assetId ORDER BY fa.folderPath ASC")
    List<UnixGroupFolderAccess> findByAssetId(@Param("assetId") Long assetId);

    /**
     * Find folder accesses by folder path pattern
     */
    @Query("SELECT fa FROM UnixGroupFolderAccess fa WHERE fa.folderPath LIKE :folderPathPattern ORDER BY fa.folderPath ASC")
    List<UnixGroupFolderAccess> findByFolderPathPattern(@Param("folderPathPattern") String folderPathPattern);

    /**
     * Find folder accesses by access type
     */
    List<UnixGroupFolderAccess> findByAccessType(UnixGroupFolderAccess.AccessType accessType);

    /**
     * Find folder accesses by access type for a specific asset
     */
    @Query("SELECT fa FROM UnixGroupFolderAccess fa JOIN fa.unixGroup ug WHERE ug.asset.id = :assetId AND fa.accessType = :accessType ORDER BY fa.folderPath ASC")
    List<UnixGroupFolderAccess> findByAssetIdAndAccessType(@Param("assetId") Long assetId, @Param("accessType") UnixGroupFolderAccess.AccessType accessType);

    /**
     * Find recursive folder accesses
     */
    List<UnixGroupFolderAccess> findByRecursiveTrue();

    /**
     * Find recursive folder accesses for a specific asset
     */
    @Query("SELECT fa FROM UnixGroupFolderAccess fa JOIN fa.unixGroup ug WHERE ug.asset.id = :assetId AND fa.recursive = true ORDER BY fa.folderPath ASC")
    List<UnixGroupFolderAccess> findByAssetIdAndRecursiveTrue(@Param("assetId") Long assetId);

    /**
     * Count folder accesses by group
     */
    long countByUnixGroupId(Long groupId);

    /**
     * Count folder accesses by asset
     */
    @Query("SELECT COUNT(fa) FROM UnixGroupFolderAccess fa JOIN fa.unixGroup ug WHERE ug.asset.id = :assetId")
    long countByAssetId(@Param("assetId") Long assetId);

    /**
     * Find folder accesses created by a specific user
     */
    List<UnixGroupFolderAccess> findByCreatedByIdOrderByCreatedAtDesc(Long userId);

    /**
     * Find folder accesses created by a specific user for a specific asset
     */
    @Query("SELECT fa FROM UnixGroupFolderAccess fa JOIN fa.unixGroup ug WHERE ug.asset.id = :assetId AND fa.createdBy.id = :userId ORDER BY fa.createdAt DESC")
    List<UnixGroupFolderAccess> findByAssetIdAndCreatedById(@Param("assetId") Long assetId, @Param("userId") Long userId);

    /**
     * Delete all folder accesses for a group
     */
    void deleteByUnixGroupId(Long groupId);

    /**
     * Delete all folder accesses for an asset
     */
    @Query("DELETE FROM UnixGroupFolderAccess fa WHERE fa.unixGroup.id IN (SELECT ug.id FROM UnixGroup ug WHERE ug.asset.id = :assetId)")
    void deleteByAssetId(@Param("assetId") Long assetId);
}
