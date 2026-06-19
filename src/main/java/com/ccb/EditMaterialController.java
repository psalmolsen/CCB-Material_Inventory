package com.ccb;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class EditMaterialController implements Initializable {

    @FXML private TextField dateField;
    @FXML private TextField codeField;
    @FXML private TextField descriptionField;
    @FXML private TextField uomField;
    @FXML private TextField priceField;
    @FXML private TextField initialStockField;
    @FXML private TextField receivedField;
    @FXML private TextField balanceField;
    @FXML private TextField outQuantityField;
    @FXML private Label errorLabel;

    private InventoryItem material;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
    }

    public void setMaterial(InventoryItem material) {
        this.material = material;
        dateField.setText(safe(material.getDate()));
        codeField.setText(safe(material.getCodeNo()));
        descriptionField.setText(safe(material.getDescription()));
        uomField.setText(safe(material.getUom()));
        priceField.setText(formatNumber(material.getUnitPrice()));
        initialStockField.setText(formatNumber(material.getInitialStock()));
        receivedField.setText(formatNumber(material.getReceivedQuantity()));
        balanceField.setText(formatNumber(material.getCurrentBalance()));
        outQuantityField.setText(formatNumber(material.getOutQuantity()));
    }

    @FXML
    private void saveChanges() {
        if (material == null || material.getSheetRowNumber() <= 0) {
            showError("Missing sheet row reference.");
            return;
        }

        try {
            List<Object> row = new ArrayList<>();
            row.add(dateField.getText().trim());
            row.add(codeField.getText().trim());
            row.add(descriptionField.getText().trim());
            row.add(uomField.getText().trim());
            row.add(parseDouble(priceField.getText()));
            row.add(parseDouble(initialStockField.getText()));
            row.add(parseDouble(receivedField.getText()));
            row.add("");
            row.add(parseDouble(balanceField.getText()));
            row.add(parseDouble(outQuantityField.getText()));

            GoogleSheetsService service = new GoogleSheetsService();
            String tab = material.getSheetTabName() == null || material.getSheetTabName().isBlank()
                ? "MAY" : material.getSheetTabName().trim().toUpperCase();
            String range = tab + "!A" + material.getSheetRowNumber() + ":J" + material.getSheetRowNumber();
            service.writeRow(range, row);
            closeDialog();
        } catch (Exception e) {
            showError("Failed to save changes: " + e.getMessage());
        }
    }

    @FXML
    private void closeDialog() {
        Stage stage = (Stage) dateField.getScene().getWindow();
        stage.close();
    }

    private double parseDouble(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return 0.0;
        }
        return Double.parseDouble(value);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String formatNumber(double value) {
        return String.format("%.0f", value);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}
