package com.ccb.controller.modal;

import com.ccb.GoogleSheetsService;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

public class AddMaterialController implements Initializable {

    @FXML private TextField codeField;
    @FXML private TextField descriptionField;
    @FXML private ComboBox<String> uomComboBox;
    @FXML private TextField priceField;
    @FXML private TextField imagePathField;
    @FXML private Label errorLabel;

    private static final String IMAGE_DIR = System.getProperty("user.dir") + "/src/imgs/Material_Icon/";
    private static final String TAB_NAME  = "MAY";

    private File selectedImageFile;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        uomComboBox.getItems().addAll(
            "Pcs", "Spool", "Cyl", "Pair", "Box",
            "Liters", "Can", "Bottle", "Sacks", "Pail"
        );
    }

    @FXML
    private void browseImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Material Image");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );
        File file = chooser.showOpenDialog(imagePathField.getScene().getWindow());
        if (file != null) {
            selectedImageFile = file;
            imagePathField.setText(file.getName());
        }
    }

    @FXML
    private void saveMaterial() {
        String code        = codeField.getText().trim();
        String description = descriptionField.getText().trim();
        String uom         = uomComboBox.getValue();
        String priceText   = priceField.getText().trim();

        if (code.isEmpty() || description.isEmpty() || uom == null || priceText.isEmpty()) {
            showError("Please fill in all fields.");
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceText);
        } catch (NumberFormatException e) {
            showError("Price must be a valid number.");
            return;
        }

        // Copy image to Material_Icon folder and rename it to the material code.
        if (selectedImageFile != null) {
            try {
                File destDir = new File(IMAGE_DIR);
                destDir.mkdirs();
                String ext = selectedImageFile.getName().substring(selectedImageFile.getName().lastIndexOf('.'));
                File dest = new File(destDir, code + ext);
                Files.copy(selectedImageFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                showError("Failed to save image: " + e.getMessage());
                return;
            }
        }

        try {
            GoogleSheetsService service = new GoogleSheetsService();

            // col: 0=empty/date,1=code,2=desc,3=uom,4=price,5=initial stock,6=received,7=empty,8=balance,9=out qty,10=empty,11-41=days,42=total issued
            List<Object> row = Arrays.asList(
                "", code, description, uom, price, 0, 0, "", 0, 0, "",
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0
            );

            service.appendRow(TAB_NAME, row);
            closeDialog();

        } catch (Exception e) {
            showError("Failed to save: " + e.getMessage());
        }
    }

    @FXML
    private void closeDialog() {
        Stage stage = (Stage) codeField.getScene().getWindow();
        stage.close();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}
