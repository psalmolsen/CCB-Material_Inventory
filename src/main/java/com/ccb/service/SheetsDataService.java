package com.ccb.service;

import com.ccb.CnfSheetService;
import com.ccb.controller.page.BrandPanelController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SheetsDataService {

    private static final int COL_UOM          = 3;
    private static final int COL_PRICE        = 4;
    private static final int COL_INITIAL      = 5;
    private static final int COL_IN_QTY       = 6;
    private static final int COL_BALANCE      = 9;
    private static final int COL_DAY_START    = 13;
    private static final int COL_TOTAL_ISSUED = 44;
    private static final int DAYS_IN_MONTH    = 31;
    public static List<BrandPanelController.BrandData> fetchCNFBrands(String sheetTabName) throws Exception {
        if (sheetTabName == null || sheetTabName.isBlank()) {
            sheetTabName = "MAY";
        }

        CnfSheetService service = new CnfSheetService();
        String resolvedTabName = resolveTabName(service, sheetTabName);
        List<List<Object>> rows = service.readSheet(resolvedTabName + "!A6:AZ");
        List<BrandPanelController.BrandData> brands = extractBrands(rows);

        if (brands.isEmpty()) {
            for (String tabName : service.getTabNames()) {
                if (tabName.equalsIgnoreCase(resolvedTabName)) {
                    continue;
                }
                List<List<Object>> fallbackRows = service.readSheet(tabName + "!A6:AZ");
                List<BrandPanelController.BrandData> fallbackBrands = extractBrands(fallbackRows);
                if (!fallbackBrands.isEmpty()) {
                    return fallbackBrands;
                }
            }
        }

        return brands;
    }

    private static List<BrandPanelController.BrandData> extractBrands(List<List<Object>> rows) {
        List<BrandPanelController.BrandData> brands = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return brands;
        }

        // Find the first row of each 7-row brand block by looking for the
        // COLLLAR row that also has a brand name in column A.
        for (int index = 0; index + 6 < rows.size(); ) {
            while (index + 6 < rows.size() && !isBrandGroupStart(rows.get(index))) {
                index++;
            }
            if (index + 6 >= rows.size()) {
                break;
            }

            List<List<Object>> groupRows = rows.subList(index, index + 7);
            if (!hasAnyValue(groupRows)) {
                index++;
                continue;
            }

            String brandName = readCell(groupRows.get(0), 0).trim();
            if (brandName.isBlank() || !looksLikeBrandName(brandName) || looksLikeHeader(brandName)) {
                index++;
                continue;
            }

            List<BrandPanelController.CategoryData> categories = new ArrayList<>();
            categories.add(buildCollarCategory(groupRows, 6 + index));
            categories.add(buildNameplateCategory(groupRows, 6 + index));
            categories.add(buildFootringCategory(groupRows, 6 + index));
            brands.add(new BrandPanelController.BrandData(brandName, categories));
            index += 7;
        }

        System.out.println("CNF brand parse: found " + brands.size() + " brand groups");
        return brands;
    }

    private static boolean isBrandGroupStart(List<Object> row) {
        String brandName = readCell(row, 0).trim();
        String category = readCell(row, 1).trim().toUpperCase(Locale.ROOT);
        return !brandName.isBlank() && looksLikeBrandName(brandName) && category.equals("COLLAR");
    }

    private static boolean looksLikeHeader(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).trim();
        return normalized.equals("brand") || normalized.equals("item") || normalized.equals("name") || normalized.contains("header");
    }

    private static boolean looksLikeBrandName(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).trim();
        return !normalized.contains("collar")
                && !normalized.contains("name")
                && !normalized.contains("foot")
                && !normalized.contains("plate")
                && !normalized.contains("variant")
                && !normalized.contains("total")
                && !normalized.contains("issued")
                && !normalized.contains("balance");
    }

    private static boolean hasAnyValue(List<List<Object>> groupRows) {
        for (List<Object> row : groupRows) {
            if (row == null) {
                continue;
            }
            for (Object value : row) {
                if (value != null && !value.toString().trim().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String resolveTabName(CnfSheetService service, String requestedTabName) throws Exception {
        if (requestedTabName == null || requestedTabName.isBlank()) {
            return "MAY";
        }

        List<String> tabs = service.getTabNames();
        String requested = requestedTabName.trim();
        for (String tab : tabs) {
            if (tab.equalsIgnoreCase(requested) || tab.toUpperCase(Locale.ROOT).startsWith(requested.toUpperCase(Locale.ROOT))) {
                return tab;
            }
        }

        return requested;
    }

    private static BrandPanelController.CategoryData buildCollarCategory(List<List<Object>> groupRows, int baseRow) {
        List<BrandPanelController.VariantData> variants = List.of(
                variantFromRow(groupRows.get(0), baseRow),
                variantFromRow(groupRows.get(1), baseRow + 1),
                variantFromRow(groupRows.get(2), baseRow + 2)
        );
        return new BrandPanelController.CategoryData(
                readCategoryLabel(groupRows.get(0)),
                variants,
                "#7b6cf6",
                "#1e2e56",
                "#7b8fd4"
        );
    }

    private static BrandPanelController.CategoryData buildNameplateCategory(List<List<Object>> groupRows, int baseRow) {
        List<BrandPanelController.VariantData> variants = List.of(
                variantFromRow(groupRows.get(3), baseRow + 3)
        );
        return new BrandPanelController.CategoryData(
                readCategoryLabel(groupRows.get(3)),
                variants,
                "#4ecda0",
                "#1a3530",
                "#4ecda0"
        );
    }

    private static BrandPanelController.CategoryData buildFootringCategory(List<List<Object>> groupRows, int baseRow) {
        List<BrandPanelController.VariantData> variants = List.of(
                variantFromRow(groupRows.get(4), baseRow + 4),
                variantFromRow(groupRows.get(5), baseRow + 5),
                variantFromRow(groupRows.get(6), baseRow + 6)
        );
        return new BrandPanelController.CategoryData(
                readCategoryLabel(groupRows.get(4)),
                variants,
                "#4a90d9",
                "#1e2e56",
                "#7b8fd4"
        );
    }

    private static BrandPanelController.VariantData variantFromRow(List<Object> row, int sheetRowNumber) {
        return new BrandPanelController.VariantData(
                readVariantLabel(row),
                readTotalIssued(row),
                readBalanceQty(row),
                readDailyIssued(row),
                readInitialStock(row),
                readReceivedQty(row),
                readUnitPrice(row),
                sheetRowNumber,
                readUom(row)
        );
    }

    private static String readUom(List<Object> row) {
        String value = readCell(row, COL_UOM).trim();
        return value.isBlank() ? "Pcs" : value;
    }

    private static String readCell(List<Object> row, int index) {
        if (row == null || index >= row.size()) {
            return "";
        }
        Object value = row.get(index);
        return value == null ? "" : value.toString();
    }

    private static String readCategoryLabel(List<Object> row) {
        String value = readCell(row, 1).trim();
        return value.isBlank() ? "" : value;
    }

    private static String readVariantLabel(List<Object> row) {
        String value = readCell(row, 2).trim();
        return value;
    }

    private static int readBalanceQty(List<Object> row) {
        return readInt(row, COL_BALANCE);
    }

    private static int readInitialStock(List<Object> row) {
        return readInt(row, COL_INITIAL);
    }

    private static int readReceivedQty(List<Object> row) {
        return readInt(row, COL_IN_QTY);
    }

    private static double readUnitPrice(List<Object> row) {
        String val = readCell(row, COL_PRICE).trim();
        if (val.isEmpty() || val.equalsIgnoreCase("n/a")) return 0.0;
        try { return Double.parseDouble(val); } catch (NumberFormatException e) { return 0.0; }
    }

    private static int readTotalIssued(List<Object> row) {
        int total = readInt(row, COL_TOTAL_ISSUED);
        if (total != 0) {
            return total;
        }
        return readDailyIssued(row).stream().mapToInt(Integer::intValue).sum();
    }

    private static int readInt(List<Object> row, int index) {
        if (row == null || index >= row.size()) {
            return 0;
        }
        int parsed = parseInt(row.get(index));
        return parsed == Integer.MIN_VALUE ? 0 : parsed;
    }

    private static List<Integer> readDailyIssued(List<Object> row) {
        List<Integer> daily = new ArrayList<>();
        if (row == null) {
            return daily;
        }
        for (int day = 0; day < DAYS_IN_MONTH; day++) {
            daily.add(readInt(row, COL_DAY_START + day));
        }
        return daily;
    }

    private static int parseInt(Object value) {
        if (value == null || value.toString().isBlank()) {
            return Integer.MIN_VALUE;
        }
        try {
            String text = value.toString().trim();
            if (text.isEmpty()) {
                return Integer.MIN_VALUE;
            }
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            if (text.contains(",")) {
                text = text.replace(",", "");
            }
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            try {
                double parsed = Double.parseDouble(value.toString().trim());
                return (int) Math.round(parsed);
            } catch (NumberFormatException ignored) {
                return Integer.MIN_VALUE;
            }
        }
    }
}
