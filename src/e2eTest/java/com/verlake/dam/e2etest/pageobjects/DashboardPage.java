package com.verlake.dam.e2etest.pageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class DashboardPage extends BasePage {

    private String baseUrl;

    @FindBy(id="btnLogout")
    public WebElement btnLogout;

    @FindBy(className = "chakra-spinner")
    public WebElement chakraSpinner;

    private WebDriver browser;

    public DashboardPage(WebDriver browser, String baseUrl) {
        super(browser);
        PageFactory.initElements(browser, this);
        this.baseUrl = baseUrl;
        this.browser = browser;
    }


    // Wait for the login page to load by checking the URL and visibility of the username field
    public void waitForDashboadPage() {
        // Wait for the URL to change to localhost:8081 (Keycloak login page)
        wait.until(ExpectedConditions.urlMatches("^" + baseUrl));

        wait.until(ExpectedConditions.invisibilityOf(chakraSpinner));
        // Wait for the username field to be visible
        wait.until(ExpectedConditions.visibilityOf(btnLogout));
    }

    public void logout() {
        browser.navigate().to(baseUrl);
        wait.until(ExpectedConditions.invisibilityOf(chakraSpinner));
        wait.until(ExpectedConditions.elementToBeClickable(btnLogout));
        btnLogout.click();
    }
}
