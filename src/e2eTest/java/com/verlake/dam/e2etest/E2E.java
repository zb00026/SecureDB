package com.verlake.dam.e2etest;

import io.github.bonigarcia.wdm.WebDriverManager;
import io.github.cdimascio.dotenv.Dotenv;

import liquibase.resource.FileSystemResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
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
        // Load environment based on profile
        String activeProfile = System.getProperty("test.profile", "dev");
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

    // Wipe all data before running Audit E2E Test
    // Truncate users, audit_trails, user_roles, s3_bucket_settings
    // add admin user to users table for initial access
    protected void executeLiquibaseChangelog(String changelogFile) {
        String url = getEnvVariable("MYSQL_URL");
        String username = getEnvVariable("MYSQL_USERNAME");
        String password = getEnvVariable("MYSQL_PASSWORD");


        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            
            // Create composite resource accessor
            String projectDir = System.getProperty("user.dir");
            String resourcesDir = projectDir + "/src/main/resources";
            CompositeResourceAccessor resourceAccessor = new CompositeResourceAccessor(
                new DirectoryResourceAccessor(new File(resourcesDir)),
                new ClassLoaderResourceAccessor()
            );
            
            Liquibase liquibase = new Liquibase(
                    changelogFile,
                    resourceAccessor,
                    database
            );

            // Clear the DATABASECHANGELOG table first
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
                stmt.execute("DELETE FROM DATABASECHANGELOG WHERE ID = 'wipe-data'");
                stmt.execute("DELETE FROM DATABASECHANGELOG WHERE ID = 'seed-e2e-data'");
                stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            
            liquibase.setChangeLogParameter("wipe.data", "true");
            liquibase.update("e2e-data");
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute changelog: " + changelogFile, e);
        }
    }
}
