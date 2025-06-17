package com.verlake.dam.service.audit_trail;

import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.assets.AssetApprover;
import com.verlake.dam.entity.assets.AssetCredential;
import com.verlake.dam.entity.dto.RoleBasedAuditTrailFilter;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.repository.AuditTrailRepository;
import com.verlake.dam.repository.assets.AssetApproversRepository;
import com.verlake.dam.repository.assets.AssetCredentialsRepository;
import com.verlake.dam.repository.assets.AssetRepository;
import com.verlake.dam.service.users.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleBasedAuditTrailService {
    
    private final AuditTrailRepository auditTrailRepository;
    private final UserService userService;
    private final AssetCredentialsRepository assetCredentialsRepository;
    private final AssetApproversRepository assetApproversRepository;
    private final AssetRepository assetRepository;

    /**
     * Get audit trails with role-based filtering
     * @param filter The filter containing search criteria
     * @return Page of audit trails filtered by user's role and permissions
     */
    public Page<AuditTrail> getAuditTrails(RoleBasedAuditTrailFilter filter) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("User not authenticated");
        }

        // Determine user's primary role for audit access
        Roles userRole = determineAuditAccessRole(currentUser);
        
        // Set role-based filter parameters
        setupRoleBasedFilter(filter, currentUser, userRole);
        
        log.info("User {} with role {} accessing audit trails with {} allowed assets", 
                currentUser.getEmail(), userRole, 
                filter.getAllowedAssetIds() != null ? filter.getAllowedAssetIds().size() : 0);

        // Execute the filtered query
        Page<AuditTrail> result = auditTrailRepository.findAll(
            filter.toSpecification(), 
            filter.toPageRequest(Sort.by(Sort.Direction.DESC, "timestamp"))
        );
        
        log.debug("Returned {} audit trail records for user {}", result.getTotalElements(), currentUser.getEmail());
        return result;
    }

    /**
     * Determines the primary role for audit access based on user's roles
     * Priority: ADMIN > AUDITOR > ASSET_OWNER > APPROVER > DEVELOPER
     */
    private Roles determineAuditAccessRole(User user) {
        Set<String> userRoleNames = user.getRoles().stream()
                .map(role -> role.getName())
                .collect(Collectors.toSet());

        if (userRoleNames.contains(Roles.ADMIN.getOriginalName())) {
            return Roles.ADMIN;
        } else if (userRoleNames.contains(Roles.AUDITOR.getOriginalName())) {
            return Roles.AUDITOR;
        } else if (userRoleNames.contains(Roles.ASSET_OWNER.getOriginalName())) {
            return Roles.ASSET_OWNER;
        } else if (userRoleNames.contains(Roles.APPROVER.getOriginalName())) {
            return Roles.APPROVER;
        } else if (userRoleNames.contains(Roles.DEVELOPER.getOriginalName())) {
            return Roles.DEVELOPER;
        } else {
            throw new AccessDeniedException("User does not have permission to access audit trails");
        }
    }

    /**
     * Sets up role-based filtering parameters
     */
    private void setupRoleBasedFilter(RoleBasedAuditTrailFilter filter, User currentUser, Roles userRole) {
        filter.setUserRole(userRole);
        filter.setCurrentUserEmail(currentUser.getEmail());

        switch (userRole) {
            case ADMIN, AUDITOR:
                // No additional filtering - can see all audit logs
                break;
                
            case ASSET_OWNER:
                setupAssetOwnerFilter(filter, currentUser);
                break;
                
            case APPROVER:
                setupApproverFilter(filter, currentUser);
                break;
                
            case DEVELOPER:
                // Filter is already set to current user email - developers see only their own logs
                break;
        }
    }

    /**
     * Sets up filtering for asset owners - they can see audit logs for their assets
     */
    private void setupAssetOwnerFilter(RoleBasedAuditTrailFilter filter, User currentUser) {
        // Get all assets owned by the current user
        List<AssetCredential> ownedCredentials = assetCredentialsRepository
                .findByUserAndUserAccessType(currentUser, Roles.ASSET_OWNER.getOriginalName());
        
        Set<Long> ownedAssetIds = ownedCredentials.stream()
                .map(credential -> credential.getAsset().getId())
                .collect(Collectors.toSet());
        
        filter.setAllowedAssetIds(ownedAssetIds);
        
        log.debug("Asset owner {} has access to {} assets: {}", 
                currentUser.getEmail(), ownedAssetIds.size(), ownedAssetIds);
    }

    /**
     * Sets up filtering for approvers - they can see audit logs for assets they approve and users they approve
     */
    private void setupApproverFilter(RoleBasedAuditTrailFilter filter, User currentUser) {
        // Get all assets where the current user is an approver
        List<AssetApprover> approverRelations = assetApproversRepository.findByUser(currentUser);
        
        Set<Long> approvedAssetIds = approverRelations.stream()
                .map(relation -> relation.getAsset().getId())
                .collect(Collectors.toSet());
        
        filter.setAllowedAssetIds(approvedAssetIds);
        
        // Get all users that this approver can approve (users who have this person as their approver)
        List<User> usersWithThisApprover = userService.getUsersByApprover(currentUser);
        Set<String> allowedUserEmails = usersWithThisApprover.stream()
                .map(User::getEmail)
                .collect(Collectors.toSet());
        
        filter.setAllowedUserEmails(allowedUserEmails);
        
        log.debug("Approver {} has access to {} assets and {} users", 
                currentUser.getEmail(), approvedAssetIds.size(), allowedUserEmails.size());
    }

    /**
     * Validates that the current user has permission to access audit trails
     */
    public void validateAuditAccess() {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("User not authenticated");
        }

        try {
            determineAuditAccessRole(currentUser);
        } catch (AccessDeniedException e) {
            log.warn("User {} attempted to access audit trails without proper permissions", currentUser.getEmail());
            throw e;
        }
    }

    /**
     * Get audit trail statistics for the current user based on their role
     */
    public AuditTrailStats getAuditTrailStats() {
        User currentUser = userService.getCurrentUser();
        Roles userRole = determineAuditAccessRole(currentUser);
        
        RoleBasedAuditTrailFilter filter = new RoleBasedAuditTrailFilter();
        setupRoleBasedFilter(filter, currentUser, userRole);
        
        long totalCount = auditTrailRepository.count(filter.toSpecification());
        
        // Get counts by action type
        filter.setAction("CREATE");
        long createCount = auditTrailRepository.count(filter.toSpecification());
        
        filter.setAction("UPDATE");
        long updateCount = auditTrailRepository.count(filter.toSpecification());
        
        filter.setAction("DELETE");
        long deleteCount = auditTrailRepository.count(filter.toSpecification());
        
        filter.setAction("QUERY_EXECUTED");
        long queryCount = auditTrailRepository.count(filter.toSpecification());
        
        return AuditTrailStats.builder()
                .totalCount(totalCount)
                .createCount(createCount)
                .updateCount(updateCount)
                .deleteCount(deleteCount)
                .queryCount(queryCount)
                .userRole(userRole.getOriginalName())
                .build();
    }

    /**
     * DTO for audit trail statistics
     */
    @lombok.Data
    @lombok.Builder
    public static class AuditTrailStats {
        private long totalCount;
        private long createCount;
        private long updateCount;
        private long deleteCount;
        private long queryCount;
        private String userRole;
    }
} 