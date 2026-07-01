package com.ccb;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class StationConsumptionRecord {
    private final LocalDate date;
    private final String station;
    private final String materialCode;
    private final String description;
    private final double quantity;
    private final String uom;
    private final double unitCost;
    private final double totalCost;
    private final String signature;

    public StationConsumptionRecord(LocalDate date, String station, String description, 
                                    double quantity, String uom, String signature) {
        this(date, station, "", description, quantity, uom, 0.0, quantity * 0.0, signature);
    }

    public StationConsumptionRecord(LocalDate date, String station, String materialCode, String description,
                                    double quantity, String uom, double unitCost, double totalCost, String signature) {
        this.date = date;
        this.station = station == null ? "" : station.trim();
        this.materialCode = materialCode == null ? "" : materialCode.trim();
        this.description = description == null ? "" : description.trim();
        this.quantity = quantity;
        this.uom = uom == null ? "" : uom.trim();
        this.unitCost = unitCost;
        this.totalCost = totalCost;
        this.signature = signature == null ? "" : signature.trim();
    }

    public LocalDate getDate() {
        return date;
    }

    public String getStation() {
        return station;
    }

    public String getMaterialCode() {
        return materialCode;
    }

    public String getDescription() {
        return description;
    }

    public double getQuantity() {
        return quantity;
    }

    public String getUom() {
        return uom;
    }

    public double getUnitCost() {
        return unitCost;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public String getSignature() {
        return signature;
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
