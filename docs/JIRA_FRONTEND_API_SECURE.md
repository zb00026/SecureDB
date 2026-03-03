# Jira Integration - Secure Frontend API Reference

## Base URL
```
https://your-dam-backend.com/api/jira
```

## Security Model

**Authentication:** OAuth 2.0 (preferred) or Bearer Token  
**Identity Mapping:** Jira Account ID → Corporate Email → DAM User ID  
**Webhook Security:** HMAC SHA256 signature verification  
**Never trust free text fields** - Always use Jira Account ID

---

## Authentication Flow

### Step 1: OAuth Authorization
Redirect user to Jira authorization URL:
```
https://your-instance.atlassian.net/plugins/servlet/oauth/authorize?
  client_id=YOUR_CLIENT_ID&
  redirect_uri=https://your-dam-backend.com/api/jira/oauth/callback&
  response_type=code&
  state=random_state_string
```

### Step 2: OAuth Callback
**GET** `/api/jira/oauth/callback`

**Query Parameters:**
- `code` - Authorization code from Jira
- `state` - State parameter (for CSRF protection)
- `error` - Error code (if authorization failed)

**Response (200 OK):**
```json
{
  "success": true,
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "userId": 123,
  "email": "user@example.com",
  "message": "Authentication successful"
}
```

**Use `accessToken` for subsequent API calls**

---

## API Endpoints

### 1. Get Available Assets (ACCESSOR Only)
**GET** `/api/jira/assets`

**Headers:**
- `Authorization: Bearer <oauth_access_token>` (required)

**Authentication:**
- OAuth token is validated
- Jira Account ID extracted from token
- Account ID → Email → DAM User ID mapping verified
- User must have ACCESSOR role

**Response (200 OK):**
```json
[
  {
    "id": 1,
    "name": "Production MySQL Database",
    "type": "DATABASE",
    "description": "Main production database"
  }
]
```

**Error Responses:**
- `401 Unauthorized` - Invalid or expired token
- `403 Forbidden` - User not found, wrong role, or account inactive
- `500 Internal Server Error` - Server error

---

### 2. Save Access Configuration
**POST** `/api/jira/config`

**Headers:**
- `Content-Type: application/json`
- `Authorization: Bearer <oauth_access_token>` (required)
- `X-Jira-Signature: <hmac-signature>` (optional for development)

**Request Body:**
```json
{
  "issueKey": "PROJ-123",
  "issueId": "10001",
  "jiraAccountId": "557058:abc123def456",
  "assetId": 1,
  "tables": "users,orders,products",
  "accessLevel": "READ_ONLY",
  "durationDays": 90,
  "businessJustification": "Need access for Q2 reporting"
}
```

**Security:**
- `jiraAccountId` is extracted from OAuth token (not from request body)
- Never trust `jiraAccountId` from request body - it's validated from token

**Response (200 OK):**
```json
{
  "success": true,
  "damRequestId": 12345,
  "message": "Access configuration saved successfully"
}
```

---

### 3. Get Access Configuration
**GET** `/api/jira/config/{issueKey}`

**Headers:**
- `Authorization: Bearer <oauth_access_token>` (optional)

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

---

### 4. Approve Access Request (ASSET_OWNER Only)
**POST** `/api/jira/request/{accessRequestId}/approve`

**Headers:**
- `Content-Type: application/json`
- `Authorization: Bearer <oauth_access_token>` (required)

**Authentication:**
- OAuth token validated
- Jira Account ID → Email → DAM User ID mapping verified
- User must have ASSET_OWNER role
- User must be owner of the asset

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

---

### 5. Reject Access Request (ASSET_OWNER Only)
**POST** `/api/jira/request/{accessRequestId}/reject`

**Headers:**
- `Content-Type: application/json`
- `Authorization: Bearer <oauth_access_token>` (required)

**Authentication:** Same as approve endpoint

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

---

### 6. Provision Access (Webhook)
**POST** `/api/jira/provision`

**Note:** Called by Jira webhooks, not frontend

**Headers:**
- `Content-Type: application/json`
- `X-Jira-Signature: <hmac-signature>` (required in production)

**Request Body:**
```json
{
  "issueKey": "PROJ-123",
  "issueId": "10001",
  "approverAccountId": "557058:xyz789",
  "timestamp": 1707234567
}
```

**Security:**
- HMAC signature verified
- `approverAccountId` validated (not trusted from body)

---

### 7. Revoke Access (Webhook)
**POST** `/api/jira/revoke`

**Note:** Called by Jira webhooks, not frontend

**Headers:**
- `Content-Type: application/json`
- `X-Jira-Signature: <hmac-signature>` (required in production)

