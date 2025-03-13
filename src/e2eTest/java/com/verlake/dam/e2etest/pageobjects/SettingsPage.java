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

    public SettingsPage(WebDriver driver) {
        super(driver);
    }

    public void waitForPageToLoad() {
        wait.until(ExpectedConditions.visibilityOf(settingsTitle));
    }

    public void configureS3Bucket(String bucketName) {
        wait.until(ExpectedConditions.elementToBeClickable(s3BucketInput));
        s3BucketInput.clear();
        s3BucketInput.sendKeys(bucketName);
        saveButton.click();
        //wait until success message is showing
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("toast-toastSuccess")));
    }
} 