package com.verlake.dam.e2etest.test;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.OutputType;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TestWatcherExtension implements TestWatcher {
    private static final String SCREENSHOTS_DIR = "screenshots";

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        String testName = context.getDisplayName();
        takeScreenshotOnError(testName, cause);
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        String testName = context.getDisplayName();
        takeScreenshotOnError(testName + "_aborted", cause);
    }

    private void takeScreenshotOnError(String name, Throwable error) {
        try {
            WebDriver browser = BaseE2ETest.getStaticBrowser();
            if (browser == null) return;

            // Create screenshots directory if it doesn't exist
            Path screenshotsDir = Paths.get(SCREENSHOTS_DIR);
            if (!Files.exists(screenshotsDir)) {
                Files.createDirectories(screenshotsDir);
            }

            // Generate timestamp for unique filename
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("%s/ERROR_%s_%s.png", SCREENSHOTS_DIR, name, timestamp);

            // Take screenshot
            TakesScreenshot ts = (TakesScreenshot) browser;
            File screenshot = ts.getScreenshotAs(OutputType.FILE);
            
            // Save screenshot
            Files.copy(screenshot.toPath(), Paths.get(filename));
            System.out.println("Error screenshot saved: " + Paths.get(filename).toAbsolutePath());
            System.out.println("Error details: " + error.getMessage());
            error.printStackTrace();
        } catch (IOException e) {
            System.err.println("Failed to save error screenshot: " + e.getMessage());
        }
    }
} 