package com.ccb;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.Spreadsheet;
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

public class OringSheetService {

    private static final String SPREADSHEET_ID = "1ReZqAWd1q5nm_m5mRysP4FplVCiszuCAhvKdPmpQKto";
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

    public OringSheetService() throws Exception {
        InputStream stream = getClass().getResourceAsStream(CREDENTIALS_PATH);
        if (stream == null) {
            throw new RuntimeException("service-account.json not found.");
        }

        GoogleCredentials credentials = GoogleCredentials
                .fromStream(stream)
                .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));

        sheetsService = new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APP_NAME)
                .build();
    }

    public List<OringRecord> readRecords() throws Exception {
        String tabName = resolvePrimaryTabName();
        ValueRange response = sheetsService.spreadsheets().values()
                .get(SPREADSHEET_ID, quoteSheetName(tabName) + "!A:H")
                .execute();
        List<List<Object>> rows = response.getValues();
        if (rows == null || rows.size() <= 2) {
            return Collections.emptyList();
        }

        List<OringRecord> records = new ArrayList<>();
        for (int i = 2; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (isBlankRow(row)) {
                continue;
            }

            String date = cell(row, 0);
            String timeSlot = cell(row, 1);
            String valveCameFrom = cell(row, 2);
            int valvesRepaired = readInt(row, 3);
            String installedTo = cell(row, 4);
            int good = readInt(row, 5);
            int rejected = readInt(row, 6);
            String remarks = cell(row, 7);

            if (!isValidDataRow(row) || looksLikeTotalsRow(date, valveCameFrom, installedTo, remarks)) {
                continue;
            }

            records.add(new OringRecord(
                    date,
                    timeSlot,
                    valveCameFrom,
                    valvesRepaired,
                    installedTo,
                    good,
                    rejected,
                    remarks,
                    parseDate(date)
            ));
        }
        return records;
    }

    public void appendRecord(String tabName, List<Object> rowData) throws Exception {
        sheetsService.spreadsheets().values()
                .append(SPREADSHEET_ID, quoteSheetName(tabName) + "!A:H", new ValueRange().setValues(List.of(rowData)))
                .setValueInputOption("USER_ENTERED")
                .setInsertDataOption("INSERT_ROWS")
                .execute();
    }

    public List<String> getTabNames() throws Exception {
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(SPREADSHEET_ID).execute();
        List<String> names = new ArrayList<>();
        if (spreadsheet.getSheets() == null) {
            return names;
        }
        for (var sheet : spreadsheet.getSheets()) {
            names.add(sheet.getProperties().getTitle());
        }
        return names;
    }

    public String resolvePrimaryTabName() throws Exception {
        List<String> tabs = getTabNames();
        if (tabs.isEmpty()) {
            return "Sheet1";
        }

        for (String tab : tabs) {
            String normalized = tab == null ? "" : tab.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("sheet1") || normalized.contains("oring") || normalized.contains("o-ring")) {
                return tab;
            }
        }
        return tabs.get(0);
    }

    private static String quoteSheetName(String name) {
        if (name == null || name.isBlank()) {
            return "Sheet1";
        }
        if (name.indexOf(' ') >= 0 || name.indexOf('!') >= 0 || name.indexOf('\'') >= 0) {
            return "'" + name.replace("'", "''") + "'";
        }
        return name;
    }

    private static boolean isBlankRow(List<Object> row) {
        if (row == null) {
            return true;
        }
        for (Object value : row) {
            if (value != null && !value.toString().trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidDataRow(List<Object> row) {
        return readInt(row, 3) > 0 || readInt(row, 5) > 0 || readInt(row, 6) > 0;
    }

    private static boolean looksLikeTotalsRow(String date, String valveCameFrom, String installedTo, String remarks) {
        String combined = String.join(" ",
                safe(date),
                safe(valveCameFrom),
                safe(installedTo),
                safe(remarks)).toLowerCase(Locale.ROOT);
        return combined.contains("total") || combined.contains("grand total");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
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
                // Try the next pattern.
            }
        }
        return null;
    }
}
