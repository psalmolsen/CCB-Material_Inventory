package com.ccb.controller.modal;

import com.ccb.GoogleSheetsService;
import com.ccb.InventoryItem;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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

    // FXML fields for image preview & selection
    @FXML private TextField imagePathField;
    @FXML private StackPane thumbFrame;
    @FXML private Label thumbFallback;
    @FXML private ImageView thumbImageView;

    private InventoryItem material;

    private static final String IMAGE_DIR = System.getProperty("user.dir") + "/src/imgs/Material_Icon/";
    private File selectedImageFile;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
    }

    public void setMaterial(InventoryItem material) {
        this.material = material;
        dateField.setText(safe(material.getDate()));
        codeField.setText(safe(material.getCodeNo()));
        descriptionField.setText(safe(material.getDescription()));
        uomField.setText(safe(material.getUom()));
        priceField.setText(material.getUnitPrice() == com.ccb.SheetMapper.PRICE_NA
                ? "N/A" : formatNumber(material.getUnitPrice()));
        initialStockField.setText(formatNumber(material.getInitialStock()));
        receivedField.setText(formatNumber(material.getReceivedQuantity()));
        balanceField.setText(formatNumber(material.getCurrentBalance()));
        outQuantityField.setText(formatNumber(material.getOutQuantity()));

        // Load current image or fallback icon
        String code = material.getCodeNo() == null ? "" : material.getCodeNo().trim();
        thumbFallback.setText(code.isEmpty() ? "M" : code.substring(0, Math.min(2, code.length())).toUpperCase());
        File imageFile = resolveImageFile(code);
        if (imageFile != null) {
            thumbImageView.setImage(new Image(imageFile.toURI().toString(), 48, 48, true, true));
            thumbImageView.setVisible(true);
            thumbFallback.setVisible(false);
            imagePathField.setText(imageFile.getName());
        } else {
            thumbImageView.setImage(null);
            thumbImageView.setVisible(false);
            thumbFallback.setVisible(true);
            imagePathField.setText("No image selected");
        }
    }

    private File resolveImageFile(String code) {
        File png = new File(IMAGE_DIR + code + ".png");
        if (png.exists()) return png;

        File jpg = new File(IMAGE_DIR + code + ".jpg");
        if (jpg.exists()) return jpg;

        File jpeg = new File(IMAGE_DIR + code + ".jpeg");
        if (jpeg.exists()) return jpeg;

        return null;
    }

    @FXML
    private void browseImage() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Select Material Image");
        chooser.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );
        File file = chooser.showOpenDialog(imagePathField.getScene().getWindow());
        if (file != null) {
            selectedImageFile = file;
            imagePathField.setText(file.getName());
            try {
                thumbImageView.setImage(new Image(file.toURI().toString(), 48, 48, true, true));
                thumbImageView.setVisible(true);
                thumbFallback.setVisible(false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void saveChanges() {
        if (material == null || material.getSheetRowNumber() <= 0) {
            showError("Missing sheet row reference.");
            return;
        }

        String oldCode = material.getCodeNo() == null ? "" : material.getCodeNo().trim();
        String newCode = codeField.getText().trim();

        if (newCode.isEmpty()) {
            showError("Code cannot be empty.");
            return;
        }

        // Copy/rename image to Material_Icon folder
        if (selectedImageFile != null) {
            try {
                File destDir = new File(IMAGE_DIR);
                destDir.mkdirs();
                String ext = selectedImageFile.getName().substring(selectedImageFile.getName().lastIndexOf('.'));
                File dest = new File(destDir, newCode + ext);
                Files.copy(selectedImageFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

                // If code changed, delete the old image file under the old code name
                if (!oldCode.isEmpty() && !oldCode.equalsIgnoreCase(newCode)) {
                    File oldImageFile = resolveImageFile(oldCode);
                    if (oldImageFile != null && oldImageFile.exists()) {
                        oldImageFile.delete();
                    }
                }
            } catch (Exception e) {
                showError("Failed to save image: " + e.getMessage());
                return;
            }
        } else if (!oldCode.isEmpty() && !oldCode.equalsIgnoreCase(newCode)) {
            // No new image selected, but code was changed. Rename the old image file to use the new code name.
            File oldImageFile = resolveImageFile(oldCode);
            if (oldImageFile != null && oldImageFile.exists()) {
                try {
                    String name = oldImageFile.getName();
                    String ext = name.substring(name.lastIndexOf('.'));
                    File dest = new File(oldImageFile.getParentFile(), newCode + ext);
                    Files.move(oldImageFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            List<Object> row = new ArrayList<>();
            row.add(dateField.getText().trim());
            row.add(newCode);
            row.add(descriptionField.getText().trim());
            row.add(uomField.getText().trim());
            String priceText = priceField.getText().trim();
            row.add(priceText.equalsIgnoreCase("n/a") || priceText.equalsIgnoreCase("na") || priceText.isEmpty()
                    ? "N/A" : parseDouble(priceText));
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
