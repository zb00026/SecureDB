package com.verlake.dam.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.verlake.dam.annotation.Audited;
import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.enums.AuditAction;
import com.verlake.dam.service.audit_trail.AuditTrailService;
import com.verlake.dam.utils.SpringContext;
import jakarta.persistence.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import jakarta.persistence.EntityManager;

@Slf4j
public class AuditEntityListener {
    private final ObjectMapper mapper = new ObjectMapper();
    
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
            String previousValue = getPreviousValue(target);
            createAuditTrail(target, AuditAction.DELETE, previousValue);
        }
    }

    private String getPreviousValue(Object target) {
        try {
            EntityManager em = SpringContext.getBean(EntityManager.class);
            Object id = target.getClass().getMethod("getId").invoke(target);
            Object originalEntity = em.find(target.getClass(), id);
            mapper.registerModule(new JavaTimeModule());
            return mapper.writeValueAsString(originalEntity);
        } catch (Exception e) {
            // If the previous entity is not found, return null, ex: create entity
            return null;
        }
    }

    private void createAuditTrail(Object target, AuditAction action, String previousValue) {
        try {
            Audited audited = target.getClass().getAnnotation(Audited.class);
            
            String username = getCurrentUsername();
            String ipAddress = getCurrentIpAddress();
            
            // Format the instanceId as "ENTITY NAME(6)"
            String entityName = audited.entity();
            String entityId = getEntityId(target);
            String formattedInstanceId = String.format("%s(%s)", entityName, entityId);

            mapper.registerModule(new JavaTimeModule());
            
            AuditTrail audit = AuditTrail.builder()
                .timestamp(LocalDateTime.now())
                .user(username)
                .action(action.name())
                .instanceId(formattedInstanceId)
                .actionMetadata(audited.entity())
                .previousValue(previousValue)
                .newValue(action != AuditAction.DELETE ? mapper.writeValueAsString(target) : null)
                .ipAddress(ipAddress)
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
        String username = "system";
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof Jwt) {
                username = ((Jwt) principal).getClaimAsString("email");
            }
        }
        return username;
    }

    private String getCurrentIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attributes.getRequest().getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getEntityId(Object target) {
        try {
            return target.getClass().getMethod("getId").invoke(target).toString();
        } catch (Exception e) {
            return "unknown";
        }
    }
} 