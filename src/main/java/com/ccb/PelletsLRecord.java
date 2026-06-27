package com.ccb;

import java.time.LocalDate;

public class PelletsLRecord {
    private final String date;
    private final String timeSlot;
    private final int blastingGood;
    private final int blastingReject;
    private final String brand;
    private final String bagSize;
    private final boolean isTotalRow;
    private final String shiftLabel;
    private final String sackGroup;
    private final LocalDate parsedDate;

    public PelletsLRecord(String date, String timeSlot, int blastingGood, int blastingReject, 
                          String brand, String bagSize, boolean isTotalRow, String shiftLabel, String sackGroup, LocalDate parsedDate) {
        this.date = date == null ? "" : date;
        this.timeSlot = timeSlot == null ? "" : timeSlot;
        this.blastingGood = Math.max(0, blastingGood);
        this.blastingReject = Math.max(0, blastingReject);
        this.brand = brand == null ? "" : brand;
        this.bagSize = bagSize == null ? "" : bagSize;
        this.isTotalRow = isTotalRow;
        this.shiftLabel = shiftLabel == null ? "" : shiftLabel;
        this.sackGroup = sackGroup == null ? "" : sackGroup;
        this.parsedDate = parsedDate;
    }

    public String getDate() {
        return date;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public int getBlastingGood() {
        return blastingGood;
    }

    public int getBlastingReject() {
        return blastingReject;
    }

    public String getBrand() {
        return brand;
    }

    public String getBagSize() {
        return bagSize;
    }

    public boolean isTotalRow() {
        return isTotalRow;
    }

    public String getShiftLabel() {
        return shiftLabel;
    }

    public String getSackGroup() {
        return sackGroup;
    }

    public LocalDate getParsedDate() {
        return parsedDate;
    }

    public double getRejectRate() {
        int total = blastingGood + blastingReject;
        if (total <= 0) {
            return 0.0;
        }
        return (blastingReject * 100.0) / total;
    }

    public String getMonthKey() {
        if (parsedDate == null) {
            return "";
        }
        return parsedDate.getYear() + "-" + String.format("%02d", parsedDate.getMonthValue());
    }
}
