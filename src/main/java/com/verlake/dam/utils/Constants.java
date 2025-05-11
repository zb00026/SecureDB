package com.verlake.dam.utils;

import org.apache.avro.Schema;

public class Constants {
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAIL = "fail";
    public static final String STATUS_NAME = "status";
    public static final String ERROR_MSG_NAME = "error_message";
    public static final String ASSET_NOT_FOUND = "Asset not found";

    public static final  String KEYCLOAK_USER_KEY = "user-key";
    public static final String KEYCLOAK_CLIENT_ATTRIBUTES = "attributes";

    public static final String ASSET_ADD_NAME = "Add";
    public static final String ASSET_REMOVE_NAME = "Remove";

    public static final String AUTH_PROVIDER_KEYCLOAK = "keycloak";
    public static final String AUTH_PROVIDER_GOOGLE = "google";

    public static final String ASSET_ACCESS_OBJECT_DATABASE = "DATABASE";
    public static final String ASSET_ACCESS_OBJECT_TABLE = "TABLE";
    public static final String ASSET_ACCESS_OBJECT_PROCEDURE = "PROCEDURE";
    public static final String ASSET_ACCESS_OBJECT_VIEW = "VIEW";

    public static final String ACCESS_OBJECT_ATTR_GRANTS = "grants";
    public static final String ACCESS_OBJECT_ATTR_DATA = "data";

    public static final String INFORMATION_SCHEMA_TABLE_NAME = "TABLE_NAME";

    public static final String ACCESS_LEVEL_TEMPLATE_FULL = "FULL ACCESS";
    public static final String ACCESS_LEVEL_TEMPLATE_SHOW_VIEW = "SHOW VIEW";
    public static final String ACCESS_LEVEL_TEMPLATE_CREATE_VIEW = "CREATE VIEW";
    public static final String ACCESS_LEVEL_TEMPLATE_CREATE_ROUTINE = "CREATE ROUTINE";
    public static final String ACCESS_LEVEL_TEMPLATE_ALTER_ROUTINE = "ALTER ROUTINE";
    public static final String ACCESS_LEVEL_ATTR_TEMPLATE = "templates";
    public static final String ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE = "access_template";

    public static final String EMAIL_VAR_RECEIVER_FIRST_NAME = "receiverFirstName";
    public static final String EMAIL_VAR_RECEIVER_LAST_NAME = "receiverLastName";
    public static final String EMAIL_VAR_REQUESTOR_FIRST_NAME = "requestorFirstName";
    public static final String EMAIL_VAR_REQUESTOR_LAST_NAME = "requestorLastName";
    public static final String EMAIL_VAR_APPROVER_FIRST_NAME = "approverFirstName";
    public static final String EMAIL_VAR_APPROVER_LAST_NAME = "approverLastName";
    public static final String EMAIL_VAR_APPROVAL_STATUS = "approvalStatus";
    public static final String EMAIL_VAR_ASSET_NAME = "assetName";
    public static final String EMAIL_VAR_ASSET_DESCRIPTION = "assetDescription";
    public static final String EMAIL_VAR_TEMP_PASSWORD = "tempPassword";
    public static final String EMAIL_VAR_REDIRECT_LINK = "redirectLink";
    public static final String EMAIL_VAR_METHOD = "method";
    public static final String EMAIL_VAR_ADMIN_NAME = "adminName";
    public static final String EMAIL_VAR_OWNER_NAME = "ownerName";
    public static final String EMAIL_VAR_APPROVER_NAME = "approverName";
    public static final String EMAIL_VAR_USER_NAME = "userName";
    public static final String EMAIL_VAR_INVITE_CODE = "inviteCode";
    public static final String EMAIL_VAR_ASSET_CREDENTIAL_ID = "assetCredentialId";
    public static final String EMAIL_VAR_DB_USERNAME = "dbUsername";
    public static final String EMAIL_VAR_DB_PASSWORD = "dbPassword";

    public static final String NOTIFY_DATA_ATTR_RECEIVER_ID = "receiverId";

    public static final int ACCESS_REQUEST_DEFAULT_EXPIRY_HOURS = 2160;

