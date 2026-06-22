package com.ccb.controller.modal;

import com.ccb.GoogleSheetsService;
import com.ccb.InventoryItem;
import com.ccb.SheetMapper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
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
        monthComboBox.getItems().addAll("JAN", "FEB", "MAR", "APR", "MAY", "JUN",
            "JUL", "AUG", "SEP", "OCT", "NOV", "DEC");
        monthComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldMonth, newMonth) -> loadMonth(newMonth));
    }

    public void setMaterial(InventoryItem material) {
        this.material = material;
        materialLabel.setText((material.getCodeNo() == null ? "" : material.getCodeNo()) + " - " +
            (material.getDescription() == null ? "" : material.getDescription()));
        String tab = material.getSheetTabName() == null || material.getSheetTabName().isBlank()
            ? "MAY" : material.getSheetTabName().trim().toUpperCase();
        if (!monthComboBox.getItems().contains(tab)) {
            tab = "MAY";
        }
        monthComboBox.getSelectionModel().select(tab);
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

        try {
            GoogleSheetsService service = new GoogleSheetsService();
            List<List<Object>> rows = service.readSheet(monthTab);
            InventoryItem matched = null;
            for (int i = GoogleSheetsService.DATA_START_ROW; i < rows.size(); i++) {
                List<Object> row = rows.get(i);
                if (row.size() > 1 && material.getCodeNo() != null && material.getCodeNo().equalsIgnoreCase(row.get(1).toString().trim())) {
                    matched = SheetMapper.fromRow(row, i + 1);
                    break;
                }
            }

            if (matched == null) {
                errorLabel.setText("No record found for " + monthTab + ".");
                errorLabel.setVisible(true);
                errorLabel.setManaged(true);
                totalIssuedLabel.setText("0000");
                peakDayLabel.setText("--");
                activeDaysLabel.setText("0");
                averageDayLabel.setText("0");
                daysBox.getChildren().clear();
                return;
            }

            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            double totalIssued = matched.getIssuedQuantity() > 0 ? matched.getIssuedQuantity() : matched.getTotalIssued();
            totalIssuedLabel.setText(String.format("%.0f", totalIssued));
            renderDays(matched, totalIssued);
        } catch (Exception e) {
            errorLabel.setText("Failed to load month data: " + e.getMessage());
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
    }

    private void renderDays(InventoryItem item, double totalIssued) {
        daysBox.getChildren().clear();
        double peakValue = -1;
        int peakDay = 0;
        int activeDays = 0;
        double maxDayValue = 0;

        for (int day = 1; day <= 31; day++) {
            double value = item.getDayValue(day);
            if (value > 0) activeDays++;
            if (value > peakValue) {
                peakValue = value;
                peakDay = day;
            }
            if (value > maxDayValue) maxDayValue = value;
        }

        peakDayLabel.setText(peakDay == 0 ? "--" : String.format("Day %02d", peakDay));
        activeDaysLabel.setText(String.valueOf(activeDays));
        averageDayLabel.setText(activeDays == 0 ? "0" : String.format("%.1f", totalIssued / activeDays));

        for (int day = 1; day <= 31; day++) {
            double value = item.getDayValue(day);
            
            HBox row = new HBox(16);
            row.setStyle("-fx-alignment: CENTER_LEFT; -fx-padding: 8 0;");

            Label dayLabel = new Label(String.format("Day %02d", day));
            dayLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(255,255,255,0.7); -fx-min-width: 60; -fx-pref-width: 60;");

            StackPane barContainer = new StackPane();
            barContainer.setStyle("-fx-min-height: 18; -fx-pref-height: 18;");
            
            Region bar = new Region();
            double pct = maxDayValue <= 0 ? 0 : (value / maxDayValue);
            double barWidth = Math.max(2, pct * 380);
            bar.setStyle("-fx-background-color: linear-gradient(to right, #5b9bdb, #3d7bb5); -fx-background-radius: 4;");
            bar.setPrefWidth(barWidth);
            bar.setMinWidth(barWidth);
            bar.setMaxWidth(barWidth);
            bar.setPrefHeight(18);

            barContainer.getChildren().add(bar);
            barContainer.setAlignment(bar, Pos.CENTER_LEFT);
            HBox.setHgrow(barContainer, javafx.scene.layout.Priority.ALWAYS);

            Label valueLabel = new Label(String.format("%.0f", value));
            valueLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: white; -fx-min-width: 50; -fx-pref-width: 50; -fx-text-alignment: RIGHT;");

            row.getChildren().addAll(dayLabel, barContainer, valueLabel);
            daysBox.getChildren().add(row);
        }
    }
}
