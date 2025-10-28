package com.verlake.dam.controller.asset_owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verlake.dam.entity.ai.AIMaskingPolicy;
import com.verlake.dam.entity.ai.dto.AIMaskingPolicyDTO;
import com.verlake.dam.entity.ai.FieldSuggestion;
import com.verlake.dam.entity.ai.MaskingIntent;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.entity.user.User;
import com.verlake.dam.exception.AIMaskingPolicyException;
import com.verlake.dam.repository.ai.AIMaskingPolicyRepository;
import com.verlake.dam.repository.assets.AssetRepository;
import com.verlake.dam.service.ai.AIMaskingPolicyService;
import com.verlake.dam.service.users.UserService;
import com.verlake.dam.utils.CommonUtils;
import com.verlake.dam.utils.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/asset_owner")
@Slf4j
public class OwnerMaskingPolicyController {

    @Autowired
    private AIMaskingPolicyRepository aiMaskingPolicyRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private AIMaskingPolicyService maskingPolicyService;
    @Autowired
    private AssetRepository assetRepository;

    /**
     * Get masking policies for assets owned by the current user
     */
    @GetMapping("/masking-policies")
    public ResponseEntity<Map<String, Object>> getMaskingPolicies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long assetId,
            @RequestParam(required = false) String status) {

        try {
            String currentUserEmail = CommonUtils.getEmailFromSession();
            User curUser = userService.getCurrentUser();
            log.info("Getting masking policies for user: {}, assetId: {}, status: {}",
                    currentUserEmail, assetId, status);

            Pageable pageable = PageRequest.of(page, size, Sort.by(Constants.SORT_FIELD_CREATED_AT).descending());

            Page<AIMaskingPolicy> policies;

            if (assetId != null) {
                // Get policies for specific asset
                if (Constants.STATUS_ACTIVE.equalsIgnoreCase(status)) {
                    policies = aiMaskingPolicyRepository.findByAssetIdAndIsActiveTrue(assetId, pageable);
                } else if (Constants.STATUS_INACTIVE.equalsIgnoreCase(status)) {
                    policies = aiMaskingPolicyRepository.findByAssetIdAndIsActiveFalse(assetId, pageable);
                } else {
                    policies = aiMaskingPolicyRepository.findByAssetId(assetId, pageable);
                }
            } else {
                // Get all policies for user's assets
                if (Constants.STATUS_ACTIVE.equalsIgnoreCase(status)) {
                    policies = aiMaskingPolicyRepository.findByCreatedByAndIsActiveTrue(curUser, pageable);
                } else if (Constants.STATUS_INACTIVE.equalsIgnoreCase(status)) {
                    policies = aiMaskingPolicyRepository.findByCreatedByAndIsActiveFalse(curUser, pageable);
                } else {
                    policies = aiMaskingPolicyRepository.findByCreatedBy(curUser, pageable);
                }
            }

            // Convert entities to DTOs to ensure consistent data structure
            List<AIMaskingPolicyDTO> policyDTOs = policies.getContent().stream()
                    .map(AIMaskingPolicyDTO::fromEntity)
                    .toList();

            Map<String, Object> response = new HashMap<>();
            response.put(Constants.FIELD_POLICIES, policyDTOs);
            response.put(Constants.FIELD_TOTAL_ELEMENTS, policies.getTotalElements());
            response.put(Constants.FIELD_TOTAL_PAGES, policies.getTotalPages());
            response.put(Constants.FIELD_CURRENT_PAGE, policies.getNumber());
            response.put(Constants.FIELD_SIZE, policies.getSize());

            return ResponseEntity.ok(response);

        } catch (AIMaskingPolicyException e) {
            log.error("AI Masking Policy error getting masking policies: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of(Constants.ERROR_FIELD_ERROR, Constants.ERROR_FAILED_TO_GET_MASKING_POLICIES));
        } catch (Exception e) {
            log.error("Unexpected error getting masking policies: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of(Constants.ERROR_FIELD_ERROR, Constants.ERROR_FAILED_TO_GET_MASKING_POLICIES));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String mapStrategy(String strategy) {
        if (strategy == null)
            return Constants.MASKING_STRATEGY_PARTIAL;
        String s = strategy.trim().toLowerCase();
        return switch (s) {
            case Constants.MASKING_STRATEGY_PARTIAL, Constants.MASKING_STRATEGY_FULL, Constants.MASKING_STRATEGY_HASH,
                    Constants.MASKING_STRATEGY_CUSTOM ->
                s;
            default -> Constants.MASKING_STRATEGY_PARTIAL;
        };
    }

    /**
     * Create masking policy using frontend MaskingPolicy request
     * Expects query param assetId and body matching the provided interface
     */
    @PostMapping("/masking-policies/{assetId}")
    public ResponseEntity<Map<String, Object>> createMaskingPolicies(
            @PathVariable Long assetId,
            @RequestBody JsonNode body) {
        try {
            User curUser = userService.getCurrentUser();
            Asset asset = validateAndGetAsset(assetId);
            
            List<AIMaskingPolicy> requests = parsePolicyRequests(body);
            List<AIMaskingPolicy> created = processPolicyRequests(requests, asset, curUser);

            return ResponseEntity.ok(Map.of(
                    Constants.FIELD_CREATED, created.size(),
                    Constants.FIELD_POLICIES, created));
        } catch (AIMaskingPolicyException e) {
            log.error("AI Masking Policy error creating masking policy: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(Constants.ERROR_FIELD_ERROR, e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error creating masking policy: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(Constants.ERROR_FIELD_ERROR, e.getMessage()));
        }
    }

    /**
     * Validate and get asset by ID
     */
    private Asset validateAndGetAsset(Long assetId) {
        return assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));
    }

    /**
     * Parse policy requests from JSON body
     */
    private List<AIMaskingPolicy> parsePolicyRequests(JsonNode body) {
        if (body == null || body.isNull()) {
            throw new IllegalArgumentException(Constants.ERROR_EMPTY_REQUEST_BODY);
        }

        ObjectMapper mapper = new ObjectMapper();
        List<AIMaskingPolicy> requests = new ArrayList<>();
        JsonNode payload = body;
        
        if (body.has(Constants.FIELD_POLICIES)) {
            payload = body.get(Constants.FIELD_POLICIES);
        }
        
        try {
            if (payload.isArray()) {
                for (JsonNode node : payload) {
                    requests.add(mapper.treeToValue(node, AIMaskingPolicy.class));
                }
            } else if (payload.isObject()) {
                requests.add(mapper.treeToValue(payload, AIMaskingPolicy.class));
            } else {
                throw new IllegalArgumentException(Constants.ERROR_INVALID_JSON_FORMAT);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse policy requests: " + e.getMessage());
        }
        
        return requests;
    }

    /**
     * Process policy requests and create masking policies
     */
    private List<AIMaskingPolicy> processPolicyRequests(List<AIMaskingPolicy> requests, Asset asset, User curUser) {
        List<AIMaskingPolicy> created = new ArrayList<>();

        for (AIMaskingPolicy request : requests) {
            if (!isValidPolicyRequest(request)) {
                continue; // skip invalid item
            }

            MaskingIntent intent = createMaskingIntent(request);
            FieldSuggestion suggestion = createFieldSuggestion(request);
            List<FieldSuggestion> suggestions = List.of(suggestion);
            
            List<String> roleNames = extractRoleNames(request);
            intent.setUserRole(String.join(",", roleNames));
            
            created.addAll(maskingPolicyService.applyMaskingPolicies(asset, intent, suggestions, curUser));
        }

        return created;
    }

    /**
     * Validate if policy request has required fields
     */
    private boolean isValidPolicyRequest(AIMaskingPolicy request) {
        return request != null && 
               !isBlank(request.getTableName()) && 
               !isBlank(request.getFieldName()) &&
               !isBlank(request.getMaskingStrategy());
    }

    /**
     * Create masking intent from policy request
     */
    private MaskingIntent createMaskingIntent(AIMaskingPolicy request) {
        return MaskingIntent.builder()
                .originalRequest(request.getOriginalRequest())
                .intentType(Constants.INTENT_TYPE_MANUAL_CREATE)
                .maskingStrategy(mapStrategy(request.getMaskingStrategy()))
                .pattern(request.getMaskingPattern())
                .preserveChars(request.getPreserveChars())
                .maskChar(request.getMaskChar())
                .confidence(1.0)
                .build();
    }

    /**
     * Create field suggestion from policy request
     */
    private FieldSuggestion createFieldSuggestion(AIMaskingPolicy request) {
        return FieldSuggestion.builder()
                .tableName(request.getTableName())
                .fieldName(request.getFieldName())
                .suggestedStrategy(mapStrategy(request.getMaskingStrategy()))
                .maskingPattern(request.getMaskingPattern())
                .preserveChars(request.getPreserveChars())
                .maskChar(request.getMaskChar())
                .confidence(0.99)
                .reason("Manual creation from frontend")
                .build();
    }

    /**
     * Extract role names from policy request
     */
    private List<String> extractRoleNames(AIMaskingPolicy request) {
        List<String> roleNames = new ArrayList<>();

        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            roleNames.addAll(request.getRoles().stream()
                    .map(role -> role.getName())
                    .toList());
        } else if (!isBlank(request.getTargetRole())) {
            for (String r : request.getTargetRole().split(",")) {
                if (!isBlank(r)) {
                    roleNames.add(r.trim());
                }
            }
        }

        if (roleNames.isEmpty()) {
            roleNames.add(Constants.ROLE_ALL);
        }

        return roleNames;
    }

    /**
     * Get masking policy by ID
     */
    @GetMapping("/masking-policies/{policyId}")
    public ResponseEntity<AIMaskingPolicy> getMaskingPolicy(@PathVariable Long policyId) {
        try {
            User curUser = userService.getCurrentUser();

            return aiMaskingPolicyRepository.findById(policyId)
                    .filter(policy -> policy.getCreatedBy() != null
                            && policy.getCreatedBy().getId().equals(curUser.getId()))
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());

        } catch (AIMaskingPolicyException e) {
            log.error("AI Masking Policy error getting masking policy {}: {}", policyId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error getting masking policy {}: {}", policyId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Update masking policy status
     */
    @PutMapping("/masking-policies/{policyId}/status")
    public ResponseEntity<AIMaskingPolicy> updatePolicyStatus(
            @PathVariable Long policyId,
            @RequestBody Map<String, Boolean> request) {

        try {
            User curUser = userService.getCurrentUser();
            Boolean isActive = request.get(Constants.FIELD_IS_ACTIVE);

            if (isActive == null) {
                return ResponseEntity.badRequest().build();
            }

            return aiMaskingPolicyRepository.findById(policyId)
                    .filter(policy -> policy.getCreatedBy() != null
                            && policy.getCreatedBy().getId().equals(curUser.getId()))
                    .map(policy -> {
                        policy.setIsActive(isActive);
                        return ResponseEntity.ok(aiMaskingPolicyRepository.save(policy));
                    })
                    .orElse(ResponseEntity.notFound().build());

        } catch (AIMaskingPolicyException e) {
            log.error("AI Masking Policy error updating status for policy {}: {}", policyId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error updating masking policy status {}: {}", policyId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Delete masking policy
     */
    @DeleteMapping("/masking-policies/{policyId}")
    public ResponseEntity<Map<String, Object>> deleteMaskingPolicy(@PathVariable Long policyId) {
        try {
            User curUser = userService.getCurrentUser();

            return aiMaskingPolicyRepository.findById(policyId)
                    .filter(policy -> policy.getCreatedBy() != null
                            && policy.getCreatedBy().getId().equals(curUser.getId()))
                    .map(policy -> {
                        aiMaskingPolicyRepository.delete(policy);
                        return CommonUtils.getSuccessResponse();
                    })
                    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of(Constants.JSON_FIELD_STATUS, Constants.JSON_FIELD_ERROR, Constants.JSON_FIELD_MESSAGE, "Policy not found")));

        } catch (AIMaskingPolicyException e) {
            log.error("AI Masking Policy error deleting policy {}: {}", policyId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of(Constants.JSON_FIELD_STATUS, Constants.JSON_FIELD_ERROR, Constants.JSON_FIELD_MESSAGE, e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error deleting masking policy {}: {}", policyId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of(Constants.JSON_FIELD_STATUS, Constants.JSON_FIELD_ERROR, Constants.JSON_FIELD_MESSAGE, "Failed to delete masking policy"));
        }
    }

    /**
     * Get masking policy statistics
     */
    @GetMapping("/masking-policies/stats")
    public ResponseEntity<Map<String, Object>> getMaskingPolicyStats() {
        try {
            String currentUserEmail = CommonUtils.getEmailFromSession();
            User curUser = userService.getCurrentUser();

            long totalPolicies = aiMaskingPolicyRepository.countByCreatedBy(curUser);
            long activePolicies = aiMaskingPolicyRepository.countByCreatedByAndIsActiveTrue(curUser);
            long inactivePolicies = aiMaskingPolicyRepository.countByCreatedByAndIsActiveFalse(curUser);

            Map<String, Object> stats = new HashMap<>();
            stats.put(Constants.FIELD_TOTAL_POLICIES, totalPolicies);
            stats.put(Constants.FIELD_ACTIVE_POLICIES, activePolicies);
            stats.put(Constants.FIELD_INACTIVE_POLICIES, inactivePolicies);
            stats.put(Constants.FIELD_USER_EMAIL, currentUserEmail);

            return ResponseEntity.ok(stats);

        } catch (AIMaskingPolicyException e) {
            log.error("AI Masking Policy error getting statistics: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of(Constants.ERROR_FIELD_ERROR, Constants.ERROR_FAILED_TO_GET_STATISTICS));
        } catch (Exception e) {
            log.error("Unexpected error getting masking policy stats: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of(Constants.ERROR_FIELD_ERROR, Constants.ERROR_FAILED_TO_GET_STATISTICS));
        }
    }
}