    public static final int INVITE_CODE_LENGTH = 15;
    public static final byte[] LIC_PUBLIC_KEY = new byte[] {
            (byte) 0x52,
            (byte) 0x53, (byte) 0x41, (byte) 0x00, (byte) 0x30, (byte) 0x82, (byte) 0x01, (byte) 0x22, (byte) 0x30,
            (byte) 0x0D, (byte) 0x06, (byte) 0x09, (byte) 0x2A, (byte) 0x86, (byte) 0x48, (byte) 0x86, (byte) 0xF7,
            (byte) 0x0D, (byte) 0x01, (byte) 0x01, (byte) 0x01, (byte) 0x05, (byte) 0x00, (byte) 0x03, (byte) 0x82,
            (byte) 0x01, (byte) 0x0F, (byte) 0x00, (byte) 0x30, (byte) 0x82, (byte) 0x01, (byte) 0x0A, (byte) 0x02,
            (byte) 0x82, (byte) 0x01, (byte) 0x01, (byte) 0x00, (byte) 0x95, (byte) 0x60, (byte) 0x56, (byte) 0xAF,
            (byte) 0x5B, (byte) 0xAE, (byte) 0x18, (byte) 0x33, (byte) 0x4D, (byte) 0xCA, (byte) 0x2D, (byte) 0xC6,
            (byte) 0xD1, (byte) 0xA6, (byte) 0xC4, (byte) 0x71, (byte) 0x33, (byte) 0x92, (byte) 0x6E, (byte) 0xF3,
            (byte) 0x85, (byte) 0x19, (byte) 0xE5, (byte) 0xE2, (byte) 0x7A, (byte) 0x8D, (byte) 0x4D, (byte) 0xAF,
            (byte) 0x8A, (byte) 0xD2, (byte) 0xF0, (byte) 0xC9, (byte) 0x68, (byte) 0x32, (byte) 0x83, (byte) 0xA8,
            (byte) 0xC9, (byte) 0xE3, (byte) 0xEB, (byte) 0x24, (byte) 0xE8, (byte) 0x0F, (byte) 0x46, (byte) 0xEE,
            (byte) 0x0D, (byte) 0xB2, (byte) 0x11, (byte) 0xD0, (byte) 0x0B, (byte) 0xFF, (byte) 0x54, (byte) 0xBF,
            (byte) 0x1E, (byte) 0xA6, (byte) 0xF6, (byte) 0xF1, (byte) 0x8D, (byte) 0x40, (byte) 0x06, (byte) 0x7D,
            (byte) 0x44, (byte) 0x17, (byte) 0x4D, (byte) 0x52, (byte) 0x7F, (byte) 0xDB, (byte) 0x92, (byte) 0x8C,
            (byte) 0x18, (byte) 0x3D, (byte) 0x4D, (byte) 0xE6, (byte) 0x5D, (byte) 0x02, (byte) 0x7F, (byte) 0x95,
            (byte) 0x3C, (byte) 0xF2, (byte) 0x09, (byte) 0x54, (byte) 0x14, (byte) 0x32, (byte) 0x99, (byte) 0xDE,
            (byte) 0xA2, (byte) 0x59, (byte) 0xEC, (byte) 0x7D, (byte) 0x35, (byte) 0x4B, (byte) 0x55, (byte) 0xCF,
            (byte) 0x28, (byte) 0x00, (byte) 0xBF, (byte) 0xF6, (byte) 0xC6, (byte) 0xF9, (byte) 0xB7, (byte) 0x52,
            (byte) 0xC8, (byte) 0xC8, (byte) 0x7D, (byte) 0x50, (byte) 0xC2, (byte) 0x34, (byte) 0x7E, (byte) 0x86,
            (byte) 0x62, (byte) 0xED, (byte) 0x67, (byte) 0xF9, (byte) 0x42, (byte) 0x3B, (byte) 0x4D, (byte) 0x27,
            (byte) 0x9A, (byte) 0x1B, (byte) 0x96, (byte) 0x1C, (byte) 0x97, (byte) 0xE3, (byte) 0xE6, (byte) 0x35,
            (byte) 0x68, (byte) 0x5E, (byte) 0x8C, (byte) 0x98, (byte) 0xAB, (byte) 0xD9, (byte) 0x84, (byte) 0x1E,
            (byte) 0xCA, (byte) 0x34, (byte) 0x59, (byte) 0x60, (byte) 0xA3, (byte) 0x4B, (byte) 0x29, (byte) 0x8C,
            (byte) 0xD6, (byte) 0x9A, (byte) 0x59, (byte) 0xB5, (byte) 0xE0, (byte) 0xE7, (byte) 0x34, (byte) 0xB3,
            (byte) 0x31, (byte) 0x67, (byte) 0x06, (byte) 0xC4, (byte) 0xA8, (byte) 0x0D, (byte) 0x75, (byte) 0x39,
            (byte) 0x97, (byte) 0x60, (byte) 0x29, (byte) 0x0D, (byte) 0xB8, (byte) 0x41, (byte) 0x40, (byte) 0xBE,
            (byte) 0x9A, (byte) 0x99, (byte) 0x41, (byte) 0xED, (byte) 0xC4, (byte) 0xA4, (byte) 0x80, (byte) 0xEE,
            (byte) 0xAF, (byte) 0xEA, (byte) 0xBC, (byte) 0xAE, (byte) 0x13, (byte) 0x26, (byte) 0x04, (byte) 0xBB,
            (byte) 0x73, (byte) 0xC0, (byte) 0x65, (byte) 0xED, (byte) 0x1E, (byte) 0xEE, (byte) 0xC5, (byte) 0x73,
            (byte) 0x48, (byte) 0x30, (byte) 0x6F, (byte) 0x88, (byte) 0x81, (byte) 0xC1, (byte) 0x33, (byte) 0x6D,
            (byte) 0xEF, (byte) 0x82, (byte) 0xE7, (byte) 0x25, (byte) 0x7E, (byte) 0xE2, (byte) 0xEF, (byte) 0x0A,
            (byte) 0xBF, (byte) 0x75, (byte) 0x4F, (byte) 0x0B, (byte) 0xF0, (byte) 0x10, (byte) 0x5D, (byte) 0x4E,
            (byte) 0xAA, (byte) 0xDB, (byte) 0x78, (byte) 0xF4, (byte) 0x71, (byte) 0x73, (byte) 0xE3, (byte) 0x82,
            (byte) 0x31, (byte) 0x91, (byte) 0x9B, (byte) 0x20, (byte) 0x48, (byte) 0x6E, (byte) 0xF9, (byte) 0x71,
            (byte) 0x92, (byte) 0x3D, (byte) 0x5F, (byte) 0x75, (byte) 0xD9, (byte) 0x53, (byte) 0x4D, (byte) 0x04,
            (byte) 0xBE, (byte) 0x04, (byte) 0x58, (byte) 0x3D, (byte) 0xD2, (byte) 0xF2, (byte) 0x4E, (byte) 0x48,
            (byte) 0xD1, (byte) 0x65, (byte) 0xA6, (byte) 0x20, (byte) 0x3C, (byte) 0x56, (byte) 0x4C, (byte) 0x99,
            (byte) 0x7D, (byte) 0x0A, (byte) 0xE8, (byte) 0xD7, (byte) 0x02, (byte) 0x03, (byte) 0x01, (byte) 0x00,
            (byte) 0x01,
    };

    public static final Schema AUDIT_SCHEMA = new Schema.Parser().parse("""
                {
                  "type": "record",
                  "name": "AuditTrail",
                  "fields": [
                    {"name": "id", "type": "long"},
                    {"name": "timestamp", "type": {"type": "string", "logicalType": "timestamp-millis"}},
                    {"name": "instanceId", "type": ["null", "string"]},
                    {"name": "user", "type": "string"},
                    {"name": "action", "type": "string"},
                    {"name": "previousValue", "type": ["null", "string"]},
                    {"name": "newValue", "type": ["null", "string"]},
                    {"name": "actionMetadata", "type": ["null", "string"]},
                    {"name": "ipAddress", "type": ["null", "string"]}
                  ]
                }
            """);
}
