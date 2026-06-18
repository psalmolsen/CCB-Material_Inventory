package com.ccb;

import java.util.ArrayList;
import java.util.List;

public class SheetMapper {

    /*
     * Sheet column mapping (0-indexed):
     * 0  = Date
     * 1  = Code No.
     * 2  = Item/Description
     * 3  = UOM
     * 4  = Stock In (In Quantity)
     * 5  = Balance Quantity   (computed — read only)
     * 6  = Out Quantity
     * 7  = Day 1
     * 8  = Day 2
     * ...
     * 37 = Day 31
     * 38 = Total Issued       (computed — read only)
     */

    private static final int COL_DATE        = 6;
    private static final int COL_CODE        = 1;
    private static final int COL_DESC        = 2;
    private static final int COL_UOM         = 3;
    private static final int COL_PRICE       = 4;
    private static final int COL_STOCK_IN    = 5;
    private static final int COL_BALANCE     = 7;  // read-only
    private static final int COL_OUT_QTY     = 8;
    private static final int COL_DAY_START   = 10; // Day 1 starts at index 10
    private static final int COL_TOTAL_ISSUED = 41; // read-only

    /** Convert a raw sheet row into an InventoryItem */
    public static InventoryItem fromRow(List<Object> row) {
        InventoryItem item = new InventoryItem();
        item.setCodeNo(getCellString(row, COL_CODE));
        item.setDescription(getCellString(row, COL_DESC));
        item.setUom(getCellString(row, COL_UOM));
        item.setUnitPrice(getCellDouble(row, COL_PRICE));
        item.setStockIn(getCellDouble(row, COL_STOCK_IN));
        item.setDate(getCellString(row, COL_DATE));
        item.setOutQuantity(getCellDouble(row, COL_OUT_QTY));

        for (int day = 1; day <= 31; day++) {
            int colIndex = COL_DAY_START + (day - 1);
            item.setDayValue(day, getCellDouble(row, colIndex));
        }
        return item;
    }

    /** Convert an InventoryItem back into a raw sheet row for writing */
    public static List<Object> toRow(InventoryItem item) {
        List<Object> row = new ArrayList<>();
        row.add("");                          // col 0 empty
        row.add(item.getCodeNo());            // col 1
        row.add(item.getDescription());       // col 2
        row.add(item.getUom());               // col 3
        row.add(item.getUnitPrice());         // col 4
        row.add(item.getStockIn());           // col 5
        row.add(item.getDate());              // col 6
        row.add(item.getBalanceQuantity());   // col 7 computed
        row.add(item.getOutQuantity());       // col 8
        row.add("");                          // col 9 empty

        for (int day = 1; day <= 31; day++) {
            row.add(item.getDayValue(day));   // col 10-40
        }

        row.add(item.getTotalIssued());       // col 41 computed
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
        try { return Double.parseDouble(val); }
        catch (NumberFormatException e) { return 0.0; }
    }
}
