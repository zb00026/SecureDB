package com.verlake.dam.controller.asset_owner;

import com.verlake.dam.controller.common.BaseAuditTrailController;
import com.verlake.dam.service.audit_trail.AuditStatsService;
import com.verlake.dam.service.audit_trail.AuditTrailCsvExportService;
import com.verlake.dam.service.audit_trail.RoleBasedAuditTrailService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for asset owner audit trail operations
 * Provides endpoints for asset owners to view audit trails for their assets
 */
@RestController
@RequestMapping("/api/asset_owner/audit-trails")
public class OwnerAuditTrailController extends BaseAuditTrailController {
    
    public OwnerAuditTrailController(RoleBasedAuditTrailService roleBasedAuditTrailService,
                                   AuditTrailCsvExportService csvExportService,
                                   AuditStatsService auditStatsService) {
        super(roleBasedAuditTrailService, csvExportService, auditStatsService);
    }

    @Override
    protected String getRoleName() {
        return "Asset owner";
    }
} 