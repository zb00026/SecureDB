package com.verlake.dam.controller.auditor;


import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.entity.dto.AuditTrailFilter;
import com.verlake.dam.entity.dto.AuditTrailDTO;
import com.verlake.dam.entity.dto.AuditStatsDTO;
import com.verlake.dam.entity.assets.dto.AssetDTO;
import com.verlake.dam.service.audit_trail.AuditTrailService;
import com.verlake.dam.service.audit_trail.AuditTrailCsvExportService;
import com.verlake.dam.service.audit_trail.AuditStatsService;
import com.verlake.dam.service.assets.AssetService;
import com.verlake.dam.exception.AuditTrailException;
import com.verlake.dam.exception.CsvExportException;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;


@RestController
@RequestMapping("/api/audit-trails")
@RequiredArgsConstructor
public class AuditTrailController {
    @Autowired
    private final AuditTrailService auditTrailService;
    
    @Autowired
    private final AuditTrailCsvExportService csvExportService;
    
    @Autowired
    private final AuditStatsService auditStatsService;
    
    @Autowired
    private final AssetService assetService;

    @GetMapping
    public Page<AuditTrailDTO> getAuditTrails(AuditTrailFilter filter) {
        Page<AuditTrail> auditTrails = auditTrailService.findAll(filter);
        
        // Convert entities to DTOs and return immediately
        return new PageImpl<>(
            auditTrails.getContent().stream()
                .map(AuditTrailDTO::fromEntity)
                .toList(),
            auditTrails.getPageable(),
            auditTrails.getTotalElements()
        );
    }

    /**
     * Get all assets for selection in audit trail filtering
     * This endpoint provides a list of all available assets that can be used
     * to filter audit trails by specific assets
     * 
     * @return List of AssetDTO containing all available assets
     */
    @GetMapping("/assets")
    public ResponseEntity<List<AssetDTO>> getAllAssets() {
        List<AssetDTO> assets = assetService.getAllAssets();
        return ResponseEntity.ok(assets);
    }

    /**
     * Download audit trails as CSV file
     * This endpoint allows downloading audit trail data in CSV format for analysis
     * 
     * @param filter The filter containing search criteria for the export
     * @return CSV file with audit trail data
     */
    @GetMapping(value = "/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @PreAuthorize("hasAuthority('ROLE_AUDITOR')")
    public ResponseEntity<byte[]> downloadAuditTrailsCsv(AuditTrailFilter filter) {
        try {
            byte[] csvContent = csvExportService.exportAllAuditTrailsToCsv(filter);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "audit_trails.csv");
            headers.setContentLength(csvContent.length);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(csvContent);
                    
        } catch (Exception e) {
            throw new CsvExportException("Failed to generate CSV export", e);
        }
    }

    /**
     * Get audit trail statistics and chart data for admin users
     * @param filter The filter containing search criteria for the stats
     * @return Audit statistics with chart data
     */
    @GetMapping("/stats/charts")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_AUDITOR')")
    public ResponseEntity<AuditStatsDTO> getAuditStats(AuditTrailFilter filter) {
        try {
            AuditStatsDTO stats = auditStatsService.generateAllAuditStats(filter);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            throw new AuditTrailException("Failed to generate audit stats", "AUDIT_STATS_GENERATION", "AuditTrailController", e);
        }
    }
} 