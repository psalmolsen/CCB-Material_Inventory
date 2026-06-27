package com.ccb;

public class SackGroupSummary {
    private final String name;
    private final String dateRange;
    private final int good;
    private final int reject;
    private final double rejectRate;
    private final boolean hasTotal;

    public SackGroupSummary(String name, String dateRange, int good, int reject, double rejectRate, boolean hasTotal) {
        this.name = name == null ? "" : name;
        this.dateRange = dateRange == null ? "" : dateRange;
        this.good = Math.max(0, good);
        this.reject = Math.max(0, reject);
        this.rejectRate = rejectRate;
        this.hasTotal = hasTotal;
    }

    public String getName() {
        return name;
    }

    public String getDateRange() {
        return dateRange;
    }

    public int getGood() {
        return good;
    }

    public int getReject() {
        return reject;
    }

    public double getRejectRate() {
        return rejectRate;
    }

    public boolean hasTotal() {
        return hasTotal;
    }
}
