package com.verlake.dam.e2etest.test;

import com.verlake.dam.e2etest.E2E;
import com.verlake.dam.e2etest.packageobjects.DashboardPage;
import com.verlake.dam.e2etest.packageobjects.KeycloakLoginPage;
import com.verlake.dam.e2etest.packageobjects.MainPage;
import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Application")
@TestMethodOrder(OrderAnnotation.class)
public class ApplicationE2ETests extends E2E {

    private MainPage homePage;

    private KeycloakLoginPage keycloakLoginPage;

    private String baseUrl;

    private String keycloakAuthUrl;

    private String keycloakUsername;

    private String keycloakPassword;

    @BeforeEach
    void beforeEach() {
        homePage = new MainPage(browser);

        Dotenv e2eDotEnv = Dotenv.configure().directory("./").filename(".env.e2etest").load();
        baseUrl = e2eDotEnv.get("FRONTEND_URL");
        keycloakAuthUrl = e2eDotEnv.get("KEYCLOAK_AUTH_URL");
        keycloakUsername = e2eDotEnv.get("KEYCLOAK_E2ETEST_USER");
        keycloakPassword = System.getenv("KEYCLOAK_E2ETEST_PASSWORD");
        browser.get(baseUrl);
    }

    @Test
    @Order(1)
    @DisplayName("I see 'Login with Keycloak' button when I open the Frontend Home Page")
    void homePageOfFrontend() {
        assertThat(browser.getTitle()).isEqualTo("DAM Frontend");
        assertThat(homePage.keycloakButton.getText()).isEqualTo("Login with Keycloak");
    }

    @Test
    @Order(2)
    @DisplayName("Wait fo Keycloak login page after clicking on 'Login with Keycloak' button")
    void keycloakLoginPage() {
        homePage.keycloakButton.click();

        keycloakLoginPage = new KeycloakLoginPage(browser, keycloakAuthUrl);
        keycloakLoginPage.login(keycloakUsername, keycloakPassword);

        // Verify that the user is logged in by checking the redirected URL or other indicators
        assertThat(browser.getCurrentUrl()).contains(baseUrl);
    }

    @Test
    @Order(3)
    @DisplayName("Check Login Page Success")
    void dashboardPage() {

        DashboardPage dshBoardPage= new DashboardPage(browser, baseUrl);
        dshBoardPage.waitForDashboadPage();
        // Verify that the user is logged in by checking the redirected URL or other indicators
        assertThat(browser.getCurrentUrl()).contains(baseUrl);
        assertThat(dshBoardPage.btnLogout.getText()).isEqualTo("Logout");
    }
}
