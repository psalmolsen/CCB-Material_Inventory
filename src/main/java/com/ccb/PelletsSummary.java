package com.ccb;

import java.util.List;
import java.util.Map;

public class PelletsSummary {
    private final int totalGood;
    private final int totalReject;
    private final double overallRejectRate;
    private final int totalSackGroups;
    private final String dateRange;
    private final Map<String, int[]> byBrand;
    private final Map<String, Integer> byKgs;
    private final List<SackGroupSummary> bySackGroup;

    public PelletsSummary(int totalGood, int totalReject, double overallRejectRate, int totalSackGroups,
                          String dateRange, Map<String, int[]> byBrand, Map<String, Integer> byKgs,
                          List<SackGroupSummary> bySackGroup) {
        this.totalGood = totalGood;
        this.totalReject = totalReject;
        this.overallRejectRate = overallRejectRate;
        this.totalSackGroups = totalSackGroups;
        this.dateRange = dateRange == null ? "" : dateRange;
        this.byBrand = byBrand;
        this.byKgs = byKgs;
        this.bySackGroup = bySackGroup;
    }

    public int getTotalGood() {
        return totalGood;
    }

    public int getTotalReject() {
        return totalReject;
    }

    public double getOverallRejectRate() {
        return overallRejectRate;
    }

    public int getTotalSackGroups() {
        return totalSackGroups;
    }

    public String getDateRange() {
        return dateRange;
    }

    public Map<String, int[]> getByBrand() {
        return byBrand;
    }

    public Map<String, Integer> getByKgs() {
        return byKgs;
    }

    public List<SackGroupSummary> getBySackGroup() {
        return bySackGroup;
    }
}
