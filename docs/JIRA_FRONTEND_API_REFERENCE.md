# Jira Integration - Frontend API Reference

## Base URL
```
https://your-dam-backend.com/api/jira
```

## Authentication
All endpoints require Jira user email in header:
```
X-Jira-User-Email: user@example.com
```

**Important:** User email must:
- Match registered email in DAM backend
- User must have appropriate role (ACCESSOR or ASSET_OWNER)
- User account must be active

---

## API Endpoints

### 1. Get Available Assets (ACCESSOR Only)
**GET** `/api/jira/assets`

**Headers:**
- `X-Jira-User-Email: user@example.com` (required)
- `Authorization: Bearer <token>` (if using token auth)

**Authorization Requirements:**
- User must be registered in DAM backend
- User must have ACCESSOR role
- User account must be active

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "name": "Production MySQL Database",
    "type": "DATABASE",
    "description": "Main production database"
  },
  {
    "id": 2,
    "name": "Staging PostgreSQL",
    "type": "DATABASE",
    "description": "Staging environment database"
  }
]
```

**Error Responses:**
- `403 Forbidden` - User not found, wrong role, or account inactive
- `500 Internal Server Error` - Server error

---

### 2. Save Access Configuration
**POST** `/api/jira/config`

**Headers:**
- `Content-Type: application/json`
- `X-Jira-Signature: <hmac-signature>` (optional for development)
- `Authorization: Bearer <token>` (if using token auth)

**Request Body:**
```json
{
  "issueKey": "PROJ-123",
  "issueId": "10001",
  "userEmail": "user@example.com",
  "assetId": 1,
  "tables": "users,orders,products",
  "accessLevel": "READ_ONLY",
  "durationDays": 90,
  "businessJustification": "Need access for Q2 reporting"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "damRequestId": 12345,
  "message": "Access configuration saved successfully"
}
```

**Error Responses:**
- `400 Bad Request` - Invalid input or configuration locked
- `401 Unauthorized` - Invalid signature
- `500 Internal Server Error` - Server error

---

### 3. Get Access Configuration
**GET** `/api/jira/config/{issueKey}`

**Example:** `GET /api/jira/config/PROJ-123`

**Headers:**
- `Authorization: Bearer <token>` (if using token auth)

**Response (200 OK):**
```json
{
  "damRequestId": 12345,
  "assetId": 1,
  "assetName": "Production MySQL Database",
  "status": "APPROVED",
  "expiryDate": "2026-05-06T00:00:00",
  "isLocked": true
}
```

**Error Responses:**
- `404 Not Found` - Configuration not found for issue

---

### 4. Approve Access Request (ASSET_OWNER Only)
**POST** `/api/jira/request/{accessRequestId}/approve`

**Headers:**
- `Content-Type: application/json`
- `X-Jira-User-Email: owner@example.com` (required)
- `Authorization: Bearer <token>` (if using token auth)

**Authorization Requirements:**
- User must be registered in DAM backend
- User must have ASSET_OWNER role
- User must be owner of the asset in the access request
- User account must be active

**Path Parameters:**
- `accessRequestId` - ID of the access request to approve

**Request Body:**
```json
{
  "requestId": 12345,
  "expirationHours": 2160
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Access request approved successfully",
  "accessRequestId": 12345,
  "status": "APPROVED"
}
```

**Error Responses:**
- `403 Forbidden` - User not found, wrong role, not asset owner, or account inactive
- `404 Not Found` - Access request not found
- `500 Internal Server Error` - Server error

---

### 5. Reject Access Request (ASSET_OWNER Only)
**POST** `/api/jira/request/{accessRequestId}/reject`

**Headers:**
- `Content-Type: application/json`
- `X-Jira-User-Email: owner@example.com` (required)
- `Authorization: Bearer <token>` (if using token auth)

**Authorization Requirements:**
- User must be registered in DAM backend
- User must have ASSET_OWNER role
- User must be owner of the asset in the access request
- User account must be active

**Path Parameters:**
- `accessRequestId` - ID of the access request to reject

**Request Body:**
```json
{
  "requestId": 12345,
  "rejectReason": "Access level too broad for production database"
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Access request rejected successfully",
  "accessRequestId": 12345,
  "status": "REJECTED"
}
```

**Error Responses:**
- `403 Forbidden` - User not found, wrong role, not asset owner, or account inactive
- `404 Not Found` - Access request not found
- `500 Internal Server Error` - Server error

---

### 6. Provision Access (Webhook)
**POST** `/api/jira/provision`

**Note:** This endpoint is called by Jira webhooks, not directly by frontend.

**Headers:**
- `Content-Type: application/json`
- `X-Jira-Signature: <hmac-signature>` (optional for development)

**Request Body:**
```json
{
  "issueKey": "PROJ-123",
  "issueId": "10001",
  "approverEmail": "admin@example.com",
  "timestamp": 1707234567
}
```

**Response (200 OK):**
```json
{
  "success": true,
  "damRequestId": 12345,
  "message": "Access provisioned successfully"
}
```

---

### 7. Revoke Access (Webhook)
**POST** `/api/jira/revoke`

**Note:** This endpoint is called by Jira webhooks, not directly by frontend.

**Headers:**
- `Content-Type: application/json`
- `X-Jira-Signature: <hmac-signature>` (optional for development)

**Request Body:**
```json
{
  "issueKey": "PROJ-123",
  "reason": "expired"
}
```

**Reason values:** `"expired"`, `"rejected"`, `"manual"`

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Access revoked successfully"
}
```

