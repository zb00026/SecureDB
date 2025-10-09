# Keycloak External Identity Provider SSO Setup

## 🔐 **Overview**

This guide shows how to configure Keycloak with external identity providers (Google, Microsoft, etc.) for seamless SSO authentication. Users can log in with their existing Google/Microsoft accounts without creating new passwords.

**Keycloak + External Providers = Enhanced SSO** (Google, Microsoft, etc.)

---

## 🚀 **How External Identity Provider SSO Works**

### **Authentication Flow:**
```
User → Frontend → Google/Microsoft Login → Keycloak → JWT Token → Backend
     ↑              ↑                        ↑
   Frontend    External Provider         SSO Provider
   (frontend-client) (Google/Microsoft)   (Keycloak)
```

### **User Experience:**
```
1. User clicks "Sign in with Google"
2. Redirected to Google login
3. User authenticates with Google
4. Google redirects back to Keycloak
5. Keycloak creates/links user account
6. Keycloak issues JWT token
7. User is logged into DAM system
```

---

## ⚙️ **External Identity Provider Configuration**

### **Step 1: Configure Google Identity Provider**

#### **1.1: Create Google OAuth Application**
```
1. Go to: https://console.cloud.google.com/
2. Create Project: "DAM SSO Integration"
3. Enable: Google+ API
4. Go to: APIs & Services → Credentials
5. Create: OAuth client ID
6. Type: Web application
7. Name: "DAM Keycloak SSO"
```

#### **1.2: Configure Authorized URLs**
```
Authorized JavaScript origins:
- http://localhost:8081

Authorized redirect URIs:
- http://localhost:8081/realms/DAM/broker/google/endpoint
```

#### **1.3: Add Identity Provider in Keycloak**
```
1. Go to: Keycloak Admin Console (http://localhost:8081)
2. Login: admin/admin
3. Select: DAM realm
4. Go to: Identity Providers
5. Add provider → Google
6. Configure:
   - Alias: google
   - Display Name: Sign in with Google
   - Client ID: [Your Google OAuth Client ID]
   - Client Secret: [Your Google OAuth Client Secret]
7. Save
```

### **Step 2: Configure Identity Provider Mappers**

#### **2.1: Username Mapper**
```
Name: Google Username
Sync mode override: Force
Mapper type: Attribute Importer
Social Profile JSON Field Path: email
User Attribute Name: username
```

#### **2.2: Email Mapper**
```
Name: Google Email
Sync mode override: Force
Mapper type: Attribute Importer
Social Profile JSON Field Path: email
User Attribute Name: email
```

#### **2.3: First Name Mapper**
```
Name: Google First Name
Sync mode override: Force
Mapper type: Attribute Importer
Social Profile JSON Field Path: given_name
User Attribute Name: firstName
```

#### **2.4: Last Name Mapper**
```
Name: Google Last Name
Sync mode override: Force
Mapper type: Attribute Importer
Social Profile JSON Field Path: family_name
User Attribute Name: lastName
```

### **Step 3: Configure DAM System**

Your DAM system is already configured for Keycloak SSO:

```properties
# application.properties
auth.provider=keycloak_sso
keycloak.auth-server-url=http://localhost:8081
keycloak.realm=DAM
spring.security.oauth2.client.registration.keycloak.client-id=backend-client
spring.security.oauth2.client.registration.keycloak.client-secret=${KEYCLOAK_CLIENT_SECRET}
spring.security.oauth2.client.provider.keycloak.issuer-uri=http://localhost:8081/realms/DAM
```

---

## 🔧 **External Identity Provider SSO Features**

### **Enhanced SSO Capabilities:**

1. **External Provider Integration**
   - Google OAuth 2.0 integration
   - Microsoft Azure AD integration
   - Automatic user account linking
   - Seamless authentication flow

2. **User Experience**
   - No password creation required
   - Use existing Google/Microsoft accounts
   - Automatic profile information import
   - One-click login experience

3. **User Management**
   - Automatic user creation on first login
   - Profile information mapping from external providers
   - User account linking and unlinking
   - Role-based access control

4. **Security Features**
   - OAuth 2.0 / OpenID Connect security
   - JWT token validation
   - Session management
   - MFA support (if enabled in external provider)

---

## 🚀 **Quick Setup Guide**

### **Prerequisites:**

```
1. ✅ Keycloak running on localhost:8081
2. ✅ DAM realm configured
3. ✅ frontend-client and backend-client configured
4. ✅ DAM system configured for Keycloak
5. ✅ Google OAuth application created
6. ✅ Google identity provider added to Keycloak
```

