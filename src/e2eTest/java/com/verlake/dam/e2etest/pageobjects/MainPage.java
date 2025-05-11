package com.verlake.dam.e2etest.pageobjects;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class MainPage extends BasePage {


    @FindBy(id = "btnLogin")
    public WebElement keycloakButton;

    @FindBy(className = "chakra-spinner")
    public WebElement chakraSpinner;

    String keycloakAuthUrl;
    String baseUrl;

    public MainPage(WebDriver browser, String baseUrl, String keycloakAuthUrl) {
        super(browser);
        PageFactory.initElements(browser, this);
        this.keycloakAuthUrl = keycloakAuthUrl;
    }

    public void navigate() {
        browser.navigate().to("/");
    }

    public void clickKeycloakButton() {

        wait.until(ExpectedConditions.elementToBeClickable(keycloakButton));
        wait.until(ExpectedConditions.invisibilityOf(chakraSpinner));
        System.out.println("Current URL: " + browser.getCurrentUrl());
        cancelTemporaryPassword();
        checkElementById("btnLogin");
        keycloakButton.click();
        System.out.println("Current URL After clicking btnLogin: " + browser.getCurrentUrl());
        wait.until(ExpectedConditions.urlMatches("^" + keycloakAuthUrl));
    }
}
