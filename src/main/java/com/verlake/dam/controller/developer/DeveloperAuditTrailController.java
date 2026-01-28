package com.verlake.dam.controller.developer;

import com.verlake.dam.controller.common.BaseAuditTrailController;
import com.verlake.dam.service.audit_trail.AuditStatsService;
import com.verlake.dam.service.audit_trail.AuditTrailCsvExportService;
import com.verlake.dam.service.audit_trail.RoleBasedAuditTrailService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/developer/audit-trails")
public class DeveloperAuditTrailController extends BaseAuditTrailController {
    
    public DeveloperAuditTrailController(RoleBasedAuditTrailService roleBasedAuditTrailService, 
                                      AuditTrailCsvExportService csvExportService,
                                      AuditStatsService auditStatsService) {
        super(roleBasedAuditTrailService, csvExportService, auditStatsService);
    }

    @Override
    protected String getRoleName() {
        return "Developer";
    }
} 