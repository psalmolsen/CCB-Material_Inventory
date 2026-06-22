package com.ccb;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ensures the current month's Google Sheet tab exists.
 *
 * On every app start:
 *  1. Checks if the current month tab (e.g. "JUN") already exists.
 *  2. If missing, finds the most recent existing month tab to use as the source.
 *  3. Copies rows 1–4 (the header template) from the source tab verbatim.
 *  4. For each material row in the source tab, writes:
 *       - Col B  = Code No
 *       - Col C  = Item/Description
 *       - Col D  = UOM
 *       - Col E  = Price/Unit
 *       - Col F  = Initial Stock  ← previous month's Balance (col I)
 *     All other columns (Received, Out Qty, daily columns, Total Issued) are left blank.
 */
public class MonthSheetProvisioner {

    private static final String[] MONTH_TABS =
        {"JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"};

    /**
     * Runs the provisioning check. Call this once on startup from a background thread.
     * Returns the name of the tab that was created, or null if no action was needed.
     */
    public static String provisionCurrentMonth() throws Exception {
        LocalDate today = LocalDate.now();
        String currentTab = MONTH_TABS[today.getMonthValue() - 1];

        GoogleSheetsService service = new GoogleSheetsService();
        List<String> existingTabs = service.getSheetTabNames();

        // Tab already exists — nothing to do
        if (existingTabs.stream().anyMatch(t -> t.equalsIgnoreCase(currentTab))) {
            return null;
        }

        // Find most recent existing month tab to use as template source
        String sourceTab = findMostRecentTab(existingTabs, today.getMonthValue() - 1);
        if (sourceTab == null) {
            // No prior month tab found — can't auto-provision
            System.out.println("MonthSheetProvisioner: no source tab found, skipping.");
            return null;
        }

        System.out.println("MonthSheetProvisioner: creating tab " + currentTab + " from " + sourceTab);

        // 1. Create the new blank tab
        service.createSheet(currentTab);

        // 2. Read the header template (rows 1–4, i.e. A1:AR4) from source
        List<List<Object>> sourceData = service.readSheet(sourceTab);
        if (sourceData.size() >= 4) {
            List<List<Object>> headerRows = sourceData.subList(0, 4);
            service.writeBlock(currentTab + "!A1", headerRows);
        }

        // 3. Read material rows from the source tab (row 5 onward = index 4+)
        List<List<Object>> materialRows = new ArrayList<>();
        for (int i = GoogleSheetsService.DATA_START_ROW; i < sourceData.size(); i++) {
            List<Object> src = sourceData.get(i);
            if (src.size() <= 1 || src.get(1).toString().isBlank()) continue;

            // Build the new row: only identity + carry-forward balance
            List<Object> newRow = new ArrayList<>();
            newRow.add(get(src, 0));           // A: Date (copy as-is or blank)
            newRow.add(get(src, 1));           // B: Code No
            newRow.add(get(src, 2));           // C: Item/Description
            newRow.add(get(src, 3));           // D: UOM
            newRow.add(get(src, 4));           // E: Price/Unit
            newRow.add(get(src, 8));           // F: Initial Stock ← previous Balance (col I = index 8)
            // G (Received), H (date), I (Balance), J (Out Qty), K onward (daily + total) — all blank
            materialRows.add(newRow);
        }

        if (!materialRows.isEmpty()) {
            // Write starting at row 5 (DATA_START_ROW + 1 in 1-based)
            int startRow = GoogleSheetsService.DATA_START_ROW + 1;
            service.writeBlock(currentTab + "!A" + startRow, materialRows);
        }

        System.out.println("MonthSheetProvisioner: tab " + currentTab + " created successfully.");
        return currentTab;
    }

    /**
     * Walks backward from the month before currentMonthIdx (0-based) to find
     * the most recent existing tab.
     */
    private static String findMostRecentTab(List<String> existingTabs, int currentMonthIdx) {
        for (int i = currentMonthIdx - 1; i >= 0; i--) {
            String candidate = MONTH_TABS[i];
            if (existingTabs.stream().anyMatch(t -> t.equalsIgnoreCase(candidate))) {
                return candidate;
            }
        }
        // Fallback: if no earlier month this year, try the last tab available
        if (!existingTabs.isEmpty()) {
            return existingTabs.get(existingTabs.size() - 1);
        }
        return null;
    }

    private static Object get(List<Object> row, int index) {
        if (row == null || index >= row.size() || row.get(index) == null) return "";
        return row.get(index).toString();
    }
}
