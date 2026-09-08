package com.jobwork.controller;

import com.jobwork.config.StageManager;
import com.jobwork.domain.EntryType;
import com.jobwork.domain.JobWorker;
import com.jobwork.domain.OpeningAdjustment;
import com.jobwork.domain.WorkerAccountLedgerRow;
import com.jobwork.service.AccountLedgerService;
import com.jobwork.service.JobWorkerService;
import com.jobwork.util.ExcelExporter;
import com.jobwork.util.GlobalUI;
import com.jobwork.util.JobWorkerComboBoxUtil;
import com.jobwork.util.PdfExporter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.jobwork.service.OpeningAdjustmentService;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class AccountLedgerController {

    @FXML private ComboBox<JobWorker> cbWorker;
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;

    @FXML private Label lblDebit;
    @FXML private Label lblCredit;
    @FXML private Label lblBalance;
    @FXML private ToggleButton btnAll;
    @FXML private ToggleButton btnCredit;
    @FXML private ToggleButton btnDebit;
    @FXML private TableView<WorkerAccountLedgerRow> tblLedger;

    @FXML private TableColumn<WorkerAccountLedgerRow, String> colDate;
    @FXML private TableColumn<WorkerAccountLedgerRow, String> colParticular;
    @FXML private TableColumn<WorkerAccountLedgerRow, String> colDebit;
    @FXML private TableColumn<WorkerAccountLedgerRow, String> colCredit;
    @FXML private TableColumn<WorkerAccountLedgerRow, String> colBalance;



    @Autowired private JobWorkerService workerService;
    @Autowired private AccountLedgerService ledgerService;
    @Autowired private OpeningAdjustmentService adjustmentService;
    @Autowired private ExcelExporter excelExporter;
    @Autowired private PdfExporter pdfExporter;
    @Autowired private StageManager stageManager;

    @FXML
    public void initialize() {

      //  cbWorker.setItems(FXCollections.observableArrayList(workerService.findAll()));
        JobWorkerComboBoxUtil.setup(
                cbWorker,
                workerService.findAll()
        );
        colDate.setCellValueFactory(c -> new SimpleStringProperty(
                GlobalUI.formatDate(c.getValue().getDate())));

        colParticular.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getParticular()));

        colDebit.setCellValueFactory(c ->
                new SimpleStringProperty(format(c.getValue().getDebit())));

        colCredit.setCellValueFactory(c ->
                new SimpleStringProperty(format(c.getValue().getCredit())));

        colBalance.setCellValueFactory(c ->
                new SimpleStringProperty(format(c.getValue().getBalance())));

        // 🔥 Balance color
        colBalance.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);

                if (empty || val == null) {
                    setText(null);
                    return;
                }

                setText(val);

                try {
                    double num = Double.parseDouble(val.replace("₹", "").trim());

                    if (num > 0) {
                        setStyle("-fx-text-fill:green; -fx-font-weight:bold;");
                    } else if (num < 0) {
                        setStyle("-fx-text-fill:red; -fx-font-weight:bold;");
                    } else {
                        setStyle("-fx-text-fill:black;");
                    }

                } catch (Exception e) {
                    setStyle("");
                }
            }
        });

        colDebit.setStyle("-fx-alignment: CENTER-RIGHT;");
        colCredit.setStyle("-fx-alignment: CENTER-RIGHT;");
        colBalance.setStyle("-fx-alignment: CENTER-RIGHT;");

        // 🔥 Highlight rows
        tblLedger.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(WorkerAccountLedgerRow data, boolean empty) {
                super.updateItem(data, empty);

                if (data == null || empty) {
                    setStyle("");
                    return;
                }

                if ("Opening Balance".equals(data.getParticular()) ||
                        "Closing Balance".equals(data.getParticular())) {

                    setStyle("-fx-background-color:#f3f4f6; -fx-font-weight:bold;");
                } else {
                    setStyle("");
                }
            }
        });

        // 🔥 Double click → open challan
        tblLedger.setOnMouseClicked(event -> {

            if (event.getClickCount() != 2) return;

            WorkerAccountLedgerRow data =
                    tblLedger.getSelectionModel().getSelectedItem();

            if (data == null) return;

            if (data.getChallanNo() != null &&
                    !"Opening Balance".equals(data.getParticular()) &&
                    !"Closing Balance".equals(data.getParticular()) &&
                    cbWorker.getValue() != null) {

                stageManager.showChallanDetail(
                        cbWorker.getValue().getId(),
                        data.getChallanNo()
                );
            }
        });
        ToggleGroup group = new ToggleGroup();

        btnAll.setToggleGroup(group);
        btnCredit.setToggleGroup(group);
        btnDebit.setToggleGroup(group);
        btnAll.setOnAction(e -> onSearch());
        btnCredit.setOnAction(e -> onSearch());
        btnDebit.setOnAction(e -> onSearch());

