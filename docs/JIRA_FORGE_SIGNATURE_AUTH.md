# Jira Forge Signature Authentication

The DAM backend uses HMAC-SHA256 signature verification for requests from the Forge app instead of OAuth tokens. No modal dialog is required.

## Configuration

**Backend:** Set `JIRA_FORGE_SECRET` (same value as `BACKEND_SECRET` in Forge app variables)

**Forge app:** Store the shared secret in Forge variables as `BACKEND_SECRET`

## Signature Format

```
signature = HMAC-SHA256(secret, accountId|timestamp|requestBody)
```

- `accountId` – Atlassian account ID (e.g. from `context.user.accountId`)
- `timestamp` – Unix timestamp in milliseconds (e.g. `Date.now().toString()`)
- `requestBody` – Raw request body (omitted for GET, or JSON string for POST)
- Use pipe `|` as separator between parts

## Required Headers

| Header        | Description                          |
|---------------|--------------------------------------|
| `X-User-Id`   | Atlassian account ID                 |
| `X-Timestamp` | Request timestamp (ms)                |
| `X-Signature` | HMAC-SHA256 hex signature            |
| `X-User-Email`| User email (required – must be sent from frontend) |

## Forge App Example (Node.js resolver)

```javascript
const crypto = require('crypto');

const secret = (process.env.BACKEND_SECRET || '').trim();  // Trim env var newlines
const accountId = context.user.accountId;
const timestamp = Date.now().toString();

// For GET /api/jira/assets (body = userEmail or empty)
const payload = userEmail ? `${accountId}|${timestamp}|${userEmail}` : `${accountId}|${timestamp}`;
const signature = crypto.createHmac('sha256', secret)
  .update(payload, 'utf8')
  .digest('hex');

const response = await fetch(`${backendUrl}/api/jira/assets`, {
  method: 'GET',
  headers: {
    'X-User-Id': accountId,
    'X-Timestamp': timestamp,
    'X-User-Email': userEmail,
    'X-Signature': signature,
    'Content-Type': 'application/json',
  },
});
```

## Forge App Example (POST with body)

```javascript
const body = JSON.stringify({ issueKey, assetId, tables, ... });
const payload = `${accountId}|${timestamp}|${body}`;
const signature = crypto.createHmac('sha256', secret)
  .update(payload)
  .digest('hex');

const response = await fetch(`${backendUrl}/api/jira/config`, {
  method: 'POST',
  headers: {
    'X-User-Id': accountId,
    'X-Timestamp': timestamp,
    'X-Signature': signature,
    'Content-Type': 'application/json',
  },
  body: body,
});
```

## Endpoint Signature Rules

| Endpoint                          | Request Body / Signed Data      |
|-----------------------------------|----------------------------------|
| `POST /api/jira/config`           | Full JSON request body          |
| `GET /api/jira/config/{issueKey}` | `issueKey` (path param)         |
| `GET /api/jira/assets`           | `userEmail` (from header)        |
| `POST /api/jira/request/{id}/approve` | Full JSON request body    |
| `POST /api/jira/request/{id}/reject`  | Full JSON request body    |

## Getting User Email in Forge

The Forge resolver has access to the current user. Use the Jira/Forge API to fetch the user's email, or include it from the issue context. Example:

```javascript
import Resolver from '@forge/resolver';
import { user } from '@forge/api';

const resolver = new Resolver();

resolver.define('getUserEmail', async (req, context) => {
  const userDetails = await user.getCurrentUser();
  // userDetails.email or fetch from Jira API
  return userDetails;
});
```

## Webhook Endpoints (unchanged)

These still use `X-Jira-Signature` (HMAC of payload only):

- `POST /api/jira/provision`
- `POST /api/jira/revoke`

## React / Browser (Web Crypto API)

If using React in the browser (no Node.js crypto), use the Web Crypto API:

```javascript
async function createSignature(secret, accountId, timestamp, body) {
  const payload = body
    ? `${accountId}|${timestamp}|${body}`
    : `${accountId}|${timestamp}`;
  const encoder = new TextEncoder();
  const keyData = encoder.encode(secret.trim());

  const key = await crypto.subtle.importKey(
    'raw',
    keyData,
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );

  const signature = await crypto.subtle.sign(
    'HMAC',
    key,
    encoder.encode(payload)
  );
  return Array.from(new Uint8Array(signature))
    .map(b => b.toString(16).padStart(2, '0'))
    .join('');
}
```

**Important:** Ensure `secret` and `payload` use the same string values. Trim the secret if it comes from env/config. Use `JSON.stringify()` with consistent key order for request bodies.

## Debugging Signature Mismatch

If payloads match but signatures differ, the **secret key** is almost certainly different:

1. **Verify same secret** – Backend uses `JIRA_FORGE_SECRET`, frontend uses `BACKEND_SECRET`. They must be identical.
2. **Log secret length** (not the value): `console.log('Secret length:', secret.length)` vs backend log. If lengths differ, the secret is different.
3. **Check encoding** – Both must use the raw UTF-8 string. If one stores it as base64, the other must decode it.
4. **Forge variables** – Ensure `getBackendSecret()` returns the plain string, not an object or wrapped value.
5. **Node.js crypto** – Use `secret` as a string: `crypto.createHmac('sha256', secret).update(payload, 'utf8').digest('hex')`. Do not pass a Buffer unless the backend also uses the same bytes.

## OAuth Callback (optional)

`GET /api/jira/oauth/callback` remains for OAuth 2.0 flow if needed for non-Forge clients (e.g. DAM frontend).
