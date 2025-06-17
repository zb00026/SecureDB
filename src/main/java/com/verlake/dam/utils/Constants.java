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
    public static final String EMAIL_VAR_IS_ACCESS_REQUEST = "isAccessRequest";
    public static final String EMAIL_VAR_TICKET_REFERENCE = "ticketReference";
    public static final String EMAIL_VAR_CHANGE_DESCRIPTION = "changeDescription";
    public static final String EMAIL_VAR_DATABASE_TYPE = "databaseType";
    public static final String EMAIL_VAR_HOST_URL = "hostUrl";
    public static final String EMAIL_VAR_EXECUTOR_NAME = "executorName";
    public static final String EMAIL_VAR_EXECUTION_TIME = "executionTime";
    public static final String EMAIL_VAR_TABLE_NAME = "tableName";
    public static final String EMAIL_VAR_AFFECTED_ROWS = "affectedRows";
    public static final String EMAIL_VAR_QUERY = "query";
    public static final String EMAIL_VAR_REQUEST_ID = "requestId";
    public static final String EMAIL_VAR_ASSET_ID = "assetId";
    public static final String EMAIL_VAR_REQUESTOR_NAME = "requestorName";
    public static final String EMAIL_VAR_MESSAGE_TYPE = "messageType";
    public static final String EMAIL_VAR_CHANGE_REQUEST_ID = "changeRequestId";
    public static final String EMAIL_VAR_REJECT_REASON = "rejectReason";
    
    // Default values
    public static final String DEFAULT_UNKNOWN_VALUE = "Unknown";
    
    // Audit metadata field constants
    public static final String AUDIT_FIELD_REQUEST_ID = "requestId";
    public static final String AUDIT_FIELD_ASSET_ID = "assetId";
    public static final String AUDIT_FIELD_USERNAME = "username";
    public static final String AUDIT_FIELD_EXECUTION_TIME_MS = "executionTimeMs";
    public static final String AUDIT_FIELD_SUCCESS = "success";
    public static final String AUDIT_FIELD_ERROR_MESSAGE = "errorMessage";
    public static final String AUDIT_FIELD_ROW_COUNT = "rowCount";
    public static final String AUDIT_FIELD_COLUMN_COUNT = "columnCount";
    
    // Audit trail filter constants
    public static final String AUDIT_TRAIL_FIELD_ACTION_METADATA = "actionMetadata";
    public static final String AUDIT_TRAIL_FIELD_INSTANCE_ID = "instanceId";
    public static final String AUDIT_TRAIL_FIELD_USER = "user";
    public static final String AUDIT_TRAIL_FIELD_ACTION = "action";
    public static final String AUDIT_TRAIL_FIELD_ASSET = "asset";
    
    // Common field names
    public static final String FIELD_ID = "id";
    public static final String METHOD_GET_ASSET = "getAsset";
    
    // Audit trail JSON field patterns
    public static final String AUDIT_JSON_ASSET_ID = "assetId";
    public static final String AUDIT_JSON_ASSET_ID_ALT = "asset_id";
    
    // Audit trail instance ID patterns
    public static final String AUDIT_INSTANCE_ASSET_PREFIX = "ASSET(";
    public static final String AUDIT_INSTANCE_ASSET_SUFFIX = ")";
    
    // Audit trail action patterns
    public static final String AUDIT_ACTION_APPROVAL = "APPROVAL";
    public static final String AUDIT_ACTION_APPROVE = "APPROVE";
    public static final String AUDIT_ACTION_REJECT = "REJECT";

    public static final String NOTIFY_DATA_ATTR_RECEIVER_ID = "receiverId";

    // License constants
    public static final String LICENSE_SOURCE_DATABASE = "Database";
    public static final String LICENSE_SOURCE_RESOURCES = "Resources";
    public static final String LICENSE_USING_DATABASE_LICENSE = "usingDatabaseLicense";
    
    // Security constants
    public static final String SECURITY_ROLE_PREFIX = "ROLE_";
    // User management constants
    public static final String USER_EMAIL_ALREADY_EXISTS = "A user with the email '%s' already exists.";
    public static final String USER_NOT_FOUND = "User ID %d does not exist";
    public static final String USER_NOT_FOUND_DOT = "User ID %d does not exist.";
    public static final String ROLES_NOT_FOUND = "One or more roles not found";
    
    // Email template names
    public static final String EMAIL_TEMPLATE_GOOGLE_INVITE = "google-invite";
    public static final String EMAIL_TEMPLATE_KEYCLOAK_INVITE = "keycloak-invite";
    
    // Password generation character sets
    public static final String PSSWD_LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    public static final String PSSWD_UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String PSSWD_DIGITS = "0123456789";
    public static final String PSSWD_SPECIALS = "!@#$%^&*()-_=+[]{}|;:'\",.<>/?";
    
    // CSV constants
    public static final String CSV_EXTENSION = ".csv";
    public static final String CSV_HEADER = "firstName,lastName,email,roleName";
    public static final String CSV_FILENAME = "user_bulk_upload_sample.csv";
    public static final String CSV_CONTENT_TYPE = "text/csv; charset=utf-8";
    public static final String CSV_ATTACHMENT_HEADER = "attachment; filename=" + CSV_FILENAME;
    public static final String CSV_COMMENT_PREFIX = "#";
    
    // Role names
    public static final String ROLE_ADMIN = "Admin";
    public static final String ROLE_DEVELOPER = "Developer";
    public static final String ROLE_ASSET_OWNER = "Asset Owner";
    public static final String ROLE_APPROVER = "Approver";
    public static final String ROLE_AUDITOR = "Auditor";
    
    // Response field names
    public static final String RESPONSE_SUCCESS = "success";
    public static final String RESPONSE_MESSAGE = "message";
    public static final String RESPONSE_ERRORS = "errors";
    public static final String RESPONSE_TOTAL_USERS = "totalUsers";
    public static final String RESPONSE_SUCCESSFUL_USERS = "successfulUsers";
    public static final String RESPONSE_FAILED_USERS = "failedUsers";
    public static final String RESPONSE_USERS = "users";
    public static final String RESPONSE_ID = "id";
    public static final String RESPONSE_EMAIL = "email";
    public static final String RESPONSE_FIRST_NAME = "firstName";
    public static final String RESPONSE_LAST_NAME = "lastName";
    
    // User field names
    public static final String USER_FIELD_ID = "id";
    public static final String USER_FIELD_FIRST_NAME = "firstName";
    public static final String USER_FIELD_LAST_NAME = "lastName";
    public static final String USER_FIELD_EMAIL = "email";
    public static final String USER_FIELD_ROLE_NAME = "roleName";
    public static final String USER_FIELD_LINE_NUMBER = "lineNumber";
    
    // Error messages
    public static final String ERROR_UPLOADED_FILE_EMPTY = "Uploaded file is empty";
    public static final String ERROR_FILE_MUST_BE_CSV = "File must be a CSV file";
    public static final String ERROR_NO_VALID_USER_DATA = "No valid user data found in CSV file";
    public static final String ERROR_VALIDATION_ERRORS_FOUND = "Validation errors found";
    public static final String ERROR_BULK_UPLOAD_FAILED = "Bulk upload failed: %s";
    public static final String ERROR_FAILED_TO_GENERATE_CSV = "Failed to generate sample CSV: %s";
    public static final String ERROR_FIELD_REQUIRED = "%s is required";
    public static final String ERROR_INVALID_EMAIL_FORMAT = "invalid email format: %s";
    public static final String ERROR_DUPLICATE_EMAIL_CSV = "duplicate email in CSV: %s";
    public static final String ERROR_EMAIL_ALREADY_EXISTS_SYSTEM = "email already exists in system: %s";
    public static final String ERROR_INVALID_ROLE_NAME = "invalid role name: '%s'. Available roles: " + ROLE_ADMIN + ", " + ROLE_DEVELOPER + ", " + ROLE_ASSET_OWNER + ", " + ROLE_APPROVER + ", " + ROLE_AUDITOR;
    
    // Success messages
    public static final String SUCCESS_CSV_GENERATED = "Sample CSV generated successfully";
    public static final String SUCCESS_USERS_CREATED = "Successfully created %d users and sent email invites";
    public static final String SUCCESS_CREATED_USERS_AND_SENT_INVITES = "Successfully created %d users and sent email invites";
    public static final String SUCCESS_GENERATING_CSV = "Generating sample CSV for bulk user creation";
    
    // Email regex pattern
    public static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@(.+)$";

    public static final String MYSQL_QUERY_SELECT = "SELECT";
    public static final String MYSQL_QUERY_SHOW = "SHOW";
    public static final String MYSQL_QUERY_DESCRIBE = "DESCRIBE";
    public static final String MYSQL_QUERY_INSERT = "INSERT";
    public static final String MYSQL_QUERY_UPDATE = "UPDATE";
    public static final String MYSQL_QUERY_DELETE = "DELETE";
    public static final String MYSQL_QUERY_CREATE = "CREATE";
    public static final String MYSQL_QUERY_ALTER = "ALTER";
    public static final String MYSQL_QUERY_EXPLAIN = "EXPLAIN";
    public static final String MYSQL_QUERY_DESC = "DESC";
    public static final String MYSQL_QUERY_EXECUTE = "EXECUTE";

    public static final String DAM_NOTIFICATION_TOPIC = "dam_notifications";

    public static final String JDBC_MYSQL_URL = "jdbc:mysql://";
    public static final String JDBC_POSTGRESQL_URL = "jdbc:postgresql://";
    public static final String JDBC_SQLSERVER_URL = "jdbc:sqlserver://";
    public static final String JDBC_ORACLE_URL = "jdbc:oracle:thin:@";

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
                    {"name": "ipAddress", "type": ["null", "string"]},
                    {"name": "assetId", "type": ["null", "long"]}
                  ]
                }
            """);
}
