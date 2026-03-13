package com.verlake.dam.enums;

/**
 * Jira access level for database access requests.
 * Values: READ_ONLY, READ_WRITE, FULL_ACCESS
 */
public enum JiraAccessLevel {
    READ_ONLY,
    READ_WRITE,
    FULL_ACCESS;

    public static JiraAccessLevel fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return JiraAccessLevel.valueOf(value.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
