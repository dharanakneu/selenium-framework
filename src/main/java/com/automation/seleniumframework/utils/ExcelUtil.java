package com.automation.seleniumframework.utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads test data from an external Excel (.xlsx) file.
 * Assignment requirement: all test data/login info must come from a data
 * table or spreadsheet - NOT a config file, and NOT hardcoded in test code.
 *
 * Expected format: first row = column headers, each subsequent row = one
 * record. Each sheet name should match the scenario it belongs to
 * (e.g. "Login", "Scenario2_Events").
 */
public class ExcelUtil {

    /**
     * Reads an entire sheet into a list of rows, where each row is a
     * Map of columnHeader -> cellValue (as String).
     *
     * @param filePath  path to the .xlsx file, e.g. "src/test/resources/testdata/TestData.xlsx"
     * @param sheetName name of the sheet to read
     */
    public static List<Map<String, String>> readSheet(String filePath, String sheetName) {
        List<Map<String, String>> records = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet not found: " + sheetName);
            }

            Row headerRow = sheet.getRow(0);
            int columnCount = headerRow.getLastCellNum();
            List<String> headers = new ArrayList<>();
            for (int c = 0; c < columnCount; c++) {
                headers.add(getCellValueAsString(headerRow.getCell(c)));
            }

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Map<String, String> record = new LinkedHashMap<>();
                for (int c = 0; c < columnCount; c++) {
                    String header = headers.get(c);
                    String value = getCellValueAsString(row.getCell(c));
                    record.put(header, value);
                }
                records.add(record);
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to read Excel file: " + filePath, e);
        }

        return records;
    }

    /**
     * Convenience method for TestNG @DataProvider - returns data as Object[][]
     * where each row is a single Map<String,String> record wrapped in an Object[].
     */
    public static Object[][] readSheetAsDataProvider(String filePath, String sheetName) {
        List<Map<String, String>> records = readSheet(filePath, sheetName);
        Object[][] data = new Object[records.size()][1];
        for (int i = 0; i < records.size(); i++) {
            data[i][0] = records.get(i);
        }
        return data;
    }

    private static String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double num = cell.getNumericCellValue();
                if (num == Math.floor(num)) {
                    return String.valueOf((long) num);
                }
                return String.valueOf(num);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }
}
