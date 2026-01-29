package com.verlake.dam.repository.assets;

import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.ApprovalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {
    
    // Find requests by requestor
    List<AccessRequest> findByRequestor(User requestor);
    
    // Find requests for a specific asset
    List<AccessRequest> findByAsset(Asset asset);

    @Query("SELECT ar FROM AccessRequest ar WHERE ar.asset = :asset AND ar.expiryDate > CURRENT_TIMESTAMP AND ar.expiryHours > 0")
    List<AccessRequest> findPendingsByAsset(Asset asset);
    
    // Delete all access requests for a specific asset
    void deleteByAsset(Asset asset);
    
    // Find requests by approval status
    List<AccessRequest> findByAccessorApproverStatus(ApprovalStatus status);
    List<AccessRequest> findByAssetApproverStatus(ApprovalStatus status);
    
    // Find pending requests (both statuses are REQUESTED)
    @Query("SELECT ar FROM AccessRequest ar WHERE ar.asset = :asset AND ar.assetApproverStatus = 'REQUESTED' AND ar.expiryDate > CURRENT_TIMESTAMP AND ar.expiryHours > 0")
    List<AccessRequest> findPendingRequestsByAsset(@Param("asset") Asset asset);
    
    // Find pending requests for multiple assets (batch query)
    @Query("SELECT ar FROM AccessRequest ar WHERE ar.asset.id IN :assetIds AND ar.assetApproverStatus = 'REQUESTED' AND ar.expiryDate > CURRENT_TIMESTAMP AND ar.expiryHours > 0")
    List<AccessRequest> findPendingRequestsByAssetIds(@Param("assetIds") List<Long> assetIds);
    
    // Find requests by asset and requestor
    @Query("SELECT ar FROM AccessRequest ar WHERE ar.asset = :asset AND ar.requestor = :requestor AND ar.expiryDate > CURRENT_TIMESTAMP AND ar.expiryHours > 0")
    List<AccessRequest> findNotExpiredByAssetAndRequestor(@Param("asset") Asset asset, @Param("requestor") User requestor);

    @Query("SELECT ar FROM AccessRequest ar WHERE ar.asset = :asset AND ar.requestor = :requestor ORDER BY ar.id DESC")
    List<AccessRequest> findByAssetAndRequestor(@Param("asset") Asset asset, @Param("requestor") User requestor);
    
    // Find latest request for an asset and user
    @Query("SELECT ar FROM AccessRequest ar WHERE ar.asset = :asset AND ar.requestor = :requestor ORDER BY ar.requestTime DESC")
    List<AccessRequest> findLatestRequestByAssetAndRequestor(@Param("asset") Asset asset, @Param("requestor") User requestor);
    
    // Find all requests that need accessor approval
    @Query("SELECT ar FROM AccessRequest ar WHERE ar.accessorApproverStatus = 'REQUESTED' ORDER BY ar.requestTime ASC")
    List<AccessRequest> findRequestsNeedingAccessorApproval();
    
    // Find all requests that need asset owner approval
    @Query("SELECT ar FROM AccessRequest ar WHERE ar.accessorApproverStatus = 'APPROVED' AND ar.assetApproverStatus = 'REQUESTED' ORDER BY ar.requestTime ASC")
    List<AccessRequest> findRequestsNeedingAssetApproval();
    
    // Find all approved requests
    @Query("SELECT ar FROM AccessRequest ar WHERE ar.accessorApproverStatus = 'APPROVED' AND ar.assetApproverStatus = 'APPROVED'")
    List<AccessRequest> findApprovedRequests();
    
    // Find all rejected requests
    @Query("SELECT ar FROM AccessRequest ar WHERE ar.accessorApproverStatus = 'REJECTED' OR ar.assetApproverStatus = 'REJECTED'")
    List<AccessRequest> findRejectedRequests();
    
    // Count pending requests for an asset
    @Query("SELECT COUNT(ar) FROM AccessRequest ar WHERE ar.asset = :asset AND (ar.accessorApproverStatus = 'REQUESTED' OR ar.assetApproverStatus = 'REQUESTED')")
    Long countPendingRequestsForAsset(@Param("asset") Asset asset);
    
    // Find requests by multiple statuses
    @Query("SELECT ar FROM AccessRequest ar WHERE ar.accessorApproverStatus IN :statuses OR ar.assetApproverStatus IN :statuses")
    List<AccessRequest> findByStatuses(@Param("statuses") List<ApprovalStatus> statuses);


    @Query("SELECT ar FROM AccessRequest ar " +
           "JOIN ar.assetCredential ac " +
           "WHERE ar.expiryDate < :currentDate " +
           "AND ac.isDeleted = false " +
           "AND ar.assetApproverStatus = 'APPROVED'")
    List<AccessRequest> findByExpiryDateBeforeAndAssetCredentialIsDeletedFalse(
            @Param("currentDate") LocalDateTime currentDate
        );
    
    @Query("SELECT ar FROM AccessRequest ar " +
           "JOIN ar.assetCredential ac " +
           "WHERE ar.requestor = :requestor " +
           "AND ar.expiryDate < :currentDate " +
           "AND ac.isDeleted = false " +
           "AND ac.userAccessType = :userAccessType " +
           "AND (ar.accessorApproverStatus != 'EXPIRED' AND ar.assetApproverStatus != 'EXPIRED')")
    List<AccessRequest> findExpiredByRequestorAndUserAccessType(
            @Param("requestor") User requestor,
            @Param("currentDate") LocalDateTime currentDate,
            @Param("userAccessType") String userAccessType
        );

    @Query("SELECT ar FROM AccessRequest ar " +
           "JOIN ar.assetCredential ac " +
           "WHERE ar.requestor = :user " +
           "AND ar.asset = :asset " +
           "AND ac.userAccessType = :userAccessType " +
           "AND ar.expiryDate > CURRENT_TIMESTAMP ")
    List<AccessRequest> findByUserAndAssetAndUserAccessTypeAndNotExpired(
            @Param("user") User user,
            @Param("asset") Asset asset,
            @Param("userAccessType") String userAccessType);

    @Query("SELECT DISTINCT ar FROM AccessRequest ar " +
           "JOIN FETCH ar.assetCredential ac " +
           "JOIN FETCH ar.asset " +
           "JOIN FETCH ar.requestor " +
           "WHERE ar.requestor = :requestor " +
           "AND ar.isTempPassword = :isTempPassword " +
           "AND ar.assetApproverStatus = :approvalStatus " +
           "AND ar.expiryDate > :currentDateTime " +
           "AND ac.isDeleted = false")
    List<AccessRequest> findByRequestorAndIsTempPasswordAndAssetApproverStatusAndIsDeletedFalseAndExpiryDateAfter(
        @Param("requestor") User requestor, 
        @Param("isTempPassword") Boolean isTempPassword, 
        @Param("approvalStatus") ApprovalStatus approvalStatus,
        @Param("currentDateTime") LocalDateTime currentDateTime);

    @Query("SELECT DISTINCT ar FROM AccessRequest ar " +
           "JOIN FETCH ar.asset " +
           "WHERE ar.asset = :asset")
    List<AccessRequest> findByAssetWithFetch(@Param("asset") Asset asset);

    // Unix access request queries
    @Query("SELECT DISTINCT ar FROM AccessRequest ar " +
           "LEFT JOIN FETCH ar.groupMemberships gm " +
           "LEFT JOIN FETCH gm.unixGroup " +
           "WHERE ar.asset.id IN :assetIds " +
           "AND ar.assetApproverStatus = :status " +
           "AND ar.requestedUsername IS NOT NULL " +
           "ORDER BY ar.requestTime DESC")
    List<AccessRequest> findPendingUnixRequestsForAssets(
            @Param("assetIds") List<Long> assetIds, 
            @Param("status") ApprovalStatus status);
    
    @Query("SELECT ar FROM AccessRequest ar WHERE ar.asset = :asset " +
           "AND ar.requestor = :requestor " +
           "AND ar.assetApproverStatus IN :statuses " +
           "AND ar.requestedUsername IS NOT NULL")
    Optional<AccessRequest> findActiveUnixRequestByAssetAndRequestor(
            @Param("asset") Asset asset, 
            @Param("requestor") User requestor, 
            @Param("statuses") List<ApprovalStatus> statuses);
    
    @Query("SELECT DISTINCT ar FROM AccessRequest ar " +
           "LEFT JOIN FETCH ar.groupMemberships gm " +
           "LEFT JOIN FETCH gm.unixGroup " +
           "WHERE ar.asset = :asset " +
           "AND ar.requestedUsername IS NOT NULL " +
           "ORDER BY ar.requestTime DESC")
    Page<AccessRequest> findUnixRequestsByAssetOrderByRequestTimeDesc(
            @Param("asset") Asset asset, 
            Pageable pageable);
    
    @Query("SELECT DISTINCT ar FROM AccessRequest ar " +
           "LEFT JOIN FETCH ar.groupMemberships gm " +
           "LEFT JOIN FETCH gm.unixGroup " +
           "WHERE ar.requestor = :requestor " +
           "AND ar.requestedUsername IS NOT NULL " +
           "ORDER BY ar.requestTime DESC")
    List<AccessRequest> findUnixRequestsByRequestorOrderByRequestTimeDesc(
            @Param("requestor") User requestor);

} 