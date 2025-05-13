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

@Repository
public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {
    
    // Find requests by requestor
    List<AccessRequest> findByRequestor(User requestor);
    
    // Find requests for a specific asset
    List<AccessRequest> findByAsset(Asset asset);
    
    // Find requests by approval status
    List<AccessRequest> findByDeveloperApproverStatus(ApprovalStatus status);
    List<AccessRequest> findByAssetApproverStatus(ApprovalStatus status);
    
    // Find pending requests (both statuses are PENDING)
    @Query("SELECT ar FROM AccessRequest ar WHERE ar.developerApproverStatus = 'PENDING' OR ar.assetApproverStatus = 'PENDING'")
    List<AccessRequest> findPendingRequests();
    
    // Find requests by asset and requestor
    @Query("SELECT ar FROM AccessRequest ar WHERE ar.asset = :asset AND ar.requestor = :requestor AND ar.expiryDate > CURRENT_TIMESTAMP")
    List<AccessRequest> findByAssetAndRequestor(@Param("asset") Asset asset, @Param("requestor") User requestor);
    
    // Find latest request for an asset and user
    @Query("SELECT ar FROM AccessRequest ar WHERE ar.asset = :asset AND ar.requestor = :requestor ORDER BY ar.requestTime DESC")
    List<AccessRequest> findLatestRequestByAssetAndRequestor(@Param("asset") Asset asset, @Param("requestor") User requestor);
    
    // Find all requests that need developer approval
    @Query("SELECT ar FROM AccessRequest ar WHERE ar.developerApproverStatus = 'PENDING' ORDER BY ar.requestTime ASC")
    List<AccessRequest> findRequestsNeedingDeveloperApproval();
    
    // Find all requests that need asset owner approval
    @Query("SELECT ar FROM AccessRequest ar WHERE ar.developerApproverStatus = 'APPROVED' AND ar.assetApproverStatus = 'PENDING' ORDER BY ar.requestTime ASC")
    List<AccessRequest> findRequestsNeedingAssetApproval();
    
    // Find all approved requests
    @Query("SELECT ar FROM AccessRequest ar WHERE ar.developerApproverStatus = 'APPROVED' AND ar.assetApproverStatus = 'APPROVED'")
    List<AccessRequest> findApprovedRequests();
    
    // Find all rejected requests
    @Query("SELECT ar FROM AccessRequest ar WHERE ar.developerApproverStatus = 'REJECTED' OR ar.assetApproverStatus = 'REJECTED'")
    List<AccessRequest> findRejectedRequests();
    
    // Count pending requests for an asset
    @Query("SELECT COUNT(ar) FROM AccessRequest ar WHERE ar.asset = :asset AND (ar.developerApproverStatus = 'PENDING' OR ar.assetApproverStatus = 'PENDING')")
    Long countPendingRequestsForAsset(@Param("asset") Asset asset);
    
    // Find requests by multiple statuses
    @Query("SELECT ar FROM AccessRequest ar WHERE ar.developerApproverStatus IN :statuses OR ar.assetApproverStatus IN :statuses")
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
           "WHERE ar.requestor = :user " +
           "AND ar.asset = :asset " +
           "AND ac.userAccessType = :userAccessType " +
           "AND ar.expiryDate > CURRENT_TIMESTAMP ")
    List<AccessRequest> findByUserAndAssetAndUserAccessTypeAndNotExpired(
            @Param("user") User user,
            @Param("asset") Asset asset,
            @Param("userAccessType") String userAccessType);

    @Query("SELECT ar FROM AccessRequest ar " +
           "JOIN ar.assetCredential ac " +
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

} 