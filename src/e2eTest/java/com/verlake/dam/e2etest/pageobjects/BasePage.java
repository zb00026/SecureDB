package com.verlake.dam.e2etest.pageobjects;

import org.openqa.selenium.*;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;

public abstract class BasePage {
    protected final WebDriver driver;
    protected final WebDriverWait wait;

    public BasePage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(60));
        PageFactory.initElements(driver, this);
    }

    public void checkElementById(String id) {
        wait.until(driver -> {
            try {
                String pgSrc = driver.getPageSource();

                System.out.println("Page source contains \"" + id + "\" : " +
                        pgSrc.contains(id));
                System.out.println(driver.getCurrentUrl());

                // Try finding element different ways
                WebElement toast = driver.findElement(By.id(id));

                System.out.println("Element properties:");
                System.out.println("- Is Displayed: " + toast.isDisplayed());
                System.out.println("- Is Enabled: " + toast.isEnabled());
                System.out.println("- Location: " + toast.getLocation());
                System.out.println("- Size: " + toast.getSize());
                System.out.println("- CSS Display: " + toast.getCssValue("display"));
                System.out.println("- CSS Visibility: " + toast.getCssValue("visibility"));
                System.out.println("- CSS Opacity: " + toast.getCssValue("opacity"));

                return pgSrc.contains(id);
            } catch (Exception e) {
                System.out.println("Exception type: " + e.getClass().getName());
                System.out.println("Exception message: " + e.getMessage());
                return false;
            }
        });
    }

    public void waitForToastSuccess() {
        checkElementById("toast-toastSuccess");
    }
}