package com.automation.seleniumframework.utils;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

/**
 * Handles screenshot capture. Assignment requirement: take screenshots
 * before and after every step, saved into a folder named after the
 * scenario.
 *
 * Folder layout produced:
 *   screenshots/
 *     Scenario1_DownloadTranscript/
 *       01_login_before.png
 *       01_login_after.png
 *       02_studentHub_before.png
 *       ...
 */
public class ScreenshotUtil {

    private static final String BASE_DIR = "screenshots";

    /**
     * Captures a screenshot for the given scenario + step name + before/after marker.
     *
     * @param driver       active WebDriver instance
     * @param scenarioName folder name for this scenario, e.g. "Scenario1_DownloadTranscript"
     * @param stepName     short description of the step, e.g. "01_login"
     * @param marker       "before" or "after"
     * @return the absolute path of the saved screenshot file
     */
    public static String capture(WebDriver driver, String scenarioName, String stepName, String marker) {
        try {
            String folderPath = BASE_DIR + File.separator + scenarioName;
            Files.createDirectories(Paths.get(folderPath));

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
            String fileName = String.format("%s_%s_%s.png", stepName, marker, timestamp);
            File destination = new File(folderPath + File.separator + fileName);

            File srcFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(srcFile.toPath(), destination.toPath());

            return destination.getAbsolutePath();
        } catch (IOException e) {
            System.err.println("Failed to capture screenshot: " + e.getMessage());
            return null;
        }
    }
}
