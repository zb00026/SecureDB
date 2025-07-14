package com.verlake.dam.controller.common;

import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.entity.dto.RoleBasedAuditTrailFilter;
import com.verlake.dam.entity.dto.AuditTrailDTO;
import com.verlake.dam.service.audit_trail.RoleBasedAuditTrailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
public abstract class BaseAuditTrailController {
    
    protected final RoleBasedAuditTrailService roleBasedAuditTrailService;

    /**
     * Get audit trails for the current user based on their role and permissions
     * @param filter The filter containing search criteria
     * @return Page of audit trails filtered by role-based access
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ASSET_OWNER', 'APPROVER', 'DEVELOPER')")
    public ResponseEntity<Page<AuditTrailDTO>> getAuditTrails(RoleBasedAuditTrailFilter filter) {
        String roleName = getRoleName();
        log.info("{} requesting audit trails with filter: {}", roleName, filter);
        
        // Validate access before processing
        roleBasedAuditTrailService.validateAuditAccess();
        
        Page<AuditTrail> auditTrails = roleBasedAuditTrailService.getAuditTrails(filter);
        
        // Convert entities to DTOs
        Page<AuditTrailDTO> dtoPage = new PageImpl<>(
            auditTrails.getContent().stream()
                .map(AuditTrailDTO::fromEntity)
                .collect(Collectors.toList()),
            auditTrails.getPageable(),
            auditTrails.getTotalElements()
        );
        
        log.info("Returning {} audit trail records for {}", dtoPage.getTotalElements(), roleName.toLowerCase());
        return ResponseEntity.ok(dtoPage);
    }

    /**
     * Get audit trail statistics for the current user based on their role
     * @return Statistics about audit trails accessible to the current user
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('ASSET_OWNER', 'APPROVER', 'DEVELOPER')")
    public ResponseEntity<RoleBasedAuditTrailService.AuditTrailStats> getAuditTrailStats() {
        String roleName = getRoleName();
        log.info("{} requesting audit trail statistics", roleName);
        
        roleBasedAuditTrailService.validateAuditAccess();
        
        RoleBasedAuditTrailService.AuditTrailStats stats = roleBasedAuditTrailService.getAuditTrailStats();
        
        log.info("Returning audit trail statistics for {}: {} total records", roleName.toLowerCase(), stats.getTotalCount());
        return ResponseEntity.ok(stats);
    }

    /**
     * Get the role name for logging purposes
     * @return The role name (e.g., "Approver", "Asset owner", "Developer")
     */
    protected abstract String getRoleName();
} 