package com.jobwork.controller;

import com.jobwork.domain.JobWorker;
import com.jobwork.domain.JobWorkerSummaryRow;
import com.jobwork.domain.SummaryTotals;
import com.jobwork.service.JobWorkerService;
import com.jobwork.service.JobWorkerSummaryService;
import com.jobwork.service.MoneyService;
import com.jobwork.util.ExcelExporter;
import com.jobwork.util.GlobalUI;
import com.jobwork.util.JobWorkerComboBoxUtil;
import com.jobwork.util.PdfExporter;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.converter.BigDecimalStringConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.LocalDate;

import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * JobWorkerSummaryController — DEFINITIVE FIXED VERSION
 * ══════════════════════════════════════════════════════════════════
 * LAYOUT FIX (in jobworker_summary.fxml, not here):
 *   Root BorderPane:
 *     top    = page header
 *     center = inner BorderPane:
 *                top    = filter card + report bar + table header bar
 *                center = ScrollPane → TableView (NO prefHeight)
 *                bottom = footer totals + action buttons (ALWAYS VISIBLE)
 *
 *   The footer is in BorderPane BOTTOM → JavaFX reserves its height
 *   FIRST, then gives remaining space to CENTER (ScrollPane+table).
 *   → Footer never scrolls off screen regardless of row count.
 *   → Table auto-adjusts height to available space.
 *   → When rows overflow, ScrollPane provides scrollbar.
 *
 * ENTER KEY FIX (in this controller):
 *   colPaisa.setOnEditCommit → Platform.runLater(tblSummary.requestFocus())
 *   colRate.setOnEditCommit  → same
 *   tfAdvanceMoney  Enter/Tab → recalc + runLater(focus) + event.consume()
 *   tfPreviousMoney Enter/Tab → same
 *   → Cursor stays in TABLE after Enter, does NOT jump to cbWorker.
 *
 * DATA (from JobWorkerSummaryService):
 *   ONE ROW PER UNIQUE PRODUCT NAME — weight and qty summed.
 *   buildSummaryRows(workerId, from, to) → grouped by ProductName.id.
 *
 * FOOTER FORMULA:
 *   Grand Total = Total − Advance Money + Previous Money
 * ══════════════════════════════════════════════════════════════════
 */
@Component
@Scope("prototype")
public class JobWorkerSummaryController implements Initializable {

  //  private static final DateTimeFormatter FMT =
           // DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Services ──────────────────────────────────────────────────
    @Autowired private JobWorkerSummaryService summaryService;
    @Autowired private JobWorkerService         workerService;
    @Autowired private PdfExporter              pdfExporter;
    @Autowired private ExcelExporter            excelExporter;

    // ── Filter controls ──────────────────────────────────────────
    @FXML private ComboBox<JobWorker> cbWorker;
    @FXML private DatePicker          dpFrom;
    @FXML private DatePicker          dpTo;

    // ── Status + report header labels ────────────────────────────
    @FXML private Label lblStatus;
    @FXML private Label lblWorkerName;
    @FXML private Label lblDateRange;
    @FXML private Label lblRowCount;
    @FXML private Label lblTotalWeight;
    @FXML private Label lblTotalQty;
    @FXML private VBox filterBox;
    @FXML private Button btnToggle;
    // ── Summary TableView + columns ───────────────────────────────
    @FXML private TableView<JobWorkerSummaryRow>     tblSummary;
    @FXML private TableColumn<JobWorkerSummaryRow, Integer> colSlNo;
   // @FXML private TableColumn<JobWorkerSummaryRow, String>     colSlNo;
    @FXML private TableColumn<JobWorkerSummaryRow, String>     colProductName;
    @FXML private TableColumn<JobWorkerSummaryRow, String>     colTotalPicks;
    @FXML private TableColumn<JobWorkerSummaryRow, String>     colLength;
    //@FXML private TableColumn<JobWorkerSummaryRow, String>     colWeight;
   // @FXML private TableColumn<JobWorkerSummaryRow, String>     colQuantity;
    @FXML private TableColumn<JobWorkerSummaryRow, String>     colUnit;
    @FXML private TableColumn<JobWorkerSummaryRow, BigDecimal> colPaisa;  // editable
    @FXML private TableColumn<JobWorkerSummaryRow, BigDecimal> colRate;   // editable
   // @FXML private TableColumn<JobWorkerSummaryRow, String>     colTotal;
    @FXML private TableColumn<JobWorkerSummaryRow, BigDecimal> colWeight;
    @FXML private TableColumn<JobWorkerSummaryRow, BigDecimal> colQuantity;
    @FXML private TableColumn<JobWorkerSummaryRow, BigDecimal> colTotal;
    // ── Footer controls ───────────────────────────────────────────
    @FXML private Label     lblTotal;
    @FXML private TextField tfAdvanceMoney;   // editable, red tint
    @FXML private TextField tfPreviousMoney;  // editable, green tint
    @FXML private Label     lblGrandTotal;
    @FXML private Label lblTotalWork;
    @FXML private Label lblPaid;
    @FXML private Label lblBalance;
    @FXML private Label lblAdvanceCalc;
    @FXML private Label lblDifference;
    @FXML private TableColumn<JobWorkerSummaryRow, String> colStatus;
    @Autowired private MoneyService moneyService;
    // ── Observable data list ──────────────────────────────────────
    private final ObservableList<JobWorkerSummaryRow> rows =
            FXCollections.observableArrayList();