**Request Body:**
```json
{
  "issueKey": "PROJ-123",
  "reason": "expired"
}
```

---

## Identity Mapping

### How It Works:

1. **User authenticates with Jira OAuth**
   - Gets OAuth access token
   - Token contains Jira Account ID

2. **Backend extracts Account ID from token**
   - Calls Jira API: `GET /rest/api/3/myself`
   - Gets Account ID and Email

3. **Backend maps Account ID → Email → DAM User ID**
   - Checks `jira_user_mappings` table
   - If mapping exists: Uses existing DAM User
   - If mapping doesn't exist: Creates mapping using email

4. **Backend validates DAM User**
   - Checks user exists
   - Checks user has required role
   - Checks user account is active

### Security Benefits:

- ✅ **Never trusts free text fields** - Account ID comes from OAuth token
- ✅ **Email verified** - Retrieved from Jira API, not user input
- ✅ **Mapping stored** - Account ID → DAM User ID mapping cached
- ✅ **Token validation** - OAuth token verified with Jira

---

## Frontend Integration Steps

### 1. OAuth Flow Implementation

```javascript
// Step 1: Redirect to Jira authorization
const authUrl = `https://your-instance.atlassian.net/plugins/servlet/oauth/authorize?` +
  `client_id=${CLIENT_ID}&` +
  `redirect_uri=${encodeURIComponent(CALLBACK_URL)}&` +
  `response_type=code&` +
  `state=${generateRandomState()}`;

window.location.href = authUrl;

// Step 2: Handle callback
// After redirect, extract 'code' from URL
const code = new URLSearchParams(window.location.search).get('code');

// Step 3: Exchange code for token
const response = await fetch('/api/jira/oauth/callback?code=' + code);
const { accessToken } = await response.json();

// Step 4: Store token securely
localStorage.setItem('jira_access_token', accessToken);
```

### 2. API Calls with OAuth Token

```javascript
// All API calls include Bearer token
const accessToken = localStorage.getItem('jira_access_token');

const response = await fetch('/api/jira/assets', {
  headers: {
    'Authorization': `Bearer ${accessToken}`
  }
});
```

### 3. Token Refresh

```javascript
// Check if token is expired
if (isTokenExpired(accessToken)) {
  // Re-authenticate user
  redirectToJiraAuth();
}
```

---

## Security Best Practices

1. **Never store OAuth tokens in localStorage** (use secure httpOnly cookies if possible)
2. **Validate token before each API call**
3. **Handle token expiration gracefully**
4. **Never send Account ID in request body** - Always extract from token
5. **Verify HMAC signatures for webhooks** (backend handles this)

---

## Error Handling

### 401 Unauthorized:
```json
{
  "timestamp": "2026-02-06T10:00:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid or expired OAuth token",
  "path": "/api/jira/assets"
}
```

**Action:** Re-authenticate user

### 403 Forbidden:
```json
{
  "timestamp": "2026-02-06T10:00:00",
  "status": 403,
  "error": "Forbidden",
  "message": "User not found for Jira Account ID: 557058:abc123",
  "path": "/api/jira/assets"
}
```

**Action:** User needs to register in DAM system first

---

## Testing

### Test OAuth Flow:
```bash
# 1. Get authorization URL
curl "https://your-instance.atlassian.net/plugins/servlet/oauth/authorize?client_id=CLIENT_ID&redirect_uri=CALLBACK_URL&response_type=code"

# 2. After redirect, exchange code for token
curl "https://your-dam-backend.com/api/jira/oauth/callback?code=AUTH_CODE"

# 3. Use token for API calls
curl -X GET "https://your-dam-backend.com/api/jira/assets" \
  -H "Authorization: Bearer ACCESS_TOKEN"
```

---

## Migration from Email-Based Auth

**Old (Insecure):**
```javascript
// ❌ DON'T USE - Trusts free text email
fetch('/api/jira/assets', {
  headers: {
    'X-Jira-User-Email': 'user@example.com'  // ❌ Can be spoofed
  }
});
```

**New (Secure):**
```javascript
// ✅ USE - OAuth token with Account ID mapping
fetch('/api/jira/assets', {
  headers: {
    'Authorization': 'Bearer ' + oauthToken  // ✅ Validated by backend
  }
});
```

---

## Summary

**Security Features Implemented:**
- ✅ OAuth 2.0 authentication
- ✅ Jira Account ID → Email → DAM User ID mapping
- ✅ HMAC signature verification for webhooks
- ✅ Never trusts free text fields
- ✅ Token-based authentication for all endpoints

**Key Changes:**
- Removed `X-Jira-User-Email` header (insecure)
- Added OAuth 2.0 flow
- Added Jira Account ID mapping
- All endpoints now use Bearer token authentication
