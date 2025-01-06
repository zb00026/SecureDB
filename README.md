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
