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
 * Key robustness fixes vs. the original:
 *  - Uses WebDriverWait + JS clicks (like the working Scenario 3) instead of
 *    bare findElement, which threw on the SPA-heavy student hub.
 *  - Switches to the new browser tab that the Registrar's "Academic Calendar"
 *    link opens - the original kept driving the old tab and every later step
 *    silently ran against the wrong window.
 *  - Uses a label-agnostic checkbox locator (works whether the <input> is a
 *    sibling of, or wrapped by, its <label>).
 */
public class Scenario5_UpdateAcademicCalendar extends BaseTest {

    private static final String SCENARIO_NAME = "Scenario5_UpdateAcademicCalendar";
    private static final String DATA_FILE = "src/test/resources/testdata/TestData.xlsx";

    @Test(priority = 5)
    public void verifyAcademicCalendarFilterAndAddButton() {
        test = ExtentReportManager.createTest(SCENARIO_NAME);

        Map<String, String> login = ExcelUtil.readSheet(DATA_FILE, "Login").get(0);
        Map<String, String> data = ExcelUtil.readSheet(DATA_FILE, "Scenario5_Calendar").get(0);

        String studentHubUrl = data.get("StudentHubUrl"); // https://student.me.northeastern.edu/
        String checkboxLabelToUncheck = data.get("CheckboxLabelToUncheck"); // e.g. "Quarter - CPS Graduate (QTR)"

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Shared NEU login + Duo (per the spec note: every scenario logs in).
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

        // The student-hub "Academic Calendar" resource link opens the Registrar
        // site in a NEW tab (handled by step 05, which switches to it by URL).
        executeStep(SCENARIO_NAME, "04_clickAcademicCalendar", () -> {
            WebElement calLink = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//a[normalize-space()='Academic Calendar']")));
            js.executeScript("arguments[0].click();", calLink);
        });

        // The student-hub link opens the Registrar in a NEW tab. Wait for it,
        // then switch to whichever tab is on registrar.northeastern.edu.
        executeStep(SCENARIO_NAME, "05_switchToRegistrarTab", () -> {
            wait.until(ExpectedConditions.numberOfWindowsToBe(2));
            switchToWindowByUrl("registrar.northeastern.edu");
        });

        // On the landing page ("Academic Calendars" with Academic / Future /
        // Past links), open the current-year "Academic Calendar" to reach the
        // page that actually has the calendar-filter checkboxes.
        executeStep(SCENARIO_NAME, "06_openCurrentYearCalendar", () -> {
            // The anchor text is "Academic Calendar" + a description line, so an
            // exact-text match fails - match the current-year link by its href.
            WebElement current = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//a[contains(@href,'/article/academic-calendar/')]")));
            js.executeScript("arguments[0].click();", current);
        });

        // That click may open yet another tab - follow it if so, then locate
        // the calendar-filter checkboxes. The 25Live widget loads async and may
        // be inside an iframe, so poll: dig through frames until they appear.
        executeStep(SCENARIO_NAME, "07_focusCalendarView", () -> {
            // The current-year link navigates in place (or opens a new tab) -
            // either way, land on the registrar tab showing the calendar.
            switchToWindowByUrl("registrar.northeastern.edu");

            // Poll (25Live loads async) until the target FILTER checkbox is
            // located in some frame. findFilterCheckbox leaves the driver
            // focused on the frame that holds it.
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
            // Re-locate (also re-focuses the correct frame) and uncheck it.
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
            // Pause so the un-checked state is clearly visible during the live demo.
            demoPause(10);
        });

        // We're currently focused inside the sidebar iframe - go back to the
        // main page before doing anything with the rest of the document.
        driver.switchTo().defaultContent();

        // Verify the "Add to My Calendar" button is present after unchecking.
        // The button may live in the same Trumba/25Live frame we're in, or
        // another - check every frame.
        boolean addButtonVisible = existsInAnyFrame("Add to My Calendar");

        // The event list and the "Add to My Calendar" button live in the main
        // page, with the button at the bottom - so scroll the main page to the
        // bottom and hold it there (single downward scroll, no jumping back up).
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
     * Scans the top document and every iframe for a checkbox whose closely
     * associated text contains ALL tokens of {@code target} (e.g. "quarter",
     * "cps", "graduate", "qtr"). Requiring every token means it lands on the
     * "Quarter - CPS Graduate (QTR)" filter, never an event-row/"All" box or a
     * different calendar filter. Leaves the driver focused on the frame that
     * holds the match; returns null (driver reset to top) if none found.
     */
    private WebElement findFilterCheckbox(String target) {
        String key = normalizeLabel(target);
        driver.manage().timeouts().implicitlyWait(java.time.Duration.ZERO);
        try {
            driver.switchTo().defaultContent();
            return searchFramesForCheckbox(key, 0);
        } finally {
            driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(10));
        }
    }

