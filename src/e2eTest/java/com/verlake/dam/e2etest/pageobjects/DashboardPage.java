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
        // Wait for the username field to be visible
        wait.until(ExpectedConditions.visibilityOf(btnLogout));

    }

    public void logout() throws InterruptedException {
        try {
            browser.navigate().to(baseUrl);
            wait.until(ExpectedConditions.invisibilityOf(chakraSpinner));
            
            // Wait for logout button and ensure it's truly clickable
            wait.until(ExpectedConditions.elementToBeClickable(btnLogout));
            wait.until(driver -> {
                try {
                    return btnLogout.isDisplayed() && btnLogout.isEnabled();
                } catch (Exception e) {
                    return false;
                }
            });
            
            // Try clicking with retry logic
            int maxRetries = 5;
            int retryCount = 0;
            boolean clickSuccess = false;
            
            while (!clickSuccess && retryCount < maxRetries) {
                try {
                    // Try JavaScript click first
                    ((JavascriptExecutor) browser).executeScript("arguments[0].click();", btnLogout);
                    clickSuccess = true;
                } catch (Exception e) {
                    retryCount++;
                    if (retryCount == maxRetries) {
                        // Last attempt, try regular click
                        try {
                            btnLogout.click();
                            clickSuccess = true;
                        } catch (Exception clickEx) {
                            System.err.println("Failed to click logout button after " + maxRetries + " attempts");
                            takeScreenshotOnError("logout_button_click_failed", clickEx);
                            throw clickEx;
                        }
                    }
                    Thread.sleep(1000); // Wait before retry
                }
            }

            Thread.sleep(3000);
            
            // Wait for login button with timeout
            WebDriverWait shortWait = new WebDriverWait(browser, Duration.ofSeconds(10));
            shortWait.until(ExpectedConditions.visibilityOf(browser.findElement(By.id("btnLogin"))));

            // Clear browser cache and cookies
            browser.manage().deleteAllCookies();
            ((JavascriptExecutor) browser).executeScript("window.localStorage.clear();");
            ((JavascriptExecutor) browser).executeScript("window.sessionStorage.clear();");
            browser.navigate().to(baseUrl);
            
        } catch (Exception e) {
            System.err.println("Logout failed: " + e.getMessage());
            System.err.println("Current URL: " + browser.getCurrentUrl());
            takeScreenshotOnError("logout_failed", e);
            throw e;
        }
    }
}
