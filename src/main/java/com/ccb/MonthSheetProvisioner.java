package com.ccb;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ensures the current month's Google Sheet tab exists.
 * Detects the naming convention (short "MAY"/"JUN" vs full "JUNE"/"JULY")
 * from existing tabs so it never creates a duplicate.
 */
public class MonthSheetProvisioner {

    public static String provisionCurrentMonth() throws Exception {
        LocalDate today = LocalDate.now();
        Month month = today.getMonth();

        GoogleSheetsService service = new GoogleSheetsService();
        List<String> existingTabs = service.getSheetTabNames();

        // Determine the tab name for the current month matching the sheet's convention
        String currentTab = resolveTabName(month, existingTabs);

        // Tab already exists — nothing to do
        if (existingTabs.stream().anyMatch(t -> t.equalsIgnoreCase(currentTab))) {
            return null;
        }

        // Find the most recent existing month tab to use as source
        String sourceTab = findMostRecentTab(existingTabs, today.getMonthValue() - 1);
        if (sourceTab == null) {
            System.out.println("MonthSheetProvisioner: no source tab found, skipping.");
            return null;
        }

        System.out.println("MonthSheetProvisioner: creating tab [" + currentTab + "] from [" + sourceTab + "]");

        // 1. Create blank tab
        service.createSheet(currentTab);

        // 2. Copy header rows 1–4 from source
        List<List<Object>> sourceData = service.readSheet(sourceTab);
        if (sourceData.size() >= 4) {
            service.writeBlock(currentTab + "!A1", sourceData.subList(0, 4));
        }

        // 3. Write identity + carry-forward balance for each material row
        List<List<Object>> materialRows = new ArrayList<>();
        for (int i = GoogleSheetsService.DATA_START_ROW; i < sourceData.size(); i++) {
            List<Object> src = sourceData.get(i);
            if (src.size() <= 1 || src.get(1).toString().isBlank()) continue;
            List<Object> newRow = new ArrayList<>();
            newRow.add(get(src, 0));  // A: Date
            newRow.add(get(src, 1));  // B: Code No
            newRow.add(get(src, 2));  // C: Description
            newRow.add(get(src, 3));  // D: UOM
            newRow.add(get(src, 4));  // E: Price/Unit
            newRow.add(get(src, 8));  // F: Initial Stock ← previous Balance
            materialRows.add(newRow);
        }
        if (!materialRows.isEmpty()) {
            service.writeBlock(currentTab + "!A" + (GoogleSheetsService.DATA_START_ROW + 1), materialRows);
        }

        System.out.println("MonthSheetProvisioner: tab [" + currentTab + "] created successfully.");
        return currentTab;
    }

    /**
     * Determines what to name the new tab by detecting the convention used
     * in existing tabs. E.g. if "JUNE" exists we use "JULY", if "JUN" exists we use "JUL".
     * Falls back to full uppercase name if no convention detected.
     */
    private static String resolveTabName(Month month, List<String> existingTabs) {
        String shortName = month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(); // JUN
        String fullName  = month.getDisplayName(TextStyle.FULL,  Locale.ENGLISH).toUpperCase(); // JUNE

        // Check if any existing tab matches a FULL month name pattern
        boolean usesFullNames = existingTabs.stream().anyMatch(tab -> {
            for (Month m : Month.values()) {
                String full = m.getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase();
                if (tab.equalsIgnoreCase(full)) return true;
            }
            return false;
        });

        return usesFullNames ? fullName : shortName;
    }

    /**
     * Walks backward from currentMonthIdx (0-based) to find the most recent
     * existing tab, matching either short or full name.
     */
    private static String findMostRecentTab(List<String> existingTabs, int currentMonthIdx) {
        int startMonth = currentMonthIdx == 0 ? 12 : currentMonthIdx;
        for (int monthNumber = startMonth; monthNumber >= 1; monthNumber--) {
            Month m = Month.of(monthNumber);
            String shortName = m.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase();
            String fullName  = m.getDisplayName(TextStyle.FULL,  Locale.ENGLISH).toUpperCase();
            for (String tab : existingTabs) {
                if (tab.equalsIgnoreCase(shortName) || tab.equalsIgnoreCase(fullName)) {
                    return tab; // return the exact tab name as it exists in the sheet
                }
            }
        }
        return existingTabs.isEmpty() ? null : existingTabs.get(existingTabs.size() - 1);
    }

    private static Object get(List<Object> row, int index) {
        if (row == null || index >= row.size() || row.get(index) == null) return "";
        return row.get(index).toString();
    }
}
