package com.verlake.dam.e2etest.pageobjects;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class AssetsPage extends BasePage {
    @FindBy(id = "lblAssetSetting")
    private WebElement assetTitle;

    @FindBy(id = "btnCreateAsset")
    private WebElement btnCreateAsset;

    @FindBy(className = "chakra-spinner")
    public WebElement chakraSpinner;

    @FindBy(id = "flexAssetTypeForm")
    private WebElement assetTypeForm;

    @FindBy(id = "flexAssetDetailForm")
    private WebElement assetDetailForm;

    private String baseUrl;

    public AssetsPage(WebDriver driver, String baseUrl) {
        super(driver);
        this.baseUrl = baseUrl;
    }


    public void navigateToAssetsPage() {
        browser.get(baseUrl + "/admin/assets");
        cancelTemporaryPassword();
    }

    public void waitForPageToLoad() {
        wait.until(ExpectedConditions.invisibilityOf(chakraSpinner));
        wait.until(ExpectedConditions.visibilityOf(btnCreateAsset));
        cancelTemporaryPassword();
    }

    public void selectAssetByCriteria(String assetName, String dbType, String hostAddress, String portNumber, String dbName) {
        String xpath = String.format("//table[@id='tblAssets']/tbody/tr[td[text()='%s'] and td[text()='%s'] and td[text()='%s'] and td[text()='%s'] and td[text()='%s']]", assetName, dbType, hostAddress, portNumber, dbName);
        WebElement assetRow = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(xpath)));
        assetRow.click();
    }

    public void checkAssetOwnerByCriteria(String firstName, String lastName, String email) {
        String inputChkXpath = String.format("//table[@id='tblAssetOwners']/tbody/tr[td[text()='%s'] and td[text()='%s'] and td[text()='%s']]//input[@type='checkbox']", firstName, lastName, email);
        String btnChkXpath = String.format("//table[@id='tblAssetOwners']/tbody/tr[td[text()='%s'] and td[text()='%s'] and td[text()='%s']]//label[contains(@class, 'chakra-checkbox')]", firstName, lastName, email);
        WebElement userCheckboxInput = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(inputChkXpath)));
        WebElement userCheckboxButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(btnChkXpath)));
        if (!userCheckboxInput.isSelected()) {
            System.out.println("User checkbox is not selected");
            userCheckboxButton.click();
            wait.until(ExpectedConditions.invisibilityOf(chakraSpinner));
        } else {
            System.out.println("User checkbox is selected");
        }
    }

    public void addNewAsset(String assetName, String databaseType, String hostAddress, String portNumber, String dbName, String description) {
        wait.until(ExpectedConditions.elementToBeClickable(btnCreateAsset));
        btnCreateAsset.click();
        wait.until(ExpectedConditions.visibilityOf(assetTypeForm));

        // Select Database type with explicit wait
        // Select Database type with explicit wait
        WebElement dbsSelect = wait.until(ExpectedConditions.elementToBeClickable(By.id("selectDBType")));
        dbsSelect.click();

        // Wait for the dropdown options to be visible
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//option[contains(@class, 'dropdown-db-option')]")));

        // Wait for the specific database type option to be clickable and click it
        WebElement dbOption = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//option[contains(text(), '" + databaseType + "') and contains(@class, 'dropdown-db-option')]")));
        dbOption.click();

        wait.until(ExpectedConditions.visibilityOf(assetDetailForm));

        WebElement assetNameInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("inputAssetName")));
        assetNameInput.clear();
        assetNameInput.sendKeys(assetName);

        // Add input for host address
        WebElement hostAddressInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("inputHostAddress")));
        hostAddressInput.clear();
        hostAddressInput.sendKeys(hostAddress);

        // Add input for port number
        WebElement portNumberInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("inputPortNumber")));
        portNumberInput.clear();
        portNumberInput.sendKeys(portNumber);

        // Add input for database name
        WebElement databaseNameInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("inputDatabaseName")));
        databaseNameInput.clear();
        databaseNameInput.sendKeys(dbName);

        // Add input for description
        WebElement descriptionInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("inputDescription")));
        descriptionInput.clear();
        descriptionInput.sendKeys(description);

        // Click the save button
        WebElement saveButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("btnSaveAsset")));
        try {
            // First attempt: Wait and click using WebDriverWait
            saveButton.click();
            Thread.sleep(500); // Small pause
        } catch (Exception e) {
            // Second attempt: Find button again and click
            wait.until(ExpectedConditions.elementToBeClickable(saveButton));
            saveButton.click();
        }

        // Wait for spinner to disappear and success toast to appear
        wait.until(ExpectedConditions.invisibilityOf(chakraSpinner));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("toast-toastSuccess")));
    }

    public void setOwnerOfAsset(String assetName, String dbType, String hostAddress, String portNumber, String dbName,
                                String ownerFirstName, String ownerLastName, String ownerEmail) {
        wait.until(ExpectedConditions.elementToBeClickable(btnCreateAsset));
        btnCreateAsset.click();
        wait.until(ExpectedConditions.visibilityOf(assetTypeForm));

        selectAssetByCriteria(assetName, dbType, hostAddress, portNumber, dbName);
        checkAssetOwnerByCriteria(ownerFirstName, ownerLastName, ownerEmail);

        // Wait for spinner to disappear and success toast to appear
        wait.until(ExpectedConditions.invisibilityOf(chakraSpinner));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("toast-toastSuccess")));
    }
}