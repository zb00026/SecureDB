package com.verlake.dam.models.assets;

/**
 * Metadata about the lockout operation
 */
public class OperationMetadata {
    private final String operation;
    private final boolean allUsersFlag;
    private final boolean assetLocked;
    private final int totalUsers;

    public OperationMetadata(String operation, boolean allUsersFlag, boolean assetLocked, int totalUsers) {
        this.operation = operation;
        this.allUsersFlag = allUsersFlag;
        this.assetLocked = assetLocked;
        this.totalUsers = totalUsers;
    }

    public String getOperation() {
        return operation;
    }

    public boolean isAllUsersFlag() {
        return allUsersFlag;
    }

    public boolean isAssetLocked() {
        return assetLocked;
    }

    public int getTotalUsers() {
        return totalUsers;
    }
}
