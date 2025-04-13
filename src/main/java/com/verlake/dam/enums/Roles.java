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

    public String getOriginalName() {
        String[] words = this.name().toLowerCase().split("_");
        return Arrays.stream(words)
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}
