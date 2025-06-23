package com.verlake.dam.enums;

public enum LockType {
    LOCK_HAGRID_ONLY("lock_hagrid_only"),
    LOCK_ALL_DB_USERS("lock_all_db_users");

    private final String value;

    LockType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * Get LockType from string value
     * @param value The string value
     * @return LockType enum or null if not found
     */
    public static LockType fromValue(String value) {
        for (LockType lockType : LockType.values()) {
            if (lockType.getValue().equals(value)) {
                return lockType;
            }
        }
        return null;
    }
} 