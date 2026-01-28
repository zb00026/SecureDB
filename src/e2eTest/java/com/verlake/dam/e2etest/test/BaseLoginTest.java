package com.verlake.dam.e2etest.test;

import com.verlake.dam.e2etest.E2E;
import com.verlake.dam.e2etest.pageobjects.AuditHistoryPage;
import com.verlake.dam.e2etest.pageobjects.DashboardPage;
import com.verlake.dam.e2etest.pageobjects.KeycloakLoginPage;
import com.verlake.dam.e2etest.pageobjects.MainPage;
import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseLoginTest extends E2E {
    protected MainPage homePage;
    protected KeycloakLoginPage keycloakLoginPage;
    protected DashboardPage dashboardPage;
    protected AuditHistoryPage auditPage;
    protected String baseUrl;
    protected String keycloakAuthUrl;

    @BeforeEach
    void baseSetUp() {
        baseUrl = getEnvVariable("FRONTEND_URL");
        keycloakAuthUrl = getEnvVariable("KEYCLOAK_AUTH_URL");
        homePage = new MainPage(browser, baseUrl, keycloakAuthUrl);
        browser.get(baseUrl);
    }

    protected void loginAsAdmin() throws InterruptedException {
        String adminUsername = getEnvVariable("KEYCLOAK_ADMIN_USER");
        String adminPassword = getEnvVariable("KEYCLOAK_ADMIN_PASSWORD");
        
        homePage.clickKeycloakButton();
        keycloakLoginPage = new KeycloakLoginPage(browser, keycloakAuthUrl);
        keycloakLoginPage.login(adminUsername, adminPassword);

        // Verify login success
        dashboardPage = new DashboardPage(browser, baseUrl);
        dashboardPage.waitForDashboadPage();
        dashboardPage.updatePassword(adminPassword);
        assertThat(browser.getCurrentUrl()).contains(baseUrl);
    }



    protected void loginAsAuditorAndNavigateToAuditTrail(String auditorUsername, String auditorPassword) throws InterruptedException {
        // Login as auditor
        homePage.clickKeycloakButton();
        Thread.sleep(3000);
        keycloakLoginPage = new KeycloakLoginPage(browser, keycloakAuthUrl);
        keycloakLoginPage.login(auditorUsername, auditorPassword);
        System.out.println("Auditor: " + auditorUsername + " Current URL: " + browser.getCurrentUrl());

        // Verify login success
        dashboardPage = new DashboardPage(browser, baseUrl);
        dashboardPage.waitForDashboadPage();
        dashboardPage.updatePassword(auditorPassword);

        // Navigate to Audit Trail Page
        auditPage = new AuditHistoryPage(browser, baseUrl);
        auditPage.navigateToAuditHistory();
    }

}