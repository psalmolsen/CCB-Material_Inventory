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
import java.util.List;
import java.util.Locale;

public class PelletsSheetService {

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
    private final List<String> completedSackGroups = new ArrayList<>();

    public PelletsSheetService() throws Exception {
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

    public List<String> getCompletedSackGroups() {
        return completedSackGroups;
    }

    public List<PelletsRecord> readRecords() throws Exception {
        completedSackGroups.clear();
        // Read A to H from Sheet1
        ValueRange response = sheetsService.spreadsheets().values()
                .get(SPREADSHEET_ID, "Sheet1!A:H")
                .execute();
        List<List<Object>> rows = response.getValues();
        if (rows == null || rows.size() <= 3) {
            return Collections.emptyList();
        }

        List<PelletsRecord> records = new ArrayList<>();
        String currentSackGroup = "";
        int consecutiveEmptyDates = 0;

        for (int i = 3; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            String dateStr = cell(row, 0);

            // Stop parsing if we encounter 3+ consecutive rows with empty date column
            if (dateStr.isBlank()) {
                consecutiveEmptyDates++;
                if (consecutiveEmptyDates >= 3) {
                    break;
                }
            } else {
                consecutiveEmptyDates = 0;
            }

            // SACK REPORT DETECTION
            String colG = cell(row, 6);
            String colH = cell(row, 7);
            String sackLabel = !colG.isEmpty() ? colG : colH;
            if (!sackLabel.isEmpty()) {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?i)(\\d+(?:st|nd|rd|th))\\s+.*sack\\s+output");
                java.util.regex.Matcher m = p.matcher(sackLabel);
                if (m.find()) {
                    currentSackGroup = m.group(1); // extracts "1st", "2nd", "3rd" etc.
                }
            }

            // TOTAL ROW DETECTION
            String timeSlot = cell(row, 1);
            if (timeSlot.equalsIgnoreCase("Total")) {
                if (!currentSackGroup.isEmpty()) {
                    completedSackGroups.add(currentSackGroup);
                }
                continue;
            }

            // VALID DATA ROW check (must have good or reject as non-empty int)
            String goodStr = cell(row, 2);
            String rejectStr = cell(row, 3);
            if (goodStr.isEmpty() && rejectStr.isEmpty()) {
                continue;
            }

            int goodVal = readInt(row, 2);
            int rejectVal = readInt(row, 3);

            // Skip row if both C and D are 0
            if (goodVal == 0 && rejectVal == 0 && goodStr.isEmpty() && rejectStr.isEmpty()) {
                continue;
            }

            String brand = cell(row, 4);
            String kgs = cell(row, 5);

            records.add(new PelletsRecord(
                    dateStr,
                    timeSlot,
                    goodVal,
                    rejectVal,
                    normalizeBrand(brand),
                    normalizeKgs(kgs),
                    currentSackGroup,
                    parseDate(dateStr)
            ));
        }

        return records;
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

    public static String normalizeBrand(String brand) {
        if (brand == null) {
            return "";
        }
        String trimmed = brand.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.equals("equi gaz") || lower.equals("equi gas") || lower.equals("equi  gaz")) {
            return "Equi Gaz";
        }
        if (lower.equals("equi gaz cnf") || lower.equals("equi gaz cf") || lower.equals("equi gaz  cnf") || lower.equals("equi gaz cnf ") || lower.contains("cnf") || lower.contains("cf")) {
            if (lower.contains("equi")) {
                return "Equi Gaz CNF";
            }
        }
        if (lower.equals("south gas")) {
            return "South Gas";
        }
        if (lower.equalsIgnoreCase("luzon")) return "Luzon";
        if (lower.equalsIgnoreCase("akxel")) return "Akxel";
        if (lower.equalsIgnoreCase("coastal")) return "Coastal";
        if (lower.equalsIgnoreCase("rapid")) return "Rapid";
        if (lower.equalsIgnoreCase("bayonate")) return "Bayonate";
        if (lower.equalsIgnoreCase("south gas")) return "South Gas";

        return trimmed;
    }

    public static String normalizeKgs(String kgs) {
        if (kgs == null) {
            return "";
        }
        String clean = kgs.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (clean.contains("11")) return "11kg";
        if (clean.contains("22")) return "22kg";
        if (clean.contains("50")) return "50kg";
        return kgs.trim();
    }
}
