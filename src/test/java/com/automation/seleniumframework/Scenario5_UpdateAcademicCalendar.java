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

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Scenario 5: Update the Academic Calendar.
 * Flow: student hub -> Resources -> Academics, Classes & Registration ->
 * Academic Calendar -> (Registrar site, opens in a NEW TAB) Academic Calendar
 * -> uncheck one calendar filter checkbox -> verify "Add to My Calendar"
 * button visible.
 *
 * Proves the suite auto-continues after Scenario 4's intentional failure.
 *
 * Checkbox matching: confirmed via live inspection that each calendar filter
 * checkbox carries its full name directly as its own aria-label attribute
 * (e.g. aria-label="  Quarter - CPS Graduate (QTR)"), so we match on that
 * one attribute directly.
 */
public class Scenario5_UpdateAcademicCalendar extends BaseTest {

    private static final String SCENARIO_NAME = "Scenario5_UpdateAcademicCalendar";
    private static final String DATA_FILE = "src/test/resources/testdata/TestData.xlsx";

    @Test(priority = 5)
    public void verifyAcademicCalendarFilterAndAddButton() {
        test = ExtentReportManager.createTest(SCENARIO_NAME);

        Map<String, String> login = ExcelUtil.readSheet(DATA_FILE, "Login").get(0);
        Map<String, String> data = ExcelUtil.readSheet(DATA_FILE, "Scenario5_Calendar").get(0);

        String studentHubUrl = data.get("StudentHubUrl");
        String checkboxLabelToUncheck = data.get("CheckboxLabelToUncheck");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        executeStep(SCENARIO_NAME, "01_login", () -> {
            loginToNEU(studentHubUrl, login.get("Username"), login.get("Password"));
            handleDuoPushIfPresent();
        });

        executeStep(SCENARIO_NAME, "02_clickResources", () -> {
            WebElement resources = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//a[normalize-space()='Resources'] | //*[normalize-space(text())='Resources']")));
            js.executeScript("arguments[0].click();", resources);
        });

        executeStep(SCENARIO_NAME, "03_academicsClassesRegistration", () -> {
            WebElement academics = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//*[contains(normalize-space(.),'Academics, Classes & Registration')]")));
            js.executeScript("arguments[0].click();", academics);
        });

        executeStep(SCENARIO_NAME, "04_clickAcademicCalendar", () -> {
            WebElement calLink = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//a[normalize-space()='Academic Calendar']")));
            js.executeScript("arguments[0].click();", calLink);
        });

        executeStep(SCENARIO_NAME, "05_switchToRegistrarTab", () -> {
            wait.until(ExpectedConditions.numberOfWindowsToBe(2));
            switchToWindowByUrl("registrar.northeastern.edu");
        });

        executeStep(SCENARIO_NAME, "06_openCurrentYearCalendar", () -> {
            WebElement current = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//a[contains(@href,'/article/academic-calendar/')]")));
            js.executeScript("arguments[0].click();", current);
        });

        executeStep(SCENARIO_NAME, "07_focusCalendarView", () -> {
            switchToWindowByUrl("registrar.northeastern.edu");

            WebElement target = null;
            long deadline = System.currentTimeMillis() + 25000;
            while (System.currentTimeMillis() < deadline && target == null) {
                target = findFilterCheckbox(checkboxLabelToUncheck);
                if (target == null) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            System.out.println(">>> [S5] URL=" + driver.getCurrentUrl()
                    + " | title=" + driver.getTitle()
                    + " | windows=" + driver.getWindowHandles().size()
                    + " | filterCheckboxFound=" + (target != null));
            test.info("Calendar page: " + driver.getCurrentUrl()
                    + " (filter checkbox found: " + (target != null) + ")");

            if (target == null) {
                throw new IllegalStateException("Calendar filter checkbox '"
                        + checkboxLabelToUncheck + "' not found on " + driver.getCurrentUrl()
                        + ". Checkbox contexts seen: " + dumpCheckboxContexts());
            }
        });

        executeStep(SCENARIO_NAME, "08_uncheckCalendarFilter", () -> {
            WebElement match = findFilterCheckbox(checkboxLabelToUncheck);
            if (match == null) {
                throw new IllegalStateException(
                        "Filter checkbox no longer present: " + checkboxLabelToUncheck);
            }
            js.executeScript("arguments[0].scrollIntoView({block:'center'});", match);
            if (match.isSelected()) {
                js.executeScript("arguments[0].click();", match);
            }
            System.out.println(">>> [S5] '" + checkboxLabelToUncheck
                    + "' unchecked - nowSelected=" + match.isSelected());
            demoPause(10);
        });

        driver.switchTo().defaultContent();

        boolean addButtonVisible = existsInAnyFrame("Add to My Calendar");

        executeStep(SCENARIO_NAME, "09_scrollToAddButton", () -> {
            driver.switchTo().defaultContent();
            js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
            demoPause(8);
        });

        ExtentReportManager.logResult(
                "'Add to My Calendar' button visible after unchecking a filter",
                "Button is present and visible on the page",
                addButtonVisible ? "Button found and visible" : "Button not found",
                addButtonVisible
        );

        Assert.assertTrue(addButtonVisible, "Expected 'Add to My Calendar' button to remain visible");
    }

    /**
     * Finds the calendar filter checkbox matching {@code target} by reading
     * each checkbox's own aria-label directly (confirmed real attribute -
     * see class javadoc). Searches the current frame and nested iframes.
     */
    private WebElement findFilterCheckbox(String target) {
        String key = normalizeLabel(target);
        driver.manage().timeouts().implicitlyWait(java.time.Duration.ZERO);
        try {
            driver.switchTo().defaultContent();
            return searchFramesForCheckboxByAriaLabel(key, 0);
        } finally {
            driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(10));
        }
    }

