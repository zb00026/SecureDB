package com.verlake.dam.controller.developer;

import com.verlake.dam.controller.common.BaseAuditTrailController;
import com.verlake.dam.service.audit_trail.RoleBasedAuditTrailService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/developer/audit-trails")
public class DeveloperAuditTrailController extends BaseAuditTrailController {
    
    public DeveloperAuditTrailController(RoleBasedAuditTrailService roleBasedAuditTrailService) {
        super(roleBasedAuditTrailService);
    }

    @Override
    protected String getRoleName() {
        return "Developer";
    }
} 