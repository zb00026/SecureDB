package com.verlake.dam.utils;

import org.apache.avro.Schema;

/**
 * Constants class with all technical property values directly embedded.
 * No longer depends on external properties files.
 */
public class Constants {
    
    // Authentication providers
    public static final String AUTH_PROVIDER_KEYCLOAK = "keycloak";
    public static final String AUTH_PROVIDER_GOOGLE = "google";

    // Asset access object types
    public static final String ASSET_ACCESS_OBJECT_DATABASE = "DATABASE";
    public static final String ASSET_ACCESS_OBJECT_TABLE = "TABLE";
    public static final String ASSET_ACCESS_OBJECT_PROCEDURE = "PROCEDURE";
    public static final String ASSET_ACCESS_OBJECT_VIEW = "VIEW";

    // Access object attributes
    public static final String ACCESS_OBJECT_ATTR_GRANTS = "grants";
    public static final String ACCESS_OBJECT_ATTR_DATA = "data";

    // Information schema
    public static final String INFORMATION_SCHEMA_TABLE_NAME = "TABLE_NAME";

    // Access level templates
    public static final String ACCESS_LEVEL_TEMPLATE_FULL = "FULL ACCESS";
    public static final String ACCESS_LEVEL_TEMPLATE_SHOW_VIEW = "SHOW VIEW";
    public static final String ACCESS_LEVEL_TEMPLATE_CREATE_VIEW = "CREATE VIEW";
    public static final String ACCESS_LEVEL_TEMPLATE_CREATE_ROUTINE = "CREATE ROUTINE";
    public static final String ACCESS_LEVEL_TEMPLATE_ALTER_ROUTINE = "ALTER ROUTINE";
    public static final String ACCESS_LEVEL_ATTR_TEMPLATE = "templates";
    public static final String ACCESS_LEVEL_ATTR_ACCESS_TEMPLATE = "access_template";

    // Email variables
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

    // Notification data
    public static final String NOTIFY_DATA_ATTR_RECEIVER_ID = "receiverId";

    // License constants
    public static final String LICENSE_SOURCE_DATABASE = "Database";
    public static final String LICENSE_SOURCE_RESOURCES = "Resources";
    public static final String LICENSE_USING_DATABASE_LICENSE = "usingDatabaseLicense";

    // Security constants
    public static final String SECURITY_ROLE_PREFIX = "ROLE_";

    // Email template names
    public static final String EMAIL_TEMPLATE_GOOGLE_INVITE = "google-invite";
    public static final String EMAIL_TEMPLATE_KEYCLOAK_INVITE = "keycloak-invite";

    // Password generation character sets
    public static final String PSSWD_LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    public static final String PSSWD_UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String PSSWD_DIGITS = "0123456789";
    public static final String PSSWD_SPECIALS = "!@#$%^&*()-_=+[]{}|;:'\",.<>/?";

    // HTTP Header constants
    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String CACHE_CONTROL_NO_CACHE = "no-cache";

    // CSV constants
    public static final String CSV_EXTENSION = ".csv";
    public static final String CSV_HEADER = "firstName,lastName,email,roleName";
    public static final String CSV_FILENAME = "user_bulk_upload_sample.csv";
    public static final String CSV_CONTENT_TYPE = "text/csv; charset=utf-8";
    public static final String CSV_ATTACHMENT_HEADER = "attachment; filename=user_bulk_upload_sample.csv";
    public static final String CSV_COMMENT_PREFIX = "#";

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
    public static final String USER_FIELD_IS_ACTIVE = "isActive";
    public static final String USER_FIELD_ROLE_NAME = "roleName";
    public static final String USER_FIELD_LINE_NUMBER = "lineNumber";

    // JWT Password Reset constants
    public static final String JWT_SUBJECT_PASSWORD_RESET = "password_reset";
    public static final String JWT_CLAIM_TYPE = "type";
    public static final String JWT_CLAIM_TYPE_PASSWORD_RESET = "password_reset";
    public static final String JWT_CLAIM_EMAIL = "email";
    public static final String JWT_CLAIM_USER_ID = "userId";

