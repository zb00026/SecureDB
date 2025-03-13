package com.verlake.dam.e2etest.pageobjects;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.*;
public class UserManagementPage extends BasePage {
    private final String baseUrl;

    @FindBy(id = "txtUsersTitle")
    private WebElement usersTitle;

    @FindBy(id = "btnSaveUser")
    private WebElement btnSaveUser;

    @FindBy(id = "flexUserForm")
    private WebElement userForm;

    public UserManagementPage(WebDriver driver, String baseUrl) {
        super(driver);
        this.baseUrl = baseUrl;
    }

    public void navigateToUserManagement() {
        driver.get(baseUrl + "/admin/users");
        wait.until(ExpectedConditions.visibilityOf(usersTitle));
        wait.until(ExpectedConditions.visibilityOf(btnSaveUser));
    }

    public void createUser(String email, String firstName, String lastName, String password, String role) {
        // Fill in user form
        driver.findElement(By.id("inputEmail")).sendKeys(email);
        driver.findElement(By.id("inputFirstName")).sendKeys(firstName);
        driver.findElement(By.id("inputLastName")).sendKeys(lastName);
        driver.findElement(By.id("inputPassword")).sendKeys(password);

        // Select roles (assuming multi-select)
        WebElement rolesSelect = driver.findElement(By.id("selectRoles"));
        rolesSelect.click();
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//div[contains(text(), '" + role + "')]"))).click();

        // Save user
        btnSaveUser.click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("toast-toastSuccess")));
    }

    public void modifyUser(String email, String newFirstName, String newLastName) {
        // Find and click user row to edit
        WebElement userRow = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//tr[contains(., '" + email + "')]")));
        userRow.click();


        // Clear and update fields
        WebElement firstNameInput = driver.findElement(By.id("inputFirstName"));
        firstNameInput.clear();
        firstNameInput.sendKeys(newFirstName);

        WebElement lastNameInput = driver.findElement(By.id("inputLastName"));
        lastNameInput.clear();
        lastNameInput.sendKeys(newLastName);

        // Save changes
        btnSaveUser.click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("toast-toastSuccess")));
    }

    public void deleteUser(String email) {
        // Find user row and click delete button
        WebElement userRow = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//tr[contains(., '" + email + "')]")));
        userRow.findElement(By.tagName("button")).click();
        // Wait for confirmation dialog and confirm deletion
        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//header[contains(text(), 'Delete User')]")));
        wait.until(ExpectedConditions.elementToBeClickable(driver.findElement(By.id("btnConfirmDeleteUser"))));
        driver.findElement(By.id("btnConfirmDeleteUser")).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("toast-toastSuccess")));
    }
}