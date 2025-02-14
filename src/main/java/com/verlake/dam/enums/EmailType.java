package com.verlake.dam.enums;

public enum EmailType {
    INVITATION,
    OTHER;
    public static EmailType fromString(String value) {
        return switch (value) {
            case "INVITATION" -> INVITATION;
            case "OTHER" -> OTHER;
            default -> throw new IllegalArgumentException("Unknown email type: " + value);
        };
    }
}
