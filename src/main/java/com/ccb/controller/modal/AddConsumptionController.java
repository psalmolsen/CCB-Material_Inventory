package com.ccb.controller.modal;

import com.ccb.StationConsumptionRecord;
import com.ccb.StationConsumptionSheetService;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.URL;
import java.time.LocalDate;
import java.util.ResourceBundle;

public class AddConsumptionController implements Initializable {

    @FXML private DatePicker dateField;
    @FXML private ComboBox<String> stationComboBox;
    @FXML private TextField materialField;
    @FXML private TextField quantityField;
    @FXML private TextField uomField;
    @FXML private TextField unitPriceField;
    @FXML private Label errorLabel;

    private Runnable onSaveCallback;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        stationComboBox.getItems().addAll(
            "Cosmetics", "Painting", "Hotworks", "CTC"
        );
        dateField.setValue(LocalDate.now());
    }

    public void setOnSaveCallback(Runnable callback) {
        this.onSaveCallback = callback;
    }

    @FXML
    private void saveConsumption() {
        LocalDate date = dateField.getValue();
        String station = stationComboBox.getValue();
        String material = materialField.getText().trim();
        String quantityText = quantityField.getText().trim();
        String uom = uomField.getText().trim();
        String priceText = unitPriceField.getText().trim();

        if (date == null || station == null || material.isEmpty() || quantityText.isEmpty() || uom.isEmpty() || priceText.isEmpty()) {
            showError("Please fill in all fields.");
            return;
        }

        double quantity;
        try {
            quantity = Double.parseDouble(quantityText);
        } catch (NumberFormatException e) {
            showError("Quantity must be a valid number.");
            return;
        }

        double unitPrice;
        try {
            unitPrice = Double.parseDouble(priceText);
        } catch (NumberFormatException e) {
            showError("Unit price must be a valid number.");
            return;
        }

        double totalCost = quantity * unitPrice;

        try {
            StationConsumptionSheetService service = new StationConsumptionSheetService();
            StationConsumptionRecord record = new StationConsumptionRecord(
                date, station, material, quantity, uom, unitPrice, totalCost
            );
            service.addRecord(record);
            
            if (onSaveCallback != null) {
                onSaveCallback.run();
            }
            
            closeDialog();

        } catch (Exception e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    @FXML
    private void closeDialog() {
        Stage stage = (Stage) dateField.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}
