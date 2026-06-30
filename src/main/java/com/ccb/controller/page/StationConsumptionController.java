package com.ccb.controller.page;

import com.ccb.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.URL;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class StationConsumptionController implements Initializable {

    private static final int ROWS_PER_PAGE = 10;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> stationFilterComboBox;
    @FXML private ComboBox<String> monthFilterComboBox;
    @FXML private Button addConsumptionButton;
    @FXML private Button refreshButton;
    @FXML private VBox loadingState;
    @FXML private VBox errorState;
    @FXML private Label errorMessage;
    @FXML private Button retryButton;
    @FXML private VBox emptyState;
    @FXML private VBox dataState;

    // View Components
    private final VBox pageContent = new VBox(14);
    private final HBox statCardsHost = new HBox(9);
    private final GridPane chartCardsHost = new GridPane();
    private final VBox tableSectionCard = new VBox(10);
    private final VBox tableRowsHost = new VBox(0);
    private final HBox paginationHost = new HBox(10);
    private final Label paginationSummaryLabel = new Label();
    private final List<HBox> tableRows = new ArrayList<>(ROWS_PER_PAGE);

    // Data Storage
    private final List<StationConsumptionRecord> allRecords = new ArrayList<>();
    private final List<StationConsumptionRecord> filteredRecords = new ArrayList<>();
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
        if (stationFilterComboBox != null) {
            stationFilterComboBox.setOnAction(e -> {
                currentPage = 0;
                render();
            });
        }
        if (monthFilterComboBox != null) {
            monthFilterComboBox.setOnAction(e -> {
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
        if (addConsumptionButton != null) {
            addConsumptionButton.setOnAction(e -> openAddConsumptionDialog());
        }

        loadData();
    }

    public void setRefreshCallback(Runnable refreshCallback) {
        this.refreshCallback = refreshCallback;
    }

    public void refresh() {
        loadData();
    }

    private void openAddConsumptionDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/ccb/fxml/modal/add_consumption.fxml"));
            Parent root = loader.load();
            
            com.ccb.controller.modal.AddConsumptionController controller = loader.getController();
            controller.setOnSaveCallback(this::loadData);
            
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initStyle(StageStyle.TRANSPARENT);
            dialog.setScene(new Scene(root));
            dialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadData() {
        showLoading();
        Task<List<StationConsumptionRecord>> task = new Task<>() {
            @Override
            protected List<StationConsumptionRecord> call() throws Exception {
                StationConsumptionSheetService service = new StationConsumptionSheetService();
                return service.readRecords();
            }

            @Override
            protected void succeeded() {
                allRecords.clear();
                allRecords.addAll(getValue() == null ? List.of() : getValue());
                currentPage = 0;
                updateFilterOptions();
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
        errorMessage.setText("Error loading Station Consumption data: " + ex.getMessage());
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

    private void updateFilterOptions() {
        // Update station filter
        Set<String> stations = new LinkedHashSet<>();
        for (StationConsumptionRecord record : allRecords) {
            if (!record.getStation().isEmpty()) {
                stations.add(record.getStation());
            }
        }

        if (stationFilterComboBox != null) {
            stationFilterComboBox.getItems().clear();
            stationFilterComboBox.getItems().add("All Stations");
            for (String station : stations) {
                stationFilterComboBox.getItems().add(station);
            }
            stationFilterComboBox.getSelectionModel().selectFirst();
        }

        // Update month filter
        Set<String> months = new LinkedHashSet<>();
        for (StationConsumptionRecord record : allRecords) {
            String monthKey = record.getMonthKey();
            if (!monthKey.isEmpty()) {
                months.add(monthKey);
            }
        }

        if (monthFilterComboBox != null) {
            monthFilterComboBox.getItems().clear();
            monthFilterComboBox.getItems().add("All Months");
            List<String> sortedMonths = new ArrayList<>(months);
            sortedMonths.sort(Collections.reverseOrder());
            for (String month : sortedMonths) {
                monthFilterComboBox.getItems().add(month);
            }
            monthFilterComboBox.getSelectionModel().selectFirst();
        }
    }

    private void render() {
        filteredRecords.clear();
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase();
        String selectedStation = stationFilterComboBox == null ? "All Stations" : stationFilterComboBox.getSelectionModel().getSelectedItem();
        String selectedMonth = monthFilterComboBox == null ? "All Months" : monthFilterComboBox.getSelectionModel().getSelectedItem();

        for (StationConsumptionRecord r : allRecords) {
            // Station filter
            if (selectedStation != null && !selectedStation.equals("All Stations")) {
                if (!r.getStation().equals(selectedStation)) {
                    continue;
                }
            }

            // Month filter
            if (selectedMonth != null && !selectedMonth.equals("All Months")) {
                if (!r.getMonthKey().equals(selectedMonth)) {
                    continue;
                }
            }

            // Search filter
            if (!query.isEmpty()) {
                String dateStr = r.getDateString().toLowerCase();
                String materialStr = r.getMaterialName().toLowerCase();
                String stationStr = r.getStation().toLowerCase();
                String uomStr = r.getUom().toLowerCase();

                if (!dateStr.contains(query) && !materialStr.contains(query) && 
                    !stationStr.contains(query) && !uomStr.contains(query)) {
                    continue;
                }
            }

            filteredRecords.add(r);
        }

        if (filteredRecords.isEmpty()) {
            showEmpty();
            return;
        }

        StationConsumptionSummary summary = computeSummary(filteredRecords);
        renderStatCards(summary);
        renderChartCards(summary);
        renderTable();
        showData();
    }

    private StationConsumptionSummary computeSummary(List<StationConsumptionRecord> records) {
        double totalCost = records.stream().mapToDouble(StationConsumptionRecord::getTotalCost).sum();
        double totalQuantity = records.stream().mapToDouble(StationConsumptionRecord::getQuantity).sum();
        
        Map<String, Double> costByStation = records.stream()
            .collect(Collectors.groupingBy(
                StationConsumptionRecord::getStation,
                Collectors.summingDouble(StationConsumptionRecord::getTotalCost)
            ));
        
        Map<String, Double> costByMaterial = records.stream()
            .collect(Collectors.groupingBy(
                StationConsumptionRecord::getMaterialName,
                Collectors.summingDouble(StationConsumptionRecord::getTotalCost)
            ));

        String topStation = costByStation.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("");

        String topMaterial = costByMaterial.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("");

        return new StationConsumptionSummary(totalCost, totalQuantity, costByStation, costByMaterial, topStation, topMaterial, records.size());
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

        tableSectionCard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E5E7EB; -fx-border-width: 0.5; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 14;");
        
        Label tblTitle = new Label("Consumption Log");
        tblTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #1A2535;");
        
        Label tblSubtitle = new Label("Daily material consumption records per station");
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
        
        paginationHost.getChildren().addAll(paginationSummaryLabel, paginationSpacer, arrowControls);

        tableSectionCard.getChildren().addAll(
                tableHeaderBox,
                buildTableHeader(),
                tableRowsHost,
                new Separator(),
                paginationHost
        );

        pageContent.getChildren().addAll(
                buildSectionLabel("AT A GLANCE — STATION CONSUMPTION"),
                statCardsHost,
                buildSectionLabel("COST ANALYSIS — BY STATION AND MATERIAL"),
                chartCardsHost,
                buildSectionLabel("FULL CONSUMPTION LOG"),
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
        
        Label cellStation = buildCell("—", "");
        cellStation.setPrefWidth(80); cellStation.setMinWidth(80);
        
        Label cellMaterial = buildCell("—", "");
        cellMaterial.setPrefWidth(150); cellMaterial.setMinWidth(150);
        
        Label cellQty = buildCell("—", "");
        cellQty.setPrefWidth(70); cellQty.setMinWidth(70);
        
        Label cellUom = buildCell("—", "");
        cellUom.setPrefWidth(60); cellUom.setMinWidth(60);
        
        Label cellPrice = buildCell("—", "");
        cellPrice.setPrefWidth(80); cellPrice.setMinWidth(80);
        
        Label cellCost = buildCell("—", "");
        cellCost.setPrefWidth(90); cellCost.setMinWidth(90);

        row.getChildren().addAll(cellDate, cellStation, cellMaterial, cellQty, cellUom, cellPrice, cellCost);

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
                headerCell("STATION", 80),
                headerCell("MATERIAL", 150),
                headerCell("QTY", 70),
                headerCell("UOM", 60),
                headerCell("UNIT PRICE", 80),
                headerCell("TOTAL COST", 90)
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

    private void renderStatCards(StationConsumptionSummary summary) {
        statCardsHost.getChildren().clear();

        VBox card1 = buildStatCard(
                "TOTAL COST",
                String.format("₱%,.2f", summary.getTotalCost()),
                StyleConstants.BLUE,
                "total material cost",
                "green",
                "All stations"
        );
        
        VBox card2 = buildStatCard(
                "TOTAL QUANTITY",
                String.format("%.2f", summary.getTotalQuantity()),
                Color.web("#D97706"),
                "units consumed",
                "green",
                "All materials"
        );

        VBox card3 = buildStatCard(
                "TOP STATION",
                summary.getTopStation().isEmpty() ? "—" : summary.getTopStation(),
                StyleConstants.GREEN,
                "highest cost station",
                "green",
                "Most expensive"
        );

        VBox card4 = buildStatCard(
                "TOP MATERIAL",
                summary.getTopMaterial().isEmpty() ? "—" : summary.getTopMaterial(),
                StyleConstants.PURPLE,
                "highest cost material",
                "green",
                "Most consumed"
        );

        VBox card5 = buildStatCard(
                "TOTAL RECORDS",
                String.format("%,d", summary.getRecordCount()),
                Color.web("#6B7280"),
                "consumption entries",
                "green",
                "All time"
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

    private Label buildPill(String text, String bgStyle, String txtStyle) {
        Label pill = new Label(text);
        pill.setStyle("-fx-background-color: " + bgStyle + "; -fx-text-fill: " + txtStyle + "; -fx-font-size: 9px; -fx-font-weight: bold; -fx-padding: 4 8; -fx-background-radius: 4px;");
        return pill;
    }

    private void renderChartCards(StationConsumptionSummary summary) {
        chartCardsHost.getChildren().clear();

        // Card 1: Bar Chart - Cost by Station
        VBox barChartCard = new VBox(10);
        barChartCard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E5E7EB; -fx-border-width: 0.5; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 13 14;");
        
        HBox barHeader = new HBox(8);
        barHeader.setAlignment(Pos.TOP_LEFT);
        Region barStripe = new Region();
        barStripe.setStyle("-fx-background-color: #1F5FA6; -fx-pref-width: 3; -fx-min-width: 3; -fx-max-width: 3; -fx-pref-height: 24;");
        
        VBox barTitleBox = new VBox(2);
        Label barTitle = new Label("Cost by Station");
        barTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1A2535;");
        Label barDesc = new Label("Total material cost per station");
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

        BarChart.Series<String, Number> series = new BarChart.Series<>();
        for (Map.Entry<String, Double> entry : summary.getCostByStation().entrySet()) {
            series.getData().add(new BarChart.Data<>(entry.getKey(), entry.getValue()));
        }
        barChart.getData().add(series);

        barChartCard.getChildren().addAll(barHeader, barChart);

        // Card 2: Pie Chart - Cost by Material (Top 5)
        VBox pieChartCard = new VBox(10);
        pieChartCard.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E5E7EB; -fx-border-width: 0.5; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 13 14;");
        
        HBox pieHeader = new HBox(8);
        pieHeader.setAlignment(Pos.TOP_LEFT);
        Region pieStripe = new Region();
        pieStripe.setStyle("-fx-background-color: #C0392B; -fx-pref-width: 3; -fx-min-width: 3; -fx-max-width: 3; -fx-pref-height: 24;");
        
        VBox pieTitleBox = new VBox(2);
        Label pieTitle = new Label("Top Materials by Cost");
        pieTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1A2535;");
        Label pieDesc = new Label("Top 5 most expensive materials");
        pieDesc.setStyle("-fx-font-size: 10px; -fx-text-fill: #9CA3AF;");
        pieTitleBox.getChildren().addAll(pieTitle, pieDesc);
        pieHeader.getChildren().addAll(pieStripe, pieTitleBox);

        PieChart pieChart = new PieChart();
        pieChart.setLegendVisible(false);
        
        List<Map.Entry<String, Double>> topMaterials = summary.getCostByMaterial().entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(5)
            .collect(Collectors.toList());

        String[] colors = {"#1F5FA6", "#C0392B", "#E9B52D", "#10B981", "#8B5CF6"};
        int colorIdx = 0;
        for (Map.Entry<String, Double> entry : topMaterials) {
            PieChart.Data data = new PieChart.Data(entry.getKey(), entry.getValue());
            pieChart.getData().add(data);
            if (colorIdx < colors.length) {
                data.getNode().setStyle("-fx-pie-color: " + colors[colorIdx] + ";");
            }
            colorIdx++;
        }

        pieChartCard.getChildren().addAll(pieHeader, pieChart);

        chartCardsHost.add(barChartCard, 0, 0);
        chartCardsHost.add(pieChartCard, 1, 0);
    }

    private void renderTable() {
        int startIdx = currentPage * ROWS_PER_PAGE;
        int endIdx = Math.min(startIdx + ROWS_PER_PAGE, filteredRecords.size());

        for (int i = 0; i < ROWS_PER_PAGE; i++) {
            HBox row = tableRows.get(i);
            int recordIdx = startIdx + i;
            
            if (recordIdx < filteredRecords.size()) {
                StationConsumptionRecord record = filteredRecords.get(recordIdx);
                updateTableRow(row, record);
                row.setVisible(true);
                row.setManaged(true);
            } else {
                row.setVisible(false);
                row.setManaged(false);
            }
        }

        paginationSummaryLabel.setText(String.format("Showing %d-%d of %d records", 
            startIdx + 1, Math.min(endIdx, filteredRecords.size()), filteredRecords.size()));
    }

    private void updateTableRow(HBox row, StationConsumptionRecord record) {
        Label cellDate = (Label) row.getChildren().get(0);
        Label cellStation = (Label) row.getChildren().get(1);
        Label cellMaterial = (Label) row.getChildren().get(2);
        Label cellQty = (Label) row.getChildren().get(3);
        Label cellUom = (Label) row.getChildren().get(4);
        Label cellPrice = (Label) row.getChildren().get(5);
        Label cellCost = (Label) row.getChildren().get(6);

        cellDate.setText(record.getDateString());
        cellStation.setText(record.getStation());
        cellMaterial.setText(record.getMaterialName());
        cellQty.setText(String.format("%.2f", record.getQuantity()));
        cellUom.setText(record.getUom());
        cellPrice.setText(String.format("₱%.2f", record.getUnitPrice()));
        cellCost.setText(String.format("₱%,.2f", record.getTotalCost()));
    }

    private Label buildCell(String text, String style) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 11px; -fx-text-fill: #1A2535;");
        return label;
    }

    private VBox buildSectionLabel(String text) {
        VBox vbox = new VBox(4);
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #9CA3AF; -fx-letter-spacing: 1px;");
        vbox.getChildren().add(label);
        return vbox;
    }

    static class StationConsumptionSummary {
        private final double totalCost;
        private final double totalQuantity;
        private final Map<String, Double> costByStation;
        private final Map<String, Double> costByMaterial;
        private final String topStation;
        private final String topMaterial;
        private final int recordCount;

        public StationConsumptionSummary(double totalCost, double totalQuantity, 
                                         Map<String, Double> costByStation, Map<String, Double> costByMaterial,
                                         String topStation, String topMaterial, int recordCount) {
            this.totalCost = totalCost;
            this.totalQuantity = totalQuantity;
            this.costByStation = costByStation;
            this.costByMaterial = costByMaterial;
            this.topStation = topStation;
            this.topMaterial = topMaterial;
            this.recordCount = recordCount;
        }

        public double getTotalCost() { return totalCost; }
        public double getTotalQuantity() { return totalQuantity; }
        public Map<String, Double> getCostByStation() { return costByStation; }
        public Map<String, Double> getCostByMaterial() { return costByMaterial; }
        public String getTopStation() { return topStation; }
        public String getTopMaterial() { return topMaterial; }
        public int getRecordCount() { return recordCount; }
    }
}
