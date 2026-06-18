package com.ccb;

import java.util.HashMap;
import java.util.Map;

public class InventoryItem {

    private String date;
    private String codeNo;
    private String description;
    private String uom;
    private double unitPrice;
    private double stockIn;
    private double outQuantity;

    // Day 1–31 stock out values
    private Map<Integer, Double> dailyOut = new HashMap<>();

    public InventoryItem() {}

    // Computed fields — not stored directly, calculated on the fly
    public double getTotalIssued() {
        return dailyOut.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public double getBalanceQuantity() {
        return stockIn - getTotalIssued();
    }

    public double getDayValue(int day) {
        return dailyOut.getOrDefault(day, 0.0);
    }

    public void setDayValue(int day, double value) {
        dailyOut.put(day, value);
    }

    // Getters and Setters
    public double getTotalPrice() {
        return unitPrice * getTotalIssued();
    }

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

    public double getStockIn()                  { return stockIn; }
    public void setStockIn(double stockIn)      { this.stockIn = stockIn; }

    public double getOutQuantity()              { return outQuantity; }
    public void setOutQuantity(double out)      { this.outQuantity = out; }

    public Map<Integer, Double> getDailyOut()   { return dailyOut; }
    public void setDailyOut(Map<Integer, Double> d) { this.dailyOut = d; }
}