    // Global Exception Handler constants
    public static final String ERROR_FIELD_ERROR = "error";
    public static final String ERROR_FIELD_DETAILS = "details";
    public static final String ERROR_FIELD_STATUS = "status";
    public static final String ERROR_FIELD_TYPE = "type";
    public static final String ERROR_TYPE_JWT_TOKEN = "JWT_TOKEN_ERROR";

    // MySQL query types
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

    // SQL template placeholders
    public static final String SQL_TEMPLATE_ON_SCHEMA = "ON $SCHEMA";

    // Notification topic
    public static final String DAM_NOTIFICATION_TOPIC = "dam_notifications";

    // JDBC URL prefixes
    public static final String JDBC_MYSQL_URL = "jdbc:mysql://";
    public static final String JDBC_POSTGRESQL_URL = "jdbc:postgresql://";
    public static final String JDBC_SQLSERVER_URL = "jdbc:sqlserver://";
    public static final String JDBC_ORACLE_URL = "jdbc:oracle:thin:@";

    // MSSQL-specific URL suffix with SSL parameters
    public static final String JDBC_SQLSERVER_SSL_PARAMS = ";encrypt=true;trustServerCertificate=true;characterEncoding=UTF-8";

    // Access request default expiry hours
    public static final String ACCESS_REQUEST_DEFAULT_EXPIRY_HOURS = "2160";

    // Invite code length
    public static final String INVITE_CODE_LENGTH = "15";

    // Database operations
    public static final String DB_SPLIT_PATTERN = "\\.";
    public static final String SQL_STATEMENT_SEPARATOR = ";";
    public static final String SQL_NEWLINE_SEPARATOR = "\n";
    public static final String SQL_SEMICOLON_NEWLINE = ";\n";

    // SQL placeholder replacements
    public static final String SQL_PLACEHOLDER_DATABASE_PUBLIC = "DATABASE public";
    public static final String SQL_PLACEHOLDER_SCHEMA_PUBLIC = "SCHEMA public";
    public static final String SQL_PLACEHOLDER_IN_SCHEMA_PUBLIC = "IN SCHEMA public";
    public static final String SQL_PLACEHOLDER_ON_DBO_BRACKET = "ON [dbo].";
    public static final String SQL_PLACEHOLDER_DBO_BRACKET = "[dbo].[";
    public static final String SQL_PLACEHOLDER_SCHEMA_BRACKET = "[$SCHEMA].[";
    public static final String SQL_PLACEHOLDER_ON_USERS = "ON USERS.";

    // Database type specific strings
    public static final String POSTGRES_SCHEMA_PUBLIC = "public";
    public static final String ORACLE_SCHEMA_USERS = "USERS";
    public static final String SQLSERVER_SCHEMA_DBO = "dbo";

    // System database names
    public static final String MYSQL_SYSTEM_DB_MYSQL = "mysql";
    public static final String MYSQL_SYSTEM_DB_PERFORMANCE_SCHEMA = "performance_schema";
    public static final String MYSQL_SYSTEM_DB_SYS = "sys";
    public static final String MYSQL_SYSTEM_DB_INFORMATION_SCHEMA = "information_schema";

    // PostgreSQL prefixes
    public static final String POSTGRES_SYSTEM_PREFIX = "pg_";

    // Database drivers
    public static final String DRIVER_POSTGRESQL = "org.postgresql.Driver";
    public static final String DRIVER_SQLSERVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    public static final String DRIVER_ORACLE = "oracle.jdbc.OracleDriver";

    // JDBC URL prefixes for Oracle
    public static final String JDBC_ORACLE_THIN_PREFIX = "jdbc:oracle:thin:@";

    // SQL error patterns
    public static final String SQL_ERROR_OBJECT_NOT_FOUND = "Cannot find the object";
    public static final String SQL_ERROR_DOES_NOT_EXIST = "does not exist";

