package com.verlake.dam.enums;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum Roles {
    ADMIN,
    DEVELOPER,
    ASSET_OWNER,
    APPROVER,
    AUDITOR;

    public String getAvailablePath() {
        return "/" + this.name().toLowerCase().replace(" ", "_") + "/**";
    }

    /**
     * Returns the original name of the object by converting the database name format to a display format.
     * This method converts underscores to spaces and capitalizes each word.
     * 
     * Example:
     * - Input: "ASSET_OWNER"
     * - Database value: "Asset Owner"
     * - Display value: "Asset Owner"
     * 
     * @return The formatted name suitable for display in the frontend
     */
    public String getOriginalName() {
        String[] words = this.name().toLowerCase().split("_");
        return Arrays.stream(words)
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}
