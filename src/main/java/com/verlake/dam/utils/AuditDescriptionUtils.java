package com.verlake.dam.utils;

import java.time.LocalDateTime;

public final class AuditDescriptionUtils {

    private AuditDescriptionUtils() {}

    /**
     * Generate simple description for audit trail
     * @param action The audit action (e.g., CREATE_ASSET, QUERY_EXECUTED, APPROVE, etc.)
     * @param entityType The entity type from actionMetadata (e.g., ASSET, USER, ACCESS_REQUEST, etc.)
     * @param newValue The new value JSON string (for queries, contains the SQL)
     * @return Simple description string
     */
    public static String generateDescription(String action, String entityType, String newValue) {
        if (action == null) {
            return "";
        }

        String upperAction = action.toUpperCase();
        String upperEntity = entityType != null ? entityType.toUpperCase() : "";

        // Try specialized handlers first
        String result = handleQueryAction(upperAction, newValue);
        if (result != null) return result;

        result = handleCommandAction(upperAction);
        if (result != null) return result;

        result = handleMaskingAction(upperAction);
        if (result != null) return result;

        result = handleApprovalAction(upperAction, upperEntity);
        if (result != null) return result;

        result = handleLoginLogoutAction(upperAction);
        if (result != null) return result;

        result = handleCrudAction(upperAction);
        if (result != null) return result;

        result = handleDownloadUploadAction(upperAction);
        if (result != null) return result;

        // Default: format the action
        return capitalizeFirst(action.replace('_', ' '));
    }

    private static String handleQueryAction(String upperAction, String newValue) {
        if (upperAction.contains("QUERY")) {
            String extracted = extractQuery(newValue);
            return extracted != null ? extracted : "Query";
        }
        return null;
    }

    private static String handleCommandAction(String upperAction) {
        if (upperAction.contains("COMMAND")) {
            return "Command execution";
        }
        return null;
    }

    private static String handleMaskingAction(String upperAction) {
        if (upperAction.contains("DATA_ACCESS_WITH_MASKING")) {
            return "Masking policy was applied";
        }
        if (upperAction.contains("MASK") || upperAction.contains("AI_MASKING")) {
            return "Setup data masking policy";
        }
        return null;
    }

    private static String handleApprovalAction(String upperAction, String upperEntity) {
        if (upperAction.equals(Constants.AUDIT_ACTION_APPROVE) || upperAction.equals(Constants.AUDIT_ACTION_TYPE_APPROVE)) {
            return upperEntity.contains("ACCESS_REQUEST") ? "Approved access request" : "Approved";
        }
        if (upperAction.equals(Constants.AUDIT_ACTION_REJECT) || upperAction.equals(Constants.AUDIT_ACTION_TYPE_REJECT)) {
            return upperEntity.contains("ACCESS_REQUEST") ? "Rejected access request" : "Rejected";
        }
        if (upperAction.equals(Constants.AUDIT_ACTION_APPROVAL) || upperAction.equals(Constants.AUDIT_ACTION_TYPE_APPROVAL)) {
            return "Approval action";
        }
        return null;
    }

    private static String handleLoginLogoutAction(String upperAction) {
        if (upperAction.equals(Constants.AUDIT_ACTION_TYPE_LOGIN)) {
            return "Login";
        }
        if (upperAction.equals(Constants.AUDIT_ACTION_TYPE_LOGOUT)) {
            return "Logout";
        }
        return null;
    }

    private static String handleCrudAction(String upperAction) {
        if (upperAction.startsWith("CREATE_")) {
            String entity = upperAction.substring(7).replace("_", " ");
            return "Created " + formatEntityName(entity);
        }
        if (upperAction.startsWith("UPDATE_")) {
            String entity = upperAction.substring(7).replace("_", " ");
            return "Updated " + formatEntityName(entity);
        }
        if (upperAction.startsWith("DELETE_")) {
            String entity = upperAction.substring(7).replace("_", " ");
            return "Deleted " + formatEntityName(entity);
        }
        return null;
    }

    private static String handleDownloadUploadAction(String upperAction) {
        if (upperAction.equals(Constants.AUDIT_ACTION_TYPE_DOWNLOAD)) {
            return "Downloaded";
        }
        if (upperAction.equals(Constants.AUDIT_ACTION_TYPE_UPLOAD)) {
            return "Uploaded";
        }
        return null;
    }

