# Freshdesk Secret Key Integration Guide

## Overview

All Freshdesk API endpoints now require a **secret key** to be sent in the request headers. This secret key is configured in the Freshdesk app settings when installing the app, and must be included in every API request.

---

## Secret Key Configuration

### Backend Setup

1. **Get the secret key from backend team:**
   - The secret key is stored in the backend `.env` file as `FRESHDESK_APP_SECRET_KEY`
   - Contact the backend team to obtain this value

2. **Configure in Freshdesk App Settings:**
   - When installing/configuring the Freshdesk app, add the secret key in the app configuration page
   - Store it in a secure location (similar to how you store the domain/API URL)

---

## API Request Format

### Required Header

All API requests must include the secret key in the `X-Freshdesk-App-Secret-Key` header:

```
X-Freshdesk-App-Secret-Key: <your-secret-key-here>
```

### Example Request

```javascript
// Example: Get assets
fetch('https://your-backend.com/api/freshdesk/assets', {
  method: 'GET',
  headers: {
    'Authorization': 'Bearer <user-token>',
    'X-Freshdesk-App-Secret-Key': '<secret-key-from-app-settings>',
    'Content-Type': 'application/json'
  }
})
```

---

## Updated API Endpoints

All endpoints now require the `X-Freshdesk-App-Secret-Key` header:

### 1. Authenticate User
**POST** `/api/freshdesk/auth`

```javascript
fetch('/api/freshdesk/auth', {
  method: 'POST',
  headers: {
    'X-Freshdesk-App-Secret-Key': '<secret-key>',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    email: 'user@example.com',
    freshdeskToken: 'optional-freshdesk-token'
  })
})
```

### 2. Get Assets
**GET** `/api/freshdesk/assets`

```javascript
fetch('/api/freshdesk/assets', {
  method: 'GET',
  headers: {
    'Authorization': 'Bearer <user-token>',
    'X-Freshdesk-App-Secret-Key': '<secret-key>'
  }
})
```

### 3. Get Access Requests
**GET** `/api/freshdesk/access-requests`

```javascript
fetch('/api/freshdesk/access-requests?assetId=123', {
  method: 'GET',
  headers: {
    'Authorization': 'Bearer <user-token>',
    'X-Freshdesk-App-Secret-Key': '<secret-key>'
  }
})
```

### 4. Get Schema
**GET** `/api/freshdesk/schema`

```javascript
fetch('/api/freshdesk/schema?requestId=456', {
  method: 'GET',
  headers: {
    'Authorization': 'Bearer <user-token>',
    'X-Freshdesk-App-Secret-Key': '<secret-key>'
  }
})
```

### 5. Execute Query
**POST** `/api/freshdesk/run-query`

```javascript
fetch('/api/freshdesk/run-query', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer <user-token>',
    'X-Freshdesk-App-Secret-Key': '<secret-key>',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    assetId: 1,
    requestId: 456,
    query: 'SELECT * FROM users LIMIT 10'
  })
})
```

### 6. Convert Natural Language to SQL
**POST** `/api/freshdesk/convert-nl-to-sql`

```javascript
fetch('/api/freshdesk/convert-nl-to-sql', {
  method: 'POST',
  headers: {
    'Authorization': 'Bearer <user-token>',
    'X-Freshdesk-App-Secret-Key': '<secret-key>',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    naturalLanguageQuery: 'Show me all users from New York',
    assetId: 1,
    requestId: 456
  })
})
```

---

## Implementation Example

### Option 1: Store Secret Key in App Configuration

```javascript
// In your Freshdesk app configuration
const APP_CONFIG = {
  backendUrl: 'https://your-backend.com',
  secretKey: '<secret-key-from-app-settings>' // Set during app installation
};

// Create a helper function for API calls
async function callFreshdeskAPI(endpoint, options = {}) {
  const url = `${APP_CONFIG.backendUrl}${endpoint}`;
  const headers = {
    'X-Freshdesk-App-Secret-Key': APP_CONFIG.secretKey,
    'Content-Type': 'application/json',
    ...options.headers
  };

  const response = await fetch(url, {
    ...options,
    headers
  });

  if (!response.ok) {
    throw new Error(`API call failed: ${response.status} ${response.statusText}`);
  }

  return response.json();
}

// Usage
const assets = await callFreshdeskAPI('/api/freshdesk/assets', {
  headers: {
    'Authorization': `Bearer ${userToken}`
  }
});
```

### Option 2: Use Axios Interceptor

```javascript
import axios from 'axios';

const apiClient = axios.create({
  baseURL: 'https://your-backend.com'
});

// Add secret key to all requests
apiClient.interceptors.request.use(config => {
  config.headers['X-Freshdesk-App-Secret-Key'] = '<secret-key-from-app-settings>';
  return config;
});

// Usage
const assets = await apiClient.get('/api/freshdesk/assets', {
  headers: {
    'Authorization': `Bearer ${userToken}`
  }
});
```

---

## Error Handling

### Missing Secret Key

If the secret key is missing or invalid, you'll receive:

**Status:** `401 Unauthorized`

**Response:**
```json
{
  "timestamp": "2026-02-06T10:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Missing Freshdesk app secret key in request header",
  "path": "/api/freshdesk/assets"
}
```

### Invalid Secret Key

**Status:** `401 Unauthorized`

**Response:**
```json
{
  "timestamp": "2026-02-06T10:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "message": "Invalid Freshdesk app secret key",
  "path": "/api/freshdesk/assets"
}
```

---

## Security Best Practices

1. **Never expose the secret key in client-side code** if possible
   - If using Freshdesk app configuration, store it securely in app settings
   - Don't hardcode it in JavaScript files that are publicly accessible

2. **Use HTTPS** for all API calls to protect the secret key in transit

3. **Store secret key securely** in Freshdesk app configuration (similar to domain/API URL)

4. **Rotate the secret key** periodically by updating both backend `.env` and Freshdesk app settings

---

## Testing

### Test the Secret Key

```javascript
// Test endpoint (health check doesn't require secret key)
fetch('/api/freshdesk/health')
  .then(res => res.json())
  .then(data => console.log('Backend is reachable:', data));

// Test with secret key
fetch('/api/freshdesk/auth', {
  method: 'POST',
  headers: {
    'X-Freshdesk-App-Secret-Key': '<your-secret-key>',
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    email: 'test@example.com'
  })
})
  .then(res => {
    if (res.status === 401) {
      console.error('Secret key is invalid or missing');
    } else {
      console.log('Secret key is valid');
    }
    return res.json();
  });
```

---

## Migration Checklist

- [ ] Obtain secret key from backend team (`FRESHDESK_APP_SECRET_KEY` from `.env`)
- [ ] Configure secret key in Freshdesk app settings (during installation)
- [ ] Update all API calls to include `X-Freshdesk-App-Secret-Key` header
- [ ] Test authentication endpoint with new header
- [ ] Test all other endpoints with new header
- [ ] Update error handling for 401 responses
- [ ] Document secret key storage location for your team

---

## Support

If you encounter issues:

1. **Verify secret key** matches the backend `.env` file value
2. **Check header name** is exactly `X-Freshdesk-App-Secret-Key` (case-sensitive)
3. **Verify HTTPS** is being used for API calls
4. **Check browser console** for CORS or network errors
5. **Contact backend team** if secret key needs to be reset

---

**Last Updated:** 2026-02-06  
**Version:** 1.0
