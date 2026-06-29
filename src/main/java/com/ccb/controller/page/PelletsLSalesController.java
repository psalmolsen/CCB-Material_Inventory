package com.ccb.controller.page;

import com.ccb.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.Cursor;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.net.URL;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

public class PelletsLSalesController implements Initializable {

    private static final int ROWS_PER_PAGE = 5;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> sackFilterComboBox;
    @FXML private Button refreshButton;
    @FXML private VBox loadingState;
    @FXML private VBox errorState;
    @FXML private Label errorMessage;
    @FXML private Button retryButton;
    @FXML private VBox emptyState;
    @FXML private VBox dataState;

    @FXML private Button prevSlotBtn;
    @FXML private Button nextSlotBtn;
    @FXML private Label  slotTimeLabel;
    @FXML private Label  slotDateLabel;
    @FXML private Label  slotCounterLabel;
    @FXML private HBox   slotDotsBox;
    @FXML private Label  slotGoodLabel;
    @FXML private Label  slotRejLabel;
    @FXML private Label  slotRateLabel;
    @FXML private Label  sackTotalGoodLabel;
    @FXML private BarChart<String,Number> slotBarChart;
    @FXML private HBox   contextStrip;

    private List<PelletsLRecord> currentSackSlots = new ArrayList<>();
    private int slotIdx = 0;
    private XYChart.Series<String,Number> goodSeries, rejSeries;

    // View Components Built Once
    private final VBox pageContent = new VBox(14);
    private final HBox statCardsHost = new HBox(9);
    private final GridPane chartCardsHost = new GridPane();
    private final GridPane brandBagSizeGrid = new GridPane();
    
    // Zone 4 Table Components
    private final VBox tableSectionCard = new VBox(10);
    private final VBox tableRowsHost = new VBox(0);
    private final HBox paginationHost = new HBox(10);
    private final Label paginationSummaryLabel = new Label();
    private final HBox pageDotsHost = new HBox(8);
    private final List<HBox> tableRows = new ArrayList<>(ROWS_PER_PAGE);

    // Data Storage
    private final List<PelletsLRecord> allRecords = new ArrayList<>();
    private final List<PelletsLRecord> filteredRecords = new ArrayList<>();
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
        if (sackFilterComboBox != null) {
            sackFilterComboBox.setOnAction(e -> {
                currentPage = 0;
                render();
                String selected = sackFilterComboBox.getValue();
                if (selected != null && !selected.equals("All sack output — pellets report, L-Sales")) {
                    loadSackSlots(selected);
                }
            });
        }
        if (retryButton != null) {
            retryButton.setOnAction(e -> loadData());
        }
        if (refreshButton != null) {
            refreshButton.setOnAction(e -> loadData());
        }

        goodSeries = new XYChart.Series<>();
        goodSeries.setName("Good");
        rejSeries = new XYChart.Series<>();
        rejSeries.setName("Rejected");
        slotBarChart.getData().addAll(goodSeries, rejSeries);
        slotBarChart.getStyleClass().add("slot-bar-chart");

