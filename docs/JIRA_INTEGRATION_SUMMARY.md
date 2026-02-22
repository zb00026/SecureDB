# Jira-DAM Integration - Implementation Summary

## ✅ Completed Backend Implementation

### 1. Database Schema Changes
- ✅ Added `jira_issue_key` and `jira_issue_id` fields to `access_requests` table
- ✅ Created Liquibase migration: `db.046-changelog-add-jira-fields.xml`
- ✅ Added database index on `jira_issue_key` for fast lookups

### 2. Backend API Endpoints

**Controller:** `JiraIntegrationController` (`/api/jira`)

#### Endpoints Created:
1. **POST `/api/jira/config`** - Save access configuration from Jira
   - Validates webhook signature (HMAC)
   - Creates or updates access request
   - Returns DAM request ID

2. **POST `/api/jira/provision`** - Provision access when approved
   - Called via webhook when Jira workflow transitions to "Approved"
   - Approves access request and creates credentials

3. **POST `/api/jira/revoke`** - Revoke access
   - Called when issue expires or is rejected
   - Marks access request as expired/rejected

4. **GET `/api/jira/config/{issueKey}`** - Get access configuration
   - Returns current configuration for a Jira issue
   - Used by Forge app to display status

5. **GET `/api/jira/assets`** - Get available assets
   - Returns list of assets for dropdown in Forge app

### 3. Service Layer

**Service:** `JiraIntegrationService`

**Features:**
- ✅ HMAC SHA256 webhook signature verification
- ✅ User lookup by email (Jira → DAM user mapping)
- ✅ Access request creation/update with configuration locking
- ✅ Access level object parsing (tables, permissions)
- ✅ Integration with existing `AccessRequestService` for approval flow

### 4. Configuration

**Added to `application.properties`:**
```properties
jira.webhook.secret=${JIRA_WEBHOOK_SECRET}
jira.api.url=${JIRA_API_URL:https://your-instance.atlassian.net}
jira.oauth.client.id=${JIRA_OAUTH_CLIENT_ID}
jira.oauth.client.secret=${JIRA_OAUTH_CLIENT_SECRET}
jira.oauth.redirect.uri=${JIRA_OAUTH_REDIRECT_URI}
```

---

## 📋 Next Steps - Jira Configuration

### Step 1: Configure Jira Custom Issue Type

1. **Go to:** Jira Admin → Issues → Issue Types
2. **Create:** "Database Access Request" issue type
3. **Add to:** Your project's issue type scheme

### Step 2: Create Custom Fields

Create these custom fields in Jira:

| Field Name | Type | Field ID (Jira assigns) |
|------------|------|-------------------------|
| Asset ID | Text (single line) | `customfield_10001` |
| Tables | Text (multi-line) | `customfield_10002` |
| Access Level | Select List | `customfield_10003` |
| Duration (Days) | Number | `customfield_10004` |
| Business Justification | Text (multi-line) | `customfield_10005` |
| DAM Request ID | Text (hidden) | `customfield_10006` |

**Access Level Options:**
- `READ_ONLY`
- `READ_WRITE`
- `FULL_ACCESS`

### Step 3: Create Workflow

**Workflow States:**
```
Draft → Configured → Pending Approval → Approved → Provisioned
                                              ↓
                                           Rejected
                                              ↓
                                           Expired
```

**Key Transitions:**

1. **Configure** (Draft → Configured)
   - Manual trigger
   - Post function: Update issue fields

2. **Submit for Approval** (Configured → Pending Approval)
   - Manual trigger
   - Condition: DAM Request ID is set
   - Post function: Lock issue fields

3. **Approve** (Pending Approval → Approved)
   - Manual trigger (Admin only)
   - Post function: **Webhook to `/api/jira/provision`**
   - Headers: `X-Jira-Signature: {{calculated_hmac}}`
   - Body:
     ```json
     {
       "issueKey": "{{issue.key}}",
       "issueId": "{{issue.id}}",
       "approverEmail": "{{user.emailAddress}}",
       "timestamp": {{now.timestamp}}
     }
     ```

4. **Reject** (Pending Approval → Rejected)
   - Manual trigger (Admin only)
   - Post function: **Webhook to `/api/jira/revoke`**
   - Body:
     ```json
     {
       "issueKey": "{{issue.key}}",
       "reason": "rejected"
     }
     ```

5. **Provision** (Approved → Provisioned)
   - Automatic (via webhook callback from DAM)
   - Post function: Update issue with status

6. **Expire** (Any → Expired)
   - Automation rule (due date reached)
   - Post function: **Webhook to `/api/jira/revoke`**
   - Body:
     ```json
     {
       "issueKey": "{{issue.key}}",
       "reason": "expired"
     }
     ```

### Step 4: Configure Automation Rules

**Rule:** "Expire Access Requests"

**Trigger:** Scheduled (daily at midnight) OR Issue due date reached

**Condition:**
- Issue type = Database Access Request
- Status = Provisioned

**Action:**
- Send webhook to: `https://your-dam-backend.com/api/jira/revoke`
- Method: POST
- Headers:
  ```
  Content-Type: application/json
  X-Jira-Signature: {{calculated_hmac_signature}}
  ```
- Body:
  ```json
  {
    "issueKey": "{{issue.key}}",
    "reason": "expired"
  }
  ```

---

## 🔐 Security Setup

### 1. Generate Webhook Secret

```bash
# Generate a secure random secret
openssl rand -hex 32
```

Set this as `JIRA_WEBHOOK_SECRET` environment variable.

### 2. Configure HMAC in Jira Webhooks

