package com.verlake.dam.models.assets;

/**
 * Field keys for result map
 */
public class FieldKeys {
    private final String processedUsersKey;
    private final String processedCountKey;

    public FieldKeys(String processedUsersKey, String processedCountKey) {
        this.processedUsersKey = processedUsersKey;
        this.processedCountKey = processedCountKey;
    }

    public String getProcessedUsersKey() {
        return processedUsersKey;
    }

    public String getProcessedCountKey() {
        return processedCountKey;
    }
}
