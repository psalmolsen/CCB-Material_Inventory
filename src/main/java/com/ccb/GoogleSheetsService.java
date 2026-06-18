package com.ccb;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

public class GoogleSheetsService {

    private static final String SPREADSHEET_ID = "1IqWRM_OiDl2W0iAppZL5ZSRs9RfFvkEkO8l3vtt-IAg";
    private static final String CREDENTIALS_PATH = "/com/ccb/credentials/service-account.json";
    public static final int DATA_START_ROW = 4;
    private static final String APP_NAME = "CCB Inventory System";

    private final Sheets sheetsService;

    public GoogleSheetsService() throws Exception {
        InputStream credentialsStream = getClass().getResourceAsStream(CREDENTIALS_PATH);
        if (credentialsStream == null) {
            throw new RuntimeException("service-account.json not found in credentials folder.");
        }

        GoogleCredentials credentials = GoogleCredentials
                .fromStream(credentialsStream)
                .createScoped(Collections.singleton(SheetsScopes.SPREADSHEETS));

        sheetsService = new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APP_NAME)
                .build();
    }

    /** Read all rows from a sheet tab */
    public List<List<Object>> readSheet(String tabName) throws Exception {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(SPREADSHEET_ID, tabName)
                .execute();
        List<List<Object>> values = response.getValues();
        return values != null ? values : Collections.emptyList();
    }

    /** Write a single cell value — range example: "for testing!E5" */
    public void writeCell(String range, Object value) throws Exception {
        ValueRange body = new ValueRange()
                .setValues(List.of(List.of(value)));
        sheetsService.spreadsheets().values()
                .update(SPREADSHEET_ID, range, body)
                .setValueInputOption("USER_ENTERED")
                .execute();
    }

    /** Write a full row */
    public void writeRow(String range, List<Object> rowData) throws Exception {
        ValueRange body = new ValueRange()
                .setValues(List.of(rowData));
        sheetsService.spreadsheets().values()
                .update(SPREADSHEET_ID, range, body)
                .setValueInputOption("USER_ENTERED")
                .execute();
    }

    /** Append a new row at the bottom */
    public void appendRow(String tabName, List<Object> rowData) throws Exception {
        ValueRange body = new ValueRange()
                .setValues(List.of(rowData));
        sheetsService.spreadsheets().values()
                .append(SPREADSHEET_ID, tabName, body)
                .setValueInputOption("USER_ENTERED")
                .setInsertDataOption("INSERT_ROWS")
                .execute();
    }
}
