package com.ccb;

import java.util.List;

public class SheetsConnectionTest {

    public static void main(String[] args) {
        try {
            System.out.println("Connecting to Google Sheets...");
            GoogleSheetsService service = new GoogleSheetsService();

            List<List<Object>> rows = service.readSheet("MAY");
            System.out.println("✅ Connection successful!");
            System.out.println("Total rows read: " + rows.size());

            // Print rows 5 onwards (row 5+ in sheet) to see actual data structure
            for (int i = 4; i < Math.min(8, rows.size()); i++) {
                System.out.println("Sheet Row " + (i + 1) + ": " + rows.get(i));
            }

        } catch (Exception e) {
            System.out.println("❌ Connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
