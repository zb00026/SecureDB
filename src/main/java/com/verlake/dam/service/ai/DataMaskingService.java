package com.verlake.dam.service.ai;

import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.entity.ai.AIMaskingPolicy;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.service.audit_trail.AuditTrailService;
import com.verlake.dam.utils.AuditDescriptionUtils;
import com.verlake.dam.utils.Constants;
import com.verlake.dam.utils.IpAddressUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

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
     * Apply masking to query results based on user roles and active policies
     */
    public List<Map<String, Object>> maskQueryResults(Asset asset, String userRole, String userEmail,
                                                     List<Map<String, Object>> queryResults) {
        if (queryResults == null || queryResults.isEmpty()) {
            return queryResults;
        }
        
        // Parse multiple roles from comma-separated string
        List<String> userRoles = userRole.contains(",") ? 
            Arrays.asList(userRole.split(",")) :
                List.of(userRole);
        
        // Get applicable masking policies for all user roles
        List<AIMaskingPolicy> policies = getApplicablePoliciesForMultipleRoles(asset, userRoles);
        
        // Debug: Log all policies for this asset (regardless of role)
        List<AIMaskingPolicy> allPolicies = maskingPolicyService.getActivePoliciesForAsset(asset);
        log.debug("Found {} total active policies for asset {}, {} applicable for roles {}", 
                allPolicies.size(), asset.getId(), policies.size(), userRoles);
        
        if (policies.isEmpty()) {
            log.debug("No applicable masking policies found for user {} (roles: {}) on asset {}", 
                    userEmail, userRoles, asset.getId());
            return queryResults; // No masking needed
        }
        
        log.info("Applying {} masking policies for user {} (roles: {}) on asset {}", 
                policies.size(), userEmail, userRoles, asset.getId());
        
        // Build masking rules map: table.field -> policy
        // When both full and partial masking exist for the same field, full masking takes precedence
        Map<String, AIMaskingPolicy> maskingRules = new HashMap<>();
        for (AIMaskingPolicy policy : policies) {
            String key = (policy.getTableName() + "." + policy.getFieldName()).toLowerCase();
            putPolicyIfPreferred(maskingRules, key, policy);

            // Also add field-only mapping for better matching
            String fieldKey = policy.getFieldName().toLowerCase();
            putPolicyIfPreferred(maskingRules, fieldKey, policy);

            log.debug("Added masking rule: {} -> {} (strategy: {})",
                    key, policy.getFieldName(), policy.getMaskingStrategy());
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
                
        String instanceId = asset != null ? String.format("ASSET(%s)", asset.getName()) : Constants.ENTITY_TYPE_ASSET;

        AuditTrail audit = AuditTrail.builder()
                .timestamp(LocalDateTime.now())
                .user(Constants.AUDIT_SYSTEM_USER)
                .action(Constants.AUDIT_ACTION_TYPE_DATA_ACCESS_WITH_MASKING)
                .newValue("Masked fields: " + maskedFields + " for role: " + userRole)
                .instanceId(instanceId)
                .ipAddress(IpAddressUtils.getCurrentIpAddress())
                .asset(asset)
                .description(AuditDescriptionUtils.generateDescription(Constants.AUDIT_ACTION_TYPE_DATA_ACCESS_WITH_MASKING, Constants.ENTITY_TYPE_ASSET, null))
                // readableDescription will be computed at read-time (DTO)
                .build();
            auditTrailService.save(audit);
        } catch (Exception e) {
            log.warn("Failed to audit data masking: {}", e.getMessage());
        }
        
        return maskedResults;
    }
    
    /**
     * Put policy into map if it should take precedence.
     * Full masking takes precedence over partial when both exist for the same field.
     */
    private void putPolicyIfPreferred(Map<String, AIMaskingPolicy> maskingRules, String key, AIMaskingPolicy policy) {
        AIMaskingPolicy existing = maskingRules.get(key);
        if (existing == null) {
            maskingRules.put(key, policy);
            return;
        }
        String existingStrategy = existing.getMaskingStrategy() != null ? existing.getMaskingStrategy().toLowerCase() : "partial";
        String newStrategy = policy.getMaskingStrategy() != null ? policy.getMaskingStrategy().toLowerCase() : "partial";
        // Full masking takes precedence over partial
        if ("full".equals(newStrategy) && "partial".equals(existingStrategy)) {
            maskingRules.put(key, policy);
        }
    }

    /**
     * Get applicable masking policies for multiple user roles
     */
    private List<AIMaskingPolicy> getApplicablePoliciesForMultipleRoles(Asset asset, List<String> userRoles) {
        List<AIMaskingPolicy> allApplicablePolicies = new ArrayList<>();
        
        for (String role : userRoles) {
            List<AIMaskingPolicy> rolePolicies = maskingPolicyService.getApplicablePolicies(asset, role.trim());
            allApplicablePolicies.addAll(rolePolicies);
            log.debug("Found {} policies for role '{}' on asset {}", rolePolicies.size(), role, asset.getId());
        }
        
        // Remove duplicates based on policy ID
        return allApplicablePolicies.stream()
                .distinct()
                .toList();
    }
    
    /**
     * Mask a single row based on masking rules
     */
    private Map<String, Object> maskRow(Map<String, Object> row, Map<String, AIMaskingPolicy> maskingRules) {
        Map<String, Object> maskedRow = new HashMap<>(row);
        int maskedFields = 0;
        
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String fieldKey = entry.getKey().toLowerCase();
            Object value = entry.getValue();
            
            // Check if this field needs masking
            AIMaskingPolicy policy = findMatchingPolicy(fieldKey, maskingRules);
            if (policy != null && value != null) {
                Object maskedValue = applyMasking(value.toString(), policy);
                maskedRow.put(entry.getKey(), maskedValue);
                maskedFields++;
                log.debug("Masked field '{}' using policy {} (strategy: {})", 
                        entry.getKey(), policy.getId(), policy.getMaskingStrategy());
            }
        }
        
        if (maskedFields > 0) {
            log.debug("Masked {} fields in row", maskedFields);
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
