package com.ccb.controller.modal;

import com.ccb.GoogleSheetsService;
import com.ccb.InventoryItem;
import com.ccb.SheetMapper;
import com.ccb.StationConsumptionRecord;
import com.ccb.StationConsumptionSheetService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public class AddConsumptionController implements Initializable {

    @FXML private Label dateLabel;
    @FXML private FlowPane stationCardsHost;
    @FXML private TextField searchField;
    @FXML private ListView<InventoryItem> materialsList;
    @FXML private VBox materialsEmptyState;
    @FXML private Label selectedStationLabel;
    @FXML private Label selectedMaterialLabel;
    @FXML private Label selectedMaterialCodeLabel;
    @FXML private Label selectedMaterialUnitLabel;
    @FXML private Label selectedMaterialBalanceLabel;
    @FXML private Label selectedMaterialUnitCostLabel;
    @FXML private Label selectedMaterialTotalCostLabel;
    @FXML private TextField receiverField;
    @FXML private TextField quantityField;
    @FXML private Button minusButton;
    @FXML private Button plusButton;
    @FXML private Label errorLabel;
    @FXML private Button saveButton;

    private final ObservableList<InventoryItem> materials = FXCollections.observableArrayList();
    private final FilteredList<InventoryItem> filteredMaterials = new FilteredList<>(materials, item -> true);
    private final Map<Button, StationOption> stationButtons = new LinkedHashMap<>();
    private final List<StationOption> stationOptions = List.of(
            new StationOption("Hotworks", "Heat, cutting, and weld processes", "#C0392B", "#FFFFFF"),
            new StationOption("Painting", "Paint and finishing station", "#293A92", "#FFFFFF"),
            new StationOption("Cosmetics", "Surface touch-up and finishing", "#E9B52D", "#1A2560"),
            new StationOption("CTC", "General CTC station", "#1F5FA6", "#FFFFFF")
    );

    private StationOption selectedStation;
    private InventoryItem selectedMaterial;
    private String currentInventoryTab;
    private Runnable onSaveCallback;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        dateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
        receiverField.textProperty().addListener((obs, oldValue, newValue) -> updateActionState());
        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            String q = newValue == null ? "" : newValue.trim().toLowerCase();
            filteredMaterials.setPredicate(item -> {
                if (q.isBlank()) {
                    return true;
                }
                String code = item.getCodeNo() == null ? "" : item.getCodeNo().toLowerCase();
                String desc = item.getDescription() == null ? "" : item.getDescription().toLowerCase();
                String uom = item.getUom() == null ? "" : item.getUom().toLowerCase();
                return code.contains(q) || desc.contains(q) || uom.contains(q);
            });
        });

        setupQuantityField();
        setupMaterialsList();
        buildStationCards();
        bindSummaryText();

        if (saveButton != null) {
            saveButton.setOnAction(e -> saveConsumption());
        }
        if (minusButton != null) {
            minusButton.setOnAction(e -> adjustQuantity(-1));
        }
        if (plusButton != null) {
            plusButton.setOnAction(e -> adjustQuantity(1));
        }

        loadData();
    }

    public void setOnSaveCallback(Runnable onSaveCallback) {
        this.onSaveCallback = onSaveCallback;
    }

    @FXML
    private void closeDialog() {
        Stage stage = (Stage) dateLabel.getScene().getWindow();
        stage.close();
    }

    private void loadData() {
        showLoading(true);
        Task<List<InventoryItem>> task = new Task<>() {
            @Override
            protected List<InventoryItem> call() throws Exception {
                GoogleSheetsService service = new GoogleSheetsService();
                List<String> tabs = service.getSheetTabNames();
                currentInventoryTab = resolveCurrentTab(tabs);

                List<List<Object>> rows = service.readSheet(currentInventoryTab);
                List<InventoryItem> loaded = new ArrayList<>();
                for (int i = GoogleSheetsService.DATA_START_ROW; i < rows.size(); i++) {
                    List<Object> row = rows.get(i);
                    if (row == null || row.size() <= 1 || row.get(1) == null || row.get(1).toString().isBlank()) {
                        continue;
                    }
                    loaded.add(SheetMapper.fromRow(row, i + 1));
                }
                return loaded;
            }
        };

        task.setOnSucceeded(e -> {
            materials.setAll(task.getValue());
            showMaterialsEmpty(materials.isEmpty());
            if (!materials.isEmpty()) {
                materialsList.getSelectionModel().selectFirst();
            }
            if (selectedStation == null && !stationOptions.isEmpty()) {
                selectStation(stationOptions.get(0));
            }
            refreshSelectionDetails();
            showLoading(false);
        });

        task.setOnFailed(e -> {
            errorLabel.setText("Unable to load materials: " + task.getException().getMessage());
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
            showLoading(false);
            showMaterialsEmpty(true);
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void setupMaterialsList() {
        materialsList.setItems(filteredMaterials);
        materialsList.setFocusTraversable(false);
        materialsList.setCellFactory(list -> new MaterialCardCell());
        materialsList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            selectedMaterial = newValue;
            materialsList.refresh();
            refreshSelectionDetails();
            updateActionState();
        });
    }

    private void setupQuantityField() {
        TextFormatter<String> formatter = new TextFormatter<>(new StringConverter<String>() {
            @Override
            public String toString(String object) {
                return object == null ? "" : object;
            }

            @Override
            public String fromString(String string) {
                return string == null ? "" : string;
            }
        }, "1", change -> {
            String next = change.getControlNewText();
            if (next == null || next.isBlank()) {
                return change;
            }
            if (next.matches("\\d*(\\.\\d*)?")) {
                return change;
            }
            return null;
        });
        quantityField.setTextFormatter(formatter);
        quantityField.textProperty().addListener((obs, oldValue, newValue) -> refreshSelectionDetails());
        if (quantityField.getText() == null || quantityField.getText().isBlank()) {
            quantityField.setText("1");
        }
    }

    private void buildStationCards() {
        stationCardsHost.getChildren().clear();
        stationButtons.clear();

        for (StationOption option : stationOptions) {
            Button card = buildStationButton(option);
            stationButtons.put(card, option);
            stationCardsHost.getChildren().add(card);
        }

        if (!stationOptions.isEmpty()) {
            selectStation(stationOptions.get(0));
        }
    }

    private Button buildStationButton(StationOption option) {
        VBox badge = new VBox();
        badge.setAlignment(Pos.CENTER);
        badge.setPrefSize(42, 42);
        badge.setMinSize(42, 42);
        badge.setMaxSize(42, 42);
        badge.setStyle(
                "-fx-background-color: " + option.color() + "; " +
                "-fx-background-radius: 999; " +
                "-fx-padding: 0; " +
                "-fx-effect: dropshadow(gaussian, rgba(26,37,96,0.12), 12, 0, 0, 4);");

        Label initials = new Label(option.abbreviation());
        initials.setStyle("-fx-text-fill: " + option.foreground() + "; -fx-font-size: 12px; -fx-font-weight: bold;");
        badge.getChildren().add(initials);

        Label title = new Label(option.name());
        title.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1A2560;");

        Label subtitle = new Label(option.subtitle());
        subtitle.setStyle("-fx-font-size: 10px; -fx-text-fill: #7B86AA;");

        VBox copy = new VBox(2, title, subtitle);
        HBox.setHgrow(copy, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(12, badge, copy, spacer);
        row.setAlignment(Pos.CENTER_LEFT);

        Button card = new Button();
        card.setGraphic(row);
        card.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setPrefWidth(220);
        card.setMinWidth(220);
        card.getStyleClass().add("station-choice-card");
        card.setOnAction(e -> selectStation(option));
        card.setFocusTraversable(false);
        return card;
    }

    private void selectStation(StationOption option) {
        selectedStation = option;
        for (Map.Entry<Button, StationOption> entry : stationButtons.entrySet()) {
            Button button = entry.getKey();
            boolean active = entry.getValue().equals(option);
            if (active) {
                button.getStyleClass().add("station-choice-card-selected");
            } else {
                button.getStyleClass().remove("station-choice-card-selected");
            }
        }
        refreshSelectionDetails();
        updateActionState();
    }

    private void refreshSelectionDetails() {
        if (dateLabel != null) {
            dateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
        }

        if (selectedStationLabel != null) {
            selectedStationLabel.setText(selectedStation == null ? "Choose a station" : selectedStation.name());
        }

        if (selectedMaterial == null) {
            selectedMaterialLabel.setText("Choose a material");
            selectedMaterialCodeLabel.setText("No material selected");
            selectedMaterialUnitLabel.setText("UOM: --");
            selectedMaterialBalanceLabel.setText("Balance: --");
            selectedMaterialUnitCostLabel.setText("Unit cost: --");
            selectedMaterialTotalCostLabel.setText("Total cost: --");
            updateActionState();
            return;
        }

        selectedMaterialLabel.setText(selectedMaterial.getDescription() == null || selectedMaterial.getDescription().isBlank()
                ? "No description" : selectedMaterial.getDescription());
        selectedMaterialCodeLabel.setText(selectedMaterial.getCodeNo() == null ? "--" : selectedMaterial.getCodeNo());
        selectedMaterialUnitLabel.setText("UOM: " + safeText(selectedMaterial.getUom(), "--"));
        selectedMaterialBalanceLabel.setText(String.format("Balance: %.2f", selectedMaterial.getBalanceQuantity()));
        selectedMaterialUnitCostLabel.setText(selectedMaterial.getUnitPrice() == SheetMapper.PRICE_NA
                ? "Unit cost: N/A"
                : String.format("Unit cost: ₱ %.2f", selectedMaterial.getUnitPrice()));

        double qty = getQuantity();
        double unitCost = selectedMaterial.getUnitPrice();
        if (unitCost == SheetMapper.PRICE_NA) {
            selectedMaterialTotalCostLabel.setText("Total cost: N/A");
        } else {
            selectedMaterialTotalCostLabel.setText(String.format("Total cost: ₱ %.2f", qty * unitCost));
        }
        updateActionState();
    }

    private void bindSummaryText() {
        quantityField.textProperty().addListener((obs, oldValue, newValue) -> {
            if (selectedMaterial != null) {
                refreshSelectionDetails();
            }
        });
    }

    private void adjustQuantity(double delta) {
        double next = Math.max(0, getQuantity() + delta);
        quantityField.setText(trimNumber(next));
        refreshSelectionDetails();
    }

    private double getQuantity() {
        try {
            String text = quantityField.getText() == null ? "" : quantityField.getText().trim();
            if (text.isBlank()) {
                return 0.0;
            }
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private void updateActionState() {
        boolean canSave = selectedStation != null
                && selectedMaterial != null
                && receiverField.getText() != null
                && !receiverField.getText().trim().isBlank()
                && getQuantity() > 0;
        saveButton.setDisable(!canSave);
    }

    private void showLoading(boolean loading) {
        if (materialsList != null) {
            materialsList.setDisable(loading);
        }
        if (stationCardsHost != null) {
            stationCardsHost.setDisable(loading);
        }
        if (loading) {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        }
    }

    private void showMaterialsEmpty(boolean empty) {
        if (materialsEmptyState != null) {
            materialsEmptyState.setVisible(empty);
            materialsEmptyState.setManaged(empty);
        }
    }

    private void saveConsumption() {
        if (selectedStation == null || selectedMaterial == null) {
            showError("Select a station and a material first.");
            return;
        }

        String receiver = receiverField.getText() == null ? "" : receiverField.getText().trim();
        if (receiver.isBlank()) {
            showError("Please enter the receiver name.");
            return;
        }

        double qty = getQuantity();
        if (qty <= 0) {
            showError("Quantity must be greater than zero.");
            return;
        }

        LocalDate date = LocalDate.now();
        double unitCost = selectedMaterial.getUnitPrice();
        double totalCost = unitCost == SheetMapper.PRICE_NA ? 0.0 : unitCost * qty;
        String stationName = selectedStation.name();
        String materialCode = safeText(selectedMaterial.getCodeNo(), "");
        String description = safeText(selectedMaterial.getDescription(), "");
        String uom = safeText(selectedMaterial.getUom(), "");

        saveButton.setDisable(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                StationConsumptionRecord record = new StationConsumptionRecord(
                        date,
                        stationName,
                        materialCode,
                        description,
                        qty,
                        uom,
                        unitCost == SheetMapper.PRICE_NA ? 0.0 : unitCost,
                        totalCost,
                        receiver
                );

                StationConsumptionSheetService ledgerService = new StationConsumptionSheetService();
                ledgerService.addRecord(record);

                GoogleSheetsService inventoryService = new GoogleSheetsService();
                List<List<Object>> rows = inventoryService.readSheet(currentInventoryTab);

                InventoryItem fresh = null;
                for (int i = GoogleSheetsService.DATA_START_ROW; i < rows.size(); i++) {
                    List<Object> row = rows.get(i);
                    if (row == null || row.size() <= 1 || row.get(1) == null) {
                        continue;
                    }
                    String code = row.get(1).toString().trim();
                    if (code.equalsIgnoreCase(materialCode)) {
                        fresh = SheetMapper.fromRow(row, i + 1);
                        break;
                    }
                }

                if (fresh == null) {
                    throw new IllegalStateException("Material row not found in current inventory tab.");
                }

                int day = date.getDayOfMonth();
                double updatedDayValue = fresh.getDayValue(day) + qty;
                String dayColumn = colNumberToName(11 + (day - 1));
                String targetRange = currentInventoryTab + "!" + dayColumn + fresh.getSheetRowNumber();
                inventoryService.writeCell(targetRange, updatedDayValue);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            if (onSaveCallback != null) {
                onSaveCallback.run();
            }
            closeDialog();
        });

        task.setOnFailed(e -> {
            showError("Failed to save consumption: " + task.getException().getMessage());
            saveButton.setDisable(false);
        });

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        saveButton.setDisable(false);
    }

    private String resolveCurrentTab(List<String> tabs) {
        if (tabs == null || tabs.isEmpty()) {
            LocalDate now = LocalDate.now();
            return now.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase();
        }

        LocalDate now = LocalDate.now();
        String shortName = now.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase();
        String fullName = now.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase();

        for (String tab : tabs) {
            if (tab.equalsIgnoreCase(shortName) || tab.equalsIgnoreCase(fullName)) {
                return tab;
            }
        }

        return tabs.get(tabs.size() - 1);
    }

    private static String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String trimNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.00001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private static String colNumberToName(int col) {
        StringBuilder sb = new StringBuilder();
        while (col > 0) {
            int rem = (col - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            col = (col - 1) / 26;
        }
        return sb.toString();
    }

    private final class MaterialCardCell extends ListCell<InventoryItem> {
        private final HBox root = new HBox(12);
        private final StackPane thumbFrame = new StackPane();
        private final ImageView thumb = new ImageView();
        private final Label thumbFallback = new Label();
        private final VBox textBlock = new VBox(3);
        private final Label codeLabel = new Label();
        private final Label descLabel = new Label();
        private final HBox metaRow = new HBox(8);
        private final Label balanceBadge = new Label();
        private final Label uomBadge = new Label();
        private final Label priceBadge = new Label();
        private InventoryItem currentItem;

        private MaterialCardCell() {
            root.getStyleClass().add("consumption-material-card");
            root.setAlignment(Pos.CENTER_LEFT);
            root.setMinHeight(104);
            root.setPrefHeight(104);

            thumbFrame.getStyleClass().add("consumption-thumb-frame");
            thumbFrame.setPrefSize(62, 62);
            thumbFrame.setMinSize(62, 62);
            thumbFrame.setMaxSize(62, 62);
            thumb.setFitWidth(60);
            thumb.setFitHeight(60);
            thumb.setPreserveRatio(true);
            thumbFallback.getStyleClass().add("consumption-thumb-fallback");
            thumbFallback.setText("MAT");
            thumbFrame.getChildren().addAll(thumbFallback, thumb);

            codeLabel.getStyleClass().add("consumption-material-code");
            descLabel.getStyleClass().add("consumption-material-desc");
            descLabel.setWrapText(true);
            descLabel.setMaxWidth(320);

            balanceBadge.getStyleClass().add("consumption-badge");
            uomBadge.getStyleClass().add("consumption-badge");
            priceBadge.getStyleClass().add("consumption-badge");

            metaRow.getChildren().addAll(balanceBadge, uomBadge, priceBadge);
            textBlock.getChildren().addAll(codeLabel, descLabel, metaRow);
            HBox.setHgrow(textBlock, Priority.ALWAYS);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            root.getChildren().addAll(thumbFrame, textBlock, spacer);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(InventoryItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                currentItem = null;
                setGraphic(null);
                return;
            }

            currentItem = item;
            codeLabel.setText(safeText(item.getCodeNo(), "--"));
            descLabel.setText(safeText(item.getDescription(), "No description"));
            balanceBadge.setText(String.format("Balance %.0f", item.getBalanceQuantity()));
            uomBadge.setText(safeText(item.getUom(), "UOM"));
            priceBadge.setText(item.getUnitPrice() == SheetMapper.PRICE_NA
                    ? "Price N/A"
                    : String.format("₱ %.2f", item.getUnitPrice()));

            thumbFallback.setText(codeLabel.getText().equals("--")
                    ? "MAT"
                    : codeLabel.getText().substring(0, Math.min(3, codeLabel.getText().length())).toUpperCase());

            File imageFile = resolveImageFile(codeLabel.getText());
            if (imageFile != null) {
                thumb.setImage(new Image(imageFile.toURI().toString(), 56, 56, true, true));
                thumb.setVisible(true);
                thumbFallback.setVisible(false);
            } else {
                thumb.setImage(null);
                thumb.setVisible(false);
                thumbFallback.setVisible(true);
            }

            boolean selected = item.equals(materialsList.getSelectionModel().getSelectedItem());
            if (selected) {
                root.getStyleClass().add("consumption-material-card-selected");
            } else {
                root.getStyleClass().remove("consumption-material-card-selected");
            }

            setGraphic(root);
        }

        private File resolveImageFile(String code) {
            if (code == null || code.isBlank()) {
                return null;
            }
            String base = System.getProperty("user.dir") + "/src/imgs/Material_Icon/";
            File dir = new File(base);
            if (!dir.exists()) {
                return null;
            }
            File[] matches = dir.listFiles((d, name) -> name.toLowerCase().startsWith(code.toLowerCase() + "."));
            if (matches != null && matches.length > 0) {
                return matches[0];
            }
            return null;
        }
    }

    private record StationOption(String name, String subtitle, String color, String foreground) {
        String abbreviation() {
            String[] parts = name.split("\\s+");
            if (parts.length == 1) {
                return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
            }
            StringBuilder builder = new StringBuilder();
            for (String part : parts) {
                if (!part.isBlank()) {
                    builder.append(Character.toUpperCase(part.charAt(0)));
                }
            }
            return builder.length() > 0 ? builder.toString() : "ST";
        }
    }
}
