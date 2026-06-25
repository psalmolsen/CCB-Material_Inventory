package com.ccb.controller.page;

import com.ccb.CnfItem;
import com.ccb.CnfMonthProvisioner;
import com.ccb.CnfSheetService;
import javafx.concurrent.Task;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.URL;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CnfController implements Initializable {

    private Label cnfCollarBalance;
    private Label cnfNameplateBalance;
    private Label cnfFootringBalance;
    private TextField cnfSearchField;
    private ComboBox<String> cnfRangeCombo;
    private VBox cnfGroupBox;

    // CNF detail overview labels (top red panel)
    private Label cnfOverviewInitial;
    private Label cnfOverviewReceived;
    private Label cnfOverviewBalance;
    private Label cnfOverviewIssued;
    private Label cnfOverviewUom;
    private Label cnfOverviewItemName;
    private Label cnfOverviewUnitPrice;
    private Label cnfOverviewTotalPrice;

    private String currentTabName = "MAY";
    private List<CnfItem> allItems = new ArrayList<>();
    private final Map<String, List<CnfItem>> tabCache = new ConcurrentHashMap<>();
    private CnfItem selectedItem;

    private static final String[] MONTH_TABS = {
            "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
            "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
    };

    // ── Injection ─────────────────────────────────────────────────────────────

    public void injectNodes(Label collarLbl, Label nameplateLbl, Label footringLbl,
                            TextField searchFld, ComboBox<String> rangeCombo, VBox groupBox,
                            Label overviewInitial, Label overviewReceived, Label overviewBalance,
                            Label overviewIssued, Label overviewUom, Label overviewItemName,
                            Label overviewUnitPrice, Label overviewTotalPrice) {
        this.cnfCollarBalance     = collarLbl;
        this.cnfNameplateBalance  = nameplateLbl;
        this.cnfFootringBalance   = footringLbl;
        this.cnfSearchField       = searchFld;
        this.cnfRangeCombo        = rangeCombo;
        this.cnfGroupBox          = groupBox;
        this.cnfOverviewInitial   = overviewInitial;
        this.cnfOverviewReceived  = overviewReceived;
        this.cnfOverviewBalance   = overviewBalance;
        this.cnfOverviewIssued    = overviewIssued;
        this.cnfOverviewUom       = overviewUom;
        this.cnfOverviewItemName  = overviewItemName;
        this.cnfOverviewUnitPrice = overviewUnitPrice;
        this.cnfOverviewTotalPrice = overviewTotalPrice;
        init();
    }

    @Override public void initialize(URL url, ResourceBundle rb) {}

    private void init() {
        if (cnfRangeCombo == null || cnfSearchField == null) {
            resolveTabThenLoad();
            ensureCurrentMonthTab();
            return;
        }

        cnfRangeCombo.getItems().addAll("Weekly", "Monthly", "Quarterly", "Yearly");
        cnfRangeCombo.getSelectionModel().select("Monthly");
        cnfRangeCombo.getSelectionModel().selectedItemProperty()
                .addListener((obs, o, r) -> {
                    refreshVisibleGroups();
                    prefetchTabsForRange(r);
                });
        cnfSearchField.textProperty().addListener((obs, o, q) -> refreshVisibleGroups());
        resolveTabThenLoad();
        ensureCurrentMonthTab();
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    private void resolveTabThenLoad() {
        Task<String> t = new Task<>() {
            @Override protected String call() throws Exception {
                Month month = LocalDate.now().getMonth();
                String full  = month.getDisplayName(TextStyle.FULL,  Locale.ENGLISH).toUpperCase();
                String short3 = month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase();
                List<String> tabs = new CnfSheetService().getTabNames();
                return tabs.stream()
                        .filter(tb -> tb.equalsIgnoreCase(full) || tb.equalsIgnoreCase(short3))
                        .findFirst().orElse(tabs.isEmpty() ? short3 : tabs.get(0));
            }
        };
        t.setOnSucceeded(e -> { currentTabName = t.getValue(); loadItems(); });
        t.setOnFailed(e -> loadItems());
        daemon(t);
    }

    public void loadItems() {
        Task<List<CnfItem>> task = new Task<>() {
            @Override protected List<CnfItem> call() throws Exception {
                CnfSheetService svc = new CnfSheetService();
                List<List<Object>> rows = svc.readSheet(currentTabName);
                List<CnfItem> items = new ArrayList<>();
                for (int i = CnfItem.DATA_START_ROW; i < rows.size(); i++) {
                    List<Object> row = rows.get(i);
                    if (row.isEmpty() || row.get(0).toString().isBlank()) continue;
                    items.add(CnfSheetService.parseRow(row, i + 1, currentTabName));
                }
                return items;
            }
        };
        task.setOnSucceeded(e -> {
            allItems = task.getValue();
            tabCache.put(currentTabName, new ArrayList<>(allItems));
            updateStatCards();
            refreshVisibleGroups();
            prefetchTabsForRange(getSelectedRange());
        });
        task.setOnFailed(e -> System.err.println("CNF load failed: " + task.getException().getMessage()));
        daemon(task);
    }

    private void ensureCurrentMonthTab() {
        Task<Void> t = new Task<>() {
            @Override protected Void call() throws Exception { CnfMonthProvisioner.provision(); return null; }
        };
        daemon(t);
    }

    private String getSelectedRange() {
        String range = cnfRangeCombo == null ? null : cnfRangeCombo.getSelectionModel().getSelectedItem();
        return (range == null || range.isBlank()) ? "Monthly" : range;
    }

    private void refreshVisibleGroups() {
        if (allItems == null) {
            return;
        }
        renderGroups(allItems, cnfSearchField == null ? "" : cnfSearchField.getText());
        updateCnfOverview(selectedItem);
    }

    private void prefetchTabsForRange(String range) {
        List<String> needed = getTabsForRange(range);
        List<String> missing = needed.stream()
                .filter(tab -> tab != null && !tabCache.containsKey(tab))
                .toList();
        if (missing.isEmpty()) {
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                CnfSheetService svc = new CnfSheetService();
                for (String tab : missing) {
                    try {
                        List<List<Object>> rows = svc.readSheet(tab);
                        List<CnfItem> items = new ArrayList<>();
                        for (int i = CnfItem.DATA_START_ROW; i < rows.size(); i++) {
                            List<Object> row = rows.get(i);
                            if (row.isEmpty() || row.get(0).toString().isBlank()) continue;
                            items.add(CnfSheetService.parseRow(row, i + 1, tab));
                        }
                        tabCache.put(tab, items);
                    } catch (Exception ignored) {
                        // Missing sheet tabs are allowed; skip quietly.
                    }
                }
                return null;
            }
        };
        task.setOnSucceeded(e -> refreshVisibleGroups());
        daemon(task);
    }

    private List<String> getTabsForRange(String range) {
        List<String> tabs = new ArrayList<>();
        LocalDate today = LocalDate.now();
        int currentMonthIdx = today.getMonthValue() - 1;

        switch (range == null ? "Monthly" : range) {
            case "Weekly":
            case "Monthly":
                tabs.add(currentTabName);
                break;
            case "Quarterly": {
                int quarterStart = (currentMonthIdx / 3) * 3;
                for (int i = quarterStart; i <= currentMonthIdx; i++) {
                    tabs.add(MONTH_TABS[i]);
                }
                break;
            }
            case "Yearly":
                for (int i = 0; i <= currentMonthIdx; i++) {
                    tabs.add(MONTH_TABS[i]);
                }
                break;
            default:
                tabs.add(currentTabName);
                break;
        }
        return tabs;
    }

    private static String normalizeItemKey(String name) {
        if (name == null) return "";
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private CnfItem findMatchingCachedItem(String tab, CnfItem baseItem) {
        List<CnfItem> cached = tabCache.get(tab);
        if (cached == null || cached.isEmpty() || baseItem == null) {
            return null;
        }

        String key = normalizeItemKey(baseItem.getItemName());
        for (CnfItem item : cached) {
            if (normalizeItemKey(item.getItemName()).equals(key)) {
                return item;
            }
        }
        return null;
    }

    // ── Grouped rendering ─────────────────────────────────────────────────────

    /**
     * Parses the brand from an item name.
     * "RAPID (COLLAR) 11kgs" → brand = "RAPID"
     * "(NAME PLATE)"         → null → belongs to previous brand
     * "22kgs" / "50kgs"      → null → weight-only sub-row, belongs to previous brand+type
     */
    private String parseBrand(String name) {
        if (name == null || name.isBlank()) return "OTHER";
        String trimmed = name.trim();
        // Starts with "(" → sub-item of previous brand
        if (trimmed.startsWith("(")) return null;
        // Weight-only row (e.g. "22kgs", "50kgs", "22KGS") → sub-variant
        if (trimmed.toLowerCase().matches("\\d+\\s*kgs?.*") || trimmed.toLowerCase().matches("\\d+\\s*kg.*"))
            return null;
        int paren = trimmed.indexOf('(');
        if (paren > 0) return trimmed.substring(0, paren).trim().toUpperCase();
        return trimmed.toUpperCase();
    }

    /**
     * Parses the CNF type from item name.
     * "RAPID (COLLAR) 11kgs" → "COLLAR"
     * "(NAME PLATE)"         → "NAME PLATE"
     * "22kgs"                → null (weight sub-row — inherits previous type)
     */
    private String parseTypeOrNull(String name) {
        if (name == null) return null;
        String s = name.trim();
        if (s.toLowerCase().matches("\\d+\\s*kgs?.*")) return null; // weight-only row
        int a = s.indexOf('('), b = s.indexOf(')');
        if (a >= 0 && b > a) return s.substring(a + 1, b).trim().toUpperCase();
        return null;
    }

    /** Extracts weight variant: "RAPID (COLLAR) 11kgs" → "11kgs", "(NAME PLATE)" → "Single" */
    private String parseVariant(String name) {
        if (name == null) return "Single";
        int b = name.lastIndexOf(')');
        if (b >= 0 && b < name.length() - 1) {
            String after = name.substring(b + 1).trim();
            if (!after.isEmpty()) return after;
        }
        return "Single";
    }

    private void renderGroups(List<CnfItem> items, String query) {
        if (cnfGroupBox == null) {
            return;
        }

        cnfGroupBox.getChildren().clear();
        if (items == null || items.isEmpty()) {
            Label empty = new Label("No CNF items loaded.");
            empty.getStyleClass().add("placeholder-text");
            cnfGroupBox.getChildren().add(empty);
            return;
        }

        String q = query == null ? "" : query.trim().toLowerCase();
        List<CnfItem> filtered = items.stream()
                .filter(i -> {
                    if (q.isEmpty()) return true;
                    String name = i.getItemName() == null ? "" : i.getItemName().toLowerCase();
                    String uom = i.getUom() == null ? "" : i.getUom().toLowerCase();
                    String type = i.getType().name().toLowerCase();
                    return name.contains(q) || uom.contains(q) || type.contains(q);
                })
                .toList();

        if (filtered.isEmpty()) {
            Label empty = new Label("No matching CNF items.");
            empty.getStyleClass().add("placeholder-text");
            cnfGroupBox.getChildren().add(empty);
            return;
        }

        LinkedHashMap<String, List<CnfItem>> grouped = new LinkedHashMap<>();
        grouped.put("COLLAR", new ArrayList<>());
        grouped.put("NAMEPLATE", new ArrayList<>());
        grouped.put("FOOTRING", new ArrayList<>());

        for (CnfItem item : filtered) {
            switch (item.getType()) {
                case COLLAR -> grouped.get("COLLAR").add(item);
                case NAMEPLATE -> grouped.get("NAMEPLATE").add(item);
                case FOOTRING -> grouped.get("FOOTRING").add(item);
                default -> grouped.computeIfAbsent("OTHER", k -> new ArrayList<>()).add(item);
            }
        }

        for (Map.Entry<String, List<CnfItem>> entry : grouped.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            cnfGroupBox.getChildren().add(buildSimpleTypePanel(entry.getKey(), entry.getValue()));
        }
    }

    private VBox buildSimpleTypePanel(String type, List<CnfItem> items) {
        VBox panel = new VBox(10);
        panel.getStyleClass().add("panel");

        Label title = new Label(switch (type) {
            case "COLLAR" -> "Collar Stock";
            case "NAMEPLATE" -> "Nameplate Stock";
            case "FOOTRING" -> "Footring Stock";
            default -> "Other Stock";
        });
        title.getStyleClass().add("panel-title");
        panel.getChildren().add(title);

        for (CnfItem item : items) {
            HBox row = new HBox(10);
            row.getStyleClass().add("stock-row");
            row.setAlignment(Pos.CENTER_LEFT);

            Label name = new Label(item.getItemName());
            name.getStyleClass().add("stock-label");
            name.setWrapText(true);
            HBox.setHgrow(name, Priority.ALWAYS);

            Label value = new Label(String.format("%s %s", fmt(item.getCurrentBalance()), item.getUom()));
            value.getStyleClass().add("stock-value");

            row.getChildren().addAll(name, value);
            row.setOnMouseClicked(e -> {
                selectedItem = item;
                updateCnfOverview(item);
            });
            panel.getChildren().add(row);
        }

        return panel;
    }

    private VBox buildBrandSection(String brand, LinkedHashMap<String, List<CnfItem>> typeMap, String range) {
        // Build a prominent brand header (badge + name + right-side summary)
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new javafx.geometry.Insets(0, 0, 6, 0));

        // Brand badge (initial) on the left
        StackPane brandBadge = new StackPane();
        brandBadge.setMinSize(44, 44); brandBadge.setMaxSize(44, 44);
        brandBadge.setStyle("-fx-background-color: #4a6cf7; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.18), 8, 0, 0, 3);");
        Label badgeLbl = new Label(brand == null || brand.isEmpty() ? "?" : brand.substring(0,1));
        badgeLbl.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px;");
        brandBadge.getChildren().add(badgeLbl);

        // Brand name + descriptor
        VBox nameBox = new VBox(2);
        Label brandTitle = new Label(brand == null ? "UNKNOWN" : brand);
        brandTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");
        Label brandDesc = new Label("PRODUCT CATEGORIES");
        brandDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: #9aa6d8; -fx-letter-spacing: 1.0px;");
        nameBox.getChildren().addAll(brandTitle, brandDesc);

        // Spacer to push summary to the right
        Region headerSpacer = new Region(); HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        // Compute brand totals
        double brandTotal = typeMap.values().stream().flatMap(List::stream).mapToDouble(CnfItem::getCurrentBalance).sum();
        int categoryCount = typeMap.size();
        long variantCount = typeMap.values().stream().flatMap(List::stream).map(i -> parseVariant(i.getItemName())).distinct().count();

        // Right-side summary (units / categories / variants)
        HBox summaryRow = new HBox(18); summaryRow.setAlignment(Pos.CENTER_RIGHT);
        summaryRow.setStyle("-fx-alignment: center-right;");

        VBox unitsBox = new VBox(2); unitsBox.setAlignment(Pos.CENTER_RIGHT);
        Label unitsVal = new Label(String.format("%.0f", brandTotal)); unitsVal.setStyle("-fx-font-size:20px; -fx-font-weight:bold; -fx-text-fill:white;");
        Label unitsLbl = new Label("Units"); unitsLbl.setStyle("-fx-font-size:10px; -fx-text-fill:#9aa6d8; -fx-letter-spacing:0.8px;");
        unitsBox.getChildren().addAll(unitsVal, unitsLbl);

        VBox catsBox = new VBox(2); catsBox.setAlignment(Pos.CENTER_RIGHT);
        Label catsVal = new Label(String.valueOf(categoryCount)); catsVal.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:white;");
        Label catsLbl = new Label("Categories"); catsLbl.setStyle("-fx-font-size:10px; -fx-text-fill:#9aa6d8;");
        catsBox.getChildren().addAll(catsVal, catsLbl);

        VBox varsBox = new VBox(2); varsBox.setAlignment(Pos.CENTER_RIGHT);
        Label varsVal = new Label(String.valueOf(variantCount)); varsVal.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:white;");
        Label varsLbl = new Label("Variants"); varsLbl.setStyle("-fx-font-size:10px; -fx-text-fill:#9aa6d8;");
        varsBox.getChildren().addAll(varsVal, varsLbl);

        summaryRow.getChildren().addAll(unitsBox, catsBox, varsBox);

        header.getChildren().addAll(brandBadge, nameBox, headerSpacer, summaryRow);

        // ── 3-column card row ─────────────────────────────────────────────────
        HBox cardRow = new HBox(14);
        cardRow.setAlignment(Pos.TOP_LEFT);

        // Render in fixed CNF order
        String[] typeOrder = {"COLLAR", "NAME PLATE", "NAMEPLATE", "FOOT RING", "FOOTRING"};
        List<String> orderedTypes = new ArrayList<>();
        for (String t : typeOrder) {
            for (String key : typeMap.keySet()) {
                if (key.equalsIgnoreCase(t) && !orderedTypes.contains(key))
                    orderedTypes.add(key);
            }
        }
        for (String key : typeMap.keySet()) {
            if (!orderedTypes.contains(key)) orderedTypes.add(key);
        }

        // Build cards and ensure equal width distribution
        List<VBox> cards = new ArrayList<>();
        for (String type : orderedTypes) {
            List<CnfItem> typeItems = typeMap.get(type);
            VBox card = buildTypeCard(type, typeItems, range);
            HBox.setHgrow(card, Priority.ALWAYS);
            cards.add(card);
            cardRow.getChildren().add(card);
        }
        // Bind each card's preferred width so all share the row equally
        int count = cards.size();
        double spacing = 14; // same as cardRow spacing
        if (count > 0) {
            for (VBox c : cards) {
                c.prefWidthProperty().bind(cardRow.widthProperty().subtract((count - 1) * spacing).multiply(1.0 / count));
                c.setMaxWidth(Double.MAX_VALUE);
            }
        }

        // Wrap header + cards inside a distinct brand panel so users clearly see brand grouping
        VBox brandShell = new VBox(12);
        brandShell.setStyle("-fx-background-color: linear-gradient(to right, #162044, #1b2a4a); -fx-background-radius: 12; -fx-border-color: #1e2d60; -fx-border-width: 1; -fx-border-radius: 12; -fx-padding: 14;");
        brandShell.getChildren().addAll(header, cardRow);

        // Outer section spacing
        VBox section = new VBox(10, brandShell);
        return section;
    }

    /**
     * Builds one type card (e.g. "Collar") matching the screenshot layout:
     * - Icon area + type name + "N variants"
     * - TOTAL UNITS + big number
     * - Per-variant bar rows with proportional width + balance number
     * - Action buttons at the bottom
     */
    private VBox buildTypeCard(String type, List<CnfItem> typeItems, String range) {
        // Friendly display name
        String displayName = switch (type) {
            case "COLLAR"    -> "Collar";
            case "NAME PLATE", "NAMEPLATE" -> "Nameplate";
            case "FOOT RING", "FOOTRING"   -> "Footring";
            default -> type.substring(0, 1).toUpperCase() + type.substring(1).toLowerCase();
        };

        double totalBalance = typeItems.stream().mapToDouble(CnfItem::getCurrentBalance).sum();
        double maxVariant = typeItems.stream().mapToDouble(CnfItem::getCurrentBalance).max().orElse(1);

        // Card shell
        VBox card = new VBox(0);
        card.setStyle("-fx-background-color: #1e2340; -fx-background-radius: 16; " +
                "-fx-padding: 16; -fx-spacing: 0; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 18, 0, 0, 6);");
        card.setMaxWidth(Double.MAX_VALUE);

        // ── Top row: icon + name + variant count ─────────────────────────────
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // Icon placeholder (colored circle background)
        String iconColor = switch (displayName) {
            case "Collar"    -> "#7c6bff";
            case "Nameplate" -> "#3ecf8e";
            case "Footring"  -> "#4a90d9";
            default          -> "#7b86aa";
        };
        StackPane iconBox = new StackPane();
        iconBox.setMinSize(36, 36); iconBox.setMaxSize(36, 36);
        iconBox.setStyle("-fx-background-color: " + iconColor + "22; -fx-background-radius: 10;");
        Label iconLbl = new Label(switch (displayName) {
            case "Collar" -> "○"; case "Nameplate" -> "⬡"; default -> "◌";
        });
        iconLbl.setStyle("-fx-text-fill: " + iconColor + "; -fx-font-size: 16px;");
        iconBox.getChildren().add(iconLbl);

        VBox nameBlock = new VBox(2);
        Label typeName = new Label(displayName);
        typeName.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: white;");
        Label variantCount = new Label(typeItems.size() == 1 && parseVariant(typeItems.get(0).getItemName()).equals("Single")
                ? "No variants" : typeItems.size() + " variants");
        variantCount.setStyle("-fx-font-size: 10px; -fx-text-fill: rgba(255,255,255,0.45);");
        nameBlock.getChildren().addAll(typeName, variantCount);
        topRow.getChildren().addAll(iconBox, nameBlock);

        // ── TOTAL UNITS section ───────────────────────────────────────────────
        VBox totalSection = new VBox(2);
        totalSection.setStyle("-fx-padding: 14 0 10 0;");
        Label totalCap = new Label("TOTAL UNITS");
        totalCap.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; " +
                "-fx-text-fill: rgba(255,255,255,0.45); -fx-letter-spacing: 1px;");
        Label totalVal = new Label(fmt(totalBalance));
        totalVal.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: white;");
        totalSection.getChildren().addAll(totalCap, totalVal);

        double rangeIssuedTotal = typeItems.stream().mapToDouble(i -> computeRangeIssued(i, range)).sum();
        VBox rangeSection = new VBox(2);
        rangeSection.setStyle("-fx-padding: 0 0 10 0;");
        Label rangeCap = new Label((range == null ? "Monthly" : range).toUpperCase(Locale.ROOT) + " ISSUED");
        rangeCap.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: rgba(255,255,255,0.45); -fx-letter-spacing: 1px;");
        Label rangeVal = new Label(fmt(rangeIssuedTotal));
        rangeVal.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #FEF200;");
        rangeSection.getChildren().addAll(rangeCap, rangeVal);

        // ── Per-variant bar rows ── single-select within this card ──────────
        VBox variantRows = new VBox(4);
        variantRows.setStyle("-fx-padding: 4 0 12 0;");

        // Track selected row per card (single-select)
        final CnfItem[] selectedHolder = {null};
        final List<Button> rowBtns = new ArrayList<>();

        String NORMAL_STYLE  = "-fx-background-color: transparent; -fx-background-radius: 8; " +
                               "-fx-border-color: transparent; -fx-border-radius: 8; -fx-border-width: 1; " +
                               "-fx-padding: 6 6; -fx-cursor: hand; -fx-alignment: CENTER_LEFT; " +
                               "-fx-transition: background-color 150ms ease, border-color 150ms ease;";
        String HOVER_STYLE   = "-fx-background-color: rgba(255,255,255,0.12); -fx-background-radius: 8; " +
                               "-fx-border-color: rgba(255,255,255,0.25); -fx-border-radius: 8; -fx-border-width: 1; " +
                               "-fx-padding: 6 6; -fx-cursor: hand; -fx-alignment: CENTER_LEFT; " +
                               "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 4, 0, 0, 1); " +
                               "-fx-transition: background-color 150ms ease, border-color 150ms ease;";
        String SELECTED_STYLE = "-fx-background-color: #FEF200; -fx-background-radius: 8; " +
                               "-fx-border-color: #e8dc00; -fx-border-radius: 8; -fx-border-width: 2; " +
                               "-fx-padding: 5 5; -fx-cursor: hand; -fx-alignment: CENTER_LEFT; " +
                               "-fx-effect: dropshadow(gaussian, rgba(232,220,0,0.3), 6, 0, 0, 2); " +
                               "-fx-transition: background-color 150ms ease, border-color 150ms ease;";

        for (CnfItem item : typeItems) {
            String variant = parseVariant(item.getItemName());
            double bal = item.getCurrentBalance();
            double ratio = maxVariant <= 0 ? 0 : bal / maxVariant;

            // Use Button so it's keyboard-focusable and screen-reader accessible
            Button rowBtn = new Button();
            rowBtn.setMaxWidth(Double.MAX_VALUE);
            rowBtn.setStyle(NORMAL_STYLE);
            rowBtn.setFocusTraversable(true);
            rowBtn.getProperties().put("aria-pressed", "false");
            rowBtn.setAccessibleText("Select " + variant + " with balance " + fmt(bal));
            rowBtns.add(rowBtn);

            Label varLbl = new Label(variant);
            varLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(255,255,255,0.6); " +
                    "-fx-min-width: 46; -fx-pref-width: 46; -fx-mouse-transparent: true;");

            StackPane track = new StackPane();
            track.setStyle("-fx-background-color: rgba(255,255,255,0.08); " +
                    "-fx-background-radius: 999; -fx-min-height: 6; -fx-pref-height: 6;");
            track.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(track, Priority.ALWAYS);

            Region fill = new Region();
            fill.setStyle("-fx-background-color: " + iconColor + "; " +
                    "-fx-background-radius: 999; -fx-min-height: 6; -fx-pref-height: 6;");
            fill.prefWidthProperty().bind(track.widthProperty().multiply(ratio));
            fill.minWidthProperty().bind(track.widthProperty().multiply(ratio));
            fill.maxWidthProperty().bind(track.widthProperty().multiply(ratio));
            StackPane.setAlignment(fill, Pos.CENTER_LEFT);
            track.getChildren().add(fill);

            Label valLbl = new Label(fmt(bal));
            valLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: white; " +
                    "-fx-min-width: 36; -fx-alignment: CENTER_RIGHT; -fx-mouse-transparent: true;");

            HBox inner = new HBox(8, varLbl, track, valLbl);
            inner.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(track, Priority.ALWAYS);
            rowBtn.setGraphic(inner);
            rowBtn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

            // Enhanced hover and focus behavior
            rowBtn.setOnMouseEntered(e -> {
                if (selectedHolder[0] != item) {
                    rowBtn.setStyle(HOVER_STYLE);
                }
            });
            rowBtn.setOnMouseExited(e -> {
                if (selectedHolder[0] != item) {
                    rowBtn.setStyle(NORMAL_STYLE);
                }
            });
            
            // Focus styling for keyboard navigation
            rowBtn.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
                if (isFocused && selectedHolder[0] != item) {
                    rowBtn.setStyle(HOVER_STYLE + "-fx-border-color: #4a90e2; -fx-border-width: 2;");
                } else if (!isFocused && selectedHolder[0] != item) {
                    rowBtn.setStyle(NORMAL_STYLE);
                }
            });

            // Click — single select within this card
            rowBtn.setOnAction(e -> {
                // Reset all rows in this card
                for (int i = 0; i < rowBtns.size(); i++) {
                    Button b = rowBtns.get(i);
                    CnfItem rowItem = typeItems.get(i);
                    b.setStyle(NORMAL_STYLE);
                    b.getProperties().put("aria-pressed", "false");
                    // Reset label colors to normal
                    HBox g = (HBox) b.getGraphic();
                    ((Label) g.getChildren().get(0)).setStyle(
                        "-fx-font-size: 11px; -fx-text-fill: rgba(255,255,255,0.6); " +
                        "-fx-min-width: 46; -fx-pref-width: 46; -fx-mouse-transparent: true;");
                    ((Label) g.getChildren().get(2)).setStyle(
                        "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: white; " +
                        "-fx-min-width: 36; -fx-alignment: CENTER_RIGHT; -fx-mouse-transparent: true;");
                }
                
                // Select this row
                rowBtn.setStyle(SELECTED_STYLE);
                rowBtn.getProperties().put("aria-pressed", "true");
                
                // Update label colors for selected state (dark text on yellow background)
                varLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #1A2560; -fx-font-weight: bold; " +
                    "-fx-min-width: 46; -fx-pref-width: 46; -fx-mouse-transparent: true;");
                valLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1A2560; " +
                    "-fx-min-width: 36; -fx-alignment: CENTER_RIGHT; -fx-mouse-transparent: true;");
                
                selectedHolder[0] = item;
                selectedItem = item;
                // Update overview panel
                updateCnfOverview(item);
            });

            // Enhanced keyboard navigation — Space/Enter triggers same as click
            rowBtn.setOnKeyPressed(e -> {
                if (e.getCode() == javafx.scene.input.KeyCode.SPACE ||
                    e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                    e.consume(); // Prevent default button behavior
                    rowBtn.fire();
                }
            });
            
            // Add keyboard support for arrow key navigation within the card
            rowBtn.setOnKeyPressed(keyEvent -> {
                if (keyEvent.getCode() == javafx.scene.input.KeyCode.DOWN ||
                    keyEvent.getCode() == javafx.scene.input.KeyCode.UP) {
                    keyEvent.consume();
                    int currentIndex = rowBtns.indexOf(rowBtn);
                    int nextIndex = keyEvent.getCode() == javafx.scene.input.KeyCode.DOWN ?
                        Math.min(currentIndex + 1, rowBtns.size() - 1) :
                        Math.max(currentIndex - 1, 0);
                    if (nextIndex != currentIndex) {
                        rowBtns.get(nextIndex).requestFocus();
                    }
                } else if (keyEvent.getCode() == javafx.scene.input.KeyCode.SPACE ||
                           keyEvent.getCode() == javafx.scene.input.KeyCode.ENTER) {
                    keyEvent.consume();
                    rowBtn.fire();
                }
            });

            variantRows.getChildren().add(rowBtn);
        }

        // ── Action buttons ────────────────────────────────────────────────────
        // Show buttons for first item only (stock in/out applies per variant — user picks from Monthly Out)
        HBox actions = new HBox(6);
        actions.setStyle("-fx-padding: 4 0 0 0;");
        actions.setAlignment(Pos.CENTER_LEFT);

        // Only show for single-item types or the first variant
        if (!typeItems.isEmpty()) {
            CnfItem representative = typeItems.get(0);
            if (typeItems.size() == 1) {
                Button si = new Button("Stock In +"); si.getStyleClass().add("stock-btn");
                Button so = new Button("Stock Out -"); so.getStyleClass().add("stock-btn");
                si.setOnAction(e -> showStockInDialog(representative));
                so.setOnAction(e -> showStockOutDialog(representative));
                actions.getChildren().addAll(si, so);
            }
            // Monthly out button always shown
            Button more = new Button("\u22EF"); more.getStyleClass().add("material-more-button");
            more.setFocusTraversable(false);
            ContextMenu menu = new ContextMenu();
            VBox panel = new VBox(6); panel.getStyleClass().add("material-action-panel");
            for (CnfItem item : typeItems) {
                String variant = parseVariant(item.getItemName());
                Button si = actionBtn("Stock In — " + variant, "Add received qty for " + variant);
                si.setOnAction(e -> { menu.hide(); showStockInDialog(item); });
                Button so = actionBtn("Stock Out — " + variant, "Record daily out for " + variant);
                so.setOnAction(e -> { menu.hide(); showStockOutDialog(item); });
                Button mo = actionBtn("Monthly Out — " + variant, "View daily chart for " + variant);
                mo.setOnAction(e -> { menu.hide(); showMonthlyOutDialog(item); });
                panel.getChildren().addAll(si, so, mo);
                if (typeItems.indexOf(item) < typeItems.size() - 1) {
                    Separator sep = new Separator(); sep.setStyle("-fx-opacity: 0.15;");
                    panel.getChildren().add(sep);
                }
            }
            CustomMenuItem cmi = new CustomMenuItem(panel, false); cmi.setHideOnClick(false);
            menu.getItems().add(cmi);
            more.setOnAction(e -> { if (!menu.isShowing()) menu.show(more, javafx.geometry.Side.BOTTOM, 0, 4); else menu.hide(); });
            actions.getChildren().add(more);
        }

        card.getChildren().addAll(topRow, totalSection, rangeSection, variantRows, actions);
        return card;
    }

    // ── Stat cards ────────────────────────────────────────────────────────────

    private void updateStatCards() {
        if (cnfCollarBalance == null) return;
        double collar = 0, nameplate = 0, footring = 0;
        for (CnfItem item : allItems) {
            switch (item.getType()) {
                case COLLAR    -> collar    += item.getCurrentBalance();
                case NAMEPLATE -> nameplate += item.getCurrentBalance();
                case FOOTRING  -> footring  += item.getCurrentBalance();
            }
        }
        cnfCollarBalance.setText(fmt(collar));
        cnfNameplateBalance.setText(fmt(nameplate));
        cnfFootringBalance.setText(fmt(footring));
    }

    /** Updates the red CNF overview panel with the selected variant's data */
    private void updateCnfOverview(CnfItem item) {
        if (cnfOverviewInitial == null) return;
        if (item == null) {
            cnfOverviewInitial.setText("00");
            cnfOverviewReceived.setText("00");
            cnfOverviewBalance.setText("00");
            cnfOverviewIssued.setText("00");
            cnfOverviewUom.setText("");
            cnfOverviewItemName.setText("— select a variant");
            if (cnfOverviewUnitPrice  != null) cnfOverviewUnitPrice.setText("—");
            if (cnfOverviewTotalPrice != null) cnfOverviewTotalPrice.setText("—");
            return;
        }
        String uom = item.getUom() == null || item.getUom().isBlank() ? "" : item.getUom().trim();
        String range = cnfRangeCombo.getSelectionModel().getSelectedItem();
        double issued = computeRangeIssued(item, range);
        cnfOverviewInitial.setText(fmt(item.getInitialStock()));
        cnfOverviewReceived.setText(fmt(item.getReceivedQuantity()));
        cnfOverviewBalance.setText(fmt(item.getCurrentBalance()));
        cnfOverviewIssued.setText(fmt(issued));
        cnfOverviewUom.setText(uom);
        cnfOverviewItemName.setText(item.getItemName() == null ? "" : "— " + item.getItemName());
        if (cnfOverviewUnitPrice != null) {
            double price = item.getUnitPrice();
            cnfOverviewUnitPrice.setText(price <= 0 ? "N/A" : String.format("\u20B1%.2f", price));
        }
        if (cnfOverviewTotalPrice != null) {
            double price = item.getUnitPrice();
            cnfOverviewTotalPrice.setText(price <= 0 ? "N/A"
                    : String.format("\u20B1%.2f", price * issued));
        }
    }

    // ── Range computation ─────────────────────────────────────────────────────

