package com.verlake.dam.e2etest.packageobjects;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

public class MainPage {
    public MainPage(WebDriver browser) {
        PageFactory.initElements(browser, this);
    }

    @FindBy(tagName = "button")
    public WebElement keycloakButton;
}
