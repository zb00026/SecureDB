package com.verlake.dam.e2etest.test;

import com.verlake.dam.e2etest.pageobjects.*;
import org.junit.jupiter.api.*;
import org.openqa.selenium.Dimension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Audit Trail")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AuditTrailE2ETest extends BaseLoginTest {
    private KeycloakLoginPage keycloakLoginPage;
    private SettingsPage settingsPage;
    private UserManagementPage userPage;
    private AuditHistoryPage auditPage;

    private String auditorUsername;
    private String auditorPassword;
    private String developerUsername;
    private String developerPassword;
    private String approverUsername;
    private String approverPassword;
    private String testBucketName;

    @BeforeAll
    void setupTestData() throws SQLException {
        if (activeProfile.equals("dev")) {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            dataSource.setUrl(env.get("MYSQL_URL"));
            dataSource.setUsername(env.get("MYSQL_USERNAME"));
            dataSource.setPassword(env.get("MYSQL_PASSWORD"));

            Connection conn = dataSource.getConnection();
            Statement stmt = conn.createStatement();
            String[] statements = {
                    "SET FOREIGN_KEY_CHECKS = 0",
                    "TRUNCATE TABLE audit_trails",
                    "TRUNCATE TABLE user_roles",
                    "TRUNCATE TABLE users",
                    "TRUNCATE TABLE assets",
                    "TRUNCATE TABLE asset_objects",
                    "TRUNCATE TABLE asset_approvers",
                    "TRUNCATE TABLE access_requests",
                    "TRUNCATE TABLE access_level_objects",
                    "TRUNCATE TABLE asset_credentials",
                    "TRUNCATE TABLE s3_bucket_settings",
                    "SET FOREIGN_KEY_CHECKS = 1",
                    "INSERT INTO users (email, first_name, last_name, is_active) VALUES ('chuc06872@gmail.com', 'E2E', 'Admin', true)",
                    "INSERT INTO user_roles (user_id, role_id) VALUES (1, 1)"
            };

            for (String sql : statements) {
                stmt.execute(sql);
            }
        }

    }

    @BeforeEach
    void beforeEach() {
        super.baseSetUp();
        browser.manage().window().setSize(new Dimension(1920, 1080));  // Full HD resolution
    
        keycloakAuthUrl = getEnvVariable("KEYCLOAK_AUTH_URL");
        auditorUsername = getEnvVariable("KEYCLOAK_AUDITOR_USER");
        auditorPassword = getEnvVariable("KEYCLOAK_AUDITOR_PASSWORD");
        developerUsername = getEnvVariable("KEYCLOAK_DEVELOPER_USER");
        developerPassword = getEnvVariable("KEYCLOAK_DEVELOPER_PASSWORD");
        approverUsername = getEnvVariable("KEYCLOAK_APPROVER_USER");
        approverPassword = getEnvVariable("KEYCLOAK_APPROVER_PASSWORD");
        testBucketName = getEnvVariable("E2E_TEST_S3_BUCKET");
    }

    @Test
    @Order(1)
    @DisplayName("Configure S3")
    void adminOperations() throws InterruptedException {
        super.loginAsAdmin();
        assertThat(dashboardPage.btnLogout.getText()).isEqualTo("Logout");

        // Configure S3
        settingsPage = new SettingsPage(browser, baseUrl);
        settingsPage.navigateToAuditHistory();
        settingsPage.waitForPageToLoad();
        settingsPage.configureS3Bucket(testBucketName);
    }

    @Test
    @Order(2)
    @DisplayName("Manage users as admin")
    void managementUsers() throws InterruptedException {

        // Manage users
        userPage = new UserManagementPage(browser, baseUrl);
        userPage.navigateToUserManagement();
        assertThat(browser.getCurrentUrl()).startsWith(baseUrl + "/admin/users");

        // Add delays between operations
        userPage.createUser(auditorUsername, "Test", "User1", auditorPassword, "Auditor");
        Thread.sleep(2000);

        userPage.modifyUser(auditorUsername, "NewAuditorFName", "NewAuditorLName");
        Thread.sleep(2000);

        userPage.createUser(developerUsername, "Test", "User2", developerPassword, "Developer");
        Thread.sleep(2000);

        userPage.modifyUser(developerUsername, "NewDeveloperFName", "NewDeveloperLName");
        Thread.sleep(2000);

        userPage.createUser(approverUsername, "Test", "User1", approverPassword, "Approver");
        Thread.sleep(2000);

        userPage.modifyUser(approverUsername, "Modified", "User1");
        Thread.sleep(2000);

        userPage.createUser("test2@example.com", "Test", "User2", "password", "Asset Owner");
        Thread.sleep(2000);

        userPage.deleteUser("test2@example.com");

        // Logout
        dashboardPage.logout();
    }

    @Test
    @Order(3)
    @DisplayName("Verify audit trail as auditor")
    void auditVerification() throws InterruptedException {
        // Login as auditor
        Thread.sleep(3000);
        browser.navigate().to(baseUrl);
        homePage.clickKeycloakButton();
        Thread.sleep(3000);
        keycloakLoginPage = new KeycloakLoginPage(browser, keycloakAuthUrl);
        keycloakLoginPage.login(auditorUsername, auditorPassword);

        // Verify login success
        dashboardPage = new DashboardPage(browser, baseUrl);
        dashboardPage.waitForDashboadPage();

        // Check audit trail
        auditPage = new AuditHistoryPage(browser, baseUrl);
        auditPage.navigateToAuditHistory();

        // Verify all actions are recorded
        System.out.println("TR Length");
        System.out.println(auditPage.getAuditEntries().size());
        // assertThat(auditPage.getAuditEntries()).hasSize(9); // S3 config + 4 user
        // actions
        assertThat(auditPage.verifyAuditEntry("CREATE", approverUsername)).isTrue();
        assertThat(auditPage.verifyAuditEntry("UPDATE", approverUsername)).isTrue();
        assertThat(auditPage.verifyAuditEntry("CREATE", "test2@example.com")).isTrue();
        assertThat(auditPage.verifyAuditEntry("DELETE", "test2@example.com")).isTrue();
    }
}