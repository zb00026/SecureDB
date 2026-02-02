package com.verlake.dam.e2etest.pageobjects;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class AssetCredentialsPage extends BasePage {
    @FindBy(id = "lblAssetSetting")
    private WebElement assetTitle;

    @FindBy(className = "chakra-spinner")
    public WebElement chakraSpinner;

    @FindBy(id = "credentialDialogBody")
    private WebElement credentialInputForm;

    @FindBy(id = "btnConfirmRelinquish")
    private WebElement btnConfirmRelinquish;

    private final String baseUrl;

    public AssetCredentialsPage(WebDriver driver, String baseUrl) {
        super(driver);
        this.baseUrl = baseUrl;
    }


    public void navigateToAssetsPage() {
        browser.get(baseUrl + "/asset_owner");
    }

    private WebElement getCredentialButton(String assetName, String assetType, String hostAddress, String portNumber, String databaseName, String className) {
        String xpath = String.format("//table[@id='tblAssetCredentials']/tbody/tr[td[text()='%s'] and td[text()='%s'] and td[text()='%s'] and td[text()='%s'] and td[text()='%s']]//button[contains(@class, '%s')]", assetName, assetType, hostAddress, portNumber, databaseName, className);
        return wait.until(ExpectedConditions.elementToBeClickable(By.xpath(xpath)));
    }

    public void waitForPageToLoad(String assetName, String assetType, String hostAddress, String portNumber, String databaseName) {
        wait.until(ExpectedConditions.invisibilityOf(chakraSpinner));
        wait.until(ExpectedConditions.visibilityOf(assetTitle));

        WebElement credentialButton = getCredentialButton(assetName, assetType, hostAddress, portNumber, databaseName, "btn-set-credential");
        wait.until(ExpectedConditions.visibilityOf(credentialButton));
    }


    public void createCredential(String assetName, String assetDbType, String assetHostAddress, String portNumber, String databaseName, String credentialUserName, String credentialPassword) {
        WebElement credentialButton = getCredentialButton(assetName, assetDbType, assetHostAddress, portNumber, databaseName, "btn-set-credential");
        wait.until(ExpectedConditions.elementToBeClickable(credentialButton));
        credentialButton.click();

        checkElementById("inputCredentialUsername");
        WebElement credentialUsernameInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("inputCredentialUsername")));
        credentialUsernameInput.clear();
        credentialUsernameInput.sendKeys(credentialUserName);

        updatePassword(credentialPassword);

        wait.until(ExpectedConditions.invisibilityOf(chakraSpinner));
    }

    public void relinquishCredential(String assetName, String assetDbType, String assetHostAddress, String portNumber, String databaseName) throws InterruptedException {
        WebElement relinquishButton = getCredentialButton(assetName, assetDbType, assetHostAddress, portNumber, databaseName,"btn-relinquish-credential");
        wait.until(ExpectedConditions.elementToBeClickable(relinquishButton));
        relinquishButton.click();

        wait.until(ExpectedConditions.elementToBeClickable(btnConfirmRelinquish));
        btnConfirmRelinquish.click();
        Thread.sleep(5000);

        wait.until(ExpectedConditions.invisibilityOf(chakraSpinner));
        wait.until(ExpectedConditions.invisibilityOf(relinquishButton));
    }
}