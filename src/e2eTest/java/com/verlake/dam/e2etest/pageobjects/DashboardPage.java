package com.verlake.dam.e2etest.pageobjects;

import org.openqa.selenium.*;
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

    public DashboardPage(WebDriver browser, String baseUrl) {
        super(browser);
        PageFactory.initElements(browser, this);
        this.baseUrl = baseUrl;
    }

    public void navigateToDashboard() {
        browser.navigate().to(baseUrl);
    }


    // Wait for the login page to load by checking the URL and visibility of the username field
    public void waitForDashboadPage() throws InterruptedException {
        browser.navigate().to(baseUrl);
        // Wait for the URL to change to localhost:8081 (Keycloak login page)
        Thread.sleep(3000);
        System.out.println("Waiting for Dashboad Page: " + browser.getCurrentUrl());
        wait.until(ExpectedConditions.urlMatches("^" + baseUrl));

        wait.until(ExpectedConditions.invisibilityOf(chakraSpinner));
        cancelTemporaryPassword();
        // Wait for the username field to be visible
        wait.until(ExpectedConditions.visibilityOf(btnLogout));

    }

    public void logout() throws InterruptedException {
        browser.navigate().to(baseUrl);
        cancelTemporaryPassword();
        wait.until(ExpectedConditions.invisibilityOf(chakraSpinner));
        wait.until(ExpectedConditions.elementToBeClickable(btnLogout));
        btnLogout.click();
        Thread.sleep(3000);

        // Clear browser cache and cookies
        browser.manage().deleteAllCookies();

        // Execute JavaScript to clear localStorage and sessionStorage
        ((JavascriptExecutor) browser).executeScript("window.localStorage.clear();");
        ((JavascriptExecutor) browser).executeScript("window.sessionStorage.clear();");
        browser.navigate().to(baseUrl);

    }
}
