package com.verlake.dam.models.assets;

import java.util.List;

/**
 * Lists of users affected by the operation
 */
public class UserLists {
    private final List<String> processedUsers;
    private final List<String> failedUsers;
    private final List<String> skippedUsers;

    public UserLists(List<String> processedUsers, List<String> failedUsers, List<String> skippedUsers) {
        this.processedUsers = processedUsers;
        this.failedUsers = failedUsers;
        this.skippedUsers = skippedUsers;
    }

    public List<String> getProcessedUsers() {
        return processedUsers;
    }

    public List<String> getFailedUsers() {
        return failedUsers;
    }

    public List<String> getSkippedUsers() {
        return skippedUsers;
    }
}
