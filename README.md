# Spring Boot Application with REST API and Liquibase Integration

This project is a Spring Boot application that demonstrates:
- A simple CRUD REST API for managing `User` entities.
- Database versioning with Liquibase.
- Integration with a relational database (e.g., H2, MySQL, PostgreSQL).

---

## **Getting Started**

### **Prerequisites**
- Java 17 or higher
- Gradle 7.x or higher
- IntelliJ IDEA or Visual Studio Code (optional)
- A database (H2, MySQL, or PostgreSQL)

---

### **1. How to Run**
1. Clone the repository:
   ```bash
   git clone git@github.com:Mindslake/DAM.git
   cd DAM
2. Build and run the app using Gradle:
   ```bash
   ./gradlew bootRun
3. Test REST API point:
   ```bash
   http://localhost:8080/api/test
---

### **2. Enable Auto-Build in IntelliJ**
&nbsp;&nbsp;&nbsp;&nbsp;To ensure changes are compiled automatically:

#### **&nbsp;&nbsp;&nbsp;&nbsp;Enable Auto-Build**:
1. Go to **File > Settings > Build, Execution, Deployment > Compiler**.
2. Check the box: **Build project automatically**.

#### **&nbsp;&nbsp;&nbsp;&nbsp;Enable Auto-Build when application is running**:
1. Go to **File > Settings > Advanced Settings > Compiler**.
2. Check the box: **Allow auto-make to start even if developed application is currently running**.

---

## KeyCloak Setup Instructions

### Option 1: Import Realm Configuration
1. Download and Start KeyCloak
   ```bash
   docker run -p 8081:8080 -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin quay.io/keycloak/keycloak:latest start-dev
   ```

2. Import Realm Configuration
   - Access admin console at http://localhost:8081
   - Login with admin/admin
   - Click on "Create Realm"
   - Click "Browse" and select the realm configuration file from:
     `src/main/resources/keycloak/realm-export.json`
   - Click "Create"

### Option 2: Manual Configuration
1. Create New Realm
   - Click "Create Realm"
   - Name: DAM
   - Click "Create"

2. Configure Clients
   a. Backend Client:
   - Client ID: backend-client
   - Client Protocol: openid-connect
   - Access Type: confidential
   - Service Accounts Enabled: ON
   - Authorization Enabled: OFF
   - Valid Redirect URIs: http://localhost:8080/*
   - Web Origins: http://localhost:8080
   
   b. Frontend Client:
   - Client ID: frontend-client
   - Client Protocol: openid-connect
   - Access Type: public
   - Valid Redirect URIs: http://localhost:5173/*
   - Web Origins: http://localhost:5173

3. Configure Client Scopes
   - Go to Client Scopes
   - Add 'email' and 'profile' to default scopes for both clients

4. Create Test User
   - Go to Users → Add User
   - Username: testuser
   - Email: test@example.com
   - Email Verified: ON
   - Click Save
   - Go to Credentials tab
   - Set password: testpass
   - Temporary: OFF

5. Update Application Properties
   - Copy backend-client secret from:
     Clients → backend-client → Credentials tab
   - Update in application.properties:
     ```properties
     spring.security.oauth2.client.registration.keycloak.client-secret=your_client_secret
     ```

## Running the Application
1. Ensure KeyCloak is running
2. Start the Spring Boot application: `./gradlew bootRun`
3. Access the application at http://localhost:8080