---

## Access Level Values

**Valid values for `accessLevel` field:**
- `"READ_ONLY"` - Read-only access
- `"READ_WRITE"` - Read and write access
- `"FULL_ACCESS"` - Full access

---

## Status Values

**Possible values for `status` field:**
- `"REQUESTED"` - Pending approval
- `"APPROVED"` - Approved and provisioned
- `"REJECTED"` - Rejected
- `"EXPIRED"` - Access expired

---

## Authentication Flow

### For ACCESSOR Users:
1. User authenticates with Jira (gets Jira email)
2. Frontend sends request with `X-Jira-User-Email` header
3. Backend validates:
   - Email exists in DAM system
   - User has ACCESSOR role
   - User account is active
4. If valid, request proceeds
5. If invalid, returns 403 Forbidden

### For ASSET_OWNER Users:
1. User authenticates with Jira (gets Jira email)
2. Frontend sends request with `X-Jira-User-Email` header
3. Backend validates:
   - Email exists in DAM system
   - User has ASSET_OWNER role
   - User is owner of the asset (for approve/reject)
   - User account is active
4. If valid, request proceeds
5. If invalid, returns 403 Forbidden

---

## Error Handling

### Common Error Responses:

**403 Forbidden:**
```json
{
  "timestamp": "2026-02-06T10:00:00",
  "status": 403,
  "error": "Forbidden",
  "message": "User not found: user@example.com. Please register in DAM system first.",
  "path": "/api/jira/assets"
}
```

**404 Not Found:**
```json
{
  "timestamp": "2026-02-06T10:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Access request not found: 12345",
  "path": "/api/jira/request/12345/approve"
}
```

**400 Bad Request:**
```json
{
  "timestamp": "2026-02-06T10:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Access configuration is locked. Cannot modify after submission.",
  "path": "/api/jira/config"
}
```

---

## Integration Checklist

- [ ] Configure base URL: `https://your-dam-backend.com/api/jira`
- [ ] Implement Jira user email extraction
- [ ] Add `X-Jira-User-Email` header to all requests
- [ ] Handle 403 errors (user not registered or wrong role)
- [ ] Handle 404 errors (resource not found)
- [ ] Handle 400 errors (validation errors)
- [ ] Implement asset selection dropdown (ACCESSOR)
- [ ] Implement approve/reject buttons (ASSET_OWNER)
- [ ] Show appropriate error messages to users
- [ ] Verify user registration before allowing actions

---

## Testing

### Test ACCESSOR Endpoint:
```bash
curl -X GET "https://your-dam-backend.com/api/jira/assets" \
  -H "X-Jira-User-Email: accessor@example.com"
```

### Test ASSET_OWNER Approve:
```bash
curl -X POST "https://your-dam-backend.com/api/jira/request/12345/approve" \
  -H "Content-Type: application/json" \
  -H "X-Jira-User-Email: owner@example.com" \
  -d '{"requestId": 12345, "expirationHours": 2160}'
```

### Test ASSET_OWNER Reject:
```bash
curl -X POST "https://your-dam-backend.com/api/jira/request/12345/reject" \
  -H "Content-Type: application/json" \
  -H "X-Jira-User-Email: owner@example.com" \
  -d '{"requestId": 12345, "rejectReason": "Access level too broad"}'
```

---

## Notes

1. **User Registration:** Users must be registered in DAM backend before using Jira integration
2. **Role Assignment:** Users must have ACCESSOR or ASSET_OWNER role assigned
3. **Asset Ownership:** ASSET_OWNER can only approve/reject requests for their own assets
4. **Email Matching:** Jira email must exactly match DAM user email
5. **Account Status:** User account must be active
