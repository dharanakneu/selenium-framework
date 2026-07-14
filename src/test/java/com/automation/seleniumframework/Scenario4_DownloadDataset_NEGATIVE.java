package com.automation.seleniumframework;

import com.automation.seleniumframework.base.BaseTest;
import com.automation.seleniumframework.utils.ExcelUtil;
import com.automation.seleniumframework.utils.ExtentReportManager;
import org.openqa.selenium.By;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Scenario 4 (NEGATIVE): Download a Dataset - this scenario is REQUIRED
 * to fail by the assignment spec ("Again... this scenario must fail").
 *
 * Design choice: we still perform every real UI step (navigate to DRS,
 * open a dataset, click "Zip File"), then intentionally verify against an
 * expectation the actual outcome cannot satisfy - the downloaded file
 * never appears in the local Downloads folder within the wait window,
 * because anonymous/unauthenticated DRS downloads for this dataset type
 * are blocked/redirected rather than completing a direct file download.
 * That mismatch (Expected: file present / Actual: file absent) is what
 * the HTML report should capture as FAIL for this row.
 *
 * IMPORTANT: TestNG does NOT stop the suite when one @Test method fails -
 * it marks this method FAILED in the report and automatically proceeds
 * to the next @Test (Scenario 5, priority = 5) with no manual
 * intervention needed. That default behavior alone satisfies the
 * "resume automatically" requirement; no special retry/catch logic
 * is needed here.
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
        String downloadsFolder = data.get("DownloadsFolder"); // e.g. "/Users/you/Downloads"
        String expectedFileNameContains = data.get("ExpectedFileNameContains"); // e.g. "dataset"

        executeStep(SCENARIO_NAME, "01_openOneSearch", () -> driver.get(searchUrl));

        executeStep(SCENARIO_NAME, "02_clickDigitalRepositoryService", () -> {
            driver.findElement(By.linkText("Digital Repository Service")).click();
        });

        executeStep(SCENARIO_NAME, "03_clickDatasetsAndOpenOne", () -> {
            driver.findElement(By.linkText("Datasets")).click();
            // TODO: confirm real locator - opens the first dataset in the results list
            driver.findElement(By.cssSelector(".search-result-title a")).click();
        });

        executeStep(SCENARIO_NAME, "04_clickZipFileDownload", () -> {
            driver.findElement(By.xpath("//*[contains(text(),'Zip File')]")).click();
        });

        // Give the browser a short window to (not) complete the download
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {
        }

        boolean fileActuallyDownloaded = false;
        File dir = new File(downloadsFolder);
        if (dir.exists() && dir.isDirectory()) {
            File[] matches = dir.listFiles((d, name) -> name.toLowerCase()
                    .contains(expectedFileNameContains.toLowerCase()));
            fileActuallyDownloaded = matches != null && matches.length > 0;
        }

        ExtentReportManager.logResult(
                "Dataset zip file downloads successfully",
                "File containing '" + expectedFileNameContains + "' present in " + downloadsFolder,
                fileActuallyDownloaded ? "File found in Downloads" : "File NOT found in Downloads (as expected - negative scenario)",
                fileActuallyDownloaded
        );

        // This assertion is EXPECTED to fail - do not "fix" it to pass.
        Assert.assertTrue(fileActuallyDownloaded,
                "INTENTIONAL FAILURE (per assignment spec): dataset download did not complete as expected.");
    }
}
