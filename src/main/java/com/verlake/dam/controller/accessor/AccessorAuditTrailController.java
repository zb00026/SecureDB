package com.verlake.dam.controller.accessor;

import com.verlake.dam.controller.common.BaseAuditTrailController;
import com.verlake.dam.service.audit_trail.AuditStatsService;
import com.verlake.dam.service.audit_trail.AuditTrailCsvExportService;
import com.verlake.dam.service.audit_trail.RoleBasedAuditTrailService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accessor/audit-trails")
public class AccessorAuditTrailController extends BaseAuditTrailController {
    
    public AccessorAuditTrailController(RoleBasedAuditTrailService roleBasedAuditTrailService, 
                                      AuditTrailCsvExportService csvExportService,
                                      AuditStatsService auditStatsService) {
        super(roleBasedAuditTrailService, csvExportService, auditStatsService);
    }

    @Override
    protected String getRoleName() {
        return "Accessor";
    }
} 