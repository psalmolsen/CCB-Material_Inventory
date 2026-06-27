package com.ccb;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PelletsDataService {

    public static PelletsSummary compute(List<PelletsRecord> records, List<String> completedSackGroups) {
        if (records == null || records.isEmpty()) {
            return new PelletsSummary(0, 0, 0.0, 0, "—", Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList());
        }

        int totalGood = 0;
        int totalReject = 0;
        LocalDate overallMinDate = null;
        LocalDate overallMaxDate = null;

        // Brand aggregation: key=brand, value=[good, reject]
        Map<String, int[]> brandAggMap = new HashMap<>();
        // Size aggregation: key=kg, value=good
        Map<String, Integer> kgsAggMap = new LinkedHashMap<>();
        kgsAggMap.put("11kg", 0);
        kgsAggMap.put("22kg", 0);
        kgsAggMap.put("50kg", 0);

        // Sack group helper storage to aggregate records by sack group
        Map<String, SackGroupHelper> sackGroupHelpers = new LinkedHashMap<>();

        for (PelletsRecord r : records) {
            totalGood += r.getShotGood();
            totalReject += r.getShotReject();

            LocalDate d = r.getParsedDate();
            if (d != null) {
                if (overallMinDate == null || d.isBefore(overallMinDate)) {
                    overallMinDate = d;
                }
                if (overallMaxDate == null || d.isAfter(overallMaxDate)) {
                    overallMaxDate = d;
                }
            }

            // Brand
            String brand = r.getBrand();
            if (!brand.isBlank()) {
                int[] vals = brandAggMap.computeIfAbsent(brand, k -> new int[2]);
                vals[0] += r.getShotGood();
                vals[1] += r.getShotReject();
            }

            // Size
            String size = r.getKgs();
            if (kgsAggMap.containsKey(size)) {
                kgsAggMap.put(size, kgsAggMap.get(size) + r.getShotGood());
            }

            // Sack Group
            String sg = r.getSackGroup();
            if (!sg.isEmpty()) {
                SackGroupHelper helper = sackGroupHelpers.computeIfAbsent(sg, k -> new SackGroupHelper(k));
                helper.addRecord(r);
            }
        }

        // 1. Sort Brand Map by Reject DESC
        List<Map.Entry<String, int[]>> brandEntries = new ArrayList<>(brandAggMap.entrySet());
        brandEntries.sort((e1, e2) -> Integer.compare(e2.getValue()[1], e1.getValue()[1]));
        Map<String, int[]> sortedBrandMap = new LinkedHashMap<>();
        for (var entry : brandEntries) {
            sortedBrandMap.put(entry.getKey(), entry.getValue());
        }

        // 2. Build Sack Group Summaries in order of appearance
        List<SackGroupSummary> sackSummaries = new ArrayList<>();
        for (SackGroupHelper helper : sackGroupHelpers.values()) {
            boolean hasTotal = completedSackGroups.contains(helper.groupName);
            sackSummaries.add(helper.toSummary(hasTotal));
        }

        // Overall reject rate
        double overallRate = 0.0;
        int sumTotal = totalGood + totalReject;
        if (sumTotal > 0) {
            overallRate = (totalReject * 100.0) / sumTotal;
        }

        String overallDateRangeStr = formatFullDateRange(overallMinDate, overallMaxDate);

        return new PelletsSummary(
                totalGood,
                totalReject,
                overallRate,
                sackSummaries.size(),
                overallDateRangeStr,
                sortedBrandMap,
                kgsAggMap,
                sackSummaries
        );
    }

    private static class SackGroupHelper {
        final String groupName;
        int good = 0;
        int reject = 0;
        LocalDate minDate = null;
        LocalDate maxDate = null;

        SackGroupHelper(String name) {
            this.groupName = name;
        }

        void addRecord(PelletsRecord r) {
            good += r.getShotGood();
            reject += r.getShotReject();
            LocalDate d = r.getParsedDate();
            if (d != null) {
                if (minDate == null || d.isBefore(minDate)) {
                    minDate = d;
                }
                if (maxDate == null || d.isAfter(maxDate)) {
                    maxDate = d;
                }
            }
        }

        SackGroupSummary toSummary(boolean hasTotal) {
            double rate = 0.0;
            int total = good + reject;
            if (total > 0) {
                rate = (reject * 100.0) / total;
            }
            String dateRangeStr = formatDateRange(minDate, maxDate);
            String displayName = groupName + " Sack Report";
            return new SackGroupSummary(displayName, dateRangeStr, good, reject, rate, hasTotal);
        }
    }

    public static String formatDateRange(LocalDate start, LocalDate end) {
        if (start == null && end == null) return "—";
        if (start == null) start = end;
        if (end == null) end = start;
        if (start.equals(end)) {
            return formatSingleDate(start);
        }

        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);
        if (start.getYear() == end.getYear()) {
            if (start.getMonth() == end.getMonth()) {
                return start.format(monthFmt) + " " + start.getDayOfMonth() + "–" + end.getDayOfMonth();
            } else {
                return start.format(monthFmt) + " " + start.getDayOfMonth() + "–" + end.format(monthFmt) + " " + end.getDayOfMonth();
            }
        } else {
            return start.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)) + " – " + end.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH));
        }
    }

    public static String formatFullDateRange(LocalDate start, LocalDate end) {
        if (start == null && end == null) return "—";
        if (start == null) start = end;
        if (end == null) end = start;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
        if (start.getYear() == end.getYear()) {
            if (start.equals(end)) {
                return start.format(fmt) + ", " + start.getYear();
            }
            if (start.getMonth() == end.getMonth()) {
                return start.format(fmt) + " – " + end.getDayOfMonth() + ", " + start.getYear();
            } else {
                return start.format(fmt) + " – " + end.format(fmt) + ", " + start.getYear();
            }
        } else {
            return start.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)) + " – " + end.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH));
        }
    }

    private static String formatSingleDate(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH));
    }
}
