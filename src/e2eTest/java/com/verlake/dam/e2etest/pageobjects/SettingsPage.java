package com.verlake.dam.e2etest.pageobjects;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class SettingsPage extends BasePage {
    @FindBy(id = "lblAuditLogStorage")
    private WebElement settingsTitle;

    @FindBy(id = "inputAuditLogStorage")
    private WebElement s3BucketInput;

    @FindBy(id = "btnApplyAuditLogStorage")
    private WebElement saveButton;

    @FindBy(className = "chakra-spinner")
    public WebElement chakraSpinner;

    private final String baseUrl;

    public SettingsPage(WebDriver driver, String baseUrl) {
        super(driver);
        this.baseUrl = baseUrl;
    }


    public void navigateToSettingsPage() {
        browser.get(baseUrl + "/hagrids_admin/settings");
    }

    public void waitForPageToLoad() {
        wait.until(ExpectedConditions.invisibilityOf(chakraSpinner));
        wait.until(ExpectedConditions.visibilityOf(settingsTitle));
    }

    public void configureS3Bucket(String bucketName) {

        wait.until(ExpectedConditions.elementToBeClickable(s3BucketInput));
        checkElementById("inputAuditLogStorage");
        s3BucketInput.clear();
        s3BucketInput.sendKeys(bucketName);
        saveButton.click();
        //wait until success message is showing
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("toast-toastSuccess")));
    }
} 