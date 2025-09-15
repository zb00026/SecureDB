package com.verlake.dam.controller.auditor;


import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.entity.dto.AuditTrailFilter;
import com.verlake.dam.entity.dto.AuditTrailDTO;
import com.verlake.dam.entity.assets.dto.AssetDTO;
import com.verlake.dam.service.audit_trail.AuditTrailService;
import com.verlake.dam.service.assets.AssetService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.util.List;


@RestController
@RequestMapping("/api/audit-trails")
@RequiredArgsConstructor
public class AuditTrailController {
    @Autowired
    private final AuditTrailService auditTrailService;
    
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
} 