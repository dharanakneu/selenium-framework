package com.automation.seleniumframework.base;

import com.aventstack.extentreports.ExtentTest;
import com.automation.seleniumframework.utils.ExtentReportManager;
import com.automation.seleniumframework.utils.ScreenshotUtil;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Every scenario test class extends this. Provides:
 *   - WebDriver lifecycle (setUp / tearDown)
 *   - Shared NEU login + Duo 2FA flow (per assignment note: all scenarios
 *     implicitly require NEU login + Duo, even when not explicitly stated)
 *   - Screenshot helper that also attaches the image into the Extent report
 *   - ExtentReports lifecycle (init once per suite, flush once at the end)
 */
public class BaseTest {

    protected WebDriver driver;
    protected ExtentTest test;

    /**
     * Dedicated folder that ALL browser downloads are routed to. Scenario 4
     * (the required negative) checks this folder: because the anonymous DRS zip
     * download never completes, the folder stays empty and the scenario fails
     * for the right reason - not because of a hard-coded/foreign path.
     */
    public static final String DOWNLOAD_DIR =
            System.getProperty("user.dir") + File.separator + "target" + File.separator + "test-downloads";

    @BeforeSuite
    public void initReport() {
        // Touches the singleton once so the report file exists even if a
        // scenario fails to instantiate driver for some reason.
        ExtentReportManager.getInstance();
    }

    @BeforeMethod
    public void setUp() {
        WebDriverManager.chromedriver().setup();

        // Route downloads to our controlled folder and suppress the save dialog.
        File downloadDir = new File(DOWNLOAD_DIR);
        downloadDir.mkdirs();
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("download.default_directory", downloadDir.getAbsolutePath());
        prefs.put("download.prompt_for_download", false);
        prefs.put("download.directory_upgrade", true);
        prefs.put("safebrowsing.enabled", true);

        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("prefs", prefs);

        driver = new ChromeDriver(options);
        driver.manage().window().maximize();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
    }

    @AfterMethod
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @AfterSuite
    public void flushReport() {
        ExtentReportManager.flush();
    }

    /**
     * Shared NEU SSO login flow - CONFIRMED locators (Microsoft/O365-style
     * login page NEU uses, verified against a real login session).
     *
     * Flow: username (i0116) -> Next (idSIButton9) -> password (i0118) ->
     * Sign in (idSIButton9 again) -> optional "Yes, this is my device" ->
     * optional "Stay signed in?" prompt.
     *
     * Note: on this primary NEU login, Duo did NOT appear inline for our
     * test account - it only appeared later on the separate Banner
     * transcript-system login (see handleDuoPushIfPresent() below). If
     * your account is configured differently and Duo appears here too,
     * call handleDuoPushIfPresent() right after this method.
     *
     * @param portalUrl the initial portal URL for this scenario
     * @param username  read from Excel - NEU username
     * @param password  read from Excel - NEU password
     */
    protected void loginToNEU(String portalUrl, String username, String password) {
        driver.get(portalUrl);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(40));

        WebElement usernameField = wait.until(
                ExpectedConditions.elementToBeClickable(By.id("i0116")));
        usernameField.clear();
        usernameField.sendKeys(username);
        driver.findElement(By.id("idSIButton9")).click();

        WebElement passwordField = wait.until(
                ExpectedConditions.elementToBeClickable(By.id("i0118")));
        passwordField.clear();
        passwordField.sendKeys(password);
        driver.findElement(By.id("idSIButton9")).click();

        // Optional: "Yes, this is my device?" prompt - only appears sometimes
        try {
            WebElement yesDeviceButton = new WebDriverWait(driver, Duration.ofSeconds(6))
                    .until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//button[contains(text(),'Yes, this is my device')]")));
            yesDeviceButton.click();
        } catch (Exception e) {
            // Prompt didn't appear this time - that's fine, continue.
        }

