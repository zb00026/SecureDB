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

    @FindBy(tagName = "button")
    public WebElement keycloakButton;

    public void clickKeycloakButton() {
        wait.until(ExpectedConditions.elementToBeClickable(keycloakButton));
        keycloakButton.click();
    }
}
