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
    public static final String AUDIT_ACTION_AI_MASKING_APPLIED = "AI_MASKING_APPLIED";
    public static final String AUDIT_ACTION_ACCESSOR_QUERY_EXECUTION = "ACCESSOR_QUERY_EXECUTION";
    public static final String AUDIT_ACTION_ASSET_OWNER_QUERY_EXECUTION = "ASSET_OWNER_QUERY_EXECUTION";

    // Query execution types
    public static final String QUERY_EXECUTION_TYPE_ACCESSOR = "ACCESSOR";
    public static final String QUERY_EXECUTION_TYPE_ASSET_OWNER = "ASSET_OWNER";
    
    // Permission check types
    public static final String PERMISSION_CHECK_TYPE_USER_MANAGEMENT = "user management";

    // AI Chat constants
    public static final String AI_SENDER = "ai";
    public static final String MASKING_STRATEGY_TOKENIZE = "tokenize";
    public static final String ACTION_TYPE_CONFIRM = "confirm";
    public static final String UNKNOWN_USER = "unknown";
    public static final String UNKNOWN_EMAIL = "unknown@example.com";

    // AI Chat error messages
    public static final String ERROR_SESSION_EXPIRED = "Session expired. Please start a new conversation.";
    
    // Field name keywords
    public static final String FIELD_KEYWORD_PHONE = "phone";
    public static final String FIELD_KEYWORD_MOBILE = "mobile";
    public static final String FIELD_KEYWORD_CONTACT = "contact";
    public static final String FIELD_KEYWORD_EMAIL = "email";
    
    // Prompt parameter names
    public static final String PROMPT_PARAM_USER_MESSAGE = "userMessage";
    public static final String PROMPT_PARAM_STRATEGY = "strategy";
    
    // AI Pattern error messages
    public static final String ERROR_PATTERN_NOT_FOUND_WITH_ID = "Pattern not found with id: ";
    
    // Gemini AI constants
    public static final String GEMINI_ERROR_NOT_AVAILABLE_IN_REGION = "not available in your region";
    public static final String GEMINI_ERROR_MANUAL_MASKING_POLICY = "manual masking policy";
    public static final String GEMINI_JSON_FIELD_PARTS = "parts";
    public static final String GEMINI_JSON_FIELD_CANDIDATES = "candidates";
    public static final String GEMINI_JSON_FIELD_FINISH_REASON = "finishReason";
    public static final String GEMINI_JSON_FIELD_CONTENT = "content";
    public static final String GEMINI_STRATEGY_PARTIAL = "partial";
    
    // Gemini AI response message keys
    public static final String GEMINI_CIRCUIT_BREAKER_FALLBACK_KEY = "gemini.circuit.breaker.fallback";
    public static final String GEMINI_LOCATION_RESTRICTION_RESPONSE_KEY = "gemini.location.restriction.response";
    public static final String GEMINI_GENERIC_ERROR_RESPONSE_KEY = "gemini.generic.error.response";
    public static final String GEMINI_MODEL_OVERLOADED_RESPONSE_KEY = "gemini.model.overloaded.response";
    
    // Gemini AI NL to SQL error message keys
    public static final String GEMINI_NL_SQL_LOCATION_RESTRICTION_KEY = "gemini.nl.sql.location.restriction";
    public static final String GEMINI_NL_SQL_CIRCUIT_BREAKER_KEY = "gemini.nl.sql.circuit.breaker";
    public static final String GEMINI_NL_SQL_GENERIC_ERROR_KEY = "gemini.nl.sql.generic.error";
    public static final String GEMINI_NL_SQL_MODEL_OVERLOADED_KEY = "gemini.nl.sql.model.overloaded";
    
    // Gemini AI error message patterns
    public static final String GEMINI_ERROR_MODEL_OVERLOADED = "model is overloaded";
    public static final String GEMINI_ERROR_QUOTA_EXCEEDED = "quota exceeded";
    public static final String GEMINI_ERROR_RATE_LIMIT = "rate limit";
    public static final String GEMINI_ERROR_TEMPORARILY_UNAVAILABLE = "temporarily unavailable";
    public static final String GEMINI_ERROR_SERVICE_UNAVAILABLE = "service unavailable";
    public static final String GEMINI_ERROR_INTERNAL_ERROR = "an internal error has occurred";

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
    public static final String EMAIL_TEMPLATE_SSO_INVITE = "sso-invite";

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

    // Asset CSV constants
    public static final String ASSET_CSV_HEADER = "name,description,type,databaseType,hostAddress,portNumber,databaseName,ownerEmail";
    public static final String ASSET_CSV_FILENAME = "asset_bulk_upload_sample.csv";
    public static final String ASSET_CSV_ATTACHMENT_HEADER = "attachment; filename=asset_bulk_upload_sample.csv";
    public static final String ASSET_CSV_EXPORT_FILENAME = "assets_export.csv";
    public static final String ASSET_CSV_EXPORT_ATTACHMENT_HEADER = "attachment; filename=assets_export.csv";

    // Asset field names
    public static final String ASSET_FIELD_LINE_NUMBER = "lineNumber";
    public static final String ASSET_FIELD_ID = "id";
    public static final String ASSET_FIELD_NAME = "name";
    public static final String ASSET_FIELD_DESCRIPTION = "description";
    public static final String ASSET_FIELD_TYPE = "type";
    public static final String ASSET_FIELD_DATABASE_TYPE = "databaseType";
    public static final String ASSET_FIELD_HOST_ADDRESS = "hostAddress";
    public static final String ASSET_FIELD_PORT_NUMBER = "portNumber";
    public static final String ASSET_FIELD_DATABASE_NAME = "databaseName";
    public static final String ASSET_FIELD_OWNER_EMAIL = "ownerEmail";

    // Response field names
    public static final String RESPONSE_SUCCESS = "success";
    public static final String RESPONSE_MESSAGE = "message";
    public static final String RESPONSE_ERRORS = "errors";
    public static final String RESPONSE_TOTAL_USERS = "totalUsers";
    public static final String RESPONSE_SUCCESSFUL_USERS = "successfulUsers";
    public static final String RESPONSE_FAILED_USERS = "failedUsers";
    public static final String RESPONSE_USERS = "users";
    public static final String RESPONSE_TOTAL_ASSETS = "totalAssets";
    public static final String RESPONSE_SUCCESSFUL_ASSETS = "successfulAssets";
    public static final String RESPONSE_FAILED_ASSETS = "failedAssets";
    public static final String RESPONSE_ASSETS = "assets";
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
    public static final String SQL_QUERY_SELECT_ZERO = "SELECT 0";

    // SQL template placeholders
    public static final String SQL_TEMPLATE_ON_SCHEMA = "ON $SCHEMA";

    // Notification topic
    public static final String DAM_NOTIFICATION_TOPIC = "dam_notifications";

    // JDBC URL prefixes
    public static final String JDBC_MYSQL_URL = "jdbc:mysql://";
    public static final String JDBC_POSTGRESQL_URL = "jdbc:postgresql://";
    public static final String JDBC_SQLSERVER_URL = "jdbc:sqlserver://";
    public static final String JDBC_ORACLE_URL = "jdbc:oracle:thin:@";
    public static final String MONGODB_CONNECTION_URL = "mongodb://";
    public static final String MONGODB_ADMIN_DATABASE = "admin";
    
    // MongoDB command names
    public static final String MONGODB_COMMAND_USERS_INFO = "usersInfo";
    public static final String MONGODB_COMMAND_CREATE_USER = "createUser";
    public static final String MONGODB_COMMAND_UPDATE_USER = "updateUser";
    public static final String MONGODB_COMMAND_GRANT_ROLES_TO_USER = "grantRolesToUser";
    public static final String MONGODB_COMMAND_REVOKE_ROLES_FROM_USER = "revokeRolesFromUser";
    public static final String MONGODB_COMMAND_DROP_USER = "dropUser";
    public static final String MONGODB_COMMAND_PING = "ping";
    
    // MongoDB document field names
    public static final String MONGODB_FIELD_USERS = "users";
    public static final String MONGODB_FIELD_ROLES = "roles";
    public static final String MONGODB_FIELD_USER = "user";
    public static final String MONGODB_FIELD_PWD = "pwd";
    public static final String MONGODB_FIELD_ROLE = "role";
    public static final String MONGODB_FIELD_DB = "db";
    
    // MongoDB error message keywords
    public static final String MONGODB_ERROR_AUTHENTICATION = "authentication";
    public static final String MONGODB_ERROR_TIMEOUT = "timeout";
    public static final String MONGODB_ERROR_CONNECTION = "connection";
    
    // MongoDB role names
    public static final String MONGODB_ROLE_DB_OWNER = "dbOwner";
    public static final String MONGODB_ROLE_USER_ADMIN = "userAdmin";
    public static final String MONGODB_ROLE_READ = "read";
    
    // Query result field names
    public static final String QUERY_RESULT_FIELD_RESULT = "result";
    public static final String QUERY_RESULT_FIELD_QUERY = "query";
    public static final String QUERY_RESULT_FIELD_HEADERS = "headers";
    public static final String QUERY_RESULT_FIELD_SUCCESS = "success";
    public static final String QUERY_RESULT_FIELD_OPERATION = "operation";
    public static final String QUERY_RESULT_FIELD_ASSET_ID = "assetId";
    public static final String QUERY_RESULT_FIELD_ASSET_NAME = "assetName";
    public static final String QUERY_RESULT_FIELD_DATABASE_TYPE = "databaseType";
    public static final String QUERY_RESULT_FIELD_ASSET_LOCKED = "assetLocked";
    public static final String QUERY_RESULT_FIELD_FAILED_USERS = "failedUsers";
    public static final String QUERY_RESULT_FIELD_SKIPPED_USERS = "skippedUsers";
    public static final String QUERY_RESULT_FIELD_FAILED_COUNT = "failedCount";
    public static final String QUERY_RESULT_FIELD_SKIPPED_COUNT = "skippedCount";
    public static final String QUERY_RESULT_OPERATION_UNLOCK = "unlock";
    public static final String QUERY_RESULT_FIELD_TOTAL_USERS = "totalUsers";
    public static final String PERMISSION_CHECK_TYPE_USER_MANAGEMENT_PERMISSIONS = "User management permissions";
    
    // Lockout operation field names
    public static final String LOCKOUT_FIELD_SUCCESS = "success";
    public static final String LOCKOUT_FIELD_OPERATION = "operation";
    public static final String LOCKOUT_FIELD_ASSET_ID = "assetId";
    public static final String LOCKOUT_FIELD_ASSET_NAME = "assetName";
    public static final String LOCKOUT_FIELD_DATABASE_TYPE = "databaseType";
    public static final String LOCKOUT_FIELD_ASSET_LOCKED = "assetLocked";
    public static final String LOCKOUT_FIELD_FAILED_USERS = "failedUsers";
    public static final String LOCKOUT_FIELD_SKIPPED_USERS = "skippedUsers";
    public static final String LOCKOUT_FIELD_FAILED_COUNT = "failedCount";
    public static final String LOCKOUT_FIELD_SKIPPED_COUNT = "skippedCount";
    public static final String LOCKOUT_OPERATION_LOCKOUT = "lockout";
    public static final String LOCKOUT_OPERATION_UNLOCK = "unlock";
    public static final String LOCKOUT_FIELD_LOCK_ALL_USERS = "lockAllUsers";
    public static final String LOCKOUT_FIELD_UNLOCK_ALL_USERS = "unlockAllUsers";
    
    // Permission check types
    public static final String PERMISSION_CHECK_TYPE_USER_MANAGEMENT_PERMISSIONS_VALUE = "user management Permissions";
    public static final String PERMISSION_USER_MANAGEMENT_PERMISSIONS = "User management permissions";

    // MSSQL-specific URL suffix with SSL parameters
    public static final String JDBC_SQLSERVER_SSL_PARAMS = ";encrypt=true;trustServerCertificate=true;characterEncoding=UTF-8";

    // Access request default expiry hours
    public static final String ACCESS_REQUEST_DEFAULT_EXPIRY_HOURS = "2160";

    // Invite code length
    public static final int INVITE_CODE_LENGTH = 15;

    // Database operations
    public static final String DB_SPLIT_PATTERN = "\\.";
    public static final String SQL_STATEMENT_SEPARATOR = ";";
    public static final String SQL_NEWLINE_SEPARATOR = "\n";
    public static final String SQL_SEMICOLON_NEWLINE = ";\n";

    // Error messages
    public static final String ERROR_PATTERN_NOT_FOUND_FOR_ID = "Pattern not found for id {}: {}";
    public static final String ERROR_SYSTEM_SENDER = "system";
    public static final String ERROR_SESSION_ID = "error";
    public static final String ERROR_FAILED_TO_GET_MASKING_POLICIES = "Failed to get masking policies";
    public static final String ERROR_FAILED_TO_GET_STATISTICS = "Failed to get statistics";
    public static final String ERROR_FAILED_TO_START_CHAT_SESSION = "Failed to start chat session. Please try again.";
    public static final String ERROR_FAILED_TO_APPLY_MASKING_POLICY = "Failed to apply the masking policy. Please try again.";
    public static final String ERROR_FAILED_TO_APPLY_MASKING_POLICIES = "Failed to apply masking policies: ";
    public static final String ERROR_FAILED_TO_RETRIEVE_SCHEMA_INFO = "Failed to retrieve schema information. Please try again.";
    public static final String ERROR_COULD_NOT_GET_CURRENT_USER_EMAIL = "Could not get current user email: {}";
    public static final String ERROR_EMPTY_REQUEST_BODY = "Empty request body";
    public static final String ERROR_INVALID_JSON_FORMAT = "Invalid JSON. Expected an object or an array of objects";

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
    public static final String NOTIFICATION_KEY_ACCESSOR_NAME = "accessorName";
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

    // Email regex pattern - simplified and safe
    public static final String EMAIL_REGEX = "^[A-Za-z0-9+_.\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$";

    // Default values
    public static final String DEFAULT_UNKNOWN_VALUE = "Unknown";

    // Query result messages
    public static final String QUERY_RESULT_EXECUTED_SUCCESSFULLY = " executed successfully";
    public static final String QUERY_RESULT_STATUS_SUCCESS = "SUCCESS";
    public static final String QUERY_RESULT_STATUS_FAILED = "FAILED";
    public static final String QUERY_RESULT_NO_DATA_RETURNED = "No data returned";
    public static final String QUERY_RESULT_ROWS_RETURNED = " rows returned";
    public static final String QUERY_RESULT_SEPARATOR = " | Result: ";

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
    public static final String ACCESS_REQUEST_NOT_FOUND = "Access request not found";
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
    public static final String ERROR_ASSET_LOCKED = "error.asset.locked";
    public static final String ERROR_FAILED_TO_CONNECT_TO_DATABASE = "error.failed.to.connect.to.database";
    public static final String ERROR_FAILED_USER_LOCKOUT = "error.failed.user.lockout";
    public static final String ERROR_FAILED_USER_UNLOCK = "error.failed.user.unlock";
    public static final String ERROR_UNSUPPORTED_DB_TYPE_USER_LISTING = "error.unsupported.db.type.user.listing";
    public static final String ERROR_UNSUPPORTED_DB_TYPE_LOCKED_USER_LISTING = "error.unsupported.db.type.locked.user.listing";
    public static final String ERROR_UNSUPPORTED_DB_TYPE_USER_LOCKING = "error.unsupported.db.type.user.locking";
    public static final String ERROR_UNSUPPORTED_DB_TYPE_USER_UNLOCKING = "error.unsupported.db.type.user.unlocking";
    public static final String ERROR_UNSUPPORTED_DB_TYPE_USER_PASSWORD_UPDATE = "error.unsupported.db.type.user.password.update";
    public static final String ERROR_USER_EMAIL_ALREADY_EXISTS = "user.email.already.exists";

    // Additional constants
    public static final String SUCCESS_CSV_GENERATED = "Sample CSV generated successfully";
    public static final String SUCCESS_USERS_CREATED = "Successfully created {0} users and sent email invites";
    public static final String SUCCESS_CREATED_USERS_AND_SENT_INVITES = "Successfully created {0} users and sent email invites";
    public static final String SUCCESS_GENERATING_CSV = "Generating sample CSV for bulk user creation";
    public static final String SUCCESS_CREATED_ASSETS = "Successfully created {0} assets";
    public static final String SUCCESS_ASSETS_EXPORTED = "Successfully exported {0} assets";
    public static final String ERROR_BULK_UPLOAD_FAILED = "error.bulk.upload.failed";
    public static final String ERROR_CREATING_EMAIL_ENTITY = "error.creating.email.entity";

    public static final String ROLE_NONE = "None";
    public static final String ROLE_ADMIN = "Admin";
    public static final String ROLE_ACCESSOR = "Accessor";
    public static final String ROLE_ASSET_OWNER = "Asset Owner";
    public static final String ROLE_APPROVER = "Approver";
    public static final String ROLE_AUDITOR = "Auditor";
    public static final String ERROR_VALIDATION_ERRORS_FOUND = "error.validation.errors.found";
    public static final String ERROR_UPLOADED_FILE_EMPTY = "Uploaded file is empty";
    public static final String ERROR_FILE_MUST_BE_CSV = "File must be a CSV file";
    public static final String ERROR_INVALID_EMAIL_FORMAT = "invalid email format: {0}";
    public static final String ERROR_DUPLICATE_EMAIL_CSV = "duplicate email in CSV: {0}";
    public static final String ERROR_EMAIL_ALREADY_EXISTS_SYSTEM = "email already exists in system: {0}";
    public static final String ERROR_INVALID_ROLE_NAME = "invalid role name: '{0}'. Available roles: Admin, Accessor, Asset Owner, Approver, Auditor";
    public static final String ERROR_NO_VALID_USER_DATA = "No valid user data found in CSV file";
    public static final String ERROR_NO_VALID_ASSET_DATA = "No valid asset data found in CSV file";
    public static final String ERROR_DUPLICATE_ASSET_NAME_CSV = "duplicate asset name in CSV: {0}";
    public static final String ERROR_ASSET_NAME_ALREADY_EXISTS_SYSTEM = "asset name already exists in system: {0}";
    public static final String ERROR_INVALID_ASSET_TYPE = "invalid asset type: '{0}'. Available types: DATABASE";
    public static final String ERROR_INVALID_DATABASE_TYPE = "invalid database type: '{0}'. Available types: MYSQL, POSTGRESQL, SQLSERVER, ORACLE, MONGODB";
    public static final String ERROR_USER_NOT_ASSET_OWNER = "user '{0}' does not have ASSET_OWNER role";
    public static final String ERROR_FIELD_REQUIRED = "{0} is required";
    public static final String ERROR_FIELD_REQUIRED_KEY = "error.field.required";


    // SQL constants
    public static final String ALTER_USER_IDENTIFIED_BY = "ALTER USER ? IDENTIFIED BY ?";

    public static final String STATUS_NAME = "status";
    public static final String ERROR_MESSAGE_NAME = "error_message";
    public static final String TIMESTAMP_NAME = "timestamp";
    public static final String HEALTHY_STATUS = "HEALTHY";
    public static final String UNHEALTHY_STATUS = "UNHEALTHY";
    public static final String ERROR_STATUS = "ERROR";
    
    // Pagination and response field constants
    public static final String FIELD_POLICIES = "policies";
    public static final String FIELD_TOTAL_ELEMENTS = "totalElements";
    public static final String FIELD_TOTAL_PAGES = "totalPages";
    public static final String FIELD_CURRENT_PAGE = "currentPage";
    public static final String FIELD_SIZE = "size";
    public static final String FIELD_CREATED = "created";
    public static final String FIELD_IS_ACTIVE = "isActive";
    public static final String FIELD_TOTAL_POLICIES = "totalPolicies";
    public static final String FIELD_ACTIVE_POLICIES = "activePolicies";
    public static final String FIELD_INACTIVE_POLICIES = "inactivePolicies";
    public static final String FIELD_USER_EMAIL = "userEmail";
    
    // Status constants
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_INACTIVE = "inactive";
    
    // User status constants
    public static final String USER_STATUS_ACTIVE = "Active";
    public static final String USER_STATUS_INACTIVE = "Inactive";
    public static final String USER_STATUS_INVITE_SENT = "Invite sent";
    public static final String USER_STATUS_DELETED = "Deleted";
    
    // Masking strategy constants
    public static final String MASKING_STRATEGY_PARTIAL = "partial";
    public static final String MASKING_STRATEGY_FULL = "full";
    public static final String MASKING_STRATEGY_HASH = "hash";
    public static final String MASKING_STRATEGY_CUSTOM = "custom";
    
    // Intent type constants
    public static final String INTENT_TYPE_MANUAL_CREATE = "manual_create";
    
    // Role constants
    public static final String ROLE_ALL = "all";
    
    // Sort field constants
    public static final String SORT_FIELD_CREATED_AT = "createdAt";

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

    /**
     * Get a boolean technical property value with default
     * @param key the property key
     * @param defaultValue the default value
     * @return the boolean property value or default
     */
    public static boolean getTechnicalPropertyAsBoolean(String key, boolean defaultValue) {
        try {
            String value = getTechnicalProperty(key, String.valueOf(defaultValue));
            return Boolean.parseBoolean(value);
        } catch (Exception e) {
            // Log debug message for configuration issues but don't throw exception
            // This allows the application to continue with default values
            return defaultValue;
        }
    }

    // ===== TERMINAL WEBSOCKET CONSTANTS =====
    
    // Terminal field names
    public static final String SESSION_START_FIELD = "sessionStart";
    public static final String UNKNOWN_VALUE = "unknown";
    
    // Terminal recording log messages
    public static final String MSG_NO_RECORDING_FOUND = "No recording found for session: {}";
    public static final String MSG_RECORDING_NOT_ACTIVE = "Recording is not active for session: {}";
    
    // Terminal recording regex patterns
    public static final String REGEX_ESCAPE_BACKSLASH = "\\\\";
    public static final String REGEX_NEWLINE_PATTERN = "[\\r\\n]";
    
    // Terminal service regex patterns
    public static final String REGEX_ESCAPE_BACKSLASH_SIMPLE = "\\";
    public static final String REGEX_NEWLINE_ESCAPE = "\\\\n";
    public static final String REGEX_CRLF_PATTERN = "\r\n";
    public static final String REGEX_CRLF_ESCAPE = "\\\\n";
    
    // Request/Response Field Names
    public static final String TERMINAL_ACTION = "action";
    public static final String TERMINAL_ASSET_ID = "assetId";
    public static final String TERMINAL_TOKEN = "token";
    public static final String TERMINAL_AUTH_PROVIDER = "authProvider";
    public static final String TERMINAL_MESSAGE = "message";
    public static final String TERMINAL_SESSION_ID = "sessionId";
    public static final String TERMINAL_TYPE = "type";
    public static final String TERMINAL_DATA = "data";
    public static final String TERMINAL_COMMAND = "command";
    public static final String TERMINAL_COLS = "cols";
    public static final String TERMINAL_ROWS = "rows";
    
    // Action Types
    public static final String TERMINAL_ACTION_AUTHENTICATE = "authenticate";
    // TERMINAL_ACTION_INPUT and TERMINAL_ACTION_COMMAND removed - now using TERMINAL_ACTION_KEYBOARD_EVENT
    public static final String TERMINAL_ACTION_RESIZE = "resize";
    public static final String TERMINAL_ACTION_DISCONNECT = "disconnect";
    public static final String TERMINAL_ACTION_GET_FOLDER_SUGGESTIONS = "get_folder_suggestions";
    public static final String TERMINAL_ACTION_INTERRUPT = "interrupt";
    public static final String TERMINAL_ACTION_KEYBOARD_EVENT = "keyboard_event";
    public static final String TERMINAL_ACTION_AUTHENTICATION_SUCCESS = "authentication_success";
    
    // Response Types
    public static final String TERMINAL_TYPE_CONNECTION_READY = "connection_ready";
    public static final String TERMINAL_TYPE_CONNECTION_ESTABLISHED = "connection_established";
    public static final String TERMINAL_TYPE_CONNECTION_PROGRESS = "connection_progress";
    public static final String TERMINAL_TYPE_SSH_CONNECTED = "ssh_connected";
    public static final String TERMINAL_TYPE_SSH_ERROR = "ssh_error";
    public static final String TERMINAL_TYPE_ERROR = "error";
    public static final String TERMINAL_TYPE_RESIZE_CONFIRMED = "resize_confirmed";
    public static final String TERMINAL_TYPE_INTERRUPT_CONFIRMED = "interrupt_confirmed";
    public static final String TERMINAL_TYPE_DISCONNECT_CONFIRMED = "disconnect_confirmed";
    // Tab completion is now handled natively by SSH terminal - no special response type needed
    public static final String TERMINAL_TYPE_OUTPUT = "output";
    public static final String TERMINAL_CONNECTION_ERROR = "connection_error";
    
    // Regular Expression Patterns
    public static final String REGEX_NEWLINE_REPLACE = "[\\r\\n]";
    public static final String REGEX_NEWLINE_REPLACEMENT = "\\\\n";
    
    // SSH Connection Constants
    public static final String SSH_NEWLINE_ESCAPE = "\\\\n";
    public static final String SSH_REGEX_NEWLINE = "[\\r\\n]";
    public static final String ENCODING_UTF8 = "UTF-8";
    
    // Terminal Success Messages
    public static final String MSG_TERMINAL_CONNECTION_READY = "Terminal connection ready - send authenticate action to proceed";
    public static final String MSG_TERMINAL_SESSION_ESTABLISHED = "Terminal session established successfully";
    public static final String MSG_TERMINAL_SESSION_AUTHENTICATED = "Terminal session authenticated successfully";
    public static final String MSG_SSH_CONNECTION_ESTABLISHED = "SSH connection established successfully";
    public static final String MSG_TERMINAL_SESSION_CLOSED = "Terminal session closed successfully";
    public static final String MSG_INTERRUPT_SIGNAL_SENT = "Interrupt signal sent successfully";
    public static final String MSG_CONNECTING_TO_SSH = "Connecting to SSH server...";
    
    // Terminal Error Messages
    public static final String MSG_MISSING_REQUIRED_PARAMS = "Missing required parameters: assetId, host, port, token, authProvider";
    public static final String MSG_INVALID_NUMERIC_PARAMS = "Invalid numeric parameters: assetId must be numbers";
    public static final String MSG_AUTHENTICATION_TOKEN_REQUIRED = "Authentication token required";
    public static final String MSG_SESSION_METADATA_NOT_FOUND = "Session metadata not found";
    public static final String MSG_TERMINAL_SESSION_NOT_FOUND = "Terminal session not found";
    public static final String MSG_SSH_SESSION_NOT_CONNECTED = "SSH session not connected";
    
    // Terminal Error Message Prefixes (for concatenation with dynamic content)
    public static final String MSG_FAILED_TO_ESTABLISH_CONNECTION = "Failed to establish terminal connection: ";
    public static final String MSG_AUTHENTICATION_FAILED = "Authentication failed: ";
    public static final String MSG_AUTHENTICATION_PROCESSING_ERROR = "Authentication processing error: ";
    public static final String MSG_FAILED_TO_ESTABLISH_SSH = "Failed to establish SSH connection: ";
    public static final String MSG_FAILED_TO_SEND_INPUT = "Failed to send input to SSH session: ";
    public static final String MSG_FAILED_TO_PROCESS_MESSAGE = "Failed to process message: ";
    public static final String MSG_INVALID_AUTH_PROVIDER = "Invalid auth provider: ";
    
    // Common JSON field names
    public static final String JSON_FIELD_STATUS = "status";
    public static final String JSON_FIELD_ERROR = "error";
    public static final String JSON_FIELD_MESSAGE = "message";
    
    // Terminal connection types
    public static final String CONNECTION_TYPE_UNIX_GROUPS = "unix-groups";
    public static final String CONNECTION_TYPE_TERMINAL = "terminal";
    public static final String CONNECTION_TYPE_FIELD = "connectionType";
    
    // Terminal log messages
    public static final String LOG_SESSION_ID = "Session ID: {}";
    
    // AI intent types
    public static final String AI_INTENT_TYPE_CUSTOM = "custom";
    
    // Session metadata field names
    public static final String SESSION_METADATA_CLIENT_IP = "clientIp";
    public static final String SESSION_METADATA_USER_AGENT = "userAgent";
    
    // Unix command constants
    public static final String UNIX_FIND_COMPATIBILITY_TEST_COMMAND = "find --version 2>/dev/null || find -version 2>/dev/null || echo 'find_available'";
    
    // Logging configuration constants
    public static final String LOGGING_DETAILED_REQUEST_RESPONSE_ENABLED = "logging.detailed.request.response.enabled";
    
    // Keycloak authentication flow constants
    public static final String KEYCLOAK_FLOW_BROWSER = "browser";
    public static final String KEYCLOAK_FLOW_DIRECT_GRANT = "direct grant";
    
    // Keycloak identity provider constants
    public static final String KEYCLOAK_IDP_GOOGLE = "google";
    public static final String KEYCLOAK_IDP_MICROSOFT = "microsoft";
    public static final String KEYCLOAK_IDP_MAPPER_GOOGLE_USER_ATTRIBUTE = "google-user-attribute-mapper";
    public static final String KEYCLOAK_IDP_MAPPER_OIDC_USER_ATTRIBUTE = "oidc-user-attribute-idp-mapper";
    public static final String KEYCLOAK_IDP_MAPPER_HARDCODED_ATTRIBUTE = "hardcoded-attribute-idp-mapper";
    
    // Keycloak mapper configuration constants
    public static final String KEYCLOAK_MAPPER_SYNC_MODE_INHERIT = "INHERIT";
    public static final String KEYCLOAK_MAPPER_SYNC_MODE_FORCE = "FORCE";
    public static final String KEYCLOAK_MAPPER_SYNC_MODE = "syncMode";
    public static final String KEYCLOAK_MAPPER_CLAIM = "claim";
    public static final String KEYCLOAK_MAPPER_JSON_FIELD = "jsonField";
    public static final String KEYCLOAK_MAPPER_USER_ATTRIBUTE = "user.attribute";
    public static final String KEYCLOAK_MAPPER_USER_ATTRIBUTE_GOOGLE = "userAttribute";
    public static final String KEYCLOAK_MAPPER_ATTRIBUTE_VALUE = "attribute.value";
    public static final String KEYCLOAK_MAPPER_ATTRIBUTE = "attribute";
    public static final String KEYCLOAK_MAPPER_EMAIL = "email";
    public static final String KEYCLOAK_MAPPER_GIVEN_NAME = "given_name";
    public static final String KEYCLOAK_MAPPER_FIRST_NAME = "firstName";
    public static final String KEYCLOAK_MAPPER_LAST_NAME = "lastName";
    public static final String KEYCLOAK_MAPPER_USERNAME = "username";
    public static final String KEYCLOAK_MAPPER_LAST_NAME_DEFAULT = "Last";
    public static final String KEYCLOAK_MAPPER_PREFERRED_USERNAME = "preferred_username";
    public static final String KEYCLOAK_MAPPER_FAMILY_NAME = "family_name";
    
    // Keycloak authentication execution names
    public static final String KEYCLOAK_EXECUTION_DIRECT_GRANT_CONDITIONAL_OTP = "Direct Grant - Conditional OTP";
    public static final String KEYCLOAK_EXECUTION_BROWSER_CONDITIONAL_OTP = "Browser - Conditional OTP";
    
    // Keycloak requirement levels
    public static final String KEYCLOAK_REQUIREMENT_REQUIRED = "REQUIRED";
    public static final String KEYCLOAK_REQUIREMENT_CONDITIONAL = "CONDITIONAL";
    
    // Audit Entity Listener constants
    public static final String AUDIT_SYSTEM_USER = "system";
    public static final String AUDIT_UNKNOWN_ENTITY = "unknown";
    
    // Entity class names
    public static final String ENTITY_CLASS_ASSET = "Asset";
    public static final String ENTITY_CLASS_USER = "User";
    public static final String ENTITY_CLASS_ROLE = "Role";
    public static final String ENTITY_CLASS_ACCESS_REQUEST = "AccessRequest";
    public static final String ENTITY_CLASS_ASSET_CREDENTIAL = "AssetCredential";
    public static final String ENTITY_CLASS_ASSET_APPROVER = "AssetApprover";
    public static final String ENTITY_CLASS_AI_PROMPT = "AIPrompt";
    public static final String ENTITY_CLASS_AI_SENSITIVE_PATTERN = "AISensitivePattern";
    public static final String ENTITY_CLASS_AI_CATEGORY = "AICategory";
    public static final String ENTITY_CLASS_EMAIL = "Email";
    
    // Entity type names
    public static final String ENTITY_TYPE_ACCESS_REQUEST = "ACCESS_REQUEST";
    public static final String ENTITY_TYPE_ASSET = "ASSET";
    public static final String ENTITY_TYPE_USER = "USER";
    public static final String ENTITY_TYPE_ASSET_CREDENTIAL = "ASSET_CREDENTIAL";
    public static final String ENTITY_TYPE_EMAIL = "EMAIL";
    
    // Approval status values
    public static final String APPROVAL_STATUS_APPROVED = "APPROVED";
    public static final String APPROVAL_STATUS_REJECTED = "REJECTED";
    
    // Method names
    public static final String METHOD_GET_ID = "getId";
    public static final String METHOD_GET_NAME = "getName";
    public static final String METHOD_GET_EMAIL = "getEmail";
    public static final String METHOD_GET_PROMPT_KEY = "getPromptKey";
    public static final String METHOD_GET_EMAIL_TO = "getEmailTo";
    public static final String METHOD_GET_ACCESSOR_APPROVER_STATUS = "getAccessorApproverStatus";
    public static final String METHOD_GET_ASSET_APPROVER_STATUS = "getAssetApproverStatus";
    
    // Default entity identifiers
    public static final String DEFAULT_UNKNOWN_ASSET = "Unknown Asset";
    public static final String DEFAULT_UNKNOWN_USER = "Unknown User";
    public static final String DEFAULT_UNKNOWN_ROLE = "Unknown Role";
    public static final String DEFAULT_UNKNOWN_PROMPT = "Unknown Prompt";
    public static final String DEFAULT_UNKNOWN_PATTERN = "Unknown Pattern";
    public static final String DEFAULT_UNKNOWN_CATEGORY = "Unknown Category";
    
    // Entity identifier prefixes
    public static final String ENTITY_PREFIX_ASSET = "Asset-";
    public static final String ENTITY_PREFIX_USER = "User-";
    public static final String ENTITY_PREFIX_ROLE = "Role-";
    public static final String ENTITY_PREFIX_ACCESS_REQUEST = "AccessRequest-";
    public static final String ENTITY_PREFIX_ASSET_CREDENTIAL = "AssetCredential-";
    public static final String ENTITY_PREFIX_ASSET_APPROVER = "AssetApprover-";
    public static final String ENTITY_PREFIX_AI_PROMPT = "AIPrompt-";
    public static final String ENTITY_PREFIX_AI_SENSITIVE_PATTERN = "AISensitivePattern-";
    public static final String ENTITY_PREFIX_AI_CATEGORY = "AICategory-";
    public static final String ENTITY_PREFIX_EMAIL = "Email-";
    
    // Entity identifier descriptions
    public static final String ENTITY_DESC_REQUEST_FOR = "Request for ";
    public static final String ENTITY_DESC_CREDENTIAL_FOR = "Credential for ";
    public static final String ENTITY_DESC_APPROVER_FOR = "Approver for ";
    public static final String ENTITY_DESC_EMAIL_TO = "Email to ";
    
    // Action prefixes
    public static final String ACTION_PREFIX_CREATE = "CREATE_";
    public static final String ACTION_PREFIX_UPDATE = "UPDATE_";
    public static final String ACTION_PREFIX_DELETE = "DELETE_";
    
    // Audit Trail DTO constants
    public static final String AUDIT_DESC_PERFORMED_ACTION_ON = "Performed action on";
    
    // Action descriptions
    public static final String AUDIT_ACTION_DESC_CREATED = "Created";
    public static final String AUDIT_ACTION_DESC_UPDATED = "Updated";
    public static final String AUDIT_ACTION_DESC_DELETED = "Deleted";
    public static final String AUDIT_ACTION_DESC_LOGGED_IN_TO = "Logged in to";
    public static final String AUDIT_ACTION_DESC_LOGGED_OUT_FROM = "Logged out from";
    public static final String AUDIT_ACTION_DESC_EXECUTED_QUERY_ON = "Executed query on";
    public static final String AUDIT_ACTION_DESC_EXECUTED_COMMAND_ON = "Executed command on";
    public static final String AUDIT_ACTION_DESC_APPROVED = "Approved";
    public static final String AUDIT_ACTION_DESC_REJECTED = "Rejected";
    public static final String AUDIT_ACTION_DESC_APPROVAL_ACTION_ON = "Approval action on";
    public static final String AUDIT_ACTION_DESC_APPLIED_AI_MASKING_TO = "Applied AI masking to";
    public static final String AUDIT_ACTION_DESC_DOWNLOADED_FROM = "Downloaded from";
    public static final String AUDIT_ACTION_DESC_UPLOADED_TO = "Uploaded to";
    
    // Action type constants
    public static final String AUDIT_ACTION_TYPE_CREATE = "CREATE";
    public static final String AUDIT_ACTION_TYPE_CREATE_ASSET = "CREATE_ASSET";
    public static final String AUDIT_ACTION_TYPE_UPDATE = "UPDATE";
    public static final String AUDIT_ACTION_TYPE_UPDATE_ASSET = "UPDATE_ASSET";
    public static final String AUDIT_ACTION_TYPE_DELETE = "DELETE";
    public static final String AUDIT_ACTION_TYPE_DELETE_ASSET = "DELETE_ASSET";
    public static final String AUDIT_ACTION_TYPE_LOGIN = "LOGIN";
    public static final String AUDIT_ACTION_TYPE_LOGOUT = "LOGOUT";
    public static final String AUDIT_ACTION_TYPE_QUERY = "QUERY";
    public static final String AUDIT_ACTION_TYPE_COMMAND = "COMMAND";
    public static final String AUDIT_ACTION_TYPE_APPROVE = "APPROVE";
    public static final String AUDIT_ACTION_TYPE_REJECT = "REJECT";
    public static final String AUDIT_ACTION_TYPE_APPROVAL = "APPROVAL";
    public static final String AUDIT_ACTION_TYPE_AI_MASKING_APPLIED = "AI_MASKING_APPLIED";
    public static final String AUDIT_ACTION_TYPE_DOWNLOAD = "DOWNLOAD";
    public static final String AUDIT_ACTION_TYPE_UPLOAD = "UPLOAD";
    public static final String AUDIT_ACTION_TYPE_QUERY_EXECUTED = "QUERY_EXECUTED";
    public static final String AUDIT_ACTION_TYPE_QUERY_FAILED = "QUERY_FAILED";
    public static final String AUDIT_ACTION_TYPE_DATA_ACCESS_WITH_MASKING = "DATA_ACCESS_WITH_MASKING";
    
    // SQL keywords
    public static final String SQL_KEYWORD_SELECT = "SELECT";
    public static final String SQL_KEYWORD_INSERT = "INSERT";
    public static final String SQL_KEYWORD_UPDATE = "UPDATE";
    public static final String SQL_KEYWORD_DELETE = "DELETE";
    public static final String SQL_KEYWORD_LIMIT = "LIMIT";
    
    // Query result structure fields
    public static final String QUERY_RESULT_FIELD_RESULTS = "results";
    public static final String QUERY_RESULT_FIELD_DATA = "data";
    
    // Database schema fields
    public static final String SCHEMA_FIELD_TABLE_NAME = "TABLE_NAME";
    public static final String SCHEMA_FIELD_TABLE_TYPE = "TABLE_TYPE";
    public static final String SCHEMA_FIELD_COLUMN_NAME = "COLUMN_NAME";
    public static final String SCHEMA_FIELD_TYPE_NAME = "TYPE_NAME";
    public static final String SCHEMA_FIELD_COLUMN_SIZE = "COLUMN_SIZE";
    public static final String SCHEMA_FIELD_IS_NULLABLE = "IS_NULLABLE";
    public static final String SCHEMA_FIELD_COLUMN_DEF = "COLUMN_DEF";
    public static final String SCHEMA_FIELD_COLUMN_KEY = "COLUMN_KEY";
    public static final String SCHEMA_FIELD_REMARKS = "REMARKS";
    public static final String SCHEMA_FIELD_ORDINAL_POSITION = "ORDINAL_POSITION";
    public static final String SCHEMA_FIELD_IS_AUTOINCREMENT = "IS_AUTOINCREMENT";
    
    // Database schema types
    public static final String SCHEMA_TYPE_TABLE = "TABLE";
    public static final String SCHEMA_TYPE_VIEW = "VIEW";
    public static final String SCHEMA_NULLABLE_YES = "YES";
    public static final String SCHEMA_NULLABLE_NO = "NO";
    
    // Approval context descriptions
    public static final String AUDIT_APPROVAL_ACCESS_REQUEST_APPROVED = "Access request approved";
    public static final String AUDIT_APPROVAL_ACCESS_REQUEST_REJECTED = "Access request rejected";
    public static final String AUDIT_APPROVAL_STATUS_UPDATED = "Approval status updated";
    public static final String AUDIT_APPROVAL_ACTION_PERFORMED = "Approval action performed";
    
    // Logging messages
    public static final String LOG_ERROR_UPDATING_PASSWORD_ID = "log.error.updating.password";
    
    // WebSocket message types
    public static final String WS_MESSAGE_TYPE_PERMISSION_APPLIED = "permission_applied";
    public static final String WS_MESSAGE_TYPE_SSH_CONNECTION_FAILED = "ssh_connection_failed";
    public static final String WS_MESSAGE_TYPE_SSH_CONNECTED = "ssh_connected";
    public static final String WS_MESSAGE_TYPE_AUTHENTICATION_SUCCESS = "authentication_success";
    public static final String WS_MESSAGE_TYPE_FOLDER_SUGGESTIONS = "folder_suggestions";
    public static final String WS_MESSAGE_TYPE_ERROR = "error";
    public static final String WS_MESSAGE_TYPE_CONNECTION_READY = "connection_ready";
    
    // WebSocket field names
    public static final String WS_FIELD_SESSION_ID = "sessionId";
    public static final String WS_FIELD_FOLDER_PATH = "folderPath";
    public static final String WS_FIELD_SUGGESTIONS = "suggestions";
    public static final String WS_FIELD_PATH = "path";
    public static final String WS_FIELD_ACTION = "action";
    public static final String WS_FIELD_USER_ACCESS_TYPE = "userAccessType";
    
    // SSH timeout constants (5 minutes)
    public static final int SSH_SESSION_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes
    public static final int SSH_CONNECT_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes
    public static final int SSH_CHANNEL_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes
    public static final int SSH_COMMAND_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes
    
    // Database type names for logging
    public static final String DB_TYPE_MYSQL = "MySQL";
    public static final String DB_TYPE_POSTGRESQL = "PostgreSQL";
    public static final String DB_TYPE_SQL_SERVER = "SQL Server";
    public static final String DB_TYPE_ORACLE = "Oracle";
    
    // Database query result field names
    public static final String DB_QUERY_RESULT_COUNT = "count";
    
    // System Settings Keys
    public static final String SYSTEM_TIMEZONE_KEY = "system.timezone";
    public static final String DEFAULT_TIMEZONE = "UTC";
    public static final String AWS_SECRETS_MANAGER_ENABLED_KEY = "aws.secrets.manager.enabled";
    public static final String AWS_SECRETS_MANAGER_ENABLED_DEFAULT = "false";
    
    // AWS Secrets Manager error message keys
    public static final String ERROR_AWS_SECRETS_MANAGER_RETRIEVAL_FAILED = "error.aws.secrets.manager.retrieval.failed";
    
    // Timezone formatting patterns
    public static final String TIMEZONE_DISPLAY_PATTERN = "yyyy-MM-dd HH:mm:ss z";
    public static final String TIMEZONE_DATE_PATTERN = "yyyy-MM-dd";
    public static final String TIMEZONE_TIME_PATTERN = "HH:mm:ss";
    
    // Keycloak retry error messages
    public static final String ERROR_THREAD_INTERRUPTED_DURING_RETRY_DELAY = "Thread interrupted during retry delay";
    
    // Error message prefixes
    public static final String ERROR_PREFIX_UNEXPECTED = "Unexpected error: ";
    
    // Log message templates
    public static final String LOG_ERROR_UPDATE_ASSET_OBJECTS_FOR_CREDENTIAL = "Failed to update asset objects for credential: {}";
    
    // Timezone constants
    public static final String TIMEZONE_UTC_OFFSET_DEFAULT = "(UTC+00:00)";
    public static final String TIMEZONE_COUNTRY_AUSTRALIA = "Australia";
    public static final String TIMEZONE_COUNTRY_UNITED_STATES = "United States";
    public static final String TIMEZONE_COUNTRY_CANADA = "Canada";
    
    // HTTP/URL constants for Keycloak impersonation
    public static final String HTTP_PROTOCOL_PREFIX = "http://";
    public static final String HTTPS_PROTOCOL_PREFIX = "https://";
    public static final String HTTP_HEADER_LOCATION = "Location";
    public static final String URL_ACCESS_TOKEN_PARAM = "access_token=";
    
    // Keycloak path constants
    public static final String KEYCLOAK_REALMS_PATH = "/realms/";
}
