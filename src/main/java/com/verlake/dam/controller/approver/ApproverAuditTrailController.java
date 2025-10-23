package com.verlake.dam.controller.approver;

import com.verlake.dam.controller.common.BaseAuditTrailController;
import com.verlake.dam.service.audit_trail.RoleBasedAuditTrailService;
import com.verlake.dam.service.audit_trail.AuditTrailCsvExportService;
import com.verlake.dam.service.audit_trail.AuditStatsService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/approver/audit-trails")
public class ApproverAuditTrailController extends BaseAuditTrailController {
    
    public ApproverAuditTrailController(RoleBasedAuditTrailService roleBasedAuditTrailService,
                                      AuditTrailCsvExportService csvExportService,
                                      AuditStatsService auditStatsService) {
        super(roleBasedAuditTrailService, csvExportService, auditStatsService);
    }

    @Override
    protected String getRoleName() {
        return "Approver";
    }
} 