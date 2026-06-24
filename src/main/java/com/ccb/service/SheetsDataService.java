package com.ccb.service;

import com.ccb.CnfSheetService;
import com.ccb.controller.page.BrandPanelController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SheetsDataService {

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
            categories.add(buildCollarCategory(groupRows));
            categories.add(buildNameplateCategory(groupRows));
            categories.add(buildFootringCategory(groupRows));
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

    private static BrandPanelController.CategoryData buildCollarCategory(List<List<Object>> groupRows) {
        List<BrandPanelController.VariantData> variants = List.of(
                new BrandPanelController.VariantData(readVariantLabel(groupRows.get(0)), readTotalIssued(groupRows.get(0)), readBalanceQty(groupRows.get(0)), readDailyIssued(groupRows.get(0))),
                new BrandPanelController.VariantData(readVariantLabel(groupRows.get(1)), readTotalIssued(groupRows.get(1)), readBalanceQty(groupRows.get(1)), readDailyIssued(groupRows.get(1))),
                new BrandPanelController.VariantData(readVariantLabel(groupRows.get(2)), readTotalIssued(groupRows.get(2)), readBalanceQty(groupRows.get(2)), readDailyIssued(groupRows.get(2)))
        );
        return new BrandPanelController.CategoryData(
                readCategoryLabel(groupRows.get(0)),
                variants,
                "#7b6cf6",
                "#1e2e56",
                "#7b8fd4"
        );
    }

    private static BrandPanelController.CategoryData buildNameplateCategory(List<List<Object>> groupRows) {
        List<BrandPanelController.VariantData> variants = List.of(
                new BrandPanelController.VariantData(readVariantLabel(groupRows.get(3)), readTotalIssued(groupRows.get(3)), readBalanceQty(groupRows.get(3)), readDailyIssued(groupRows.get(3)))
        );
        return new BrandPanelController.CategoryData(
                readCategoryLabel(groupRows.get(3)),
                variants,
                "#4ecda0",
                "#1a3530",
                "#4ecda0"
        );
    }

    private static BrandPanelController.CategoryData buildFootringCategory(List<List<Object>> groupRows) {
        List<BrandPanelController.VariantData> variants = List.of(
                new BrandPanelController.VariantData(readVariantLabel(groupRows.get(4)), readTotalIssued(groupRows.get(4)), readBalanceQty(groupRows.get(4)), readDailyIssued(groupRows.get(4))),
                new BrandPanelController.VariantData(readVariantLabel(groupRows.get(5)), readTotalIssued(groupRows.get(5)), readBalanceQty(groupRows.get(5)), readDailyIssued(groupRows.get(5))),
                new BrandPanelController.VariantData(readVariantLabel(groupRows.get(6)), readTotalIssued(groupRows.get(6)), readBalanceQty(groupRows.get(6)), readDailyIssued(groupRows.get(6)))
        );
        return new BrandPanelController.CategoryData(
                readCategoryLabel(groupRows.get(4)),
                variants,
                "#4a90d9",
                "#1e2e56",
                "#7b8fd4"
        );
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
        return readInt(row, 9);
    }

    private static int readTotalIssued(List<Object> row) {
        if (row == null) {
            return 0;
        }
        for (int i = row.size() - 1; i >= 0; i--) {
            Object value = row.get(i);
            if (value == null || value.toString().isBlank()) {
                continue;
            }
            int parsed = parseInt(value);
            if (parsed != Integer.MIN_VALUE) {
                return parsed;
            }
        }
        return 0;
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
        if (row == null || row.size() <= 11) {
            return daily;
        }

        int lastNonEmpty = 10;
        for (int i = row.size() - 1; i >= 11; i--) {
            Object value = row.get(i);
            if (value == null || value.toString().trim().isEmpty()) {
                continue;
            }
            lastNonEmpty = i;
            break;
        }

        for (int i = 11; i < lastNonEmpty; i++) {
            daily.add(readInt(row, i));
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
