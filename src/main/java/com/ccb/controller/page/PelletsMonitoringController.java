package com.ccb.controller.page;

import com.ccb.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.net.URL;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;

public class PelletsMonitoringController implements Initializable {

    private static final int ROWS_PER_PAGE = 8;
    private static final String MONTH_ALL = "ALL";

    @FXML private TextField searchField;
    @FXML private MenuButton monthFilterButton;
    @FXML private Button refreshButton;
    @FXML private VBox loadingState;
    @FXML private VBox errorState;
    @FXML private Label errorMessage;
    @FXML private Button retryButton;
    @FXML private VBox emptyState;
    @FXML private VBox dataState;

    // View Components Built Once
    private final VBox pageContent = new VBox(14);
    private final HBox statCardsHost = new HBox(8);
    private final GridPane chartCardsHost = new GridPane();
    private final GridPane sackCardsGrid = new GridPane();
    
    // Zone 4 Table Components
    private final VBox tableSectionCard = new VBox(10);
    private final VBox tableRowsHost = new VBox(0);
    private final HBox paginationHost = new HBox(10);
    private final Label paginationSummaryLabel = new Label();
    private final HBox pageDotsHost = new HBox(8);
    private final List<HBox> tableRows = new ArrayList<>(ROWS_PER_PAGE);

