package com.verlake.dam.entity.dto.unix;

import com.verlake.dam.entity.dto.PageRequestDTO;
import com.verlake.dam.entity.unix.UnixGroup;
import jakarta.persistence.criteria.Predicate;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Filter DTO for Unix group queries with pagination support
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class UnixGroupFilter extends PageRequestDTO {
    
    private Long assetId;
    private String groupName;
    private Boolean isSystemGroup;
    private String description;
    private Long createdById;
    private String folderPath;
    private String accessType;
    
    /**
     * Convert filter to JPA Specification
     */
    public Specification<UnixGroup> toSpecification() {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            addBasicFilters(root, criteriaBuilder, predicates);
            addFolderAccessFilters(root, criteriaBuilder, predicates);
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
    
    /**
     * Add basic filters (asset, group name, system group, description, created by)
     */
    private void addBasicFilters(jakarta.persistence.criteria.Root<UnixGroup> root, 
                                jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
                                List<Predicate> predicates) {
        // Filter by asset ID
        if (assetId != null) {
            predicates.add(criteriaBuilder.equal(root.get("asset").get("id"), assetId));
        }
        
        // Filter by group name (case-insensitive partial match)
        if (groupName != null && !groupName.trim().isEmpty()) {
            predicates.add(criteriaBuilder.like(
                criteriaBuilder.lower(root.get("groupName")), 
                "%" + groupName.toLowerCase() + "%"
            ));
        }
        
        // Filter by system group flag
        if (isSystemGroup != null) {
            predicates.add(criteriaBuilder.equal(root.get("isSystemGroup"), isSystemGroup));
        }
        
        // Filter by description (case-insensitive partial match)
        if (description != null && !description.trim().isEmpty()) {
            predicates.add(criteriaBuilder.like(
                criteriaBuilder.lower(root.get("description")), 
                "%" + description.toLowerCase() + "%"
            ));
        }
        
        // Filter by created by user ID
        if (createdById != null) {
            predicates.add(criteriaBuilder.equal(root.get("createdBy").get("id"), createdById));
        }
    }
    
    /**
     * Add folder access related filters
     */
    private void addFolderAccessFilters(jakarta.persistence.criteria.Root<UnixGroup> root, 
                                      jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
                                      List<Predicate> predicates) {
        // Filter by folder path (if group has access to specific folder)
        if (folderPath != null && !folderPath.trim().isEmpty()) {
            predicates.add(criteriaBuilder.like(
                root.join("folderAccesses").get("folderPath"), 
                "%" + folderPath + "%"
            ));
        }
        
        // Filter by access type
        if (accessType != null && !accessType.trim().isEmpty()) {
            predicates.add(criteriaBuilder.equal(
                root.join("folderAccesses").get("accessType"), 
                accessType.toUpperCase()
            ));
        }
    }
    
    /**
     * Create pageable with default sorting
     */
    @Override
    public Pageable toPageRequest() {
        // Default sort by group name ascending, then by created date descending
        Sort sort = Sort.by(Sort.Direction.ASC, "groupName")
                       .and(Sort.by(Sort.Direction.DESC, "createdAt"));
        return toPageRequest(sort);
    }
}
