package com.ccb;

import java.util.*;

public class PelletsLDataService {

    public static PelletsLSummary compute(List<PelletsLRecord> records) {
        if (records == null || records.isEmpty()) {
            return new PelletsLSummary(0, 0, 0, "", "", 0.0,
                    Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList());
        }

        int totalBlastingGood = 0;
        int totalBlastingReject = 0;

        // Shift aggregation: shift label -> [good, reject]
        Map<String, int[]> shiftAggMap = new LinkedHashMap<>();

        // Brand aggregation: brand -> good output (for summary computation)
        Map<String, Integer> brandAggMap = new HashMap<>();

        // Bag size aggregation: bag size -> count of entries
        Map<String, Integer> bagSizeCountMap = new HashMap<>();

        // Track unique shifts
        Set<String> uniqueShifts = new HashSet<>();

        for (PelletsLRecord r : records) {
            // Only use non-total rows for summary computation
            if (r.isTotalRow()) {
                continue;
            }

            totalBlastingGood += r.getBlastingGood();
            totalBlastingReject += r.getBlastingReject();

            // Shift aggregation (use total row values when available, otherwise sum individual records)
            String shiftLabel = r.getShiftLabel();
            if (!shiftLabel.isEmpty()) {
                uniqueShifts.add(shiftLabel);
                int[] shiftVals = shiftAggMap.computeIfAbsent(shiftLabel, k -> new int[2]);
                shiftVals[0] += r.getBlastingGood();
                shiftVals[1] += r.getBlastingReject();
            }

            // Brand aggregation (by good output)
            String brand = r.getBrand();
            if (!brand.isEmpty()) {
                brandAggMap.put(brand, brandAggMap.getOrDefault(brand, 0) + r.getBlastingGood());
            }

            // Bag size count
            String bagSize = r.getBagSize();
            if (!bagSize.isEmpty()) {
                bagSizeCountMap.put(bagSize, bagSizeCountMap.getOrDefault(bagSize, 0) + 1);
            }
        }

        // Find top brand (by good output)
        String topBrand = "";
        int maxBrandOutput = 0;
        for (Map.Entry<String, Integer> entry : brandAggMap.entrySet()) {
            if (entry.getValue() > maxBrandOutput) {
                maxBrandOutput = entry.getValue();
                topBrand = entry.getKey();
            }
        }

        // Find top bag size (by count)
        String topBagSize = "";
        int maxBagSizeCount = 0;
        int totalBagSizeEntries = 0;
        for (Map.Entry<String, Integer> entry : bagSizeCountMap.entrySet()) {
            totalBagSizeEntries += entry.getValue();
            if (entry.getValue() > maxBagSizeCount) {
                maxBagSizeCount = entry.getValue();
                topBagSize = entry.getKey();
            }
        }

        // Calculate top bag size percentage
        double topBagSizePercentage = 0.0;
        if (totalBagSizeEntries > 0 && maxBagSizeCount > 0) {
            topBagSizePercentage = (maxBagSizeCount * 100.0) / totalBagSizeEntries;
        }

        // Sort brand map by good output descending
        List<Map.Entry<String, Integer>> brandEntries = new ArrayList<>(brandAggMap.entrySet());
        brandEntries.sort((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()));
        Map<String, Integer> sortedBrandMap = new LinkedHashMap<>();
        for (var entry : brandEntries) {
            sortedBrandMap.put(entry.getKey(), entry.getValue());
        }

        // Sort bag size map by count descending
        List<Map.Entry<String, Integer>> bagSizeEntries = new ArrayList<>(bagSizeCountMap.entrySet());
        bagSizeEntries.sort((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()));
        Map<String, Integer> sortedBagSizeMap = new LinkedHashMap<>();
        for (var entry : bagSizeEntries) {
            sortedBagSizeMap.put(entry.getKey(), entry.getValue());
        }

        // Count total shifts
        int totalShifts = uniqueShifts.size();

        return new PelletsLSummary(
                totalBlastingGood,
                totalBlastingReject,
                totalShifts,
                topBrand,
                topBagSize,
                topBagSizePercentage,
                shiftAggMap,
                sortedBrandMap,
                sortedBagSizeMap,
                records
        );
    }
}
