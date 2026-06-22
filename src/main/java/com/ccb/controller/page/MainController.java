package com.ccb.controller.page;

import com.ccb.GoogleSheetsService;
import com.ccb.InventoryItem;
import com.ccb.MonthSheetProvisioner;
import com.ccb.SheetMapper;
import com.ccb.controller.modal.EditMaterialController;
import com.ccb.controller.modal.MaterialMonthOutController;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class MainController implements Initializable {

    @FXML
    private VBox materialSection;
    @FXML
    private VBox cnfSection;
    @FXML
    private VBox oringSection;
    @FXML
    private VBox salesSection;

    @FXML
    private Button btnMaterial;
    @FXML
    private Button btnCnf;
    @FXML
    private Button btnOring;
    @FXML
    private Button btnSales;
    @FXML
    private Button btnAddMaterial;

    @FXML
    private Label pageTitle;

    @FXML
    private Label materialTotalCount;
    @FXML
    private Label materialInitialUnit;
    @FXML
    private Label materialLowCount;
    @FXML
    private Label materialReceivedUnit;
    @FXML
    private Label materialInCount;
    @FXML
    private Label materialBalanceUnit;
    @FXML
    private Label materialOutCount;
    @FXML
    private Label materialIssuedUnit;

    @FXML
    private Label collarCount;
    @FXML
    private Label nameplateCount;
    @FXML
    private Label footringCount;

    @FXML
    private Label oringTotalCount;
    @FXML
    private Label oringLowCount;
    @FXML
    private Label oringIssuedCount;

    @FXML
    private Label salesTotalCount;
    @FXML
    private Label salesMonthCount;
    @FXML
    private Label salesTodayCount;

    @FXML
    private ListView<InventoryItem> materialsList;

    @FXML
    private ComboBox<String> reportRangeCombo;

    @FXML
    private TextField materialSearchField;

    private static final String IMAGE_DIR = System.getProperty("user.dir") + "/src/imgs/Material_Icon/";
    private static final String TAB_NAME = "MAY";

    // Tab abbreviation → month number mapping
    private static final Map<String, Integer> TAB_TO_MONTH = new HashMap<>();
    static {
        String[] tabs = {"JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"};
        for (int i = 0; i < tabs.length; i++) TAB_TO_MONTH.put(tabs[i], i + 1);
    }
    private static final String[] MONTH_TABS = {"JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"};

    // Cache of already-fetched tabs: tabName → list of items
    private final Map<String, List<InventoryItem>> tabCache = new HashMap<>();

    // Full unfiltered list — search filters against this
    private final ObservableList<InventoryItem> allMaterials = FXCollections.observableArrayList();

    private List<Button> navButtons;
    private List<VBox> sections;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        navButtons = List.of(btnMaterial, btnCnf, btnOring, btnSales);
        sections = List.of(materialSection, cnfSection, oringSection, salesSection);

        btnMaterial.setOnAction(e -> showMaterial());
        btnCnf.setOnAction(e -> showCnf());
        btnOring.setOnAction(e -> showOring());
        btnSales.setOnAction(e -> showSales());
        btnAddMaterial.setOnAction(e -> openAddMaterial());

        materialsList.setFocusTraversable(false);
        materialsList.setFixedCellSize(126);
        materialsList.setCellFactory(list -> new MaterialCardCell());
        materialsList.setItems(FXCollections.observableArrayList());
        materialsList.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldItem, selectedItem) -> updateMaterialOverview(selectedItem));

        // Search bar — filters the full list live as the user types
        FilteredList<InventoryItem> filteredMaterials = new FilteredList<>(allMaterials, p -> true);
        materialsList.setItems(filteredMaterials);
        materialSearchField.textProperty().addListener((obs, oldVal, query) -> {
            String q = query == null ? "" : query.trim().toLowerCase();
            filteredMaterials.setPredicate(item -> {
                if (q.isEmpty()) return true;
                String code = item.getCodeNo() == null ? "" : item.getCodeNo().toLowerCase();
                String desc = item.getDescription() == null ? "" : item.getDescription().toLowerCase();
                return code.contains(q) || desc.contains(q);
            });
        });

        // Report range dropdown
        reportRangeCombo.getItems().addAll("Weekly", "Monthly", "Quarterly", "Yearly");
        reportRangeCombo.getSelectionModel().select("Monthly");
        reportRangeCombo.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, range) -> refreshCardValues());

        showSection(0, "Material Monitoring");
        updateMaterialOverview(null);
        loadMaterials();
        ensureCurrentMonthTab();
    }

    /**
     * Runs on startup in a background thread.
     * Creates the current month's sheet tab if it doesn't exist yet,
     * pre-filling it with identity columns and carrying forward the previous month's balance.
     */
    private void ensureCurrentMonthTab() {
        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return MonthSheetProvisioner.provisionCurrentMonth();
            }
        };
        task.setOnSucceeded(e -> {
            String created = task.getValue();
            if (created != null) {
                System.out.println("New month tab ready: " + created);
                // Optionally reload if the created tab matches TAB_NAME
            }
        });
        task.setOnFailed(e ->
            System.err.println("Month provisioning failed: " + task.getException().getMessage())
        );
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    public void loadMaterials() {
        Task<ObservableList<InventoryItem>> task = new Task<>() {
            @Override
            protected ObservableList<InventoryItem> call() throws Exception {
                GoogleSheetsService service = new GoogleSheetsService();
                List<List<Object>> rows = service.readSheet(TAB_NAME);
                System.out.println("DEBUG: Tab=" + TAB_NAME + "  total rows=" + rows.size() + "  DATA_START_ROW=" + GoogleSheetsService.DATA_START_ROW);
                ObservableList<InventoryItem> items = FXCollections.observableArrayList();
                List<InventoryItem> cacheList = new ArrayList<>();
                for (int i = GoogleSheetsService.DATA_START_ROW; i < rows.size(); i++) {
                    List<Object> row = rows.get(i);
                    if (row.size() > 1 && !row.get(1).toString().isBlank()) {
                        InventoryItem item = SheetMapper.fromRow(row, i + 1);
                        item.setSheetTabName(TAB_NAME);
                        items.add(item);
                        cacheList.add(item);
                    }
                }
                System.out.println("DEBUG: items parsed=" + items.size());
                tabCache.put(TAB_NAME, cacheList);
                return items;
            }
        };
        task.setOnSucceeded(e -> {
            allMaterials.setAll(task.getValue());
            materialsList.getSelectionModel().clearSelection();
            updateMaterialOverview(null);
            prefetchTabsForRange(reportRangeCombo.getSelectionModel().getSelectedItem());
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            System.err.println("Failed to load materials: " + ex.getMessage());
            ex.printStackTrace();
        });
        Thread loader = new Thread(task);
        loader.setDaemon(true);
        loader.start();
    }

    /**
     * Pre-fetch tabs needed for quarterly/yearly ranges so they're ready in cache.
     */
    private void prefetchTabsForRange(String range) {
        List<String> needed = getTabsForRange(range);
        List<String> missing = new ArrayList<>();
        for (String tab : needed) {
            if (!tabCache.containsKey(tab)) missing.add(tab);
        }
        if (missing.isEmpty()) return;

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                GoogleSheetsService service = new GoogleSheetsService();
                for (String tab : missing) {
                    try {
                        List<List<Object>> rows = service.readSheet(tab);
                        List<InventoryItem> items = new ArrayList<>();
                        for (int i = GoogleSheetsService.DATA_START_ROW; i < rows.size(); i++) {
                            List<Object> row = rows.get(i);
                            if (row.size() > 1 && !row.get(1).toString().isBlank()) {
                                InventoryItem item = SheetMapper.fromRow(row, i + 1);
                                item.setSheetTabName(tab);
                                items.add(item);
                            }
                        }
                        tabCache.put(tab, items);
                    } catch (Exception ex) {
                        // Tab may not exist yet — skip silently
                    }
                }
                return null;
            }
        };
        task.setOnSucceeded(e -> refreshCardValues());
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    /**
     * Returns the list of sheet tabs needed for a given range, based on today's date.
     */
    private List<String> getTabsForRange(String range) {
        List<String> tabs = new ArrayList<>();
        if (range == null) { tabs.add(TAB_NAME); return tabs; }
        LocalDate today = LocalDate.now();
        int currentMonthIdx = today.getMonthValue() - 1; // 0-based index into MONTH_TABS

        switch (range) {
            case "Weekly":
            case "Monthly":
                tabs.add(TAB_NAME);
                break;
            case "Quarterly": {
                // Quarter start month (0-based): Q1=0,Q2=3,Q3=6,Q4=9
                int qStart = (currentMonthIdx / 3) * 3;
                for (int i = qStart; i <= currentMonthIdx; i++) {
                    tabs.add(MONTH_TABS[i]);
                }
                break;
            }
            case "Yearly": {
                for (int i = 0; i <= currentMonthIdx; i++) {
                    tabs.add(MONTH_TABS[i]);
                }
                break;
            }
            default:
                tabs.add(TAB_NAME);
        }
        return tabs;
    }

    /**
     * Computes the issued quantity for an item based on the selected range and today's date.
     * Always starts from day 1 of the period.
     */
    double computeRangeIssued(InventoryItem baseItem, String range) {
        LocalDate today = LocalDate.now();
        int todayDay = today.getDayOfMonth();

        switch (range == null ? "Monthly" : range) {
            case "Weekly": {
                // Week starts Monday; find first day of this week, clamped to day 1
                int dow = today.getDayOfWeek().getValue(); // Mon=1 … Sun=7
                int weekStartDay = Math.max(1, todayDay - (dow - 1));
                double sum = 0;
                for (int d = weekStartDay; d <= todayDay; d++) {
                    sum += baseItem.getDayValue(d);
                }
                return sum;
            }
            case "Monthly": {
                // Day 1 up to today's day-of-month
                double sum = 0;
                for (int d = 1; d <= todayDay; d++) {
                    sum += baseItem.getDayValue(d);
                }
                return sum;
            }
            case "Quarterly":
            case "Yearly": {
                List<String> tabs = getTabsForRange(range);
                String currentTab = TAB_NAME;
                double sum = 0;
                for (String tab : tabs) {
                    boolean isCurrent = tab.equals(currentTab);
                    if (isCurrent) {
                        // Only days 1 → today
                        for (int d = 1; d <= todayDay; d++) {
                            sum += baseItem.getDayValue(d);
                        }
                    } else {
                        // Past complete months — look up matching item from cache
                        List<InventoryItem> cached = tabCache.get(tab);
                        if (cached != null) {
                            for (InventoryItem other : cached) {
                                if (baseItem.getCodeNo() != null &&
                                    baseItem.getCodeNo().equalsIgnoreCase(other.getCodeNo())) {
                                    sum += other.getTotalIssued();
                                    break;
                                }
                            }
                        }
                    }
                }
                return sum;
            }
            default:
                return baseItem.getTotalIssued();
        }
    }

    /**
     * Forces all visible cards to re-render their value labels.
     */
    private void refreshCardValues() {
        // Trigger a refresh by briefly cycling the cell factory
        materialsList.refresh();
    }

    @FXML
    public void showMaterial() {
        showSection(0, "Material Monitoring");
    }

    @FXML
    public void showCnf() {
        showSection(1, "CNF Monitoring");
    }

    @FXML
    public void showOring() {
        showSection(2, "O-Ring Monitoring");
    }

    @FXML
    public void showSales() {
        showSection(3, "Pellets L-Sales Report");
    }

    @FXML
    public void openAddMaterial() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/ccb/fxml/modal/add_material.fxml"));
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
            if (active)
                btn.getStyleClass().add("nav-btn-active");
        }
    }

    private void updateMaterialOverview(InventoryItem item) {
        if (item == null) {
            materialTotalCount.setText("00");
            materialInitialUnit.setText("");
            materialLowCount.setText("00");
            materialReceivedUnit.setText("");
            materialInCount.setText("00");
            materialBalanceUnit.setText("");
            materialOutCount.setText("00");
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/ccb/fxml/modal/material_month_out.fxml"));
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/ccb/fxml/modal/edit_material.fxml"));
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
        if (png.exists())
            return png;

        File jpg = new File(IMAGE_DIR + code + ".jpg");
        if (jpg.exists())
            return jpg;

        File jpeg = new File(IMAGE_DIR + code + ".jpeg");
        if (jpeg.exists())
            return jpeg;

        return null;
    }

    private String colNumberToName(int col) {
        StringBuilder sb = new StringBuilder();
        while (col > 0) {
            col--; // 1-based to 0-based
            sb.insert(0, (char) ('A' + (col % 26)));
            col /= 26;
        }
        return sb.toString();
    }

    private void showStockInDialog(InventoryItem item) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox(0);
        root.getStyleClass().add("dialog-shell");
        root.setPrefWidth(420);

        // --- HEADER ---
        VBox header = new VBox(4);
        header.getStyleClass().addAll("dialog-header", "dialog-header-alt");

        HBox headerRow = new HBox(10);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        VBox headerTitles = new VBox(4);
        HBox.setHgrow(headerTitles, Priority.ALWAYS);
        Label eyebrow = new Label("STOCK-IN RECORDING");
        eyebrow.getStyleClass().add("dialog-eyebrow");
        Label title = new Label("Add Received Quantity");
        title.getStyleClass().add("dialog-title");
        Label subtitle = new Label("Add item quantity to inventory stock.");
        subtitle.getStyleClass().add("dialog-subtitle");
        headerTitles.getChildren().addAll(eyebrow, title, subtitle);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 14px; -fx-cursor: hand; -fx-border-width: 0; -fx-padding: 0;");
        closeBtn.setOnAction(e -> dialog.close());

        headerRow.getChildren().addAll(headerTitles, closeBtn);
        header.getChildren().add(headerRow);

        // --- BODY ---
        VBox body = new VBox(14);
        body.getStyleClass().add("dialog-body");

        // Context Panel (Hero Panel)
        VBox hero = new VBox(6);
        hero.getStyleClass().add("hero-panel");
        Label heroTag = new Label(item.getCodeNo() != null ? item.getCodeNo().toUpperCase() : "MATERIAL");
        heroTag.getStyleClass().add("hero-tag");
        Label heroHeadline = new Label(item.getDescription() != null ? item.getDescription() : "No Description");
        heroHeadline.getStyleClass().add("hero-headline");
        heroHeadline.setWrapText(true);

        String balanceStr = String.format("Current Balance: %.0f %s", item.getCurrentBalance(),
                item.getUom() != null ? item.getUom() : "");
        Label heroCopy = new Label(balanceStr);
        heroCopy.getStyleClass().add("hero-copy");
        hero.getChildren().addAll(heroTag, heroHeadline, heroCopy);

        // Panel Card for Inputs
        VBox card = new VBox(12);
        card.getStyleClass().add("panel-card");

        Label qtyLabel = new Label("QUANTITY TO ADD");
        qtyLabel.getStyleClass().add("field-caption");

        // Stepper Layout
        HBox stepper = new HBox(8);
        stepper.setAlignment(Pos.CENTER);

        Button minus = new Button("-");
        minus.getStyleClass().add("stock-step-button");
        minus.setStyle("-fx-font-size: 18px; -fx-background-radius: 8; -fx-min-width: 44; -fx-min-height: 38;");

        TextField qtyField = new TextField("1");
        qtyField.getStyleClass().add("stock-qty");
        qtyField.setStyle(
                "-fx-background-color: #f8faff; -fx-text-fill: #1A2560; -fx-font-size: 18px; -fx-font-weight: bold; -fx-alignment: center; -fx-background-radius: 8; -fx-min-height: 38; -fx-pref-width: 100; -fx-border-color: #dfe5fb; -fx-border-radius: 8;");

        Button plus = new Button("+");
        plus.getStyleClass().add("stock-step-button");
        plus.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-min-width: 44; -fx-min-height: 38;");

        stepper.getChildren().addAll(minus, qtyField, plus);

        card.getChildren().addAll(qtyLabel, stepper);

        // --- FOOTER BUTTONS ---
        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("btn-secondary");

        Button save = new Button("Save");
        save.getStyleClass().add("btn-add-material");

        buttons.getChildren().addAll(cancel, save);

        body.getChildren().addAll(hero, card, buttons);
        root.getChildren().addAll(header, body);

        minus.setOnAction(e -> {
            try {
                int v = Integer.parseInt(qtyField.getText().trim());
                qtyField.setText(String.valueOf(Math.max(0, v - 1)));
            } catch (Exception ex) {
                qtyField.setText("0");
            }
        });
        plus.setOnAction(e -> {
            try {
                int v = Integer.parseInt(qtyField.getText().trim());
                qtyField.setText(String.valueOf(v + 1));
            } catch (Exception ex) {
                qtyField.setText("1");
            }
        });

        cancel.setOnAction(e -> dialog.close());
        save.setOnAction(e -> {
            try {
                String tab = item.getSheetTabName() == null || item.getSheetTabName().isBlank() ? TAB_NAME
                        : item.getSheetTabName();
                String qty = qtyField.getText().trim();
                LocalDate date = LocalDate.now();
                if (qty.isEmpty())
                    return;

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
                task.setOnSucceeded(ev -> {
                    dialog.close();
                    loadMaterials();
                });
                task.setOnFailed(ev -> {
                    dialog.close();
                });
                new Thread(task).start();
            } catch (Exception ex) {
                dialog.close();
            }
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

        VBox root = new VBox(0);
        root.getStyleClass().add("dialog-shell");
        root.setPrefWidth(420);

        // --- HEADER ---
        VBox header = new VBox(4);
        header.getStyleClass().addAll("dialog-header", "dialog-header-alt");

        HBox headerRow = new HBox(10);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        VBox headerTitles = new VBox(4);
        HBox.setHgrow(headerTitles, Priority.ALWAYS);
        Label eyebrow = new Label("STOCK OUT RECORDING");
        eyebrow.getStyleClass().add("dialog-eyebrow");
        Label title = new Label("STOCK OUT RECORD");
        title.getStyleClass().add("dialog-title");
        Label subtitle = new Label("Record daily items issued to production.");
        subtitle.getStyleClass().add("dialog-subtitle");
        headerTitles.getChildren().addAll(eyebrow, title, subtitle);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: rgba(255,255,255,0.7); -fx-font-size: 14px; -fx-cursor: hand; -fx-border-width: 0; -fx-padding: 0;");
        closeBtn.setOnAction(e -> dialog.close());

        headerRow.getChildren().addAll(headerTitles, closeBtn);
        header.getChildren().add(headerRow);

        // --- BODY ---
        VBox body = new VBox(14);
        body.getStyleClass().add("dialog-body");

        // Context Panel (Hero Panel)
        VBox hero = new VBox(6);
        hero.getStyleClass().add("hero-panel");
        Label heroTag = new Label(item.getCodeNo() != null ? item.getCodeNo().toUpperCase() : "MATERIAL");
        heroTag.getStyleClass().add("hero-tag");
        Label heroHeadline = new Label(item.getDescription() != null ? item.getDescription() : "No Description");
        heroHeadline.getStyleClass().add("hero-headline");
        heroHeadline.setWrapText(true);

        String balanceStr = String.format("Current Balance: %.0f %s", item.getCurrentBalance(),
                item.getUom() != null ? item.getUom() : "");
        Label heroCopy = new Label(balanceStr);
        heroCopy.getStyleClass().add("hero-copy");
        hero.getChildren().addAll(heroTag, heroHeadline, heroCopy);

        // Panel Card for Inputs
        VBox card = new VBox(12);
        card.getStyleClass().add("panel-card");

        Label qtyLabel = new Label("QUANTITY TO DISBURSE");
        qtyLabel.getStyleClass().add("field-caption");

        // Stepper Layout
        HBox stepper = new HBox(8);
        stepper.setAlignment(Pos.CENTER);

        Button minus = new Button("-");
        minus.getStyleClass().add("stock-step-button");
        minus.setStyle("-fx-font-size: 18px; -fx-background-radius: 8; -fx-min-width: 44; -fx-min-height: 38;");

        TextField qtyField = new TextField("1");
        qtyField.getStyleClass().add("stock-qty");
        qtyField.setStyle(
                "-fx-background-color: #f8faff; -fx-text-fill: #1A2560; -fx-font-size: 18px; -fx-font-weight: bold; -fx-alignment: center; -fx-background-radius: 8; -fx-min-height: 38; -fx-pref-width: 100; -fx-border-color: #dfe5fb; -fx-border-radius: 8;");

        Button plus = new Button("+");
        plus.getStyleClass().add("stock-step-button");
        plus.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-background-radius: 8; -fx-min-width: 44; -fx-min-height: 38;");

        stepper.getChildren().addAll(minus, qtyField, plus);

        card.getChildren().addAll(qtyLabel, stepper);

        // --- FOOTER BUTTONS ---
        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("btn-secondary");

        Button save = new Button("Save");
        save.getStyleClass().add("btn-add-material");

        buttons.getChildren().addAll(cancel, save);

        body.getChildren().addAll(hero, card, buttons);
        root.getChildren().addAll(header, body);

        minus.setOnAction(e -> {
            try {
                int v = Integer.parseInt(qtyField.getText().trim());
                qtyField.setText(String.valueOf(Math.max(0, v - 1)));
            } catch (Exception ex) {
                qtyField.setText("0");
            }
        });
        plus.setOnAction(e -> {
            try {
                int v = Integer.parseInt(qtyField.getText().trim());
                qtyField.setText(String.valueOf(v + 1));
            } catch (Exception ex) {
                qtyField.setText("1");
            }
        });

        cancel.setOnAction(e -> dialog.close());
        save.setOnAction(e -> {
            try {
                String qty = qtyField.getText().trim();
                LocalDate date = LocalDate.now();
                if (qty.isEmpty())
                    return;
                int day = date.getDayOfMonth();
                int colNumber = 12 + (day - 1);
                String colLetter = colNumberToName(colNumber);
                String tab = item.getSheetTabName() == null || item.getSheetTabName().isBlank() ? TAB_NAME
                        : item.getSheetTabName();
                String range = tab + "!" + colLetter + item.getSheetRowNumber();
                Task<Void> task = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        GoogleSheetsService service = new GoogleSheetsService();
                        service.writeCell(range, Double.parseDouble(qty));
                        return null;
                    }
                };
                task.setOnSucceeded(ev -> {
                    dialog.close();
                    loadMaterials();
                });
                task.setOnFailed(ev -> {
                    dialog.close();
                });
                new Thread(task).start();
            } catch (Exception ex) {
                dialog.close();
            }
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

            Button monthOutButton = buildActionButton("Monthly Daily Out Report",
                    "View daily issued quantities per day of the month");
            monthOutButton.setOnAction(e -> {
                moreMenu.hide();
                if (currentItem != null) {
                    openMonthlyOutDialog(currentItem);
                }
            });

            Button editButton = buildActionButton("Edit Material Details",
                    "Update code, description, UOM, price and stock figures");
            editButton.setOnAction(e -> {
                moreMenu.hide();
                if (currentItem != null) {
                    openEditMaterialDialog(currentItem);
                }
            });

            actionPanel.getChildren().addAll(monthOutButton, editButton);
            CustomMenuItem actionItem = new CustomMenuItem(actionPanel, false);
            actionItem.setHideOnClick(false);
            moreMenu.getItems().add(actionItem);

            titleRow.getStyleClass().add("material-title-row");
            Button stockInBtn = new Button("Stock In +");
            stockInBtn.getStyleClass().add("stock-btn");
            stockInBtn.setOnAction(e -> {
                if (currentItem != null)
                    showStockInDialog(currentItem);
            });
            Button stockOutBtn = new Button("Stock Out -");
            stockOutBtn.getStyleClass().add("stock-btn");
            stockOutBtn.setOnAction(e -> {
                if (currentItem != null)
                    showStockOutDialog(currentItem);
            });
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
            unitPriceValue.setText(item.getUnitPrice() == SheetMapper.PRICE_NA
                    ? "N/A" : String.format("\u20B1 %.2f", item.getUnitPrice()));

            String range = reportRangeCombo.getSelectionModel().getSelectedItem();
            double rangeIssued = computeRangeIssued(item, range);
            double rangeValue = item.getUnitPrice() == SheetMapper.PRICE_NA ? 0 : item.getUnitPrice() * rangeIssued;

            String rangeLabel = switch (range == null ? "Monthly" : range) {
                case "Weekly"    -> "THIS WEEK VALUE";
                case "Monthly"   -> "THIS MONTH VALUE";
                case "Quarterly" -> "THIS QUARTER VALUE";
                case "Yearly"    -> "THIS YEAR VALUE";
                default          -> "VALUE";
            };
            totalPriceLabel.setText(rangeLabel);
            totalPriceValue.setText(item.getUnitPrice() == SheetMapper.PRICE_NA
                    ? "N/A" : String.format("\u20B1 %.2f", rangeValue));

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
            row.getChildren().add(textBlock);
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