### **How Users Experience External SSO:**

```
1. User visits Frontend (localhost:5173)
2. Frontend redirects to Keycloak login
3. User sees "Sign in with Google" button
4. User clicks Google button → Redirected to Google
5. User authenticates with Google credentials
6. Google redirects back to Keycloak
7. Keycloak creates/links user account automatically
8. Keycloak issues JWT token to Frontend
9. Frontend sends JWT token to Backend
10. User is logged into DAM system
```

---

## 🔄 **External SSO Flow Example**

### **First Login with Google:**
```
1. User → Frontend (localhost:5173)
2. Frontend → "Please log in"
3. Frontend → Redirect to Keycloak
4. Keycloak → Shows "Sign in with Google" button
5. User → Clicks Google button
6. User → Redirected to Google login
7. User → Authenticates with Google
8. Google → Redirects back to Keycloak
9. Keycloak → Creates/links user account
10. Keycloak → Issues JWT token to Frontend
11. Frontend → Sends JWT token to Backend
12. Backend → Validates JWT token with Keycloak
13. User → Logged into DAM system
```

### **Subsequent Logins (SSO):**
```
1. User → Frontend (localhost:5173)
2. Frontend → Check Keycloak session
3. Keycloak → "User already logged in"
4. Frontend → User logged in automatically (SSO!)
5. Frontend → Sends JWT token to Backend
6. User → Access granted
```

### **Multiple Applications:**
```
1. User → App A (uses same Keycloak)
2. User → App B (uses same Keycloak)
3. App B → "User already logged in" (SSO!)
4. No re-authentication needed!
```

---

## 🎯 **Benefits of External Identity Provider SSO**

### **✅ Advantages:**
- **User convenience** - Use existing Google/Microsoft accounts
- **No password management** - Users don't need to create new passwords
- **Automatic profile import** - User information imported from external providers
- **Reduced friction** - One-click login experience
- **Trusted authentication** - Leverage Google/Microsoft security
- **Faster onboarding** - No account creation process
- **Social login** - Modern authentication experience

### **✅ Perfect for:**
- Public-facing applications
- User convenience priority
- Social login desired
- External user base
- Quick user onboarding
- Modern authentication experience

---

## 🔐 **Security with External Identity Provider SSO**

### **Enhanced Security Features:**

1. **OAuth 2.0 / OpenID Connect**
   ```
   Google/Microsoft → OAuth 2.0 security
   Keycloak → JWT token validation
   DAM System → Secure token verification
   ```

2. **External Provider Security**
   ```
   Google → 2FA, security alerts, account recovery
   Microsoft → Azure AD security, conditional access
   Keycloak → Additional security layer
   ```

3. **JWT Token Security**
   ```
   Keycloak → Issues signed JWT tokens
   Backend → Validates token signatures
   Frontend → Secure token storage
   ```

4. **Session Management**
   ```
   Keycloak Admin → Realm Settings → Sessions
   Configure: Timeout, remember me, SLO
   External providers → Their own session management
   ```

---

## 🧪 **Testing External Identity Provider SSO**

### **Test 1: Google SSO Login**
```
1. Go to: Your frontend application (localhost:5173)
2. Click: "Sign in with Google" button
3. Authenticate: With your Google account
4. Check: Redirected back to your application
5. Verify: You're logged in with Google account info
```

### **Test 2: Profile Information Import**
```
1. Login: With Google account
2. Check: Username matches Google email
3. Check: First name imported from Google
4. Check: Last name imported from Google (or "_" if empty)
5. Check: Email matches Google email
```

### **Test 3: SSO Across Applications**
```
1. Login to: Application A (using same Keycloak)
2. Open: Application B (using same Keycloak)
3. Check: No login required (SSO working!)
4. Verify: Same user account across applications
```

### **Test 4: Session Management**
```
1. Login: With Google account
2. Wait: For session timeout
3. Try: Accessing application
4. Check: Redirected to login (session expired)
```

---

## ⚙️ **Advanced External Identity Provider Configuration**

### **Custom Login Themes:**
```
Keycloak Admin → Realm Settings → Themes
Login Theme: Custom theme (shows Google/Microsoft buttons)
Account Theme: Custom theme
```

### **Identity Provider Mappers:**
```
Keycloak Admin → Identity Providers → Google → Mappers
Configure: Custom attribute mapping
Add: Script mappers for complex logic
```

### **Custom Authentication Flows:**
```
Keycloak Admin → Authentication → Flows
Create: Custom First Broker Login flow
Modify: Update Profile requirement
Add: Conditional authenticators
```