    /**
     * Generate human-readable description for audit trail
     * @param action The audit action
     * @param entityType The entity type from actionMetadata
     * @param instanceId The instance identifier
     * @param userEmail The user email who performed the action
     * @param timestamp The timestamp when the action occurred
     * @param timezoneConverter The timezone converter for formatting timestamp
     * @return Human-readable description string
     */
    public static String generateReadableDescription(String action, String entityType, String instanceId, 
                                                     String userEmail, LocalDateTime timestamp, 
                                                     TimezoneConverter timezoneConverter) {
        String timeStr = timestamp != null && timezoneConverter != null 
            ? timezoneConverter.convertToSystemTimezoneString(timestamp) 
            : "";
        String actor = buildActor(userEmail, entityType);
        String upperAction = action != null ? action.toUpperCase() : "";
        String upperEntity = entityType != null ? entityType.toUpperCase() : "";
        String timeSuffix = timeStr.isEmpty() ? "" : " at " + timeStr;
        return composeReadable(upperAction, upperEntity, instanceId, actor, timeSuffix);
    }

    /**
     * Generate human-readable description base (without time suffix).
     */
    public static String generateReadableBase(String action, String entityType, String instanceId, String userEmail) {
        String actor = buildActor(userEmail, entityType);
        String upperAction = action != null ? action.toUpperCase() : "";
        String upperEntity = entityType != null ? entityType.toUpperCase() : "";
        return composeReadable(upperAction, upperEntity, instanceId, actor, "");
    }

    private static String buildActor(String userEmail, String entityType) {
        String user = safe(userEmail);
        boolean isSystem = Constants.AUDIT_SYSTEM_USER.equals(user) ||
                (Constants.ENTITY_TYPE_EMAIL.equalsIgnoreCase(entityType));
        return isSystem ? "System" : ("User " + user);
    }

    private static String composeReadable(String upperAction, String upperEntity, String instanceId, String actor, String timeSuffix) {
        // Try specialized handlers first
        String result = handleQueryReadable(upperAction, actor, timeSuffix);
        if (result != null) return result;

        result = handleCommandReadable(upperAction, actor, timeSuffix);
        if (result != null) return result;

        result = handleMaskingReadable(upperAction, actor, timeSuffix);
        if (result != null) return result;

        result = handleApprovalReadable(upperAction, upperEntity, instanceId, actor, timeSuffix);
        if (result != null) return result;

        result = handleLoginLogoutReadable(upperAction, actor, timeSuffix);
        if (result != null) return result;

        result = handleCrudReadable(upperAction, upperEntity, instanceId, actor, timeSuffix);
        if (result != null) return result;

        result = handleDownloadUploadReadable(upperAction, upperEntity, instanceId, actor, timeSuffix);
        if (result != null) return result;

        // Default handling
        if (!upperAction.isEmpty()) {
            return String.format("%s performed %s%s", actor, capitalizeFirst(upperAction.replace('_', ' ').toLowerCase()), timeSuffix);
        }
        return String.format("%s performed action%s", actor, timeSuffix);
    }

    private static String handleQueryReadable(String upperAction, String actor, String timeSuffix) {
        if (upperAction.contains("QUERY")) {
            return String.format("%s executed query%s", actor, timeSuffix);
        }
        return null;
    }

    private static String handleCommandReadable(String upperAction, String actor, String timeSuffix) {
        if (upperAction.contains("COMMAND")) {
            return String.format("%s executed command%s", actor, timeSuffix);
        }
        return null;
    }

    private static String handleMaskingReadable(String upperAction, String actor, String timeSuffix) {
        if (upperAction.contains("DATA_ACCESS_WITH_MASKING")) {
            return String.format("%s applied data masking policy%s", actor, timeSuffix);
        }
        if (upperAction.contains("MASK") || upperAction.contains("AI_MASKING")) {
            return String.format("%s setup data masking policy%s", actor, timeSuffix);
        }
        return null;
    }

    private static String handleApprovalReadable(String upperAction, String upperEntity, String instanceId, String actor, String timeSuffix) {
        if (upperAction.equals(Constants.AUDIT_ACTION_APPROVE) || upperAction.equals(Constants.AUDIT_ACTION_TYPE_APPROVE)) {
            String entityInfo = formatInstanceIdForReadable(instanceId, upperEntity);
            return String.format("%s approved %s%s", actor, entityInfo, timeSuffix);
        }
        if (upperAction.equals(Constants.AUDIT_ACTION_REJECT) || upperAction.equals(Constants.AUDIT_ACTION_TYPE_REJECT)) {
            String entityInfo = formatInstanceIdForReadable(instanceId, upperEntity);
            return String.format("%s rejected %s%s", actor, entityInfo, timeSuffix);
        }
        if (upperAction.equals(Constants.AUDIT_ACTION_APPROVAL) || upperAction.equals(Constants.AUDIT_ACTION_TYPE_APPROVAL)) {
            return String.format("%s performed approval action%s", actor, timeSuffix);
        }
        return null;
    }

