package com.automation.seleniumframework;

import com.automation.seleniumframework.base.BaseTest;
import com.automation.seleniumframework.utils.ExcelUtil;
import com.automation.seleniumframework.utils.ExtentReportManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.Map;

public class Scenario3_ReserveLibrarySpot extends BaseTest {

    private static final String SCENARIO_NAME = "Scenario3_ReserveLibrarySpot";
    private static final String DATA_FILE = "src/test/resources/testdata/TestData.xlsx";

    @Test(priority = 3)
    public void reserveSpot() throws InterruptedException {
        test = ExtentReportManager.createTest(SCENARIO_NAME);

        Map<String, String> data = ExcelUtil.readSheet(DATA_FILE, "Scenario3_Library").get(0);
        String libraryUrl = data.get("LibraryUrl");
        String seatStyle = data.get("SeatStyle");
        String capacity = data.get("Capacity");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        executeStep(SCENARIO_NAME, "01_openLibraryWebsite", () -> {
            driver.get(libraryUrl);
            wait.until(ExpectedConditions.titleContains("Library"));

            try {
                WebElement rejectButton = new WebDriverWait(driver, Duration.ofSeconds(5))
                        .until(ExpectedConditions.elementToBeClickable(
                                By.xpath("//button[contains(text(),'Reject') or contains(text(),'reject')]")));
                js.executeScript("arguments[0].click();", rejectButton);
            } catch (Exception e) {
                System.out.println("No cookie consent popup found - continuing.");
            }
        });
        demoPause(3);

        executeStep(SCENARIO_NAME, "02_clickReserveStudyRoom", () -> {
            WebElement reserveLink = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//a[contains(text(),'Reserve A Study Room')]")));
            js.executeScript("arguments[0].click();", reserveLink);
            wait.until(ExpectedConditions.urlContains("library-rooms-spaces"));
        });
        demoPause(3);

        executeStep(SCENARIO_NAME, "03_selectBoston", () -> {
            WebElement bostonButton = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//img[contains(@src, 'Boston.png')]")));
            js.executeScript("arguments[0].click();", bostonButton);
            wait.until(ExpectedConditions.urlContains("ideas/rooms-spaces"));
        });
        demoPause(3);

        executeStep(SCENARIO_NAME, "04_clickBookARoom", () -> {
            WebElement bookButton = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//a[contains(text(), 'Book a Room')]")));
            js.executeScript("arguments[0].click();", bookButton);
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.urlContains("northeastern.libcal.com"),
                    ExpectedConditions.urlContains("reserve/spaces/studyspace")
            ));
        });
        demoPause(3);

        executeStep(SCENARIO_NAME, "05_selectSeatStyle", () -> {
            WebElement seatStyleDropdown = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("gid")));
            new Select(seatStyleDropdown).selectByVisibleText(seatStyle);
        });
        demoPause(2);

        executeStep(SCENARIO_NAME, "06_selectCapacity", () -> {
            WebElement capacityDropdown = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("capacity")));
            new Select(capacityDropdown).selectByVisibleText(capacity);
        });
        demoPause(2);

        // ---- Step 7 (NEW per assignment V2): Select an available time slot ----
        executeStep(SCENARIO_NAME, "07_selectAvailableSlot", () -> {
            WebElement availableSlot = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("a.fc-timeline-event.s-lc-eq-avail")));
            js.executeScript("arguments[0].click();", availableSlot);
        });
        demoPause(3);

        // ---- Step 8: Scroll to bottom ----
        executeStep(SCENARIO_NAME, "08_scrollToBottom", () -> {
            js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
        });
        demoPause(3);

        // ---- Verification ----
        boolean urlValid = driver.getCurrentUrl().contains("libcal.com/spaces");
        String actualUrl = driver.getCurrentUrl();

        boolean slotSelected = !driver.findElements(By.cssSelector(".s-lc-pending-booking")).isEmpty();


        ExtentReportManager.logResult(
                "An available time slot is selected",
                "Booking details panel (.s-lc-pending-booking) is visible at the bottom of the page after scrolling",
                slotSelected ? "Booking details panel found - slot selection confirmed" : "Booking details panel not found",
                slotSelected
        );

        Assert.assertTrue(urlValid, "Expected to land on the filtered LibCal spaces page. Actual URL: " + actualUrl);
        Assert.assertTrue(slotSelected, "Expected the booking details panel (.s-lc-pending-booking) to be visible after selecting a slot");
    }

    /** Keeps the browser on screen for {@code seconds} so each step is visible
     *  and explainable during the live presentation. */
    private void demoPause(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}