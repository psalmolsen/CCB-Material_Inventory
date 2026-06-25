package com.ccb.controller.page;

import com.ccb.CnfSheetService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

public class BrandPanelController implements Initializable {

    @FXML private VBox brandListContainer;
    // Overview panel labels — set by MainController after FXML load
    private Label overviewItemName;
    private Label overviewInitial;
    private Label overviewReceived;
    private Label overviewBalance;
    private Label overviewIssued;
    private Label overviewUom;
    private Label overviewUnitPrice;
    private Label overviewTotalPrice;
    private String selectedTabName = "MAY";
    private Runnable refreshCallback;

    public void setSelectedTabName(String tabName) {
        if (tabName != null && !tabName.isBlank()) {
            this.selectedTabName = tabName;
        }
    }

    public void setRefreshCallback(Runnable callback) {
        this.refreshCallback = callback;
    }

    public void setOverviewLabels(Label itemName, Label initial, Label received,
                                   Label balance, Label issued, Label uom,
                                   Label unitPrice, Label totalPrice) {
        this.overviewItemName  = itemName;
        this.overviewInitial   = initial;
        this.overviewReceived  = received;
        this.overviewBalance   = balance;
        this.overviewIssued    = issued;
        this.overviewUom       = uom;
        this.overviewUnitPrice = unitPrice;
        this.overviewTotalPrice = totalPrice;
    }

    private static final String BG_CARD        = "#1a2540";
    private static final String BG_SUMMARY     = "#131d33";
    private static final String BG_BTN         = "#243058";
    private static final String BG_BTN_HOVER   = "#2e3e6e";
    private static final String TEXT_PRIMARY    = "#ffffff";
    private static final String TEXT_SECONDARY  = "#8fa8c8";
    private static final String TEXT_MUTED      = "#506080";
    private static final String DIVIDER         = "#243058";
    private static final String BAR_TRACK       = "#243058";

    // CNF brand sheet columns (0-indexed, data from row 6)
    private static final int COL_UOM           = 3;
    private static final int COL_PRICE         = 4;
    private static final int COL_INITIAL       = 5;
    private static final int COL_IN_QTY        = 6;
    private static final int COL_DATE          = 7;
    private static final int COL_BALANCE       = 9;
    private static final int COL_DAY_START     = 13; // N = Day 1
    private static final int COL_TOTAL_ISSUED  = 44;

    private VBox activeActionSection;
    private Runnable activeSelectionClear;

    public record VariantData(String label, int totalIssued, int balanceQty, List<Integer> dailyIssued,
                              int initialStock, int receivedQty, double unitPrice, int sheetRowNumber,
                              String uom) {}

    public record CategoryData(
        String title,
        List<VariantData> variants,
        String barColor,
        String iconBg,
        String iconColor
    ) {
        public int totalIssued() {
            return variants.stream().mapToInt(VariantData::totalIssued).sum();
        }
        public String subtitle() {
            return variants.size() == 1 ? "No variants" : variants.size() + " variants";
        }
    }

