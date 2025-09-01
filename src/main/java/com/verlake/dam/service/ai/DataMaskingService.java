package com.verlake.dam.service.ai;

import com.verlake.dam.entity.ai.AIMaskingPolicy;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.service.audit_trail.AuditTrailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
@Slf4j
public class DataMaskingService {
    
    private final AIMaskingPolicyService maskingPolicyService;
    private final AuditTrailService auditTrailService;
    
    public DataMaskingService(AIMaskingPolicyService maskingPolicyService, 
                             AuditTrailService auditTrailService) {
        this.maskingPolicyService = maskingPolicyService;
        this.auditTrailService = auditTrailService;
    }
    
    /**
     * Apply masking to query results based on user role and active policies
     */
    public List<Map<String, Object>> maskQueryResults(Asset asset, String userRole, String userEmail,
                                                     List<Map<String, Object>> queryResults) {
        if (queryResults == null || queryResults.isEmpty()) {
            return queryResults;
        }
        
        // Get applicable masking policies for this user's role
        List<AIMaskingPolicy> policies = maskingPolicyService.getApplicablePolicies(asset, userRole);
        if (policies.isEmpty()) {
            return queryResults; // No masking needed
        }
        
        log.info("Applying {} masking policies for user {} (role: {}) on asset {}", 
                policies.size(), userEmail, userRole, asset.getId());
        
        // Build masking rules map: table.field -> policy
        Map<String, AIMaskingPolicy> maskingRules = new HashMap<>();
        for (AIMaskingPolicy policy : policies) {
            String key = (policy.getTableName() + "." + policy.getFieldName()).toLowerCase();
            maskingRules.put(key, policy);
        }
        
        // Apply masking to each row
        List<Map<String, Object>> maskedResults = queryResults.stream()
            .map(row -> maskRow(row, maskingRules))
            .toList();
        
        // Audit trail for data access with masking
        try {
            String maskedFields = policies.stream()
                .map(p -> p.getTableName() + "." + p.getFieldName())
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
                
            AuditTrail audit = AuditTrail.builder()
                .timestamp(LocalDateTime.now())
                .user(userEmail)
                .action("DATA_ACCESS_WITH_MASKING")
                .newValue("Masked fields: " + maskedFields + " for role: " + userRole)
                .asset(asset)
                .build();
            auditTrailService.save(audit);
        } catch (Exception e) {
            log.warn("Failed to audit data masking: {}", e.getMessage());
        }
        
        return maskedResults;
    }
    
    /**
     * Mask a single row based on masking rules
     */
    private Map<String, Object> maskRow(Map<String, Object> row, Map<String, AIMaskingPolicy> maskingRules) {
        Map<String, Object> maskedRow = new HashMap<>(row);
        
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String fieldKey = entry.getKey().toLowerCase();
            Object value = entry.getValue();
            
            // Check if this field needs masking
            AIMaskingPolicy policy = findMatchingPolicy(fieldKey, maskingRules);
            if (policy != null && value != null) {
                Object maskedValue = applyMasking(value.toString(), policy);
                maskedRow.put(entry.getKey(), maskedValue);
            }
        }
        
        return maskedRow;
    }
    
    /**
     * Find matching masking policy for a field (handles table.field or just field name)
     */
    private AIMaskingPolicy findMatchingPolicy(String fieldKey, Map<String, AIMaskingPolicy> maskingRules) {
        // Try exact match first (table.field)
        AIMaskingPolicy policy = maskingRules.get(fieldKey);
        if (policy != null) {
            return policy;
        }
        
        // Try field name only match
        for (Map.Entry<String, AIMaskingPolicy> entry : maskingRules.entrySet()) {
            String ruleKey = entry.getKey();
            if (ruleKey.contains(".")) {
                String fieldName = ruleKey.substring(ruleKey.lastIndexOf(".") + 1);
                if (fieldKey.equals(fieldName) || fieldKey.endsWith("." + fieldName)) {
                    return entry.getValue();
                }
            }
        }
        
        return null;
    }
    
    /**
     * Apply specific masking strategy to a value
     */
    private Object applyMasking(String value, AIMaskingPolicy policy) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        
        String strategy = policy.getMaskingStrategy();
        if (strategy == null) {
            strategy = "partial";
        }
        
        return switch (strategy.toLowerCase()) {
            case "full" -> maskFull(value, policy);
            case "partial" -> maskPartial(value, policy);
            case "hash" -> "HASH_********";
            case "custom" -> maskCustom(value, policy);
            default -> maskPartial(value, policy);
        };
    }
    
    /**
     * Full masking - replace entire value with mask characters
     */
    private String maskFull(String value, AIMaskingPolicy policy) {
        String maskChar = policy.getMaskChar() != null ? policy.getMaskChar() : "*";
        return maskChar.repeat(Math.min(value.length(), 8)); // Limit to 8 chars
    }
    
    /**
     * Partial masking - preserve some characters, mask others
     */
    private String maskPartial(String value, AIMaskingPolicy policy) {
        if (value.length() <= 2) {
            return value; // Too short to mask meaningfully
        }
        
        int preserveChars = policy.getPreserveChars() != null ? policy.getPreserveChars() : 2;
        String maskChar = policy.getMaskChar() != null ? policy.getMaskChar() : "*";
        
        // For email addresses, preserve domain structure
        if (value.contains("@")) {
            return maskEmail(value, preserveChars, maskChar);
        }
        
        // For other values, preserve last N characters
        if (preserveChars >= value.length()) {
            return value; // Don't mask if preserve chars >= length
        }
        
        String masked = maskChar.repeat(value.length() - preserveChars);
        String preserved = value.substring(value.length() - preserveChars);
        return masked + preserved;
    }
    
    /**
     * Email-specific partial masking
     */
    private String maskEmail(String email, int preserveChars, String maskChar) {
        String[] parts = email.split("@");
        if (parts.length != 2) {
            return maskPartial(email, null); // Fallback to regular partial masking
        }
        
        String localPart = parts[0];
        String domain = parts[1];
        
        if (localPart.length() <= preserveChars) {
            return localPart.charAt(0) + maskChar.repeat(Math.max(1, localPart.length() - 1)) + "@" + domain;
        }
        
        String maskedLocal = localPart.substring(0, preserveChars) + 
                           maskChar.repeat(Math.max(1, localPart.length() - preserveChars));
        return maskedLocal + "@" + domain;
    }
    
    /**
     * Custom masking using pattern
     */
    private String maskCustom(String value, AIMaskingPolicy policy) {
        String pattern = policy.getMaskingPattern();
        if (pattern == null || pattern.isEmpty()) {
            return maskPartial(value, policy); // Fallback to partial
        }
        
        try {
            // Simple pattern replacement (you can extend this)
            return value.replaceAll(pattern, policy.getMaskChar() != null ? policy.getMaskChar() : "*");
        } catch (Exception e) {
            log.warn("Custom masking pattern failed for value, falling back to partial masking: {}", e.getMessage());
            return maskPartial(value, policy);
        }
    }
}
