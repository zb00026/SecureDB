package com.verlake.dam.enums;

public enum EmailType {
    INVITATION,
    RELINQUISH_ASSET_CREDENTIAL,
    OTHER;
    public static EmailType fromString(String value) {
        return switch (value) {
            case "INVITATION" -> INVITATION;
            case "RELINQUISH_ASSET_CREDENTIAL" -> RELINQUISH_ASSET_CREDENTIAL;
            case "OTHER" -> OTHER;
            default -> throw new IllegalArgumentException("Unknown email type: " + value);
        };
    }
}
