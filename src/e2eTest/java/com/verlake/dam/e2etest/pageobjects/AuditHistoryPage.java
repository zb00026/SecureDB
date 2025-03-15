package com.verlake.dam.e2etest.pageobjects;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import java.util.List;

public class AuditHistoryPage extends BasePage {
    @FindBy(id = "audit-menu")
    private WebElement auditMenu;

    @FindBy(id = "tableAuditTrail")
    private WebElement auditTable;

    @FindBy(id = "btnSearchAuditTrail")
    private WebElement btnSearchAuditTrail;

    @FindBy(className = "chakra-spinner")
    public WebElement chakraSpinner;

    private String baseUrl;

    public AuditHistoryPage(WebDriver driver, String baseUrl) {
        super(driver);
        this.baseUrl = baseUrl;
    }

    public void navigateToAuditHistory() {
        driver.get(baseUrl + "/auditor/audit-trail");
        wait.until(ExpectedConditions.invisibilityOf(chakraSpinner));
        wait.until(ExpectedConditions.visibilityOf(btnSearchAuditTrail));
        wait.until(ExpectedConditions.visibilityOf(auditTable));
    }

    public List<WebElement> getAuditEntries() {
        return auditTable.findElements(By.tagName("tr"));
    }

    public boolean verifyAuditEntry(String action, String target) {
        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//tr[contains(., '" + action + "') and contains(., '" + target + "')]")));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
} 