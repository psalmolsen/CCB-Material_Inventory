package com.ccb;

import java.util.ArrayList;
import java.util.List;

public class SheetMapper {

    /*
     * Sheet column mapping (0-indexed):
     * 0  = Date / empty
     * 1  = Code No.
     * 2  = Item/Description
     * 3  = UOM
     * 4  = Price / Unit
     * 5  = Initial Stock
     * 6  = Received / In Quantity
     * 7  = empty divider
     * 8  = Balance Quantity
     * 9  = Out Quantity
     * 10 = empty divider
     * 11 = Day 1
     * 12 = Day 2
     * ...
     * 41 = Day 31
     * 42 = Total Issued
     */

    private static final int COL_DATE = 0;
    private static final int COL_CODE = 1;
    private static final int COL_DESC = 2;
    private static final int COL_UOM = 3;
    private static final int COL_PRICE = 4;
    private static final int COL_INITIAL_STOCK = 5;
    private static final int COL_RECEIVED = 6;
    private static final int COL_BALANCE = 8;
    private static final int COL_OUT_QTY = 9;
    private static final int COL_DAY_START = 11;
    private static final int COL_TOTAL_ISSUED = 42;

    /** Convert a raw sheet row into an InventoryItem */
    public static InventoryItem fromRow(List<Object> row) {
        return fromRow(row, -1);
    }

    /** Convert a raw sheet row into an InventoryItem with its sheet row number. */
    public static InventoryItem fromRow(List<Object> row, int sheetRowNumber) {
        InventoryItem item = new InventoryItem();
        item.setDate(getCellString(row, COL_DATE));
        item.setCodeNo(getCellString(row, COL_CODE));
        item.setDescription(getCellString(row, COL_DESC));
        item.setUom(getCellString(row, COL_UOM));
        item.setUnitPrice(getCellDouble(row, COL_PRICE));
        item.setInitialStock(getCellDouble(row, COL_INITIAL_STOCK));
        item.setReceivedQuantity(getCellDouble(row, COL_RECEIVED));
        item.setCurrentBalance(getCellDouble(row, COL_BALANCE));
        item.setOutQuantity(getCellDouble(row, COL_OUT_QTY));

        for (int day = 1; day <= 31; day++) {
            int colIndex = COL_DAY_START + (day - 1);
            item.setDayValue(day, getCellDouble(row, colIndex));
        }

        if (hasCell(row, COL_TOTAL_ISSUED)) {
            item.setIssuedQuantity(getCellDouble(row, COL_TOTAL_ISSUED));
        } else {
            item.setIssuedQuantity(item.getTotalIssued());
        }

        if (!hasCell(row, COL_BALANCE)) {
            item.setCurrentBalance(item.getInitialStock() + item.getReceivedQuantity() - item.getIssuedQuantity());
        }

        if (sheetRowNumber > 0) {
            item.setSheetRowNumber(sheetRowNumber);
        }

        return item;
    }

    /** Convert an InventoryItem back into a raw sheet row for writing */
    public static List<Object> toRow(InventoryItem item) {
        List<Object> row = new ArrayList<>();
        row.add(item.getDate());              // col 0
        row.add(item.getCodeNo());            // col 1
        row.add(item.getDescription());       // col 2
        row.add(item.getUom());               // col 3
        row.add(item.getUnitPrice());         // col 4
        row.add(item.getInitialStock());      // col 5
        row.add(item.getReceivedQuantity());  // col 6
        row.add("");                         // col 7 empty
        row.add(item.getCurrentBalance());    // col 8
        row.add(item.getOutQuantity());       // col 9
        row.add("");                         // col 10 empty

        for (int day = 1; day <= 31; day++) {
            row.add(item.getDayValue(day));    // col 11-41
        }

        row.add(item.getIssuedQuantity());    // col 42
        return row;
    }

    private static String getCellString(List<Object> row, int index) {
        if (row == null || index >= row.size()) return "";
        Object val = row.get(index);
        return val != null ? val.toString() : "";
    }

    private static double getCellDouble(List<Object> row, int index) {
        String val = getCellString(row, index);
        if (val.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private static boolean hasCell(List<Object> row, int index) {
        return row != null && index < row.size() && row.get(index) != null && !row.get(index).toString().isBlank();
    }
}
