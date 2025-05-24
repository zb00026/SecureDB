package com.verlake.dam.e2etest.pageobjects;

import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.By;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.OutputType;

public abstract class BasePage {
    protected final WebDriver browser;
    protected final WebDriverWait wait;
    private static final String SCREENSHOTS_DIR = "screenshots";

    @FindBy(id="btnCancelCredential")
    public WebElement btnCancelCredential;

    public BasePage(WebDriver driver) {
        this.browser = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(60));
        PageFactory.initElements(driver, this);
    }

    public void checkElementById(String id) {
        String pgSrc = browser.getPageSource();
        System.out.println("Page source contains \"" + id + "\" : " + pgSrc.contains(id));
        System.out.println(browser.getCurrentUrl());

        // Try finding element different ways
        WebElement toast = browser.findElement(By.id(id));

        System.out.println("Element properties:");
        System.out.println("- Is Displayed: " + toast.isDisplayed());
        System.out.println("- Is Enabled: " + toast.isEnabled());
        System.out.println("- Location: " + toast.getLocation());
        System.out.println("- Size: " + toast.getSize());
        System.out.println("- CSS Display: " + toast.getCssValue("display"));
        System.out.println("- CSS Visibility: " + toast.getCssValue("visibility"));
        System.out.println("- CSS Opacity: " + toast.getCssValue("opacity"));
    }

    public void cancelTemporaryPassword() {
        try {
            WebDriverWait wait = new WebDriverWait(browser, Duration.ofSeconds(5));
//            checkElementById("btnCancelCredential");
            wait.until(ExpectedConditions.visibilityOf(btnCancelCredential));
            wait.until(ExpectedConditions.elementToBeClickable(btnCancelCredential));
            btnCancelCredential.click();
        } catch (TimeoutException e) {
            System.out.println("Timed out waiting for credential cancel button to be clickable");
            // Button either not found or not clickable within timeout - silently continue
        }
    }

    public void updatePassword(String password) {
        try {
            checkElementById("inputCredentialPassword");
            WebElement credentialPasswordInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("inputCredentialPassword")));
            credentialPasswordInput.clear();
            credentialPasswordInput.sendKeys(password);

            checkElementById("btnSaveCredential");
            WebElement createCredentialButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("btnSaveCredential")));
            createCredentialButton.click();
        } catch (Exception e) {
            System.out.println("Skipping password update - elements not found: " + e.getMessage());
        }
    }

    public void waitForToastSuccess() {
        checkElementById("toast-toastSuccess");
    }

    public void takeScreenshot(String name) {
        try {
            // Create screenshots directory if it doesn't exist
            Path screenshotsDir = Paths.get(SCREENSHOTS_DIR);
            if (!Files.exists(screenshotsDir)) {
                Files.createDirectories(screenshotsDir);
            }

            // Generate timestamp for unique filename
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("%s/%s_%s.png", SCREENSHOTS_DIR, name, timestamp);

            // Take screenshot
            TakesScreenshot ts = (TakesScreenshot) browser;
            File screenshot = ts.getScreenshotAs(OutputType.FILE);
            
            // Save screenshot
            Files.copy(screenshot.toPath(), Paths.get(filename));
            System.out.println("Screenshot saved: " + Paths.get(filename).toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save screenshot: " + e.getMessage());
        }
    }

    public void takeScreenshotOnError(String name, Throwable error) {
        try {
            // Create screenshots directory if it doesn't exist
            Path screenshotsDir = Paths.get(SCREENSHOTS_DIR);
            if (!Files.exists(screenshotsDir)) {
                Files.createDirectories(screenshotsDir);
            }

            // Generate timestamp for unique filename
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("%s/ERROR_%s_%s.png", SCREENSHOTS_DIR, name, timestamp);

            // Take screenshot
            TakesScreenshot ts = (TakesScreenshot) browser;
            File screenshot = ts.getScreenshotAs(OutputType.FILE);
            
            // Save screenshot
            Files.copy(screenshot.toPath(), Paths.get(filename));
            System.out.println("Error screenshot saved: " + Paths.get(filename).toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save error screenshot: " + e.getMessage());
        }
    }
}