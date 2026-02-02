package com.verlake.dam.e2etest.pageobjects;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.assertj.core.api.Assertions.assertThat;

public class ApproveAccessRequestPage extends BasePage {

    @FindBy(className = "chakra-spinner")
    public WebElement chakraSpinner;


    private final String baseUrl;

    public ApproveAccessRequestPage(WebDriver browser, String baseUrl) {
        super(browser);
        PageFactory.initElements(browser, this);
        this.baseUrl = baseUrl;
    }

    public void navigate() {
        browser.navigate().to(baseUrl + "/asset_owner");
    }

    public void clickApproveAccessRequestByCriteria(String assetName, String assetDescription, String requestor, String email) throws InterruptedException {
        String xpath = String.format("//table[@id='tblRequestApprovals']/tbody/tr[td[text()='%s'] and td[text()='%s'] and td[text()='%s'] and td[text()='%s']]",
                assetName, assetDescription, requestor, email);
        
        // Now find and click the row
        WebElement approvalRow = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(xpath)));
        
        // Find the approve button
        WebElement approveButton = approvalRow.findElement(
                By.xpath(".//button[contains(text(), 'Approve') or .//FormattedMessage[@id='text.approve']]"));
        approvalRow.click();
        approveButton.click();

        WebElement finalApproveButton = wait.until(ExpectedConditions.elementToBeClickable(By.id("btnApprove")));
        finalApproveButton.click();
        wait.until(ExpectedConditions.invisibilityOf(chakraSpinner));
        Thread.sleep(1000);
        assertThat(browser.getPageSource() != null && browser.getPageSource().toLowerCase().contains("approved request access successfully")).isTrue();
    }

}
