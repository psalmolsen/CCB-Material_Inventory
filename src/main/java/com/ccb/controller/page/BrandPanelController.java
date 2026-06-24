package com.ccb.controller.page;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class BrandPanelController implements Initializable {

    @FXML private VBox brandListContainer;

    private static final String BG_CARD        = "#1a2540";
    private static final String BG_SUMMARY     = "#131d33";
    private static final String BG_BTN         = "#243058";
    private static final String BG_BTN_HOVER   = "#2e3e6e";
    private static final String TEXT_PRIMARY    = "#ffffff";
    private static final String TEXT_SECONDARY  = "#8fa8c8";
    private static final String TEXT_MUTED      = "#506080";
    private static final String DIVIDER         = "#243058";
    private static final String BAR_TRACK       = "#243058";

    public record VariantData(String label, int totalIssued, int balanceQty, List<Integer> dailyIssued) {}

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
        loading.setStyle("-fx-text-fill: " + TEXT_MUTED + "; -fx-font-size: 13px;");
        brandListContainer.getChildren().add(loading);
    }

    public void loadBrands(List<BrandData> brands) {
        Platform.runLater(() -> {
            brandListContainer.getChildren().clear();
            if (brands == null || brands.isEmpty()) {
                Label empty = new Label("No CNF brand data available for this month.");
                empty.setStyle("-fx-text-fill: " + TEXT_MUTED + "; -fx-font-size: 13px;");
                brandListContainer.getChildren().add(empty);
                return;
            }
            for (BrandData brand : brands) {
                brandListContainer.getChildren().add(buildBrandSection(brand));
            }
        });
    }

    private VBox buildBrandSection(BrandData brand) {
        VBox section = new VBox(12);
        section.setFillWidth(true);

        HBox labelRow = new HBox(10);
        labelRow.setAlignment(Pos.CENTER_LEFT);
        labelRow.setPadding(new Insets(10, 14, 10, 14));
        labelRow.setMaxWidth(Double.MAX_VALUE);
        labelRow.setStyle(
            "-fx-background-color: rgba(123,108,246,0.10);" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: rgba(123,108,246,0.22);" +
            "-fx-border-radius: 12;"
        );

        Region accent = new Region();
        accent.setMinWidth(4);
        accent.setMaxWidth(4);
        accent.setMinHeight(38);
        accent.setPrefHeight(38);
        accent.setStyle("-fx-background-color: #7b6cf6; -fx-background-radius: 2;");

        VBox nameStack = new VBox(2);
        Label brandName = new Label(brand.name());
        brandName.setStyle(
            "-fx-text-fill: " + TEXT_PRIMARY + ";" +
            "-fx-font-size: 17px; -fx-font-weight: bold;"
        );
        Label subtitle = new Label("PRODUCT CATEGORIES");
        subtitle.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 11px;");
        nameStack.getChildren().addAll(brandName, subtitle);
        HBox.setHgrow(nameStack, Priority.ALWAYS);

        labelRow.getChildren().addAll(accent, nameStack);

        HBox columns = new HBox(12);
        columns.setFillHeight(true);
        for (CategoryData cat : brand.categories()) {
            VBox card = buildCategoryCard(cat, brand.name());
            HBox.setHgrow(card, Priority.ALWAYS);
            columns.getChildren().add(card);
        }

        section.getChildren().addAll(labelRow, columns);
        return section;
    }

    private VBox buildCategoryCard(CategoryData cat, String brandName) {
        VBox card = new VBox(12);
        card.setPadding(new Insets(16));
        card.setStyle(
            "-fx-background-color: " + BG_CARD + ";" +
            "-fx-background-radius: 12;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 10, 0, 0, 3);"
        );

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label("○");
        icon.setStyle(
            "-fx-background-color: " + cat.iconBg() + ";" +
            "-fx-background-radius: 8;" +
            "-fx-text-fill: " + cat.iconColor() + ";" +
            "-fx-font-size: 16px;" +
            "-fx-min-width: 34px; -fx-min-height: 34px;" +
            "-fx-alignment: center;"
        );

        VBox titleBox = new VBox(2);
        Label titleLbl = new Label(cat.title());
        titleLbl.setStyle(
            "-fx-text-fill: " + TEXT_PRIMARY + ";" +
            "-fx-font-size: 14px; -fx-font-weight: bold;"
        );
        Label subLbl = new Label(cat.subtitle());
        subLbl.setStyle("-fx-text-fill: " + TEXT_MUTED + "; -fx-font-size: 11px;");
        titleBox.getChildren().addAll(titleLbl, subLbl);
        header.getChildren().addAll(icon, titleBox);

        VBox summary = new VBox(8);
        summary.setPadding(new Insets(12));
        summary.setStyle(
            "-fx-background-color: " + BG_SUMMARY + ";" +
            "-fx-background-radius: 8;"
        );

        Label totalLbl = new Label("TOTAL ISSUED");
        totalLbl.setStyle(
            "-fx-text-fill: " + TEXT_MUTED + ";" +
            "-fx-font-size: 10px; -fx-font-weight: bold;"
        );

        Label totalVal = new Label(String.valueOf(cat.totalIssued()));
        totalVal.setStyle(
            "-fx-text-fill: " + TEXT_PRIMARY + ";" +
            "-fx-font-size: 26px; -fx-font-weight: bold;"
        );

        summary.getChildren().addAll(totalLbl, totalVal);

        int maxIssued = cat.variants().stream()
            .mapToInt(VariantData::totalIssued).max().orElse(1);
        if (maxIssued == 0) maxIssued = 1;

        for (VariantData v : cat.variants()) {
            summary.getChildren().add(buildBarRow(brandName, cat.title(), v, maxIssued, cat.barColor()));
        }

        Label more = new Label("...");
        more.setStyle("-fx-text-fill: " + TEXT_MUTED + "; -fx-font-size: 11px;");

        Region divider = new Region();
        divider.setMinHeight(1);
        divider.setMaxHeight(1);
        divider.setStyle("-fx-background-color: " + DIVIDER + ";");

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button stockIn  = makeButton("Stock In +",  brandName, cat.title());
        Button stockOut = makeButton("Stock Out -", brandName, cat.title());
        Button moreBtn  = makeButton("...",          brandName, cat.title());

        actions.getChildren().addAll(stockIn, stockOut, moreBtn);
        card.getChildren().addAll(header, summary, more, divider, actions);
        return card;
    }

    private HBox buildBarRow(String brandName, String category, VariantData v, int maxIssued, String barColor) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setCursor(Cursor.HAND);

        Label lbl = new Label(v.label());
        lbl.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 11px;");
        lbl.setMinWidth(42);

        StackPane track = new StackPane();
        track.setMinHeight(5);
        track.setMaxHeight(5);
        track.setStyle("-fx-background-color: " + BAR_TRACK + "; -fx-background-radius: 3;");
        HBox.setHgrow(track, Priority.ALWAYS);

        Rectangle fill = new Rectangle(0, 5);
        fill.setArcWidth(3);
        fill.setArcHeight(3);
        fill.setFill(Color.web(barColor));

        double pct = (double) v.totalIssued() / maxIssued;
        track.widthProperty().addListener((obs, oldW, newW) ->
            fill.setWidth(newW.doubleValue() * pct)
        );
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        track.getChildren().add(fill);

        Label val = new Label(String.valueOf(v.totalIssued()));
        val.setStyle(
            "-fx-text-fill: " + TEXT_PRIMARY + ";" +
            "-fx-font-size: 11px; -fx-font-weight: bold;"
        );
        val.setMinWidth(28);
        val.setAlignment(Pos.CENTER_RIGHT);

        row.getChildren().addAll(lbl, track, val);
        row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 6;"));
        row.setOnMouseExited(e -> row.setStyle("-fx-background-color: transparent;"));
        row.setOnMouseClicked(e -> showCnFOverview(brandName, category, v));
        return row;
    }

    private void showCnFOverview(String brandName, String category, VariantData variant) {
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

        Button close = new Button("✕");
        close.setStyle("-fx-background-color:transparent;-fx-text-fill:rgba(255,255,255,0.7);-fx-font-size:14px;-fx-cursor:hand;-fx-border-width:0;-fx-padding:0;");
        close.setOnAction(ev -> dialog.close());

        headerRow.getChildren().addAll(titleBox, close);
        header.getChildren().add(headerRow);

        VBox body = new VBox(14);
        body.getStyleClass().add("dialog-body");

        VBox hero = new VBox(6);
        hero.getStyleClass().add("hero-panel");
        hero.setStyle(
            "-fx-background-color: linear-gradient(to right, #fdeaea, #fff7f7);" +
            "-fx-background-radius: 18;" +
            "-fx-border-color: #f2b5bc;" +
            "-fx-border-radius: 18;" +
            "-fx-border-width: 1;"
        );

        Label heroTag = new Label(category);
        heroTag.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #C0392B;");
        Label heroHeadline = new Label(variant.label());
        heroHeadline.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1A2560;");
        Label heroCopy = new Label("Current Balance: " + variant.balanceQty());
        heroCopy.setStyle("-fx-font-size: 11px; -fx-text-fill: #7b86aa;");
        hero.getChildren().addAll(heroTag, heroHeadline, heroCopy);

        HBox stats = new HBox(10);
        stats.setAlignment(Pos.CENTER_LEFT);

        VBox totalCard = new VBox(4);
        totalCard.getStyleClass().add("panel-card");
        totalCard.setStyle("-fx-background-color: #C0392B; -fx-background-radius: 16; -fx-border-color: #cc1522; -fx-border-radius: 16; -fx-border-width: 1;");
        Label totalLbl = new Label("TOTAL ISSUED");
        totalLbl.setStyle("-fx-text-fill: rgba(255,255,255,0.75); -fx-font-size: 10px; -fx-font-weight: bold;");
        Label totalVal = new Label(String.valueOf(variant.totalIssued()));
        totalVal.setStyle("-fx-text-fill: white; -fx-font-size: 24px; -fx-font-weight: bold;");
        totalCard.getChildren().addAll(totalLbl, totalVal);
        HBox.setHgrow(totalCard, Priority.ALWAYS);

        VBox balanceCard = new VBox(4);
        balanceCard.getStyleClass().add("panel-card");
        balanceCard.setStyle("-fx-background-color: white; -fx-background-radius: 16; -fx-border-color: #f2b5bc; -fx-border-radius: 16; -fx-border-width: 1;");
        Label balanceLbl = new Label("BALANCE QTY");
        balanceLbl.setStyle("-fx-text-fill: #7b86aa; -fx-font-size: 10px; -fx-font-weight: bold;");
        Label balanceVal = new Label(String.valueOf(variant.balanceQty()));
        balanceVal.setStyle("-fx-text-fill: #C0392B; -fx-font-size: 24px; -fx-font-weight: bold;");
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
                badge.setStyle("-fx-background-color: #fdeaea; -fx-text-fill: #C0392B;");

                StackPane track = new StackPane();
                track.getStyleClass().add("day-bar-track");
                track.setStyle("-fx-background-color: #f6d6d2; -fx-background-radius: 999; -fx-border-color: #f2b5bc; -fx-border-radius: 999; -fx-border-width: 1;");
                track.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(track, Priority.ALWAYS);

                Region fill = new Region();
                fill.setStyle("-fx-background-color: linear-gradient(to right, #ffb3b3, #C0392B 60%, #8f1f16); -fx-background-radius: 999;");
                double ratio = maxVal <= 0 ? 0 : (double) value / maxVal;
                fill.prefWidthProperty().bind(track.widthProperty().multiply(ratio));
                fill.minWidthProperty().bind(track.widthProperty().multiply(ratio));
                fill.maxWidthProperty().bind(track.widthProperty().multiply(ratio));
                StackPane.setAlignment(fill, Pos.CENTER_LEFT);
                track.getChildren().add(fill);

                Label val = new Label(String.valueOf(value));
                val.setStyle("-fx-min-width: 68; -fx-pref-width: 68; -fx-background-color: rgba(255,255,255,0.98); -fx-background-radius: 10; -fx-padding: 7 10; -fx-alignment: center; -fx-border-color: #C0392B; -fx-border-radius: 10; -fx-border-width: 1.5; -fx-text-fill: #1A2560; -fx-font-weight: bold; -fx-font-size: 13px;");

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

    private Button makeButton(String text, String brand, String category) {
        Button btn = new Button(text);
        btn.setStyle(
            "-fx-background-color: " + BG_BTN + ";" +
            "-fx-text-fill: " + TEXT_PRIMARY + ";" +
            "-fx-font-size: 12px;" +
            "-fx-background-radius: 6;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 6 14 6 14;"
        );
        btn.setOnMouseEntered(e ->
            btn.setStyle(btn.getStyle().replace(BG_BTN, BG_BTN_HOVER))
        );
        btn.setOnMouseExited(e ->
            btn.setStyle(btn.getStyle().replace(BG_BTN_HOVER, BG_BTN))
        );

        btn.setOnAction(e -> {
            System.out.println("[" + text + "] Brand: " + brand + " | Category: " + category);
        });

        return btn;
    }
}
