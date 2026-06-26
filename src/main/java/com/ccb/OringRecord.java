package com.ccb;

import java.time.LocalDate;

public class OringRecord {

    private final String date;
    private final String timeSlot;
    private final String valveCameFrom;
    private final int valvesRepaired;
    private final String installedTo;
    private final int good;
    private final int rejected;
    private final String remarks;
    private final LocalDate parsedDate;

    public OringRecord(String date,
                       String timeSlot,
                       String valveCameFrom,
                       int valvesRepaired,
                       String installedTo,
                       int good,
                       int rejected,
                       String remarks,
                       LocalDate parsedDate) {
        this.date = date == null ? "" : date;
        this.timeSlot = timeSlot == null ? "" : timeSlot;
        this.valveCameFrom = valveCameFrom == null ? "" : valveCameFrom;
        this.valvesRepaired = Math.max(0, valvesRepaired);
        this.installedTo = installedTo == null ? "" : installedTo;
        this.good = Math.max(0, good);
        this.rejected = Math.max(0, rejected);
        this.remarks = remarks == null ? "" : remarks;
        this.parsedDate = parsedDate;
    }

    public String getDate() {
        return date;
    }

    public String getTimeSlot() {
        return timeSlot;
    }

    public String getValveCameFrom() {
        return valveCameFrom;
    }

    public int getValvesRepaired() {
        return valvesRepaired;
    }

    public String getInstalledTo() {
        return installedTo;
    }

    public int getGood() {
        return good;
    }

    public int getRejected() {
        return rejected;
    }

    public String getRemarks() {
        return remarks;
    }

    public LocalDate getParsedDate() {
        return parsedDate;
    }

    public boolean isFlagged() {
        return rejected > 0;
    }

    public double getRejectRate() {
        if (good + rejected <= 0) {
            return 0.0;
        }
        return (rejected * 100.0) / (good + rejected);
    }

    public String getMonthKey() {
        if (parsedDate == null) {
            return "";
        }
        return parsedDate.getYear() + "-" + String.format("%02d", parsedDate.getMonthValue());
    }
}
