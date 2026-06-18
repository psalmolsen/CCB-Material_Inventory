package com.ccb;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    @FXML private VBox materialSection;
    @FXML private VBox cnfSection;
    @FXML private VBox oringSection;
    @FXML private VBox salesSection;

    @FXML private Button btnMaterial;
    @FXML private Button btnCnf;
    @FXML private Button btnOring;
    @FXML private Button btnSales;
    @FXML private Button btnAddMaterial;

    @FXML private Label pageTitle;

    @FXML private Label materialTotalCount;
    @FXML private Label materialLowCount;
    @FXML private Label materialInCount;
    @FXML private Label materialOutCount;

    @FXML private Label collarCount;
    @FXML private Label nameplateCount;
    @FXML private Label footringCount;

    @FXML private Label oringTotalCount;
    @FXML private Label oringLowCount;
    @FXML private Label oringIssuedCount;

    @FXML private Label salesTotalCount;
    @FXML private Label salesMonthCount;
    @FXML private Label salesTodayCount;

    @FXML private ListView<InventoryItem> materialsList;

    private static final String IMAGE_DIR = "src/imgs/Material_Icon/";
    private static final String TAB_NAME = "MAY";

    private List<Button> navButtons;
    private List<VBox> sections;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        navButtons = List.of(btnMaterial, btnCnf, btnOring, btnSales);
        sections = List.of(materialSection, cnfSection, oringSection, salesSection);

        materialsList.setFocusTraversable(false);
        materialsList.setFixedCellSize(126);
        materialsList.setCellFactory(list -> new MaterialCardCell());
        materialsList.setItems(FXCollections.observableArrayList());

        showSection(0, "Material Monitoring");
        loadMaterials();
    }

    public void loadMaterials() {
        Task<ObservableList<InventoryItem>> task = new Task<>() {
            @Override
            protected ObservableList<InventoryItem> call() throws Exception {
                GoogleSheetsService service = new GoogleSheetsService();
                List<List<Object>> rows = service.readSheet(TAB_NAME);
                ObservableList<InventoryItem> items = FXCollections.observableArrayList();
                for (int i = GoogleSheetsService.DATA_START_ROW; i < rows.size(); i++) {
                    List<Object> row = rows.get(i);
                    if (row.size() > 1 && !row.get(1).toString().isBlank()) {
                        items.add(SheetMapper.fromRow(row));
                    }
                }
                return items;
            }
        };
        task.setOnSucceeded(e -> materialsList.setItems(task.getValue()));
        task.setOnFailed(e -> System.err.println("Failed to load: " + task.getException().getMessage()));
        Thread loader = new Thread(task);
        loader.setDaemon(true);
        loader.start();
    }

    @FXML public void showMaterial() { showSection(0, "Material Monitoring"); }
    @FXML public void showCnf() { showSection(1, "CNF Monitoring"); }
    @FXML public void showOring() { showSection(2, "O-Ring Monitoring"); }
    @FXML public void showSales() { showSection(3, "Pellets L-Sales Report"); }

    @FXML
    public void openAddMaterial() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/ccb/add_material.fxml"));
            Parent root = loader.load();
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initStyle(StageStyle.TRANSPARENT);
            Scene scene = new Scene(root);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            dialog.setScene(scene);
            dialog.showAndWait();
            loadMaterials();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showSection(int index, String title) {
        pageTitle.setText(title);
        btnAddMaterial.setVisible(index == 0);
        btnAddMaterial.setManaged(index == 0);
        for (int i = 0; i < sections.size(); i++) {
            boolean active = i == index;
            sections.get(i).setVisible(active);
            sections.get(i).setManaged(active);
            Button btn = navButtons.get(i);
            btn.getStyleClass().removeAll("nav-btn-active");
            if (active) btn.getStyleClass().add("nav-btn-active");
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

    private String statusText(InventoryItem item) {
        double balance = item.getBalanceQuantity();
        if (balance <= 0) return "OUT OF STOCK";
        if (balance <= 10) return "LOW STOCK";
        return "AVAILABLE";
    }

    private String stockText(InventoryItem item) {
        return String.format("In %.0f  |  Issued %.0f  |  Balance %.0f",
            item.getStockIn(),
            item.getTotalIssued(),
            item.getBalanceQuantity());
    }

    private final class MaterialCardCell extends ListCell<InventoryItem> {
        private final HBox root = new HBox(16);
        private final StackPane thumbFrame = new StackPane();
        private final ImageView thumb = new ImageView();
        private final Label thumbFallback = new Label();
        private final VBox center = new VBox(7);
        private final Label codeLabel = new Label();
        private final Label descLabel = new Label();
        private final HBox chipsRow = new HBox(8);
        private final Label uomChip = new Label();
        private final Label stockChip = new Label();
        private final Label stockLine = new Label();
        private final VBox pricePanel = new VBox(8);
        private final Label unitPriceLabel = new Label();
        private final Label unitPriceValue = new Label();
        private final Label totalPriceLabel = new Label();
        private final Label totalPriceValue = new Label();

        private MaterialCardCell() {
            getStyleClass().add("material-list-cell");

            thumbFrame.getStyleClass().add("material-thumb-frame");
            thumb.setFitWidth(58);
            thumb.setFitHeight(58);
            thumb.setPreserveRatio(true);
            thumb.setSmooth(true);
            thumbFallback.getStyleClass().add("material-thumb-fallback");
            thumbFallback.setAlignment(Pos.CENTER);
            thumbFallback.setText("M");
            thumbFrame.getChildren().addAll(thumbFallback, thumb);

            codeLabel.getStyleClass().add("material-code");
            descLabel.getStyleClass().add("material-desc");
            descLabel.setWrapText(true);

            chipsRow.getStyleClass().add("material-chips-row");
            uomChip.getStyleClass().addAll("material-chip", "material-chip-blue");
            stockChip.getStyleClass().addAll("material-chip", "material-chip-yellow");
            stockLine.getStyleClass().add("material-stock-line");

            center.getStyleClass().add("material-content");
            center.getChildren().addAll(codeLabel, descLabel, chipsRow, stockLine);
            HBox.setHgrow(center, Priority.ALWAYS);

            pricePanel.getStyleClass().add("material-price-panel");
            pricePanel.setAlignment(Pos.CENTER_RIGHT);
            unitPriceLabel.getStyleClass().add("material-price-label");
            unitPriceValue.getStyleClass().add("material-price-value");
            totalPriceLabel.getStyleClass().add("material-price-label");
            totalPriceValue.getStyleClass().addAll("material-price-value", "material-total-value");
            pricePanel.getChildren().addAll(unitPriceLabel, unitPriceValue, totalPriceLabel, totalPriceValue);

            root.getStyleClass().add("material-card");
            root.getChildren().addAll(thumbFrame, center, pricePanel);
            root.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(InventoryItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            String code = item.getCodeNo() == null ? "" : item.getCodeNo().trim();
            String description = item.getDescription() == null ? "" : item.getDescription().trim();

            codeLabel.setText(code.isEmpty() ? "Unnamed material" : code);
            descLabel.setText(description.isEmpty() ? "No description provided" : description);
            uomChip.setText(item.getUom() == null || item.getUom().isBlank() ? "UOM" : item.getUom());
            stockChip.setText(statusText(item));
            stockChip.getStyleClass().removeAll("material-chip-blue", "material-chip-yellow", "material-chip-red");
            if (item.getBalanceQuantity() <= 0) {
                stockChip.getStyleClass().add("material-chip-red");
            } else if (item.getBalanceQuantity() <= 10) {
                stockChip.getStyleClass().add("material-chip-yellow");
            } else {
                stockChip.getStyleClass().add("material-chip-blue");
            }

            unitPriceLabel.setText("UNIT PRICE");
            unitPriceValue.setText(String.format("\u20B1 %.2f", item.getUnitPrice()));
            totalPriceLabel.setText("TOTAL ISSUED VALUE");
            totalPriceValue.setText(String.format("\u20B1 %.2f", item.getTotalPrice()));
            stockLine.setText(stockText(item));

            chipsRow.getChildren().setAll(uomChip, stockChip);

            thumbFallback.setText(code.isEmpty() ? "M" : code.substring(0, Math.min(2, code.length())).toUpperCase());
            File imageFile = resolveImageFile(code);
            if (imageFile != null) {
                thumb.setImage(new Image(imageFile.toURI().toString(), 58, 58, true, true));
                thumb.setVisible(true);
                thumbFallback.setVisible(false);
            } else {
                thumb.setImage(null);
                thumb.setVisible(false);
                thumbFallback.setVisible(true);
            }

            setText(null);
            setGraphic(root);
        }
    }
}
