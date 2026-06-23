package com.ccb;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ensures the current month's CNF sheet tab exists.
 * On first run: renames "Sheet1" → current month name (e.g. "MAY").
 * Each month: creates a new tab pre-filled with item identities + carried balance.
 * Mirrors MonthSheetProvisioner logic but for the CNF spreadsheet.
 */
public class CnfMonthProvisioner {

    public static String provision() throws Exception {
        LocalDate today = LocalDate.now();
        Month month = today.getMonth();
        CnfSheetService svc = new CnfSheetService();
        List<String> tabs = svc.getTabNames();

        // Step 1: rename "Sheet1" to current month name if it exists
        for (String tab : tabs) {
            if (tab.equalsIgnoreCase("Sheet1")) {
                String name = resolveMonthName(month, tabs);
                svc.renameSheet("Sheet1", name);
                System.out.println("CnfMonthProvisioner: renamed Sheet1 → " + name);
                tabs = svc.getTabNames(); // refresh
                break;
            }
        }

        // Step 2: determine the target tab name for this month
        String currentTab = resolveMonthName(month, tabs);

        // Already exists — nothing to do
        if (tabs.stream().anyMatch(t -> t.equalsIgnoreCase(currentTab))) {
            return null;
        }

        // Step 3: find source tab (most recent existing month)
        String sourceTab = findMostRecentTab(tabs, today.getMonthValue() - 1);
        if (sourceTab == null) {
            System.out.println("CnfMonthProvisioner: no source tab, skipping.");
            return null;
        }

        System.out.println("CnfMonthProvisioner: creating [" + currentTab + "] from [" + sourceTab + "]");

        // Step 4: create the new tab
        svc.createSheet(currentTab);

        // Step 5: copy header rows 1–5
        List<List<Object>> srcData = svc.readSheet(sourceTab);
        if (srcData.size() >= CnfItem.DATA_START_ROW) {
            svc.writeBlock(currentTab + "!A1", srcData.subList(0, CnfItem.DATA_START_ROW));
        }

        // Step 6: for each data row, copy identity + carry balance → initial stock
        List<List<Object>> newRows = new ArrayList<>();
        for (int i = CnfItem.DATA_START_ROW; i < srcData.size(); i++) {
            List<Object> src = srcData.get(i);
            if (src.isEmpty() || src.get(0).toString().isBlank()) continue;

            List<Object> row = new ArrayList<>();
            row.add(get(src, CnfItem.COL_ITEM));      // A: Item name
            row.add(get(src, CnfItem.COL_UOM));       // B: UOM
            row.add(get(src, CnfItem.COL_PRICE));     // C: Price/Unit
            row.add(get(src, CnfItem.COL_BALANCE));   // D: Initial Stock ← previous Balance
            // E–onward left blank (IN QTY, Date, Balance formula, Out formula, daily cols)
            newRows.add(row);
        }
        if (!newRows.isEmpty()) {
            svc.writeBlock(currentTab + "!A" + (CnfItem.DATA_START_ROW + 1), newRows);
        }

        System.out.println("CnfMonthProvisioner: [" + currentTab + "] created successfully.");
        return currentTab;
    }

    private static String resolveMonthName(Month month, List<String> existingTabs) {
        String full  = month.getDisplayName(TextStyle.FULL,  Locale.ENGLISH).toUpperCase();
        String short3 = month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase();
        boolean useFull = existingTabs.stream().anyMatch(tab -> {
            for (Month m : Month.values()) {
                if (tab.equalsIgnoreCase(m.getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase()))
                    return true;
            }
            return false;
        });
        return useFull ? full : short3;
    }

    private static String findMostRecentTab(List<String> tabs, int currentMonthIdx) {
        for (int i = currentMonthIdx - 1; i >= 0; i--) {
            Month m = Month.of(i + 1);
            String s = m.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase();
            String f = m.getDisplayName(TextStyle.FULL,  Locale.ENGLISH).toUpperCase();
            for (String tab : tabs) {
                if (tab.equalsIgnoreCase(s) || tab.equalsIgnoreCase(f)) return tab;
            }
        }
        return tabs.isEmpty() ? null : tabs.get(tabs.size() - 1);
    }

    private static Object get(List<Object> row, int idx) {
        if (row == null || idx >= row.size() || row.get(idx) == null) return "";
        return row.get(idx).toString();
    }
}
