package com.verlake.dam.e2etest.test;

import com.verlake.dam.e2etest.E2E;
import com.verlake.dam.e2etest.pageobjects.*;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;

public class BaseLoginTest extends E2E {
    protected MainPage homePage;
    protected KeycloakLoginPage keycloakLoginPage;
    protected DashboardPage dashboardPage;
    protected String baseUrl;
    protected String keycloakAuthUrl;

    @BeforeEach
    void baseSetUp() {
        homePage = new MainPage(browser);
        baseUrl = getEnvVariable("FRONTEND_URL");
        keycloakAuthUrl = getEnvVariable("KEYCLOAK_AUTH_URL");
        browser.get(baseUrl);
    }

    protected void loginAsAdmin() {
        String adminUsername = getEnvVariable("KEYCLOAK_ADMIN_USER");
        String adminPassword = getEnvVariable("KEYCLOAK_ADMIN_PASSWORD");
        
        homePage.clickKeycloakButton();
        keycloakLoginPage = new KeycloakLoginPage(browser, keycloakAuthUrl);
        keycloakLoginPage.login(adminUsername, adminPassword);

        // Verify login success
        dashboardPage = new DashboardPage(browser, baseUrl);
        dashboardPage.waitForDashboadPage();
        assertThat(browser.getCurrentUrl()).contains(baseUrl);
    }
}