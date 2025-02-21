package com.verlake.dam.e2etest.packageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class KeycloakLoginPage {

    @FindBy(id="username")
    public WebElement usernameField;

    @FindBy(id="password")
    public WebElement passwordField;

    @FindBy(id="kc-login")
    public WebElement loginButton;

    private WebDriver browser;

    private String keycloakAuthUrl;

    public KeycloakLoginPage(WebDriver browser, String keycloakAuthUrl) {
        PageFactory.initElements(browser, this);
        this.keycloakAuthUrl = keycloakAuthUrl;
        this.browser = browser;
    }


    // Wait for the login page to load by checking the URL and visibility of the username field
    public void waitForLoginPage() {
        WebDriverWait wait = new WebDriverWait(browser, Duration.ofSeconds(5));

        // Wait for the URL to change to localhost:8081 (Keycloak login page)
        wait.until(ExpectedConditions.urlMatches("^" + keycloakAuthUrl));


        // Wait for the username field to be visible
        wait.until(ExpectedConditions.visibilityOf(usernameField));
    }

    public void login(String username, String password) {
        // Wait for the login page to load and be ready
        waitForLoginPage();

        // Fill in the credentials and click the login button
        usernameField.sendKeys(username);
        passwordField.sendKeys(password);
        loginButton.click();
    }
}