    // Data Storage
    private final List<PelletsRecord> allRecords = new ArrayList<>();
    private final List<PelletsRecord> filteredRecords = new ArrayList<>();
    private final Set<String> availableMonths = new LinkedHashSet<>();
    private List<String> completedSackGroups = new ArrayList<>();
    private String selectedMonthKey = MONTH_ALL;
    private int currentPage = 0;
    private Runnable refreshCallback;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        buildPageShell();
        
        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldValue, newValue) -> {
                currentPage = 0;
                render();
            });
        }
        if (retryButton != null) {
            retryButton.setOnAction(e -> loadData());
        }
        if (refreshButton != null) {
            refreshButton.setOnAction(e -> loadData());
        }
        
        loadData();
    }

    public void setRefreshCallback(Runnable refreshCallback) {
        this.refreshCallback = refreshCallback;
    }

    public void refresh() {
        loadData();
    }

    /**
     * Loads the sheet records asynchronously in a background thread.
     */
    private void loadData() {
        showLoading();
        Task<List<PelletsRecord>> task = new Task<>() {
            private List<String> completedSacks = new ArrayList<>();

            @Override
            protected List<PelletsRecord> call() throws Exception {
                PelletsSheetService service = new PelletsSheetService();
                List<PelletsRecord> records = service.readRecords();
                completedSacks = service.getCompletedSackGroups();
                return records;
            }

            @Override
            protected void succeeded() {
                completedSackGroups = completedSacks;
                allRecords.clear();
                allRecords.addAll(getValue() == null ? List.of() : getValue());
                currentPage = 0;
                updateMonthOptions();
                render();
            }

            @Override
            protected void failed() {
                showError(getException());
            }
        };
        
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private void showLoading() {
        loadingState.setVisible(true);
        loadingState.setManaged(true);
        errorState.setVisible(false);
        errorState.setManaged(false);
        emptyState.setVisible(false);
        emptyState.setManaged(false);
        dataState.setVisible(false);
        dataState.setManaged(false);
    }

    private void showError(Throwable ex) {
        ex.printStackTrace();
        errorMessage.setText("Error loading Pellets data: " + ex.getMessage());
        loadingState.setVisible(false);
        loadingState.setManaged(false);
        errorState.setVisible(true);
        errorState.setManaged(true);
        emptyState.setVisible(false);
        emptyState.setManaged(false);
        dataState.setVisible(false);
        dataState.setManaged(false);
    }

    private void showEmpty() {
        loadingState.setVisible(false);
        loadingState.setManaged(false);
        errorState.setVisible(false);
        errorState.setManaged(false);
        emptyState.setVisible(true);
        emptyState.setManaged(true);
        dataState.setVisible(false);
        dataState.setManaged(false);
    }

    private void showData() {
        loadingState.setVisible(false);
        loadingState.setManaged(false);
        errorState.setVisible(false);
        errorState.setManaged(false);
        emptyState.setVisible(false);
        emptyState.setManaged(false);
        dataState.setVisible(true);
        dataState.setManaged(true);
    }

    private void updateMonthOptions() {
        availableMonths.clear();
        for (PelletsRecord record : allRecords) {
            if (record.getParsedDate() != null) {
                availableMonths.add(record.getMonthKey());
            }
        }

        if (monthFilterButton == null) {
            return;
        }

        monthFilterButton.getItems().clear();
        monthFilterButton.getItems().add(buildMonthItem("All Months", MONTH_ALL));

        List<String> sortedMonths = new ArrayList<>(availableMonths);
        sortedMonths.sort(Comparator.reverseOrder());
        for (String monthKey : sortedMonths) {
            monthFilterButton.getItems().add(buildMonthItem(formatMonthKey(monthKey), monthKey));
        }

        String currentMonthKey = LocalDate.now().getYear() + "-" + String.format("%02d", LocalDate.now().getMonthValue());
        if (selectedMonthKey == null || selectedMonthKey.isBlank() || 
            (!MONTH_ALL.equals(selectedMonthKey) && !availableMonths.contains(selectedMonthKey))) {
            selectedMonthKey = availableMonths.contains(currentMonthKey) ? currentMonthKey : MONTH_ALL;
        }
        monthFilterButton.setText(MONTH_ALL.equals(selectedMonthKey) ? "All Months" : formatMonthKey(selectedMonthKey));
    }

    private MenuItem buildMonthItem(String label, String key) {
        MenuItem item = new MenuItem(label);
        item.setOnAction(e -> {
            selectedMonthKey = key;
            currentPage = 0;
            monthFilterButton.setText(MONTH_ALL.equals(key) ? "All Months" : formatMonthKey(key));
            render();
        });
        return item;
    }

    private String formatMonthKey(String monthKey) {
        if (monthKey == null || monthKey.isBlank() || MONTH_ALL.equals(monthKey)) {
            return "All Months";
        }
        try {
            YearMonth ym = YearMonth.parse(monthKey);
            return ym.atDay(1).getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + ym.getYear();
        } catch (Exception ex) {
            return monthKey;
        }
    }

    /**
     * Filters records based on search query and month filter, 
     * computes the PelletsSummary, and updates the layout.
     */
    private void render() {
        // Filter records
        filteredRecords.clear();
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);

        for (PelletsRecord r : allRecords) {
            // Month filter
            if (!MONTH_ALL.equals(selectedMonthKey)) {
                if (r.getParsedDate() == null || !r.getMonthKey().equals(selectedMonthKey)) {
                    continue;
                }
            }

            // Search filter (Match date, brand, kgs, timeSlot, or sackGroup)
            if (!query.isEmpty()) {
                String dateStr = r.getDate().toLowerCase(Locale.ROOT);
                String brandStr = r.getBrand().toLowerCase(Locale.ROOT);
                String kgsStr = r.getKgs().toLowerCase(Locale.ROOT);
                String tsStr = r.getTimeSlot().toLowerCase(Locale.ROOT);
                String sgStr = r.getSackGroup().toLowerCase(Locale.ROOT);

                if (!dateStr.contains(query) && !brandStr.contains(query) && 
                    !kgsStr.contains(query) && !tsStr.contains(query) && !sgStr.contains(query)) {
                    continue;
                }
            }

            filteredRecords.add(r);
        }

        if (filteredRecords.isEmpty()) {
            showEmpty();
            return;
        }

        // Run aggregation service
        PelletsSummary summary = PelletsDataService.compute(filteredRecords, completedSackGroups);

        // Update Zone 1: Stat Cards
        renderStatCards(summary);

        // Update Zone 2: Chart Cards
        renderChartCards(summary);

        // Update Zone 3: Sack Cards
        renderSackCards(summary);

        // Update Zone 4: Table Rows & Pagination
        renderTable();

        showData();
    }

    /**
     * Pre-builds the structure of the dashboard shell once on initialization.
     */
    private void buildPageShell() {
        if (dataState == null) {
            return;
        }

        pageContent.setStyle("-fx-background-color: #F4F5F7;");

        // Zone 1 Host
        statCardsHost.setAlignment(Pos.TOP_LEFT);
        
        // Zone 2 Host
        chartCardsHost.setHgap(10);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(56.5);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(43.5);
        chartCardsHost.getColumnConstraints().addAll(col1, col2);

        // Zone 3 Host
        sackCardsGrid.setHgap(8);
        sackCardsGrid.setVgap(8);
        for (int i = 0; i < 3; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / 3.0);
            sackCardsGrid.getColumnConstraints().add(cc);
        }

        // Zone 4 Table Section Card Setup
        tableSectionCard.setStyle("-fx-background-color: " + StyleConstants.WHITE_HEX + "; -fx-border-color: " + StyleConstants.BORDER_HEX + "; -fx-border-width: 0.5; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 14;");
        
        Label tblTitle = new Label("Repair Log");
        tblTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.TEXT_DARK_HEX + ";");
        
        Label tblSubtitle = new Label("All time slot entries from Google Sheets — 8 rows per page");
        tblSubtitle.setStyle("-fx-font-size: 10px; -fx-text-fill: " + StyleConstants.TEXT_MUTED_HEX + ";");
        
        VBox tableHeaderBox = new VBox(4);
        tableHeaderBox.getChildren().addAll(tblTitle, tblSubtitle);

        // Build pre-built table rows for LAZY RENDER
        tableRowsHost.setSpacing(0);
        for (int i = 0; i < ROWS_PER_PAGE; i++) {
            HBox rowNode = prebuildTableRowNode();
            tableRows.add(rowNode);
            tableRowsHost.getChildren().add(rowNode);
        }

        // Build pagination elements
        paginationHost.setAlignment(Pos.CENTER_LEFT);
        paginationSummaryLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: " + StyleConstants.TEXT_MUTED_HEX + ";");
        
        Region paginationSpacer = new Region();
        HBox.setHgrow(paginationSpacer, Priority.ALWAYS);
        
        HBox arrowControls = buildArrowControls();
        
        paginationHost.getChildren().addAll(paginationSummaryLabel, paginationSpacer, pageDotsHost, arrowControls);

        tableSectionCard.getChildren().addAll(
                tableHeaderBox,
                buildTableHeader(),
                tableRowsHost,
                new Separator(),
                paginationHost
        );

        // Compile all sections in order
        pageContent.getChildren().addAll(
                buildSectionLabel("AT A GLANCE — OVERALL PELLETS OUTPUT"),
                statCardsHost,
                buildSectionLabel("GOOD VS REJECTED — BY BRAND"),
                chartCardsHost,
                buildSectionLabel("PER SACK REPORT — TOTALS"),
                sackCardsGrid,
                buildSectionLabel("FULL DETAIL LOG — EVERY TIME SLOT ENTRY"),
                tableSectionCard
        );

        dataState.getChildren().setAll(pageContent);
    }

    /**
     * Pre-builds a blank table row node to avoid recreation overhead.
     */
    private HBox prebuildTableRowNode() {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPrefHeight(36);
        row.setMinHeight(36);
        row.setMaxHeight(36);
        row.setStyle("-fx-background-color: " + StyleConstants.WHITE_HEX + "; -fx-border-color: transparent transparent " + StyleConstants.BORDER_HEX + " transparent; -fx-border-width: 0 0 1 0; -fx-padding: 0 10;");

        // Cell widths matching: Date (94), TimeSlot (100), Brand (120), BagSize (80), Good (70), Rejected (80), Reject Rate (110), Sack (100)
        Label cellDate = buildCell("—", "");
        cellDate.setPrefWidth(94); cellDate.setMinWidth(94);
        
        Label cellTimeSlot = buildCell("—", "");
        cellTimeSlot.setPrefWidth(100); cellTimeSlot.setMinWidth(100);
        
        Label cellBrand = buildCell("—", "");
        cellBrand.setPrefWidth(120); cellBrand.setMinWidth(120);
        
        Label cellBagSize = buildCell("—", "");
        cellBagSize.setPrefWidth(80); cellBagSize.setMinWidth(80);
        
        Label cellGood = buildCell("—", "");
        cellGood.setPrefWidth(70); cellGood.setMinWidth(70);
        
        Label cellRejected = buildCell("—", "");
        cellRejected.setPrefWidth(80); cellRejected.setMinWidth(80);

        // Reject Rate cell contains rate text + mini ratio bar
        VBox cellRateBox = new VBox(2);
        cellRateBox.setPrefWidth(110); cellRateBox.setMinWidth(110);
        cellRateBox.setAlignment(Pos.CENTER_LEFT);
        
        Label rateText = new Label("0.0%");
        
        StackPane barTrack = new StackPane();
        barTrack.setAlignment(Pos.CENTER_LEFT);
        barTrack.setStyle("-fx-background-color: " + StyleConstants.SURFACE_HEX + "; -fx-background-radius: 2px; -fx-pref-width: 60px; -fx-min-width: 60px; -fx-max-width: 60px; -fx-pref-height: 4px; -fx-min-height: 4px; -fx-max-height: 4px;");
        
        Region barFill = new Region();
        barFill.setStyle("-fx-background-color: " + StyleConstants.GREEN_HEX + "; -fx-background-radius: 2px; -fx-pref-height: 4px; -fx-min-height: 4px; -fx-max-height: 4px;");
        barFill.setPrefWidth(0); barFill.setMinWidth(0); barFill.setMaxWidth(0);
        barTrack.getChildren().add(barFill);
        
        cellRateBox.getChildren().addAll(rateText, barTrack);

        Label cellSack = buildCell("—", "");
        cellSack.setPrefWidth(100); cellSack.setMinWidth(100);

        row.getChildren().addAll(cellDate, cellTimeSlot, cellBrand, cellBagSize, cellGood, cellRejected, cellRateBox, cellSack);

        for (Node child : row.getChildren()) {
            HBox.setHgrow(child, Priority.ALWAYS);
        }

        // Add hover effects dynamically
        row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: " + StyleConstants.SURFACE_HEX + "; -fx-border-color: transparent transparent " + StyleConstants.BORDER_HEX + " transparent; -fx-border-width: 0 0 1 0; -fx-padding: 0 10; -fx-min-height: 36; -fx-pref-height: 36; -fx-max-height: 36; -fx-alignment: center-left;"));
        row.setOnMouseExited(e -> row.setStyle("-fx-background-color: " + StyleConstants.WHITE_HEX + "; -fx-border-color: transparent transparent " + StyleConstants.BORDER_HEX + " transparent; -fx-border-width: 0 0 1 0; -fx-padding: 0 10; -fx-min-height: 36; -fx-pref-height: 36; -fx-max-height: 36; -fx-alignment: center-left;"));

        return row;
    }

    private HBox buildTableHeader() {
        HBox header = new HBox(8);
        header.setStyle("-fx-background-color: " + StyleConstants.SURFACE_HEX + "; -fx-border-color: " + StyleConstants.BORDER_HEX + "; -fx-border-width: 1; -fx-border-radius: 10px; -fx-background-radius: 10px; -fx-min-height: 32px; -fx-pref-height: 32px; -fx-padding: 0 10; -fx-alignment: center-left;");
        
        header.getChildren().addAll(
                headerCell("DATE", 94),
                headerCell("TIME SLOT", 100),
                headerCell("BRAND", 120),
                headerCell("BAG SIZE", 80),
                headerCell("GOOD", 70),
                headerCell("REJECTED", 80),
                headerCell("REJECT RATE", 110),
                headerCell("SACK REPORT", 100)
        );
        for (Node node : header.getChildren()) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }
        return header;
    }

    private Label headerCell(String text, double width) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.TEXT_MUTED_HEX + "; -fx-letter-spacing: 1px;");
        label.setMinWidth(width);
        label.setPrefWidth(width);
        return label;
    }

    private HBox buildArrowControls() {
        HBox box = new HBox(6);
        box.setAlignment(Pos.CENTER);
        
        Button prev = paginationArrowButton("‹");
        Button next = paginationArrowButton("›");
        
        prev.setOnAction(e -> {
            if (currentPage > 0) {
                currentPage--;
                renderTable();
            }
        });
        next.setOnAction(e -> {
            if ((currentPage + 1) * ROWS_PER_PAGE < filteredRecords.size()) {
                currentPage++;
                renderTable();
            }
        });
        
        box.getChildren().addAll(prev, next);
        return box;
    }

    private Button paginationArrowButton(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-min-width: 26px; -fx-min-height: 26px; -fx-pref-width: 26px; -fx-pref-height: 26px; -fx-background-color: #FFFFFF; -fx-border-color: #E5E7EB; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-border-width: 0.5; -fx-text-fill: #1B2A3B; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 0;");
        
        btn.setOnMouseEntered(e -> {
            if (!btn.isDisable()) {
                btn.setStyle("-fx-min-width: 26px; -fx-min-height: 26px; -fx-pref-width: 26px; -fx-pref-height: 26px; -fx-background-color: #1B2A3B; -fx-border-color: #1B2A3B; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-border-width: 0.5; -fx-text-fill: #FFFFFF; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 0;");
            }
        });
        btn.setOnMouseExited(e -> {
            if (!btn.isDisable()) {
                btn.setStyle("-fx-min-width: 26px; -fx-min-height: 26px; -fx-pref-width: 26px; -fx-pref-height: 26px; -fx-background-color: #FFFFFF; -fx-border-color: #E5E7EB; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-border-width: 0.5; -fx-text-fill: #1B2A3B; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 0;");
            }
        });
        return btn;
    }

    /**
     * Renders/Refreshes Stat Cards in Zone 1.
     */
    private void renderStatCards(PelletsSummary summary) {
        statCardsHost.getChildren().clear();

        // Card 1
        VBox card1 = buildStatCard(
                "TOTAL SHOT BLASTING GOOD",
                String.format("%,d", summary.getTotalGood()),
                StyleConstants.BLUE,
                "sacks passed shot blasting",
                "Across all sack reports",
                "blue"
        );
        
        // Card 2
        VBox card2 = buildStatCard(
                "TOTAL SHOT BLASTING REJECT",
                String.format("%,d", summary.getTotalReject()),
                StyleConstants.RED,
                "sacks rejected in shot blasting",
                "High reject volume",
                "red"
        );

        // Card 3
        Color rateColor = rejectRateColor(summary.getOverallRejectRate());
        String badgeText;
        String badgeStyle;
        if (summary.getOverallRejectRate() >= 75.0) {
            badgeText = "Needs attention";
            badgeStyle = "red";
        } else if (summary.getOverallRejectRate() >= 65.0) {
            badgeText = "Monitor closely";
            badgeStyle = "yellow";
        } else {
            badgeText = "On track";
            badgeStyle = "green";
        }
        
        VBox card3 = buildStatCard(
                "OVERALL REJECT RATE",
                String.format("%.1f%%", summary.getOverallRejectRate()),
                rateColor,
                "of all sacks are rejected",
                badgeText,
                badgeStyle
        );

        // Card 4
        VBox card4 = buildStatCard(
                "SACK REPORT BATCHES",
                String.format("%,d", summary.getTotalSackGroups()),
                StyleConstants.PURPLE,
                "total sack output reports",
                summary.getDateRange(),
                "yellow"
        );

        statCardsHost.getChildren().addAll(card1, card2, card3, card4);
        for (Node node : statCardsHost.getChildren()) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }
    }

    /**
     * Renders/Refreshes Brand & Size Charts in Zone 2.
     */
    private void renderChartCards(PelletsSummary summary) {
        chartCardsHost.getChildren().clear();

        // --- Card 1: Performance Per Brand ---
        VBox brandCard = new VBox(10);
        brandCard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E5E7EB; -fx-border-width: 0.5; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 13 14;");
        
        // Header
        HBox brandHeader = new HBox(8);
        brandHeader.setAlignment(Pos.TOP_LEFT);
        Region brandStripe = new Region();
        brandStripe.setStyle("-fx-background-color: " + StyleConstants.BLUE_HEX + "; -fx-pref-width: 3; -fx-min-width: 3; -fx-max-width: 3; -fx-pref-height: 24;");
        
        VBox brandTitleBox = new VBox(2);
        Label brandTitle = new Label("Shot blasting performance per brand");
        brandTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.TEXT_DARK_HEX + ";");
        Label brandDesc = new Label("Blue = good sacks · Red = rejected. Sorted by highest reject count.");
        brandDesc.setStyle("-fx-font-size: 10px; -fx-text-fill: " + StyleConstants.TEXT_MUTED_HEX + ";");
        brandTitleBox.getChildren().addAll(brandTitle, brandDesc);
        brandHeader.getChildren().addAll(brandStripe, brandTitleBox);

        // Chart rows
        VBox brandList = new VBox(8);
        brandList.setStyle("-fx-padding: 10 0 0 0;");
        int maxReject = 1;
        for (int[] gb : summary.getByBrand().values()) {
            maxReject = Math.max(maxReject, gb[0]);
            maxReject = Math.max(maxReject, gb[1]);
        }
        
        for (Map.Entry<String, int[]> entry : summary.getByBrand().entrySet()) {
            HBox barRow = buildDualHBar(entry.getKey(), entry.getValue()[0], entry.getValue()[1], maxReject);
            brandList.getChildren().add(barRow);
        }

        brandCard.getChildren().addAll(brandHeader, brandList);

        // --- Card 2: Output by Bag Size & Variant CNF ---
        VBox sizeCard = new VBox(10);
        sizeCard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E5E7EB; -fx-border-width: 0.5; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 13 14;");

        // Section A Header
        HBox sectionAHeader = new HBox(8);
        sectionAHeader.setAlignment(Pos.TOP_LEFT);
        Region sectionAStripe = new Region();
        sectionAStripe.setStyle("-fx-background-color: " + StyleConstants.YELLOW_HEX + "; -fx-pref-width: 3; -fx-min-width: 3; -fx-max-width: 3; -fx-pref-height: 24;");
        
        VBox sectionATitleBox = new VBox(2);
        Label sectionATitle = new Label("Output by bag size");
        sectionATitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.TEXT_DARK_HEX + ";");
        Label sectionADesc = new Label("Good sacks per bag size across all batches");
        sectionADesc.setStyle("-fx-font-size: 10px; -fx-text-fill: " + StyleConstants.TEXT_MUTED_HEX + ";");
        sectionATitleBox.getChildren().addAll(sectionATitle, sectionADesc);
        sectionAHeader.getChildren().addAll(sectionAStripe, sectionATitleBox);

        // Section A Bars
        VBox sizeList = new VBox(8);
        sizeList.setStyle("-fx-padding: 8 0 0 0;");
        int maxGoodSize = 1;
        for (int val : summary.getByKgs().values()) {
            maxGoodSize = Math.max(maxGoodSize, val);
        }
        
        sizeList.getChildren().addAll(
                buildSingleHBar("11 kg", summary.getByKgs().getOrDefault("11kg", 0), maxGoodSize, StyleConstants.BLUE),
                buildSingleHBar("22 kg", summary.getByKgs().getOrDefault("22kg", 0), maxGoodSize, StyleConstants.YELLOW),
                buildSingleHBar("50 kg", summary.getByKgs().getOrDefault("50kg", 0), maxGoodSize, StyleConstants.RED)
        );

        // Divider
        Region sizeDivider = new Region();
        sizeDivider.setStyle("-fx-background-color: " + StyleConstants.BORDER_HEX + "; -fx-pref-height: 0.5; -fx-min-height: 0.5; -fx-max-height: 0.5; -fx-margin: 4 0;");

        // Section B Header
        HBox sectionBHeader = new HBox(8);
        sectionBHeader.setAlignment(Pos.TOP_LEFT);
        Region sectionBStripe = new Region();
        sectionBStripe.setStyle("-fx-background-color: " + StyleConstants.PURPLE_HEX + "; -fx-pref-width: 3; -fx-min-width: 3; -fx-max-width: 3; -fx-pref-height: 24;");
        
        VBox sectionBTitleBox = new VBox(2);
        Label sectionBTitle = new Label("Equi Gaz CNF vs regular Equi Gaz");
        sectionBTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.TEXT_DARK_HEX + ";");
        Label sectionBDesc = new Label("CNF variant tracked separately");
        sectionBDesc.setStyle("-fx-font-size: 10px; -fx-text-fill: " + StyleConstants.TEXT_MUTED_HEX + ";");
        sectionBTitleBox.getChildren().addAll(sectionBTitle, sectionBDesc);
        sectionBHeader.getChildren().addAll(sectionBStripe, sectionBTitleBox);

        // Section B Bars
        VBox cnfVsRegList = new VBox(8);
        cnfVsRegList.setStyle("-fx-padding: 8 0 0 0;");
        
        int regGood = 0;
        int cnfGood = 0;
        if (summary.getByBrand().containsKey("Equi Gaz")) {
            regGood = summary.getByBrand().get("Equi Gaz")[0];
        }
        if (summary.getByBrand().containsKey("Equi Gaz CNF")) {
            cnfGood = summary.getByBrand().get("Equi Gaz CNF")[0];
        }
        int maxCNFGood = Math.max(1, Math.max(regGood, cnfGood));
        
        cnfVsRegList.getChildren().addAll(
                buildSingleHBar("Equi Gaz", regGood, maxCNFGood, StyleConstants.BLUE),
                buildSingleHBar("Equi Gaz CNF", cnfGood, maxCNFGood, StyleConstants.PURPLE)
        );

        sizeCard.getChildren().addAll(sectionAHeader, sizeList, sizeDivider, sectionBHeader, cnfVsRegList);

        // Add to GridPane row
        chartCardsHost.add(brandCard, 0, 0);
        chartCardsHost.add(sizeCard, 1, 0);
    }

    /**
     * Renders/Refreshes Sack Report cards grid in Zone 3.
     */
    private void renderSackCards(PelletsSummary summary) {
        sackCardsGrid.getChildren().clear();
        int idx = 0;
        for (SackGroupSummary s : summary.getBySackGroup()) {
            int r = idx / 3;
            int c = idx % 3;
            VBox card = buildSackCard(s);
            sackCardsGrid.add(card, c, r);
            idx++;
        }
    }

    /**
     * Renders the visible logs on the page using lazy render in-place updates.
     */
    private void renderTable() {
        int totalEntries = filteredRecords.size();
        int totalPages = (int) Math.ceil((double) totalEntries / ROWS_PER_PAGE);
        if (totalPages <= 0) totalPages = 1;

        if (currentPage >= totalPages) {
            currentPage = totalPages - 1;
        }
        if (currentPage < 0) {
            currentPage = 0;
        }

        int startIdx = currentPage * ROWS_PER_PAGE;
        int endIdx = Math.min(startIdx + ROWS_PER_PAGE, totalEntries);

        // Update prebuilt row cells in-place
        for (int i = 0; i < ROWS_PER_PAGE; i++) {
            HBox rowNode = tableRows.get(i);
            int recordIdx = startIdx + i;

            if (recordIdx < totalEntries) {
                rowNode.setVisible(true);
                rowNode.setManaged(true);
                PelletsRecord record = filteredRecords.get(recordIdx);

                // Date
                Label lblDate = (Label) rowNode.getChildren().get(0);
                lblDate.setText(record.getDate().isEmpty() ? "—" : record.getDate());
                lblDate.setStyle(record.getDate().isEmpty() ? 
                        "-fx-font-size: 11px; -fx-text-fill: " + StyleConstants.TEXT_HINT_HEX + ";" :
                        "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.TEXT_DARK_HEX + ";");

                // Time Slot
                Label lblTime = (Label) rowNode.getChildren().get(1);
                lblTime.setText(record.getTimeSlot().isEmpty() ? "—" : record.getTimeSlot());
                if (record.getTimeSlot().isEmpty()) {
                    lblTime.setStyle("-fx-font-size: 11px; -fx-text-fill: " + StyleConstants.TEXT_HINT_HEX + ";");
                } else {
                    lblTime.setStyle("-fx-background-color: " + StyleConstants.BADGE_BLUE_BG + "; -fx-text-fill: " + StyleConstants.BADGE_BLUE_TXT + "; -fx-background-radius: 8; -fx-padding: 2 7; -fx-font-size: 9px; -fx-font-weight: bold;");
                }

                // Brand
                Label lblBrand = (Label) rowNode.getChildren().get(2);
                lblBrand.setText(record.getBrand().isEmpty() ? "—" : record.getBrand());
                lblBrand.setStyle("-fx-background-color: " + StyleConstants.SURFACE_HEX + "; -fx-text-fill: " + StyleConstants.TEXT_MID_HEX + "; -fx-border-color: " + StyleConstants.BORDER_HEX + "; -fx-border-width: 0.5; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 2 7; -fx-font-size: 9px; -fx-font-weight: bold;");

                // Bag Size
                Label lblSize = (Label) rowNode.getChildren().get(3);
                lblSize.setText(record.getKgs());
                lblSize.setStyle("-fx-font-size: 10px; -fx-text-fill: " + StyleConstants.TEXT_MID_HEX + ";");

                // Good
                Label lblGood = (Label) rowNode.getChildren().get(4);
                lblGood.setText(String.format("%,d", record.getShotGood()));
                lblGood.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.GREEN_HEX + ";");

                // Rejected
                Label lblRej = (Label) rowNode.getChildren().get(5);
                lblRej.setText(String.format("%,d", record.getShotReject()));
                lblRej.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.RED_HEX + ";");

                // Reject Rate VBox in-place update
                VBox rateBox = (VBox) rowNode.getChildren().get(6);
                Label rateTxt = (Label) rateBox.getChildren().get(0);
                StackPane barTrack = (StackPane) rateBox.getChildren().get(1);
                Region barFill = (Region) barTrack.getChildren().get(0);

                double rate = record.getRejectRate();
                rateTxt.setText(String.format("%.1f%%", rate));
                Color color = rejectRateColor(rate);
                String hex = "#" + color.toString().substring(2, 8);
                rateTxt.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + hex + ";");
                
                barFill.setStyle("-fx-background-color: " + hex + "; -fx-background-radius: 2; -fx-pref-height: 4px; -fx-min-height: 4px; -fx-max-height: 4px;");
                double fillWidth = 60.0 * (rate / 100.0);
                barFill.setPrefWidth(fillWidth);
                barFill.setMinWidth(fillWidth);
                barFill.setMaxWidth(fillWidth);

                // Sack Report
                Label lblSack = (Label) rowNode.getChildren().get(7);
                lblSack.setText(record.getSackGroup().isEmpty() ? "—" : record.getSackGroup() + " Sack");
                if (record.getSackGroup().isEmpty()) {
                    lblSack.setStyle("-fx-font-size: 11px; -fx-text-fill: " + StyleConstants.TEXT_HINT_HEX + ";");
                } else {
                    lblSack.setStyle("-fx-background-color: " + StyleConstants.BADGE_BLUE_BG + "; -fx-text-fill: " + StyleConstants.BADGE_BLUE_TXT + "; -fx-background-radius: 6; -fx-padding: 2 5; -fx-font-size: 9px; -fx-font-weight: bold;");
                }

                // Left border warning accent if row has rejections
                final boolean isFlagged = record.getShotReject() > 0;
                updateRowStyle(rowNode, isFlagged, false);

                rowNode.setOnMouseEntered(e -> updateRowStyle(rowNode, isFlagged, true));
                rowNode.setOnMouseExited(e -> updateRowStyle(rowNode, isFlagged, false));
            } else {
                rowNode.setVisible(false);
                rowNode.setManaged(false);
            }
        }

        // Update summary label (Showing X–Y of Z entries)
        paginationSummaryLabel.setText(String.format("Showing %d–%d of %d entries", 
                totalEntries == 0 ? 0 : startIdx + 1, endIdx, totalEntries));

        // Update prev/next buttons disabled state
        HBox arrowControls = (HBox) paginationHost.getChildren().get(3);
        Button prevBtn = (Button) arrowControls.getChildren().get(0);
        Button nextBtn = (Button) arrowControls.getChildren().get(1);

        prevBtn.setDisable(currentPage <= 0);
        nextBtn.setDisable(startIdx + ROWS_PER_PAGE >= totalEntries);
        
        updateArrowStyles(prevBtn);
        updateArrowStyles(nextBtn);

        // Render Page Dots
        renderPageDots(totalPages);
    }

    private void updateArrowStyles(Button btn) {
        if (btn.isDisable()) {
            btn.setStyle("-fx-min-width: 26px; -fx-min-height: 26px; -fx-pref-width: 26px; -fx-pref-height: 26px; -fx-background-color: #F4F5F7; -fx-border-color: #E5E7EB; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-border-width: 0.5; -fx-text-fill: " + StyleConstants.TEXT_HINT_HEX + "; -fx-font-weight: bold; -fx-padding: 0;");
        } else {
            btn.setStyle("-fx-min-width: 26px; -fx-min-height: 26px; -fx-pref-width: 26px; -fx-pref-height: 26px; -fx-background-color: #FFFFFF; -fx-border-color: #E5E7EB; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-border-width: 0.5; -fx-text-fill: #1B2A3B; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 0;");
        }
    }

    private void renderPageDots(int totalPages) {
        pageDotsHost.getChildren().clear();
        pageDotsHost.setAlignment(Pos.CENTER);

        for (int i = 0; i < totalPages; i++) {
            final int pageIndex = i;
            Region dot = new Region();
            
            if (i == currentPage) {
                // Active pill style
                dot.setStyle("-fx-background-color: #1B2A3B; -fx-min-width: 16px; -fx-pref-width: 16px; -fx-max-width: 16px; -fx-min-height: 6px; -fx-pref-height: 6px; -fx-max-height: 6px; -fx-background-radius: 3px;");
            } else {
                // Inactive circle style
                dot.setStyle("-fx-background-color: #E5E7EB; -fx-min-width: 6px; -fx-pref-width: 6px; -fx-max-width: 6px; -fx-min-height: 6px; -fx-pref-height: 6px; -fx-max-height: 6px; -fx-background-radius: 999; -fx-cursor: hand;");
                dot.setOnMouseClicked(e -> {
                    currentPage = pageIndex;
                    renderTable();
                });
            }
            
            pageDotsHost.getChildren().add(dot);
        }
    }

    // --- REUSE HELPER BUILDERS ---

    private VBox buildStatCard(String question, String value, Color color, String context, String badgeText, String badgeStyle) {
        VBox card = new VBox(8);
        String hexColor = "#" + color.toString().substring(2, 8);
        card.setStyle("-fx-background-color: " + StyleConstants.WHITE_HEX + "; -fx-background-radius: 12px; -fx-border-radius: 12px; -fx-border-width: 3px 0.5px 0.5px 0.5px; -fx-border-color: " + hexColor + " " + StyleConstants.BORDER_HEX + " " + StyleConstants.BORDER_HEX + " " + StyleConstants.BORDER_HEX + "; -fx-padding: 12px 14px;");

        Label qLbl = new Label(question);
        qLbl.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.TEXT_MUTED_HEX + "; -fx-letter-spacing: 0.5px;");

        Label valLbl = new Label(value);
        valLbl.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: " + hexColor + ";");

        Label ctxLbl = new Label(context);
        ctxLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: " + StyleConstants.TEXT_MID_HEX + ";");

        // Badge pill styling
        String bgStyle;
        String txtStyle;
        if ("green".equals(badgeStyle)) {
            bgStyle = StyleConstants.BADGE_GREEN_BG;
            txtStyle = StyleConstants.BADGE_GREEN_TXT;
        } else if ("yellow".equals(badgeStyle)) {
            bgStyle = StyleConstants.BADGE_YELLOW_BG;
            txtStyle = StyleConstants.BADGE_YELLOW_TXT;
        } else if ("red".equals(badgeStyle)) {
            bgStyle = StyleConstants.BADGE_RED_BG;
            txtStyle = StyleConstants.BADGE_RED_TXT;
        } else if ("purple".equals(badgeStyle)) {
            bgStyle = StyleConstants.BADGE_PURPLE_BG;
            txtStyle = StyleConstants.BADGE_PURPLE_TXT;
        } else {
            bgStyle = StyleConstants.BADGE_BLUE_BG;
            txtStyle = StyleConstants.BADGE_BLUE_TXT;
        }

        Label pill = buildPill(badgeText, bgStyle, txtStyle);
        
        card.getChildren().addAll(qLbl, valLbl, ctxLbl, pill);
        return card;
    }

    private HBox buildSectionLabel(String text) {
        HBox hbox = new HBox(10);
        hbox.setAlignment(Pos.CENTER_LEFT);
        hbox.setStyle("-fx-padding: 10 0 5 0;");
        
        Label label = new Label(text.toUpperCase());
        label.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.TEXT_MUTED_HEX + "; -fx-letter-spacing: 1.2px;");

        Region line = new Region();
        HBox.setHgrow(line, Priority.ALWAYS);
        line.setStyle("-fx-background-color: " + StyleConstants.BORDER_HEX + "; -fx-pref-height: 0.5px; -fx-min-height: 0.5px; -fx-max-height: 0.5px;");

        hbox.getChildren().addAll(label, line);
        return hbox;
    }

    private HBox buildDualHBar(String labelText, int goodVal, int rejectVal, int maxVal) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.TEXT_DARK_HEX + ";");
        lbl.setPrefWidth(75);
        lbl.setMinWidth(75);
        lbl.setMaxWidth(75);

        VBox barStack = new VBox(4);
        HBox.setHgrow(barStack, Priority.ALWAYS);

        double maxValD = Math.max(1, maxVal);
        double goodPct = (double) goodVal / maxValD;
        double rejectPct = (double) rejectVal / maxValD;

        // Bar 1 (good)
        GridPane bar1Container = new GridPane();
        bar1Container.setMaxWidth(Double.MAX_VALUE);
        ColumnConstraints fill1 = new ColumnConstraints();
        fill1.setPercentWidth(Math.max(0.1, goodPct * 100));
        ColumnConstraints empty1 = new ColumnConstraints();
        empty1.setPercentWidth(Math.max(0.1, (1.0 - goodPct) * 100));
        bar1Container.getColumnConstraints().addAll(fill1, empty1);

        Region bar1 = new Region();
        bar1.setStyle("-fx-background-color: rgba(31, 95, 166, 0.6); -fx-background-radius: 3px; -fx-pref-height: 5px; -fx-min-height: 5px; -fx-max-height: 5px;");
        bar1Container.add(bar1, 0, 0);

        // Bar 2 (reject)
        GridPane bar2Container = new GridPane();
        bar2Container.setMaxWidth(Double.MAX_VALUE);
        ColumnConstraints fill2 = new ColumnConstraints();
        fill2.setPercentWidth(Math.max(0.1, rejectPct * 100));
        ColumnConstraints empty2 = new ColumnConstraints();
        empty2.setPercentWidth(Math.max(0.1, (1.0 - rejectPct) * 100));
        bar2Container.getColumnConstraints().addAll(fill2, empty2);

        Region bar2 = new Region();
        bar2.setStyle("-fx-background-color: rgba(192, 57, 43, 0.6); -fx-background-radius: 3px; -fx-pref-height: 5px; -fx-min-height: 5px; -fx-max-height: 5px;");
        bar2Container.add(bar2, 0, 0);

        barStack.getChildren().addAll(bar1Container, bar2Container);

        // Values column: good in BLUE, reject in RED
        VBox valsCol = new VBox(2);
        valsCol.setAlignment(Pos.CENTER_RIGHT);
        valsCol.setPrefWidth(60);
        valsCol.setMinWidth(60);

        Label goodLbl = new Label(String.format("%,d", goodVal));
        goodLbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.BLUE_HEX + ";");

        Label rejectLbl = new Label(String.format("%,d", rejectVal));
        rejectLbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.RED_HEX + ";");

        valsCol.getChildren().addAll(goodLbl, rejectLbl);

        row.getChildren().addAll(lbl, barStack, valsCol);
        return row;
    }

    private HBox buildSingleHBar(String labelText, int val, int maxVal, Color color) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.TEXT_DARK_HEX + ";");
        lbl.setPrefWidth(75);
        lbl.setMinWidth(75);

        GridPane barContainer = new GridPane();
        HBox.setHgrow(barContainer, Priority.ALWAYS);
        barContainer.setMaxWidth(Double.MAX_VALUE);

        double pct = (double) val / Math.max(1, maxVal);
        ColumnConstraints fill = new ColumnConstraints();
        fill.setPercentWidth(Math.max(0.1, pct * 100));
        ColumnConstraints empty = new ColumnConstraints();
        empty.setPercentWidth(Math.max(0.1, (1.0 - pct) * 100));
        barContainer.getColumnConstraints().addAll(fill, empty);

        Region bar = new Region();
        String hexColor = "#" + color.toString().substring(2, 8);
        bar.setStyle("-fx-background-color: " + hexColor + "; -fx-background-radius: 3px; -fx-pref-height: 6px; -fx-min-height: 6px; -fx-max-height: 6px;");
        barContainer.add(bar, 0, 0);

        Label valLbl = new Label(String.format("%,d", val));
        valLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.TEXT_DARK_HEX + ";");
        valLbl.setPrefWidth(55);
        valLbl.setMinWidth(55);
        valLbl.setAlignment(Pos.CENTER_RIGHT);

        row.getChildren().addAll(lbl, barContainer, valLbl);
        return row;
    }

    private VBox buildSackCard(SackGroupSummary s) {
        VBox card = new VBox(8);
        card.setStyle("-fx-background-color: " + StyleConstants.SURFACE_HEX + "; -fx-border-color: " + StyleConstants.BORDER_HEX + "; -fx-border-width: 0.5px; -fx-border-radius: 9px; -fx-background-radius: 9px; -fx-padding: 10 12;");

        HBox row1 = new HBox();
        row1.setAlignment(Pos.CENTER_LEFT);
        Label nameLbl = new Label(s.getName());
        nameLbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.TEXT_DARK_HEX + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label dateRangeLbl = new Label(s.getDateRange());
        dateRangeLbl.setStyle("-fx-font-size: 9px; -fx-text-fill: " + StyleConstants.TEXT_MUTED_HEX + ";");

        row1.getChildren().addAll(nameLbl, spacer, dateRangeLbl);

        // Row 2: 3 value columns with dividers
        HBox row2 = new HBox(8);
        row2.setAlignment(Pos.CENTER);

        VBox colGood = buildSackCardCol("GOOD", s.hasTotal() ? String.format("%,d", s.getGood()) : "—", s.hasTotal() ? StyleConstants.BLUE : StyleConstants.TEXT_HINT);
        VBox colReject = buildSackCardCol("REJECT", s.hasTotal() ? String.format("%,d", s.getReject()) : "—", s.hasTotal() ? StyleConstants.RED : StyleConstants.TEXT_HINT);
        VBox colRate = buildSackCardCol("RATE", s.hasTotal() ? String.format("%.1f%%", s.getRejectRate()) : "—", s.hasTotal() ? rejectRateColor(s.getRejectRate()) : StyleConstants.TEXT_HINT);

        HBox.setHgrow(colGood, Priority.ALWAYS);
        HBox.setHgrow(colReject, Priority.ALWAYS);
        HBox.setHgrow(colRate, Priority.ALWAYS);

        Region div1 = new Region();
        div1.setStyle("-fx-background-color: " + StyleConstants.BORDER_HEX + "; -fx-pref-width: 0.5px; -fx-min-width: 0.5px; -fx-max-width: 0.5px; -fx-pref-height: 25;");
        Region div2 = new Region();
        div2.setStyle("-fx-background-color: " + StyleConstants.BORDER_HEX + "; -fx-pref-width: 0.5px; -fx-min-width: 0.5px; -fx-max-width: 0.5px; -fx-pref-height: 25;");

        row2.getChildren().addAll(colGood, div1, colReject, div2, colRate);
        card.getChildren().addAll(row1, row2);
        return card;
    }

    private VBox buildSackCardCol(String labelText, String valText, Color valColor) {
        VBox col = new VBox(2);
        col.setAlignment(Pos.CENTER);

        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: " + StyleConstants.TEXT_MUTED_HEX + "; -fx-letter-spacing: 0.5px;");

        Label val = new Label(valText);
        String hexColor = "#" + valColor.toString().substring(2, 8);
        val.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: " + hexColor + ";");

        col.getChildren().addAll(lbl, val);
        return col;
    }

    private Label buildPill(String text, String bgColor, String textColor) {
        Label label = new Label(text);
        label.setStyle("-fx-background-color: " + bgColor + "; -fx-text-fill: " + textColor + "; -fx-background-radius: 999; -fx-padding: 4 8; -fx-font-size: 10px; -fx-font-weight: bold;");
        return label;
    }

    private Label buildCell(String value, String style) {
        Label label = new Label(value);
        if (style != null && !style.isEmpty()) {
            label.setStyle(style);
        }
        return label;
    }

    private Color rejectRateColor(double rate) {
        if (rate < 65.0) {
            return StyleConstants.GREEN;
        } else if (rate < 75.0) {
            return Color.web("#D97706");
        } else {
            return StyleConstants.RED;
        }
    }

    private void updateRowStyle(HBox rowNode, boolean isFlagged, boolean isHovered) {
        String bg = isHovered ? StyleConstants.SURFACE_HEX : StyleConstants.WHITE_HEX;
        if (isFlagged) {
            rowNode.setStyle("-fx-background-color: " + bg + "; -fx-border-color: transparent transparent " + StyleConstants.BORDER_HEX + " transparent, transparent transparent transparent " + StyleConstants.RED_HEX + "; -fx-border-width: 0 0 1 0, 0 0 0 3; -fx-padding: 0 10; -fx-alignment: center-left; -fx-min-height: 36px; -fx-pref-height: 36px; -fx-max-height: 36px;");
        } else {
            rowNode.setStyle("-fx-background-color: " + bg + "; -fx-border-color: transparent transparent " + StyleConstants.BORDER_HEX + " transparent; -fx-border-width: 0 0 1 0; -fx-padding: 0 10; -fx-alignment: center-left; -fx-min-height: 36px; -fx-pref-height: 36px; -fx-max-height: 36px;");
        }
    }
}
