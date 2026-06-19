package com.ccb;

import java.util.HashMap;
import java.util.Map;

public class InventoryItem {

    private String date;
    private String codeNo;
    private String description;
    private String uom;
    private double unitPrice;
    private double initialStock;
    private double receivedQuantity;
    private double currentBalance;
    private double outQuantity;
    private double issuedQuantity;
    private int sheetRowNumber;
    private String sheetTabName;

    // Day 1–31 stock out values
    private Map<Integer, Double> dailyOut = new HashMap<>();

    public InventoryItem() {}

    // Computed fields — not stored directly, calculated on the fly
    public double getTotalIssued() {
        return dailyOut.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public double getBalanceQuantity() {
        return currentBalance;
    }

    public double getDayValue(int day) {
        return dailyOut.getOrDefault(day, 0.0);
    }

    public void setDayValue(int day, double value) {
        dailyOut.put(day, value);
    }

    // Getters and Setters
    public double getTotalPrice() {
        return unitPrice * getIssuedQuantity();
    }

    public double getIssuedQuantity()               { return issuedQuantity; }
    public void setIssuedQuantity(double issued)    { this.issuedQuantity = issued; }

    public double getInitialStock()                 { return initialStock; }
    public void setInitialStock(double initial)     { this.initialStock = initial; }

    public double getReceivedQuantity()             { return receivedQuantity; }
    public void setReceivedQuantity(double received){ this.receivedQuantity = received; }

    public double getCurrentBalance()               { return currentBalance; }
    public void setCurrentBalance(double balance)   { this.currentBalance = balance; }

    public double getUnitPrice()               { return unitPrice; }
    public void setUnitPrice(double unitPrice)  { this.unitPrice = unitPrice; }

    public String getDate()                     { return date; }
    public void setDate(String date)            { this.date = date; }

    public String getCodeNo()                   { return codeNo; }
    public void setCodeNo(String codeNo)        { this.codeNo = codeNo; }

    public String getDescription()              { return description; }
    public void setDescription(String d)        { this.description = d; }

    public String getUom()                      { return uom; }
    public void setUom(String uom)              { this.uom = uom; }

    public double getStockIn()                  { return initialStock; }
    public void setStockIn(double stockIn)      { this.initialStock = stockIn; }

    public double getOutQuantity()              { return outQuantity; }
    public void setOutQuantity(double out)      { this.outQuantity = out; }

    public Map<Integer, Double> getDailyOut()   { return dailyOut; }
    public void setDailyOut(Map<Integer, Double> d) { this.dailyOut = d; }

    public int getSheetRowNumber()              { return sheetRowNumber; }
    public void setSheetRowNumber(int rowNumber){ this.sheetRowNumber = rowNumber; }

    public String getSheetTabName()             { return sheetTabName; }
    public void setSheetTabName(String tabName) { this.sheetTabName = tabName; }
}
