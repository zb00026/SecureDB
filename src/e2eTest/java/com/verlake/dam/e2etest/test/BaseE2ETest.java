package com.verlake.dam.e2etest.test;

import com.verlake.dam.e2etest.pageobjects.*;
import lombok.Getter;
import org.junit.jupiter.api.*;
import org.openqa.selenium.Dimension;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.OutputType;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@ExtendWith(TestWatcherExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseE2ETest extends BaseLoginTest implements TestWatcher {
    protected static WebDriver staticBrowser;

    protected KeycloakLoginPage keycloakLoginPage;
    protected AssetsPage assetsPage;
    protected UserManagementPage ownerCreatePage;
    protected AssetCredentialsPage assetCredentialsPage;
    protected SettingsPage settingsPage;
    protected UserManagementPage userPage;


    protected String auditorUsername;
    protected String auditorPassword;
    protected String developerUsername;
    protected String developerPassword;
    protected String assetOwnerUsername;
    protected String assetOwnerPassword;

    protected String assetName;
    protected String assetDbType;
    protected String assetHostAddress;
    protected String assetPortNumber;
    protected String assetDatabaseName;
    protected String assetDescription;
    protected String assetCredentialUsername;
    protected String assetCredentialPassword;

    @BeforeEach
    protected void baseBeforeEach() {
        super.baseSetUp();
        staticBrowser = browser;  // Store browser reference for static methods
        browser.manage().window().setSize(new Dimension(1920, 1080));  // Full HD resolution
    
        keycloakAuthUrl = getEnvVariable("KEYCLOAK_AUTH_URL");
        auditorUsername = getEnvVariable("KEYCLOAK_AUDITOR_USER");
        auditorPassword = getEnvVariable("KEYCLOAK_AUDITOR_PASSWORD");
        developerUsername = getEnvVariable("KEYCLOAK_DEVELOPER_USER");
        developerPassword = getEnvVariable("KEYCLOAK_DEVELOPER_PASSWORD");
        assetOwnerUsername = getEnvVariable("KEYCLOAK_ASSET_OWNER_USER");
        assetOwnerPassword = getEnvVariable("KEYCLOAK_ASSET_OWNER_PASSWORD");

        assetName = getEnvVariable("ASSET_NAME");
        assetDbType = getEnvVariable("ASSET_DB_TYPE");
        assetHostAddress = getEnvVariable("ASSET_HOST_ADDRESS");
        assetPortNumber = getEnvVariable("ASSET_PORT_NUMBER");
        assetDatabaseName = getEnvVariable("ASSET_DB_NAME");
        assetDescription = getEnvVariable("ASSET_DESCRIPTION");
        assetCredentialUsername = getEnvVariable("ASSET_CREDENTIAL_USERNAME");
        assetCredentialPassword = getEnvVariable("MYSQL_PASSWORD");
    }

    protected void createAsset() throws InterruptedException {
        super.loginAsAdmin();
        dashboardPage.waitForDashboadPage();
        assertThat(dashboardPage.btnLogout.getText()).isEqualTo("Logout");

        // Add Asset
        assetsPage = new AssetsPage(browser, baseUrl);
        assetsPage.navigateToAssetsPage();
        assetsPage.waitForPageToLoad();
        assetsPage.addNewAsset(assetName, assetDbType, assetHostAddress, assetPortNumber, assetDatabaseName, assetDescription);
    }

    protected void createAndSetAssetOwner() throws InterruptedException {
        // Create Asset Owner Account
        ownerCreatePage = new UserManagementPage(browser, baseUrl);
        ownerCreatePage.navigateToUserManagement();
        assertThat(browser.getCurrentUrl()).startsWith(baseUrl + "/admin/users");

        ownerCreatePage.createUser(assetOwnerUsername, "E2E Asset", "E2E Owner", assetOwnerPassword, "Asset Owner");
        Thread.sleep(2000);

        // Set the owner of created Asset
        assetsPage.navigateToAssetsPage();
        assetsPage.setOwnerOfAsset(assetName, assetDbType, assetHostAddress, assetPortNumber, assetDatabaseName,
                "E2E Asset", "E2E Owner", assetOwnerUsername);
        Thread.sleep(2000);

        // Logout
        dashboardPage.logout();
    }

    protected void loginAssetOwnerAndCreateCredential() throws InterruptedException {
        // Login as asset owner
        browser.get(baseUrl);
        homePage.clickKeycloakButton();
        keycloakLoginPage = new KeycloakLoginPage(browser, keycloakAuthUrl);
        keycloakLoginPage.login(assetOwnerUsername, assetOwnerPassword);

        // Verify login success
        dashboardPage.navigateToDashboard();
        dashboardPage.waitForDashboadPage();
        dashboardPage.updatePassword(assetOwnerPassword);

        // Navigate to Asset Credentials Page
        assetCredentialsPage = new AssetCredentialsPage(browser, baseUrl);
        assetCredentialsPage.navigateToAssetsPage();
        assetCredentialsPage.waitForPageToLoad(assetName, assetDbType, assetHostAddress, assetPortNumber, assetDatabaseName);
        assetCredentialsPage.createCredential(assetName, assetDbType, assetHostAddress, assetPortNumber, assetDatabaseName, assetCredentialUsername, assetCredentialPassword);
    }

    public static WebDriver getStaticBrowser() {
        return staticBrowser;
    }
}