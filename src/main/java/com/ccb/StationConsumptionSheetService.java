package com.ccb;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.api.services.sheets.v4.model.AppendDimensionRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StationConsumptionSheetService {

    private static final String SPREADSHEET_ID = "19h3v6jdlP8KqNVeUCE0PYsXl9-uFAgDKWBnd6XpMD8o";
    private static final String CREDENTIALS_PATH = "/com/ccb/credentials/service-account.json";
    private static final String APP_NAME = "CCB Inventory System";

    private final Sheets sheetsService;

    public StationConsumptionSheetService() throws Exception {
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

    public List<StationConsumptionRecord> readRecords() throws Exception {
        // Read A to G from Sheet1
        ValueRange response = sheetsService.spreadsheets().values()
                .get(SPREADSHEET_ID, "Sheet1!A:G")
                .execute();
        List<List<Object>> rows = response.getValues();
        
        if (rows == null || rows.size() <= 1) {
            return Collections.emptyList();
        }

        List<StationConsumptionRecord> records = new ArrayList<>();

        // Skip row 0 (header), start from row 1
        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);

            // Skip empty rows
            if (isRowEmpty(row)) {
                continue;
            }

            // Read columns A-G
            String dateStr = cell(row, 0);
            String station = cell(row, 1);
            String materialName = cell(row, 2);
            String quantityStr = cell(row, 3);
            String uom = cell(row, 4);
            String unitPriceStr = cell(row, 5);
            String totalCostStr = cell(row, 6);

            // Parse values
            LocalDate date = StationConsumptionRecord.parseDate(dateStr);
            double quantity = parseDouble(quantityStr);
            double unitPrice = parseDouble(unitPriceStr);
            double totalCost = parseDouble(totalCostStr);

            // Skip if date is null or station is empty
            if (date == null || station.isEmpty()) {
                continue;
            }

            // Normalize station name
            station = normalizeStation(station);

            records.add(new StationConsumptionRecord(
                    date,
                    station,
                    materialName,
                    quantity,
                    uom,
                    unitPrice,
                    totalCost
            ));
        }

        return records;
    }

    public void addRecord(StationConsumptionRecord record) throws Exception {
        // Find the next empty row
        ValueRange response = sheetsService.spreadsheets().values()
                .get(SPREADSHEET_ID, "Sheet1!A:G")
                .execute();
        List<List<Object>> rows = response.getValues();
        
        int nextRow = 1; // Start after header (row 0)
        if (rows != null && rows.size() > 1) {
            nextRow = rows.size();
        }
        
        // Prepare the row data
        List<Object> rowData = new ArrayList<>();
        rowData.add(record.getDateString());
        rowData.add(record.getStation());
        rowData.add(record.getMaterialName());
        rowData.add(record.getQuantity());
        rowData.add(record.getUom());
        rowData.add(record.getUnitPrice());
        rowData.add(record.getTotalCost());
        
        // Write to the sheet
        String range = "Sheet1!A" + (nextRow + 1) + ":G" + (nextRow + 1);
        ValueRange body = new ValueRange().setValues(List.of(rowData));
        
        sheetsService.spreadsheets().values()
                .update(SPREADSHEET_ID, range, body)
                .setValueInputOption("USER_ENTERED")
                .execute();
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
        return false;
    }

    private static String cell(List<Object> row, int index) {
        if (row == null || index < 0 || index >= row.size() || row.get(index) == null) {
            return "";
        }
        return row.get(index).toString().trim();
    }

    private static double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        try {
            String normalized = value.replace(",", "");
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private static String normalizeStation(String station) {
        if (station == null) {
            return "";
        }
        String trimmed = station.trim().toLowerCase();
        
        if (trimmed.contains("cosmetic")) {
            return "Cosmetics";
        }
        if (trimmed.contains("paint")) {
            return "Painting";
        }
        if (trimmed.contains("hot") || trimmed.contains("hotwork")) {
            return "Hotworks";
        }
        if (trimmed.contains("ctc")) {
            return "CTC";
        }
        
        // Return original if no match
        return station.trim();
    }
}
