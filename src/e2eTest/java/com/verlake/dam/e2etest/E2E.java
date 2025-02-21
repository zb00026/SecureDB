package com.verlake.dam.e2etest;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;


@ActiveProfiles("test")
public abstract class E2E {

    protected static WebDriver browser;

//    protected final String baseUrl = "https://qa.hagrids.com/";

    static {
        WebDriverManager.chromedriver().setup();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry propertyRegistry) {
        propertyRegistry.add("FRONTEND_URL", () -> System.getenv("FRONTEND_URL"));

    }

    @BeforeAll
    static void beforeAll() {
        browser = new ChromeDriver(new ChromeOptions().addArguments("--headless"));
    }

    @AfterAll
    static void afterAll() {
        browser.quit();
    }
}
