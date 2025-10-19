package com.verlake.dam.controller.common;

import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.entity.dto.RoleBasedAuditTrailFilter;
import com.verlake.dam.entity.dto.AuditTrailDTO;
import com.verlake.dam.service.audit_trail.RoleBasedAuditTrailService;
import com.verlake.dam.service.audit_trail.AuditTrailCsvExportService;
import com.verlake.dam.exception.CsvExportException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;


@RequiredArgsConstructor
@Slf4j
public abstract class BaseAuditTrailController {
    
    protected final RoleBasedAuditTrailService roleBasedAuditTrailService;
    protected final AuditTrailCsvExportService csvExportService;

    /**
     * Get audit trails for the current user based on their role and permissions
     * @param filter The filter containing search criteria
     * @return Page of audit trails filtered by role-based access
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ASSET_OWNER', 'ROLE_APPROVER', 'ROLE_DEVELOPER')")
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
                .toList(),
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
     * Download audit trails as CSV file for the current user based on their role
     * @param filter The filter containing search criteria for the export
     * @return CSV file with audit trail data filtered by role-based access
     */
    @GetMapping(value = "/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ASSET_OWNER', 'ROLE_APPROVER', 'ROLE_DEVELOPER')")
    public ResponseEntity<byte[]> downloadAuditTrailsCsv(RoleBasedAuditTrailFilter filter) {
        String roleName = getRoleName();
        log.info("{} requesting CSV export of audit trails with filter: {}", roleName, filter);
        
        try {
            byte[] csvContent = csvExportService.exportAuditTrailsToCsv(filter);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", 
                    String.format("audit_trails_%s.csv", roleName.toLowerCase().replace(" ", "_")));
            headers.setContentLength(csvContent.length);
            
            log.info("Generated CSV export for {}: {} bytes", roleName, csvContent.length);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(csvContent);
                    
        } catch (Exception e) {
            log.error("Failed to generate CSV export for {}: {}", roleName, e.getMessage(), e);
            throw new CsvExportException("Failed to generate CSV export for " + roleName, e);
        }
    }

    /**
     * Get the role name for logging purposes
     * @return The role name (e.g., "Approver", "Asset owner", "Developer")
     */
    protected abstract String getRoleName();
} 