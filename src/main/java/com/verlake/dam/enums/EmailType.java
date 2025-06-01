package com.verlake.dam.enums;

public enum EmailType {
    INVITATION,
    RELINQUISH_ASSET_CREDENTIAL,
    ASSET_APPROVE_NOTIFY,
    DEVELOPER_ASSET_REQUEST_NOTIFY,
    DEVELOPER_RELINQUISH_ASSET_NOTIFY,
    APPROVAL_ASSET_ACCESS_REQUEST,
    ASSET_OWNER_UPDATE_ASSET_OBJECT_ERROR, //Error when update asset object when asset owner logs in
    DELETE_QUERY_ALERT, //Alert when DELETE queries are executed
    OTHER;
    public static EmailType fromString(String value) {
        return switch (value) {
            case "INVITATION" -> INVITATION;
            case "RELINQUISH_ASSET_CREDENTIAL" -> RELINQUISH_ASSET_CREDENTIAL;
            case "ASSET_APPROVE_NOTIFY" -> ASSET_APPROVE_NOTIFY;
            case "DEVELOPER_ASSET_REQUEST_NOTIFY" -> DEVELOPER_ASSET_REQUEST_NOTIFY;
            case "DEVELOPER_RELINQUISH_ASSET_NOTIFY" -> DEVELOPER_ASSET_REQUEST_NOTIFY;
            case "APPROVAL_ASSET_ACCESS_REQUEST" -> APPROVAL_ASSET_ACCESS_REQUEST;
            case "ASSET_OWNER_UPDATE_ASSET_OBJECT_ERROR" -> ASSET_OWNER_UPDATE_ASSET_OBJECT_ERROR;
            case "DELETE_QUERY_ALERT" -> DELETE_QUERY_ALERT;
            case "OTHER" -> OTHER;
            default -> throw new IllegalArgumentException("Unknown email type: " + value);
        };
    }
}
