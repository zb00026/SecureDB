package com.verlake.dam.models.assets;

/**
 * Data class for lockout result parameters
 */
public class LockoutResultData {
    private final OperationMetadata metadata;
    private final UserLists userLists;
    private final FieldKeys fieldKeys;

    public LockoutResultData(OperationMetadata metadata, UserLists userLists, FieldKeys fieldKeys) {
        this.metadata = metadata;
        this.userLists = userLists;
        this.fieldKeys = fieldKeys;
    }

    public OperationMetadata getMetadata() {
        return metadata;
    }

    public UserLists getUserLists() {
        return userLists;
    }

    public FieldKeys getFieldKeys() {
        return fieldKeys;
    }
}
