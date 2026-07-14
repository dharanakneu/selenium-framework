package com.automation.seleniumframework;

import com.automation.seleniumframework.base.BaseTest;
import com.automation.seleniumframework.utils.ExcelUtil;
import com.automation.seleniumframework.utils.ExtentReportManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

/**
 * Scenario 5: Update the Academic Calendar.
 * Steps: student hub -> Resources -> Academics, Classes & Registration ->
 * Academic Calendar -> (registrar site) Academic Calendar -> uncheck one
 * calendar filter checkbox -> verify "Add to My Calendar" button visible.
 *
 * Proves the suite auto-continues after Scenario 4's intentional failure.
 */
public class Scenario5_UpdateAcademicCalendar extends BaseTest {

    private static final String SCENARIO_NAME = "Scenario5_UpdateAcademicCalendar";
    private static final String DATA_FILE = "src/test/resources/testdata/TestData.xlsx";

    @Test(priority = 5)
    public void verifyAcademicCalendarFilterAndAddButton() {
        test = ExtentReportManager.createTest(SCENARIO_NAME);

        List<Map<String, String>> loginRows = ExcelUtil.readSheet(DATA_FILE, "Login");
        Map<String, String> login = loginRows.get(0);

        List<Map<String, String>> rows = ExcelUtil.readSheet(DATA_FILE, "Scenario5_Calendar");
        Map<String, String> data = rows.get(0);

        String studentHubUrl = data.get("StudentHubUrl"); // https://student.me.northeastern.edu/
        String checkboxLabelToUncheck = data.get("CheckboxLabelToUncheck"); // e.g. "Quarter - CPS Graduate (QTR)"

        executeStep(SCENARIO_NAME, "01_login", () ->
                loginToNEU(studentHubUrl, login.get("Username"), login.get("Password")));

        executeStep(SCENARIO_NAME, "02_clickResources", () -> {
            driver.findElement(By.linkText("Resources")).click();
        });

        executeStep(SCENARIO_NAME, "03_academicsClassesRegistration", () -> {
            driver.findElement(By.xpath("//*[contains(text(),'Academics, Classes & Registration')]")).click();
        });

        executeStep(SCENARIO_NAME, "04_clickAcademicCalendar", () -> {
            driver.findElement(By.linkText("Academic Calendar")).click();
        });

        executeStep(SCENARIO_NAME, "05_registrarAcademicCalendar", () -> {
            driver.findElement(By.linkText("Academic Calendar")).click();
        });

        executeStep(SCENARIO_NAME, "06_uncheckCalendarFilter", () -> {
            // TODO: confirm real locator - the calendar filter checkboxes on the right side
            WebElement checkbox = driver.findElement(
                    By.xpath("//label[contains(text(),'" + checkboxLabelToUncheck + "')]/preceding-sibling::input"));
            if (checkbox.isSelected()) {
                checkbox.click();
            }
        });

        boolean addButtonVisible = false;
        executeStep(SCENARIO_NAME, "07_verifyAddToCalendarButton", () -> {
            ((org.openqa.selenium.JavascriptExecutor) driver)
                    .executeScript("window.scrollTo(0, document.body.scrollHeight);");
        });

        addButtonVisible = driver.findElements(By.xpath("//*[contains(text(),'Add to My Calendar')]")).size() > 0;

        ExtentReportManager.logResult(
                "'Add to My Calendar' button visible after unchecking a filter",
                "Button is present and visible on the page",
                addButtonVisible ? "Button found and visible" : "Button not found",
                addButtonVisible
        );

        Assert.assertTrue(addButtonVisible, "Expected 'Add to My Calendar' button to remain visible");
    }
}