For each webhook, calculate HMAC SHA256 signature:

**JavaScript Example (for Jira Automation):**
```javascript
const crypto = require('crypto');
const secret = 'YOUR_WEBHOOK_SECRET';
const payload = JSON.stringify(requestBody);
const signature = crypto.createHmac('sha256', secret)
    .update(payload)
    .digest('hex');
```

**Python Example:**
```python
import hmac
import hashlib
import json

secret = 'YOUR_WEBHOOK_SECRET'
payload = json.dumps(request_body)
signature = hmac.new(
    secret.encode('utf-8'),
    payload.encode('utf-8'),
    hashlib.sha256
).hexdigest()
```

---

## 🚀 Forge App Development (Next Phase)

### Step 1: Initialize Forge App

```bash
npm install -g @forge/cli
forge login
forge create
# Select: Custom UI (React)
# Name: dam-integration
```

### Step 2: Create App Panel

See `docs/JIRA_INTEGRATION_IMPLEMENTATION.md` for complete Forge app code.

**Key Components:**
- Issue panel module
- Form for configuring access (asset, tables, permissions, duration)
- API calls to DAM backend
- OAuth token handling

### Step 3: Deploy Forge App

```bash
cd dam-jira-app
forge deploy
forge install
```

---

## 🧪 Testing

### Test Backend Endpoints

```bash
# 1. Test config endpoint
curl -X POST https://your-dam-backend.com/api/jira/config \
  -H "Content-Type: application/json" \
  -H "X-Jira-Signature: test-signature" \
  -d '{
    "issueKey": "PROJ-123",
    "issueId": "10001",
    "userEmail": "user@example.com",
    "assetId": 1,
    "tables": "users,orders",
    "accessLevel": "READ_ONLY",
    "durationDays": 90,
    "businessJustification": "Testing integration"
  }'

# 2. Test provision endpoint
curl -X POST https://your-dam-backend.com/api/jira/provision \
  -H "Content-Type: application/json" \
  -H "X-Jira-Signature: test-signature" \
  -d '{
    "issueKey": "PROJ-123",
    "issueId": "10001",
    "approverEmail": "admin@example.com",
    "timestamp": 1234567890
  }'

# 3. Test get config
curl https://your-dam-backend.com/api/jira/config/PROJ-123

# 4. Test get assets
curl https://your-dam-backend.com/api/jira/assets
```

---

## 📝 Important Notes

1. **User Mapping:** The integration uses email addresses to map Jira users to DAM users. Ensure:
   - Jira user emails match DAM user emails
   - DAM users exist before creating Jira requests

2. **Configuration Locking:** Once an access request is submitted for approval (status != REQUESTED), the configuration cannot be modified. This prevents silent changes.

3. **Access Level Mapping:** The service maps Jira access levels to DAM AccessLevel templates:
   - `READ_ONLY` → `SELECT`
   - `READ_WRITE` → `INSERT, UPDATE, DELETE`
   - `FULL_ACCESS` → `FULL ACCESS`

4. **Webhook Security:** Always verify webhook signatures in production. The service skips verification if `jira.webhook.secret` is not configured (for development).

5. **Error Handling:** All endpoints return appropriate HTTP status codes:
   - `200 OK` - Success
   - `400 Bad Request` - Invalid input
   - `401 Unauthorized` - Invalid signature
   - `404 Not Found` - Resource not found
   - `500 Internal Server Error` - Server error

---

## 🔄 Integration Flow

```
1. User creates Jira issue (Database Access Request)
   ↓
2. User clicks "Configure Access" in app panel
   ↓
3. Forge app calls GET /api/jira/assets (populate dropdown)
   ↓
4. User selects asset, tables, permissions, duration
   ↓
5. Forge app calls POST /api/jira/config (save configuration)
   ↓
6. DAM creates/updates AccessRequest with jira_issue_key
   ↓
7. User submits issue for approval
   ↓
8. Admin approves in Jira
   ↓
9. Jira webhook → POST /api/jira/provision
   ↓
10. DAM approves AccessRequest and creates credentials
    ↓
11. Jira issue transitions to "Provisioned"
    ↓
12. User logs into DAM and queries database
```

---

## 📚 Documentation Files

- **Full Implementation Guide:** `docs/JIRA_INTEGRATION_IMPLEMENTATION.md`
- **This Summary:** `docs/JIRA_INTEGRATION_SUMMARY.md`

---

## ✅ Checklist

- [x] Backend API endpoints created
- [x] Database schema updated
- [x] Webhook security (HMAC) implemented
- [x] Service layer with business logic
- [ ] Jira custom issue type configured
- [ ] Jira custom fields created
- [ ] Jira workflow configured
- [ ] Jira automation rules configured
- [ ] Forge app developed
- [ ] OAuth 2.0 configured
- [ ] End-to-end testing completed
- [ ] Production deployment

---

## 🆘 Troubleshooting

### Issue: "User not found" error
**Solution:** Ensure Jira user email matches DAM user email. Create DAM user if needed.

### Issue: "Access configuration is locked"
**Solution:** Configuration can only be modified when status is REQUESTED. Create a new issue if changes are needed.

### Issue: "Invalid webhook signature"
**Solution:** 
1. Verify `JIRA_WEBHOOK_SECRET` is set correctly
2. Ensure HMAC calculation uses the same secret
3. Verify payload serialization matches (JSON format)

### Issue: "No AccessLevel found"
**Solution:** Ensure access levels are seeded in database for the asset's database type. Check `db.009-changelog-seed-db-access-level.xml`.
