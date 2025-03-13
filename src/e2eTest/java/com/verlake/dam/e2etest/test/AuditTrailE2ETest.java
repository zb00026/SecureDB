package com.verlake.dam.e2etest.test;

import com.verlake.dam.e2etest.E2E;
import com.verlake.dam.e2etest.pageobjects. *;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api. *;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Audit Trail")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) 
public class AuditTrailE2ETest extends BaseLoginTest {
    private KeycloakLoginPage keycloakLoginPage;
    private SettingsPage settingsPage;
    private UserManagementPage userPage;
    private AuditHistoryPage auditPage;

    private String keycloakAuthUrl;
    private String auditorUsername;
    private String auditorPassword;
    private String developerUsername;
    private String developerPassword;
    private String testBucketName;

    @BeforeAll
    void setupTestData() {
        // Specify the full path relative to resources directory
        executeLiquibaseChangelog("db/changelog/db.changelog-seed-e2e-data.xml");
    }

    @BeforeEach
    void beforeEach() {
        super.baseSetUp();
        keycloakAuthUrl = getEnvVariable("KEYCLOAK_AUTH_URL");
        auditorUsername = getEnvVariable("KEYCLOAK_AUDITOR_USER");
        auditorPassword = getEnvVariable("KEYCLOAK_AUDITOR_PASSWORD");
        developerUsername = getEnvVariable("KEYCLOAK_DEVELOPER_USER");
        developerPassword = getEnvVariable("KEYCLOAK_DEVELOPER_PASSWORD");
        testBucketName = getEnvVariable("E2E_TEST_S3_BUCKET");
    }

    @Test
    @Order(1)
    @DisplayName("Configure S3 and manage users as admin")
    void adminOperations() {
        super.loginAsAdmin();
        assertThat(dashboardPage.btnLogout.getText()).isEqualTo("Logout");

        // Configure S3
        settingsPage = new SettingsPage(browser);
        settingsPage.waitForPageToLoad();
        settingsPage.configureS3Bucket(testBucketName);

        // Manage users
        userPage = new UserManagementPage(browser, baseUrl);
        userPage.navigateToUserManagement();
        assertThat(browser.getCurrentUrl()).startsWith(baseUrl + "/admin/users");
        userPage.createUser(auditorUsername, "Test", "User1", auditorPassword, "Auditor");
        userPage.modifyUser(auditorUsername, "NewAuditorFName", "NewAuditorLName");
        userPage.createUser(developerUsername, "Test", "User2", developerPassword, "Developer");
        userPage.modifyUser(developerUsername, "NewDeveloperFName", "NewDeveloperLName");

        userPage.createUser("test1@example.com", "Test", "User1", "password", "Approver");
        userPage.modifyUser("test1@example.com", "Modified", "User1");
        userPage.createUser("test2@example.com", "Test", "User2", "password", "Resource Owner");
        userPage.deleteUser("test2@example.com");

        // Logout
        dashboardPage.logout();
    }

    @Test
    @Order(2)
    @DisplayName("Verify audit trail as auditor")
    void auditVerification() {
        // Login as auditor
        homePage.clickKeycloakButton();
        keycloakLoginPage = new KeycloakLoginPage(browser, keycloakAuthUrl);
        keycloakLoginPage.login(auditorUsername, auditorPassword);

        // Verify login success
        dashboardPage = new DashboardPage(browser, baseUrl);
        dashboardPage.waitForDashboadPage();

        // Check audit trail
        auditPage = new AuditHistoryPage(browser, baseUrl);
        auditPage.navigateToAuditHistory();

        // Verify all actions are recorded
        assertThat(auditPage.getAuditEntries()).hasSize(9); // S3 config + 4 user actions
        assertThat(auditPage.verifyAuditEntry("CREATE", "test1@example.com")).isTrue();
        assertThat(auditPage.verifyAuditEntry("UPDATE", "test1@example.com")).isTrue();
        assertThat(auditPage.verifyAuditEntry("CREATE", "test2@example.com")).isTrue();
        assertThat(auditPage.verifyAuditEntry("DELETE", "test2@example.com")).isTrue();
    }
}