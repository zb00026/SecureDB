package com.verlake.dam.service.ai;

import com.verlake.dam.entity.ai.AIMaskingPolicy;
import com.verlake.dam.entity.ai.FieldSuggestion;
import com.verlake.dam.entity.ai.MaskingIntent;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.enums.Roles;
import com.verlake.dam.exception.AIMaskingPolicyException;
import com.verlake.dam.repository.ai.AIMaskingPolicyRepository;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AIMaskingPolicyService {
    
    private final AIMaskingPolicyRepository policyRepository;
    
    public AIMaskingPolicyService(AIMaskingPolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }
    
    /**
     * Apply masking policies based on AI suggestions
     */
    @Transactional
    public List<AIMaskingPolicy> applyMaskingPolicies(Asset asset, MaskingIntent intent, 
                                                      List<FieldSuggestion> suggestions, User user) {
        List<AIMaskingPolicy> appliedPolicies = new ArrayList<>();
        
        try {
            if (suggestions == null) suggestions = new ArrayList<>();

            // Resolve target roles (expand "non-admin" to all roles except ADMIN)
            List<String> targetRoles = resolveTargetRoles(intent.getUserRole());

            for (FieldSuggestion suggestion : suggestions) {
                String strategy = resolveStrategy(intent, suggestion);
                
                // Check if policy already exists for this table+field+strategy+creator
                List<AIMaskingPolicy> existing = policyRepository.findByAssetAndTableNameAndFieldNameAndMaskingStrategyAndCreatedBy(
                        asset,
                        suggestion.getTableName(),
                        suggestion.getFieldName(),
                        strategy,
                        user
                );
                
                if (existing != null && !existing.isEmpty()) {
                    // Update existing policy by adding new roles
                    AIMaskingPolicy existingPolicy = existing.get(0);
                    String currentRoles = existingPolicy.getTargetRole() != null ? existingPolicy.getTargetRole() : "";
                    String updatedRoles = mergeRoles(currentRoles, targetRoles);
                    existingPolicy.setTargetRole(updatedRoles);
                    AIMaskingPolicy savedPolicy = policyRepository.save(existingPolicy);
                    appliedPolicies.add(savedPolicy);
                    log.info("Updated masking policy for {}.{} with roles: {}", 
                            suggestion.getTableName(), suggestion.getFieldName(), updatedRoles);
                } else {
                    // Create new policy with all target roles
                    String allRoles = String.join(",", targetRoles);
                    AIMaskingPolicy policy = createPolicyFromSuggestion(asset, intent, suggestion, user, allRoles);
                    AIMaskingPolicy savedPolicy = policyRepository.save(policy);
                    appliedPolicies.add(savedPolicy);
                    log.info("Created masking policy for {}.{} with strategy: {} roles: {}", 
                            suggestion.getTableName(), suggestion.getFieldName(), strategy, allRoles);
                }
            }
            
            log.info("Successfully applied {} masking policies for asset {}", appliedPolicies.size(), asset.getId());
            return appliedPolicies;
            
        } catch (Exception e) {
            log.error("Error applying masking policies for asset {}: {}", asset.getId(), e.getMessage());
            throw new AIMaskingPolicyException(Constants.ERROR_FAILED_TO_APPLY_MASKING_POLICIES + e.getMessage(), e);
        }
    }
    
    /**
     * Create policy from field suggestion
     */
    private AIMaskingPolicy createPolicyFromSuggestion(Asset asset, MaskingIntent intent, 
                                                       FieldSuggestion suggestion, User user, String targetRole) {
        return AIMaskingPolicy.builder()
            .originalRequest(intent.getOriginalRequest())
            .intentType(intent.getIntentType())
            .tableName(suggestion.getTableName())
            .fieldName(suggestion.getFieldName())
            .maskingStrategy(resolveStrategy(intent, suggestion))
            .maskingPattern(suggestion.getMaskingPattern())
            .preserveChars(resolvePreserveChars(intent, suggestion))
            .maskChar(resolveMaskChar(intent, suggestion))
            .targetRole(targetRole)
            .aiConfidence(suggestion.getConfidence())
            .aiReasoning(suggestion.getReason())
            .userConfirmed(true)
            .isActive(true)
            .asset(asset)
            .createdBy(user)
            .appliedAt(LocalDateTime.now())
            .build();
    }

    private List<String> resolveTargetRoles(String userRoleIntent) {
        if (userRoleIntent == null || userRoleIntent.isBlank() || userRoleIntent.equalsIgnoreCase("all")) {
            return getAllRoles();
        }

        if (isNonAdminRole(userRoleIntent)) {
            return getNonAdminRoles();
        }

        // Check if the intent contains comma-separated roles
        if (userRoleIntent.contains(",")) {
            return resolveMultipleRoles(userRoleIntent);
        }

        return resolveSingleRole(userRoleIntent);
    }
    
    /**
     * Get all available roles
     */
    private List<String> getAllRoles() {
        List<String> roles = new ArrayList<>();
        for (Roles r : Roles.values()) {
            roles.add(r.getOriginalName());
        }
        return roles;
    }
    
    /**
     * Check if the role intent is for non-admin users
     */
    private boolean isNonAdminRole(String userRoleIntent) {
        return userRoleIntent.equalsIgnoreCase("non-admin") || 
               userRoleIntent.equalsIgnoreCase("not admin") ||
               userRoleIntent.equalsIgnoreCase("non_admin");
    }
    
    /**
     * Get all roles except admin
     */
    private List<String> getNonAdminRoles() {
        List<String> roles = new ArrayList<>();
        for (Roles r : Roles.values()) {
            if (r != Roles.ADMIN) {
                roles.add(r.getOriginalName());
            }
        }
        return roles;
    }
    
    /**
     * Resolve multiple comma-separated roles from the intent
     */
    private List<String> resolveMultipleRoles(String userRoleIntent) {
        List<String> roles = new ArrayList<>();
        String[] roleTokens = userRoleIntent.split(",");
        
        for (String roleToken : roleTokens) {
            String trimmedRole = roleToken.trim();
            if (!trimmedRole.isEmpty()) {
                // Use the existing single role resolution logic for each role
                List<String> resolvedRoles = resolveSingleRole(trimmedRole);
                roles.addAll(resolvedRoles);
            }
        }
        
        return roles;
    }
    
    /**
     * Resolve a single role from the intent
     */
    private List<String> resolveSingleRole(String userRoleIntent) {
        List<String> roles = new ArrayList<>();
        
        // Try to map to a known single role (both enum name and display/original name)
        Roles enumRole = tryGetEnumRole(userRoleIntent);
        if (enumRole != null) {
            roles.add(enumRole.getOriginalName());
            return roles;
        }
        
        // Check against original names
        Roles originalNameRole = findRoleByOriginalName(userRoleIntent);
        if (originalNameRole != null) {
            roles.add(originalNameRole.getOriginalName());
            return roles;
        }

        // Fallback: treat as custom role string
        roles.add(userRoleIntent);
        return roles;
    }
    
    /**
     * Try to get role from enum value
     */
    private Roles tryGetEnumRole(String userRoleIntent) {
        try {
            return Roles.valueOf(userRoleIntent.trim().toUpperCase().replace(' ', '_'));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
    
    /**
     * Find role by original name
     */
    private Roles findRoleByOriginalName(String userRoleIntent) {
        for (Roles r : Roles.values()) {
            if (r.getOriginalName().equalsIgnoreCase(userRoleIntent)) {
                return r;
            }
        }
        return null;
    }

    private String resolveStrategy(MaskingIntent intent, FieldSuggestion suggestion) {
        if (intent.getMaskingStrategy() != null && !intent.getMaskingStrategy().isBlank()) {
            return intent.getMaskingStrategy();
        }
        return suggestion.getSuggestedStrategy() != null ? suggestion.getSuggestedStrategy() : "partial";
    }

    private Integer resolvePreserveChars(MaskingIntent intent, FieldSuggestion suggestion) {
        if (intent.getPreserveChars() != null) return intent.getPreserveChars();
        if (suggestion.getPreserveChars() != null) return suggestion.getPreserveChars();
        String strategy = resolveStrategy(intent, suggestion).toLowerCase();
        if (strategy.equals("full") || strategy.equals("hash")) return 0;
        // default heuristics
        String field = suggestion.getFieldName() != null ? suggestion.getFieldName().toLowerCase() : "";
        if (containsAnyIgnoreCase(field, "card", "ssn", "phone", "mobile", "tel")) return 4;
        if (field.contains("email")) return 1;
        return 2;
    }

    private String resolveMaskChar(MaskingIntent intent, FieldSuggestion suggestion) {
        if (intent.getMaskChar() != null && !intent.getMaskChar().isBlank()) return intent.getMaskChar();
        if (suggestion.getMaskChar() != null && !suggestion.getMaskChar().isBlank()) return suggestion.getMaskChar();
        return "*";
    }
    
    /**
     * Get active policies for an asset
     */
    public List<AIMaskingPolicy> getActivePolicies(Asset asset) {
        return policyRepository.findByAssetAndIsActiveTrue(asset);
    }
    
    /**
     * Get policies created by user
     */
    public List<AIMaskingPolicy> getPoliciesByUser(User user) {
        return policyRepository.findByCreatedBy(user);
    }
    
    /**
     * Deactivate policy
     */
    @Transactional
    public void deactivatePolicy(Long policyId) {
        AIMaskingPolicy policy = policyRepository.findById(policyId)
            .orElseThrow(() -> new AIMaskingPolicyException("Policy not found with id: " + policyId));
        
        policy.setIsActive(false);
        policyRepository.save(policy);
        
        log.info("Deactivated masking policy {}", policyId);
    }
    
    /**
     * Merge new roles with existing roles, avoiding duplicates
     */
    private String mergeRoles(String currentRoles, List<String> newRoles) {
        List<String> existing = new ArrayList<>();
        if (currentRoles != null && !currentRoles.trim().isEmpty()) {
            for (String role : currentRoles.split(",")) {
                String trimmed = role.trim();
                if (!trimmed.isEmpty() && !existing.contains(trimmed)) {
                    existing.add(trimmed);
                }
            }
        }
        
        for (String newRole : newRoles) {
            if (!existing.contains(newRole)) {
                existing.add(newRole);
            }
        }
        
        return String.join(",", existing);
    }
    
    /**
     * Get applicable masking policies for a user's role on a specific asset
     */
    public List<AIMaskingPolicy> getApplicablePolicies(Asset asset, String userRole) {
        return policyRepository.findByAssetAndIsActiveTrueAndTargetRoleContaining(asset, userRole);
    }
    
    /**
     * Get all active policies for an asset (for debugging)
     */
    public List<AIMaskingPolicy> getActivePoliciesForAsset(Asset asset) {
        return policyRepository.findByAssetAndIsActiveTrue(asset);
    }
    
    /**
     * Safe alternative to regex matching - checks if field contains any of the keywords
     */
    private boolean containsAnyIgnoreCase(String field, String... keywords) {
        if (field == null || field.isEmpty()) return false;
        String lowerField = field.toLowerCase();
        for (String keyword : keywords) {
            if (lowerField.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
} 