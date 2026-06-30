package com.ccb;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class StationConsumptionRecord {
    private final LocalDate date;
    private final String station;
    private final String materialName;
    private final double quantity;
    private final String uom;
    private final double unitPrice;
    private final double totalCost;

    public StationConsumptionRecord(LocalDate date, String station, String materialName, 
                                    double quantity, String uom, double unitPrice, double totalCost) {
        this.date = date;
        this.station = station == null ? "" : station.trim();
        this.materialName = materialName == null ? "" : materialName.trim();
        this.quantity = quantity;
        this.uom = uom == null ? "" : uom.trim();
        this.unitPrice = unitPrice;
        this.totalCost = totalCost;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getStation() {
        return station;
    }

    public String getMaterialName() {
        return materialName;
    }

    public double getQuantity() {
        return quantity;
    }

    public String getUom() {
        return uom;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public String getDateString() {
        if (date == null) {
            return "";
        }
        return date.format(DateTimeFormatter.ofPattern("M/d/yyyy"));
    }

    public String getMonthKey() {
        if (date == null) {
            return "";
        }
        return date.getYear() + "-" + String.format("%02d", date.getMonthValue());
    }

    public static LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String text = value.trim();
        
        List<DateTimeFormatter> formats = List.of(
            DateTimeFormatter.ofPattern("M/d/uu"),
            DateTimeFormatter.ofPattern("M/d/uuuu"),
            DateTimeFormatter.ofPattern("MM/dd/uu"),
            DateTimeFormatter.ofPattern("MM/dd/uuuu"),
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("MMM d, uuuu"),
            DateTimeFormatter.ofPattern("d MMM uuuu")
        );
        
        for (DateTimeFormatter formatter : formats) {
            try {
                return LocalDate.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }
}
