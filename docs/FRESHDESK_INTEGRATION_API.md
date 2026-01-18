# Freshdesk Integration - Backend API Documentation

## Overview

This document describes the backend API endpoints for Freshdesk integration. These endpoints allow the Freshdesk sidebar app to authenticate users and access Hagrids database query functionality.

---

## Base URL

**Freshdesk API**: `/api/freshdesk`

---

## Authentication Flow

The Freshdesk app authenticates users as developers in Hagrids:

1. Freshdesk user opens a ticket
2. Freshdesk app calls `/api/freshdesk/auth` with user's email
3. Backend verifies user exists and has DEVELOPER role
4. Backend returns a Hagrids authentication token
5. Frontend uses this token for subsequent API calls

---

## API Endpoints

### 1. Health Check

**Endpoint:** `GET /api/freshdesk/health`

**Description:** Health check endpoint for Freshdesk app.

**Authentication:** Not required

**Request:**
```
GET /api/freshdesk/health
```

**Response:**
```json
{
  "status": "ok",
  "service": "freshdesk-integration"
}
```

---

### 2. Authenticate Freshdesk User

**Endpoint:** `POST /api/freshdesk/auth`

**Description:** Authenticates a Freshdesk user as a Hagrids developer and returns an authentication token.

**Authentication:** Not required (this is the authentication endpoint)

**Request:**
```
POST /api/freshdesk/auth
Content-Type: application/json

Body:
{
  "email": "user@example.com",
  "freshdeskToken": "optional-freshdesk-token"
}
```

**Request Body:**
```json
{
  "email": "user@example.com",
  "freshdeskToken": "optional-freshdesk-token"
}
```

**Request Fields:**
- `email` (string, required): Freshdesk user's email address (must match Hagrids user email)
- `freshdeskToken` (string, optional): Freshdesk authentication token for validation

**Response:**
```json
{
  "success": true,
  "token": "hagrids-jwt-token-here",
  "user": {
    "id": 123,
    "email": "user@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "isActive": true
  },
  "message": "Authentication successful"
}
```

**Response Fields:**
- `success` (boolean): Authentication success status
- `token` (string): Hagrids JWT token for subsequent API calls
- `user` (object): User information
  - `id` (number): User ID
  - `email` (string): User email
  - `firstName` (string): User first name
  - `lastName` (string): User last name
  - `isActive` (boolean): User active status
- `message` (string): Success message

**Status Codes:**
- `200 OK`: Authentication successful
- `401 Unauthorized`: Authentication failed
  - User not found in Hagrids
  - User does not have DEVELOPER role
  - User account is not active

**Error Response:**
```json
{
  "timestamp": "2025-01-15T14:45:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "User does not have DEVELOPER role. Access denied."
}
```

---

### 3. Get Assets

**Endpoint:** `GET /api/freshdesk/assets`

**Description:** Retrieves all database assets available to the authenticated developer.

**Authentication:** Required (Bearer token from `/api/freshdesk/auth`)

**Request:**
```
GET /api/freshdesk/assets
Authorization: Bearer <hagrids-token>
```

**Response:**
```json
[
  {
    "id": 1,
    "name": "Production Database",
    "hostAddress": "db.example.com",
    "portNumber": "3306",
    "databaseType": "MYSQL",
    "databaseName": "production_db"
  },
  {
    "id": 2,
    "name": "Staging Database",
    "hostAddress": "staging-db.example.com",
    "portNumber": "5432",
    "databaseType": "POSTGRESQL",
    "databaseName": "staging_db"
  }
]
```

**Status Codes:**
- `200 OK`: Success
- `401 Unauthorized`: Invalid or missing token
- `403 Forbidden`: User does not have DEVELOPER role

---

### 4. Get Access Requests

**Endpoint:** `GET /api/freshdesk/access-requests`

**Description:** Retrieves approved access requests for the authenticated developer.

**Authentication:** Required (Bearer token)

**Request:**
```
GET /api/freshdesk/access-requests?assetId=123
Authorization: Bearer <hagrids-token>
```

**Query Parameters:**
- `assetId` (number, optional): Filter by specific asset ID

**Response:**
```json
[
  {
    "id": 456,
    "assetId": 123,
    "assetName": "Production Database",
    "status": "APPROVED",
    "expiryDate": "2025-12-31T23:59:59",
    "requestedUsername": "dev_user"
  }
]
```

**Response Fields:**
- `id` (number): Access request ID
- `assetId` (number): Asset ID
- `assetName` (string): Asset name
- `status` (string): Approval status (APPROVED)
- `expiryDate` (string): ISO 8601 expiry date/time
- `requestedUsername` (string): Requested database username

**Status Codes:**
- `200 OK`: Success
- `401 Unauthorized`: Invalid or missing token
- `403 Forbidden`: User does not have DEVELOPER role

---

### 5. Get Database Schema

**Endpoint:** `GET /api/freshdesk/schema`

**Description:** Retrieves database schema for a specific access request.

**Authentication:** Required (Bearer token)

**Request:**
```
GET /api/freshdesk/schema?requestId=456
Authorization: Bearer <hagrids-token>
```

**Query Parameters:**
- `requestId` (number, required): Access request ID

