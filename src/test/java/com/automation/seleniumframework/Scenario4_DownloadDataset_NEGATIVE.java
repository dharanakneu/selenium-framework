package com.automation.seleniumframework;

import com.automation.seleniumframework.base.BaseTest;
import com.automation.seleniumframework.utils.ExcelUtil;
import com.automation.seleniumframework.utils.ExtentReportManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Scenario 4 (NEGATIVE): Download a Dataset - this scenario is REQUIRED
 * to fail by the assignment spec ("Again... this scenario must fail").
 *
 * Design choice: we still perform every real UI step (navigate to DRS,
 * open a dataset, click "Zip File"), then verify against an expectation the
 * actual outcome cannot satisfy - the file never lands in the local
 * Downloads folder within the wait window, because anonymous DRS zip
 * downloads for this dataset type are blocked/redirected rather than
 * completing a direct file download. That mismatch (Expected: file present /
 * Actual: file absent) is what the HTML report captures as FAIL for this row.
 *
 * IMPORTANT - two things this class guarantees:
 *  1. The UI navigation is wrapped in try/catch. Locators on the live DRS
 *     site can drift, but this scenario must fail regardless, so a navigation
 *     hiccup must NOT abort the method before we record the controlled
 *     result. We always reach logResult + the final assertion.
 *  2. TestNG does NOT stop the suite when one @Test fails - it marks this
 *     method FAILED and automatically proceeds to Scenario 5. Combined with
 *     testng.xml ordering, that satisfies "resume automatically to the next
 *     scenario" with no manual intervention.
 */
public class Scenario4_DownloadDataset_NEGATIVE extends BaseTest {

    private static final String SCENARIO_NAME = "Scenario4_DownloadDataset_NEGATIVE";
    private static final String DATA_FILE = "src/test/resources/testdata/TestData.xlsx";

    @Test(priority = 4)
    public void verifyDatasetDownload_ExpectedToFail() {
        test = ExtentReportManager.createTest(SCENARIO_NAME);

        List<Map<String, String>> rows = ExcelUtil.readSheet(DATA_FILE, "Scenario4_Dataset");
        Map<String, String> data = rows.get(0);

        String searchUrl = data.get("SearchUrl");
        String expectedFileNameContains = data.get("ExpectedFileNameContains"); // e.g. "dataset"

        // Downloads are routed to this controlled folder (see BaseTest). Start
        // it empty so a leftover file from a previous run can't make the
        // negative scenario pass by accident.
        String downloadsFolder = DOWNLOAD_DIR;
        clearDownloadsFolder(downloadsFolder);

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Best-effort UI navigation - never let a locator hiccup abort the
        // run before we record the (intentionally failing) result below.
        // Case-insensitive locators (XPath contains is case-sensitive, and the
        // Primo/DRS menu text is often uppercase). demoPause() keeps each step
        // on screen long enough to see during the live presentation.
        try {
            executeStep(SCENARIO_NAME, "01_openOneSearch", () -> {
                driver.get(searchUrl);
                demoPause(3);
            });

            String oneSearchWindow = driver.getWindowHandle();

            executeStep(SCENARIO_NAME, "02_clickDigitalRepositoryService", () -> {
                WebElement drs = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
                        "//a[contains(" + lower(".") + ",'digital repository')]"
                      + " | //*[self::button or self::span][contains(" + lower(".") + ",'digital repository')]")));
                js.executeScript("arguments[0].click();", drs);
                demoPause(3);
            });

            // The DRS link opens the repository site, often in a NEW tab.
            switchToNewestWindow(oneSearchWindow, "repository");
            demoPause(2);

            executeStep(SCENARIO_NAME, "03_clickDatasetsAndOpenOne", () -> {
                WebElement datasets = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
                        "//*[self::a or self::button][contains(" + lower(".") + ",'datasets')]")));
                js.executeScript("arguments[0].click();", datasets);
                demoPause(2);

                // Open the first dataset in the results list.
                WebElement firstDataset = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
                        "(//a[contains(@href,'/files/') or contains(@href,'neu:') or contains(@href,'drs:')])[1]")));
                js.executeScript("arguments[0].click();", firstDataset);
                demoPause(3);
            });

            executeStep(SCENARIO_NAME, "04_clickZipFileDownload", () -> {
                WebElement zip = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(
                        "//*[contains(" + lower(".") + ",'zip file') or contains(" + lower(".") + ",'download')]")));
                js.executeScript("arguments[0].click();", zip);
                demoPause(3);
            });
        } catch (Exception navProblem) {
            // Expected for a negative scenario - log and fall through to the assertion.
            test.warning("Navigation step could not be completed (acceptable for this "
                    + "negative scenario): " + navProblem.getMessage());
        }

        // Give the browser a window to (not) complete the download - also keeps
        // the final state on screen for the demo.
        demoPause(6);

        // A COMPLETED download is any real file in the folder (ignore in-progress
        // *.crdownload parts and macOS .DS_Store). For this negative scenario the
        // anonymous DRS zip never completes, so the folder stays empty -> FAIL.
        boolean fileActuallyDownloaded = false;
        File dir = new File(downloadsFolder);
        if (dir.exists() && dir.isDirectory()) {
            File[] completed = dir.listFiles((d, name) -> {
                String n = name.toLowerCase();
                return !n.endsWith(".crdownload") && !n.endsWith(".tmp") && !n.equals(".ds_store");
            });
            fileActuallyDownloaded = completed != null && completed.length > 0;
        }

        ExtentReportManager.logResult(
                "Dataset zip file downloads successfully",
                "A completed dataset file (e.g. containing '" + expectedFileNameContains
                        + "') present in " + downloadsFolder,
                fileActuallyDownloaded
                        ? "File found in download folder"
                        : "No file in download folder (as expected - negative scenario)",
                fileActuallyDownloaded
        );

        // This assertion is EXPECTED to fail - do not "fix" it to pass.
        Assert.assertTrue(fileActuallyDownloaded,
                "INTENTIONAL FAILURE (per assignment spec): dataset download did not complete as expected.");
    }

    /** Deletes any leftover files so the folder starts empty for this run. */
    private void clearDownloadsFolder(String folder) {
        File dir = new File(folder);
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
    }

    /** Builds a lowercase() XPath wrapper so text matching is case-insensitive. */
    private static String lower(String xpathExpr) {
        return "translate(normalize-space(" + xpathExpr
                + "),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')";
    }

    /** Keeps the browser on screen for {@code seconds} so each step is visible
     *  during the live presentation before the browser closes. */
    private void demoPause(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