    private WebElement searchFramesForCheckboxByAriaLabel(String key, int depth) {
        List<WebElement> boxes = driver.findElements(By.cssSelector("input[type='checkbox'][aria-label]"));
        for (WebElement box : boxes) {
            String aria = box.getAttribute("aria-label");
            if (aria != null && matchesAllTokens(normalizeLabel(aria), key)) {
                return box;
            }
        }
        if (depth >= 3) {
            return null;
        }
        int frameCount = driver.findElements(By.tagName("iframe")).size();
        for (int i = 0; i < frameCount; i++) {
            List<WebElement> frames = driver.findElements(By.tagName("iframe"));
            if (i >= frames.size()) {
                break;
            }
            driver.switchTo().frame(frames.get(i));
            WebElement found = searchFramesForCheckboxByAriaLabel(key, depth + 1);
            if (found != null) {
                return found;
            }
            driver.switchTo().parentFrame();
        }
        return null;
    }

    /** True if every whitespace-separated token (length > 1) in keyNormalized appears in haystackNormalized. */
    private static boolean matchesAllTokens(String haystackNormalized, String keyNormalized) {
        for (String token : keyNormalized.split(" ")) {
            if (token.length() > 1 && !haystackNormalized.contains(token)) {
                return false;
            }
        }
        return true;
    }

    /** True if any frame (top document or nested iframe) has an element containing text. */
    private boolean existsInAnyFrame(String text) {
        By locator = By.xpath("//*[contains(normalize-space(.),'" + text + "')]");
        driver.manage().timeouts().implicitlyWait(java.time.Duration.ZERO);
        try {
            driver.switchTo().defaultContent();
            boolean found = searchFramesForText(locator, 0);
            driver.switchTo().defaultContent();
            return found;
        } finally {
            driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(10));
        }
    }

    private boolean searchFramesForText(By locator, int depth) {
        if (!driver.findElements(locator).isEmpty()) {
            return true;
        }
        if (depth >= 3) {
            return false;
        }
        int frameCount = driver.findElements(By.tagName("iframe")).size();
        for (int i = 0; i < frameCount; i++) {
            List<WebElement> frames = driver.findElements(By.tagName("iframe"));
            if (i >= frames.size()) {
                break;
            }
            driver.switchTo().frame(frames.get(i));
            if (searchFramesForText(locator, depth + 1)) {
                return true;
            }
            driver.switchTo().parentFrame();
        }
        return false;
    }

    /** Diagnostic: lists the aria-label of every checkbox across all frames,
     *  so a "not found" failure shows exactly what labels the page exposes. */
    private String dumpCheckboxContexts() {
        StringBuilder sb = new StringBuilder();
        driver.manage().timeouts().implicitlyWait(java.time.Duration.ZERO);
        try {
            for (int f = -1; ; f++) {
                driver.switchTo().defaultContent();
                List<WebElement> frames = driver.findElements(By.tagName("iframe"));
                if (f >= frames.size()) {
                    break;
                }
                if (f >= 0) {
                    driver.switchTo().frame(frames.get(f));
                }
                List<WebElement> boxes = driver.findElements(By.cssSelector("input[type='checkbox'][aria-label]"));
                List<String> labels = new java.util.ArrayList<>();
                for (WebElement b : boxes) {
                    String a = b.getAttribute("aria-label");
                    if (a != null && !a.trim().isEmpty()) {
                        labels.add(a.trim());
                    }
                }
                if (!labels.isEmpty()) {
                    sb.append("[frame ").append(f).append("] ").append(String.join(" | ", labels)).append("  ");
                }
            }
        } catch (Exception ignored) {
            // best-effort diagnostic
        } finally {
            driver.switchTo().defaultContent();
            driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(10));
        }
        return sb.toString();
    }

    private static String normalizeLabel(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }

    private void demoPause(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}