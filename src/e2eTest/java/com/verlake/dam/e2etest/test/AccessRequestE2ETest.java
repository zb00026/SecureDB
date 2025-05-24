package com.verlake.dam.e2etest.test;

import com.verlake.dam.e2etest.pageobjects.*;
import org.junit.jupiter.api.*;

@DisplayName("Developer Access Request Operations")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AccessRequestE2ETest extends BaseE2ETest {

    private DeveloperAssetPage developerAssetPage;
    private ApproveAccessRequestPage approveAccessRequestPage;

    @BeforeAll
    void setupTestData() {
        super.baseSetUp();
    }

    void loginWithCredential(String username, String password) throws InterruptedException {
        browser.get(baseUrl);
        homePage.clickKeycloakButton();
        keycloakLoginPage = new KeycloakLoginPage(browser, keycloakAuthUrl);
        keycloakLoginPage.login(username, password);

        // Verify login success
        if (dashboardPage == null) {
            dashboardPage = new DashboardPage(browser, baseUrl);
        }
        dashboardPage.navigateToDashboard();
        dashboardPage.waitForDashboadPage();
        dashboardPage.updatePassword(password);

    }

    @Test
    @Order(1)
    @DisplayName("Assign owner to previously created asset")
    void createAnAssetOwner() throws InterruptedException {
        super.loginAsAdmin();
        assetsPage = new AssetsPage(browser, baseUrl);
        assetsPage.navigateToAssetsPage();
        assetsPage.waitForPageToLoad();
        assetsPage.navigateToAssetsPage();
        assetsPage.setOwnerOfAsset(assetName, assetDbType, assetHostAddress, assetPortNumber, assetDatabaseName,
                "E2E Asset", "E2E Owner", assetOwnerUsername);
        dashboardPage.logout();
    }

    @Test
    @Order(2)
    @DisplayName("Log in as Asset Owner and create Credential")
    void loginAssetOwnerCreateCredential() throws InterruptedException {
        loginAssetOwnerAndCreateCredential();
        dashboardPage.takeScreenshot("Asset Owner and Create Credential111-");
        browser.navigate().refresh();
        browser.navigate().refresh();
        dashboardPage.takeScreenshot("Asset Owner and Create Credential222-");
        Thread.sleep(2000);
        dashboardPage.logout();
    }

    @Test
    @Order(3)
    @DisplayName("Log in as Developer and create Access Request")
    void loginDeveloperCreateAccessRequest() throws InterruptedException {
        loginWithCredential(developerUsername, developerPassword);
        developerAssetPage = new DeveloperAssetPage(browser, baseUrl);
        developerAssetPage.navigate();

        developerAssetPage.clickRequestAccessAssetByCriteria(assetName, assetDbType, assetHostAddress, assetPortNumber, assetDatabaseName);
        developerAssetPage.requestAccess();
        developerAssetPage.takeScreenshot("Developer Access Request");
        dashboardPage.logout();
    }

    @Test
    @Order(4)
    @DisplayName("Log in as Asset Owner and approve Access Request")
    void loginAssetOwnerApproveAccessRequest() throws InterruptedException {
        // Login as asset owner
        loginWithCredential(assetOwnerUsername, assetOwnerPassword);
        approveAccessRequestPage = new ApproveAccessRequestPage(browser, baseUrl);
        approveAccessRequestPage.navigate();
        Thread.sleep(1000);
        approveAccessRequestPage.clickApproveAccessRequestByCriteria(assetName, assetDescription, "NewDeveloperFName NewDeveloperLName", developerUsername);
        dashboardPage.logout();
        System.out.println(browser.getCurrentUrl());

    }

    @Test
    @Order(5)
    @DisplayName("Log in as Developer and update password of approved access request")
    void loginDeveloperUpdateApprovedAccessRequestPassword() throws InterruptedException {
        System.out.println(browser.getCurrentUrl());
        loginWithCredential(developerUsername, developerPassword);
        developerAssetPage = new DeveloperAssetPage(browser, baseUrl);
        developerAssetPage.navigate();

        developerAssetPage.updateApprovedAccessRequestPassword(assetName, assetDescription, assetHostAddress, assetPortNumber, assetDatabaseName, "!Q029OLkdj25!*");
        dashboardPage.logout();
    }
}