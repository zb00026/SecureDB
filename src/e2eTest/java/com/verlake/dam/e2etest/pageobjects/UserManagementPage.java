package com.verlake.dam.e2etest.pageobjects;

import org.openqa.selenium.*;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class UserManagementPage extends BasePage {
    private final String baseUrl;

    @FindBy(id = "txtUsersTitle")
    private WebElement usersTitle;

    @FindBy(id = "btnSaveUser")
    private WebElement btnSaveUser;

    @FindBy(id = "btnCreateUser")
    private WebElement btnCreateUser;


    @FindBy(id = "flexUserForm")
    private WebElement userForm;

    @FindBy(className = "chakra-spinner")
    public WebElement chakraSpinner;

    public UserManagementPage(WebDriver driver, String baseUrl) {
        super(driver);

        this.baseUrl = baseUrl;
    }

    public void navigateToUserManagement() {
        browser.get(baseUrl + "/hagrids_admin/users");
        wait.until(ExpectedConditions.invisibilityOf(chakraSpinner));
        wait.until(ExpectedConditions.visibilityOf(usersTitle));
    }

    public void createUser(String email, String firstName, String lastName, String password, String role)
            throws InterruptedException {
        try {
            navigateToUserManagement();
            wait.until(ExpectedConditions.visibilityOf(btnCreateUser));
            btnCreateUser.click();
            // Wait for form elements to be visible and interactable
            wait.until(ExpectedConditions.visibilityOf(userForm));

            // Fill in user form with explicit waits
            WebElement emailInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("inputEmail")));
            emailInput.clear();
            emailInput.sendKeys(email);

            WebElement firstNameInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("inputFirstName")));
            firstNameInput.clear();
            firstNameInput.sendKeys(firstName);

            WebElement lastNameInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("inputLastName")));
            lastNameInput.clear();
            lastNameInput.sendKeys(lastName);

            WebElement passwordInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("inputPassword")));
            passwordInput.clear();
            passwordInput.sendKeys(password);

            // Select roles with explicit wait
            WebElement rolesSelect = wait.until(ExpectedConditions.elementToBeClickable(By.id("selectRoles")));
            rolesSelect.click();

            // Wait for role options to be visible and click the specific role
            WebElement roleOption = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//div[contains(text(), '" + role + "')]")));
            roleOption.click();

            // Try different approaches to click the save button
            try {
                // First attempt: Wait and click using WebDriverWait
                wait.until(ExpectedConditions.elementToBeClickable(btnSaveUser));
                Thread.sleep(500); // Small pause
                btnSaveUser.click();
            } catch (Exception e) {
                // Second attempt: Find button again and click
                WebElement saveButton = browser.findElement(By.id("btnSaveUser"));
                wait.until(ExpectedConditions.elementToBeClickable(saveButton));
                saveButton.click();
            }

            // Wait for spinner to disappear and success toast to appear
            wait.until(ExpectedConditions.invisibilityOf(chakraSpinner));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("toast-toastSuccess")));

            // Add small delay to ensure form is reset
            Thread.sleep(1000);
        } catch (Exception e) {
            System.out.println("Failed to create user: " + email);
            System.out.println("Error: " + e.getMessage());
            throw e;
        }
    }

    public void modifyUser(String email, String newFirstName, String newLastName) {
        navigateToUserManagement();
        // Find and click user row to edit
        WebElement userRow = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//tr[contains(., '" + email + "')]")));
        userRow.click();

        // Clear and update fields
        WebElement firstNameInput = browser.findElement(By.id("inputFirstName"));
        firstNameInput.clear();
        firstNameInput.sendKeys(newFirstName);

        WebElement lastNameInput = browser.findElement(By.id("inputLastName"));
        lastNameInput.clear();
        lastNameInput.sendKeys(newLastName);

        // Save changes
        btnSaveUser.click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("toast-toastSuccess")));
    }

    public void deleteUser(String email) throws InterruptedException {
        browser.manage().window().setSize(new Dimension(1920, 1080));
        browser.manage().window().maximize();

        // Find user row
        String userRowXPath = "//tbody/tr[.//td[contains(text(), '" + email + "')]]";
        WebElement userRow = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath(userRowXPath)));

        // Find the delete button
        WebElement deleteButton = userRow.findElement(
                By.xpath(".//button[contains(text(), 'Delete') or .//FormattedMessage[@id='text.delete']]"));

        // Scroll into view
        ((JavascriptExecutor) browser).executeScript("arguments[0].scrollIntoView({block: 'center'});", deleteButton);
        Thread.sleep(500);

        // Ensure the button is clickable
        wait.until(ExpectedConditions.elementToBeClickable(deleteButton));
        deleteButton.click();

        // Wait for the DamAlertDialog to appear and be clickable
        wait.until(ExpectedConditions.elementToBeClickable(By.id("btnConfirmDeleteUser")))
                .click();

        // Wait for success toast
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("toast-toastSuccess")));
    }
}