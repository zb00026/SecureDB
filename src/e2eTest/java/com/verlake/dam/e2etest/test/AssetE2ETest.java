package com.verlake.dam.e2etest.test;

import com.verlake.dam.e2etest.pageobjects.*;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Asset/Owner/Audit Trail Test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Order(2)
public class AssetE2ETest extends BaseLoginTest {

    private AssetsPage assetsPage;
    private UserManagementPage ownerCreatePage;
    private AssetCredentialsPage assetCredentialsPage;

    private String auditorUsername;
    private String auditorPassword;
    private String assetOwnerUsername;
    private String assetOwnerPassword;

    private String assetName;
    private String assetDbType;
    private String assetHostAddress;
    private String assetDescription;
    private String assetCredentialUsername;
    private String assetCredentialPassword;

    @BeforeAll
    void setupTestData() {
    }

    @BeforeEach
    void beforeEach() {
        super.baseSetUp();
        keycloakAuthUrl = getEnvVariable("KEYCLOAK_AUTH_URL");
        auditorUsername = getEnvVariable("KEYCLOAK_AUDITOR_USER");
        auditorPassword = getEnvVariable("KEYCLOAK_AUDITOR_PASSWORD");
        assetOwnerUsername = getEnvVariable("KEYCLOAK_ASSET_OWNER_USER");
        assetOwnerPassword = getEnvVariable("KEYCLOAK_ASSET_OWNER_PASSWORD");
        assetName = getEnvVariable("ASSET_NAME");
        assetDbType = getEnvVariable("ASSET_DB_TYPE");
        assetHostAddress = getEnvVariable("ASSET_HOST_ADDRESS");
        assetDescription = getEnvVariable("ASSET_DESCRIPTION");
        assetCredentialUsername = getEnvVariable("ASSET_CREDENTIAL_USERNAME");
        assetCredentialPassword = getEnvVariable("MYSQL_PASSWORD");
    }

    @Test
    @Order(1)
    @DisplayName("Create an Asset")
    void createAnAsset() throws InterruptedException {
        super.loginAsAdmin();
        assertThat(dashboardPage.btnLogout.getText()).isEqualTo("Logout");

        // Add Asset
        assetsPage = new AssetsPage(browser, baseUrl);
        assetsPage.navigateToAssetsPage();
        assetsPage.waitForPageToLoad();
        assetsPage.addNewAsset(assetName, assetDbType, assetHostAddress, assetDescription);

    }

    @Test
    @Order(2)
    @DisplayName("Assign owner to created asset")
    void createAnAssetOwner() throws InterruptedException {
        // Create Resource Owner Account
        ownerCreatePage = new UserManagementPage(browser, baseUrl);
        ownerCreatePage.navigateToUserManagement();
        assertThat(browser.getCurrentUrl()).startsWith(baseUrl + "/admin/users");

        // Add delays between operations
        ownerCreatePage.createUser(assetOwnerUsername, "E2E Asset", "E2E Owner", assetOwnerPassword, "Resource Owner");
        Thread.sleep(2000);
    }

    @Test
    @Order(3)
    @DisplayName("Set the owner of created Asset")
    void setOwnerOfAsset() throws InterruptedException {
        // Set the owner of created Asset
        assetsPage.navigateToAssetsPage();
        assetsPage.setOwnerOfAsset(assetName, assetDbType, assetHostAddress,
                "E2E Asset", "E2E Owner", assetOwnerUsername);
        Thread.sleep(2000);

        // Logout
        dashboardPage.logout();
    }

    @Test
    @Order(4)
    @DisplayName("Log in as Asset Owner and create Credential")
    void loginAssetOwnerCreateCredential() throws InterruptedException {
        // Login as auditor
        browser.get(baseUrl);
        homePage.clickKeycloakButton();
        keycloakLoginPage = new KeycloakLoginPage(browser, keycloakAuthUrl);
        keycloakLoginPage.login(assetOwnerUsername, assetOwnerPassword);

        // Verify login success
        dashboardPage.navigateToDashboard();
        dashboardPage.waitForDashboadPage();

        // Navigate to Asset Credentials Page
        assetCredentialsPage = new AssetCredentialsPage(browser, baseUrl);
        assetCredentialsPage.navigateToAssetsPage();
        assetCredentialsPage.waitForPageToLoad(assetName, assetDbType, assetHostAddress);
        assetCredentialsPage.createCredential(assetName, assetDbType, assetHostAddress, assetCredentialUsername, assetCredentialPassword);

        //Relinquish Credential
        assetCredentialsPage.relinquishCredential(assetName, assetDbType, assetHostAddress);
        dashboardPage.logout();
    }

    @Test
    @Order(5)
    @DisplayName("Verify audit trail as auditor")
    void auditVerification() {
        // Login as auditor
        homePage.clickKeycloakButton();
        keycloakLoginPage = new KeycloakLoginPage(browser, keycloakAuthUrl);
        keycloakLoginPage.login(auditorUsername, auditorPassword);

        // Verify login success
        dashboardPage = new DashboardPage(browser, baseUrl);
        dashboardPage.waitForDashboadPage();

        // Navigate to Audit Trail Page
        AuditHistoryPage auditPage = new AuditHistoryPage(browser, baseUrl);
        auditPage.navigateToAuditHistory();
        assertThat(auditPage.verifyAuditEntry("CREATE", assetName)).isTrue();
        assertThat(auditPage.verifyAuditEntry("CREATE", assetOwnerUsername)).isTrue();
        assertThat(auditPage.verifyAuditEntry("CREATE", "ASSET_CREDENTIAL")).isTrue();
        assertThat(auditPage.verifyAuditEntry("UPDATE", assetCredentialUsername)).isTrue();
        assertThat(auditPage.verifyAuditEntry("DELETE", assetCredentialUsername)).isTrue();
        assertThat(auditPage.verifyAuditEntry("CREATE", "RELINQUISH_ASSET_CREDENTIAL")).isTrue();
        assertThat(auditPage.verifyAuditEntry("UPDATE", "RELINQUISH_ASSET_CREDENTIAL")).isTrue();
        
    }
}
