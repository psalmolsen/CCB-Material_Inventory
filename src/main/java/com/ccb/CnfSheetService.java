package com.ccb;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Google Sheets service for the CNF Monitoring spreadsheet.
 * Separate from GoogleSheetsService so each uses its own spreadsheet ID.
 */
public class CnfSheetService {

    public static final String SPREADSHEET_ID  = "1zHX-FOFrr4zdaUBQKLA3GatIpuG3pWlotukHXI8doEA";
    private static final String CREDENTIALS_PATH = "/com/ccb/credentials/service-account.json";
    private static final String APP_NAME          = "CCB Inventory System";

    private final Sheets sheetsService;

    public CnfSheetService() throws Exception {
        InputStream stream = getClass().getResourceAsStream(CREDENTIALS_PATH);
        if (stream == null) throw new RuntimeException("service-account.json not found.");
        GoogleCredentials creds = GoogleCredentials
                .fromStream(stream)
                .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));
        sheetsService = new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(creds))
                .setApplicationName(APP_NAME)
                .build();
    }

    public List<List<Object>> readSheet(String tab) throws Exception {
        ValueRange r = sheetsService.spreadsheets().values()
                .get(SPREADSHEET_ID, tab).execute();
        return r.getValues() != null ? r.getValues() : Collections.emptyList();
    }

    public void writeCell(String range, Object value) throws Exception {
        sheetsService.spreadsheets().values()
                .update(SPREADSHEET_ID, range,
                        new ValueRange().setValues(List.of(List.of(value))))
                .setValueInputOption("USER_ENTERED").execute();
    }

    public void writeBlock(String range, List<List<Object>> rows) throws Exception {
        sheetsService.spreadsheets().values()
                .update(SPREADSHEET_ID, range,
                        new ValueRange().setValues(rows))
                .setValueInputOption("USER_ENTERED").execute();
    }

    public List<String> getTabNames() throws Exception {
        Spreadsheet sp = sheetsService.spreadsheets().get(SPREADSHEET_ID).execute();
        List<String> names = new ArrayList<>();
        for (var s : sp.getSheets()) names.add(s.getProperties().getTitle());
        return names;
    }

    public Integer getSheetId(String tab) throws Exception {
        Spreadsheet sp = sheetsService.spreadsheets().get(SPREADSHEET_ID).execute();
        for (var s : sp.getSheets()) {
            if (s.getProperties().getTitle().equalsIgnoreCase(tab))
                return s.getProperties().getSheetId();
        }
        return null;
    }

    public void renameSheet(String oldName, String newName) throws Exception {
        Integer sheetId = getSheetId(oldName);
        if (sheetId == null) return;
        UpdateSheetPropertiesRequest req = new UpdateSheetPropertiesRequest()
                .setProperties(new SheetProperties().setSheetId(sheetId).setTitle(newName))
                .setFields("title");
        sheetsService.spreadsheets().batchUpdate(SPREADSHEET_ID,
                new BatchUpdateSpreadsheetRequest().setRequests(
                        List.of(new Request().setUpdateSheetProperties(req)))).execute();
    }

    public void createSheet(String name) throws Exception {
        sheetsService.spreadsheets().batchUpdate(SPREADSHEET_ID,
                new BatchUpdateSpreadsheetRequest().setRequests(List.of(
                        new Request().setAddSheet(
                                new AddSheetRequest().setProperties(
                                        new SheetProperties().setTitle(name)))))).execute();
    }

    /** Parse one raw row into a CnfItem */
    public static CnfItem parseRow(List<Object> row, int sheetRowNumber, String tabName) {
        CnfItem item = new CnfItem();
        item.setItemName(cell(row, CnfItem.COL_ITEM));
        item.setUom(cell(row, CnfItem.COL_UOM));
        item.setUnitPrice(cellDouble(row, CnfItem.COL_PRICE));
        item.setInitialStock(cellDouble(row, CnfItem.COL_INITIAL));
        item.setReceivedQuantity(cellDouble(row, CnfItem.COL_RECEIVED));
        item.setDate(cell(row, CnfItem.COL_DATE));
        item.setCurrentBalance(cellDouble(row, CnfItem.COL_BALANCE));
        item.setOutQuantity(cellDouble(row, CnfItem.COL_OUT_QTY));
        for (int day = 1; day <= 31; day++) {
            item.setDayValue(day, cellDouble(row, CnfItem.COL_DAY_START + day - 1));
        }
        item.setSheetRowNumber(sheetRowNumber);
        item.setSheetTabName(tabName);
        return item;
    }

    private static String cell(List<Object> row, int idx) {
        if (row == null || idx >= row.size() || row.get(idx) == null) return "";
        return row.get(idx).toString();
    }

    private static double cellDouble(List<Object> row, int idx) {
        String v = cell(row, idx).trim();
        if (v.isEmpty()) return 0.0;
        try { return Double.parseDouble(v); } catch (NumberFormatException e) { return 0.0; }
    }
}
