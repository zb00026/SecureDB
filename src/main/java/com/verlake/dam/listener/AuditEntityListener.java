package com.verlake.dam.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.verlake.dam.annotation.Audited;
import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.entity.assets.AccessRequest;
import com.verlake.dam.entity.assets.Asset;
import com.verlake.dam.enums.AuditAction;
import com.verlake.dam.service.audit_trail.AuditTrailService;
import com.verlake.dam.utils.AuditDescriptionUtils;
import com.verlake.dam.utils.Constants;
import com.verlake.dam.utils.IpAddressUtils;
import com.verlake.dam.utils.SpringContext;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class AuditEntityListener {
    private static final ThreadLocal<Map<String, String>> SNAPSHOTS =
            ThreadLocal.withInitial(HashMap::new);
    
    // Use a static ObjectMapper instance for JPA listeners
    // JPA listeners are instantiated before Spring context is fully initialized,
    // so we can't use dependency injection or SpringContext
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();
    
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }
    
    private ObjectMapper getObjectMapper() {
        // Try to get from Spring context if available, otherwise use static instance
        try {
            return SpringContext.getBean(ObjectMapper.class);
        } catch (Exception e) {
            // Fallback to static instance if SpringContext is not available
            // This happens during JPA entity scanning before Spring context is initialized
            return OBJECT_MAPPER;
        }
    }

    private String snapshotKey(Object entity) {
        try {
            Object id = entity.getClass().getMethod(Constants.METHOD_GET_ID).invoke(entity);
            return entity.getClass().getName() + "#" + id;
        } catch (Exception e) {
            return entity.getClass().getName() + "#unknown";
        }
    }

    @PostLoad
    public void postLoad(Object target) {
        if (target.getClass().isAnnotationPresent(Audited.class)) {
            try {
                ObjectMapper mapper = getObjectMapper();
                String json = mapper.writeValueAsString(target);
                SNAPSHOTS.get().put(snapshotKey(target), json);
            } catch (Exception ignored) {
                // Silently ignore serialization failures for snapshotting
                // This is non-critical - if snapshotting fails, audit will proceed without previousValue
            }
        }
    }
    
    @PrePersist
    public void prePersist(Object target) {
        if (target.getClass().isAnnotationPresent(Audited.class)) {
            createAuditTrail(target, AuditAction.CREATE, null);
        }
    }

    @PreUpdate
    public void preUpdate(Object target) {
        if (target.getClass().isAnnotationPresent(Audited.class)) {
            String previousValue = getPreviousValue(target);
            createAuditTrail(target, AuditAction.UPDATE, previousValue);
        }
    }

    @PreRemove
    public void preRemove(Object target) {
        if (target.getClass().isAnnotationPresent(Audited.class)) {
            String previousValue;
            try {
                ObjectMapper mapper = getObjectMapper();
                previousValue = mapper.writeValueAsString(target);
            } catch (Exception e) {
                previousValue = null;
            }
            createAuditTrail(target, AuditAction.DELETE, previousValue);
        }
    }

    private String getPreviousValue(Object target) {
        String key = snapshotKey(target);
        String snap = SNAPSHOTS.get().get(key);
        // Clear snapshot after use to avoid memory leaks
        SNAPSHOTS.get().remove(key);
        return snap;
    }

    private void createAuditTrail(Object target, AuditAction action, String previousValue) {
        try {
            Audited audited = target.getClass().getAnnotation(Audited.class);
            
            String username = getCurrentUsername();
            String ipAddress = getCurrentIpAddress();
            
            // Get special identifier for the entity
            String specialIdentifier = getSpecialIdentifier(target);
            String entityName = audited.entity();
            String formattedInstanceId = String.format("%s(%s)", entityName, specialIdentifier);

            ObjectMapper mapper = getObjectMapper();
            
            // Extract asset entity if available
            Asset assetEntity = extractAsset(target);
            
            // Use enhanced audit action constants for better descriptions
            String actionDescription = getEnhancedActionDescription(action, audited.entity(), target);
            
        AuditTrail audit = AuditTrail.builder()
                .timestamp(LocalDateTime.now())
                .user(username)
                .action(actionDescription)
                .instanceId(formattedInstanceId)
                .actionMetadata(audited.entity())
                .previousValue(previousValue)
                .newValue(action != AuditAction.DELETE ? mapper.writeValueAsString(target) : null)
                .ipAddress(ipAddress)
                .asset(assetEntity)
                .description(AuditDescriptionUtils.generateDescription(actionDescription, audited.entity(), action != AuditAction.DELETE ? mapper.writeValueAsString(target) : null))
                // readableDescription will be computed at read-time (DTO) using current system timezone
                .build();

            AuditTrailService auditService = SpringContext.getBean(AuditTrailService.class);
            auditService.save(audit);
        } catch (Exception e) {
            log.error("Failed to create audit trail for {} action on {}: ", 
                action, target.getClass().getSimpleName(), e);
        }
    }

    private String getCurrentUsername() {
        // Default to "system" in cases where:
        // 1. No security context exists (e.g., background jobs, scheduled tasks)
        // 2. No authentication is present
        // 3. Principal is not a JWT token (e.g., during system initialization)
        String username = Constants.AUDIT_SYSTEM_USER;
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof Jwt) {
                username = ((Jwt) principal).getClaimAsString("email");
            }
        }
        return username;
    }

    private String getCurrentIpAddress() {
        return IpAddressUtils.getCurrentIpAddress();
    }

    private String getEntityId(Object target) {
        try {
            return target.getClass().getMethod(Constants.METHOD_GET_ID).invoke(target).toString();
        } catch (Exception e) {
            return Constants.AUDIT_UNKNOWN_ENTITY;
        }
    }

    /**
     * Extracts asset entity from the target entity if it's asset-related
     */
    private Asset extractAsset(Object target) {
        try {
            // Direct asset entity
            if (target instanceof Asset asset) {
                return asset;
            }
            
            // Access requests related to assets
            if (target instanceof AccessRequest) {
                Object asset = target.getClass().getMethod(Constants.METHOD_GET_ASSET).invoke(target);
                if (asset instanceof Asset assetEntity) {
                    return assetEntity;
                }
            }
            
            // Asset-related entities (AssetCredential, AssetApprover, etc.)
            if (hasAssetField(target)) {
                Object asset = target.getClass().getMethod(Constants.METHOD_GET_ASSET).invoke(target);
                if (asset instanceof Asset assetEntity) {
                    return assetEntity;
                }
            }
            
        } catch (Exception e) {
            log.debug("Could not extract asset from {}: {}", target.getClass().getSimpleName(), e.getMessage());
        }
        return null;
    }

    /**
     * Checks if the target entity has an asset field
     */
    private boolean hasAssetField(Object target) {
        try {
            target.getClass().getMethod(Constants.METHOD_GET_ASSET);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Gets a special identifier for the entity based on its type
     */
    private String getSpecialIdentifier(Object target) {
        try {
            String className = target.getClass().getSimpleName();
            
            switch (className) {
                case Constants.ENTITY_CLASS_ASSET:
                    return getAssetName(target);
                case Constants.ENTITY_CLASS_USER:
                    return getUserEmail(target);
                case Constants.ENTITY_CLASS_ROLE:
                    return getRoleName(target);
                case Constants.ENTITY_CLASS_ACCESS_REQUEST:
                    return getAccessRequestIdentifier(target);
                case Constants.ENTITY_CLASS_ASSET_CREDENTIAL:
                    return getAssetCredentialIdentifier(target);
                case Constants.ENTITY_CLASS_ASSET_APPROVER:
                    return getAssetApproverIdentifier(target);
                case Constants.ENTITY_CLASS_AI_PROMPT:
                    return getAIPromptKey(target);
                case Constants.ENTITY_CLASS_AI_SENSITIVE_PATTERN:
                    return getAISensitivePatternName(target);
                case Constants.ENTITY_CLASS_AI_CATEGORY:
                    return getAICategoryName(target);
                case Constants.ENTITY_CLASS_EMAIL:
                    return getEmailIdentifier(target);
                default:
                    // Fallback to ID for unknown entities
                    return getEntityId(target);
            }
        } catch (Exception e) {
            log.debug("Could not get special identifier for {}: {}", target.getClass().getSimpleName(), e.getMessage());
            return getEntityId(target);
        }
    }

    /**
     * Gets action description using Constants.java audit action variables
     */
    private String getActionDescription(AuditAction action, String entityType) {
        switch (action) {
            case CREATE:
                return Constants.ACTION_PREFIX_CREATE + entityType;
            case UPDATE:
                return Constants.ACTION_PREFIX_UPDATE + entityType;
            case DELETE:
                return Constants.ACTION_PREFIX_DELETE + entityType;
            default:
                return action.name();
        }
    }

    /**
     * Gets enhanced action description using specific audit action constants
     */
    private String getEnhancedActionDescription(AuditAction action, String entityType, Object target) {
        try {
            // Check for specific audit action constants based on entity type and context
            switch (entityType) {
                case Constants.ENTITY_TYPE_ACCESS_REQUEST:
                    return getAccessRequestActionDescription(action, target);
                case Constants.ENTITY_TYPE_ASSET:
                    return getAssetActionDescription(action);
                case Constants.ENTITY_TYPE_USER:
                    return getUserActionDescription(action);
                case Constants.ENTITY_TYPE_ASSET_CREDENTIAL:
                    return getAssetCredentialActionDescription(action);
                default:
                    return getActionDescription(action, entityType);
            }
        } catch (Exception e) {
            log.debug("Could not get enhanced action description: {}", e.getMessage());
            return getActionDescription(action, entityType);
        }
    }

    private String getAccessRequestActionDescription(AuditAction action, Object target) {
        try {
            // Check if this is an approval/rejection action
            Object accessorStatus = target.getClass().getMethod(Constants.METHOD_GET_ACCESSOR_APPROVER_STATUS).invoke(target);
            Object assetStatus = target.getClass().getMethod(Constants.METHOD_GET_ASSET_APPROVER_STATUS).invoke(target);
            
            if (action == AuditAction.UPDATE) {
                if (accessorStatus != null && accessorStatus.toString().equals(Constants.APPROVAL_STATUS_APPROVED)) {
                    return Constants.AUDIT_ACTION_APPROVE;
                } else if (accessorStatus != null && accessorStatus.toString().equals(Constants.APPROVAL_STATUS_REJECTED)) {
                    return Constants.AUDIT_ACTION_REJECT;
                } else if (assetStatus != null && assetStatus.toString().equals(Constants.APPROVAL_STATUS_APPROVED)) {
                    return Constants.AUDIT_ACTION_APPROVE;
                } else if (assetStatus != null && assetStatus.toString().equals(Constants.APPROVAL_STATUS_REJECTED)) {
                    return Constants.AUDIT_ACTION_REJECT;
                }
            }
            return getActionDescription(action, Constants.ENTITY_TYPE_ACCESS_REQUEST);
        } catch (Exception e) {
            return getActionDescription(action, Constants.ENTITY_TYPE_ACCESS_REQUEST);
        }
    }

    private String getAssetActionDescription(AuditAction action) {
        return getActionDescription(action, Constants.ENTITY_TYPE_ASSET);
    }

    private String getUserActionDescription(AuditAction action) {
        return getActionDescription(action, Constants.ENTITY_TYPE_USER);
    }

    private String getAssetCredentialActionDescription(AuditAction action) {
        return getActionDescription(action, Constants.ENTITY_TYPE_ASSET_CREDENTIAL);
    }

    // Entity-specific identifier methods
    private String getAssetName(Object target) {
        try {
            Object name = target.getClass().getMethod(Constants.METHOD_GET_NAME).invoke(target);
            return name != null ? name.toString() : Constants.DEFAULT_UNKNOWN_ASSET;
        } catch (Exception e) {
            return Constants.ENTITY_PREFIX_ASSET + getEntityId(target);
        }
    }

    private String getUserEmail(Object target) {
        try {
            Object email = target.getClass().getMethod(Constants.METHOD_GET_EMAIL).invoke(target);
            return email != null ? email.toString() : Constants.DEFAULT_UNKNOWN_USER;
        } catch (Exception e) {
            return Constants.ENTITY_PREFIX_USER + getEntityId(target);
        }
    }

    private String getRoleName(Object target) {
        try {
            Object name = target.getClass().getMethod(Constants.METHOD_GET_NAME).invoke(target);
            return name != null ? name.toString() : Constants.DEFAULT_UNKNOWN_ROLE;
        } catch (Exception e) {
            return Constants.ENTITY_PREFIX_ROLE + getEntityId(target);
        }
    }

    private String getAccessRequestIdentifier(Object target) {
        try {
            // Try to get asset name first
            Object asset = target.getClass().getMethod(Constants.METHOD_GET_ASSET).invoke(target);
            if (asset != null) {
                Object assetName = asset.getClass().getMethod(Constants.METHOD_GET_NAME).invoke(asset);
                if (assetName != null) {
                    return Constants.ENTITY_DESC_REQUEST_FOR + assetName;
                }
            }
            // Fallback to ID
            return Constants.ENTITY_PREFIX_ACCESS_REQUEST + getEntityId(target);
        } catch (Exception e) {
            return Constants.ENTITY_PREFIX_ACCESS_REQUEST + getEntityId(target);
        }
    }

    private String getAssetCredentialIdentifier(Object target) {
        try {
            // Try to get asset name first
            Object asset = target.getClass().getMethod(Constants.METHOD_GET_ASSET).invoke(target);
            if (asset != null) {
                Object assetName = asset.getClass().getMethod(Constants.METHOD_GET_NAME).invoke(asset);
                if (assetName != null) {
                    return Constants.ENTITY_DESC_CREDENTIAL_FOR + assetName;
                }
            }
            // Fallback to ID
            return Constants.ENTITY_PREFIX_ASSET_CREDENTIAL + getEntityId(target);
        } catch (Exception e) {
            return Constants.ENTITY_PREFIX_ASSET_CREDENTIAL + getEntityId(target);
        }
    }

    private String getAssetApproverIdentifier(Object target) {
        try {
            // Try to get asset name first
            Object asset = target.getClass().getMethod(Constants.METHOD_GET_ASSET).invoke(target);
            if (asset != null) {
                Object assetName = asset.getClass().getMethod(Constants.METHOD_GET_NAME).invoke(asset);
                if (assetName != null) {
                    return Constants.ENTITY_DESC_APPROVER_FOR + assetName;
                }
            }
            // Fallback to ID
            return Constants.ENTITY_PREFIX_ASSET_APPROVER + getEntityId(target);
        } catch (Exception e) {
            return Constants.ENTITY_PREFIX_ASSET_APPROVER + getEntityId(target);
        }
    }

    private String getAIPromptKey(Object target) {
        try {
            Object promptKey = target.getClass().getMethod(Constants.METHOD_GET_PROMPT_KEY).invoke(target);
            return promptKey != null ? promptKey.toString() : Constants.DEFAULT_UNKNOWN_PROMPT;
        } catch (Exception e) {
            return Constants.ENTITY_PREFIX_AI_PROMPT + getEntityId(target);
        }
    }

    private String getAISensitivePatternName(Object target) {
        try {
            Object name = target.getClass().getMethod(Constants.METHOD_GET_NAME).invoke(target);
            return name != null ? name.toString() : Constants.DEFAULT_UNKNOWN_PATTERN;
        } catch (Exception e) {
            return Constants.ENTITY_PREFIX_AI_SENSITIVE_PATTERN + getEntityId(target);
        }
    }

    private String getAICategoryName(Object target) {
        try {
            Object name = target.getClass().getMethod(Constants.METHOD_GET_NAME).invoke(target);
            return name != null ? name.toString() : Constants.DEFAULT_UNKNOWN_CATEGORY;
        } catch (Exception e) {
            return Constants.ENTITY_PREFIX_AI_CATEGORY + getEntityId(target);
        }
    }

    private String getEmailIdentifier(Object target) {
        try {
            // Try to get recipient email first
            Object recipientEmail = target.getClass().getMethod(Constants.METHOD_GET_EMAIL_TO).invoke(target);
            if (recipientEmail != null) {
                // Use the raw recipient email as the instance identifier content
                return recipientEmail.toString();
            }
            // Fallback to ID
            return Constants.ENTITY_PREFIX_EMAIL + getEntityId(target);
        } catch (Exception e) {
            return Constants.ENTITY_PREFIX_EMAIL + getEntityId(target);
        }
    }

} 