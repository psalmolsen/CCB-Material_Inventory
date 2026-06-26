package com.ccb.controller.page;

import com.ccb.OringRecord;
import com.ccb.OringSheetService;
import javafx.application.Platform;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.shape.Circle;
import javafx.stage.Window;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.scene.layout.GridPane;

import java.net.URL;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class OringMonitoringController implements Initializable {

    private static final int ROWS_PER_PAGE = 5;
    private static final String MONTH_ALL = "ALL";

    @FXML private TextField searchField;
    @FXML private MenuButton monthFilterButton;
    @FXML private Button refreshButton;
    @FXML private Button addRecordButton;
    @FXML private VBox loadingState;
    @FXML private VBox errorState;
    @FXML private Label errorMessage;
    @FXML private Button retryButton;
    @FXML private VBox emptyState;
    @FXML private VBox dataState;

    private final VBox pageContent = new VBox(16);
    private final HBox summaryCardsHost = new HBox(10);
    private final HBox chartsRowHost = new HBox(10);
    private final TilePane spotlightGrid = new TilePane();
    private final VBox tableSection = new VBox(10);
    private final VBox tableRowsHost = new VBox(0);
    private final HBox paginationHost = new HBox(10);
    private final Label paginationSummary = new Label();
    private final HBox pageDotsHost = new HBox(6);

    private final Label valvesRepairedValue = new Label("0");
    private final Label valvesRepairedBadge = new Label("good");
    private final Label qcPassedValue = new Label("0");
    private final Label qcPassedTotal = new Label("/ 0");
    private final Label qcBadge = new Label("0.0% pass rate");
    private final Label rejectedValue = new Label("0");
    private final Label rejectedBadge = new Label("0.0% reject rate");

    private final Label barChartTitle = new Label("Repairs per day - good vs rejected");
    private final Label barChartDesc = new Label("Taller green bar = more good repairs. Red shows defects that day.");
    private final Label pieChartTitle = new Label("Overall result");
    private final Label pieChartDesc = new Label("Out of 0 valves repaired");
    private final Label pieGoodLegend = new Label("Good 0");
    private final Label pieRejectedLegend = new Label("Rejected 0");

    private final Label spotlightSubtitle = new Label("Each card = one valve source. Red border = needs attention.");

    private final List<OringRecord> allRecords = new ArrayList<>();
    private final List<OringRecord> filteredRecords = new ArrayList<>();
    private final LinkedHashSet<String> availableMonths = new LinkedHashSet<>();
    private String selectedMonthKey = MONTH_ALL;
    private String currentSheetTabName = "Sheet1";
    private int currentPage = 0;

    private BarChart<String, Number> barChart;
    private PieChart pieChart;
    private Runnable refreshCallback;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
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
        if (addRecordButton != null) {
            addRecordButton.setContentDisplay(ContentDisplay.LEFT);
            addRecordButton.setGraphic(createAddIcon());
            addRecordButton.setOnAction(e -> showAddRecordDialog());
        }
        loadData();
    }

    public void refresh() {
        loadData();
    }

    public void setRefreshCallback(Runnable refreshCallback) {
        this.refreshCallback = refreshCallback;
    }

    private void buildPageShell() {
        if (dataState == null) {
            return;
        }

        summaryCardsHost.setAlignment(Pos.TOP_LEFT);
        summaryCardsHost.getChildren().addAll(
                buildSummaryCard("HOW MANY VALVES DID WE FIX?", valvesRepairedValue, "valves repaired this month", valvesRepairedBadge, "#1F5FA6"),
                buildSummaryCard("HOW MANY O-RINGS PASSED QC?", buildQcValueNode(), "o-rings passed quality check", qcBadge, "#16A34A"),
                buildSummaryCard("HOW MANY WERE REJECTED?", rejectedValue, "o-rings rejected / defective", rejectedBadge, "#C0392B")
        );
        for (Node node : summaryCardsHost.getChildren()) {
            HBox.setHgrow(node, javafx.scene.layout.Priority.ALWAYS);
        }

        chartsRowHost.setAlignment(Pos.TOP_LEFT);
        chartsRowHost.getChildren().addAll(
                buildChartCard("#1F5FA6", barChartTitle, barChartDesc, createBarChartHolder()),
                buildChartCard("#C0392B", pieChartTitle, pieChartDesc, createPieChartHolder())
        );
        for (Node node : chartsRowHost.getChildren()) {
            HBox.setHgrow(node, javafx.scene.layout.Priority.ALWAYS);
        }

        spotlightGrid.setPrefColumns(3);
        spotlightGrid.setHgap(10);
        spotlightGrid.setVgap(10);
        spotlightGrid.setTileAlignment(Pos.TOP_LEFT);

        tableRowsHost.setSpacing(0);
        paginationHost.setAlignment(Pos.CENTER_LEFT);
        paginationHost.getChildren().addAll(paginationSummary, new Region(), buildPaginationControls());
        HBox.setHgrow(paginationHost.getChildren().get(1), javafx.scene.layout.Priority.ALWAYS);

        tableSection.getChildren().addAll(
                tableCardHeader("Repair Log"),
                new Label("Flagged rows (red left border) = entries with at least 1 rejection."),
                buildTableHeader(),
                tableRowsHost,
                new Separator(),
                paginationHost
        );
        tableSection.getStyleClass().add("oring-surface");
        ((Label) tableSection.getChildren().get(1)).getStyleClass().add("oring-table-subtitle");

        pageContent.getChildren().addAll(
                sectionHeader("AT A GLANCE - WHAT HAPPENED THIS PERIOD?"),
                summaryCardsHost,
                sectionHeader("WHEN DID REPAIRS HAPPEN?"),
                chartsRowHost,
                sectionHeader("WHERE DID REJECTS COME FROM? - PROBLEM SPOTLIGHT"),
                spotlightSubtitle,
                spotlightGrid,
                sectionHeader("FULL REPAIR LOG - EVERY ENTRY"),
                tableSection
        );

        dataState.getChildren().setAll(pageContent);
    }

    private Node buildSummaryCard(String title, Node valueNode, String context, Label badge, String accentColor) {
        VBox card = new VBox(8);
        card.getStyleClass().add("oring-summary-card");
        Label question = new Label(title);
        question.getStyleClass().add("oring-question-label");

        if (valueNode instanceof Label valueLabel) {
            valueLabel.getStyleClass().add("oring-big-value");
            valueLabel.setStyle("-fx-text-fill: " + accentColor + ";");
        }

        Label contextLabel = new Label(context);
        contextLabel.getStyleClass().add("oring-context-label");

        badge.getStyleClass().add("oring-badge");
        card.getChildren().addAll(question, valueNode, contextLabel, badge);
        return card;
    }

    private Node buildQcValueNode() {
        HBox row = new HBox(4);
        row.setAlignment(Pos.BASELINE_LEFT);
        qcPassedValue.getStyleClass().add("oring-big-value");
        qcPassedValue.setStyle("-fx-text-fill: #16A34A;");
        qcPassedTotal.getStyleClass().add("oring-muted-total");
        row.getChildren().addAll(qcPassedValue, qcPassedTotal);
        return row;
    }

    private Node buildChartCard(String accent, Label title, Label description, Node content) {
        VBox card = new VBox(10);
        card.getStyleClass().add("oring-surface");

        Region stripe = new Region();
        stripe.setPrefWidth(3);
        stripe.setMinWidth(3);
        stripe.setMaxWidth(3);
        stripe.setStyle("-fx-background-color: " + accent + ";");

        VBox header = new VBox(4);
        title.getStyleClass().add("oring-card-title");
        description.getStyleClass().add("oring-card-desc");
        header.getChildren().addAll(title, description);

        HBox headingRow = new HBox(10, stripe, header);
        headingRow.setAlignment(Pos.TOP_LEFT);
        card.getChildren().addAll(headingRow, content);
        return card;
    }

    private Node createBarChartHolder() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setTickLabelGap(10);
        yAxis.setTickLabelGap(8);
        barChart = new BarChart<>(xAxis, yAxis);
        barChart.setLegendVisible(true);
        barChart.setAnimated(false);
        barChart.setCategoryGap(20);
        barChart.setBarGap(6);
        barChart.setVerticalGridLinesVisible(false);
        barChart.setHorizontalGridLinesVisible(true);
        barChart.setMinHeight(220);
        barChart.setPrefHeight(220);
        barChart.getStyleClass().add("oring-bar-chart");
        return barChart;
    }

    private Node createPieChartHolder() {
        pieChart = new PieChart();
        pieChart.setLegendVisible(false);
        pieChart.setLabelsVisible(false);
        pieChart.setAnimated(false);
        pieChart.setStartAngle(90);
        pieChart.getStyleClass().add("oring-pie-chart");

        Circle hole = new Circle(70);
        hole.setFill(javafx.scene.paint.Color.WHITE);
        hole.setMouseTransparent(true);

        StackPane chartWrap = new StackPane(pieChart, hole);
        chartWrap.setMinHeight(220);
        chartWrap.setPrefHeight(220);

        HBox legend = new HBox(14, legendItem("#16A34A", pieGoodLegend), legendItem("#C0392B", pieRejectedLegend));
        legend.setAlignment(Pos.CENTER_LEFT);

        return new VBox(10, chartWrap, legend);
    }

    private Node legendItem(String color, Label label) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        Region dot = new Region();
        dot.setPrefSize(10, 10);
        dot.setMinSize(10, 10);
        dot.setMaxSize(10, 10);
        dot.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 999;");
        label.getStyleClass().add("oring-legend-label");
        row.getChildren().addAll(dot, label);
        return row;
    }

    private Label sectionHeader(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("oring-section-label");
        return label;
    }

    private HBox tableCardHeader(String title) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label(title);
        label.getStyleClass().add("oring-table-title");
        row.getChildren().add(label);
        return row;
    }

    private HBox buildTableHeader() {
        HBox header = new HBox(8);
        header.getStyleClass().add("oring-table-header");
        header.getChildren().addAll(
                headerCell("DATE", 94),
                headerCell("TIME SLOT", 110),
                headerCell("VALVE CAME FROM", 140),
                headerCell("REPAIRED", 86),
                headerCell("INSTALLED TO", 118),
                headerCell("GOOD", 64),
                headerCell("REJECTED", 78),
                headerCell("REMARKS", 220)
        );
        for (Node node : header.getChildren()) {
            HBox.setHgrow(node, javafx.scene.layout.Priority.ALWAYS);
        }
        return header;
    }

    private Label headerCell(String text, double width) {
        Label label = new Label(text);
        label.getStyleClass().add("oring-table-head-cell");
        label.setMinWidth(width);
        label.setPrefWidth(width);
        return label;
    }

    private Node buildPaginationControls() {
        Button prev = paginationButton("\u2039");
        Button next = paginationButton("\u203A");
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
        return new HBox(8, prev, pageDotsHost, next);
    }

    private Button paginationButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("oring-page-button");
        return button;
    }

    private void loadData() {
        showLoading();
        Task<List<OringRecord>> task = new Task<>() {
            @Override
            protected List<OringRecord> call() throws Exception {
                OringSheetService service = new OringSheetService();
                currentSheetTabName = service.resolvePrimaryTabName();
                return service.readRecords();
            }
        };
        task.setOnSucceeded(e -> {
            allRecords.clear();
            allRecords.addAll(task.getValue() == null ? List.of() : task.getValue());
            currentPage = 0;
            updateMonthOptions();
            render();
        });
        task.setOnFailed(e -> showError(task.getException()));
        daemon(task);
    }

    private void updateMonthOptions() {
        availableMonths.clear();
        for (OringRecord record : allRecords) {
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
        if (selectedMonthKey == null || selectedMonthKey.isBlank() || (!MONTH_ALL.equals(selectedMonthKey) && !availableMonths.contains(selectedMonthKey))) {
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

    private void render() {
        filteredRecords.clear();
        filteredRecords.addAll(filterRecords());
        if (filteredRecords.isEmpty()) {
            showEmpty();
            return;
        }

        updateVisibility(false, false, false, true);
        renderSummary();
        renderCharts();
        renderSpotlight();
        renderTable();
    }

    private List<OringRecord> filterRecords() {
        String query = searchField == null || searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        return allRecords.stream()
                .filter(record -> MONTH_ALL.equals(selectedMonthKey) || selectedMonthMatches(record))
                .filter(record -> query.isBlank() || recordMatches(record, query))
                .collect(Collectors.toList());
    }

    private boolean selectedMonthMatches(OringRecord record) {
        return record.getParsedDate() != null && selectedMonthKey != null && selectedMonthKey.equals(record.getMonthKey());
    }

    private boolean recordMatches(OringRecord record, String query) {
        return contains(record.getDate(), query)
                || contains(record.getTimeSlot(), query)
                || contains(record.getValveCameFrom(), query)
                || contains(record.getInstalledTo(), query)
                || contains(record.getRemarks(), query)
                || String.valueOf(record.getValvesRepaired()).contains(query)
                || String.valueOf(record.getGood()).contains(query)
                || String.valueOf(record.getRejected()).contains(query);
    }

    private boolean contains(String value, String query) {
        return value != null && !value.isBlank() && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void renderSummary() {
        int valvesRepaired = filteredRecords.stream().mapToInt(OringRecord::getValvesRepaired).sum();
        int good = filteredRecords.stream().mapToInt(OringRecord::getGood).sum();
        int rejected = filteredRecords.stream().mapToInt(OringRecord::getRejected).sum();
        double passRate = valvesRepaired <= 0 ? 0.0 : (good * 100.0) / valvesRepaired;
        double rejectRate = valvesRepaired <= 0 ? 0.0 : (rejected * 100.0) / valvesRepaired;

        valvesRepairedValue.setText(formatNumber(valvesRepaired));
        valvesRepairedBadge.setText(passRate >= 95 ? "Great month" : "Check trend");
        valvesRepairedBadge.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #16A34A;");
        qcPassedValue.setText(formatNumber(good));
        qcPassedTotal.setText("/ " + formatNumber(valvesRepaired));
        qcBadge.setText(String.format(Locale.US, "%.1f%% pass rate - %s", passRate, passRate >= 90 ? "good" : "watch this"));
        qcBadge.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #16A34A;");
        rejectedValue.setText(formatNumber(rejected));
        rejectedBadge.setText(String.format(Locale.US, "%.1f%% reject rate", rejectRate));
        if (rejectRate < 10) {
            rejectedBadge.setStyle("-fx-background-color: #FEF3C7; -fx-text-fill: #D97706;");
        } else {
            rejectedBadge.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #C0392B;");
        }
    }

    private void renderCharts() {
        Map<String, int[]> byDate = new LinkedHashMap<>();
        List<OringRecord> sorted = new ArrayList<>(filteredRecords);
        sorted.sort(Comparator.comparing((OringRecord r) -> r.getParsedDate() == null ? LocalDate.MAX : r.getParsedDate())
                .thenComparing(OringRecord::getDate));

        for (OringRecord record : sorted) {
            String label = record.getDate().isBlank() ? "Unknown" : record.getDate();
            byDate.computeIfAbsent(label, k -> new int[2]);
            byDate.get(label)[0] += record.getGood();
            byDate.get(label)[1] += record.getRejected();
        }

        barChart.getData().clear();
        CategoryAxis xAxis = (CategoryAxis) barChart.getXAxis();
        NumberAxis yAxis = (NumberAxis) barChart.getYAxis();
        xAxis.setCategories(javafx.collections.FXCollections.observableArrayList(byDate.keySet()));
        yAxis.setLowerBound(0);

        BarChart.Series<String, Number> goodSeries = new BarChart.Series<>();
        goodSeries.setName("Good");
        BarChart.Series<String, Number> rejectedSeries = new BarChart.Series<>();
        rejectedSeries.setName("Rejected");
        for (Map.Entry<String, int[]> entry : byDate.entrySet()) {
            goodSeries.getData().add(new BarChart.Data<>(entry.getKey(), entry.getValue()[0]));
            rejectedSeries.getData().add(new BarChart.Data<>(entry.getKey(), entry.getValue()[1]));
        }
        barChart.getData().addAll(goodSeries, rejectedSeries);
        styleBarSeries(goodSeries, "#1F5FA6");
        styleBarSeries(rejectedSeries, "#C0392B");

        int good = filteredRecords.stream().mapToInt(OringRecord::getGood).sum();
        int rejected = filteredRecords.stream().mapToInt(OringRecord::getRejected).sum();
        int total = good + rejected;
        pieChart.getData().setAll(
                new PieChart.Data("Good", Math.max(0, good)),
                new PieChart.Data("Rejected", Math.max(0, rejected))
        );
        pieGoodLegend.setText("Good " + formatNumber(good));
        pieRejectedLegend.setText("Rejected " + formatNumber(rejected));
        pieChartDesc.setText("Out of " + formatNumber(total) + " valves repaired");
        Platform.runLater(() -> {
            int index = 0;
            for (PieChart.Data data : pieChart.getData()) {
                Node node = data.getNode();
                if (node != null) {
                    node.setStyle("-fx-pie-color: " + (index == 0 ? "#16A34A" : "#C0392B") + ";");
                }
                index++;
            }
        });
    }

    private void styleBarSeries(BarChart.Series<String, Number> series, String color) {
        for (BarChart.Data<String, Number> data : series.getData()) {
            data.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    newNode.setStyle("-fx-bar-fill: " + color + ";");
                }
            });
        }
    }

    private void renderSpotlight() {
        spotlightGrid.getChildren().clear();
        Map<String, List<OringRecord>> bySource = filteredRecords.stream()
                .collect(Collectors.groupingBy(record -> normalize(record.getValveCameFrom()), LinkedHashMap::new, Collectors.toList()));

        List<SourceSummary> summaries = new ArrayList<>();
        for (Map.Entry<String, List<OringRecord>> entry : bySource.entrySet()) {
            int good = entry.getValue().stream().mapToInt(OringRecord::getGood).sum();
            int rejected = entry.getValue().stream().mapToInt(OringRecord::getRejected).sum();
            int total = good + rejected;
            double rejectRate = total <= 0 ? 0.0 : (rejected * 100.0) / total;
            summaries.add(new SourceSummary(entry.getKey(), good, rejected, rejectRate));
        }

        summaries.sort(Comparator.comparingInt(SourceSummary::rank).thenComparing(SourceSummary::source));
        for (SourceSummary summary : summaries) {
            spotlightGrid.getChildren().add(buildSourceCard(summary));
        }
    }

    private Node buildSourceCard(SourceSummary summary) {
        String bg;
        String border;
        String badgeText;
        String accent;
        if (summary.rejectRate() > 30) {
            bg = "#FFF1F2";
            border = "#FECDD3";
            badgeText = "High reject - check source";
            accent = "#C0392B";
        } else if (summary.rejectRate() >= 15) {
            bg = "#FFFBEB";
            border = "#FDE68A";
            badgeText = "Monitor closely";
            accent = "#D97706";
        } else {
            bg = "#F0FDF4";
            border = "#BBF7D0";
            badgeText = "Clean - no rejects";
            accent = "#16A34A";
        }

        VBox card = new VBox(8);
        card.getStyleClass().add("oring-source-card");
        card.setStyle("-fx-background-color: " + bg + "; -fx-border-color: " + border + ";");

        Label source = new Label(summary.source());
        source.getStyleClass().add("oring-source-name");

        HBox goodRow = rowMetric("Good", formatNumber(summary.good()), "#16A34A");
        HBox rejectedRow = rowMetric("Rejected", formatNumber(summary.rejected()), "#C0392B");
        HBox rateRow = rowMetric("Reject rate", String.format(Locale.US, "%.1f%%", summary.rejectRate()), accent);

        Label badge = new Label(badgeText);
        badge.getStyleClass().add("oring-status-badge");
        badge.setStyle("-fx-background-color: " + badgeFill(accent) + "; -fx-text-fill: " + accent + ";");

        card.getChildren().addAll(source, goodRow, rejectedRow, rateRow, badge);
        return card;
    }

    private String badgeFill(String accent) {
        if ("#C0392B".equals(accent)) {
            return "#FEE2E2";
        }
        if ("#D97706".equals(accent)) {
            return "#FEF3C7";
        }
        return "#DCFCE7";
    }

    private HBox rowMetric(String label, String value, String color) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        Label key = new Label(label);
        key.getStyleClass().add("oring-metric-key");
        Label val = new Label(value);
        val.getStyleClass().add("oring-metric-value");
        val.setStyle("-fx-text-fill: " + color + ";");
        row.getChildren().addAll(key, val);
        return row;
    }

    private void renderTable() {
        tableRowsHost.getChildren().clear();
        int total = filteredRecords.size();
        int pageCount = Math.max(1, (int) Math.ceil(total / (double) ROWS_PER_PAGE));
        if (currentPage >= pageCount) {
            currentPage = pageCount - 1;
        }
        int start = currentPage * ROWS_PER_PAGE;
        int end = Math.min(start + ROWS_PER_PAGE, total);
        List<OringRecord> pageRecords = filteredRecords.subList(Math.min(start, total), Math.min(end, total));

        for (OringRecord record : pageRecords) {
            tableRowsHost.getChildren().add(buildTableRow(record));
        }

        paginationSummary.setText("Showing " + (total == 0 ? 0 : start + 1) + "-" + end + " of " + total + " records");
        updatePageDots(pageCount);
    }

    private Node buildTableRow(OringRecord record) {
        HBox row = new HBox(8);
        row.getStyleClass().add("oring-table-row");
        if (record.isFlagged()) {
            row.getStyleClass().add("oring-table-row-flagged");
        }
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinHeight(36);
        row.setPrefHeight(36);
        row.setMaxHeight(36);

        row.getChildren().addAll(
                cellLabel(record.getDate(), "oring-cell-date", 94),
                pillLabel(record.getTimeSlot(), "oring-pill-blue", 110),
                pillLabel(record.getValveCameFrom(), "oring-pill-gray", 140),
                plainMetric(formatNumber(record.getValvesRepaired()), "oring-cell-strong", 86),
                pillLabel(record.getInstalledTo(), "oring-pill-blue", 118),
                plainMetric(formatNumber(record.getGood()), "oring-cell-good", 64),
                plainMetric(record.getRejected() > 0 ? formatNumber(record.getRejected()) : "\u2014",
                        record.getRejected() > 0 ? "oring-cell-bad" : "oring-cell-muted", 78),
                remarksLabel(record.getRemarks())
        );
        return row;
    }

    private Label cellLabel(String value, String styleClass, double width) {
        Label label = new Label(emptyOrDash(value));
        label.getStyleClass().add(styleClass);
        label.setMinWidth(width);
        label.setPrefWidth(width);
        return label;
    }

    private Label pillLabel(String value, String styleClass, double width) {
        Label label = new Label(emptyOrDash(value));
        label.getStyleClass().add(styleClass);
        label.setMinWidth(width);
        label.setPrefWidth(width);
        return label;
    }

    private Label plainMetric(String value, String styleClass, double width) {
        Label label = new Label(value);
        label.getStyleClass().add(styleClass);
        label.setMinWidth(width);
        label.setPrefWidth(width);
        return label;
    }

    private Label remarksLabel(String remarks) {
        Label label = new Label(emptyOrDash(remarks));
        label.getStyleClass().add("oring-cell-remarks");
        label.setWrapText(true);
        label.setMinWidth(220);
        label.setPrefWidth(220);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private void updatePageDots(int pageCount) {
        pageDotsHost.getChildren().clear();
        for (int i = 0; i < pageCount; i++) {
            int pageIndex = i;
            Region dot = new Region();
            dot.getStyleClass().add("oring-page-dot");
            if (pageIndex == currentPage) {
                dot.getStyleClass().add("active");
            }
            dot.setOnMouseClicked(e -> {
                currentPage = pageIndex;
                renderTable();
            });
            pageDotsHost.getChildren().add(dot);
        }
    }

    private void showLoading() {
        updateVisibility(true, false, false, false);
    }

    private void showError(Throwable throwable) {
        String message = throwable == null ? "Unable to load O-Ring data." : throwable.getMessage();
        if (message == null || message.isBlank()) {
            message = "Unable to load O-Ring data.";
        }
        if (errorMessage != null) {
            errorMessage.setText(message);
        }
        updateVisibility(false, true, false, false);
    }

    private void showEmpty() {
        updateVisibility(false, false, true, false);
    }

    private void updateVisibility(boolean loading, boolean error, boolean empty, boolean dataVisible) {
        setStateVisible(loadingState, loading);
        setStateVisible(errorState, error);
        setStateVisible(emptyState, empty);
        setStateVisible(dataState, dataVisible);
    }

    private void setStateVisible(VBox node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private String emptyOrDash(String value) {
        return value == null || value.isBlank() ? "\u2014" : value;
    }

    private String formatNumber(int value) {
        return String.format(Locale.US, "%,d", value);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void daemon(Task<?> task) {
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    private Node createAddIcon() {
        StackPane square = new StackPane();
        square.setPrefSize(18, 18);
        square.setMinSize(18, 18);
        square.setMaxSize(18, 18);
        square.setStyle("-fx-background-color:#E9B52D;-fx-background-radius:5;");
        Label plus = new Label("+");
        plus.setStyle("-fx-text-fill:#1B2A3B;-fx-font-size:12px;-fx-font-weight:bold;");
        square.getChildren().add(plus);
        return square;
    }

    private void showAddRecordDialog() {
        Window owner = resolveOwnerWindow();
        if (owner == null) {
            return;
        }

        Node ownerRoot = owner.getScene() == null ? null : owner.getScene().getRoot();
        if (ownerRoot != null) {
            ownerRoot.setEffect(new GaussianBlur(4));
        }

        Stage dialog = new Stage(StageStyle.TRANSPARENT);
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);

        VBox card = new VBox(0);
        card.setPrefWidth(480);
        card.setMaxWidth(480);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-border-color: #E5E7EB; -fx-border-radius: 14; -fx-border-width: 1; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 60, 0, 0, 20);");

        VBox header = new VBox(4);
        header.setStyle("-fx-padding: 18 20 16 20; -fx-border-color: transparent transparent #F0F2F5 transparent; -fx-border-width: 0 0 1 0;");
        HBox headerRow = new HBox(10);
        headerRow.setAlignment(Pos.TOP_LEFT);
        Region stripe = new Region();
        stripe.setPrefSize(4, 20);
        stripe.setStyle("-fx-background-color: #E9B52D; -fx-background-radius: 2;");
        VBox headerText = new VBox(2);
        Label title = new Label("Add O-Ring Repair Record");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1A2535;");
        headerText.getChildren().add(title);
        HBox.setHgrow(headerText, javafx.scene.layout.Priority.ALWAYS);
        Button closeBtn = new Button("X");
        closeBtn.setStyle("-fx-min-width:28;-fx-min-height:28;-fx-pref-width:28;-fx-pref-height:28;-fx-background-color:#F9FAFB;-fx-border-color:#E5E7EB;-fx-border-radius:7;-fx-background-radius:7;-fx-text-fill:#6B7280;-fx-font-weight:bold;-fx-cursor:hand;");
        headerRow.getChildren().addAll(stripe, headerText, closeBtn);
        header.getChildren().add(headerRow);

        VBox body = new VBox(14);
        body.setStyle("-fx-padding: 18 20;");

        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.setPrefWidth(Double.MAX_VALUE);
        TextField timeSlot = textInput("Type time manually");

        TextField valveFromField = textInput("e.g. EQUI JO#119");
        TextField installedToField = textInput("e.g. Akxel, EQUI");
        TextField repairedField = numericInput("0");
        TextField goodField = numericInput("0");
        TextField rejectedField = numericInput("0");
        TextArea remarksField = new TextArea();
        remarksField.setPrefRowCount(3);
        remarksField.setWrapText(true);
        remarksField.setPromptText("Optional notes, technician name, observations...");
        remarksField.setStyle(inputStyle());

        Label dateHint = requiredHint();
        Label valveHint = requiredHint();
        Label installedHint = requiredHint();
        Label repairedHint = requiredHint();
        Label goodHint = requiredHint();
        Label warningLabel = new Label("⚠ Good + Rejected exceeds total valves repaired. Please check your numbers.");
        warningLabel.setVisible(false);
        warningLabel.setManaged(false);
        warningLabel.setStyle("-fx-background-color:#FEF3C7;-fx-border-color:#FDE68A;-fx-border-radius:7;-fx-background-radius:7;-fx-padding:6 10;-fx-text-fill:#D97706;-fx-font-size:10px;");
        Label saveError = new Label("Failed to save. Check your connection and try again.");
        saveError.setVisible(false);
        saveError.setManaged(false);
        saveError.setStyle("-fx-background-color:#FEE2E2;-fx-border-color:#FECDD3;-fx-border-radius:7;-fx-background-radius:7;-fx-padding:8 12;-fx-text-fill:#C0392B;-fx-font-size:10px;");

        Label repairedValue = previewValue("0", "#1F5FA6");
        Label goodValue = previewValue("0", "#16A34A");
        Label rejectedValuePreview = previewValue("0", "#C0392B");
        Label rejectRateValue = previewValue("0.0%", "#16A34A");

        Runnable updatePreview = () -> {
            int repaired = parseNonNegative(repairedField.getText());
            int good = parseNonNegative(goodField.getText());
            int rejected = parseNonNegative(rejectedField.getText());
            double rate = (good + rejected) <= 0 ? 0.0 : (rejected * 100.0) / (good + rejected);
            repairedValue.setText(formatNumber(repaired));
            goodValue.setText(formatNumber(good));
            rejectedValuePreview.setText(formatNumber(rejected));
            rejectRateValue.setText(String.format(Locale.US, "%.1f%%", rate));
            rejectRateValue.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:" + (rate == 0 ? "#16A34A" : rate < 10 ? "#D97706" : "#C0392B") + ";");
            boolean overTotal = repaired > 0 && (good + rejected) > repaired;
            warningLabel.setVisible(overTotal);
            warningLabel.setManaged(overTotal);
        };

        repairedField.textProperty().addListener((obs, oldVal, newVal) -> {
            hideFieldError(repairedField, repairedHint);
            updatePreview.run();
        });
        goodField.textProperty().addListener((obs, oldVal, newVal) -> {
            hideFieldError(goodField, goodHint);
            updatePreview.run();
        });
        rejectedField.textProperty().addListener((obs, oldVal, newVal) -> updatePreview.run());
        datePicker.valueProperty().addListener((obs, oldVal, newVal) -> hideFieldError(datePicker.getEditor(), dateHint));
        valveFromField.textProperty().addListener((obs, oldVal, newVal) -> hideFieldError(valveFromField, valveHint));
        installedToField.textProperty().addListener((obs, oldVal, newVal) -> hideFieldError(installedToField, installedHint));
        updatePreview.run();

        GridPane row1 = twoColumnRow(dateBlock(datePicker, dateHint), timeBlock(timeSlot));
        GridPane row2 = twoColumnRow(textBlock("VALVE CAME FROM", valveFromField, valveHint), textBlock("INSTALLED TO", installedToField, installedHint));
        GridPane row3 = threeColumnRow(integerBlock("VALVES REPAIRED", repairedField, "Total valves fixed", repairedHint),
                integerBlock("GOOD", goodField, "Passed QC", goodHint),
                integerBlock("REJECTED", rejectedField, "Defective", null));

        VBox qcPreview = new VBox(0);
        qcPreview.setStyle("-fx-background-color:#F9FAFB;-fx-border-color:#E5E7EB;-fx-border-radius:9;-fx-background-radius:9;-fx-padding:10 14;");
        HBox previewRow = new HBox(10);
        previewRow.setAlignment(Pos.CENTER_LEFT);
        previewRow.getChildren().addAll(
                previewMetric("REPAIRED", repairedValue),
                divider(),
                previewMetric("GOOD", goodValue),
                divider(),
                previewMetric("REJECTED", rejectedValuePreview),
                divider(),
                previewMetric("REJECT RATE", rejectRateValue)
        );
        qcPreview.getChildren().add(previewRow);

        TextField[] numericFields = {repairedField, goodField, rejectedField};
        for (TextField field : numericFields) {
            field.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("\\d*") ? change : null));
        }

        VBox remarksBlock = fieldBlock("REMARKS", remarksField, null);
        body.getChildren().addAll(
                row1,
                row2,
                sectionDivider("QC COUNT"),
                row3,
                qcPreview,
                warningLabel,
                remarksBlock,
                saveError
        );

        Region greenDot = new Region();
        greenDot.setPrefSize(6, 6);
        greenDot.setStyle("-fx-background-color:#16A34A;-fx-background-radius:999;");
        HBox footerNote = new HBox(6, greenDot);
        footerNote.setAlignment(Pos.CENTER_LEFT);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color:white;-fx-border-color:#E5E7EB;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 16;-fx-text-fill:#6B7280;-fx-font-size:12px;-fx-font-weight:600;-fx-cursor:hand;");
        Button saveBtn = new Button("Save Record →");
        saveBtn.setStyle("-fx-background-color:#1B2A3B;-fx-border-color:transparent;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 20;-fx-text-fill:white;-fx-font-size:12px;-fx-font-weight:bold;-fx-cursor:hand;");

        HBox footerButtons = new HBox(8, cancelBtn, saveBtn);
        footerButtons.setAlignment(Pos.CENTER_RIGHT);
        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, javafx.scene.layout.Priority.ALWAYS);
        HBox footer = new HBox(10, footerNote, footerSpacer, footerButtons);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setStyle("-fx-padding:14 20; -fx-border-color:#F0F2F5 transparent transparent transparent; -fx-border-width:1 0 0 0; -fx-background-color:#F9FAFB;");

        VBox shell = new VBox(0, header, body, footer);
        shell.setMaxWidth(480);
        shell.setStyle("-fx-background-color:white;");

        StackPane overlay = new StackPane(shell);
        overlay.setStyle("-fx-background-color: rgba(10,20,35,0.55); -fx-padding: 28;");

        Scene scene = new Scene(overlay);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.setWidth(480);
        dialog.setHeight(700);
        dialog.setX(owner.getX() + (owner.getWidth() - 480) / 2);
        dialog.setY(owner.getY() + (owner.getHeight() - 700) / 2);

        Runnable cleanup = () -> {
            if (ownerRoot != null) {
                ownerRoot.setEffect(null);
            }
        };
        dialog.setOnHidden(e -> cleanup.run());

        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                dialog.close();
                event.consume();
            }
        });

        closeBtn.setOnAction(e -> dialog.close());
        cancelBtn.setOnAction(e -> dialog.close());

        saveBtn.setOnAction(e -> {
            boolean valid = true;
            saveError.setVisible(false);
            saveError.setManaged(false);

            valid &= validateRequired(datePicker.getEditor(), dateHint, datePicker.getValue() != null);
            valid &= validateRequired(valveFromField, valveHint, !valveFromField.getText().trim().isBlank());
            valid &= validateRequired(installedToField, installedHint, !installedToField.getText().trim().isBlank());
            valid &= validateRequired(repairedField, repairedHint, !repairedField.getText().trim().isBlank());
            valid &= validateRequired(goodField, goodHint, !goodField.getText().trim().isBlank());
            if (!valid) {
                return;
            }

            int repaired = parseNonNegative(repairedField.getText());
            int good = parseNonNegative(goodField.getText());
            int rejected = parseNonNegative(rejectedField.getText());
            if (good + rejected > repaired) {
                warningLabel.setVisible(true);
                warningLabel.setManaged(true);
                return;
            }

            saveBtn.setDisable(true);
            String originalText = saveBtn.getText();
            saveBtn.setText("Saving...");

            Task<Void> saveTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    OringSheetService service = new OringSheetService();
                    LocalDate date = datePicker.getValue();
                    String time = timeSlot.getText();
                    List<Object> row = List.of(
                            date == null ? "" : date.format(DateTimeFormatter.ofPattern("MM/dd/yy")),
                            time == null ? "" : time,
                            valveFromField.getText().trim(),
                            repaired,
                            installedToField.getText().trim(),
                            good,
                            rejected,
                            remarksField.getText() == null ? "" : remarksField.getText().trim()
                    );
                    service.appendRecord(resolveSheetTabName(), row);
                    return null;
                }
            };
            saveTask.setOnSucceeded(ev -> {
                dialog.close();
                if (refreshCallback != null) {
                    refreshCallback.run();
                } else {
                    refresh();
                }
                showToast("Record saved to Google Sheets ✓");
            });
            saveTask.setOnFailed(ev -> {
                saveBtn.setDisable(false);
                saveBtn.setText(originalText);
                saveError.setVisible(true);
                saveError.setManaged(true);
            });
            daemon(saveTask);
        });

        dialog.show();
    }

    private Window resolveOwnerWindow() {
        if (searchField != null && searchField.getScene() != null) {
            return searchField.getScene().getWindow();
        }
        if (refreshButton != null && refreshButton.getScene() != null) {
            return refreshButton.getScene().getWindow();
        }
        return null;
    }

    private String resolveSheetTabName() {
        return currentSheetTabName == null || currentSheetTabName.isBlank() ? "Sheet1" : currentSheetTabName;
    }

    private TextField textInput(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setStyle(inputStyle());
        return field;
    }

    private TextField numericInput(String initial) {
        TextField field = new TextField(initial);
        field.setStyle(inputStyle());
        field.setAlignment(Pos.CENTER_LEFT);
        return field;
    }

    private String inputStyle() {
        return "-fx-background-color:#F9FAFB;-fx-border-color:#E5E7EB;-fx-border-radius:8;-fx-background-radius:8;-fx-padding:8 10;-fx-font-size:12px;-fx-text-fill:#1A2535;";
    }

    private Label requiredHint() {
        Label hint = new Label("This field is required");
        hint.setStyle("-fx-font-size:9px;-fx-text-fill:#C0392B;");
        hint.setVisible(false);
        hint.setManaged(false);
        return hint;
    }

    private VBox fieldBlock(String labelText, Node field, Label hint) {
        VBox block = new VBox(6);
        Label label = new Label(labelText);
        label.setStyle("-fx-font-size:10px;-fx-font-weight:bold;-fx-text-fill:#6B7280; -fx-letter-spacing:1px;");
        block.getChildren().addAll(label, field);
        if (hint != null) {
            block.getChildren().add(hint);
        }
        return block;
    }

    private GridPane twoColumnRow(VBox left, VBox right) {
        GridPane row = new GridPane();
        row.setHgap(12);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(50);
        c1.setHgrow(Priority.ALWAYS);
        c1.setFillWidth(true);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(50);
        c2.setHgrow(Priority.ALWAYS);
        c2.setFillWidth(true);
        row.getColumnConstraints().addAll(c1, c2);
        row.add(left, 0, 0);
        row.add(right, 1, 0);
        left.setMaxWidth(Double.MAX_VALUE);
        right.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    private GridPane threeColumnRow(VBox first, VBox second, VBox third) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.add(first, 0, 0);
        grid.add(second, 1, 0);
        grid.add(third, 2, 0);
        GridPane.setHgrow(first, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setHgrow(second, javafx.scene.layout.Priority.ALWAYS);
        GridPane.setHgrow(third, javafx.scene.layout.Priority.ALWAYS);
        return grid;
    }

    private VBox dateBlock(DatePicker picker, Label hint) {
        VBox block = new VBox(6);
        Label label = new Label("DATE *");
        label.setStyle("-fx-font-size:10px;-fx-font-weight:bold;-fx-text-fill:#6B7280; -fx-letter-spacing:1px;");
        picker.setStyle(inputStyle());
        picker.setMaxWidth(Double.MAX_VALUE);
        picker.setPrefWidth(Double.MAX_VALUE);
        block.getChildren().addAll(label, picker, hint);
        return block;
    }

    private VBox timeBlock(TextField field) {
        VBox block = new VBox(6);
        Label label = new Label("TIME SLOT");
        label.setStyle("-fx-font-size:10px;-fx-font-weight:bold;-fx-text-fill:#6B7280; -fx-letter-spacing:1px;");
        field.setStyle(inputStyle());
        field.setMaxWidth(Double.MAX_VALUE);
        field.setPrefWidth(Double.MAX_VALUE);
        block.getChildren().addAll(label, field);
        return block;
    }

    private VBox textBlock(String title, TextField field, Label hint) {
        return fieldBlock(title + " *", field, hint);
    }

    private VBox integerBlock(String title, TextField field, String note, Label hint) {
        VBox block = new VBox(6);
        Label label = new Label(title + " *");
        label.setStyle("-fx-font-size:10px;-fx-font-weight:bold;-fx-text-fill:#6B7280; -fx-letter-spacing:1px;");
        Label noteLabel = new Label(note);
        noteLabel.setStyle("-fx-font-size:9px;-fx-text-fill:#9CA3AF;");
        block.getChildren().addAll(label, field, noteLabel);
        if (hint != null) {
            block.getChildren().add(hint);
        }
        return block;
    }

    private VBox sectionDivider(String text) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER);
        Region left = new Region();
        left.setStyle("-fx-background-color:#F0F2F5;-fx-pref-height:1;");
        Region right = new Region();
        right.setStyle("-fx-background-color:#F0F2F5;-fx-pref-height:1;");
        Label label = new Label(text);
        label.setStyle("-fx-font-size:9px;-fx-font-weight:bold;-fx-text-fill:#9CA3AF; -fx-letter-spacing:1px;");
        HBox.setHgrow(left, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(right, javafx.scene.layout.Priority.ALWAYS);
        row.getChildren().addAll(left, label, right);
        VBox wrapper = new VBox(row);
        return wrapper;
    }

    private VBox previewMetric(String label, Label value) {
        VBox block = new VBox(2);
        Label l = new Label(label);
        l.setStyle("-fx-font-size:9px;-fx-font-weight:bold;-fx-text-fill:#9CA3AF; -fx-letter-spacing:1px;");
        block.setAlignment(Pos.CENTER);
        block.getChildren().addAll(l, value);
        return block;
    }

    private Region divider() {
        Region r = new Region();
        r.setPrefSize(1, 30);
        r.setStyle("-fx-background-color:#E5E7EB;");
        return r;
    }

    private Label previewValue(String text, String color) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:" + color + ";");
        return label;
    }

    private boolean validateRequired(Node field, Label hint, boolean valid) {
        if (valid) {
            hideFieldError(field, hint);
            return true;
        }
        showFieldError(field, hint);
        return false;
    }

    private void showFieldError(Node field, Label hint) {
        if (field != null) {
            field.setStyle(inputStyle() + "-fx-border-color:#C0392B;");
        }
        if (hint != null) {
            hint.setVisible(true);
            hint.setManaged(true);
        }
    }

    private void hideFieldError(Node field, Label hint) {
        if (field != null) {
            field.setStyle(inputStyle());
        }
        if (hint != null) {
            hint.setVisible(false);
            hint.setManaged(false);
        }
    }

    private int parseNonNegative(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void showToast(String message) {
        Window owner = resolveOwnerWindow();
        if (owner == null) {
            return;
        }

        VBox toast = new VBox(0);
        toast.setStyle("-fx-background-color:#1B2A3B;-fx-background-radius:10;-fx-padding:12 16;-fx-border-color:#1B2A3B;-fx-border-radius:10;");
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        Region dot = new Region();
        dot.setPrefSize(8, 8);
        dot.setStyle("-fx-background-color:#16A34A;-fx-background-radius:999;");
        Label label = new Label(message);
        label.setStyle("-fx-text-fill:white;-fx-font-size:11px;-fx-font-weight:bold;");
        row.getChildren().addAll(dot, label);
        toast.getChildren().add(row);

        StackPane root = new StackPane(toast);
        root.setStyle("-fx-background-color: transparent;");
        root.setOpacity(0.0);
        root.setTranslateX(40);
        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);

        Stage stage = new Stage(StageStyle.TRANSPARENT);
        stage.initOwner(owner);
        stage.setScene(scene);
        stage.setWidth(300);
        stage.setHeight(54);
        stage.setX(owner.getX() + owner.getWidth() - 320);
        stage.setY(owner.getY() + owner.getHeight() - 90);
        stage.show();

        Timeline slideIn = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(root.opacityProperty(), 0.0), new KeyValue(root.translateXProperty(), 40.0)),
                new KeyFrame(Duration.millis(220), new KeyValue(root.opacityProperty(), 1.0), new KeyValue(root.translateXProperty(), 0.0))
        );
        slideIn.play();

        PauseTransition hold = new PauseTransition(Duration.seconds(3));
        hold.setOnFinished(e -> {
            Timeline slideOut = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(root.opacityProperty(), 1.0), new KeyValue(root.translateXProperty(), 0.0)),
                    new KeyFrame(Duration.millis(200), new KeyValue(root.opacityProperty(), 0.0), new KeyValue(root.translateXProperty(), 40.0))
            );
            slideOut.setOnFinished(ev -> stage.close());
            slideOut.play();
        });
        hold.play();
    }

    private record SourceSummary(String source, int good, int rejected, double rejectRate) {
        int rank() {
            if (rejectRate > 30) {
                return 0;
            }
            if (rejectRate >= 15) {
                return 1;
            }
            return 2;
        }
    }
}
