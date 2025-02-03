package com.verlake.dam.enums;

public enum Roles {
    ADMIN,
    DEVELOPER,
    RESOURCE_OWNER,
    APPROVER,
    AUDITOR;

    public String getAvailablePath() {
        return "/" + this.name().toLowerCase().replace(" ", "_") + "/**";
    }

}