double computeRangeIssued(CnfItem item, String range) {
    if (item == null) {
        return 0;
    }

    LocalDate today = LocalDate.now();
    int todayDay = today.getDayOfMonth();
    String selectedRange = range == null ? "Monthly" : range;

    if ("Weekly".equals(selectedRange)) {
        int dow = today.getDayOfWeek().getValue();
        int start = Math.max(1, todayDay - (dow - 1));
        double sum = 0;
        for (int d = start; d <= todayDay; d++) {
            sum += item.getDayValue(d);
        }
        return sum;
    }

    if ("Monthly".equals(selectedRange)) {
        double sum = 0;
        for (int d = 1; d <= todayDay; d++) {
            sum += item.getDayValue(d);
        }
        return sum;
    }

    if ("Quarterly".equals(selectedRange) || "Yearly".equals(selectedRange)) {
        double sum = 0;
        for (String tab : getTabsForRange(selectedRange)) {
            if (tab == null || tab.isBlank()) {
                continue;
            }

            if (tab.equalsIgnoreCase(currentTabName)) {
                for (int d = 1; d <= todayDay; d++) {
                    sum += item.getDayValue(d);
                }
                continue;
            }

            CnfItem matched = findMatchingCachedItem(tab, item);
            if (matched != null) {
                sum += matched.getTotalIssued();
            }
        }
        return sum;
    }

    return item.getTotalIssued();
}

    // ── Dialogs ───────────────────────────────────────────────────────────────

    void showStockInDialog(CnfItem item) {
        Stage dialog = dlgStage();
        VBox root = dlgShell();
        VBox header = dlgHeader("STOCK-IN RECORDING", "Add Received Quantity",
                "Add received quantity to " + item.getItemName(), dialog);
        VBox body = new VBox(14); body.getStyleClass().add("dialog-body");
        body.getChildren().addAll(
            heroPanel(item.getItemName(),
                String.format("Current Balance: %.0f %s", item.getCurrentBalance(), item.getUom())),
            qtyCard("QUANTITY TO ADD"));
        HBox btns = footerBtns(dialog);
        Button save = (Button) btns.getChildren().get(1);
        TextField qty = findQtyField(body);
        body.getChildren().add(btns);
        root.getChildren().addAll(header, body);
        save.setOnAction(e -> {
            String q = qty.getText().trim(); if (q.isEmpty()) return;
            String tab = item.getSheetTabName() == null ? currentTabName : item.getSheetTabName();
            Task<Void> t = new Task<>() {
                @Override protected Void call() throws Exception {
                    CnfSheetService svc = new CnfSheetService();
                    svc.writeCell(tab + "!E" + item.getSheetRowNumber(), Double.parseDouble(q));
                    svc.writeCell(tab + "!F" + item.getSheetRowNumber(), LocalDate.now().toString());
                    return null;
                }
            };
            t.setOnSucceeded(ev -> { dialog.close(); loadItems(); });
            t.setOnFailed(ev -> dialog.close());
            daemon(t);
        });
        show(dialog, root);
    }

    void showStockOutDialog(CnfItem item) {
        Stage dialog = dlgStage();
        VBox root = dlgShell();
        VBox header = dlgHeader("STOCK OUT RECORDING", "Stock Out Record",
                "Record daily items issued to production", dialog);
        VBox body = new VBox(14); body.getStyleClass().add("dialog-body");
        body.getChildren().addAll(
            heroPanel(item.getItemName(),
                String.format("Current Balance: %.0f %s", item.getCurrentBalance(), item.getUom())),
            qtyCard("QUANTITY TO DISBURSE"));
        HBox btns = footerBtns(dialog);
        Button save = (Button) btns.getChildren().get(1);
        TextField qty = findQtyField(body);
        body.getChildren().add(btns);
        root.getChildren().addAll(header, body);
        save.setOnAction(e -> {
            String q = qty.getText().trim(); if (q.isEmpty()) return;
            int day = LocalDate.now().getDayOfMonth();
            String col = colName(12 + (day - 1));
            String tab = item.getSheetTabName() == null ? currentTabName : item.getSheetTabName();
            Task<Void> t = new Task<>() {
                @Override protected Void call() throws Exception {
                    new CnfSheetService().writeCell(tab + "!" + col + item.getSheetRowNumber(), Double.parseDouble(q));
                    return null;
                }
            };
            t.setOnSucceeded(ev -> { dialog.close(); loadItems(); });
            t.setOnFailed(ev -> dialog.close());
            daemon(t);
        });
        show(dialog, root);
    }

    void showMonthlyOutDialog(CnfItem item) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        VBox root = new VBox(0); root.getStyleClass().add("dialog-shell");
        root.setPrefWidth(560); root.setPrefHeight(700);

        String range = getSelectedRange();
        VBox hdrBox = dlgHeader("", item.getItemName(),
                range + " issued quantities for " + currentTabName, dialog);
        VBox body = new VBox(14); body.getStyleClass().add("dialog-body");

        VBox pill = new VBox(4); pill.getStyleClass().add("summary-pill"); pill.setAlignment(Pos.CENTER);
        Label sl = new Label(range.toUpperCase(Locale.ROOT) + " ISSUED"); sl.getStyleClass().add("summary-label");
        Label sv = new Label(fmt(computeRangeIssued(item, range))); sv.getStyleClass().add("summary-value");
        pill.getChildren().addAll(sl, sv);

        VBox listBox = new VBox(8); listBox.getStyleClass().add("day-list-stack");
        List<RangeMetric> metrics = buildCnfRangeMetrics(item, range);
        double maxVal = metrics.stream().mapToDouble(RangeMetric::value).max().orElse(0);
        for (RangeMetric metric : metrics) {
            HBox row = new HBox(12); row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("day-row");
            if (metric.value() <= 0) row.setOpacity(0.45);
            Label badge = new Label(metric.label()); badge.getStyleClass().add("day-badge");
            StackPane track = new StackPane(); track.getStyleClass().add("day-bar-track");
            track.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(track, Priority.ALWAYS);
            Region fill = new Region(); fill.getStyleClass().add("day-bar-fill");
            fill.setMinHeight(6); fill.setPrefHeight(6);
            double ratio = (maxVal <= 0 || metric.value() <= 0) ? 0 : metric.value() / maxVal;
            fill.prefWidthProperty().bind(track.widthProperty().multiply(ratio));
            fill.minWidthProperty().bind(track.widthProperty().multiply(ratio));
            fill.maxWidthProperty().bind(track.widthProperty().multiply(ratio));
            StackPane.setAlignment(fill, Pos.CENTER_LEFT); track.getChildren().add(fill);
            Label vb = new Label(metric.value() > 0 ? fmt(metric.value()) : "—"); vb.getStyleClass().add("day-value-badge");
            row.getChildren().addAll(badge, track, vb); listBox.getChildren().add(row);
        }
        VBox shell = new VBox(listBox); shell.getStyleClass().add("day-list-shell");
        ScrollPane scroll = new ScrollPane(shell);
        scroll.setFitToWidth(true); scroll.setFitToHeight(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        scroll.getStyleClass().add("glass-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        body.getChildren().addAll(pill, scroll);
        root.getChildren().addAll(hdrBox, body);
        show(dialog, root);
    }

    private record RangeMetric(String label, double value) {}

    private List<RangeMetric> buildCnfRangeMetrics(CnfItem item, String range) {
        List<RangeMetric> metrics = new ArrayList<>();
        LocalDate today = LocalDate.now();
        String selected = range == null ? "Monthly" : range;

        if ("Weekly".equals(selected)) {
            int dow = today.getDayOfWeek().getValue();
            int start = Math.max(1, today.getDayOfMonth() - (dow - 1));
            for (int day = start; day <= today.getDayOfMonth(); day++) {
                metrics.add(new RangeMetric(String.format("DAY %02d", day), item.getDayValue(day)));
            }
            return metrics;
        }

        if ("Monthly".equals(selected)) {
            for (int day = 1; day <= today.getDayOfMonth(); day++) {
                metrics.add(new RangeMetric(String.format("DAY %02d", day), item.getDayValue(day)));
            }
            return metrics;
        }

        List<String> tabs = getTabsForRange(selected);
        for (String tab : tabs) {
            if (tab == null || tab.isBlank()) continue;
            if (tab.equalsIgnoreCase(currentTabName)) {
                metrics.add(new RangeMetric(tab, computeRangeIssued(item, "Monthly")));
                continue;
            }
            CnfItem matched = findMatchingCachedItem(tab, item);
            metrics.add(new RangeMetric(tab, matched == null ? 0 : matched.getTotalIssued()));
        }
        return metrics;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Stage dlgStage() {
        Stage s = new Stage(); s.initModality(Modality.APPLICATION_MODAL);
        s.initStyle(StageStyle.TRANSPARENT); return s;
    }
    private VBox dlgShell() {
        VBox v = new VBox(0); v.getStyleClass().add("dialog-shell"); v.setPrefWidth(420); return v;
    }
    private VBox dlgHeader(String eyebrow, String title, String subtitle, Stage dialog) {
        VBox h = new VBox(4); h.getStyleClass().addAll("dialog-header", "dialog-header-alt");
        HBox row = new HBox(10); row.setAlignment(Pos.CENTER_LEFT);
        VBox titles = new VBox(4); HBox.setHgrow(titles, Priority.ALWAYS);
        if (!eyebrow.isBlank()) { Label e = new Label(eyebrow); e.getStyleClass().add("dialog-eyebrow"); titles.getChildren().add(e); }
        Label t = new Label(title); t.getStyleClass().add("dialog-title");
        Label s = new Label(subtitle); s.getStyleClass().add("dialog-subtitle");
        titles.getChildren().addAll(t, s);
        Button close = new Button("✕");
        close.setStyle("-fx-background-color:transparent;-fx-text-fill:rgba(255,255,255,0.7);-fx-font-size:14px;-fx-cursor:hand;-fx-border-width:0;-fx-padding:0;");
        close.setOnAction(ev -> dialog.close());
        row.getChildren().addAll(titles, close); h.getChildren().add(row); return h;
    }
    private VBox heroPanel(String headline, String copy) {
        VBox v = new VBox(6); v.getStyleClass().add("hero-panel");
        Label h = new Label(headline); h.getStyleClass().add("hero-headline"); h.setWrapText(true);
        Label c = new Label(copy); c.getStyleClass().add("hero-copy");
        v.getChildren().addAll(h, c); return v;
    }
    private VBox qtyCard(String caption) {
        VBox card = new VBox(12); card.getStyleClass().add("panel-card");
        Label lbl = new Label(caption); lbl.getStyleClass().add("field-caption");
        Button minus = new Button("-"); minus.getStyleClass().add("stock-step-button");
        minus.setStyle("-fx-font-size:18px;-fx-background-radius:8;-fx-min-width:44;-fx-min-height:38;");
        TextField qty = new TextField("1"); qty.getStyleClass().add("stock-qty");
        qty.setStyle("-fx-background-color:#f8faff;-fx-text-fill:#1A2560;-fx-font-size:18px;-fx-font-weight:bold;-fx-alignment:center;-fx-background-radius:8;-fx-min-height:38;-fx-pref-width:100;-fx-border-color:#dfe5fb;-fx-border-radius:8;");
        Button plus = new Button("+"); plus.getStyleClass().add("stock-step-button");
        plus.setStyle("-fx-font-size:18px;-fx-font-weight:bold;-fx-background-radius:8;-fx-min-width:44;-fx-min-height:38;");
        minus.setOnAction(e -> { try { int v = Integer.parseInt(qty.getText().trim()); qty.setText(String.valueOf(Math.max(0,v-1))); } catch(Exception ex){qty.setText("0");} });
        plus.setOnAction(e -> { try { int v = Integer.parseInt(qty.getText().trim()); qty.setText(String.valueOf(v+1)); } catch(Exception ex){qty.setText("1");} });
        HBox stepper = new HBox(8, minus, qty, plus); stepper.setAlignment(Pos.CENTER);
        card.getChildren().addAll(lbl, stepper); return card;
    }
    private TextField findQtyField(VBox body) {
        for (var n : body.getChildren()) { if (n instanceof VBox card) {
            for (var c : card.getChildren()) { if (c instanceof HBox hb) {
                for (var x : hb.getChildren()) { if (x instanceof TextField tf) return tf; }}}}}
        return new TextField("1");
    }
    private HBox footerBtns(Stage dialog) {
        Button cancel = new Button("Cancel"); cancel.getStyleClass().add("btn-secondary");
        Button save = new Button("Save"); save.getStyleClass().add("btn-add-material");
        cancel.setOnAction(e -> dialog.close());
        HBox h = new HBox(10, cancel, save); h.setAlignment(Pos.CENTER_RIGHT); return h;
    }
    private void show(Stage dialog, VBox root) {
        Scene scene = new Scene(root); scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/com/ccb/css/style.css").toExternalForm());
        dialog.setScene(scene); dialog.showAndWait();
    }
    private Button actionBtn(String title, String sub) {
        Label t = new Label(title); t.getStyleClass().add("material-action-button-title");
        Label s = new Label(sub);   s.getStyleClass().add("material-action-button-subtitle");
        VBox tb = new VBox(2, t, s); HBox row = new HBox(tb); HBox.setHgrow(tb, Priority.ALWAYS);
        Button btn = new Button(); btn.getStyleClass().add("material-action-button");
        btn.setGraphic(row); btn.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        btn.setMaxWidth(Double.MAX_VALUE); return btn;
    }
    private static String fmt(double v) { return String.format("%.0f", v); }
    private static String colName(int col) {
        StringBuilder sb = new StringBuilder();
        while (col > 0) { col--; sb.insert(0,(char)('A'+(col%26))); col/=26; }
        return sb.toString();
    }
    private static void daemon(Task<?> t) { Thread th = new Thread(t); th.setDaemon(true); th.start(); }
}
