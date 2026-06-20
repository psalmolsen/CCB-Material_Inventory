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
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Button;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.MenuItem;
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
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;

import java.io.File;
import java.net.URL;
import java.time.LocalDate;
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
    @FXML private Label materialInitialUnit;
    @FXML private Label materialLowCount;
    @FXML private Label materialReceivedUnit;
    @FXML private Label materialInCount;
    @FXML private Label materialBalanceUnit;
    @FXML private Label materialOutCount;
    @FXML private Label materialIssuedUnit;

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

    private static final String IMAGE_DIR = System.getProperty("user.dir") + "/src/imgs/Material_Icon/";
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
        materialsList.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, selectedItem) ->
            updateMaterialOverview(selectedItem)
        );

        showSection(0, "Material Monitoring");
        updateMaterialOverview(null);
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
                        InventoryItem item = SheetMapper.fromRow(row, i + 1);
                        item.setSheetTabName(TAB_NAME);
                        items.add(item);
                    }
                }
                return items;
            }
        };
        task.setOnSucceeded(e -> {
            materialsList.setItems(task.getValue());
            materialsList.getSelectionModel().clearSelection();
            updateMaterialOverview(null);
        });
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
            applyDialogStyles(scene);
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

    private void updateMaterialOverview(InventoryItem item) {
        if (item == null) {
            materialTotalCount.setText("0000");
            materialInitialUnit.setText("");
            materialLowCount.setText("0000");
            materialReceivedUnit.setText("");
            materialInCount.setText("0000");
            materialBalanceUnit.setText("");
            materialOutCount.setText("0000");
            materialIssuedUnit.setText("");
            return;
        }

        String uom = item.getUom() == null || item.getUom().isBlank() ? "UOM" : item.getUom().trim();
        materialTotalCount.setText(formatQuantity(item.getInitialStock()));
        materialInitialUnit.setText(uom);
        materialLowCount.setText(formatQuantity(item.getReceivedQuantity()));
        materialReceivedUnit.setText(uom);
        materialInCount.setText(formatQuantity(item.getCurrentBalance()));
        materialBalanceUnit.setText(uom);
        materialOutCount.setText(formatQuantity(item.getIssuedQuantity()));
        materialIssuedUnit.setText(uom);
    }

    private void openMonthlyOutDialog(InventoryItem item) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/ccb/material_month_out.fxml"));
            Parent root = loader.load();
            MaterialMonthOutController controller = loader.getController();
            controller.setMaterial(item);
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.TRANSPARENT);
            Scene scene = new Scene(root);
            applyDialogStyles(scene);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openEditMaterialDialog(InventoryItem item) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/ccb/edit_material.fxml"));
            Parent root = loader.load();
            EditMaterialController controller = loader.getController();
            controller.setMaterial(item);
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.TRANSPARENT);
            Scene scene = new Scene(root);
            applyDialogStyles(scene);
            stage.setScene(scene);
            stage.showAndWait();
            loadMaterials();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void applyDialogStyles(Scene scene) {
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/com/ccb/css/style.css").toExternalForm());
    }

    private String formatQuantity(double value) {
        return String.format("%.0f", value);
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

    private String colNumberToName(int col) {
        StringBuilder sb = new StringBuilder();
        while (col > 0) {
            col--; // 1-based to 0-based
            sb.insert(0, (char)('A' + (col % 26)));
            col /= 26;
        }
        return sb.toString();
    }

            private void showStockInDialog(InventoryItem item) {
                Stage dialog = new Stage();
                dialog.initModality(Modality.APPLICATION_MODAL);
                dialog.initStyle(StageStyle.TRANSPARENT);

                VBox root = new VBox(10);
                root.getStyleClass().add("stock-dialog");

                // number picker
                HBox stepper = new HBox(8);
                stepper.getStyleClass().add("stock-stepper");
                Button minus = new Button("-");
                minus.getStyleClass().add("stock-step-button");
                TextField qtyField = new TextField("0");
                qtyField.getStyleClass().add("stock-qty");
                qtyField.setPrefWidth(80);
                qtyField.setAlignment(Pos.CENTER);
                Button plus = new Button("+");
                plus.getStyleClass().add("stock-step-button");
                stepper.getChildren().addAll(minus, qtyField, plus);

                DatePicker datePicker = new DatePicker(LocalDate.now());

                HBox buttons = new HBox(8);
                Button cancel = new Button("Cancel");
                Button save = new Button("Save");
                buttons.getChildren().addAll(cancel, save);

                root.getChildren().addAll(new Label("Stock In (writes to column G)"), stepper, new Label("Date (column H)"), datePicker, buttons);

                minus.setOnAction(e -> {
                    try { int v = Integer.parseInt(qtyField.getText().trim()); qtyField.setText(String.valueOf(Math.max(0, v-1))); } catch (Exception ex) { qtyField.setText("0"); }
                });
                plus.setOnAction(e -> {
                    try { int v = Integer.parseInt(qtyField.getText().trim()); qtyField.setText(String.valueOf(v+1)); } catch (Exception ex) { qtyField.setText("1"); }
                });

                cancel.setOnAction(e -> dialog.close());
                save.setOnAction(e -> {
                    try {
                        String tab = item.getSheetTabName() == null || item.getSheetTabName().isBlank() ? TAB_NAME : item.getSheetTabName();
                        String qty = qtyField.getText().trim();
                        LocalDate date = datePicker.getValue();
                        if (qty.isEmpty() || date == null) return;

                        Task<Void> task = new Task<>() {
                            @Override
                            protected Void call() throws Exception {
                                GoogleSheetsService service = new GoogleSheetsService();
                                String gRange = tab + "!G" + item.getSheetRowNumber();
                                String hRange = tab + "!H" + item.getSheetRowNumber();
                                service.writeCell(gRange, Double.parseDouble(qty));
                                service.writeCell(hRange, date.toString());
                                return null;
                            }
                        };
                        task.setOnSucceeded(ev -> { dialog.close(); loadMaterials(); });
                        task.setOnFailed(ev -> { dialog.close(); });
                        new Thread(task).start();
                    } catch (Exception ex) { dialog.close(); }
                });

                Scene scene = new Scene(root);
                applyDialogStyles(scene);
                dialog.setScene(scene);
                dialog.showAndWait();
            }

            private void showStockOutDialog(InventoryItem item) {
                Stage dialog = new Stage();
                dialog.initModality(Modality.APPLICATION_MODAL);
                dialog.initStyle(StageStyle.TRANSPARENT);

                VBox root = new VBox(10);
                root.getStyleClass().add("stock-dialog");
                TextField qtyField = new TextField();
                DatePicker datePicker = new DatePicker(LocalDate.now());
                HBox buttons = new HBox(8);
                Button cancel = new Button("Cancel");
                Button save = new Button("Save");
                buttons.getChildren().addAll(cancel, save);
                root.getChildren().addAll(new Label("Record Daily Out (writes to L-AP based on day)"), qtyField, new Label("Date"), datePicker, buttons);
                cancel.setOnAction(e -> dialog.close());
                save.setOnAction(e -> {
                    try {
                        String qty = qtyField.getText().trim();
                        LocalDate date = datePicker.getValue();
                        if (qty.isEmpty() || date == null) return;
                        int day = date.getDayOfMonth();
                        int colNumber = 12 + (day - 1);
                        String colLetter = colNumberToName(colNumber);
                        String tab = item.getSheetTabName() == null || item.getSheetTabName().isBlank() ? TAB_NAME : item.getSheetTabName();
                        String range = tab + "!" + colLetter + item.getSheetRowNumber();
                        Task<Void> task = new Task<>() {
                            @Override
                            protected Void call() throws Exception {
                                GoogleSheetsService service = new GoogleSheetsService();
                                service.writeCell(range, Double.parseDouble(qty));
                                return null;
                            }
                        };
                        task.setOnSucceeded(ev -> { dialog.close(); loadMaterials(); });
                        task.setOnFailed(ev -> { dialog.close(); });
                        new Thread(task).start();
                    } catch (Exception ex) { dialog.close(); }
                });
                Scene scene = new Scene(root);
                applyDialogStyles(scene);
                dialog.setScene(scene);
                dialog.showAndWait();
            }

            private final class MaterialCardCell extends ListCell<InventoryItem> {
        private final HBox root = new HBox(16);
        private final StackPane thumbFrame = new StackPane();
        private final ImageView thumb = new ImageView();
        private final Label thumbFallback = new Label();
        private final VBox center = new VBox(7);
        private final HBox titleRow = new HBox(8);
        private final Label codeLabel = new Label();
        private final Button moreButton = new Button("\u22EF");
        private final Label descLabel = new Label();
        private final VBox pricePanel = new VBox(8);
        private final Label unitPriceLabel = new Label();
        private final Label unitPriceValue = new Label();
        private final Label totalPriceLabel = new Label();
        private final Label totalPriceValue = new Label();
        private final ContextMenu moreMenu = new ContextMenu();
        private InventoryItem currentItem;

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
            HBox.setHgrow(codeLabel, Priority.ALWAYS);
            moreButton.getStyleClass().add("material-more-button");
            moreButton.setTextFill(javafx.scene.paint.Color.web("#7b86aa"));
            moreButton.setFocusTraversable(false);
            moreButton.setOnAction(e -> {
                if (!moreMenu.isShowing()) {
                    moreMenu.show(moreButton, javafx.geometry.Side.BOTTOM, 0, 4);
                } else {
                    moreMenu.hide();
                }
            });

            VBox actionPanel = new VBox(8);
            actionPanel.getStyleClass().add("material-action-panel");
            Label actionTitle = new Label("Quick Actions");
            actionTitle.getStyleClass().add("material-action-title");
            Label actionHint = new Label("Choose what to inspect or edit");
            actionHint.getStyleClass().add("material-action-hint");

            Button monthOutButton = buildActionButton("View Monthly Daily Out", "Inspect each day for the selected month");
            monthOutButton.setOnAction(e -> {
                moreMenu.hide();
                if (currentItem != null) {
                    openMonthlyOutDialog(currentItem);
                }
            });

            Button editButton = buildActionButton("Edit A-J Data", "Update the sheet row fields");
            editButton.setOnAction(e -> {
                moreMenu.hide();
                if (currentItem != null) {
                    openEditMaterialDialog(currentItem);
                }
            });

            actionPanel.getChildren().addAll(actionTitle, actionHint, monthOutButton, editButton);
            CustomMenuItem actionItem = new CustomMenuItem(actionPanel, false);
            actionItem.setHideOnClick(false);
            moreMenu.getItems().add(actionItem);

            titleRow.getStyleClass().add("material-title-row");
                        Button stockInBtn = new Button("Stock In +");
                        stockInBtn.getStyleClass().add("stock-btn");
                        stockInBtn.setOnAction(e -> { if (currentItem != null) showStockInDialog(currentItem); });
                        Button stockOutBtn = new Button("Stock Out -");
                        stockOutBtn.getStyleClass().add("stock-btn");
                        stockOutBtn.setOnAction(e -> { if (currentItem != null) showStockOutDialog(currentItem); });
                        titleRow.getChildren().addAll(codeLabel, stockInBtn, stockOutBtn, moreButton);
                        descLabel.getStyleClass().add("material-desc");
                        descLabel.setWrapText(true);

            center.getStyleClass().add("material-content");
            center.getChildren().addAll(titleRow, descLabel);
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
                currentItem = null;
                moreMenu.hide();
                return;
            }

            currentItem = item;

            String code = item.getCodeNo() == null ? "" : item.getCodeNo().trim();
            String description = item.getDescription() == null ? "" : item.getDescription().trim();

            codeLabel.setText(code.isEmpty() ? "" : code);
            descLabel.setText(description.isEmpty() ? "No description provided" : description);

            unitPriceLabel.setText("UNIT PRICE");
            unitPriceValue.setText(String.format("\u20B1 %.2f", item.getUnitPrice()));
            totalPriceLabel.setText("TOTAL ISSUED VALUE");
            totalPriceValue.setText(String.format("\u20B1 %.2f", item.getTotalPrice()));

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

        private Button buildActionButton(String title, String subtitle) {
            Label titleLabel = new Label(title);
            titleLabel.getStyleClass().add("material-action-button-title");
            Label subtitleLabel = new Label(subtitle);
            subtitleLabel.getStyleClass().add("material-action-button-subtitle");

            VBox textBlock = new VBox(2, titleLabel, subtitleLabel);
            HBox row = new HBox(10);
            row.getStyleClass().add("material-action-row");
            Label icon = new Label("➜");
            icon.getStyleClass().add("material-action-icon");
            row.getChildren().addAll(textBlock, icon);
            HBox.setHgrow(textBlock, Priority.ALWAYS);

            Button button = new Button();
            button.getStyleClass().add("material-action-button");
            button.setGraphic(row);
            button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            button.setMaxWidth(Double.MAX_VALUE);
            return button;
        }
    }
}
