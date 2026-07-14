package com.automation.seleniumframework;

import com.automation.seleniumframework.base.BaseTest;
import com.automation.seleniumframework.utils.ExcelUtil;
import com.automation.seleniumframework.utils.ExtentReportManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Scenario 2: Add two events to the Canvas Calendar.
 *
 * Real flow, confirmed against a live run (Canvas login uses the same
 * Microsoft SSO as the main NEU portal - i0116/i0118/idSIButton9):
 *   1. Log into Canvas
 *   2. Handle Duo (a "trust-browser-button" prompt appears here, in
 *      addition to / instead of the iframe push flow seen in Scenario 1)
 *   3. Click Calendar in the nav
 *   4. For each of 2 rows in Scenario2_Events: click "+", fill Title,
 *      Date, Start/End time, Frequency, Location, Calendar, then Submit
 *   5. Verify both event titles appear on the calendar
 *
 * All data comes from TestData.xlsx (Login sheet + Scenario2_Events sheet -
 * must contain exactly 2 rows).
 */
public class Scenario2_AddCalendarEvents extends BaseTest {

    private static final String SCENARIO_NAME = "Scenario2_AddCalendarEvents";
    private static final String DATA_FILE = "src/test/resources/testdata/TestData.xlsx";

    @Test(priority = 2)
    public void verifyTwoEventsAdded() throws Exception {
        test = ExtentReportManager.createTest(SCENARIO_NAME);

        Map<String, String> login = ExcelUtil.readSheet(DATA_FILE, "Login").get(0);
        List<Map<String, String>> events = ExcelUtil.readSheet(DATA_FILE, "Scenario2_Events");
        Assert.assertEquals(events.size(), 2, "Excel sheet must contain exactly 2 event rows");

        String canvasUrl = login.get("CanvasUrl");
        String username = login.get("Username");
        String password = login.get("Password");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(40));

        // ---- Step 1: Login to Canvas ----
        executeStep(SCENARIO_NAME, "01_loginToCanvas", () -> loginToNEU(canvasUrl, username, password));

        // ---- Step 2: Handle Canvas-specific Duo "trust this browser" prompt ----
        executeStep(SCENARIO_NAME, "02_handleDuoTrustBrowser", () -> {
            try {
                WebElement trustBrowserButton = new WebDriverWait(driver, Duration.ofSeconds(10))
                        .until(ExpectedConditions.elementToBeClickable(By.id("trust-browser-button")));
                trustBrowserButton.click();
            } catch (Exception e) {
                System.out.println("No 'trust-browser-button' prompt seen - trying iframe push instead.");
                handleDuoPushIfPresent();
            }
        });

        // ---- Step 3: Navigate to Calendar ----
        executeStep(SCENARIO_NAME, "03_clickCalendar", () -> {
            WebElement calendarLink = wait.until(
                    ExpectedConditions.elementToBeClickable(By.id("global_nav_calendar_link")));
            calendarLink.click();
        });

        JavascriptExecutor js = (JavascriptExecutor) driver;

        // ---- Step 4: Create both events from Excel data ----
        int eventNumber = 1;
        for (Map<String, String> event : events) {
            final int currentEvent = eventNumber;
            final String title = event.get("Title");
            final String date = event.get("Day");
            final String startTime = event.get("StartTime");
            final String endTime = event.get("EndTime");
            final String frequency = event.get("Frequency");
            final String location = event.get("Location");
            final String calendarName = event.get("Calendar");

            executeStep(SCENARIO_NAME, "0" + (3 + currentEvent) + "_createEvent" + currentEvent, () -> {
                driver.navigate().refresh();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                }

                WebElement eventIcon = wait.until(
                        ExpectedConditions.elementToBeClickable(By.xpath("//a[@id='create_new_event_link']")));
                eventIcon.click();

                driver.findElement(By.xpath("//input[@data-testid='edit-calendar-event-form-title']"))
                        .sendKeys(title);

                WebElement calendarTrigger = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//button[@data-popover-trigger='true'][.//span[contains(text(),'Choose a date')]]")));
                calendarTrigger.click();

                WebElement dayButton = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//button[@data-cid='Calendar.Day'][.//span[contains(@class,'screenReaderContent') and text()='"
                                + date + "']]")));
                dayButton.click();

                WebElement fromInput = driver.findElement(
                        By.xpath("//input[@data-testid='event-form-start-time']"));
                fromInput.click();
                fromInput.clear();
                fromInput.sendKeys(startTime);
                fromInput.sendKeys(Keys.TAB);

                WebElement toInput = driver.findElement(
                        By.xpath("//input[@data-testid='event-form-end-time']"));
                toInput.click();
                toInput.clear();
                toInput.sendKeys(endTime);
                toInput.sendKeys(Keys.TAB);

                WebElement picker = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//input[@data-testid='frequency-picker']")));
                picker.click();
                WebElement frequencySelect = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//*[text()='" + frequency + "']")));
                frequencySelect.click();

                WebElement locationInput = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//input[@data-testid='edit-calendar-event-form-location']")));
                locationInput.sendKeys(location);

                selectCalendarDropdown(calendarName);

                WebElement submit = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.xpath("//button[@data-testid='edit-calendar-event-submit-button']")));
                js.executeScript("arguments[0].scrollIntoView(true);", submit);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
                js.executeScript("arguments[0].click();", submit);
            });

            eventNumber++;
        }

        // ---- Step 5: Verify both events appear on the calendar ----
        boolean allEventsVisible = events.stream()
                .allMatch(e -> driver.getPageSource().contains(e.get("Title")));

        ExtentReportManager.logResult(
                "Both events appear on calendar",
                "Both event titles visible on the calendar grid",
                allEventsVisible ? "Both events found on calendar" : "One or both events missing",
                allEventsVisible
        );

        Assert.assertTrue(allEventsVisible, "Expected both created events to appear on the calendar");
    }

    /**
     * Selects a value from Canvas's "Calendar" dropdown in the Edit Event
     * form - an Instructure UI SimpleSelect component (a readonly text
     * input that opens an ARIA listbox on click, not a native <select>).
     */
    private void selectCalendarDropdown(String calendarName) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        WebElement calendarInput = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//input[@data-testid='edit-calendar-event-form-context']")));
        calendarInput.click();

        WebElement option = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//*[@role='option'][contains(text(),'" + calendarName + "')]")));
        option.click();
    }
}