    public record BrandData(String name, List<CategoryData> categories) {}

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Label loading = new Label("Loading brand data...");
        loading.setStyle("-fx-text-fill: #a6afc7; -fx-font-size: 13px;");
        brandListContainer.getChildren().add(loading);
    }

    public void loadBrands(List<BrandData> brands) {
        Platform.runLater(() -> {
            clearGlobalSelection();
            brandListContainer.getChildren().clear();
            if (brands == null || brands.isEmpty()) {
                Label empty = new Label("No CNF brand data available for this month.");
                empty.setStyle("-fx-text-fill: #a6afc7; -fx-font-size: 13px;");
                brandListContainer.getChildren().add(empty);
                return;
            }

            int globalMaxIssued = 0;
            for (BrandData brand : brands) {
                for (CategoryData cat : brand.categories()) {
                    globalMaxIssued = Math.max(globalMaxIssued, cat.totalIssued());
                }
            }

            for (BrandData brand : brands) {
                brandListContainer.getChildren().add(buildBrandSection(brand, globalMaxIssued));
            }
        });
    }

    private VBox buildBrandSection(BrandData brand, int globalMaxIssued) {
        VBox section = new VBox(12);
        section.setFillWidth(true);

        HBox labelRow = new HBox(10);
        labelRow.setAlignment(Pos.CENTER_LEFT);
        labelRow.setPadding(new Insets(10, 14, 10, 14));
        labelRow.setMaxWidth(Double.MAX_VALUE);
        labelRow.getStyleClass().add("cnf-brand-header");

        Region accent = new Region();
        accent.setMinWidth(4);
        accent.setMaxWidth(4);
        accent.setMinHeight(38);
        accent.setPrefHeight(38);
        accent.getStyleClass().add("cnf-brand-header-accent");

        VBox nameStack = new VBox(2);
        Label brandName = new Label(brand.name());
        brandName.getStyleClass().add("cnf-brand-name");
        Label subtitle = new Label("PRODUCT CATEGORIES");
        subtitle.getStyleClass().add("cnf-brand-subtitle");
        nameStack.getChildren().addAll(brandName, subtitle);
        HBox.setHgrow(nameStack, Priority.ALWAYS);

        labelRow.getChildren().addAll(accent, nameStack);

        HBox columns = new HBox(12);
        columns.setFillHeight(true);
        List<VBox> cards = new java.util.ArrayList<>();
        for (CategoryData cat : brand.categories()) {
            boolean isActive = (globalMaxIssued > 0 && cat.totalIssued() == globalMaxIssued);
            VBox card = buildCategoryCard(cat, brand.name(), isActive);
            HBox.setHgrow(card, Priority.ALWAYS);
            cards.add(card);
            columns.getChildren().add(card);
        }

        int count = cards.size();
        double spacing = 12;
        if (count > 0) {
            for (VBox card : cards) {
                card.prefWidthProperty().bind(columns.widthProperty().subtract((count - 1) * spacing).multiply(1.0 / count));
                card.setMinWidth(Region.USE_COMPUTED_SIZE);
                card.setMaxWidth(Double.MAX_VALUE);
            }
        }

        section.getChildren().addAll(labelRow, columns);
        return section;
    }

    private VBox buildCategoryCard(CategoryData cat, String brandName, boolean isActive) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(16));
        card.getStyleClass().add("cnf-category-card");
        if (isActive) {
            card.getStyleClass().add("cnf-category-card-active");
        }

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label(switch (cat.title().toUpperCase(java.util.Locale.ROOT)) {
            case "COLLAR" -> "○";
            case "NAME PLATE", "NAMEPLATE" -> "⬡";
            default -> "◌";
        });
        icon.getStyleClass().add("cnf-category-icon");

        VBox titleBox = new VBox(2);
        Label titleLbl = new Label(cat.title());
        titleLbl.getStyleClass().add("cnf-card-title");
        Label subLbl = new Label(cat.subtitle());
        subLbl.getStyleClass().add("cnf-card-subtitle");
        titleBox.getChildren().addAll(titleLbl, subLbl);
        header.getChildren().addAll(icon, titleBox);

        if (isActive) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label activeBadge = new Label("Active");
            activeBadge.getStyleClass().add("cnf-active-badge");
            header.getChildren().addAll(spacer, activeBadge);
        }

        VBox summary = new VBox(8);
        summary.setPadding(new Insets(12));
        summary.getStyleClass().add("cnf-card-summary");

        Label totalLbl = new Label("TOTAL ISSUED");
        totalLbl.getStyleClass().add("cnf-card-total-label");

        Label totalVal = new Label(String.valueOf(cat.totalIssued()));
        totalVal.getStyleClass().add("cnf-card-total-value");

        summary.getChildren().addAll(totalLbl, totalVal);

        int maxIssued = cat.variants().stream()
            .mapToInt(VariantData::totalIssued).max().orElse(1);
        if (maxIssued == 0) maxIssued = 1;

        Label more = new Label("...");
        more.getStyleClass().add("cnf-card-more-label");

        Region verticalSpacer = new Region();
        VBox.setVgrow(verticalSpacer, Priority.ALWAYS);

        Region divider = new Region();
        divider.getStyleClass().add("cnf-card-divider");

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button stockIn  = new Button("Stock In +");
        Button stockOut = new Button("Stock Out -");
        Button moreBtn  = new Button("⋯");
        stockIn.getStyleClass().add("stock-btn");
        stockOut.getStyleClass().add("stock-btn");
        moreBtn.getStyleClass().add("material-more-button");
        moreBtn.setFocusTraversable(false);
        actions.getChildren().addAll(stockIn, stockOut, moreBtn);

        VBox actionSection = new VBox(8, divider, actions);
        actionSection.setVisible(false);
        actionSection.setManaged(false);

        final VariantData[] sel = {null};
        final java.util.List<HBox> barRows = new java.util.ArrayList<>();

        Runnable hideCardActions = () -> {
            sel[0] = null;
            actionSection.setVisible(false);
            actionSection.setManaged(false);
            for (HBox barRow : barRows) {
                barRow.getStyleClass().remove("cnf-bar-row-selected");
            }
        };

        for (VariantData v : cat.variants()) {
            HBox barRow = buildBarRow(brandName, cat.title(), v, maxIssued, isActive,
                    sel, barRows, actionSection, hideCardActions);
            barRows.add(barRow);
            summary.getChildren().add(barRow);
        }

        stockIn.setOnAction(e -> {
            if (sel[0] == null) return;
            showCnfStockInDialog(brandName, cat.title(), sel[0], selectedTabName);
        });

        stockOut.setOnAction(e -> {
            if (sel[0] == null) return;
            showCnfStockOutDialog(brandName, cat.title(), sel[0], selectedTabName);
        });

        ContextMenu moreMenu = new ContextMenu();
        VBox menuPanel = new VBox(10);
        menuPanel.getStyleClass().add("material-action-panel");
        HBox menuHeader = new HBox(10);
        menuHeader.setAlignment(Pos.CENTER_LEFT);
        Label menuTitle = new Label("Quick Actions");
        menuTitle.getStyleClass().add("material-action-title");
        Region menuSpacer = new Region();
        HBox.setHgrow(menuSpacer, Priority.ALWAYS);
        Button closeMenuBtn = new Button("X");
        closeMenuBtn.setFocusTraversable(false);
        closeMenuBtn.setStyle("-fx-background-color:transparent;-fx-text-fill:#7b86aa;-fx-font-size:14px;-fx-font-weight:bold;-fx-cursor:hand;-fx-border-width:0;-fx-padding:0 2 0 2;");
        closeMenuBtn.setOnAction(e -> moreMenu.hide());
        menuHeader.getChildren().addAll(menuTitle, menuSpacer, closeMenuBtn);
        Button monthOutBtn = buildActionMenuBtn("Monthly Daily Out Report",
                "View daily issued quantities for this variant");
        monthOutBtn.setOnAction(e -> {
            moreMenu.hide();
            if (sel[0] != null) showCnfMonthlyOutDialog(brandName, cat.title(), sel[0], isActive);
        });
        menuPanel.getChildren().addAll(menuHeader, monthOutBtn);
        CustomMenuItem cmi = new CustomMenuItem(menuPanel, false);
        cmi.setHideOnClick(false);
        moreMenu.getItems().add(cmi);
        moreBtn.setOnAction(e -> {
            if (sel[0] == null) return;
            if (!moreMenu.isShowing()) moreMenu.show(moreBtn, javafx.geometry.Side.BOTTOM, 0, 4);
            else moreMenu.hide();
        });

        card.getChildren().addAll(header, summary, more, verticalSpacer, actionSection);
        return card;
    }

    private void clearGlobalSelection() {
        if (activeSelectionClear != null) {
            activeSelectionClear.run();
        }
        activeSelectionClear = null;
        activeActionSection = null;
    }

    private void selectVariant(HBox row, VariantData variant, String brandName, String category,
                               VariantData[] sel, java.util.List<HBox> barRows,
                               VBox actionSection, Runnable hideCardActions) {
        clearGlobalSelection();
        sel[0] = variant;
        updateOverviewLabels(brandName, category, variant);
        for (HBox barRow : barRows) {
            barRow.getStyleClass().remove("cnf-bar-row-selected");
        }
        row.getStyleClass().add("cnf-bar-row-selected");
        actionSection.setVisible(true);
        actionSection.setManaged(true);
        activeActionSection = actionSection;
        activeSelectionClear = hideCardActions;
    }

    private HBox buildBarRow(String brandName, String category, VariantData v,
                              int maxIssued, boolean isParentCardActive,
                              VariantData[] sel, java.util.List<HBox> barRows,
                              VBox actionSection, Runnable hideCardActions) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setCursor(Cursor.HAND);

        Label lbl = new Label(v.label());
        lbl.getStyleClass().add("cnf-bar-label");
        lbl.setMinWidth(42);

        StackPane track = new StackPane();
        track.getStyleClass().add("cnf-bar-track");
        HBox.setHgrow(track, Priority.ALWAYS);

        Rectangle fill = new Rectangle(0, 5);
        fill.setArcWidth(3);
        fill.setArcHeight(3);
        fill.setFill(Color.web(isParentCardActive ? "#F0B429" : "#1E3A8A"));

        double pct = (double) v.totalIssued() / maxIssued;
        track.widthProperty().addListener((obs, oldW, newW) ->
            fill.setWidth(newW.doubleValue() * pct)
        );
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        track.getChildren().add(fill);

        Label val = new Label(String.valueOf(v.totalIssued()));
        val.getStyleClass().add("cnf-bar-value");
        val.setMinWidth(28);
        val.setAlignment(Pos.CENTER_RIGHT);

        row.getChildren().addAll(lbl, track, val);
        row.getStyleClass().add("cnf-bar-row");
        row.setOnMouseEntered(e -> {
            if (!row.getStyleClass().contains("cnf-bar-row-selected")) {
                row.setStyle("-fx-background-color: rgba(30,58,138,0.04); -fx-background-radius: 6;");
            }
        });
        row.setOnMouseExited(e -> {
            if (!row.getStyleClass().contains("cnf-bar-row-selected")) {
                row.setStyle("-fx-background-color: transparent;");
            }
        });
        row.setOnMouseClicked(e -> selectVariant(row, v, brandName, category, sel, barRows,
                actionSection, hideCardActions));
        return row;
    }

    private void updateOverviewLabels(String brandName, String category, VariantData v) {
        if (overviewInitial == null) return; // not wired yet
        if (overviewItemName  != null) overviewItemName.setText("— " + brandName + " " + category + " " + v.label());
        if (overviewInitial   != null) overviewInitial.setText(String.valueOf(v.initialStock()));
        if (overviewReceived  != null) overviewReceived.setText(String.valueOf(v.receivedQty()));
        if (overviewBalance   != null) overviewBalance.setText(String.valueOf(v.balanceQty()));
        if (overviewIssued    != null) overviewIssued.setText(String.valueOf(v.totalIssued()));
        if (overviewUom       != null) overviewUom.setText(v.uom() == null || v.uom().isBlank() ? "Pcs" : v.uom());
        if (overviewUnitPrice != null) {
            double price = v.unitPrice();
            overviewUnitPrice.setText(price <= 0 ? "N/A" : String.format("\u20B1%.2f", price));
        }
        if (overviewTotalPrice != null) {
            double price = v.unitPrice();
            overviewTotalPrice.setText(price <= 0 ? "N/A"
                    : String.format("\u20B1%.2f", price * v.totalIssued()));
        }
    }

    private void showCnFOverview(String brandName, String category, VariantData variant, boolean isParentCardActive) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox(0);
        root.getStyleClass().add("dialog-shell");
        root.setPrefWidth(560);

        VBox header = new VBox(4);
        header.getStyleClass().addAll("dialog-header", "dialog-header-alt");

        HBox headerRow = new HBox(10);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        Label eyebrow = new Label("CNF OVERVIEW");
        eyebrow.getStyleClass().add("dialog-eyebrow");
        Label title = new Label(brandName + " / " + category);
        title.getStyleClass().add("dialog-title");
        Label subtitle = new Label("Variant: " + variant.label());
        subtitle.getStyleClass().add("dialog-subtitle");
        titleBox.getChildren().addAll(eyebrow, title, subtitle);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        Button close = new Button("X");
        close.setFocusTraversable(false);
        close.setStyle("-fx-background-color:transparent;-fx-text-fill:#7b86aa;-fx-font-size:14px;-fx-font-weight:bold;-fx-cursor:hand;-fx-border-width:0;-fx-padding:0 2 0 2;");
        close.setOnAction(ev -> dialog.close());

        headerRow.getChildren().addAll(titleBox, close);
        header.getChildren().add(headerRow);

        VBox body = new VBox(14);
        body.getStyleClass().add("dialog-body");

        boolean isAlert = variant.balanceQty() <= 0;
        String themeColor;
        String themeBg;
        String themeBorder;
        String trackBg;
        String trackBorder;
        String barGradient;

        if (isAlert) {
            themeColor = "#C0392B";
            themeBg = "#fdeaea";
            themeBorder = "#f2b5bc";
            trackBg = "#f6d6d2";
            trackBorder = "#f2b5bc";
            barGradient = "linear-gradient(to right, #ffb3b3, #C0392B 60%, #8f1f16)";
        } else if (isParentCardActive) {
            themeColor = "#F0B429";
            themeBg = "rgba(240, 180, 41, 0.05)";
            themeBorder = "#F0B429";
            trackBg = "#e6ebf8";
            trackBorder = "rgba(240, 180, 41, 0.20)";
            barGradient = "linear-gradient(to right, #ffe69c, #F0B429 60%, #b88614)";
        } else {
            themeColor = "#1E3A8A";
            themeBg = "rgba(30, 58, 138, 0.05)";
            themeBorder = "rgba(30, 58, 138, 0.15)";
            trackBg = "#e6ebf8";
            trackBorder = "rgba(30, 58, 138, 0.15)";
            barGradient = "linear-gradient(to right, #9cc7ff, #1E3A8A 60%, #1a2560)";
        }

        VBox hero = new VBox(6);
        hero.getStyleClass().add("hero-panel");
        hero.setStyle(
            "-fx-background-color: " + themeBg + ";" +
            "-fx-background-radius: 18;" +
            "-fx-border-color: " + themeBorder + ";" +
            "-fx-border-radius: 18;" +
            "-fx-border-width: 1;"
        );

        Label heroTag = new Label(category + (isAlert ? " - OUT OF STOCK" : ""));
        heroTag.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + themeColor + ";");
        Label heroHeadline = new Label(variant.label());
        heroHeadline.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1A2560;");
        Label heroCopy = new Label("Current Balance: " + variant.balanceQty());
        heroCopy.setStyle("-fx-font-size: 11px; -fx-text-fill: #7b86aa;");
        hero.getChildren().addAll(heroTag, heroHeadline, heroCopy);

        HBox stats = new HBox(10);
        stats.setAlignment(Pos.CENTER_LEFT);

        VBox totalCard = new VBox(4);
        totalCard.getStyleClass().add("panel-card");
        totalCard.setStyle(
            "-fx-background-color: " + themeColor + ";" +
            "-fx-background-radius: 16;" +
            "-fx-border-color: " + themeColor + ";" +
            "-fx-border-radius: 16;" +
            "-fx-border-width: 1;"
        );
        String totalValTextFill = isParentCardActive && !isAlert ? "#1A2560" : "white";
        String totalLblTextFill = isParentCardActive && !isAlert ? "rgba(26, 37, 96, 0.75)" : "rgba(255,255,255,0.75)";

        Label totalLbl = new Label("TOTAL ISSUED");
        totalLbl.setStyle("-fx-text-fill: " + totalLblTextFill + "; -fx-font-size: 10px; -fx-font-weight: bold;");
        Label totalVal = new Label(String.valueOf(variant.totalIssued()));
        totalVal.setStyle("-fx-text-fill: " + totalValTextFill + "; -fx-font-size: 24px; -fx-font-weight: bold;");
        totalCard.getChildren().addAll(totalLbl, totalVal);
        HBox.setHgrow(totalCard, Priority.ALWAYS);

        VBox balanceCard = new VBox(4);
        balanceCard.getStyleClass().add("panel-card");
        balanceCard.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 16;" +
            "-fx-border-color: " + themeBorder + ";" +
            "-fx-border-radius: 16;" +
            "-fx-border-width: 1;"
        );
        Label balanceLbl = new Label("BALANCE QTY");
        balanceLbl.setStyle("-fx-text-fill: #7b86aa; -fx-font-size: 10px; -fx-font-weight: bold;");
        Label balanceVal = new Label(String.valueOf(variant.balanceQty()));
        balanceVal.setStyle("-fx-text-fill: " + themeColor + "; -fx-font-size: 24px; -fx-font-weight: bold;");
        balanceCard.getChildren().addAll(balanceLbl, balanceVal);
        HBox.setHgrow(balanceCard, Priority.ALWAYS);

        stats.getChildren().addAll(totalCard, balanceCard);

        VBox dayList = new VBox(8);
        dayList.getStyleClass().add("day-list-stack");

        List<Integer> daily = variant.dailyIssued() == null ? List.of() : variant.dailyIssued();
        int maxVal = daily.stream().mapToInt(Integer::intValue).max().orElse(0);
        if (maxVal <= 0) {
            Label empty = new Label("No daily CNF transactions found for this row.");
            empty.setStyle("-fx-text-fill: #7b86aa; -fx-font-size: 12px;");
            dayList.getChildren().add(empty);
        } else {
            for (int day = 0; day < daily.size(); day++) {
                int value = daily.get(day) == null ? 0 : daily.get(day);
                if (value <= 0) {
                    continue;
                }
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("day-row");

                Label badge = new Label(String.format("DAY %02d", day + 1));
                badge.getStyleClass().add("day-badge");
                badge.setStyle("-fx-background-color: " + themeBg + "; -fx-text-fill: " + themeColor + ";");

                StackPane track = new StackPane();
                track.getStyleClass().add("day-bar-track");
                track.setStyle(
                    "-fx-background-color: " + trackBg + ";" +
                    "-fx-background-radius: 999;" +
                    "-fx-border-color: " + trackBorder + ";" +
                    "-fx-border-radius: 999;" +
                    "-fx-border-width: 1;"
                );
                track.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(track, Priority.ALWAYS);

                Region fill = new Region();
                fill.setStyle("-fx-background-color: " + barGradient + "; -fx-background-radius: 999;");
                double ratio = maxVal <= 0 ? 0 : (double) value / maxVal;
                fill.prefWidthProperty().bind(track.widthProperty().multiply(ratio));
                fill.minWidthProperty().bind(track.widthProperty().multiply(ratio));
                fill.maxWidthProperty().bind(track.widthProperty().multiply(ratio));
                StackPane.setAlignment(fill, Pos.CENTER_LEFT);
                track.getChildren().add(fill);

                Label val = new Label(String.valueOf(value));
                val.setStyle("-fx-min-width: 68; -fx-pref-width: 68; -fx-background-color: rgba(255,255,255,0.98); -fx-background-radius: 10; -fx-padding: 7 10; -fx-alignment: center; -fx-border-color: " + themeColor + "; -fx-border-radius: 10; -fx-border-width: 1.5; -fx-text-fill: #1A2560; -fx-font-weight: bold; -fx-font-size: 13px;");

                row.getChildren().addAll(badge, track, val);
                dayList.getChildren().add(row);
            }
        }

        VBox shell = new VBox(dayList);
        shell.getStyleClass().add("day-list-shell");
        ScrollPane scroll = new ScrollPane(shell);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("glass-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        body.getChildren().addAll(hero, stats, scroll);
        root.getChildren().addAll(header, body);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/com/ccb/css/style.css").toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private void showCnfStockInDialog(String brandName, String category, VariantData variant, String tabName) {
        String itemName = brandName + " / " + category + " / " + variant.label();
        String uom = variant.uom() == null || variant.uom().isBlank() ? "Pcs" : variant.uom();
        Stage dialog = dlgStage();
        VBox root = dlgShell();
        VBox header = dlgHeader("STOCK-IN RECORDING", "Add Received Quantity",
                "Add received quantity to inventory stock.", dialog);
        VBox body = new VBox(14);
        body.getStyleClass().add("dialog-body");
        body.getChildren().addAll(
                heroPanel(itemName, "Current Balance: " + variant.balanceQty() + " " + uom),
                qtyCard("QUANTITY TO ADD"));
        HBox btns = footerBtns(dialog);
        Button save = (Button) btns.getChildren().get(1);
        TextField qty = findQtyField(body);
        body.getChildren().add(btns);
        root.getChildren().addAll(header, body);
        save.setOnAction(e -> {
            String q = qty.getText().trim();
            if (q.isEmpty()) return;
            String tab = tabName == null ? selectedTabName : tabName;
            int rowNum = variant.sheetRowNumber();
            Task<Void> t = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    CnfSheetService svc = new CnfSheetService();
                    svc.writeCell(tab + "!" + colName(COL_IN_QTY + 1) + rowNum, Double.parseDouble(q));
                    svc.writeCell(tab + "!" + colName(COL_DATE + 1) + rowNum, LocalDate.now().toString());
                    return null;
                }
            };
            t.setOnSucceeded(ev -> {
                dialog.close();
                if (refreshCallback != null) refreshCallback.run();
            });
            t.setOnFailed(ev -> dialog.close());
            daemon(t);
        });
        show(dialog, root);
    }

    private void showCnfStockOutDialog(String brandName, String category, VariantData variant, String tabName) {
        String itemName = brandName + " / " + category + " / " + variant.label();
        String uom = variant.uom() == null || variant.uom().isBlank() ? "Pcs" : variant.uom();
        Stage dialog = dlgStage();
        VBox root = dlgShell();
        VBox header = dlgHeader("STOCK OUT RECORDING", "Stock Out Record",
                "Record daily items issued to production.", dialog);
        VBox body = new VBox(14);
        body.getStyleClass().add("dialog-body");
        body.getChildren().addAll(
                heroPanel(itemName, "Current Balance: " + variant.balanceQty() + " " + uom),
                qtyCard("QUANTITY TO DISBURSE"));
        HBox btns = footerBtns(dialog);
        Button save = (Button) btns.getChildren().get(1);
        TextField qty = findQtyField(body);
        body.getChildren().add(btns);
        root.getChildren().addAll(header, body);
        save.setOnAction(e -> {
            String q = qty.getText().trim();
            if (q.isEmpty()) return;
            int day = LocalDate.now().getDayOfMonth();
            String col = colName(COL_DAY_START + day);
            String tab = tabName == null ? selectedTabName : tabName;
            int rowNum = variant.sheetRowNumber();
            Task<Void> t = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    new CnfSheetService().writeCell(tab + "!" + col + rowNum, Double.parseDouble(q));
                    return null;
                }
            };
            t.setOnSucceeded(ev -> {
                dialog.close();
                if (refreshCallback != null) refreshCallback.run();
            });
            t.setOnFailed(ev -> dialog.close());
            daemon(t);
        });
        show(dialog, root);
    }

    private void showCnfMonthlyOutDialog(String brandName, String category, VariantData variant,
                                         boolean isParentCardActive) {
        showCnFOverview(brandName, category, variant, isParentCardActive);
    }

    private Button buildActionMenuBtn(String title, String subtitle) {
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

    private Stage dlgStage() {
        Stage s = new Stage();
        s.initModality(Modality.APPLICATION_MODAL);
        s.initStyle(StageStyle.TRANSPARENT);
        return s;
    }

    private VBox dlgShell() {
        VBox v = new VBox(0);
        v.getStyleClass().add("dialog-shell");
        v.setPrefWidth(420);
        return v;
    }

    private VBox dlgHeader(String eyebrow, String title, String subtitle, Stage dialog) {
        VBox h = new VBox(4);
        h.getStyleClass().addAll("dialog-header", "dialog-header-alt");
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox titles = new VBox(4);
        HBox.setHgrow(titles, Priority.ALWAYS);
        if (!eyebrow.isBlank()) {
            Label e = new Label(eyebrow);
            e.getStyleClass().add("dialog-eyebrow");
            titles.getChildren().add(e);
        }
        Label t = new Label(title);
        t.getStyleClass().add("dialog-title");
        Label s = new Label(subtitle);
        s.getStyleClass().add("dialog-subtitle");
        titles.getChildren().addAll(t, s);
        Button close = new Button("✕");
        close.setStyle("-fx-background-color:transparent;-fx-text-fill:rgba(255,255,255,0.7);-fx-font-size:14px;-fx-cursor:hand;-fx-border-width:0;-fx-padding:0;");
        close.setOnAction(ev -> dialog.close());
        row.getChildren().addAll(titles, close);
        h.getChildren().add(row);
        return h;
    }

    private VBox heroPanel(String headline, String copy) {
        VBox v = new VBox(6);
        v.getStyleClass().add("hero-panel");
        Label h = new Label(headline);
        h.getStyleClass().add("hero-headline");
        h.setWrapText(true);
        Label c = new Label(copy);
        c.getStyleClass().add("hero-copy");
        v.getChildren().addAll(h, c);
        return v;
    }

    private VBox qtyCard(String caption) {
        VBox card = new VBox(12);
        card.getStyleClass().add("panel-card");
        Label lbl = new Label(caption);
        lbl.getStyleClass().add("field-caption");
        Button minus = new Button("-");
        minus.getStyleClass().add("stock-step-button");
        minus.setStyle("-fx-font-size:18px;-fx-background-radius:8;-fx-min-width:44;-fx-min-height:38;");
        TextField qty = new TextField("1");
        qty.getStyleClass().add("stock-qty");
        qty.setStyle("-fx-background-color:#f8faff;-fx-text-fill:#1A2560;-fx-font-size:18px;-fx-font-weight:bold;-fx-alignment:center;-fx-background-radius:8;-fx-min-height:38;-fx-pref-width:100;-fx-border-color:#dfe5fb;-fx-border-radius:8;");
        Button plus = new Button("+");
        plus.getStyleClass().add("stock-step-button");
        plus.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-background-radius:8;-fx-min-width:44;-fx-min-height:38;");
        minus.setOnAction(e -> {
            try {
                int v = Integer.parseInt(qty.getText().trim());
                qty.setText(String.valueOf(Math.max(0, v - 1)));
            } catch (Exception ex) {
                qty.setText("0");
            }
        });
        plus.setOnAction(e -> {
            try {
                int v = Integer.parseInt(qty.getText().trim());
                qty.setText(String.valueOf(v + 1));
            } catch (Exception ex) {
                qty.setText("1");
            }
        });
        HBox stepper = new HBox(8, minus, qty, plus);
        stepper.setAlignment(Pos.CENTER);
        card.getChildren().addAll(lbl, stepper);
        return card;
    }

    private TextField findQtyField(VBox body) {
        for (var n : body.getChildren()) {
            if (n instanceof VBox card) {
                for (var c : card.getChildren()) {
                    if (c instanceof HBox hb) {
                        for (var x : hb.getChildren()) {
                            if (x instanceof TextField tf) return tf;
                        }
                    }
                }
            }
        }
        return new TextField("1");
    }

    private HBox footerBtns(Stage dialog) {
        Button cancel = new Button("Close");
        cancel.getStyleClass().add("btn-secondary");
        Button save = new Button("Save");
        save.getStyleClass().add("btn-add-material");
        cancel.setOnAction(e -> dialog.close());
        HBox h = new HBox(10, cancel, save);
        h.setAlignment(Pos.CENTER_RIGHT);
        return h;
    }

    private void show(Stage dialog, VBox root) {
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/com/ccb/css/style.css").toExternalForm());
        dialog.setScene(scene);
        dialog.showAndWait();
    }

    private static String colName(int col) {
        StringBuilder sb = new StringBuilder();
        while (col > 0) {
            col--;
            sb.insert(0, (char) ('A' + (col % 26)));
            col /= 26;
        }
        return sb.toString();
    }

    private static void daemon(Task<?> t) {
        Thread th = new Thread(t);
        th.setDaemon(true);
        th.start();
    }

    private Button makeButton(String text, String brand, String category) {
        Button btn = new Button(text);
        btn.getStyleClass().add("cnf-outline-btn");
        btn.setOnAction(e -> {
            System.out.println("[" + text + "] Brand: " + brand + " | Category: " + category);
        });
        return btn;
    }
}
