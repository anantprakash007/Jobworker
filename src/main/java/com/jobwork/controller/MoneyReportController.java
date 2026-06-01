package com.jobwork.controller;

import com.jobwork.domain.EntryStatus;
import com.jobwork.domain.JobWorker;
import com.jobwork.domain.MoneyReceipt;
import com.jobwork.repository.ProductEntryRepository;
import com.jobwork.service.JobWorkerService;
import com.jobwork.service.MoneyService;
import com.jobwork.util.ExcelExporter;
import com.jobwork.util.GlobalUI;
import com.jobwork.util.PdfExporter;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.util.*;

@Component
public class MoneyReportController implements Initializable {

    @Autowired private MoneyService moneyService;
    @Autowired private JobWorkerService workerService;
    @Autowired private PdfExporter pdfExporter;
    @Autowired private ExcelExporter excelExporter;
    @Autowired
    private ProductEntryRepository productEntryRepo;

    @FXML private ComboBox<JobWorker> cbWorker;
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private TextField tfChallanNo;

    @FXML private TableView<MoneyReceipt> tblReport;

    @FXML private TableColumn<MoneyReceipt, String> colDate;
    @FXML private TableColumn<MoneyReceipt, String> colChallan;
    @FXML private TableColumn<MoneyReceipt, String> colWorker;
    @FXML private TableColumn<MoneyReceipt, String> colAmount;
    @FXML private TableColumn<MoneyReceipt, String> colMode;
    @FXML private TableColumn<MoneyReceipt, String> colRemark;


