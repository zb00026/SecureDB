package com.verlake.dam.e2etest.pageobjects;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.List;
import java.util.Random;

public class DeveloperAssetPage extends BasePage {

    @FindBy(className = "chakra-spinner")
    public WebElement chakraSpinner;

    @FindBy(id = "txtAccessRequestReason")
    public WebElement txtAccessRequestReason;

    @FindBy(id = "btnAccessRequest")
    public WebElement btnAccessRequest;

    private String keycloakAuthUrl;
    private String baseUrl;

    public DeveloperAssetPage(WebDriver browser, String baseUrl) {
        super(browser);
        PageFactory.initElements(browser, this);
        this.baseUrl = baseUrl;
    }

    public void navigate() {
        browser.navigate().to(baseUrl + "/developer/assets");
    }

    public WebElement getAssetRow(String assetName, String dbType, String hostAddress, String portNumber, String dbName) {
        String xpath = String.format("//table[@id='tblAssets']/tbody/tr[td[text()='%s'] and td[text()='%s'] and td[text()='%s'] and td[text()='%s'] and td[text()='%s']]", assetName, dbType, hostAddress, portNumber, dbName);
        WebElement assetRow = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(xpath)));
        assetRow.click();
        return assetRow;
    }

    public void clickRequestAccessAssetByCriteria(String assetName, String dbType, String hostAddress, String portNumber, String dbName) {
        WebElement assetRow = getAssetRow(assetName, dbType, hostAddress, portNumber, dbName);

        // Find the delete button
        WebElement requestButton = assetRow.findElement(
                By.xpath(".//button[contains(text(), 'Request Access') or .//FormattedMessage[@id='text.request_access']]"));
        requestButton.click();
    }

    public void updateApprovedAccessRequestPassword(String assetName, String dbType, String hostAddress, String portNumber, String dbName, String credentialPassword) {
        WebElement assetRow = getAssetRow(assetName, dbType, hostAddress, portNumber, dbName);

        // Find the update password text element
        WebElement updatePasswordElement = assetRow.findElement(
                By.xpath(".//p[contains(@class, 'btnUpdatePassword') and text()='Update Password']"));
        updatePasswordElement.click();

        updatePassword(credentialPassword);

        wait.until(ExpectedConditions.invisibilityOf(chakraSpinner));
    }

    public void requestAccess() throws InterruptedException {
        wait.until(ExpectedConditions.visibilityOf(txtAccessRequestReason));
        txtAccessRequestReason.clear();
        txtAccessRequestReason.sendKeys("To Test E2E Tests");
        Thread.sleep(3000);
        checkRandomCheckboxes();
        btnAccessRequest.click();
    }

    public void checkRandomCheckboxes() {
        // Find all checkbox control elements (the visible part of Chakra UI checkboxes)
        List<WebElement> checkboxControls = browser.findElements(By.xpath("//span[contains(@class, 'chakra-checkbox__control')]"));
        
        // Create a random number generator
        Random random = new Random();
        int nChkCnt = 0;
        
        // Check random checkboxes
        for (WebElement checkboxControl : checkboxControls) {
            if (nChkCnt > 10) break;
            if (random.nextBoolean()) {  // 50% chance to check each checkbox
                try {
                    // Wait for the element to be clickable
                    wait.until(ExpectedConditions.elementToBeClickable(checkboxControl));
                    // Click the visible control element
                    checkboxControl.click();
                    Thread.sleep(500); // Small delay to prevent rapid clicking
                    nChkCnt++;
                } catch (Exception e) {
                    System.out.println("Could not click checkbox: " + e.getMessage());
                }
            }
        }
    }
}
