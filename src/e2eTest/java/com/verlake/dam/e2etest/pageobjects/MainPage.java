package com.verlake.dam.e2etest.pageobjects;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

public class MainPage extends BasePage {


    @FindBy(id = "btnLogin")
    public WebElement keycloakButton;

    @FindBy(className = "chakra-spinner")
    public WebElement chakraSpinner;

    String keycloakAuthUrl;
    String baseUrl;

    public MainPage(WebDriver browser, String baseUrl, String keycloakAuthUrl) {
        super(browser);
        PageFactory.initElements(browser, this);
        this.keycloakAuthUrl = keycloakAuthUrl;
    }

    public void navigate() {
        browser.navigate().to("/");
    }

    public void clickKeycloakButton() throws InterruptedException {
        try {
            // Wait for spinner to disappear first
            wait.until(ExpectedConditions.invisibilityOf(chakraSpinner));
            
            // Wait for button to be clickable and ensure it's truly clickable
            wait.until(ExpectedConditions.elementToBeClickable(keycloakButton));
            wait.until(driver -> {
                try {
                    return keycloakButton.isDisplayed() && keycloakButton.isEnabled();
                } catch (Exception e) {
                    return false;
                }
            });

            System.out.println("Current URL before click: " + browser.getCurrentUrl());
            
            // Try clicking with retry logic
            int maxRetries = 5;
            int retryCount = 0;
            boolean clickSuccess = false;
            
            while (!clickSuccess && retryCount < maxRetries) {
                try {
                    // Try JavaScript click first
                    ((JavascriptExecutor) browser).executeScript("arguments[0].click();", keycloakButton);
                    clickSuccess = true;
                } catch (Exception e) {
                    retryCount++;
                    if (retryCount == maxRetries) {
                        // Last attempt, try regular click
                        try {
                            keycloakButton.click();
                            clickSuccess = true;
                        } catch (Exception clickEx) {
                            System.err.println("Failed to click Keycloak button after " + maxRetries + " attempts");
                            takeScreenshotOnError("keycloak_button_click_failed", clickEx);
                            throw clickEx;
                        }
                    }
                    Thread.sleep(1000); // Wait before retry
                }
            }

            // Wait for URL change with shorter timeout
            WebDriverWait shortWait = new WebDriverWait(browser, Duration.ofSeconds(10));
            shortWait.until(ExpectedConditions.urlMatches("^" + keycloakAuthUrl));
            System.out.println("Current URL after click: " + browser.getCurrentUrl());
            
        } catch (Exception e) {
            System.err.println("Failed to click Keycloak button: " + e.getMessage());
            System.err.println("Current URL at failure: " + browser.getCurrentUrl());
            takeScreenshotOnError("keycloak_button_click_failed", e);
            throw e;
        }
    }
}