    // ════════════════════════════════════════════════════════════
    //  INITIALIZE
    // ════════════════════════════════════════════════════════════
    @Override
    public void initialize(URL url, ResourceBundle rb) {

        // Populate worker dropdown
       // cbWorker.setItems(FXCollections.observableArrayList(
               // workerService.findAll()));


        // Default date range: first of current month → today
       // dpFrom.setValue(LocalDate.now().withDayOfMonth(1));
        //dpTo.setValue(LocalDate.now());
        JobWorkerComboBoxUtil.setup(
                cbWorker,
                workerService.findAll()
        );

        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);

                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                setText(status);

                switch (status) {
                    case "PAID":
                        setStyle("-fx-text-fill:green; -fx-font-weight:bold;");
                        break;
                    case "PARTIAL":
                        setStyle("-fx-text-fill:orange; -fx-font-weight:bold;");
                        break;
                    case "ADVANCE":
                        setStyle("-fx-text-fill:blue; -fx-font-weight:bold;");
                        break;
                    default:
                        setStyle("-fx-text-fill:red; -fx-font-weight:bold;");
                }
            }
        });
        // ── Wire read-only columns ───────────────────────────────
       /** colSlNo.setCellValueFactory(c ->
                new SimpleStringProperty(
                        String.valueOf(c.getValue().getSlNo())));*/
        colSlNo.setCellValueFactory(c ->
                new SimpleObjectProperty<>(c.getValue().getSlNo()));

        colProductName.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getProductName()));

        colTotalPicks.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getTotalPicks()));

        colLength.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getLength()));
       /** colWeight.setCellValueFactory(c ->
                new SimpleStringProperty(fmt2(c.getValue().getWeight())));

        colQuantity.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().getQuantity().toPlainString()));
*/
        colUnit.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getUnit()));

       /** colTotal.setCellValueFactory(c ->
                new SimpleStringProperty(fmt2(c.getValue().getTotalAmount()))););
        */// 🔥 ROW CLICK → OPEN LEDGER
        tblSummary.setRowFactory(tv -> {
            TableRow<JobWorkerSummaryRow> row = new TableRow<>();

            row.setOnMouseClicked(event -> {

                if (row.isEmpty()) return;

                // 🔥 BLOCK when editing cell
                if (tblSummary.getEditingCell() != null) return;

                // 🔥 Only trigger when clicking EMPTY area (not cell)
                if (event.getTarget() instanceof TableCell) return;

                if (event.getClickCount() == 2) {

                    JobWorker worker = cbWorker.getValue();
                    if (worker == null) return;

                    openLedgerScreen(worker);
                }
            });

            return row;
        });
        colWeight.setCellValueFactory(c ->
                new SimpleObjectProperty<>(c.getValue().getWeight()));

        colQuantity.setCellValueFactory(c ->
                new SimpleObjectProperty<>(c.getValue().getQuantity()));

        colTotal.setCellValueFactory(c ->
                new SimpleObjectProperty<>(c.getValue().getTotalAmount()));

        // ── Make table editable ──────────────────────────────────
        tblSummary.setEditable(true);

        // ── Wire editable Paisa and Rate columns ─────────────────
        setupPaisaColumn();
        setupRateColumn();

        // ── Add Edit + Delete columns programmatically ────────────
        addEditColumn();
        addDeleteColumn();
        tblSummary.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tblSummary.setFixedCellSize(35);

        tblSummary.prefHeightProperty().bind(
                Bindings.min(
                        tblSummary.fixedCellSizeProperty()
                                .multiply(Bindings.size(tblSummary.getItems()).add(1.01)),
                        300   // 🔥 MAX HEIGHT LIMIT (adjust if needed)
                )
        );
        tblSummary.setItems(rows);
        colWeight.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal val, boolean empty) {
                super.updateItem(val, empty);
                setText(empty ? "" : fmt2(val));
            }
        });

        colQuantity.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal val, boolean empty) {
                super.updateItem(val, empty);
                setText(empty ? "" : val.toPlainString());
            }
        });

        colTotal.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal val, boolean empty) {
                super.updateItem(val, empty);
                setText(empty ? "" : fmt2(val));
            }
        });

        // ── Wire footer TextFields ────────────────────────────────
        setupAdvanceMoneyField();
        setupPreviousMoneyField();
    }

    // ════════════════════════════════════════════════════════════
    //  PAISA COLUMN — amber tint, editable
    //
    //  After pressing Enter:
    //    Platform.runLater → tblSummary.requestFocus()
    //  This keeps focus in the TABLE, not cbWorker.
    //  Paisa is standalone — it does NOT calculate Total.
    //  Total is calculated from Rate × Quantity.
    // ════════════════════════════════════════════════════════════
    @FXML
    private void onToggleFilters() {

        boolean visible = filterBox.isVisible();

        filterBox.setVisible(!visible);
        filterBox.setManaged(!visible); // 🔥 IMPORTANT

        btnToggle.setText(visible ? "👁 Show Filters" : "🙈 Hide Filters");
    }
    private void openLedgerScreen(JobWorker worker) {

        try {
            GlobalUI.openWindow(
                    "/fxml/advance_ledger.fxml",
                    "Worker Ledger - " + worker.getName(),
                    controller -> {
                        if (controller instanceof AdvanceLedgerController c) {
                            c.setWorker(worker);   // 🔥 PASS WORKER
                        }
                    }
            );
        } catch (Exception e) {
            GlobalUI.warn("Failed to open ledger: " + e.getMessage());
        }
    }
    private void setupPaisaColumn() {
        colPaisa.setEditable(true);
        colPaisa.setCellValueFactory(c ->
                new SimpleObjectProperty<>(c.getValue().getPaisa()));

        colPaisa.setCellFactory(col -> {
            TextFieldTableCell<JobWorkerSummaryRow, BigDecimal> cell =
                    new TextFieldTableCell<>(new BigDecimalStringConverter() {
                        @Override public BigDecimal fromString(String s) {
                            try { return (s == null || s.isBlank())
                                    ? BigDecimal.ZERO : new BigDecimal(s.trim()); }
                            catch (NumberFormatException e) { return BigDecimal.ZERO; }
                        }
                        @Override public String toString(BigDecimal v) {
                            return v == null ? "0.00" : fmt2(v);
                        }
                    });
            // Amber tint — signals "editable"
            cell.setStyle("-fx-background-color:#fffbeb;-fx-text-fill:#92400e;" +
                    "-fx-font-weight:bold;");
            return cell;
        });

        colPaisa.setOnEditCommit(event -> {
            JobWorkerSummaryRow row = event.getRowValue();
            row.setPaisa(event.getNewValue() != null
                    ? event.getNewValue() : BigDecimal.ZERO);
            tblSummary.refresh();
            recalculateFooter();
            // FIX: return focus to table — NOT to cbWorker
            Platform.runLater(() -> tblSummary.requestFocus());
        });
    }

    // ════════════════════════════════════════════════════════════
    //  RATE COLUMN — green tint, editable
    //  After commit: Total = Quantity × Rate (auto-calculated).
    //  Same focus fix as Paisa.
    // ════════════════════════════════════════════════════════════
    private void setupRateColumn() {
        colRate.setEditable(true);
        colRate.setCellValueFactory(c ->
                new SimpleObjectProperty<>(c.getValue().getRate()));

        colRate.setCellFactory(col -> {
            TextFieldTableCell<JobWorkerSummaryRow, BigDecimal> cell =
                    new TextFieldTableCell<>(new BigDecimalStringConverter() {
                        @Override public BigDecimal fromString(String s) {
                            try { return (s == null || s.isBlank())
                                    ? BigDecimal.ZERO : new BigDecimal(s.trim()); }
                            catch (NumberFormatException e) { return BigDecimal.ZERO; }
                        }
                        @Override public String toString(BigDecimal v) {
                            return v == null ? "0.00" : fmt2(v);
                        }
                    });
            // Green tint — signals "drives Total"
            cell.setStyle("-fx-background-color:#f0fdf4;-fx-text-fill:#065f46;" +
                    "-fx-font-weight:bold;");
            return cell;
        });

        colRate.setOnEditCommit(event -> {
            JobWorkerSummaryRow row = event.getRowValue();
            BigDecimal rate = event.getNewValue() != null
                    ? event.getNewValue() : BigDecimal.ZERO;
            row.setRate(rate);
            // Total = Quantity × Rate
            row.setTotal(row.getQuantity()
                    .multiply(rate)
                    .setScale(2, RoundingMode.HALF_UP));
            tblSummary.refresh();
            recalculateFooter();
            // FIX: focus back to table
            Platform.runLater(() -> tblSummary.requestFocus());
        });
    }

    // ════════════════════════════════════════════════════════════
    //  ADVANCE MONEY FIELD
    //  Enter/Tab: recalculate + focus table + consume event
    //  consume() prevents default Tab traversal to cbWorker.
    // ════════════════════════════════════════════════════════════
    private void setupAdvanceMoneyField() {
        if (tfAdvanceMoney == null) return;
        tfAdvanceMoney.setPromptText("0.00");
        tfAdvanceMoney.setText("0.00");

        tfAdvanceMoney.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER
                    || event.getCode() == KeyCode.TAB) {
                recalculateFooter();
                Platform.runLater(() -> tblSummary.requestFocus());
                event.consume(); // stop Tab from moving to cbWorker
            }
        });

        // Also recalculate when field loses focus
        tfAdvanceMoney.focusedProperty().addListener(
                (obs, wasFocused, isFocused) -> {
                    if (wasFocused && !isFocused) recalculateFooter();
                });
    }

    // ════════════════════════════════════════════════════════════
    //  PREVIOUS MONEY FIELD — same pattern as Advance
    // ════════════════════════════════════════════════════════════
    private void setupPreviousMoneyField() {
        if (tfPreviousMoney == null) return;
        tfPreviousMoney.setPromptText("0.00");
        tfPreviousMoney.setText("0.00");

        tfPreviousMoney.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER
                    || event.getCode() == KeyCode.TAB) {
                recalculateFooter();
                Platform.runLater(() -> tblSummary.requestFocus());
                event.consume();
            }
        });

        tfPreviousMoney.focusedProperty().addListener(
                (obs, wasFocused, isFocused) -> {
                    if (wasFocused && !isFocused) recalculateFooter();
                });
    }

    // ════════════════════════════════════════════════════════════
    //  SEARCH
    // ════════════════════════════════════════════════════════════

    @FXML
    public void onSearch() {

        if (cbWorker.getValue() == null) {
            GlobalUI.warn("Please select a Job Worker."); return;
        }
        if (dpFrom.getValue() == null || dpTo.getValue() == null) {
            GlobalUI.warn("Please select both From and To dates."); return;
        }
        if (dpFrom.getValue().isAfter(dpTo.getValue())) {
            GlobalUI.warn("From Date cannot be after To Date."); return;
        }

        Long wid = cbWorker.getValue().getId();
        LocalDate from = dpFrom.getValue();
        LocalDate to = dpTo.getValue();

        // 🔹 Load data
        List<JobWorkerSummaryRow> data =
                summaryService.buildSummaryRows(wid, from, to);

        rows.setAll(data);
        // 🔥 AUTO HIDE FILTER AFTER SEARCH
        filterBox.setVisible(false);
        filterBox.setManaged(false);
        btnToggle.setText("👁 Show Filters");

        // =====================================================
        // 🔥 CORRECT CALCULATION (FROM SERVICE)
        // =====================================================
        SummaryTotals t =
                summaryService.calculateTotals(wid, from, to, data);

        lblTotalWork.setText("₹ " + fmt2(t.getTotalWork()));
        lblPaid.setText("₹ " + fmt2(t.getTotalPaid()));

        // 🔥 IMPORTANT FIX
        lblBalance.setText("₹ " + fmt2(t.getOpening()));

        lblDifference.setText("₹ " + fmt2(t.getDifference()));

        // 🔹 COLOR
        if (t.getDifference().compareTo(BigDecimal.ZERO) < 0) {
            lblDifference.setStyle("-fx-text-fill:blue; -fx-font-weight:bold;");
        } else if (t.getDifference().compareTo(BigDecimal.ZERO) > 0) {
            lblDifference.setStyle("-fx-text-fill:red; -fx-font-weight:bold;");
        } else {
            lblDifference.setStyle("-fx-text-fill:green; -fx-font-weight:bold;");
        }

        // 🔹 HEADER
        lblWorkerName.setText(cbWorker.getValue().getName());
        lblDateRange.setText(
                GlobalUI.formatDate(from) + "  →  " + GlobalUI.formatDate(to)
        );
        lblRowCount.setText(String.valueOf(data.size()));

        // 🔹 TOTALS
        BigDecimal sumWt = data.stream()
                .map(JobWorkerSummaryRow::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal sumQty = data.stream()
                .map(JobWorkerSummaryRow::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        lblTotalWeight.setText("Total Wt: " + fmt2(sumWt) + " kg");
        lblTotalQty.setText("Total Qty: " + sumQty.toPlainString());

        recalculateFooter();
        lblStatus.setText("● " + data.size() + " rows found");
    }
    // ════════════════════════════════════════════════════════════
    //  FOOTER RECALCULATION
    //  Grand Total = Total − Advance Money + Previous Money
    // ════════════════════════════════════════════════════════════
    private void recalculateFooter() {
        BigDecimal total = rows.stream()
                .map(JobWorkerSummaryRow::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal advance = parseBd(
                tfAdvanceMoney  != null ? tfAdvanceMoney.getText()  : "0");
        BigDecimal previous = parseBd(
                tfPreviousMoney != null ? tfPreviousMoney.getText() : "0");

        BigDecimal grand = summaryService.grandTotal(total, advance, previous);

        lblTotal.setText("₹ " + fmt2(total));
        lblGrandTotal.setText("₹ " + fmt2(grand));
    }


    // ════════════════════════════════════════════════════════════
    //  RESET
    // ════════════════════════════════════════════════════════════
    @FXML
    public void onReset() {

        // =====================================================
        // 🔥 1. CLEAR FILTERS (REAL RESET)
        // =====================================================
        cbWorker.getSelectionModel().clearSelection();
        dpFrom.setValue(null);
        dpTo.setValue(null);

        // =====================================================
        // 🔥 2. CLEAR TABLE DATA
        // =====================================================
        rows.clear();
        tblSummary.getItems().clear();

        // =====================================================
        // 🔥 3. CLEAR HEADER INFO
        // =====================================================
        lblWorkerName.setText("—");
        lblDateRange.setText("—");
        lblRowCount.setText("0");
        lblStatus.setText("● Select Worker");

        // =====================================================
        // 🔥 4. CLEAR SUMMARY LABELS
        // =====================================================
        lblTotalWork.setText("₹ 0.00");
        lblPaid.setText("₹ 0.00");
        lblBalance.setText("₹ 0.00");
        lblAdvanceCalc.setText("₹ 0.00");
        lblDifference.setText("₹ 0.00");

        // Reset color
        lblDifference.setStyle("-fx-text-fill:#1e293b;");

        // =====================================================
        // 🔥 5. CLEAR TOTALS
        // =====================================================
        lblTotalWeight.setText("Total Wt: 0.00 kg");
        lblTotalQty.setText("Total Qty: 0");

        lblTotal.setText("₹ 0.00");
        lblGrandTotal.setText("₹ 0.00");

        // =====================================================
        // 🔥 6. RESET INPUT FIELDS
        // =====================================================
        if (tfAdvanceMoney != null) tfAdvanceMoney.setText("0.00");
        if (tfPreviousMoney != null) tfPreviousMoney.setText("0.00");

        // =====================================================
        // 🔥 7. REFRESH TABLE UI
        // =====================================================
        tblSummary.refresh();
    }

    // ════════════════════════════════════════════════════════════
    //  PDF EXPORT
    // ════════════════════════════════════════════════════════════
    @FXML
    public void onPdf() {
        if (!GlobalUI.validateExport(rows)) return;
        FileChooser fc = new FileChooser();
        String wn = cbWorker.getValue() != null
                ? cbWorker.getValue().getName().replaceAll("\\s+", "_") : "worker";
        fc.setInitialFileName("summary_" + wn + ".pdf");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File f = fc.showSaveDialog(tblSummary.getScene().getWindow());
        if (f != null) {
            try {
                pdfExporter.exportJobWorkerSummary(
                        new ArrayList<>(rows),
                        cbWorker.getValue() != null
                                ? cbWorker.getValue().getName() : "",
                        dpFrom.getValue(), dpTo.getValue(),
                        lblTotal.getText(),
                        "₹ " + (tfAdvanceMoney  != null
                                ? tfAdvanceMoney.getText()  : "0.00"),
                        "₹ " + (tfPreviousMoney != null
                                ? tfPreviousMoney.getText() : "0.00"),
                        lblGrandTotal.getText(),
                        f.toPath());
                GlobalUI.success("PDF saved:\n" + f.getAbsolutePath());
            } catch (IOException ex) {
                GlobalUI.warn("PDF failed: " + ex.getMessage());
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    //  EXCEL EXPORT
    // ════════════════════════════════════════════════════════════
    @FXML
    public void onExcel() {
        if (!GlobalUI.validateExport(rows)) return;
        FileChooser fc = new FileChooser();
        String wn = cbWorker.getValue() != null
                ? cbWorker.getValue().getName().replaceAll("\\s+", "_") : "worker";
        fc.setInitialFileName("summary_" + wn + ".xlsx");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        File f = fc.showSaveDialog(tblSummary.getScene().getWindow());
        if (f != null) {
            try {
                excelExporter.exportJobWorkerSummary(
                        new ArrayList<>(rows),
                        cbWorker.getValue() != null
                                ? cbWorker.getValue().getName() : "",
                        dpFrom.getValue(), dpTo.getValue(),
                        lblTotal.getText(),
                        "₹ " + (tfAdvanceMoney  != null
                                ? tfAdvanceMoney.getText()  : "0.00"),
                        "₹ " + (tfPreviousMoney != null
                                ? tfPreviousMoney.getText() : "0.00"),
                        lblGrandTotal.getText(),
                        f.toPath());
                GlobalUI.success("Excel saved:\n" + f.getAbsolutePath());
            } catch (IOException ex) {
                GlobalUI.warn("Excel failed: " + ex.getMessage());
            }
        }
    }

    @FXML
    public void onPrint() {
        GlobalUI.success("Generate PDF first, then print from your PDF viewer.");
    }

    // ════════════════════════════════════════════════════════════
    //  EDIT COLUMN — programmatic, opens dialog
    // ════════════════════════════════════════════════════════════
    private void addEditColumn() {
        TableColumn<JobWorkerSummaryRow, Void> col = new TableColumn<>("Edit");
        col.setPrefWidth(72);
        col.setStyle("-fx-alignment:CENTER;");
        col.setCellFactory(c -> new TableCell<>() {
            final Button btn = new Button("✏ Edit");
            {
                btn.setStyle(
                        "-fx-background-color:linear-gradient(to right,#1e3a8a,#1d4ed8);" +
                                "-fx-text-fill:white;-fx-font-size:11px;-fx-font-weight:bold;" +
                                "-fx-padding:4 10;-fx-background-radius:6;-fx-cursor:hand;");
                btn.setOnAction(e ->
                        openEditDialog(getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btn);
            }
        });
        tblSummary.getColumns().add(col);
    }

    // ════════════════════════════════════════════════════════════
    //  DELETE COLUMN — programmatic
    // ════════════════════════════════════════════════════════════
    private void addDeleteColumn() {
        TableColumn<JobWorkerSummaryRow, Void> col = new TableColumn<>("Del");
        col.setPrefWidth(66);
        col.setStyle("-fx-alignment:CENTER;");

        col.setCellFactory(c -> new TableCell<>() {
            final Button btn = new Button("🗑 Del");

            {
                btn.setStyle(
                        "-fx-background-color:linear-gradient(to right,#dc2626,#b91c1c);" +
                                "-fx-text-fill:white;-fx-font-size:11px;-fx-font-weight:bold;" +
                                "-fx-padding:4 10;-fx-background-radius:6;-fx-cursor:hand;");

                btn.setOnAction(e -> {

                    JobWorkerSummaryRow row =
                            getTableView().getItems().get(getIndex());

                    if (row != null && GlobalUI.confirm("Delete Row", "Delete this row?")) {

                        rows.remove(row);

                        for (int i = 0; i < rows.size(); i++) {
                            rows.get(i).setSlNo(i + 1);
                        }

                        tblSummary.refresh();
                        recalculateFooter();
                        lblRowCount.setText(String.valueOf(rows.size()));
                    }
                });
            }

            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btn);
            }
        });

        tblSummary.getColumns().add(col);
    }
    // ════════════════════════════════════════════════════════════
    //  EDIT DIALOG — all 8 fields editable
    // ════════════════════════════════════════════════════════════
    private void openEditDialog(JobWorkerSummaryRow row) {
        Dialog<JobWorkerSummaryRow> dialog = new Dialog<>();
        dialog.setTitle("Edit Summary Row");
        dialog.setHeaderText("Editing: " + row.getProductName());

        ButtonType saveBtn = new ButtonType("💾 Save",
                ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
        dialog.getDialogPane().setMinWidth(440);

        javafx.scene.layout.GridPane grid =
                new javafx.scene.layout.GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.setStyle("-fx-padding:16;");

        TextField tfName       = tf(row.getProductName(),              220);
        TextField tfTotalPicks = tf(row.getTotalPicks(),               120);
        TextField tfLen        = tf(row.getLength(),                   120);
        TextField tfQty        = tf(row.getQuantity().toPlainString(), 110);
        TextField tfWt         = tf(fmt2(row.getWeight()),             110);
        TextField tfUnit       = tf(row.getUnit(),                     110);
        TextField tfPaisa      = tf(fmt2(row.getPaisa()),              110);
        tfPaisa.setStyle("-fx-background-color:#fffbeb;-fx-border-color:#fcd34d;");
        TextField tfRate = tf(fmt2(row.getRate()), 110);
        tfRate.setStyle("-fx-background-color:#f0fdf4;-fx-border-color:#86efac;");

        grid.addRow(0, lbl("Product Name:"), tfName);
        grid.addRow(1, lbl("Total Picks:"),  tfTotalPicks);
        grid.addRow(2, lbl("Length:"),       tfLen);
        grid.addRow(3, lbl("Quantity:"),     tfQty);
        grid.addRow(4, lbl("Weight (kg):"),  tfWt);
        grid.addRow(5, lbl("Unit:"),         tfUnit);
        grid.addRow(6, lbl("Paisa (₹):"),   tfPaisa);
        grid.addRow(7, lbl("Rate (₹):"),     tfRate);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn != saveBtn) return null;
            try {
                row.setProductName(tfName.getText().trim());
                row.setTotalPicks(tfTotalPicks.getText().trim());
                row.setLength(tfLen.getText().trim());
                row.setUnit(tfUnit.getText().trim());
                BigDecimal qty   = new BigDecimal(tfQty.getText().trim());
                BigDecimal wt    = new BigDecimal(tfWt.getText().trim());
                BigDecimal paisa = new BigDecimal(tfPaisa.getText().trim());
                BigDecimal rate  = new BigDecimal(tfRate.getText().trim());
                row.setQuantity(qty);
                row.setWeight(wt);
                row.setPaisa(paisa);
                row.setRate(rate);
                row.setTotal(qty.multiply(rate).setScale(2, RoundingMode.HALF_UP));
                return row;
            } catch (NumberFormatException ex) {
                GlobalUI.warn("Invalid number entered."); return null;
            }
        });

        dialog.showAndWait().ifPresent(updated -> {
            tblSummary.refresh();
            recalculateFooter();
        });
    }

    // ════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════

    private String fmt2(BigDecimal v) {
        return v == null ? "0.00"
                : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private BigDecimal parseBd(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        s = s.replace("₹", "").replace(",", "").trim();
        try { return new BigDecimal(s); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private Label lbl(String t) {
        Label l = new Label(t);
        l.setStyle("-fx-font-weight:bold;");
        return l;
    }

    private TextField tf(String val, double w) {
        TextField t = new TextField(val);
        t.setPrefWidth(w);
        return t;
    }

  /**  private void warn(String m) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setHeaderText(null); a.setContentText(m); a.showAndWait();
    }

    private void info(String m) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null); a.setContentText(m); a.showAndWait();
    }*/
}