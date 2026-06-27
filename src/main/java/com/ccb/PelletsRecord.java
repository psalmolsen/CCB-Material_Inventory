package com.ccb;

import java.time.LocalDate;

public class PelletsRecord {
    private final String date;
    private final String timeSlot;
    private final int shotGood;
    private final int shotReject;
    private final String brand;
    private final String kgs;
    private final String sackGroup;
    private final LocalDate parsedDate;

    public PelletsRecord(String date, String timeSlot, int shotGood, int shotReject, String brand, String kgs, String sackGroup, LocalDate parsedDate) {
        this.date = date == null ? "" : date;
        this.timeSlot = timeSlot == null ? "" : timeSlot;
        this.shotGood = Math.max(0, shotGood);
        this.shotReject = Math.max(0, shotReject);
        this.brand = brand == null ? "" : brand;
        this.kgs = kgs == null ? "" : kgs;
        this.sackGroup = sackGroup == null ? "" : sackGroup;
        this.parsedDate = parsedDate;
    }

    public String getDate() {
        return date;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public int getShotGood() {
        return shotGood;
    }

    public int getShotReject() {
        return shotReject;
    }

    public String getBrand() {
        return brand;
    }

    public String getKgs() {
        return kgs;
    }

    public String getSackGroup() {
        return sackGroup;
    }

    public LocalDate getParsedDate() {
        return parsedDate;
    }

    public double getRejectRate() {
        int total = shotGood + shotReject;
        if (total <= 0) {
            return 0.0;
        }
        return (shotReject * 100.0) / total;
    }

    public String getMonthKey() {
        if (parsedDate == null) {
            return "";
        }
        return parsedDate.getYear() + "-" + String.format("%02d", parsedDate.getMonthValue());
    }
}
