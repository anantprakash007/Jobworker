package com.jobwork.controller;

import com.jobwork.domain.*;
import com.jobwork.service.JobWorkerService;
import com.jobwork.service.MoneyService;
import com.jobwork.util.ExcelExporter;
import com.jobwork.util.GlobalUI;
import com.jobwork.util.PdfExporter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.jobwork.domain.EntryType;
import java.io.File;
import java.math.BigDecimal;
import java.util.*;

@Component
public class AdvanceLedgerController {

    @FXML private ComboBox<JobWorker> cbWorker;
    @FXML private TableView<WorkerLedgerRow> tblLedger;
    @FXML private Label lblTotal;

    @FXML private TableColumn<WorkerLedgerRow, String> colDate;
    @FXML private TableColumn<WorkerLedgerRow, String> colType;
    @FXML private TableColumn<WorkerLedgerRow, String> colAmount;
    @FXML private TableColumn<WorkerLedgerRow, String> colBalance;

    @Autowired private JobWorkerService workerService;
    @Autowired private MoneyService moneyService;
    @Autowired private ExcelExporter excelExporter;
    @Autowired private PdfExporter pdfExporter;

    private JobWorker worker;

    @FXML
    public void initialize() {

        cbWorker.setItems(FXCollections.observableArrayList(workerService.findAll()));

        colDate.setCellValueFactory(c ->
                new SimpleStringProperty(GlobalUI.formatDate(c.getValue().getDate()))
        );

        colType.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getType())
        );

        colAmount.setCellValueFactory(c ->
                new SimpleStringProperty("₹ " + format(c.getValue().getAmount()))
        );

        colBalance.setCellValueFactory(c ->
                new SimpleStringProperty("₹ " + format(c.getValue().getBalance()))
        );

        colAmount.setStyle("-fx-alignment: CENTER-RIGHT;");
        colBalance.setStyle("-fx-alignment: CENTER-RIGHT;");
        colType.setStyle("-fx-alignment: CENTER;");
    }

    @FXML
    public void onLoad() {
        if (cbWorker.getValue() == null) {
            GlobalUI.warn("Select worker");
            return;
        }
        worker = cbWorker.getValue();
        loadData();
    }

    public void setWorker(JobWorker worker) {
        this.worker = worker;
        cbWorker.setValue(worker);
        loadData();
    }

    private void loadData() {

        if (worker == null) return;

        List<MoneyReceipt> receipts =
                moneyService.searchReport(worker.getId(), null, null, null);

        receipts.sort(Comparator.comparing(
                MoneyReceipt::getReceiptDate,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));

        List<WorkerLedgerRow> data = new ArrayList<>();
        BigDecimal balance = BigDecimal.ZERO;

        for (MoneyReceipt r : receipts) {

            if (r.getStatus() == EntryStatus.CANCELLED) continue;

            BigDecimal amt = safe(r.getAmount());
            if (r.getEntryType() == null || r.getEntryType() == EntryType.ADVANCE) {
                balance = balance.add(amt);
            } else {
                balance = balance.subtract(amt);
            }

            WorkerLedgerRow row = new WorkerLedgerRow();
            row.setDate(r.getReceiptDate());
            row.setType("PAYMENT");
            row.setAmount(amt);
            row.setBalance(balance);

            data.add(row);
        }

        tblLedger.setItems(FXCollections.observableArrayList(data));
        applyAutoResize(tblLedger);

        BigDecimal total = data.stream()
                .map(WorkerLedgerRow::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        lblTotal.setText("Total: ₹ " + format(total));
    }

    @FXML
    public void onExcel() {
        try{
        File file = choose("Excel", "*.xlsx");
        if (file == null) return;

        excelExporter.exportLedger(
                new ArrayList<>(tblLedger.getItems()),
                file.toPath(),
                worker != null ? worker.getName() : "ALL"
        );

        GlobalUI.success("Excel exported");
        } catch (Exception e) {
            GlobalUI.warn("Export failed: " + e.getMessage());
        }
    }

    @FXML
    public void onPdf() {
        try {
            File file = choose("PDF", "*.pdf");
            if (file == null) return;

            pdfExporter.exportLedger(
                    new ArrayList<>(tblLedger.getItems()),
                    file.toPath(),
                    worker != null ? worker.getName() : "ALL"
            );

            GlobalUI.success("PDF exported");
        } catch (Exception e) {
            GlobalUI.warn("Export failed: " + e.getMessage());
        }
    }

    // ================= UTIL =================

    private String format(BigDecimal v) {
        return String.format("%.2f", v == null ? BigDecimal.ZERO : v);
    }

    private BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private File choose(String title, String ext) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save " + title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(title, ext));
        return fc.showSaveDialog(null);
    }

    private void applyAutoResize(TableView<?> table) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

}