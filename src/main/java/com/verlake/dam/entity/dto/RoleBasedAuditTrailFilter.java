package com.verlake.dam.entity.dto;

import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.utils.Constants;
import jakarta.persistence.criteria.Predicate;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Data
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class RoleBasedAuditTrailFilter extends AuditTrailFilter {
    
    // Additional fields for role-based filtering
    private String instanceId;
    private String actionMetadata;
    
    // Role-based filtering fields
    private Roles userRole;
    private String currentUserEmail;
    private Set<Long> allowedAssetIds;
    private Set<String> allowedUserEmails;

    @Override
    public Specification<AuditTrail> toSpecification() {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            // Apply base filters from parent class
            Specification<AuditTrail> baseSpec = super.toSpecification();
            if (baseSpec != null) {
                Predicate basePredicate = baseSpec.toPredicate(root, query, cb);
                if (basePredicate != null) {
                    predicates.add(basePredicate);
                }
            }
            
            // Add additional text filters specific to role-based filtering
            addAdditionalTextFilters(predicates, root, cb);
            
            // Role-based filters
            addRoleBasedFilters(predicates, root, cb);

            log.debug("Filter conditions: startDate={}, endDate={}, action={}, user={}, role={}, allowedAssets={}", 
                getStartDate(), getEndDate(), getAction(), getUser(), userRole, allowedAssetIds != null ? allowedAssetIds.size() : 0);

            return predicates.isEmpty() ? null : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void addAdditionalTextFilters(List<Predicate> predicates, jakarta.persistence.criteria.Root<AuditTrail> root, 
                                        jakarta.persistence.criteria.CriteriaBuilder cb) {
        if (instanceId != null && !instanceId.trim().isEmpty()) {
            predicates.add(cb.like(
                cb.upper(root.get(Constants.AUDIT_TRAIL_FIELD_INSTANCE_ID)),
                "%" + instanceId.trim().toUpperCase() + "%"
            ));
        }

        if (actionMetadata != null && !actionMetadata.trim().isEmpty()) {
            predicates.add(cb.like(
                cb.upper(root.get(Constants.AUDIT_TRAIL_FIELD_ACTION_METADATA)),
                "%" + actionMetadata.trim().toUpperCase() + "%"
            ));
        }
    }

    private void addRoleBasedFilters(List<Predicate> predicates, jakarta.persistence.criteria.Root<AuditTrail> root, 
                                   jakarta.persistence.criteria.CriteriaBuilder cb) {
        if (userRole == null) {
            return;
        }

        switch (userRole) {
            case ASSET_OWNER:
                addAssetOwnerFilters(predicates, root, cb);
                break;
            case APPROVER:
                addApproverFilters(predicates, root, cb);
                break;
            case ADMIN, AUDITOR:
                // Admin and Auditor can see all audit logs - no additional filters
                break;
            case DEVELOPER:
                addDeveloperFilters(predicates, root, cb);
                break;
        }
    }

    private void addAssetOwnerFilters(List<Predicate> predicates, jakarta.persistence.criteria.Root<AuditTrail> root, 
                                    jakarta.persistence.criteria.CriteriaBuilder cb) {
        // Exclude audit logs where asset is null for asset owners
        predicates.add(cb.isNotNull(root.get(Constants.AUDIT_TRAIL_FIELD_ASSET)));
        
        if (allowedAssetIds != null && !allowedAssetIds.isEmpty()) {
            List<Predicate> assetPredicates = new ArrayList<>();
            
            // Filter by asset relationship using asset IDs
            assetPredicates.add(root.get(Constants.AUDIT_TRAIL_FIELD_ASSET).get(Constants.FIELD_ID).in(allowedAssetIds));
            
            // Also include audit logs for the current user's own actions
            if (currentUserEmail != null) {
                assetPredicates.add(cb.equal(
                    cb.upper(root.get(Constants.AUDIT_TRAIL_FIELD_USER)),
                    currentUserEmail.toUpperCase()
                ));
            }
            
            predicates.add(cb.or(assetPredicates.toArray(new Predicate[0])));
        } else {
            // If no allowed assets, return no results (always false predicate)
            predicates.add(cb.equal(cb.literal(1), 0));
        }
    }

    private void addApproverFilters(List<Predicate> predicates, jakarta.persistence.criteria.Root<AuditTrail> root, 
                                  jakarta.persistence.criteria.CriteriaBuilder cb) {
        List<Predicate> approverPredicates = new ArrayList<>();
        
        // Include asset-related audit logs for assets they approve
        if (allowedAssetIds != null && !allowedAssetIds.isEmpty()) {
            approverPredicates.add(root.get(Constants.AUDIT_TRAIL_FIELD_ASSET).get(Constants.FIELD_ID).in(allowedAssetIds));
        }
        
        // Include audit logs for users they approve
        if (allowedUserEmails != null && !allowedUserEmails.isEmpty()) {
            for (String email : allowedUserEmails) {
                approverPredicates.add(cb.equal(
                    cb.upper(root.get(Constants.AUDIT_TRAIL_FIELD_USER)),
                    email.toUpperCase()
                ));
            }
        }
        
        // Include their own audit logs
        if (currentUserEmail != null) {
            approverPredicates.add(cb.equal(
                cb.upper(root.get(Constants.AUDIT_TRAIL_FIELD_USER)),
                currentUserEmail.toUpperCase()
            ));
        }
        
        // Include approval-related actions
        approverPredicates.add(cb.like(
            cb.upper(root.get(Constants.AUDIT_TRAIL_FIELD_ACTION)),
            "%" + Constants.AUDIT_ACTION_APPROVAL + "%"
        ));
        approverPredicates.add(cb.like(
            cb.upper(root.get(Constants.AUDIT_TRAIL_FIELD_ACTION)),
            "%" + Constants.AUDIT_ACTION_APPROVE + "%"
        ));
        approverPredicates.add(cb.like(
            cb.upper(root.get(Constants.AUDIT_TRAIL_FIELD_ACTION)),
            "%" + Constants.AUDIT_ACTION_REJECT + "%"
        ));
        
        if (!approverPredicates.isEmpty()) {
            predicates.add(cb.or(approverPredicates.toArray(new Predicate[0])));
        }
    }

    private void addDeveloperFilters(List<Predicate> predicates, jakarta.persistence.criteria.Root<AuditTrail> root, 
                                   jakarta.persistence.criteria.CriteriaBuilder cb) {
        // Exclude audit logs where asset is null for developers
        predicates.add(cb.isNotNull(root.get(Constants.AUDIT_TRAIL_FIELD_ASSET)));
        
        // Developers can only see their own audit logs
        if (currentUserEmail != null) {
            predicates.add(cb.equal(
                cb.upper(root.get(Constants.AUDIT_TRAIL_FIELD_USER)),
                currentUserEmail.toUpperCase()
            ));
        }
    }
} 