    // Database query strings
    public static final String MYSQL_QUERY_SHOW_GRANTS = "SHOW GRANTS FOR CURRENT_USER";
    public static final String POSTGRES_PRIVILEGE_TYPE_COLUMN = "privilege_type";
    public static final String DB_COLUMN_TABLE_CAT = "TABLE_CAT";
    public static final String DB_COLUMN_ROUTINE_NAME = "ROUTINE_NAME";
    public static final String DB_COLUMN_SCHEMA_NAME = "schema_name";
    public static final String DB_COLUMN_TABLE_SCHEMA = "table_schema";
    public static final String DB_COLUMN_VIEW_NAME = "view_name";
    public static final String DB_COLUMN_PROCEDURE_NAME = "procedure_name";
    public static final String DB_COLUMN_PRIVILEGE = "PRIVILEGE";
    public static final String DB_COLUMN_TYPE = "TYPE";
    public static final String DB_COLUMN_TABLESPACE_NAME = "TABLESPACE_NAME";
    public static final String DB_COLUMN_OWNER = "OWNER";
    public static final String DB_COLUMN_OBJECT_NAME = "OBJECT_NAME";

    // SQL DDL keywords
    public static final String SQL_KEYWORD_TABLE = "TABLE";
    public static final String SQL_KEYWORD_VIEW = "VIEW";
    public static final String SQL_KEYWORD_PROCEDURE = "PROCEDURE";
    public static final String SQL_KEYWORD_ROUTINE = "ROUTINE";

    // Password and authentication
    public static final String TEMP_PSD_PREFIX = "temp";

    // Transaction control keywords
    public static final String SQL_TRANSACTION_START = "START TRANSACTION";
    public static final String SQL_TRANSACTION_COMMIT = "COMMIT";
    public static final String SQL_TRANSACTION_ROLLBACK = "ROLLBACK";
    public static final String SQL_TRANSACTION_BEGIN = "BEGIN";
    public static final String SQL_ROLLBACK_SUFFIX = " ROLLBACK;";
    public static final String SQL_COMMIT_SUFFIX = " COMMIT;";

    // Query result headers
    public static final String QUERY_RESULT_STATUS_HEADER = "Status";
    public static final String QUERY_RESULT_AFFECTED_ROWS_HEADER = "Affected Rows";
    public static final String QUERY_RESULT_ERROR_HEADER = "Error";

    // Notification data keys
    public static final String NOTIFICATION_KEY_REQUEST_ID = "requestId";
    public static final String NOTIFICATION_KEY_ASSET_ID = "assetId";
    public static final String NOTIFICATION_KEY_ASSET_NAME = "assetName";
    public static final String NOTIFICATION_KEY_ASSET_DESCRIPTION = "assetDescription";
    public static final String NOTIFICATION_KEY_DEVELOPER_NAME = "developerName";
    public static final String NOTIFICATION_KEY_APPROVER_NAME = "approverName";
    public static final String NOTIFICATION_KEY_APPROVAL_STATUS = "approvalStatus";

    // Schema information
    public static final String INFORMATION_SCHEMA_PREFIX = "information_schema.";

    // Log messages
    public static final String LOG_ERROR_FETCHING_GRANTS = "Error fetching grants";
    public static final String LOG_ERROR_FETCHING_DATABASES = "Error fetching databases";
    public static final String LOG_ERROR_FETCHING_OBJECTS_FOR_DB = "Error fetching objects for database: ";
    public static final String LOG_ERROR_FETCHING_VIEWS_FOR_DB = "Error fetching views for database: ";
    public static final String LOG_ERROR_FETCHING_PROCEDURES_FOR_DB = "Error fetching procedures for database: ";
    public static final String LOG_ERROR_FETCHING_INFO_SCHEMA = "Error fetching information_schema tables";
    public static final String LOG_ERROR_UPDATING_PASSWORD = "Error updating password";
    public static final String LOG_ERROR_REVOKING_ACCESS_FOR_USER = "Error revoking access for user: ";
    public static final String LOG_ERROR_DECRYPTING_OWNER_PASSWORD = "Error decrypting owner password";

