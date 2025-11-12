package com.verlake.dam.enums;

public enum ApprovalStatus {
    REQUESTED("Requested"),
    APPROVAL_IN_PROGRESS("Approval in progress"),
    APPROVED("Approved"),
    REJECTED("Rejected"),
    RELINQUISHED_AFTER_APPROVED("Relinquished"),
    RELINQUISHED_BEFORE_APPROVAL("Relinquished before Approval"),
    EXPIRED("Expired");

    private final String displayName;

    ApprovalStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
} 