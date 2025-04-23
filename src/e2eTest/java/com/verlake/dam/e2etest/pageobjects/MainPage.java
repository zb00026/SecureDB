package com.verlake.dam.e2etest.pageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class MainPage extends BasePage {
    
    public MainPage(WebDriver browser) {
        super(browser);
        PageFactory.initElements(browser, this);
    }

    @FindBy(id = "btnLogin")
    public WebElement keycloakButton;

    @FindBy(className = "chakra-spinner")
    public WebElement chakraSpinner;

    public void navigate() {
        driver.navigate().to("/");
    }

    public void clickKeycloakButton() {
        wait.until(ExpectedConditions.elementToBeClickable(keycloakButton));
        wait.until(ExpectedConditions.invisibilityOf(chakraSpinner));
        checkElementById("btnLogin");
        keycloakButton.click();
    }
}