        loadData();
    }

    public void setRefreshCallback(Runnable refreshCallback) {
        this.refreshCallback = refreshCallback;
    }

    public void refresh() {
        loadData();
    }

    private void loadData() {
        showLoading();
        Task<List<PelletsLRecord>> task = new Task<>() {
            @Override
            protected List<PelletsLRecord> call() throws Exception {
                PelletsLSheetService service = new PelletsLSheetService();
                return service.readRecords();
            }

            @Override
            protected void succeeded() {
                allRecords.clear();
                allRecords.addAll(getValue() == null ? List.of() : getValue());
                currentPage = 0;
                updateOutputOptions();
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
        errorMessage.setText("Error loading Pellets L-Sales data: " + ex.getMessage());
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

    private void updateOutputOptions() {
        Set<String> sackGroups = new LinkedHashSet<>();
        for (PelletsLRecord record : allRecords) {
            if (!record.getSackGroup().isEmpty()) {
                sackGroups.add(record.getSackGroup());
            }
        }

        if (sackFilterComboBox == null) {
            return;
        }

        sackFilterComboBox.getItems().clear();
        sackFilterComboBox.getItems().add("All sack output — pellets report, L-Sales");

        for (String sackGroup : sackGroups) {
            sackFilterComboBox.getItems().add(sackGroup);
        }

        sackFilterComboBox.getSelectionModel().selectFirst();
    }

    private void render() {
        filteredRecords.clear();
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        String selectedSack = sackFilterComboBox == null ? "All sack output — pellets report, L-Sales" : sackFilterComboBox.getSelectionModel().getSelectedItem();

        for (PelletsLRecord r : allRecords) {
            if (selectedSack != null && !selectedSack.startsWith("All")) {
                if (!r.getSackGroup().equals(selectedSack)) {
                    continue;
                }
            }

            if (!query.isEmpty()) {
                String dateStr = r.getDate().toLowerCase(Locale.ROOT);
                String brandStr = r.getBrand().toLowerCase(Locale.ROOT);
                String bagStr = r.getBagSize().toLowerCase(Locale.ROOT);
                String tsStr = r.getTimeSlot().toLowerCase(Locale.ROOT);
                String shiftStr = r.getShiftLabel().toLowerCase(Locale.ROOT);

                if (!dateStr.contains(query) && !brandStr.contains(query) && 
                    !bagStr.contains(query) && !tsStr.contains(query) && !shiftStr.contains(query)) {
                    continue;
                }
            }

            filteredRecords.add(r);
        }

        if (filteredRecords.isEmpty()) {
            showEmpty();
            return;
        }

        PelletsLSummary summary = PelletsLDataService.compute(filteredRecords);
        renderStatCards(summary);
        renderChartCards(summary);
        renderBrandBagSizePanels(summary);
        renderTable();
        showData();
    }

    private void buildPageShell() {
        if (dataState == null) {
            return;
        }

        pageContent.setStyle("-fx-background-color: #F4F5F7;");

        statCardsHost.setAlignment(Pos.TOP_LEFT);

        chartCardsHost.setHgap(10);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(60);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(40);
        chartCardsHost.getColumnConstraints().addAll(col1, col2);

        brandBagSizeGrid.setHgap(8);
        ColumnConstraints bbCol1 = new ColumnConstraints();
        bbCol1.setPercentWidth(50);
        ColumnConstraints bbCol2 = new ColumnConstraints();
        bbCol2.setPercentWidth(50);
        brandBagSizeGrid.getColumnConstraints().addAll(bbCol1, bbCol2);

        tableSectionCard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E5E7EB; -fx-border-width: 0.5; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 14;");
        
        Label tblTitle = new Label("Shift Log");
        tblTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #1A2535;");
        
        Label tblSubtitle = new Label("Yellow rows = shift totals. Use arrows to browse all entries.");
        tblSubtitle.setStyle("-fx-font-size: 10px; -fx-text-fill: #9CA3AF;");
        
        VBox tableHeaderBox = new VBox(4);
        tableHeaderBox.getChildren().addAll(tblTitle, tblSubtitle);

        tableRowsHost.setSpacing(0);
        for (int i = 0; i < ROWS_PER_PAGE; i++) {
            HBox rowNode = prebuildTableRowNode();
            tableRows.add(rowNode);
            tableRowsHost.getChildren().add(rowNode);
        }

        paginationHost.setAlignment(Pos.CENTER_LEFT);
        paginationSummaryLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #9CA3AF;");
        
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

        pageContent.getChildren().addAll(
                buildSectionLabel("AT A GLANCE — PELLETS OUTPUT THIS PERIOD"),
                statCardsHost,
                buildSectionLabel("WHEN DID OUTPUT HAPPEN? — SHIFT BY SHIFT"),
                chartCardsHost,
                buildSectionLabel("WHAT WAS PRODUCED? — BY BRAND AND BAG SIZE"),
                brandBagSizeGrid,
                buildSectionLabel("FULL SHIFT LOG — EVERY ENTRY"),
                tableSectionCard
        );

        dataState.getChildren().setAll(pageContent);
    }

    private HBox prebuildTableRowNode() {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPrefHeight(36);
        row.setMinHeight(36);
        row.setMaxHeight(36);
        row.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: transparent transparent #F3F4F6 transparent; -fx-border-width: 0 0 1 0; -fx-padding: 0 10;");

        Label cellDate = buildCell("—", "");
        cellDate.setPrefWidth(90); cellDate.setMinWidth(90);
        
        Label cellShift = buildCell("—", "");
        cellShift.setPrefWidth(80); cellShift.setMinWidth(80);
        
        Label cellTimeSlot = buildCell("—", "");
        cellTimeSlot.setPrefWidth(100); cellTimeSlot.setMinWidth(100);
        
        Label cellGood = buildCell("—", "");
        cellGood.setPrefWidth(80); cellGood.setMinWidth(80);
        
        Label cellReject = buildCell("—", "");
        cellReject.setPrefWidth(80); cellReject.setMinWidth(80);
        
        Label cellBrand = buildCell("—", "");
        cellBrand.setPrefWidth(100); cellBrand.setMinWidth(100);
        
        Label cellBagSize = buildCell("—", "");
        cellBagSize.setPrefWidth(80); cellBagSize.setMinWidth(80);

        row.getChildren().addAll(cellDate, cellShift, cellTimeSlot, cellGood, cellReject, cellBrand, cellBagSize);

        for (Node child : row.getChildren()) {
            HBox.setHgrow(child, Priority.ALWAYS);
        }

        row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: #F9FAFB; -fx-border-color: transparent transparent #F3F4F6 transparent; -fx-border-width: 0 0 1 0; -fx-padding: 0 10; -fx-min-height: 36; -fx-pref-height: 36; -fx-max-height: 36; -fx-alignment: center-left;"));
        row.setOnMouseExited(e -> row.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: transparent transparent #F3F4F6 transparent; -fx-border-width: 0 0 1 0; -fx-padding: 0 10; -fx-min-height: 36; -fx-pref-height: 36; -fx-max-height: 36; -fx-alignment: center-left;"));

        return row;
    }

    private HBox buildTableHeader() {
        HBox header = new HBox(8);
        header.setStyle("-fx-background-color: #F9FAFB; -fx-border-color: #E5E7EB; -fx-border-width: 1; -fx-border-radius: 10px; -fx-background-radius: 10px; -fx-min-height: 32px; -fx-pref-height: 32px; -fx-padding: 0 10; -fx-alignment: center-left;");
        
        header.getChildren().addAll(
                headerCell("DATE", 90),
                headerCell("SHIFT", 80),
                headerCell("TIME SLOT", 100),
                headerCell("BLASTING GOOD", 80),
                headerCell("BLASTING REJECT", 80),
                headerCell("BRAND", 100),
                headerCell("BAG SIZE", 80)
        );
        for (Node node : header.getChildren()) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }
        return header;
    }

    private Label headerCell(String text, double width) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #9CA3AF; -fx-letter-spacing: 1px;");
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

    private void renderStatCards(PelletsLSummary summary) {
        statCardsHost.getChildren().clear();

        double rejectRate = summary.getOverallRejectRate();
        String rejectBadgeText = String.format("%.1f%% reject rate", rejectRate);
        String rejectBadgeStyle = rejectRate < 15.0 ? "yellow" : "red";

        VBox card1 = buildStatCard(
                "TOTAL BLASTING GOOD",
                String.format("%,d", summary.getTotalBlastingGood()),
                StyleConstants.BLUE,
                "units passed blasting",
                "green",
                ""
        );
        
        VBox card2 = buildStatCard(
                "TOTAL BLASTING REJECT",
                String.format("%,d", summary.getTotalBlastingReject()),
                StyleConstants.RED,
                "units rejected",
                rejectBadgeStyle,
                rejectBadgeText
        );

        VBox card3 = buildStatCard(
                "TOTAL SHIFTS LOGGED",
                String.format("%,d", summary.getTotalShifts()),
                Color.web("#D97706"),
                "shifts this period",
                "green",
                "All complete"
        );

        VBox card4 = buildStatCard(
                "TOP BRAND",
                summary.getTopBrand().isEmpty() ? "—" : summary.getTopBrand(),
                StyleConstants.GREEN,
                "highest volume output",
                "green",
                "Most produced"
        );

        double bagSizePct = summary.getTopBagSizePercentage();
        String bagSizeBadge = String.format("%.0f%%", bagSizePct);
        VBox card5 = buildStatCard(
                "TOP BAG SIZE",
                summary.getTopBagSize().isEmpty() ? "—" : summary.getTopBagSize(),
                StyleConstants.PURPLE,
                "dominant bag size",
                "blue",
                bagSizeBadge
        );

        statCardsHost.getChildren().addAll(card1, card2, card3, card4, card5);
        for (Node node : statCardsHost.getChildren()) {
            HBox.setHgrow(node, Priority.ALWAYS);
        }
    }

    private VBox buildStatCard(String question, String value, Color color, String context, String badgeStyle, String badgeText) {
        VBox card = new VBox(8);
        String hexColor = "#" + color.toString().substring(2, 8);
        card.setStyle("-fx-background-color: #FFFFFF; -fx-background-radius: 12px; -fx-border-radius: 12px; -fx-border-width: 3px 0.5px 0.5px 0.5px; -fx-border-color: " + hexColor + " #E5E7EB #E5E7EB #E5E7EB; -fx-padding: 12px 14px;");

        Label qLbl = new Label(question);
        qLbl.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #9CA3AF; -fx-letter-spacing: 0.5px;");

        Label valLbl = new Label(value);
        valLbl.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + hexColor + ";");

        Label ctxLbl = new Label(context);
        ctxLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #6B7280;");

        if (!badgeText.isEmpty()) {
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
            } else if ("blue".equals(badgeStyle)) {
                bgStyle = StyleConstants.BADGE_BLUE_BG;
                txtStyle = StyleConstants.BADGE_BLUE_TXT;
            } else {
                bgStyle = StyleConstants.BADGE_PURPLE_BG;
                txtStyle = StyleConstants.BADGE_PURPLE_TXT;
            }

            Label pill = buildPill(badgeText, bgStyle, txtStyle);
            card.getChildren().addAll(qLbl, valLbl, ctxLbl, pill);
        } else {
            card.getChildren().addAll(qLbl, valLbl, ctxLbl);
        }
        
        return card;
    }

    private void renderChartCards(PelletsLSummary summary) {
        chartCardsHost.getChildren().clear();

        // Card 1: Bar Chart - Good vs Rejected per shift
        VBox barChartCard = new VBox(10);
        barChartCard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E5E7EB; -fx-border-width: 0.5; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 13 14;");
        
        HBox barHeader = new HBox(8);
        barHeader.setAlignment(Pos.TOP_LEFT);
        Region barStripe = new Region();
        barStripe.setStyle("-fx-background-color: #1F5FA6; -fx-pref-width: 3; -fx-min-width: 3; -fx-max-width: 3; -fx-pref-height: 24;");
        
        VBox barTitleBox = new VBox(2);
        Label barTitle = new Label("Good vs Rejected per shift");
        barTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1A2535;");
        Label barDesc = new Label("Each group = one shift. Blue = good output. Red = rejects.");
        barDesc.setStyle("-fx-font-size: 10px; -fx-text-fill: #9CA3AF;");
        barTitleBox.getChildren().addAll(barTitle, barDesc);
        barHeader.getChildren().addAll(barStripe, barTitleBox);

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setLegendVisible(false);
        barChart.setCategoryGap(8);
        barChart.setBarGap(4);
        barChart.setStyle("-fx-background-color: #F3F4F6;");

        xAxis.setStyle("-fx-tick-label-fill: #6B7280; -fx-tick-label-font-size: 9px;");
        yAxis.setStyle("-fx-tick-label-fill: #6B7280; -fx-tick-label-font-size: 9px;");
        yAxis.setTickLabelsVisible(false);
        yAxis.setMinorTickVisible(false);

        BarChart.Series<String, Number> goodSeries = new BarChart.Series<>();
        goodSeries.setName("Good");
        BarChart.Series<String, Number> rejectSeries = new BarChart.Series<>();
        rejectSeries.setName("Rejected");

        for (Map.Entry<String, int[]> entry : summary.getByShift().entrySet()) {
            String shiftLabel = entry.getKey();
            int good = entry.getValue()[0];
            int reject = entry.getValue()[1];
            
            goodSeries.getData().add(new BarChart.Data<>(shiftLabel, good));
            rejectSeries.getData().add(new BarChart.Data<>(shiftLabel, reject));
        }

        barChart.getData().addAll(goodSeries, rejectSeries);

        for (Node node : barChart.lookupAll(".chart-bar")) {
            node.setStyle("-fx-bar-fill: #1F5FA6; -fx-background-radius: 4;");
        }

        barChartCard.getChildren().addAll(barHeader, barChart);

        // Card 2: Pie Chart - Overall blasting result
        VBox pieChartCard = new VBox(10);
        pieChartCard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E5E7EB; -fx-border-width: 0.5; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 13 14;");
        
        HBox pieHeader = new HBox(8);
        pieHeader.setAlignment(Pos.TOP_LEFT);
        Region pieStripe = new Region();
        pieStripe.setStyle("-fx-background-color: #C0392B; -fx-pref-width: 3; -fx-min-width: 3; -fx-max-width: 3; -fx-pref-height: 24;");
        
        VBox pieTitleBox = new VBox(2);
        Label pieTitle = new Label("Overall blasting result");
        pieTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1A2535;");
        Label pieDesc = new Label("Out of all units processed");
        pieDesc.setStyle("-fx-font-size: 10px; -fx-text-fill: #9CA3AF;");
        pieTitleBox.getChildren().addAll(pieTitle, pieDesc);
        pieHeader.getChildren().addAll(pieStripe, pieTitleBox);

        PieChart pieChart = new PieChart();
        pieChart.setLegendVisible(false);
        pieChart.setStyle("-fx-pie-color: transparent;");
        pieChart.getData().addAll(
                new PieChart.Data("Good", summary.getTotalBlastingGood()),
                new PieChart.Data("Rejected", summary.getTotalBlastingReject())
        );

        for (PieChart.Data data : pieChart.getData()) {
            if (data.getName().equals("Good")) {
                data.getNode().setStyle("-fx-pie-color: #1F5FA6;");
            } else {
                data.getNode().setStyle("-fx-pie-color: #C0392B;");
            }
        }

        // Legend
        HBox legendBox = new HBox(12);
        legendBox.setAlignment(Pos.CENTER_LEFT);
        
        Circle goodDot = new Circle(4);
        goodDot.setFill(Color.web("#1F5FA6"));
        Label goodLegend = new Label("Good " + String.format("%,d", summary.getTotalBlastingGood()));
        goodLegend.setStyle("-fx-font-size: 10px; -fx-text-fill: #1A2535;");
        
        Circle rejectDot = new Circle(4);
        rejectDot.setFill(Color.web("#C0392B"));
        Label rejectLegend = new Label("Rejected " + String.format("%,d", summary.getTotalBlastingReject()));
        rejectLegend.setStyle("-fx-font-size: 10px; -fx-text-fill: #1A2535;");
        
        legendBox.getChildren().addAll(goodDot, goodLegend, rejectDot, rejectLegend);

        pieChartCard.getChildren().addAll(pieHeader, pieChart, legendBox);

        chartCardsHost.add(barChartCard, 0, 0);
        chartCardsHost.add(pieChartCard, 1, 0);
    }

    private void renderBrandBagSizePanels(PelletsLSummary summary) {
        brandBagSizeGrid.getChildren().clear();

        // Panel 1: Output by Brand
        VBox brandPanel = new VBox(10);
        brandPanel.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E5E7EB; -fx-border-width: 0.5; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 13 14;");
        
        HBox brandHeader = new HBox(8);
        brandHeader.setAlignment(Pos.TOP_LEFT);
        Region brandStripe = new Region();
        brandStripe.setStyle("-fx-background-color: #E9B52D; -fx-pref-width: 3; -fx-min-width: 3; -fx-max-width: 3; -fx-pref-height: 24;");
        
        VBox brandTitleBox = new VBox(2);
        Label brandTitle = new Label("Output by Brand");
        brandTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1A2535;");
        Label brandDesc = new Label("Which brand had the most pellet output this period");
        brandDesc.setStyle("-fx-font-size: 10px; -fx-text-fill: #9CA3AF;");
        brandTitleBox.getChildren().addAll(brandTitle, brandDesc);
        brandHeader.getChildren().addAll(brandStripe, brandTitleBox);

        VBox brandBars = new VBox(8);
        brandBars.setStyle("-fx-padding: 10 0 0 0;");
        
        int maxBrandVal = 1;
        for (int val : summary.getByBrand().values()) {
            maxBrandVal = Math.max(maxBrandVal, val);
        }
        
        int brandIndex = 0;
        for (Map.Entry<String, Integer> entry : summary.getByBrand().entrySet()) {
            String barColor;
            if (brandIndex < 3) {
                barColor = "#1F5FA6";
            } else if (brandIndex < summary.getByBrand().size() / 2) {
                barColor = "#E9B52D";
            } else {
                barColor = "#9CA3AF";
            }
            brandBars.getChildren().add(buildBrandBar(entry.getKey(), entry.getValue(), maxBrandVal, barColor));
            brandIndex++;
        }

        brandPanel.getChildren().addAll(brandHeader, brandBars);

        // Panel 2: Output by Bag Size
        VBox bagSizePanel = new VBox(10);
        bagSizePanel.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E5E7EB; -fx-border-width: 0.5; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 13 14;");
        
        HBox bagHeader = new HBox(8);
        bagHeader.setAlignment(Pos.TOP_LEFT);
        Region bagStripe = new Region();
        bagStripe.setStyle("-fx-background-color: #7C3AED; -fx-pref-width: 3; -fx-min-width: 3; -fx-max-width: 3; -fx-pref-height: 24;");
        
        VBox bagTitleBox = new VBox(2);
        Label bagTitle = new Label("Output by Bag Size");
        bagTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1A2535;");
        Label bagDesc = new Label("Which bag size was filled most across all shifts");
        bagDesc.setStyle("-fx-font-size: 10px; -fx-text-fill: #9CA3AF;");
        bagTitleBox.getChildren().addAll(bagTitle, bagDesc);
        bagHeader.getChildren().addAll(bagStripe, bagTitleBox);

        VBox bagSizeBars = new VBox(8);
        bagSizeBars.setStyle("-fx-padding: 10 0 0 0;");
        
        int maxBagSizeVal = 1;
        for (int val : summary.getByBagSize().values()) {
            maxBagSizeVal = Math.max(maxBagSizeVal, val);
        }

        // Bag size opacity mapping
        Map<String, Double> bagOpacityMap = Map.of(
                "11kg", 1.0,
                "22kg", 0.7,
                "50kg", 0.5,
                "1kg", 0.3
        );

        for (Map.Entry<String, Integer> entry : summary.getByBagSize().entrySet()) {
            double opacity = bagOpacityMap.getOrDefault(entry.getKey(), 0.5);
            bagSizeBars.getChildren().add(buildBagSizeBar(entry.getKey(), entry.getValue(), maxBagSizeVal, opacity));
        }

        // Stacked proportion bar
        int totalBagEntries = summary.getByBagSize().values().stream().mapToInt(Integer::intValue).sum();
        HBox proportionBar = new HBox();
        proportionBar.setStyle("-fx-background-color: #F3F4F6; -fx-background-radius: 4px; -fx-pref-height: 8px; -fx-min-height: 8px; -fx-max-height: 8px;");
        
        for (Map.Entry<String, Integer> entry : summary.getByBagSize().entrySet()) {
            double pct = totalBagEntries > 0 ? (entry.getValue() * 100.0) / totalBagEntries : 0;
            double opacity = bagOpacityMap.getOrDefault(entry.getKey(), 0.5);
            Region segment = new Region();
            segment.setStyle("-fx-background-color: rgba(124, 58, 237, " + opacity + "); -fx-background-radius: 4px;");
            HBox.setHgrow(segment, Priority.ALWAYS);
            proportionBar.getChildren().add(segment);
        }

        // Legend for proportion bar
        HBox proportionLegend = new HBox(8);
        proportionLegend.setAlignment(Pos.CENTER_LEFT);
        for (Map.Entry<String, Integer> entry : summary.getByBagSize().entrySet()) {
            double pct = totalBagEntries > 0 ? (entry.getValue() * 100.0) / totalBagEntries : 0;
            double opacity = bagOpacityMap.getOrDefault(entry.getKey(), 0.5);
            Region legendDot = new Region();
            legendDot.setStyle("-fx-background-color: rgba(124, 58, 237, " + opacity + "); -fx-pref-width: 8px; -fx-min-width: 8px; -fx-max-width: 8px; -fx-pref-height: 8px; -fx-min-height: 8px; -fx-max-height: 8px; -fx-background-radius: 2px;");
            Label legendText = new Label(String.format("%s %.0f%%", entry.getKey(), pct));
            legendText.setStyle("-fx-font-size: 9px; -fx-text-fill: #6B7280;");
            proportionLegend.getChildren().addAll(legendDot, legendText);
        }

        bagSizePanel.getChildren().addAll(bagHeader, bagSizeBars, proportionBar, proportionLegend);

        brandBagSizeGrid.add(brandPanel, 0, 0);
        brandBagSizeGrid.add(bagSizePanel, 1, 0);
    }

    private HBox buildBrandBar(String label, int value, int maxVal, String colorHex) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1A2535;");
        lbl.setPrefWidth(90);
        lbl.setMinWidth(90);

        StackPane barTrack = new StackPane();
        barTrack.setStyle("-fx-background-color: #F3F4F6; -fx-background-radius: 4px; -fx-pref-height: 8px; -fx-min-height: 8px; -fx-max-height: 8px;");
        HBox.setHgrow(barTrack, Priority.ALWAYS);
        barTrack.setAlignment(Pos.CENTER_LEFT);

        double pct = maxVal > 0 ? (double) value / maxVal : 0;
        Region barFill = new Region();
        barFill.setStyle("-fx-background-color: " + colorHex + "; -fx-background-radius: 4px; -fx-pref-height: 8px; -fx-min-height: 8px; -fx-max-height: 8px;");
        barFill.setPrefWidth(pct * 200);
        barFill.setMaxWidth(Double.MAX_VALUE);
        barTrack.getChildren().add(barFill);

        Label valLbl = new Label(String.format("%,d", value));
        valLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: " + colorHex + ";");
        valLbl.setAlignment(Pos.CENTER_RIGHT);
        valLbl.setPrefWidth(60);

        row.getChildren().addAll(lbl, barTrack, valLbl);
        return row;
    }

    private HBox buildBagSizeBar(String label, int value, int maxVal, double opacity) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1A2535;");
        lbl.setPrefWidth(90);
        lbl.setMinWidth(90);

        StackPane barTrack = new StackPane();
        barTrack.setStyle("-fx-background-color: #F3F4F6; -fx-background-radius: 4px; -fx-pref-height: 8px; -fx-min-height: 8px; -fx-max-height: 8px;");
        HBox.setHgrow(barTrack, Priority.ALWAYS);
        barTrack.setAlignment(Pos.CENTER_LEFT);

        double pct = maxVal > 0 ? (double) value / maxVal : 0;
        Region barFill = new Region();
        barFill.setStyle("-fx-background-color: rgba(124, 58, 237, " + opacity + "); -fx-background-radius: 4px; -fx-pref-height: 8px; -fx-min-height: 8px; -fx-max-height: 8px;");
        barFill.setPrefWidth(pct * 200);
        barFill.setMaxWidth(Double.MAX_VALUE);
        barTrack.getChildren().add(barFill);

        Label valLbl = new Label(String.format("%,d", value));
        valLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #7C3AED;");
        valLbl.setAlignment(Pos.CENTER_RIGHT);
        valLbl.setPrefWidth(60);

        row.getChildren().addAll(lbl, barTrack, valLbl);
        return row;
    }

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

        for (int i = 0; i < ROWS_PER_PAGE; i++) {
            HBox rowNode = tableRows.get(i);
            int recordIdx = startIdx + i;

            if (recordIdx < totalEntries) {
                rowNode.setVisible(true);
                rowNode.setManaged(true);
                PelletsLRecord record = filteredRecords.get(recordIdx);

                // Date
                Label lblDate = (Label) rowNode.getChildren().get(0);
                lblDate.setText(record.getDate().isEmpty() ? "—" : record.getDate());
                lblDate.setStyle(record.getDate().isEmpty() ? 
                        "-fx-font-size: 11px; -fx-text-fill: #D1D5DB;" :
                        "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1A2535;");

                // Shift
                Label lblShift = (Label) rowNode.getChildren().get(1);
                lblShift.setText(record.getShiftLabel().isEmpty() ? "—" : record.getShiftLabel());
                if (record.getShiftLabel().isEmpty()) {
                    lblShift.setStyle("-fx-font-size: 11px; -fx-text-fill: #D1D5DB;");
                } else {
                    lblShift.setStyle("-fx-background-color: #1B2A3B; -fx-text-fill: #E9B52D; -fx-background-radius: 8; -fx-padding: 2 7; -fx-font-size: 9px; -fx-font-weight: bold;");
                }

                // Time Slot
                Label lblTime = (Label) rowNode.getChildren().get(2);
                if (record.isTotalRow()) {
                    lblTime.setText("Shift Total");
                    lblTime.setStyle("-fx-background-color: #FEF3C7; -fx-text-fill: #D97706; -fx-background-radius: 8; -fx-padding: 2 7; -fx-font-size: 9px; -fx-font-weight: bold;");
                } else {
                    lblTime.setText(record.getTimeSlot().isEmpty() ? "—" : record.getTimeSlot());
                    if (record.getTimeSlot().isEmpty()) {
                        lblTime.setStyle("-fx-font-size: 11px; -fx-text-fill: #D1D5DB;");
                    } else {
                        lblTime.setStyle("-fx-background-color: #EFF6FF; -fx-text-fill: #1D4ED8; -fx-background-radius: 8; -fx-padding: 2 7; -fx-font-size: 9px; -fx-font-weight: bold;");
                    }
                }

                // Good
                Label lblGood = (Label) rowNode.getChildren().get(3);
                lblGood.setText(String.format("%,d", record.getBlastingGood()));
                lblGood.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #16A34A;");

                // Reject
                Label lblRej = (Label) rowNode.getChildren().get(4);
                if (record.getBlastingReject() == 0) {
                    lblRej.setText("—");
                    lblRej.setStyle("-fx-font-size: 11px; -fx-text-fill: #D1D5DB;");
                } else {
                    lblRej.setText(String.format("%,d", record.getBlastingReject()));
                    lblRej.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #C0392B;");
                }

                // Brand
                Label lblBrand = (Label) rowNode.getChildren().get(5);
                lblBrand.setText(record.getBrand().isEmpty() ? "—" : record.getBrand());
                lblBrand.setStyle("-fx-background-color: #F3F4F6; -fx-text-fill: #374151; -fx-border-color: #E5E7EB; -fx-border-width: 0.5; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 2 7; -fx-font-size: 9px; -fx-font-weight: bold;");

                // Bag Size
                Label lblBagSize = (Label) rowNode.getChildren().get(6);
                lblBagSize.setText(record.getBagSize().isEmpty() ? "—" : record.getBagSize());
                lblBagSize.setStyle("-fx-background-color: #F5F3FF; -fx-text-fill: #6D28D9; -fx-border-color: #E5E7EB; -fx-border-width: 0.5; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 2 7; -fx-font-size: 9px; -fx-font-weight: bold;");

                // Total row styling
                if (record.isTotalRow()) {
                    rowNode.setStyle("-fx-background-color: #FFFBEB; -fx-border-color: #FDE68A transparent #FDE68A transparent; -fx-border-width: 1 0 2 3; -fx-padding: 0 10; -fx-min-height: 36; -fx-pref-height: 36; -fx-max-height: 36; -fx-alignment: center-left;");
                    for (Node child : rowNode.getChildren()) {
                        if (child instanceof Label) {
                            ((Label) child).setStyle(((Label) child).getStyle() + " -fx-font-weight: bold;");
                        }
                    }
                } else {
                    rowNode.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: transparent transparent #F3F4F6 transparent; -fx-border-width: 0 0 1 0; -fx-padding: 0 10; -fx-min-height: 36; -fx-pref-height: 36; -fx-max-height: 36; -fx-alignment: center-left;");
                }

                rowNode.setOnMouseEntered(e -> {
                    if (!record.isTotalRow()) {
                        rowNode.setStyle("-fx-background-color: #F9FAFB; -fx-border-color: transparent transparent #F3F4F6 transparent; -fx-border-width: 0 0 1 0; -fx-padding: 0 10; -fx-min-height: 36; -fx-pref-height: 36; -fx-max-height: 36; -fx-alignment: center-left;");
                    }
                });
                rowNode.setOnMouseExited(e -> {
                    if (record.isTotalRow()) {
                        rowNode.setStyle("-fx-background-color: #FFFBEB; -fx-border-color: #FDE68A transparent #FDE68A transparent; -fx-border-width: 1 0 2 3; -fx-padding: 0 10; -fx-min-height: 36; -fx-pref-height: 36; -fx-max-height: 36; -fx-alignment: center-left;");
                    } else {
                        rowNode.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: transparent transparent #F3F4F6 transparent; -fx-border-width: 0 0 1 0; -fx-padding: 0 10; -fx-min-height: 36; -fx-pref-height: 36; -fx-max-height: 36; -fx-alignment: center-left;");
                    }
                });
            } else {
                rowNode.setVisible(false);
                rowNode.setManaged(false);
            }
        }

        paginationSummaryLabel.setText(String.format("Showing %d–%d of %d entries", 
                totalEntries == 0 ? 0 : startIdx + 1, endIdx, totalEntries));

        HBox arrowControls = (HBox) paginationHost.getChildren().get(3);
        Button prevBtn = (Button) arrowControls.getChildren().get(0);
        Button nextBtn = (Button) arrowControls.getChildren().get(1);

        prevBtn.setDisable(currentPage <= 0);
        nextBtn.setDisable(startIdx + ROWS_PER_PAGE >= totalEntries);
        
        updateArrowStyles(prevBtn);
        updateArrowStyles(nextBtn);

        renderPageDots(totalPages);
    }

    private void updateArrowStyles(Button btn) {
        if (btn.isDisable()) {
            btn.setStyle("-fx-min-width: 26px; -fx-min-height: 26px; -fx-pref-width: 26px; -fx-pref-height: 26px; -fx-background-color: #F4F5F7; -fx-border-color: #E5E7EB; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-border-width: 0.5; -fx-text-fill: #D1D5DB; -fx-font-weight: bold; -fx-padding: 0;");
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
                dot.setStyle("-fx-background-color: #1B2A3B; -fx-min-width: 16px; -fx-pref-width: 16px; -fx-max-width: 16px; -fx-min-height: 6px; -fx-pref-height: 6px; -fx-max-height: 6px; -fx-background-radius: 3px;");
            } else {
                dot.setStyle("-fx-background-color: #E5E7EB; -fx-min-width: 6px; -fx-pref-width: 6px; -fx-max-width: 6px; -fx-min-height: 6px; -fx-pref-height: 6px; -fx-max-height: 6px; -fx-background-radius: 999; -fx-cursor: hand;");
                dot.setOnMouseClicked(e -> {
                    currentPage = pageIndex;
                    renderTable();
                });
            }
            
            pageDotsHost.getChildren().add(dot);
        }
    }

    private HBox buildSectionLabel(String text) {
        HBox hbox = new HBox(10);
        hbox.setAlignment(Pos.CENTER_LEFT);
        hbox.setStyle("-fx-padding: 10 0 5 0;");
        
        Label label = new Label(text.toUpperCase());
        label.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #9CA3AF; -fx-letter-spacing: 1.2px;");

        Region line = new Region();
        HBox.setHgrow(line, Priority.ALWAYS);
        line.setStyle("-fx-background-color: #E5E7EB; -fx-pref-height: 0.5px; -fx-min-height: 0.5px; -fx-max-height: 0.5px;");

        hbox.getChildren().addAll(label, line);
        return hbox;
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

    private void loadSackSlots(String sackLabel) {
        currentSackSlots = allRecords.stream()
            .filter(r -> r.getSackGroup().equals(sackLabel) && !r.isTotalRow())
            .collect(Collectors.toList());
        slotIdx = 0;
        renderSlot();
    }

    private void renderSlot() {
        if (currentSackSlots.isEmpty()) {
            return;
        }
        PelletsLRecord sl = currentSackSlots.get(slotIdx);
        int total = currentSackSlots.size();
        int good = sl.getBlastingGood();
        int rej = sl.getBlastingReject();
        double rate = (good + rej) == 0 ? 0 : (rej * 100.0) / (good + rej);
        int sackTotal = currentSackSlots.stream().mapToInt(PelletsLRecord::getBlastingGood).sum();

        Platform.runLater(() -> {
            slotTimeLabel.setText(sl.getTimeSlot());
            slotDateLabel.setText(sl.getDate());
            slotCounterLabel.setText("Time slot " + (slotIdx + 1) + " of " + total);
            slotGoodLabel.setText(String.format("%,d", good));
            slotRejLabel.setText(String.format("%,d", rej));
            slotRateLabel.setText(String.format("%.1f%%", rate));

            String rateColor;
            if (rate >= 70) {
                rateColor = "#C0392B";
            } else if (rate >= 40) {
                rateColor = "#D97706";
            } else {
                rateColor = "#16A34A";
            }
            slotRateLabel.setStyle("-fx-text-fill: " + rateColor + "; -fx-font-size: 20px; -fx-font-weight: bold;");

            sackTotalGoodLabel.setText(String.format("%,d total", sackTotal));

            prevSlotBtn.setDisable(slotIdx == 0);
            nextSlotBtn.setDisable(slotIdx == total - 1);

            updateSlotChart(sl);
            buildDots(total);
            buildContextStrip();
        });
    }

    private void updateSlotChart(PelletsLRecord sl) {
        goodSeries.getData().clear();
        rejSeries.getData().clear();
        goodSeries.getData().add(new XYChart.Data<>(sl.getTimeSlot(), sl.getBlastingGood()));
        rejSeries.getData().add(new XYChart.Data<>(sl.getTimeSlot(), sl.getBlastingReject()));
    }

    private void buildDots(int total) {
        slotDotsBox.getChildren().clear();
        if (total > 9) {
            Label label = new Label("slot " + (slotIdx + 1) + " of " + total);
            label.setStyle("-fx-font-size: 10px; -fx-text-fill: #9CA3AF;");
            slotDotsBox.getChildren().add(label);
            return;
        }
        for (int i = 0; i < total; i++) {
            final int idx = i;
            Region dot = new Region();
            if (i == slotIdx) {
                dot.setStyle("-fx-background-color: #1B2A3B; -fx-background-radius: 3; -fx-pref-width: 16; -fx-pref-height: 6;");
            } else {
                dot.setStyle("-fx-background-color: #E5E7EB; -fx-background-radius: 3; -fx-pref-width: 6; -fx-pref-height: 6;");
            }
            dot.setCursor(Cursor.HAND);
            dot.setOnMouseClicked(e -> {
                slotIdx = idx;
                renderSlot();
            });
            slotDotsBox.getChildren().add(dot);
        }
    }

    private void buildContextStrip() {
        contextStrip.getChildren().clear();
        int maxG = currentSackSlots.stream().mapToInt(PelletsLRecord::getBlastingGood).max().orElse(1);
        int maxR = currentSackSlots.stream().mapToInt(PelletsLRecord::getBlastingReject).max().orElse(1);

        for (int i = 0; i < currentSackSlots.size(); i++) {
            final int idx = i;
            PelletsLRecord r = currentSackSlots.get(i);
            boolean active = (i == slotIdx);

            Region gBar = new Region();
            gBar.setPrefWidth(6);
            gBar.setPrefHeight(Math.max(2, (r.getBlastingGood() * 20) / maxG));
            gBar.setStyle("-fx-background-color: #1F5FA6CC; -fx-background-radius: 2 2 0 0;");

            Region rBar = new Region();
            rBar.setPrefWidth(6);
            rBar.setPrefHeight(Math.max(2, (r.getBlastingReject() * 20) / maxR));
            rBar.setStyle("-fx-background-color: #C0392BCC; -fx-background-radius: 2 2 0 0;");

            HBox bars = new HBox(2, gBar, rBar);
            bars.setAlignment(Pos.BOTTOM_CENTER);
            bars.setPrefHeight(24);

            Label timeL = new Label(r.getTimeSlot());
            timeL.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: " + (active ? "#E9B52D" : "#6B7280") + ";");

            VBox card = new VBox(3, bars, timeL);
            card.setAlignment(Pos.CENTER);
            card.setPadding(new Insets(4, 8, 4, 8));

            if (active) {
                card.setStyle("-fx-background-color: #1B2A3B; -fx-border-color: #1B2A3B; -fx-border-radius: 6; -fx-background-radius: 6; -fx-border-width: 1;");
            } else {
                card.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-border-radius: 6; -fx-background-radius: 6; -fx-border-width: 1;");
            }

            card.setCursor(Cursor.HAND);
            card.setOnMouseClicked(e -> {
                slotIdx = idx;
                renderSlot();
            });

            contextStrip.getChildren().add(card);
        }
    }

    @FXML
    private void onPrevSlot() {
        if (slotIdx > 0) {
            slotIdx--;
            renderSlot();
        }
    }

    @FXML
    private void onNextSlot() {
        if (slotIdx < currentSackSlots.size() - 1) {
            slotIdx++;
            renderSlot();
        }
    }
}