// default
        btnAll.setSelected(true);
    }

    // ONLY showing changed parts clearly — everything else same

    @FXML
    public void onSearch() {

        if (cbWorker.getValue() == null) {
            GlobalUI.warn("Select worker");
            return;
        }

        Long workerId = cbWorker.getValue().getId();
        LocalDate from = dpFrom.getValue();
        LocalDate to = dpTo.getValue();

        // 🔥 LOAD FULL LEDGER
        List<WorkerAccountLedgerRow> data =
                ledgerService.getLedger(workerId, null, to);

        List<WorkerAccountLedgerRow> filtered = new ArrayList<>();

        // =====================================================
        // 🔥 FILTER
        // =====================================================
        for (WorkerAccountLedgerRow r : data) {

            String p = r.getParticular();

            // ❌ SKIP ORIGINAL CLOSING (VERY IMPORTANT)
            if ("Closing Balance".equals(p)) continue;

            // ✅ Always include Opening
            if ("Opening Balance".equals(p)) {
                filtered.add(r);
                continue;
            }

            // DATE FILTER
            if (from != null && r.getDate() != null && r.getDate().isBefore(from)) continue;
            if (to != null && r.getDate() != null && r.getDate().isAfter(to)) continue;

            // TOGGLE FILTER
            if (btnAll.isSelected()) {
                filtered.add(r);
            }
            else if (btnCredit.isSelected()) {
                if (r.getCredit() != null && r.getCredit().compareTo(BigDecimal.ZERO) > 0) {
                    filtered.add(r);
                }
            }
            else if (btnDebit.isSelected()) {
                if (r.getDebit() != null && r.getDebit().compareTo(BigDecimal.ZERO) > 0) {
                    filtered.add(r);
                }
            }
        }

        // =====================================================
        // 🔥 ADD SINGLE CLOSING (CORRECT WAY)
        // =====================================================
        BigDecimal finalBalance = filtered.isEmpty()
                ? BigDecimal.ZERO
                : filtered.get(filtered.size() - 1).getBalance();

        WorkerAccountLedgerRow closing = new WorkerAccountLedgerRow();
        closing.setDate(to);
        closing.setParticular("Closing Balance");
        closing.setDebit(BigDecimal.ZERO);
        closing.setCredit(BigDecimal.ZERO);
        closing.setBalance(finalBalance);

        filtered.add(closing);

        // =====================================================
        // 🔥 TOTALS (ONLY FILTERED DATA)
        // =====================================================
        BigDecimal debit = filtered.stream()
                .filter(r -> !"Opening Balance".equals(r.getParticular()))
                .map(r -> r.getDebit() == null ? BigDecimal.ZERO : r.getDebit())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal credit = filtered.stream()
                .filter(r -> !"Opening Balance".equals(r.getParticular()))
                .map(r -> r.getCredit() == null ? BigDecimal.ZERO : r.getCredit())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // =====================================================
        // 🔥 UI
        // =====================================================
        tblLedger.setItems(FXCollections.observableArrayList(filtered));

        lblDebit.setText(format(debit));
        lblCredit.setText(format(credit));
        lblBalance.setText(format(finalBalance));
    }
    @FXML
    private void onToday() {
        LocalDate today = LocalDate.now();
        dpFrom.setValue(today);
        dpTo.setValue(today);
        onSearch();
    }

    @FXML
    private void onWeek() {
        LocalDate today = LocalDate.now();
        dpFrom.setValue(today.minusDays(6)); // last 7 days
        dpTo.setValue(today);
        onSearch();
    }

    @FXML
    private void onMonth() {
        LocalDate today = LocalDate.now();
        dpFrom.setValue(today.withDayOfMonth(1)); // 1st day
        dpTo.setValue(today);
        onSearch();
    }

    @FXML
    public void onReset() {

        // =====================================================
        // 🔥 1. CLEAR FILTERS
        // =====================================================
        dpFrom.setValue(null);
        dpTo.setValue(null);

        // Optional: reset worker
        // cbWorker.setValue(null);

        // Reset toggle buttons
        btnAll.setSelected(true);

        // =====================================================
        // 🔥 2. CLEAR TABLE (VERY IMPORTANT)
        // =====================================================
        tblLedger.getItems().clear();

        // =====================================================
        // 🔥 3. RESET TOTALS
        // =====================================================
        lblDebit.setText("₹ 0.00");
        lblCredit.setText("₹ 0.00");
        lblBalance.setText("₹ 0.00");

    }
    @FXML
    public void onExcel() {
        try {
            File file = choose("Excel", "*.xlsx");
            if (file == null) return;

            excelExporter.exportAccountLedgerProExcel(
                    "PYARE LAL",
                    cbWorker.getValue().getName(),
                    getDateRange(),
                    new ArrayList<>(tblLedger.getItems()),
                    file.toPath()
            );

            GlobalUI.success("Excel exported");
        } catch (Exception e) {
            GlobalUI.warn(e.getMessage());
        }
    }

    @FXML
    public void onPdf() {
        try {
            File file = choose("PDF", "*.pdf");
            if (file == null) return;

            pdfExporter.exportAccountLedgerPro(
                    "PYARE LAL",
                    cbWorker.getValue().getName(),
                    getDateRange(),
                    new ArrayList<>(tblLedger.getItems()),
                    file.toPath()
            );

            GlobalUI.success("PDF exported");
        } catch (Exception e) {
            GlobalUI.warn(e.getMessage());
        }
    }

    private String format(BigDecimal v) {
        return "₹ " + String.format("%.2f", v == null ? BigDecimal.ZERO : v);
    }

    private File choose(String title, String ext) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save " + title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(title, ext));
        return fc.showSaveDialog(null);
    }

    private String getDateRange() {
        if (dpFrom.getValue() == null && dpTo.getValue() == null)
            return "All Dates";

        return (dpFrom.getValue() != null ? dpFrom.getValue() : "Start")
                + " to " +
                (dpTo.getValue() != null ? dpTo.getValue() : "End");
    }
    @FXML
    public void onAddAdjustment() {

        Dialog<OpeningAdjustment> dialog = new Dialog<>();
        dialog.setTitle("Opening Adjustment");

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        TextField tfAmount = new TextField();
        ComboBox<EntryType> cbType = new ComboBox<>();
        cbType.getItems().addAll(EntryType.ADVANCE, EntryType.PAYMENT);

        DatePicker dpDate = new DatePicker(LocalDate.now());
        TextField tfRemark = new TextField();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        grid.addRow(0, new Label("Amount"), tfAmount);
        grid.addRow(1, new Label("Type"), cbType);
        grid.addRow(2, new Label("Date"), dpDate);
        grid.addRow(3, new Label("Remark"), tfRemark);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn != saveBtn) return null;

            OpeningAdjustment a = new OpeningAdjustment();

            a.setWorker(cbWorker.getValue());
            a.setAmount(new BigDecimal(tfAmount.getText()));
            a.setEntryType(cbType.getValue());
            a.setDate(dpDate.getValue());
            a.setRemark(tfRemark.getText());

            return a;
        });

        dialog.showAndWait().ifPresent(a -> {
            adjustmentService.save(a);
            onSearch(); // refresh ledger
        });
    }
}