package com.ccb;

import java.util.List;
import java.util.Map;

public class PelletsLSummary {
    private final int totalBlastingGood;
    private final int totalBlastingReject;
    private final int totalShifts;
    private final String topBrand;
    private final String topBagSize;
    private final double topBagSizePercentage;
    private final Map<String, int[]> byShift; // shift label -> [good, reject]
    private final Map<String, Integer> byBrand; // brand -> good output
    private final Map<String, Integer> byBagSize; // bag size -> count
    private final List<PelletsLRecord> allRecords;

    public PelletsLSummary(int totalBlastingGood, int totalBlastingReject, int totalShifts,
                          String topBrand, String topBagSize, double topBagSizePercentage,
                          Map<String, int[]> byShift, Map<String, Integer> byBrand,
                          Map<String, Integer> byBagSize, List<PelletsLRecord> allRecords) {
        this.totalBlastingGood = totalBlastingGood;
        this.totalBlastingReject = totalBlastingReject;
        this.totalShifts = totalShifts;
        this.topBrand = topBrand == null ? "" : topBrand;
        this.topBagSize = topBagSize == null ? "" : topBagSize;
        this.topBagSizePercentage = topBagSizePercentage;
        this.byShift = byShift;
        this.byBrand = byBrand;
        this.byBagSize = byBagSize;
        this.allRecords = allRecords;
    }

    public int getTotalBlastingGood() {
        return totalBlastingGood;
    }

    public int getTotalBlastingReject() {
        return totalBlastingReject;
    }

    public int getTotalShifts() {
        return totalShifts;
    }

    public String getTopBrand() {
        return topBrand;
    }

    public String getTopBagSize() {
        return topBagSize;
    }

    public double getTopBagSizePercentage() {
        return topBagSizePercentage;
    }

    public Map<String, int[]> getByShift() {
        return byShift;
    }

    public Map<String, Integer> getByBrand() {
        return byBrand;
    }

    public Map<String, Integer> getByBagSize() {
        return byBagSize;
    }

    public List<PelletsLRecord> getAllRecords() {
        return allRecords;
    }

    public double getOverallRejectRate() {
        int total = totalBlastingGood + totalBlastingReject;
        if (total <= 0) {
            return 0.0;
        }
        return (totalBlastingReject * 100.0) / total;
    }
}