    @FXML private Label lblTotalWork;
    @FXML private Label lblTotalPaid;
    @FXML private Label lblBalance;
    @Override
    public void initialize(URL url, ResourceBundle rb) {

        cbWorker.setItems(FXCollections.observableArrayList(workerService.findAll()));

        colDate.setCellValueFactory(c ->
                new SimpleStringProperty(GlobalUI.formatDate(c.getValue().getReceiptDate())));

        colChallan.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().getChallanNo() != null ? c.getValue().getChallanNo() : ""
                ));

        colWorker.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().getJobWorker() != null
                                ? c.getValue().getJobWorker().getName() : ""
                ));

        colAmount.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().getAmount() != null
                                ? "₹ " + c.getValue().getAmount().toPlainString() : ""
                ));

        colMode.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().getTransferMode() != null
                                ? c.getValue().getTransferMode().name() : ""
                ));

        colRemark.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().getRemark() != null
                                ? c.getValue().getRemark() : ""
                ));

        // highlight cancelled rows
        tblReport.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(MoneyReceipt item, boolean empty) {
                super.updateItem(item, empty);

                if (item == null || empty) {
                    setStyle("");
                } else if (item.getStatus() == EntryStatus.CANCELLED) {
                    setStyle("-fx-background-color:#f3f4f6; -fx-text-fill:gray;");
                } else {
                    setStyle("");
                }
            }
        });

        addEditColumn();
        addDeleteColumn();
        addRestoreColumn();
    }

    // ================= SEARCH =================

    @FXML
    public void onSearch() {

        Long workerId = cbWorker.getValue() != null
                ? cbWorker.getValue().getId() : null;

        LocalDate from = dpFrom.getValue();
        LocalDate to = dpTo.getValue();

        String challan = tfChallanNo.getText() != null
                ? tfChallanNo.getText().trim() : null;

        if (challan != null && challan.isEmpty()) challan = null;

        List<MoneyReceipt> results =
                moneyService.searchReport(workerId, from, to, challan);

        results.sort(Comparator.comparing(
                MoneyReceipt::getReceiptDate,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));

        tblReport.setItems(FXCollections.observableArrayList(results));

        // ================= TOTAL CALCULATION =================

// Total Paid
        BigDecimal totalPaid = results.stream()
                .filter(r -> r.getStatus() != EntryStatus.CANCELLED)
                .map(r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

// Total Work (from ProductEntry)
        BigDecimal totalWork = productEntryRepo.sumTotal(workerId, from, to);
        if (totalWork == null) totalWork = BigDecimal.ZERO;
// Final Balance
        BigDecimal balance = totalWork.subtract(totalPaid);

// ================= UI UPDATE =================

        lblTotalWork.setText("₹ " + totalWork.toPlainString());
        lblTotalPaid.setText("₹ " + totalPaid.toPlainString());
        lblBalance.setText("₹ " + balance.toPlainString());

    }

    // ================= EDIT =================
    private void addEditColumn() {

        TableColumn<MoneyReceipt, Void> col = new TableColumn<>("Edit");

        col.setCellFactory(c -> new TableCell<>() {

            final Button btn = new Button("✏");

            {
                btn.setOnAction(e -> {
                    MoneyReceipt r = getTableView().getItems().get(getIndex());
                    openEditDialog(r);
                });
            }

            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btn);
            }
        });

        tblReport.getColumns().add(col);
    }

    // ================= DELETE =================
    private void addDeleteColumn() {

        TableColumn<MoneyReceipt, Void> col = new TableColumn<>("Delete");

        col.setCellFactory(c -> new TableCell<>() {

            final Button btn = new Button("🗑");

            {
                btn.setOnAction(e -> {

                    MoneyReceipt r = getTableView().getItems().get(getIndex());

                    if (GlobalUI.confirm("Delete", "Confirm delete?")) {
                        moneyService.delete(r.getId());
                        onSearch();
                    }
                });
            }

            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btn);
            }
        });

        tblReport.getColumns().add(col);
    }

    // ================= RESTORE =================
    private void addRestoreColumn() {

        TableColumn<MoneyReceipt, Void> col = new TableColumn<>("Restore");

        col.setCellFactory(c -> new TableCell<>() {

            final Button btn = new Button("♻");

            {
                btn.setOnAction(e -> {
                    MoneyReceipt r = getTableView().getItems().get(getIndex());
                    moneyService.restorePayment(r.getId());
                    GlobalUI.success("Restored");
                    onSearch();
                });
            }

            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);

                if (empty) {
                    setGraphic(null);
                    return;
                }

                MoneyReceipt r = getTableView().getItems().get(getIndex());

                setGraphic(r.getStatus() == EntryStatus.CANCELLED ? btn : null);
            }
        });

        tblReport.getColumns().add(col);
    }

    // ================= EDIT DIALOG =================
    private void openEditDialog(MoneyReceipt r) {

        Dialog<MoneyReceipt> dialog = new Dialog<>();
        dialog.setTitle("Edit Receipt");

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        TextField tfAmt = new TextField(
                r.getAmount() != null ? r.getAmount().toPlainString() : ""
        );

        TextField tfRemark = new TextField(r.getRemark());

        dialog.getDialogPane().setContent(
                new VBox(10, new Label("Amount"), tfAmt, new Label("Remark"), tfRemark)
        );

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                r.setAmount(new BigDecimal(tfAmt.getText()));
                r.setRemark(tfRemark.getText());
                return r;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(updated -> {
            moneyService.save(updated);
            onSearch();
        });
    }
    @FXML
    public void onPdf() {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Save PDF File");

            fc.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("PDF Files (*.pdf)", "*.pdf")
            );

            File file = fc.showSaveDialog(null);
            if (file == null) return;

            // 🔥 CALL YOUR EXISTING PDF EXPORTER
            pdfExporter.exportMoneyReport(
                    tblReport.getItems(),
                    cbWorker.getValue() != null ? cbWorker.getValue().getName() : "All",
                    dpFrom.getValue(),
                    dpTo.getValue(),
                    file.toPath()
            );

            GlobalUI.success("PDF exported successfully!");

        } catch (Exception e) {
            GlobalUI.warn("PDF export failed: " + e.getMessage());
        }
    }
    @FXML
    public void onExcel() {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Save Excel File");

            fc.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Excel Files (*.xlsx)", "*.xlsx")
            );

            File file = fc.showSaveDialog(null);
            if (file == null) return;

            // 🔥 CALL YOUR EXISTING EXPORTER
            excelExporter.exportMoneyReport(
                    tblReport.getItems(),
                    cbWorker.getValue() != null ? cbWorker.getValue().getName() : "All",
                    dpFrom.getValue(),
                    dpTo.getValue(),
                    file.toPath()
            );

            GlobalUI.success("Excel exported successfully!");

        } catch (Exception e) {
            GlobalUI.warn("Excel export failed: " + e.getMessage());
        }
    }
}