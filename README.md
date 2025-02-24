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

4. Create Admin User of DAM Realm
   - Go to Users → Add User
   - Username: admin
   - Email: admin@example.com
   - Email Verified: ON
   - Click Save
   - Go to Credentials tab
   - Set password: `your_keycloak_dam_realm_admin_password`
   - Temporary: OFF

5. Update Application Properties
   - Copy backend-client secret from:
     Clients → backend-client → Credentials tab
   - Update in application.properties:
     ```properties
     spring.security.oauth2.client.registration.keycloak.client-secret=your_client_secret
     ```

## Running the Application in IntelliJ
1. Ensure KeyCloak is running
2. Ensure MySQL is running
3. Start the Spring Boot application
   <img width="907" alt="image" src="https://github.com/user-attachments/assets/9ba9c094-d9ed-4cbc-9e9b-459cec097897" />
   1. Set `Program Arguments` as following.
   `--spring.profiles.active=dev`

   2. Set `Environment Variables` as following.      `MYSQL_PASSWORD=your_mysql_db_password;KEYCLOAK_ADMIN_USER_PASSWORD=your_keycloak_dam_realm_admin_password;KEYCLOAK_CLIENT_SECRET=your_client_secret;MAIL_SENDER=your_email;MAIL_USERNAME=AWS_SES_USERNAME;MAIL_PASSWORD=AWS_SES_USER_PASSWORD;APM_SECRET_TOKEN=elastic_apm_token;APM_SERVICE_NAME=dev_dam`
   3. Run Application.
   
4. Start E2E Test
   Run following gradlew command
   `gradlew e2eTest --refresh-dependencies --stacktrace`
   
6. Start Clean, Test and Build
   <img width="816" alt="image" src="https://github.com/user-attachments/assets/7afa3d14-ef3a-49f6-98be-831ec8bad98a" />

   1. Set following parameters
   `clean build --refresh-dependencies --stacktrace`

   2. Set environment variables
   `MAIL_SENDER=your_email;MYSQL_PASSWORD=your_mysql_db_password;GOOGLE_ISSUE_URI=https://accounts.google.com;KEYCLOAK_REALM_NAME=DAM;MYSQL_URL=jdbc:mysql://localhost:3306/dam;spring-boot.run.profiles=dev;MYSQL_USERNAME=root;AUTH_PROVIDER=keycloak;GOOGLE_JWKS_URI=https://www.googleapis.com/oauth2/v3/certs;HOST_DOMAIN_URI=http://localhost:5173;KEYCLOAK_URL=http://localhost:8081`

   3. Run
   
