package com.ccb;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PelletsLSheetService {

    private static final String SPREADSHEET_ID = "13a0XL9TojETDZ6V4KWaJsKjj_PLkAIwjqwyVZ5-kCVs";
    private static final String CREDENTIALS_PATH = "/com/ccb/credentials/service-account.json";
    private static final String APP_NAME = "CCB Inventory System";

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("M/d/uu"),
            DateTimeFormatter.ofPattern("M/d/uuuu"),
            DateTimeFormatter.ofPattern("MM/dd/uu"),
            DateTimeFormatter.ofPattern("MM/dd/uuuu"),
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH)
    );

    private final Sheets sheetsService;

    public PelletsLSheetService() throws Exception {
        InputStream stream = getClass().getResourceAsStream(CREDENTIALS_PATH);
        if (stream == null) {
            throw new RuntimeException("service-account.json not found in resources.");
        }

        GoogleCredentials credentials = GoogleCredentials
                .fromStream(stream)
                .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS_READONLY));

        sheetsService = new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APP_NAME)
                .build();
    }

    public List<PelletsLRecord> readRecords() throws Exception {
        // Read A to H from Sheet1
        ValueRange response = sheetsService.spreadsheets().values()
                .get(SPREADSHEET_ID, "Sheet1!A:H")
                .execute();
        List<List<Object>> rows = response.getValues();
        if (rows == null || rows.size() <= 2) {
            return Collections.emptyList();
        }

        List<PelletsLRecord> records = new ArrayList<>();
        String currentSackGroup = "";

        // Skip row 0 (title) and row 1 (header), start from row 2
        for (int i = 2; i < rows.size(); i++) {
            List<Object> row = rows.get(i);

            // Check if row is completely empty - skip but continue parsing
            if (isRowEmpty(row)) {
                continue;
            }

            // Read columns A-F
            String dateStr = cell(row, 0);
            String timeSlot = cell(row, 1);
            String goodStr = cell(row, 2);
            String rejectStr = cell(row, 3);
            String brand = cell(row, 4);
            String bagSize = cell(row, 5);
            String colG = cell(row, 6);

            // SACK SEPARATOR ROW DETECTION
            // A row is a separator if:
            // 1. Columns A-F are empty OR
            // 2. Column B is empty and column G has text OR
            // 3. Column G contains "sack" and columns C-D (good/reject) are empty (total row separator)
            boolean isSeparatorRow = isColumnsAtoFEmpty(row) || (timeSlot.isEmpty() && !colG.isEmpty()) || (colG.toLowerCase().contains("sack") && goodStr.isEmpty() && rejectStr.isEmpty());
            if (isSeparatorRow && colG.toLowerCase().contains("sack")) {
                // Extract sack group label from column G
                currentSackGroup = colG.trim();
                continue; // Skip this separator row
            }

            // TOTAL ROW DETECTION
            boolean isTotalRow = false;
            if (timeSlot.equalsIgnoreCase("Total")) {
                isTotalRow = true;
            } else if (timeSlot.isEmpty() && (!goodStr.isEmpty() || !rejectStr.isEmpty())) {
                // Row has no time slot but has values in C/D - likely a total row
                int goodVal = readInt(row, 2);
                int rejectVal = readInt(row, 3);
                if (goodVal > 0 || rejectVal > 0) {
                    isTotalRow = true;
                }
            }

            // Parse numeric values
            int goodVal = readInt(row, 2);
            int rejectVal = readInt(row, 3);

            // Skip if both good and reject are 0 and not a total row
            if (!isTotalRow && goodVal == 0 && rejectVal == 0) {
                continue;
            }

            // Normalize brand and bag size
            String normalizedBrand = normalizeBrand(brand);
            String normalizedBagSize = normalizeBagSize(bagSize);

            records.add(new PelletsLRecord(
                    dateStr,
                    timeSlot,
                    goodVal,
                    rejectVal,
                    normalizedBrand,
                    normalizedBagSize,
                    isTotalRow,
                    "", // shiftLabel - will be assigned based on sack group
                    currentSackGroup,
                    parseDate(dateStr)
            ));
        }

        // Post-process: assign shift labels based on sack groups
        records = assignShiftLabelsFromSackGroups(records);

        return records;
    }

    private List<PelletsLRecord> assignShiftLabelsFromSackGroups(List<PelletsLRecord> records) {
        List<PelletsLRecord> result = new ArrayList<>();
        String currentSackGroup = "";
        int timeSlotIndex = 0;
        
        for (PelletsLRecord record : records) {
            String sackGroup = record.getSackGroup();
            if (!sackGroup.isEmpty() && !sackGroup.equals(currentSackGroup)) {
                // New sack group - reset time slot index
                currentSackGroup = sackGroup;
                timeSlotIndex = 0;
            }
            
            // Assign shift label based on time slot index within sack group
            String shiftLabel = "";
            if (!record.isTotalRow()) {
                timeSlotIndex++;
                shiftLabel = getOrdinal(timeSlotIndex) + " Shift";
            }
            
            result.add(new PelletsLRecord(
                    record.getDate(),
                    record.getTimeSlot(),
                    record.getBlastingGood(),
                    record.getBlastingReject(),
                    record.getBrand(),
                    record.getBagSize(),
                    record.isTotalRow(),
                    shiftLabel,
                    record.getSackGroup(),
                    record.getParsedDate()
            ));
        }
        
        return result;
    }

    private static String getOrdinal(int n) {
        if (n >= 11 && n <= 13) {
            return n + "th";
        }
        switch (n % 10) {
            case 1: return n + "st";
            case 2: return n + "nd";
            case 3: return n + "rd";
            default: return n + "th";
        }
    }

    private static boolean isColumnsAtoFEmpty(List<Object> row) {
        if (row == null || row.size() < 6) {
            return true;
        }
        for (int i = 0; i < 6; i++) {
            Object cell = row.get(i);
            if (cell != null && !cell.toString().trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isRowEmpty(List<Object> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }
        for (Object cell : row) {
            if (cell != null && !cell.toString().trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String cell(List<Object> row, int index) {
        if (row == null || index < 0 || index >= row.size() || row.get(index) == null) {
            return "";
        }
        return row.get(index).toString().trim();
    }

    private static int readInt(List<Object> row, int index) {
        String value = cell(row, index);
        if (value.isBlank()) {
            return 0;
        }
        try {
            String normalized = value.replace(",", "");
            if (normalized.endsWith(".0")) {
                normalized = normalized.substring(0, normalized.length() - 2);
            }
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ex) {
            try {
                return (int) Math.round(Double.parseDouble(value.replace(",", "")));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
    }

    private static LocalDate parseDate(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank()) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private static String normalizeBrand(String brand) {
        if (brand == null) {
            return "";
        }
        String trimmed = brand.trim();
        String lower = trimmed.toLowerCase(Locale.ENGLISH);
        if (lower.equals("akxel")) return "Akxel";
        if (lower.equals("equi") || lower.equals("equi gaz")) return "Equi";
        if (lower.equals("south gas")) return "South Gas";
        if (lower.equals("luzon")) return "Luzon";
        if (lower.equals("coastal")) return "Coastal";
        if (lower.equals("rapid")) return "Rapid";
        if (lower.equals("bayonate")) return "Bayonate";
        return trimmed;
    }

    private static String normalizeBagSize(String bagSize) {
        if (bagSize == null) {
            return "";
        }
        String clean = bagSize.trim().toLowerCase(Locale.ENGLISH).replace(" ", "");
        if (clean.contains("1")) {
            if (clean.contains("11")) return "11kg";
            if (clean.contains("50")) return "50kg";
            return "1kg";
        }
        if (clean.contains("22")) return "22kg";
        if (clean.contains("50")) return "50kg";
        return bagSize.trim();
    }
}
