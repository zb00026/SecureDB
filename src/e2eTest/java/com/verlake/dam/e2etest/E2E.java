package com.verlake.dam.e2etest;

import io.github.bonigarcia.wdm.WebDriverManager;
import io.github.cdimascio.dotenv.Dotenv;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.CompositeResourceAccessor;
import liquibase.resource.DirectoryResourceAccessor;
import liquibase.resource.ClassLoaderResourceAccessor;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;


@ActiveProfiles("test")
public abstract class E2E {

    protected static WebDriver browser;
    protected static Dotenv env;
    protected static String activeProfile;

    static {
        WebDriverManager.chromedriver().setup();
    }

    @BeforeAll
    static void beforeAll() {
        // Load environment based on profile
        activeProfile = System.getProperty("test.profile", "dev");
        String envFile = activeProfile.equals("dev") ? ".env.dev.e2etest" : ".env.qa.e2etest";
        env = Dotenv.configure()
                .directory("./")
                .filename(envFile)
                .load();
        browser = new ChromeDriver(new ChromeOptions().addArguments("--headless"));
    }

    @AfterAll
    static void afterAll() {
        if (browser != null) {
            browser.quit();
        }
    }

    protected String getEnvVariable(String key) {
        return env.get(key);
    }

}
