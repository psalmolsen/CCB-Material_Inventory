package com.ccb.controller.modal;

import com.ccb.GoogleSheetsService;
import com.ccb.InventoryItem;
import com.ccb.SheetMapper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;
import javafx.stage.Stage;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class MaterialMonthOutController implements Initializable {

    @FXML private Label materialLabel;
    @FXML private Label dialogSubtitle;
    @FXML private ComboBox<String> monthComboBox;
    @FXML private Label totalIssuedLabel;
    @FXML private Label peakDayLabel;
    @FXML private Label activeDaysLabel;
    @FXML private Label averageDayLabel;
    @FXML private VBox daysBox;
    @FXML private Label errorLabel;

    private InventoryItem material;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Tabs are loaded dynamically from the sheet in setMaterial()
        monthComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldMonth, newMonth) -> {
            if (newMonth != null && dialogSubtitle != null) {
                dialogSubtitle.setText("Daily issued quantities for " + newMonth);
            }
            loadMonth(newMonth);
        });
    }

    public void setMaterial(InventoryItem material) {
        this.material = material;
        materialLabel.setText((material.getCodeNo() == null ? "" : material.getCodeNo()) + " - " +
            (material.getDescription() == null ? "" : material.getDescription()));

        // Load available tabs from the sheet, then select the current one
        javafx.concurrent.Task<java.util.List<String>> tabTask = new javafx.concurrent.Task<>() {
            @Override
            protected java.util.List<String> call() throws Exception {
                return new GoogleSheetsService().getSheetTabNames();
            }
        };
        tabTask.setOnSucceeded(e -> {
            java.util.List<String> tabs = tabTask.getValue();
            monthComboBox.getItems().setAll(tabs);
            // Select the tab this item came from, or the first available
            String currentTab = material.getSheetTabName() == null || material.getSheetTabName().isBlank()
                    ? "" : material.getSheetTabName().trim();
            // Try exact match first, then case-insensitive
            String toSelect = tabs.stream()
                    .filter(t -> t.equalsIgnoreCase(currentTab))
                    .findFirst()
                    .orElse(tabs.isEmpty() ? null : tabs.get(0));
            if (toSelect != null) {
                monthComboBox.getSelectionModel().select(toSelect);
            }
        });
        tabTask.setOnFailed(e -> {
            // Fallback to hardcoded list if fetch fails
            monthComboBox.getItems().setAll("JAN","FEB","MAR","APR","MAY","JUN",
                    "JUL","AUG","SEP","OCT","NOV","DEC");
            String tab = material.getSheetTabName() == null ? "MAY"
                    : material.getSheetTabName().trim().toUpperCase();
            monthComboBox.getSelectionModel().select(tab);
        });
        Thread t = new Thread(tabTask);
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void closeDialog() {
        Stage stage = (Stage) materialLabel.getScene().getWindow();
        stage.close();
    }

    private void loadMonth(String monthTab) {
        if (material == null || monthTab == null || monthTab.isBlank()) {
            return;
        }

        // Show loading state
        totalIssuedLabel.setText("...");
        peakDayLabel.setText("...");
        activeDaysLabel.setText("...");
        averageDayLabel.setText("...");
        daysBox.getChildren().clear();
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        javafx.concurrent.Task<InventoryItem> task = new javafx.concurrent.Task<>() {
            @Override
            protected InventoryItem call() throws Exception {
                GoogleSheetsService service = new GoogleSheetsService();
                List<List<Object>> rows = service.readSheet(monthTab);
                for (int i = GoogleSheetsService.DATA_START_ROW; i < rows.size(); i++) {
                    List<Object> row = rows.get(i);
                    if (row.size() > 1 && material.getCodeNo() != null
                            && material.getCodeNo().equalsIgnoreCase(row.get(1).toString().trim())) {
                        return SheetMapper.fromRow(row, i + 1);
                    }
                }
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            InventoryItem matched = task.getValue();
            if (matched == null) {
                errorLabel.setText("No record found for " + monthTab + ".");
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                totalIssuedLabel.setText("0");
                peakDayLabel.setText("--");
                activeDaysLabel.setText("0");
                averageDayLabel.setText("0");
                return;
            }
            double totalIssued = matched.getTotalIssued() > 0 ? matched.getTotalIssued()
                    : matched.getIssuedQuantity();
            totalIssuedLabel.setText(String.format("%.0f", totalIssued));
            renderDays(matched, totalIssued);
        });

        task.setOnFailed(e -> {
            errorLabel.setText("Failed to load: " + task.getException().getMessage());
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
            totalIssuedLabel.setText("0");
        });

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    private void renderDays(InventoryItem item, double totalIssued) {
        daysBox.getChildren().clear();

        // First pass — find the month's max so bars are proportional to it
        double maxDayValue = 0;
        for (int day = 1; day <= 31; day++) {
            double v = item.getDayValue(day);
            if (v > maxDayValue) maxDayValue = v;
        }

        int peakDay = 0;
        int activeDays = 0;

        for (int day = 1; day <= 31; day++) {
            double value = item.getDayValue(day);
            if (value > 0) {
                activeDays++;
                if (value == maxDayValue) peakDay = day;
            }

            // ── Row container ────────────────────────────────────
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("day-row");

            // ── Day badge (left) ─────────────────────────────────
            Label dayBadge = new Label(String.format("DAY %02d", day));
            dayBadge.getStyleClass().add("day-badge");

            // ── Bar track (middle, grows to fill) ────────────────
            StackPane track = new StackPane();
            track.getStyleClass().add("day-bar-track");
            track.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(track, Priority.ALWAYS);

            Region fill = new Region();
            fill.getStyleClass().add("day-bar-fill");
            fill.setMinHeight(6);
            fill.setPrefHeight(6);

            // Bind bar width to track width * proportion
            double ratio = (maxDayValue <= 0 || value <= 0) ? 0.0 : (value / maxDayValue);
            fill.prefWidthProperty().bind(track.widthProperty().multiply(ratio));
            fill.minWidthProperty().bind(track.widthProperty().multiply(ratio));
            fill.maxWidthProperty().bind(track.widthProperty().multiply(ratio));

            StackPane.setAlignment(fill, Pos.CENTER_LEFT);
            track.getChildren().add(fill);

            // ── Value badge (right) ──────────────────────────────
            Label valueBadge = new Label(value > 0 ? String.format("%.0f", value) : "—");
            valueBadge.getStyleClass().add("day-value-badge");

            row.getChildren().addAll(dayBadge, track, valueBadge);

            // Dim rows with zero activity slightly
            if (value <= 0) {
                row.setOpacity(0.45);
            }

            daysBox.getChildren().add(row);
        }

        peakDayLabel.setText(peakDay == 0 ? "--" : String.format("Day %02d", peakDay));
        activeDaysLabel.setText(String.valueOf(activeDays));
        averageDayLabel.setText(activeDays == 0 ? "0" : String.format("%.1f", totalIssued / activeDays));
    }
}
