package com.verlake.dam.e2etest;

import com.google.common.collect.ImmutableMap;
import io.github.bonigarcia.wdm.WebDriverManager;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;


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

        ChromeOptions options = new ChromeOptions();
        
        // Add existing options
        options.addArguments("--disable-web-security");
        options.addArguments("--allow-running-insecure-content");
        options.addArguments("--headless");
        
        // Add notification permissions
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("profile.default_content_setting_values.notifications", 1); // 1 - Allow, 2 - Block
        options.setExperimentalOption("prefs", prefs);

        // Add additional Chrome arguments for notifications
        options.addArguments("--use-fake-ui-for-media-stream");
        options.addArguments("--use-fake-device-for-media-stream");
        options.addArguments("--allow-file-access");
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("--start-maximized");

        // Add existing capabilities
        Map<String, Object> chromePrefs = new HashMap<>();
        chromePrefs.putAll(ImmutableMap.of(
            "profile.default_content_settings.popups", 0,
            "profile.default_content_setting_values.notifications", 1,
            "profile.default_content_setting_values.automatic_downloads", 1
        ));
        options.setExperimentalOption("prefs", chromePrefs);

        browser = new ChromeDriver(options);
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
