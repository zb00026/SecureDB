package com.verlake.dam.e2etest.packageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class DashboardPage {

    private String baseUrl;

    @FindBy(id="btnLogout")
    public WebElement btnLogout;

    private WebDriver browser;

    public DashboardPage(WebDriver browser, String baseUrl) {
        PageFactory.initElements(browser, this);
        this.baseUrl = baseUrl;
        this.browser = browser;
    }


    // Wait for the login page to load by checking the URL and visibility of the username field
    public void waitForDashboadPage() {
        WebDriverWait wait = new WebDriverWait(browser, Duration.ofSeconds(5));

        // Wait for the URL to change to localhost:8081 (Keycloak login page)
        wait.until(ExpectedConditions.urlMatches("^" + baseUrl));

        // Wait for the username field to be visible
        wait.until(ExpectedConditions.visibilityOf(btnLogout));
    }
}
