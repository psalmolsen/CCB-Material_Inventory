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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StationConsumptionSheetService {

    private static final String SPREADSHEET_ID = "19h3v6jdlP8KqNVeUCE0PYsXl9-uFAgDKWBnd6XpMD8o";
    private static final String CREDENTIALS_PATH = "/com/ccb/credentials/service-account.json";
    private static final String APP_NAME = "CCB Inventory System";
    private static final String SHEET_NAME = "Sheet1";
    private static final List<Object> HEADER_ROW = List.of(
            "Date", "Station", "Material Code", "Description",
            "Quantity", "UOM", "Unit Cost", "Total Cost", "Received By"
    );

    private final Sheets sheetsService;

    public StationConsumptionSheetService() throws Exception {
        InputStream stream = getClass().getResourceAsStream(CREDENTIALS_PATH);
        if (stream == null) {
            throw new RuntimeException("service-account.json not found in resources.");
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

    public List<StationConsumptionRecord> readRecords() throws Exception {
        ensureHeaderRow();

        // Read A to I from Sheet1
        ValueRange response = sheetsService.spreadsheets().values()
                .get(SPREADSHEET_ID, SHEET_NAME + "!A:I")
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

            // Read columns A-F
            String dateStr = cell(row, 0);
            String station = cell(row, 1);
            String materialCode = cell(row, 2);
            String description = cell(row, 3);
            String quantityStr = cell(row, 4);
            String uom = cell(row, 5);
            String unitCostStr = cell(row, 6);
            String totalCostStr = cell(row, 7);
            String signature = cell(row, 8);

            // Parse values
            LocalDate date = StationConsumptionRecord.parseDate(dateStr);
            double quantity = parseDouble(quantityStr);
            double unitCost = parseDouble(unitCostStr);
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
                    materialCode,
                    description,
                    quantity,
                    uom,
                    unitCost,
                    totalCost,
                    signature
            ));
        }

        return records;
    }

    public void addRecord(StationConsumptionRecord record) throws Exception {
        ensureHeaderRow();

        // Find the next empty row
        ValueRange response = sheetsService.spreadsheets().values()
                .get(SPREADSHEET_ID, SHEET_NAME + "!A:I")
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
        rowData.add(record.getMaterialCode());
        rowData.add(record.getDescription());
        rowData.add(record.getQuantity());
        rowData.add(record.getUom());
        rowData.add(record.getUnitCost());
        rowData.add(record.getTotalCost());
        rowData.add(record.getSignature());
        
        // Write to the sheet
        String range = SHEET_NAME + "!A" + (nextRow + 1) + ":I" + (nextRow + 1);
        ValueRange body = new ValueRange().setValues(List.of(rowData));
        
        sheetsService.spreadsheets().values()
                .update(SPREADSHEET_ID, range, body)
                .setValueInputOption("USER_ENTERED")
                .execute();
    }

    private void ensureHeaderRow() throws Exception {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(SPREADSHEET_ID, SHEET_NAME + "!A:I")
                .execute();
        List<List<Object>> rows = response.getValues();
        if (rows == null || rows.isEmpty()) {
            sheetsService.spreadsheets().values()
                    .update(SPREADSHEET_ID, SHEET_NAME + "!A1:I1", new ValueRange().setValues(List.of(HEADER_ROW)))
                    .setValueInputOption("USER_ENTERED")
                    .execute();
        }
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
