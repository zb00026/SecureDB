package com.verlake.dam.controller.auditor;


import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.entity.dto.AuditTrailFilter;
import com.verlake.dam.service.audit_trail.AuditTrailService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
@RestController
@RequestMapping("/api/audit-trails")
@RequiredArgsConstructor
public class AuditTrailController {
    @Autowired
    private final AuditTrailService auditTrailService;

    @GetMapping
    public Page<AuditTrail> getAuditTrails(AuditTrailFilter filter) {
        return auditTrailService.findAll(filter);
    }
} 