    private static String handleLoginLogoutReadable(String upperAction, String actor, String timeSuffix) {
        if (upperAction.equals(Constants.AUDIT_ACTION_TYPE_LOGIN)) {
            return String.format("%s logged in%s", actor, timeSuffix);
        }
        if (upperAction.equals(Constants.AUDIT_ACTION_TYPE_LOGOUT)) {
            return String.format("%s logged out%s", actor, timeSuffix);
        }
        return null;
    }

    private static String handleCrudReadable(String upperAction, String upperEntity, String instanceId, String actor, String timeSuffix) {
        if (upperAction.startsWith("CREATE_")) {
            String entity = upperAction.substring(7).replace("_", " ");
            String entityInfo = formatInstanceIdForReadable(instanceId, upperEntity);
            return String.format("%s created %s %s%s", actor, formatEntityName(entity), entityInfo, timeSuffix);
        }
        if (upperAction.startsWith("UPDATE_")) {
            String entity = upperAction.substring(7).replace("_", " ");
            String entityInfo = formatInstanceIdForReadable(instanceId, upperEntity);
            return String.format("%s updated %s %s%s", actor, formatEntityName(entity), entityInfo, timeSuffix);
        }
        if (upperAction.startsWith("DELETE_")) {
            String entity = upperAction.substring(7).replace("_", " ");
            String entityInfo = formatInstanceIdForReadable(instanceId, upperEntity);
            return String.format("%s deleted %s %s%s", actor, formatEntityName(entity), entityInfo, timeSuffix);
        }
        return null;
    }

    private static String handleDownloadUploadReadable(String upperAction, String upperEntity, String instanceId, String actor, String timeSuffix) {
        if (upperAction.equals(Constants.AUDIT_ACTION_TYPE_DOWNLOAD)) {
            return String.format("%s downloaded from %s%s", actor, formatInstanceIdForReadable(instanceId, upperEntity), timeSuffix);
        }
        if (upperAction.equals(Constants.AUDIT_ACTION_TYPE_UPLOAD)) {
            return String.format("%s uploaded to %s%s", actor, formatInstanceIdForReadable(instanceId, upperEntity), timeSuffix);
        }
        return null;
    }

    /**
     * Extract SQL query from newValue JSON string
     */
    private static String extractQuery(String newValue) {
        if (newValue == null) return null;
        String[] lines = newValue.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            String upper = trimmed.toUpperCase();
            if (upper.startsWith(Constants.SQL_KEYWORD_SELECT) || 
                upper.startsWith(Constants.SQL_KEYWORD_INSERT) || 
                upper.startsWith(Constants.SQL_KEYWORD_UPDATE) || 
                upper.startsWith(Constants.SQL_KEYWORD_DELETE)) {
                return trimmed;
            }
        }
        return null;
    }

    /**
     * Format entity name for display (e.g., "ACCESS_REQUEST" -> "access request")
     */
    private static String formatEntityName(String entity) {
        if (entity == null || entity.isEmpty()) return "";
        return entity.toLowerCase().replace("_", " ");
    }

    /**
     * Format instance ID for readable description
     * Example: "ASSET(My Database)" -> "'My Database'"
     * Example: "USER(email@example.com)" -> "'email@example.com'"
     */
    private static String formatInstanceIdForReadable(String instanceId, String entityType) {
        if (instanceId == null || instanceId.isEmpty()) {
            return formatEntityName(entityType);
        }
        
        // Extract identifier from format like "ASSET(identifier)" or "USER(identifier)"
        if (instanceId.contains("(") && instanceId.contains(")")) {
            int start = instanceId.indexOf("(") + 1;
            int end = instanceId.lastIndexOf(")");
            if (start > 0 && end > start) {
                String identifier = instanceId.substring(start, end);
                return "'" + identifier + "'";
            }
        }
        
        return instanceId;
    }

    private static String safe(String v) { 
        return v == null ? "" : v; 
    }

    private static String capitalizeFirst(String v) {
        if (v == null || v.isEmpty()) return "";
        return Character.toUpperCase(v.charAt(0)) + v.substring(1).toLowerCase();
    }
}