    // User deletion messages
    public static final String LOG_USER_CASCADE_DELETE_START = "Deleting user ID: {} ({}) with database cascade";
    public static final String LOG_USER_CASCADE_DELETE_SUCCESS = "Successfully deleted user ID: {} ({}) with cascade";

    // Map keys for credential management
    public static final String CREDENTIAL_ID_KEY = "credentialID";

    // Email regex pattern
    public static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@(.+)$";

    // Default values
    public static final String DEFAULT_UNKNOWN_VALUE = "Unknown";

    // Query result messages
    public static final String QUERY_RESULT_EXECUTED_SUCCESSFULLY = " executed successfully";

    // Keycloak constants
    public static final String KEYCLOAK_CLIENT_ATTRIBUTES = "attributes";
    public static final String KEYCLOAK_USER_KEY = "user-key";

    // Audit constants
    public static final String AUDIT_SCHEMA_NAME = "audit_schema";

    // Error messages
    public static final String ERROR_UNSUPPORTED_DATABASE_TYPE = "error.unsupported.database.type";
    public static final String ERROR_USER_NO_ACCESS_TO_ASSET = "error.user.no.access.to.asset";
    public static final String USER_NOT_FOUND = "user.not.found";
    public static final String ASSET_NOT_FOUND = "asset.not.found";
    public static final String ERROR_NO_ACCESS_LEVEL_OBJECTS = "error.no.access.level.objects";
    public static final String NOTIFICATION_TITLE_NEW_ACCESS_REQUEST = "notification.title.new.access.request";
    public static final String NOTIFICATION_TITLE_RELINQUISHED_ACCESS_REQUEST = "notification.title.relinquished.access.request";
    public static final String ERROR_ACCESS_REQUEST_NOT_FOUND = "error.access.request.not.found";
    public static final String ERROR_NO_ACCESS_REQUEST = "error.no.access.request";
    public static final String ERROR_ASSET_NOT_FOUND_MSG = "error.asset.not.found.msg";
    public static final String ERROR_ACCESS_REQUEST_NOT_FOUND_MSG = "error.access.request.not.found.msg";
    public static final String ERROR_NEW_CREDENTIAL_NOT_FOUND = "error.new.credential.not.found";
    public static final String NOTIFICATION_TITLE_APPROVAL_RESULT = "notification.title.approval.result";
    public static final String ERROR_NO_VALID_CREDENTIALS = "error.no.valid.credentials";
    public static final String ERROR_CREDENTIAL_ERRORS = "error.credential.errors";
    public static final String ERROR_DATABASE_TYPE_NOT_SUPPORTED = "error.database.type.not.supported";
    public static final String ERROR_CONNECTING_TO_DATABASE = "error.connecting.to.database";
    public static final String ERROR_REVOKING_DATABASE_ACCESS = "error.revoking.database.access";
    public static final String ERROR_TEMP_PASSWORD_QUERY = "error.temp.password.query";
    public static final String ERROR_UNKNOWN_QUERY_TYPE = "error.unknown.query.type";
    public static final String ERROR_EXECUTING_QUERY = "error.executing.query";
    public static final String ERROR_ASSET_CANNOT_BE_NULL = "error.asset.cannot.be.null";
    public static final String ERROR_ADMIN_CREDENTIAL_CANNOT_BE_NULL = "error.admin.credential.cannot.be.null";
    public static final String ERROR_ADMIN_CREDENTIAL_NO_PASSWORD = "error.admin.credential.no.password";
    public static final String ERROR_ASSET_DATABASE_TYPE_NULL = "error.asset.database.type.null";
    public static final String ERROR_FAILED_TO_CONNECT_TO_DATABASE = "error.failed.to.connect.to.database";
    public static final String ERROR_FAILED_USER_LOCKOUT = "error.failed.user.lockout";
    public static final String ERROR_FAILED_USER_UNLOCK = "error.failed.user.unlock";
    public static final String ERROR_UNSUPPORTED_DB_TYPE_USER_LISTING = "error.unsupported.db.type.user.listing";
    public static final String ERROR_UNSUPPORTED_DB_TYPE_LOCKED_USER_LISTING = "error.unsupported.db.type.locked.user.listing";
    public static final String ERROR_UNSUPPORTED_DB_TYPE_USER_LOCKING = "error.unsupported.db.type.user.locking";
    public static final String ERROR_UNSUPPORTED_DB_TYPE_USER_UNLOCKING = "error.unsupported.db.type.user.unlocking";
    public static final String ERROR_UNSUPPORTED_DB_TYPE_USER_PASSWORD_UPDATE = "error.unsupported.db.type.user.password.update";