**Response:**
```json
{
  "tables": [
    {
      "name": "users",
      "columns": [
        {
          "name": "id",
          "type": "INT",
          "nullable": false
        },
        {
          "name": "email",
          "type": "VARCHAR(255)",
          "nullable": false
        }
      ]
    }
  ],
  "totalTables": 10,
  "totalColumns": 150
}
```

**Status Codes:**
- `200 OK`: Success
- `400 Bad Request`: Invalid requestId
- `401 Unauthorized`: Invalid or missing token
- `403 Forbidden`: User does not own this access request

---

### 6. Execute Query

**Endpoint:** `POST /api/freshdesk/run-query`

**Description:** Executes a SQL query on a database asset via an access request.

**Authentication:** Required (Bearer token)

**Request:**
```
POST /api/freshdesk/run-query
Authorization: Bearer <hagrids-token>
Content-Type: application/json

Body:
{
  "assetId": 123,
  "requestId": 456,
  "query": "SELECT * FROM users LIMIT 10"
}
```

**Request Body:**
```json
{
  "assetId": 123,
  "requestId": 456,
  "query": "SELECT * FROM users LIMIT 10"
}
```

**Request Fields:**
- `assetId` (number, required): Asset ID
- `requestId` (number, required): Access request ID
- `query` (string, required): SQL query to execute

**Response:**
```json
{
  "status": "success",
  "results": {
    "columns": ["id", "email", "name"],
    "rows": [
      [1, "user1@example.com", "User One"],
      [2, "user2@example.com", "User Two"]
    ],
    "rowCount": 2
  }
}
```

**Status Codes:**
- `200 OK`: Query executed successfully
- `400 Bad Request`: Invalid query or missing parameters
- `401 Unauthorized`: Invalid or missing token
- `403 Forbidden`: User does not own this access request

---

### 7. Convert Natural Language to SQL

**Endpoint:** `POST /api/freshdesk/convert-nl-to-sql`

**Description:** Converts a natural language query to SQL using AI.

**Authentication:** Required (Bearer token)

**Request:**
```
POST /api/freshdesk/convert-nl-to-sql
Authorization: Bearer <hagrids-token>
Content-Type: application/json

Body:
{
  "requestId": 456,
  "naturalLanguageQuery": "Show me all users from the users table"
}
```

**Request Body:**
```json
{
  "requestId": 456,
  "naturalLanguageQuery": "Show me all users from the users table"
}
```

**Request Fields:**
- `requestId` (number, required): Access request ID
- `naturalLanguageQuery` (string, required): Natural language query description

**Response:**
```json
{
  "sql": "SELECT * FROM users",
  "confidence": 0.95,
  "explanation": "This query selects all columns from the users table"
}
```

**Status Codes:**
- `200 OK`: Conversion successful
- `400 Bad Request`: Invalid request or missing parameters
- `401 Unauthorized`: Invalid or missing token
- `403 Forbidden`: User does not own this access request

---

## Security Configuration

### CORS Configuration

The Freshdesk endpoints have CORS enabled to allow requests from Freshdesk domains:

```java
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {...})
```

**Note:** In production, restrict `origins` to specific Freshdesk domains for security.

### Authorization

All endpoints (except `/health` and `/auth`) require:
1. Valid Bearer token in `Authorization` header
2. User must have DEVELOPER role
3. User must own the access request (for request-specific endpoints)

---

## Error Handling

### Common Error Responses

**401 Unauthorized:**
```json
{
  "timestamp": "2025-01-15T14:45:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication failed: User not found"
}
```

**403 Forbidden:**
```json
{
  "timestamp": "2025-01-15T14:45:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied: DEVELOPER role required"
}
```

**400 Bad Request:**
```json
{
  "timestamp": "2025-01-15T14:45:00",
  "status": 400,
  "error": "Bad Request",
  "message": "requestId is required for developer queries"
}
```

---

## Implementation Notes

### Token Generation

The `/api/freshdesk/auth` endpoint currently returns a session ID. In production, this should:

1. Generate a proper JWT token using Keycloak
2. Store session mapping in Redis/cache
3. Set appropriate expiration time
4. Support token refresh

### User Mapping

- Freshdesk user email must match Hagrids user email exactly
- User must exist in Hagrids database
- User must have DEVELOPER role assigned
- User account must be active

### Access Request Validation

All request-specific endpoints verify:
- User owns the access request
- Access request is approved
- Access request is not expired

---

## Summary

### API Endpoints

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/api/freshdesk/health` | Health check | No |
| POST | `/api/freshdesk/auth` | Authenticate user | No |
| GET | `/api/freshdesk/assets` | Get assets | Yes |
| GET | `/api/freshdesk/access-requests` | Get access requests | Yes |
| GET | `/api/freshdesk/schema` | Get schema | Yes |
| POST | `/api/freshdesk/run-query` | Execute query | Yes |
| POST | `/api/freshdesk/convert-nl-to-sql` | Convert NL to SQL | Yes |

### Authentication Flow

1. Frontend calls `/api/freshdesk/auth` with email
2. Backend validates user and returns token
3. Frontend uses token in `Authorization: Bearer <token>` header
4. Backend validates token on each request

---

**Last Updated:** 2025-01-15
**Version:** 1.0