### **User Account Linking:**
```
Keycloak Admin → Identity Providers → Google
Configure: Account linking policies
Set: Trust email verification
Enable: Store tokens
```

### **API Access:**
```
Keycloak Admin → Clients → backend-client
Configure: Service account roles
Add: view-identity-providers role
Use: For API authentication
```

---

## 🔄 **External Provider vs Standalone SSO**

### **External Identity Provider SSO (This Guide):**
```
✅ Users use existing Google/Microsoft accounts
✅ Social login convenience
✅ No account creation needed
✅ Automatic profile import
✅ Modern authentication experience
✅ Reduced user friction
❌ External dependencies
❌ API rate limits
❌ Privacy concerns
❌ More complex setup
```

### **Standalone Keycloak SSO:**
```
✅ No external dependencies
✅ Complete control
✅ No API limits
✅ Better privacy
✅ Easier compliance
✅ Faster setup
❌ Users need new accounts
❌ No social login convenience
❌ More user friction
```

---

## 🎯 **When to Use External Identity Provider SSO**

### **✅ Use External Identity Provider SSO When:**
- Public-facing applications
- User convenience is priority
- Social login desired
- External user base
- Quick user onboarding
- Modern authentication experience
- Reduced user friction needed

### **✅ Use Standalone SSO When:**
- Internal company applications
- High security requirements
- Complete control needed
- Custom authentication flows
- Compliance requirements
- No external dependencies wanted

---

## 📊 **Monitoring External Identity Provider SSO**

### **Keycloak Admin Console:**
```
Users: View all users and their sessions
Events: Monitor login/logout events (including external provider logins)
Sessions: Active user sessions
Clients: Application connections
Identity Providers: External provider status and configuration
```

### **External SSO Metrics:**
```
- Active sessions count
- Login success rate (overall and by provider)
- External provider login success rate
- Session duration
- User distribution by application
- Failed login attempts
- Identity provider usage statistics
```

---

## 🚨 **Troubleshooting External Identity Provider SSO**

### **Issue: "Invalid token"**
```
❌ Problem: JWT token validation fails
✅ Solution: Check client configuration and secret
```

### **Issue: "Google login not working"**
```
❌ Problem: Google OAuth configuration issues
✅ Solution: Check Google OAuth client ID/secret and redirect URIs
```

### **Issue: "Profile information not imported"**
```
❌ Problem: User attributes not mapped correctly
✅ Solution: Check identity provider mappers configuration
```

### **Issue: "Redirect loop"**
```
❌ Problem: Infinite redirects between apps
✅ Solution: Check redirect URIs in client config
```

### **Issue: "User not found"**
```
❌ Problem: User exists in Keycloak but not in app
✅ Solution: Check user mapping and attributes
```

### **Issue: "403 Forbidden on identity providers"**
```
❌ Problem: backend-client lacks view-identity-providers role
✅ Solution: Add view-identity-providers role to backend-client service account
```

---

## 📋 **External Identity Provider SSO Checklist**

### **Configuration:**
- [ ] Keycloak running and accessible
- [ ] DAM realm configured
- [ ] frontend-client and backend-client configured
- [ ] Google OAuth application created
- [ ] Google identity provider added to Keycloak
- [ ] Identity provider mappers configured
- [ ] Redirect URIs configured

### **Security:**
- [ ] Google OAuth client ID/secret configured
- [ ] backend-client has view-identity-providers role
- [ ] Session timeout configured
- [ ] HTTPS in production
- [ ] Strong client secrets

### **Testing:**
- [ ] Google SSO login works
- [ ] Profile information imported correctly
- [ ] SSO between applications works
- [ ] Session management works
- [ ] Logout works
- [ ] Token validation works

---

## 🎉 **Summary**

### **External Identity Provider SSO:**
- ✅ **Google/Microsoft integration**
- ✅ **Keycloak as SSO broker**
- ✅ **Automatic user account creation**
- ✅ **Profile information import**
- ✅ **Modern authentication experience**

### **What You Have:**
```
✅ Keycloak = SSO Broker
✅ Google = Identity Provider
✅ Frontend = SSO Client (frontend-client)
✅ Backend = Token Validator (backend-client)
✅ JWT tokens for authentication
✅ Single sign-on working
✅ External provider integration
```

### **What You Need:**
```
✅ Google OAuth setup
✅ Google identity provider in Keycloak
✅ Identity provider mappers
✅ frontend-client and backend-client
✅ Proper service account roles
```

---

**Your Keycloak setup with external identity providers provides a complete modern SSO solution!** 🚀🔐
