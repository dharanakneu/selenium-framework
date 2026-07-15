package com.automation.seleniumframework.utils;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Singleton wrapper around ExtentReports, PLUS a simple standalone HTML
 * table report with the exact columns the assignment requires literally:
 * Test scenario name | Expected | Actual | Pass/Fail.
 *
 * ExtentReports' Spark theme is great for the rich, screenshot-embedded
 * view, but it does not render a literal 4-column table - Expected/Actual
 * end up combined into one log line. So logResult() now ALSO records each
 * row into a list, and flush() writes that list out as a second, plain
 * HTML file (SummaryTable.html) that satisfies the column requirement
 * unambiguously.
 */
public class ExtentReportManager {

    private static ExtentReports extent;
    private static ExtentTest test;
    private static String currentScenarioName;

    /** One row per logResult() call, used to build the plain 4-column summary table. */
    private static final List<String[]> summaryRows = new ArrayList<>();

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

    public static ExtentTest createTest(String scenarioName) {
        test = getInstance().createTest(scenarioName);
        currentScenarioName = scenarioName;
        return test;
    }

    public static ExtentTest getTest() {
        return test;
    }

    public static void logResult(String stepDescription, String expected, String actual, boolean passed) {
        String message = String.format(
                "%s | Expected: [%s] | Actual: [%s]", stepDescription, expected, actual);
        if (passed) {
            test.pass(message);
        } else {
            test.fail(message);
        }

        summaryRows.add(new String[]{
                currentScenarioName != null ? currentScenarioName : "Unknown Scenario",
                expected,
                actual,
                passed ? "PASS" : "FAIL"
        });
    }

    public static void attachScreenshot(String path, String title) {
        try {
            byte[] bytes = Files.readAllBytes(Paths.get(path));
            String base64 = Base64.getEncoder().encodeToString(bytes);
            test.info(title,
                    MediaEntityBuilder.createScreenCaptureFromBase64String(base64).build());
        } catch (Exception e) {
            test.warning("Could not attach screenshot: " + path);
        }
    }

    private static void writeSummaryTable() {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>")
                .append("<title>INFO6255 Selenium Assignment - Summary Report</title>")
                .append("<style>")
                .append("body{font-family:Arial,sans-serif;margin:30px;background:#f5f5f5;}")
                .append("h1{color:#333;}")
                .append("table{border-collapse:collapse;width:100%;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,0.1);}")
                .append("th,td{border:1px solid #ddd;padding:10px 14px;text-align:left;vertical-align:top;}")
                .append("th{background:#8B0000;color:#fff;}")
                .append("tr:nth-child(even){background:#f9f9f9;}")
                .append(".pass{color:green;font-weight:bold;}")
                .append(".fail{color:red;font-weight:bold;}")
                .append("</style></head><body>")
                .append("<h1>INFO6255 Selenium Assignment - Test Execution Summary</h1>")
                .append("<table><tr><th>Test Scenario Name</th><th>Expected</th><th>Actual</th><th>Pass/Fail</th></tr>");

        for (String[] row : summaryRows) {
            String statusClass = row[3].equals("PASS") ? "pass" : "fail";
            html.append("<tr><td>").append(escapeHtml(row[0])).append("</td>")
                    .append("<td>").append(escapeHtml(row[1])).append("</td>")
                    .append("<td>").append(escapeHtml(row[2])).append("</td>")
                    .append("<td class='").append(statusClass).append("'>").append(row[3]).append("</td></tr>");
        }

        html.append("</table></body></html>");

        try (FileWriter writer = new FileWriter("test-output/SummaryTable.html")) {
            writer.write(html.toString());
        } catch (IOException e) {
            System.err.println("Failed to write SummaryTable.html: " + e.getMessage());
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static void flush() {
        if (extent != null) {
            extent.flush();
        }
        writeSummaryTable();
    }
}