    // Additional constants
    public static final String SUCCESS_CREATED_USERS_AND_SENT_INVITES = "success.created.users.and.sent.invites";
    public static final String ERROR_BULK_UPLOAD_FAILED = "error.bulk.upload.failed";
    public static final String ERROR_CREATING_EMAIL_ENTITY = "error.creating.email.entity";
    public static final String ERROR_FIELD_REQUIRED = "error.field.required";
    public static final String ROLE_NONE = "role.none";
    public static final String ROLE_DEVELOPER = "role.developer";
    public static final String ROLE_ADMIN = "role.admin";
    public static final String ROLE_ASSET_OWNER = "role.asset.owner";
    public static final String ROLE_APPROVER = "role.approver";
    public static final String ROLE_AUDITOR = "role.auditor";
    public static final String ERROR_VALIDATION_ERRORS_FOUND = "error.validation.errors.found";
    public static final String ERROR_UPLOADED_FILE_EMPTY = "error.uploaded.file.empty";
    public static final String ERROR_FILE_MUST_BE_CSV = "error.file.must.be.csv";
    public static final String ERROR_INVALID_EMAIL_FORMAT = "error.invalid.email.format";
    public static final String ERROR_DUPLICATE_EMAIL_CSV = "error.duplicate.email.csv";
    public static final String ERROR_EMAIL_ALREADY_EXISTS_SYSTEM = "error.email.already.exists.system";
    public static final String ERROR_INVALID_ROLE_NAME = "error.invalid.role.name";
    public static final String ERROR_NO_VALID_USER_DATA = "error.no.valid.user.data";

    // SQL constants
    public static final String ALTER_USER_IDENTIFIED_BY = "ALTER USER ? IDENTIFIED BY ?";

    public static final String STATUS_NAME = "status";
    public static final String ERROR_MESSAGE_NAME = "error_message";

    public static final String ASSET_ADD_NAME = "Add";
    public static final String ASSET_REMOVE_NAME = "Remove";

    // License public key (cannot be externalized as it's a byte array)
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

    // Audit schema (cannot be externalized as it's a complex schema object)
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

    /**
     * Get a localized message
     * @param code the message code
     * @return the localized message
     */
    public static String getMessage(String code) {
        return I18nUtils.getMessage(code);
    }

    /**
     * Get a localized message with arguments
     * @param code the message code
     * @param args the arguments to substitute
     * @return the localized message
     */
    public static String getMessage(String code, Object... args) {
        return I18nUtils.getMessage(code, args);
    }

    /**
     * Get a technical property value
     * @param key the property key
     * @return the property value
     */
    public static String getTechnicalProperty(String key) {
        return I18nUtils.getTechnicalProperty(key);
    }

    /**
     * Get a technical property value with default
     * @param key the property key
     * @param defaultValue the default value
     * @return the property value or default
     */
    public static String getTechnicalProperty(String key, String defaultValue) {
        return I18nUtils.getTechnicalProperty(key, defaultValue);
    }

    /**
     * Get an integer technical property value
     * @param key the property key
     * @return the integer property value
     */
    public static int getTechnicalPropertyAsInt(String key) {
        return I18nUtils.getTechnicalPropertyAsInt(key);
    }

    /**
     * Get an integer technical property value with default
     * @param key the property key
     * @param defaultValue the default value
     * @return the integer property value or default
     */
    public static int getTechnicalPropertyAsInt(String key, int defaultValue) {
        return I18nUtils.getTechnicalPropertyAsInt(key, defaultValue);
    }
}