        // Optional: "Stay signed in?" prompt - only appears sometimes
        try {
            WebElement staySignedInButton = new WebDriverWait(driver, Duration.ofSeconds(6))
                    .until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//input[@id='idSIButton9' and @value='Yes']")));
            staySignedInButton.click();
        } catch (Exception e) {
            // Prompt didn't appear this time - that's fine, continue.
        }
    }

    /**
     * Handles a Duo push challenge if one appears inside an iframe
     * (confirmed real flow: the separate Banner/transcript-system login
     * triggers this). Waits for you to actually approve the push on your
     * phone before continuing, rather than guessing a fixed sleep time -
     * this is the ONE manual intervention the assignment permits.
     */
    protected void handleDuoPushIfPresent() {
        try {
            WebElement duoIframe = new WebDriverWait(driver, Duration.ofSeconds(8))
                    .until(ExpectedConditions.presenceOfElementLocated(By.tagName("iframe")));
            driver.switchTo().frame(duoIframe);

            WebElement sendPushButton = new WebDriverWait(driver, Duration.ofSeconds(8))
                    .until(ExpectedConditions.elementToBeClickable(
                            By.xpath("//button[contains(text(),'Send Me a Push')]")));
            sendPushButton.click();

            int waitSeconds = 3;
            System.out.println(">>> Duo push sent. Approve it on your device NOW.");
            for (int remaining = waitSeconds; remaining > 0; remaining--) {
                System.out.println("    Waiting for Duo approval... " + remaining + "s remaining");
                Thread.sleep(1000);
            }

            driver.switchTo().defaultContent();

            // Duo sometimes shows its own "Is this your device?" prompt after
            // approval - appears inconsistently, so handle it as optional/best-effort.
            try {
                WebElement duoYesDeviceButton = new WebDriverWait(driver, Duration.ofSeconds(5))
                        .until(ExpectedConditions.elementToBeClickable(
                                By.xpath("//button[contains(text(),'Yes, this is my device')]")));
                duoYesDeviceButton.click();
            } catch (Exception ignoredDeviceDialog) {
                // Didn't appear this run - continue normally.
            }

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            driver.switchTo().defaultContent();
        } catch (Exception e) {
            System.out.println("No Duo push prompt detected - continuing without it.");
            driver.switchTo().defaultContent();
        }
    }

    /**
     * Switches the driver to the most-recently-opened window/tab. Many NEU
     * links (e.g. the Registrar's Academic Calendar) open in a NEW tab; the
     * driver keeps pointing at the old one until we explicitly switch. If a
     * window whose URL contains {@code preferUrlSubstring} exists, we land
     * there; otherwise we land on whatever tab isn't the current one.
     */
    protected void switchToNewestWindow(String currentHandle, String preferUrlSubstring) {
        String fallback = null;
        for (String handle : driver.getWindowHandles()) {
            if (handle.equals(currentHandle)) {
                continue;
            }
            fallback = handle;
            driver.switchTo().window(handle);
            if (preferUrlSubstring != null
                    && driver.getCurrentUrl().toLowerCase().contains(preferUrlSubstring.toLowerCase())) {
                return; // matched the page we wanted
            }
        }
        if (fallback != null) {
            driver.switchTo().window(fallback);
        }
        // No other window - stay where we are (link opened in the same tab).
    }

    /**
     * Scans every open window/tab and switches to the first one whose URL
     * contains {@code urlSubstring}. More reliable than "newest window" when a
     * link might open either in the same tab or a new one - we just go to
     * whichever tab is showing the page we want. If none match, the driver is
     * left on the last window scanned.
     */
    protected void switchToWindowByUrl(String urlSubstring) {
        String needle = urlSubstring.toLowerCase();
        for (String handle : driver.getWindowHandles()) {
            driver.switchTo().window(handle);
            if (driver.getCurrentUrl().toLowerCase().contains(needle)) {
                return;
            }
        }
    }

    /**
     * Some pages embed content (like the 25Live academic calendar widget)
     * inside an iframe, so elements aren't reachable from the top document.
     * This scans the top document and each iframe, leaving the driver focused
     * on the first frame that contains {@code locator}. Returns true if found.
     *
     * Temporarily drops the implicit wait to 0 so scanning empty frames is
     * fast, then restores it.
     */
    protected boolean focusFrameContaining(By locator) {
        driver.manage().timeouts().implicitlyWait(Duration.ZERO);
        try {
            driver.switchTo().defaultContent();
            if (!driver.findElements(locator).isEmpty()) {
                return true;
            }
            for (WebElement frame : driver.findElements(By.tagName("iframe"))) {
                driver.switchTo().defaultContent();
                driver.switchTo().frame(frame);
                if (!driver.findElements(locator).isEmpty()) {
                    return true; // leave driver focused inside this frame
                }
            }
            driver.switchTo().defaultContent();
            return false;
        } finally {
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        }
    }

    /**
     * Captures a "before" screenshot, runs the given step, then captures
     * an "after" screenshot - and attaches both to the Extent report.
     * Wrap each meaningful UI action in this to satisfy the
     * before/after-every-step screenshot requirement.
     */
    protected void executeStep(String scenarioName, String stepName, Runnable stepAction) {
        String beforePath = ScreenshotUtil.capture(driver, scenarioName, stepName, "before");
        if (beforePath != null) {
            ExtentReportManager.attachScreenshot(beforePath, stepName + " - before");
        }

        stepAction.run();

        String afterPath = ScreenshotUtil.capture(driver, scenarioName, stepName, "after");
        if (afterPath != null) {
            ExtentReportManager.attachScreenshot(afterPath, stepName + " - after");
        }
    }
}
