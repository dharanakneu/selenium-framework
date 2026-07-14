package com.automation.seleniumframework.utils;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;

/**
 * Singleton wrapper around ExtentReports. Produces the final HTML report
 * with columns: Test scenario name, Actual, Expected, Pass/Fail.
 *
 * ExtentReports' built-in log()/pass()/fail() calls plus test name give us
 * this structure automatically; we just need to log Expected vs Actual
 * consistently for every scenario.
 */
public class ExtentReportManager {

    private static ExtentReports extent;
    private static ExtentTest test;

    public static ExtentReports getInstance() {
        if (extent == null) {
            ExtentSparkReporter spark = new ExtentSparkReporter("test-output/SeleniumAssignmentReport.html");
            spark.config().setDocumentTitle("INFO6255 Selenium Assignment Report");
            spark.config().setReportName("Group Selenium Automation - Test Execution Report");

            extent = new ExtentReports();
            extent.attachReporter(spark);
            extent.setSystemInfo("Course", "INFO6255");
            extent.setSystemInfo("Term", "Summer 2026");
        }
        return extent;
    }

    /** Starts logging for a new scenario/test. Call at the beginning of each @Test. */
    public static ExtentTest createTest(String scenarioName) {
        test = getInstance().createTest(scenarioName);
        return test;
    }

    public static ExtentTest getTest() {
        return test;
    }

    /**
     * Logs a single assertion outcome in the Expected / Actual / Pass-Fail shape
     * the assignment requires.
     */
    public static void logResult(String stepDescription, String expected, String actual, boolean passed) {
        String message = String.format(
                "%s | Expected: [%s] | Actual: [%s]", stepDescription, expected, actual);
        if (passed) {
            test.pass(message);
        } else {
            test.fail(message);
        }
    }

    public static void attachScreenshot(String path, String title) {
        try {
            test.info(title).addScreenCaptureFromPath(path);
        } catch (Exception e) {
            test.warning("Could not attach screenshot: " + path);
        }
    }

    /** Must be called once, after ALL tests finish (e.g. in an @AfterSuite), to flush the report to disk. */
    public static void flush() {
        if (extent != null) {
            extent.flush();
        }
    }
}
