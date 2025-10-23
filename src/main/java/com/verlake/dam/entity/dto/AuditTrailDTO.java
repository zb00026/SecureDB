package com.verlake.dam.entity.dto;

import com.verlake.dam.entity.AuditTrail;
import com.verlake.dam.utils.Constants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for AuditTrail to avoid Hibernate proxy serialization issues
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditTrailDTO {
    private Long id;
    private LocalDateTime timestamp;
    private String instanceId;
    private String user;
    private String action;
    private String previousValue;
    private String newValue;
    private String actionMetadata;
    private boolean synced;
    private String ipAddress;
    private Long assetId; // Only the ID, not the full Asset object
    private String description; // Human-readable description of the audit event

    /**
     * Convert AuditTrail entity to DTO
     */
    public static AuditTrailDTO fromEntity(AuditTrail auditTrail) {
        return AuditTrailDTO.builder()
                .id(auditTrail.getId())
                .timestamp(auditTrail.getTimestamp())
                .instanceId(auditTrail.getInstanceId())
                .user(auditTrail.getUser())
                .action(auditTrail.getAction())
                .previousValue(auditTrail.getPreviousValue())
                .newValue(auditTrail.getNewValue())
                .actionMetadata(auditTrail.getActionMetadata())
                .synced(auditTrail.isSynced())
                .ipAddress(auditTrail.getIpAddress())
                .assetId(auditTrail.getAsset() != null ? auditTrail.getAsset().getId() : null)
                .description(generateDescription(auditTrail))
                .build();
    }

    /**
     * Generate human-readable description from audit trail fields
     */
    private static String generateDescription(AuditTrail auditTrail) {
        StringBuilder description = new StringBuilder();
        
        // Build base description
        appendActionDescription(description, auditTrail.getAction());
        appendAssetInformation(description, auditTrail);
        appendActionSpecificDetails(description, auditTrail);
        appendMetadataAndIp(description, auditTrail);
        
        return description.toString();
    }

    /**
     * Append action description to the builder
     */
    private static void appendActionDescription(StringBuilder description, String action) {
        if (action != null) {
            String actionText = getActionText(action);
            description.append(actionText);
        } else {
            description.append(Constants.AUDIT_DESC_PERFORMED_ACTION_ON);
        }
    }

    /**
     * Get human-readable action text
     */
    private static String getActionText(String action) {
        return switch (action.toUpperCase()) {
            case Constants.AUDIT_ACTION_TYPE_CREATE, Constants.AUDIT_ACTION_TYPE_CREATE_ASSET -> Constants.AUDIT_ACTION_DESC_CREATED;
            case Constants.AUDIT_ACTION_TYPE_UPDATE, Constants.AUDIT_ACTION_TYPE_UPDATE_ASSET -> Constants.AUDIT_ACTION_DESC_UPDATED;
            case Constants.AUDIT_ACTION_TYPE_DELETE, Constants.AUDIT_ACTION_TYPE_DELETE_ASSET -> Constants.AUDIT_ACTION_DESC_DELETED;
            case Constants.AUDIT_ACTION_TYPE_LOGIN -> Constants.AUDIT_ACTION_DESC_LOGGED_IN_TO;
            case Constants.AUDIT_ACTION_TYPE_LOGOUT -> Constants.AUDIT_ACTION_DESC_LOGGED_OUT_FROM;
            case Constants.AUDIT_ACTION_TYPE_QUERY, Constants.AUDIT_ACTION_DEVELOPER_QUERY_EXECUTION, Constants.AUDIT_ACTION_ASSET_OWNER_QUERY_EXECUTION -> Constants.AUDIT_ACTION_DESC_EXECUTED_QUERY_ON;
            case Constants.AUDIT_ACTION_TYPE_COMMAND -> Constants.AUDIT_ACTION_DESC_EXECUTED_COMMAND_ON;
            case Constants.AUDIT_ACTION_TYPE_APPROVE -> Constants.AUDIT_ACTION_DESC_APPROVED;
            case Constants.AUDIT_ACTION_TYPE_REJECT -> Constants.AUDIT_ACTION_DESC_REJECTED;
            case Constants.AUDIT_ACTION_TYPE_APPROVAL -> Constants.AUDIT_ACTION_DESC_APPROVAL_ACTION_ON;
            case Constants.AUDIT_ACTION_TYPE_AI_MASKING_APPLIED -> Constants.AUDIT_ACTION_DESC_APPLIED_AI_MASKING_TO;
            case Constants.AUDIT_ACTION_TYPE_DOWNLOAD -> Constants.AUDIT_ACTION_DESC_DOWNLOADED_FROM;
            case Constants.AUDIT_ACTION_TYPE_UPLOAD -> Constants.AUDIT_ACTION_DESC_UPLOADED_TO;
            default -> capitalizeWords(action);
        };
    }

    /**
     * Append asset information to the builder
     */
    private static void appendAssetInformation(StringBuilder description, AuditTrail auditTrail) {
        if (auditTrail.getAsset() != null) {
            description.append(" asset '").append(auditTrail.getAsset().getName()).append("'");
        } else if (auditTrail.getInstanceId() != null) {
            description.append(" ").append(auditTrail.getInstanceId());
        }
    }

    /**
     * Append action-specific details to the builder
     */
    private static void appendActionSpecificDetails(StringBuilder description, AuditTrail auditTrail) {
        String action = auditTrail.getAction();
        
        if (isUpdateAction(action)) {
            appendUpdateDetails(description, auditTrail);
        } else if (isQueryExecutionAction(action)) {
            appendQueryDetails(description, auditTrail);
        } else if (Constants.AUDIT_ACTION_TYPE_COMMAND.equals(action)) {
            appendCommandDetails(description, auditTrail);
        } else if (isApprovalAction(action)) {
            appendApprovalDetails(description, auditTrail);
        }
    }

    /**
     * Check if action is an update action
     */
    private static boolean isUpdateAction(String action) {
        return Constants.AUDIT_ACTION_TYPE_UPDATE.equals(action) || Constants.AUDIT_ACTION_TYPE_UPDATE_ASSET.equals(action);
    }

    /**
     * Append update details to the builder
     */
    private static void appendUpdateDetails(StringBuilder description, AuditTrail auditTrail) {
        if (auditTrail.getPreviousValue() != null && auditTrail.getNewValue() != null) {
            description.append(" (changed from '").append(truncateValue(auditTrail.getPreviousValue(), 50))
                      .append("' to '").append(truncateValue(auditTrail.getNewValue(), 50)).append("')");
        }
    }

    /**
     * Append query details to the builder
     */
    private static void appendQueryDetails(StringBuilder description, AuditTrail auditTrail) {
        if (auditTrail.getNewValue() != null) {
            String queryDetails = extractQueryFromNewValue(auditTrail.getNewValue());
            if (queryDetails != null) {
                description.append(" - Query: ").append(truncateQuery(queryDetails));
            }
        }
    }

    /**
     * Append command details to the builder
     */
    private static void appendCommandDetails(StringBuilder description, AuditTrail auditTrail) {
        if (auditTrail.getNewValue() != null) {
            description.append(" (command: ").append(truncateValue(auditTrail.getNewValue(), 100)).append(")");
        }
    }

    /**
     * Append approval details to the builder
     */
    private static void appendApprovalDetails(StringBuilder description, AuditTrail auditTrail) {
        description.append(" - ").append(getApprovalContext(auditTrail));
    }

    /**
     * Append metadata and IP address to the builder
     */
    private static void appendMetadataAndIp(StringBuilder description, AuditTrail auditTrail) {
        if (auditTrail.getActionMetadata() != null && !auditTrail.getActionMetadata().isEmpty()) {
            description.append(" - ").append(auditTrail.getActionMetadata());
        }
        
        if (auditTrail.getIpAddress() != null && !auditTrail.getIpAddress().isEmpty()) {
            description.append(" from IP ").append(auditTrail.getIpAddress());
        }
    }
    
    /**
     * Capitalize only the first letter of the text
     */
    private static String capitalizeWords(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
    
    /**
     * Truncate long values for better readability
     */
    private static String truncateValue(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
    
    /**
     * Check if the action is a query execution action
     */
    private static boolean isQueryExecutionAction(String action) {
        return Constants.AUDIT_ACTION_TYPE_QUERY.equals(action) || 
               Constants.AUDIT_ACTION_DEVELOPER_QUERY_EXECUTION.equals(action) || 
               Constants.AUDIT_ACTION_ASSET_OWNER_QUERY_EXECUTION.equals(action);
    }
    
    /**
     * Check if the action is an approval action
     */
    private static boolean isApprovalAction(String action) {
        return Constants.AUDIT_ACTION_TYPE_APPROVE.equals(action) || 
               Constants.AUDIT_ACTION_TYPE_REJECT.equals(action) || 
               Constants.AUDIT_ACTION_TYPE_APPROVAL.equals(action);
    }
    
    /**
     * Extract query from newValue JSON string
     */
    private static String extractQueryFromNewValue(String newValue) {
        if (newValue == null) {
            return null;
        }
        
        // Try to find SQL keywords in the JSON string
        if (newValue.contains(Constants.SQL_KEYWORD_SELECT) || newValue.contains(Constants.SQL_KEYWORD_INSERT) || 
            newValue.contains(Constants.SQL_KEYWORD_UPDATE) || newValue.contains(Constants.SQL_KEYWORD_DELETE)) {
            // Extract the query part from JSON
            String[] lines = newValue.split("\n");
            for (String line : lines) {
                if (line.trim().startsWith(Constants.SQL_KEYWORD_SELECT) || line.trim().startsWith(Constants.SQL_KEYWORD_INSERT) || 
                    line.trim().startsWith(Constants.SQL_KEYWORD_UPDATE) || line.trim().startsWith(Constants.SQL_KEYWORD_DELETE)) {
                    return line.trim();
                }
            }
        }
        
        return null;
    }
    
    /**
     * Truncate long queries to show first 25 chars, ..., and last 25 chars
     */
    private static String truncateQuery(String query) {
        if (query == null || query.length() <= 50) {
            return query;
        }
        
        String trimmedQuery = query.trim();
        if (trimmedQuery.length() <= 50) {
            return trimmedQuery;
        }
        
        String firstPart = trimmedQuery.substring(0, 25);
        String lastPart = trimmedQuery.substring(trimmedQuery.length() - 25);
        return firstPart + "..." + lastPart;
    }
    
    /**
     * Get approval context for approval actions
     */
    private static String getApprovalContext(AuditTrail auditTrail) {
        if (Constants.AUDIT_ACTION_TYPE_APPROVE.equals(auditTrail.getAction())) {
            return Constants.AUDIT_APPROVAL_ACCESS_REQUEST_APPROVED;
        } else if (Constants.AUDIT_ACTION_TYPE_REJECT.equals(auditTrail.getAction())) {
            return Constants.AUDIT_APPROVAL_ACCESS_REQUEST_REJECTED;
        } else if (Constants.AUDIT_ACTION_TYPE_APPROVAL.equals(auditTrail.getAction())) {
            return Constants.AUDIT_APPROVAL_STATUS_UPDATED;
        }
        return Constants.AUDIT_APPROVAL_ACTION_PERFORMED;
    }
} 