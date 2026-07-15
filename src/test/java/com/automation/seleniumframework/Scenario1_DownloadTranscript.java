package com.automation.seleniumframework;

import com.automation.seleniumframework.base.BaseTest;
import com.automation.seleniumframework.utils.ExcelUtil;
import com.automation.seleniumframework.utils.ExtentReportManager;
import com.automation.seleniumframework.utils.ScreenshotUtil;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Scenario 1: Download the latest transcript.
 *
 * Real flow, confirmed against a live run:
 *   1. Log into the main NEU portal (Microsoft-style login: i0116/i0118/idSIButton9)
 *   2. Student Hub -> Resources -> Academics, Classes & Registration -> My Transcript
 *      (opens a NEW browser tab)
 *   3. That new tab is a SEPARATE Banner login (different credentials, Duo-protected)
 *   4. Select Transcript Level + Type -> Submit
 *   5. Print the page via Chrome's native print dialog (Robot-driven keystrokes) -> Save as PDF
 *   6. Verify the PDF actually landed in the Downloads folder
 *
 * All data comes from TestData.xlsx - Login sheet (portal creds) and
 * Scenario1_Transcript sheet (transcript level/type + separate Banner creds).
 */
public class Scenario1_DownloadTranscript extends BaseTest {

    private static final String SCENARIO_NAME = "Scenario1_DownloadTranscript";
    private static final String DATA_FILE = "src/test/resources/testdata/TestData.xlsx";

    // Student Hub navigation locators
    private final By resourcesTab = By.xpath("//a[@data-testid='link-resources']");
    private final By academicsText = By.xpath("//span[contains(text(),'Academics, Classes')]");
    private final By transcriptsLink = By.xpath("//a[contains(text(),'Unofficial Transcript')]");


    @Test(priority = 1)
    public void verifyTranscriptDownload() throws Exception {
        test = ExtentReportManager.createTest(SCENARIO_NAME);

        Map<String, String> login = ExcelUtil.readSheet(DATA_FILE, "Login").get(0);
        Map<String, String> data = ExcelUtil.readSheet(DATA_FILE, "Scenario1_Transcript").get(0);

        String portalUrl = login.get("PortalUrl");
        String username = login.get("Username");
        String password = login.get("Password");
        String transcriptLevel = data.get("TranscriptLevel");
        String transcriptType = data.get("TranscriptType");
        String transcriptUsername = data.get("TranscriptUsername");
        String transcriptPassword = data.get("TranscriptPassword");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(40));

        // ---- Step 1: Login to main NEU portal ----
        executeStep(SCENARIO_NAME, "01_loginToPortal", () ->
                loginToNEU(portalUrl, username, password));
        demoPause(2);

        // ---- Step 2: Navigate Student Hub -> Resources -> Academics -> My Transcript ----
        executeStep(SCENARIO_NAME, "02_clickResources", () -> {
            WebElement resources = wait.until(ExpectedConditions.elementToBeClickable(resourcesTab));
            resources.click();
        });
        demoPause(2);

        executeStep(SCENARIO_NAME, "03_clickAcademics", () -> {
            WebElement academics = wait.until(ExpectedConditions.visibilityOfElementLocated(academicsText));
            academics.click();
        });
        demoPause(2);

        // "My Transcript" opens a new tab - handle window switching
        Set<String> beforeClickHandles = driver.getWindowHandles();
        executeStep(SCENARIO_NAME, "04_clickMyTranscript", () -> {
            WebElement transcripts = wait.until(ExpectedConditions.elementToBeClickable(transcriptsLink));
            transcripts.click();
        });
        wait.until(d -> d.getWindowHandles().size() > beforeClickHandles.size());
        for (String handle : driver.getWindowHandles()) {
            if (!beforeClickHandles.contains(handle)) {
                driver.switchTo().window(handle);
                break;
            }
        }
        demoPause(2);