    /**
     * Depth-first search of the CURRENTLY-focused frame and all nested iframes
     * (Trumba embeds the calendar sidebar in cross-origin, sometimes nested,
     * iframes). Leaves the driver focused on the frame holding the match.
     */
    private WebElement searchFramesForCheckbox(String key, int depth) {
        WebElement hit = matchCheckboxInCurrentFrame(key);
        if (hit != null) {
            return hit;
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
            WebElement found = searchFramesForCheckbox(key, depth + 1);
            if (found != null) {
                return found; // leave driver focused inside this (possibly nested) frame
            }
            driver.switchTo().parentFrame(); // back up to keep scanning siblings
        }
        return null;
    }

    /**
     * Within the currently-focused frame, returns the first checkbox whose
     * tightly-coupled text (label[for], aria-label, adjacent sibling, or
     * wrapping label) contains every whitespace-separated token of the key.
     */
    private WebElement matchCheckboxInCurrentFrame(String normalizedKey) {
        Object result = ((JavascriptExecutor) driver).executeScript(
                "var key = arguments[0];"
              + "var tokens = key.split(' ').filter(function(t){return t.length > 1;});"
              + "if (tokens.length === 0) return null;"
              + "function norm(s){return (s||'').toLowerCase().replace(/[^a-z0-9]+/g,' ');}"
              + "function hasAll(s){var n=norm(s);for(var j=0;j<tokens.length;j++){if(n.indexOf(tokens[j])<0)return false;}return true;}"
              // Strategy A: anchor on the visible label text, then grab its checkbox.
              + "var all=document.querySelectorAll('body *');"
              + "for (var i=0;i<all.length;i++){"
              + "  var el=all[i];"
              + "  if(!hasAll(el.textContent)) continue;"
              + "  var childHas=false;"
              + "  for(var c=0;c<el.children.length;c++){ if(hasAll(el.children[c].textContent)){childHas=true;break;} }"
              + "  if(childHas) continue;"                       // want the innermost element that holds the text
              + "  if(el.tagName==='LABEL' && el.htmlFor){var t=document.getElementById(el.htmlFor); if(t&&t.type==='checkbox')return t;}"
              + "  var prev=el.previousElementSibling; if(prev&&prev.matches&&prev.matches(\"input[type='checkbox']\"))return prev;"
              + "  var next=el.nextElementSibling; if(next&&next.matches&&next.matches(\"input[type='checkbox']\"))return next;"
              + "  var scope=el;"
              + "  for(var d=0;d<4&&scope;d++){ var cb=scope.querySelector?scope.querySelector(\"input[type='checkbox']\"):null; if(cb)return cb; scope=scope.parentElement; }"
              + "}"
              // Strategy B: examine each checkbox's tightly-coupled text.
              + "var boxes=document.querySelectorAll(\"input[type='checkbox']\");"
              + "for (var k=0;k<boxes.length;k++){"
              + "  var b=boxes[k], txt='';"
              + "  if(b.id){var esc=(window.CSS&&CSS.escape)?CSS.escape(b.id):b.id; var l=document.querySelector(\"label[for='\"+esc+\"']\"); if(l)txt+=' '+l.textContent;}"
              + "  if(b.getAttribute('aria-label'))txt+=' '+b.getAttribute('aria-label');"
              + "  if(b.nextElementSibling)txt+=' '+b.nextElementSibling.textContent;"
              + "  if(b.previousElementSibling)txt+=' '+b.previousElementSibling.textContent;"
              + "  var lab=b.closest('label'); if(lab)txt+=' '+lab.textContent;"
              + "  if(hasAll(txt))return b;"
              + "}"
              + "return null;", normalizedKey);
        return (WebElement) result;
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

    /** Diagnostic: lists the associated text of every checkbox across all frames,
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
                Object r = ((JavascriptExecutor) driver).executeScript(
                        "var out=[];var bs=document.querySelectorAll(\"input[type='checkbox']\");"
                      + "for(var i=0;i<bs.length;i++){var b=bs[i],t='';"
                      + "if(b.nextElementSibling)t+=b.nextElementSibling.textContent;"
                      + "var l=b.closest('label');if(l)t+=' '+l.textContent;"
                      + "t=(t||b.getAttribute('aria-label')||'').trim().replace(/\\s+/g,' ');"
                      + "if(t)out.push(t);} return out.join(' | ');");
                if (r != null && !r.toString().isEmpty()) {
                    sb.append("[frame ").append(f).append("] ").append(r).append("  ");
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

    /** Lowercases and collapses all non-alphanumeric runs to single spaces, so
     *  "Quarter - CPS Graduate (QTR)" and "Quarter – CPS Graduate  (QTR)" match. */
    private static String normalizeLabel(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Keeps the browser on screen for {@code seconds} so the current state is
     *  visible during the live presentation before the driver quits. */
    private void demoPause(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
