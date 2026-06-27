package com.ccb;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents one row in the CNF sheet.
 *
 * Column mapping (0-indexed):
 * A(0) = Item name (used as unique identifier — no code col)
 * B(1) = UOM
 * C(2) = Price/Unit
 * D(3) = Initial Stock (carried from previous month's balance)
 * E(4) = IN QUANTITY (received this month) — app writes here
 * F(5) = Date — app writes here
 * G(6) = divider (skip)
 * H(7) = Balance Qty (sheet formula: D+E-AQ, never written by app)
 * I(8) = Out QTY (sheet formula: SUM(L:AP), never written by app)
 * J(9) = divider (skip)
 * K(10) = divider (skip)
 * L(11) = Day 1 ... AP(41) = Day 31 — app writes daily stock-out here
 *
 * Data starts at row 6 (0-indexed: row 5).
 */
public class CnfItem {

    public static final int DATA_START_ROW = 5; // 0-indexed → row 6 in sheet
    public static final int COL_ITEM = 0;
    public static final int COL_UOM = 1;
    public static final int COL_PRICE = 2;
    public static final int COL_INITIAL = 3;
    public static final int COL_RECEIVED = 4;
    public static final int COL_DATE = 5;
    // G(6) = divider
    public static final int COL_BALANCE = 7;
    public static final int COL_OUT_QTY = 8;
    // J(9), K(10) = dividers
    public static final int COL_DAY_START = 11; // L = Day 1

    // Item type classification keywords
    public enum CnfType {
        COLLAR, NAMEPLATE, FOOTRING, OTHER
    }

    private String itemName;
    private String uom;
    private double unitPrice;
    private double initialStock;
    private double receivedQuantity;
    private String date;
    private double currentBalance;
    private double outQuantity;
    private int sheetRowNumber;
    private String sheetTabName;
    private Map<Integer, Double> dailyOut = new HashMap<>();

    public CnfItem() {
    }

    // ── Computed ──────────────────────────────────────────────────────────────

    public double getTotalIssued() {
        return dailyOut.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public double getDayValue(int day) {
        return dailyOut.getOrDefault(day, 0.0);
    }

    public void setDayValue(int day, double value) {
        dailyOut.put(day, value);
    }

    /** Classify this item based on its name */
    public CnfType getType() {
        if (itemName == null)
            return CnfType.OTHER;
        String upper = itemName.toUpperCase();
        if (upper.contains("COLLAR"))
            return CnfType.COLLAR;
        if (upper.contains("NAME PLATE") || upper.contains("NAMEPLATE"))
            return CnfType.NAMEPLATE;
        if (upper.contains("FOOT RING") || upper.contains("FOOTRING"))
            return CnfType.FOOTRING;
        return CnfType.OTHER;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String v) {
        this.itemName = v;
    }

    public String getUom() {
        return uom;
    }

    public void setUom(String v) {
        this.uom = v;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(double v) {
        this.unitPrice = v;
    }

    public double getInitialStock() {
        return initialStock;
    }

    public void setInitialStock(double v) {
        this.initialStock = v;
    }

    public double getReceivedQuantity() {
        return receivedQuantity;
    }

    public void setReceivedQuantity(double v) {
        this.receivedQuantity = v;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String v) {
        this.date = v;
    }

    public double getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(double v) {
        this.currentBalance = v;
    }

    public double getOutQuantity() {
        return outQuantity;
    }

    public void setOutQuantity(double v) {
        this.outQuantity = v;
    }

    public int getSheetRowNumber() {
        return sheetRowNumber;
    }

    public void setSheetRowNumber(int v) {
        this.sheetRowNumber = v;
    }

    public String getSheetTabName() {
        return sheetTabName;
    }

    public void setSheetTabName(String v) {
        this.sheetTabName = v;
    }

    public Map<Integer, Double> getDailyOut() {
        return dailyOut;
    }
}