        // ---- Step 3: Separate Banner login (with Duo) ----
        executeStep(SCENARIO_NAME, "05_loginToBannerTranscriptSystem", () -> {
            WebElement userField = wait.until(ExpectedConditions.elementToBeClickable(By.id("username")));
            WebElement passField = wait.until(ExpectedConditions.elementToBeClickable(By.id("password")));
            WebElement loginButton = wait.until(
                    ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(),'Log In')]")));

            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].value=arguments[1];", userField, transcriptUsername);
            js.executeScript("arguments[0].value=arguments[1];", passField, transcriptPassword);
            loginButton.click();
        });

        handleDuoPushIfPresent();

        // ---- Step 4: Select transcript level/type and submit ----
        executeStep(SCENARIO_NAME, "06_selectTranscriptOptions", () -> {
            selectAngularDropdown("transcriptLevelSelection", transcriptLevel);
            selectAngularDropdown("transcriptTypeSelection", transcriptType);
        });

        executeStep(SCENARIO_NAME, "07_waitForTranscriptToRender", () -> {
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), "Curriculum Information"),
                    ExpectedConditions.textToBePresentInElementLocated(By.tagName("body"), "Student Information")
            ));
            try {
                Thread.sleep(2000); // brief buffer for any remaining rendering/animations
            } catch (InterruptedException ignored) {
            }
        });

        // ---- Step 5: Print -> Save as PDF via native Chrome dialog ----
        ScreenshotUtil.capture(driver, SCENARIO_NAME, "08_beforePrintDialog", "before");
        printCurrentPageToPdf();
        ScreenshotUtil.capture(driver, SCENARIO_NAME, "08_afterPrintDialog", "after");

        // ---- Step 6: Verify the PDF landed in Downloads ----
        boolean fileDownloaded = waitForTranscriptPdf();

        ExtentReportManager.logResult(
                "Transcript PDF downloads to local Desktop folder",
                "A PDF containing 'academic' and 'transcript' in its name appears in Desktop within 30s",
                fileDownloaded ? "Transcript PDF found in Desktop" : "Transcript PDF NOT found in Desktop",
                fileDownloaded
        );

        Assert.assertTrue(fileDownloaded, "Transcript PDF was not downloaded.");
    }

    /**
     * Drives Chrome's native print dialog via OS-level keystrokes (java.awt.Robot),
     * since Selenium cannot interact with browser-native (non-DOM) dialogs.
     *
     * FRAGILE - depends on: right-click context menu item order, number of Tab
     * stops in the print dialog, and OS default save location. Re-verify the
     * loop counts below (currently 4 down-arrows to reach Print, 9 tabs to
     * reach the Print button) if this breaks on a different machine.
     */
    private void printCurrentPageToPdf() throws Exception {
        Robot robot = new Robot();

        new Actions(driver).contextClick(driver.findElement(By.tagName("body"))).perform();
        Thread.sleep(1500);

        for (int i = 0; i < 4; i++) {
            robot.keyPress(KeyEvent.VK_DOWN);
            robot.keyRelease(KeyEvent.VK_DOWN);
            Thread.sleep(200);
        }
        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);
        Thread.sleep(3000); // print preview loading

        for (int i = 0; i < 7; i++) {
            robot.keyPress(KeyEvent.VK_SHIFT);
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_SHIFT);
            Thread.sleep(300);
        }
        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);
        Thread.sleep(800);

        typeString(robot, "Save as PDF");
        Thread.sleep(500);
        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);
        Thread.sleep(1500);

        for (int i = 0; i < 5; i++) {
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);
            Thread.sleep(300);
        }

        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);
        Thread.sleep(4000);

        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);
        Thread.sleep(4000);

        robot.keyPress(KeyEvent.VK_ENTER);
        robot.keyRelease(KeyEvent.VK_ENTER);
        Thread.sleep(4000);
    }

    private void typeString(Robot robot, String text) throws InterruptedException {
        for (char c : text.toCharArray()) {
            int keyCode = KeyEvent.getExtendedKeyCodeForChar(c);
            robot.keyPress(keyCode);
            robot.keyRelease(keyCode);
            Thread.sleep(50);
        }
    }

    /** Polls the Downloads folder for up to 30s for a freshly-created transcript PDF. */
    private boolean waitForTranscriptPdf() throws InterruptedException {
        String home = System.getProperty("user.home");
        File[] candidateDirs = {
                new File(home + "/Downloads"),
                new File(home + "/Desktop"),
                new File(home + "/Documents")
        };

        for (int i = 0; i < 30; i++) {
            for (File dir : candidateDirs) {
                if (dir == null || !dir.isDirectory()) continue;

                File[] files = dir.listFiles((d, name) ->
                        name.toLowerCase().contains("academic")
                                && name.toLowerCase().contains("transcript")
                                && name.toLowerCase().endsWith(".pdf"));

                if (files != null && files.length > 0) {
                    File mostRecent = files[0];
                    for (File f : files) {
                        if (f.lastModified() > mostRecent.lastModified()) {
                            mostRecent = f;
                        }
                    }
                    if ((System.currentTimeMillis() - mostRecent.lastModified()) < 60000) {
                        System.out.println("Found transcript PDF at: " + mostRecent.getAbsolutePath());
                        return true;
                    }
                }
            }
            Thread.sleep(1000);
        }
        return false;
    }

    private void selectAngularDropdown(String containerId, String optionText) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
        By selectedValue = By.cssSelector("#" + containerId + " .select2-choice");

        // EXACT-text match, not contains(): a loose contains('Graduate') also
        // matches 'Undergraduate' and can leave the widget on its 'All Levels'
        // default. Also look in select2's body-level '.select2-drop', since the
        // option list is often rendered there rather than inside the container.
        By exactOption = By.xpath(
                "//div[@id='" + containerId + "']//li[normalize-space(.)='" + optionText + "']"
              + " | //div[contains(@class,'select2-drop')]//li[normalize-space(.)='" + optionText + "']");

        for (int attempt = 1; attempt <= 2; attempt++) {
            // Open the dropdown.
            wait.until(ExpectedConditions.elementToBeClickable(selectedValue)).click();

            // Native click - select2 reacts to real mouse events, not JS clicks.
            wait.until(ExpectedConditions.elementToBeClickable(exactOption)).click();

            // Verify the widget actually shows the chosen option (retry once).
            try {
                wait.until(ExpectedConditions.textToBePresentInElementLocated(selectedValue, optionText));
                return;
            } catch (Exception notSelectedYet) {
                if (attempt == 2) {
                    String actual = driver.findElement(selectedValue).getText();
                    throw new IllegalStateException("Failed to select '" + optionText + "' in #"
                            + containerId + " - still shows '" + actual + "'");
                }
            }
        }
    }


    private void demoPause(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
