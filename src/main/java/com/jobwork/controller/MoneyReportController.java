package com.jobwork.controller;

import com.jobwork.domain.EntryStatus;
import com.jobwork.domain.JobWorker;
import com.jobwork.domain.MoneyReceipt;
import com.jobwork.repository.ProductEntryRepository;
import com.jobwork.service.JobWorkerService;
import com.jobwork.service.MoneyService;
import com.jobwork.util.ExcelExporter;
import com.jobwork.util.GlobalUI;
import com.jobwork.util.JobWorkerComboBoxUtil;
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
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.util.*;

@Component
public class MoneyReportController implements Initializable {

    @Autowired
    private MoneyService moneyService;

    @Autowired
    private JobWorkerService workerService;

    @Autowired
    private PdfExporter pdfExporter;

    @Autowired
    private ExcelExporter excelExporter;

    @Autowired
    private ProductEntryRepository productEntryRepo;

    @FXML
    private ComboBox<JobWorker> cbWorker;

    @FXML
    private DatePicker dpFrom;

    @FXML
    private DatePicker dpTo;

    @FXML
    private TextField tfChallanNo;

    @FXML
    private TableView<MoneyReceipt> tblReport;

    @FXML
    private TableColumn<MoneyReceipt, String> colDate;

    @FXML
    private TableColumn<MoneyReceipt, String> colChallan;

    @FXML
    private TableColumn<MoneyReceipt, String> colWorker;

    @FXML
    private TableColumn<MoneyReceipt, String> colAmount;

    @FXML
    private TableColumn<MoneyReceipt, String> colMode;

    @FXML
    private TableColumn<MoneyReceipt, String> colRemark;

    @FXML
    private Label lblTotalWork;

    @FXML
    private Label lblTotalPaid;

    @FXML
    private Label lblBalance;


    // ============================================================
    // INITIALIZE
    // ============================================================

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        /*
         * CENTRAL JOB WORKER COMBOBOX
         *
         * IMPORTANT:
         * Do not call cbWorker.setItems(...) again after this.
         */
        JobWorkerComboBoxUtil.setup(
                cbWorker,
                workerService.findAll()
        );


        // ========================================================
        // TABLE COLUMNS
        // ========================================================

        colDate.setCellValueFactory(c ->
                new SimpleStringProperty(
                        GlobalUI.formatDate(
                                c.getValue().getReceiptDate()
                        )
                )
        );


        colChallan.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().getChallanNo() != null
                                ? c.getValue().getChallanNo()
                                : ""
                )
        );


        colWorker.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().getJobWorker() != null
                                ? c.getValue().getJobWorker().getName()
                                : ""
                )
        );


        colAmount.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().getAmount() != null
                                ? "₹ "
                                + c.getValue().getAmount().toPlainString()
                                : ""
                )
        );


        colMode.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().getTransferMode() != null
                                ? c.getValue().getTransferMode().name()
                                : ""
                )
        );


        colRemark.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().getRemark() != null
                                ? c.getValue().getRemark()
                                : ""
                )
        );


        // ========================================================
        // CANCELLED ROW HIGHLIGHT
        // ========================================================

        tblReport.setRowFactory(tv ->
                new TableRow<>() {

                    @Override
                    protected void updateItem(
                            MoneyReceipt item,
                            boolean empty) {

                        super.updateItem(item, empty);

                        if (item == null || empty) {

                            setStyle("");

                        } else if (
                                item.getStatus()
                                        == EntryStatus.CANCELLED) {

                            setStyle(
                                    "-fx-background-color:#f3f4f6;"
                                            + " -fx-text-fill:gray;"
                            );

                        } else {

                            setStyle("");
                        }
                    }
                }
        );


        // ========================================================
        // ACTION COLUMNS
        // ========================================================

        addEditColumn();
        addDeleteColumn();
        addRestoreColumn();
    }


    // ============================================================
    // SEARCH
    // ============================================================

    @FXML
    public void onSearch() {

        /*
         * IMPORTANT:
         *
         * cbWorker.getValue() is JobWorker.
         * We take only its database ID for the service query.
         */
        Long workerId =
                cbWorker.getValue() != null
                        ? cbWorker.getValue().getId()
                        : null;


        LocalDate from =
                dpFrom.getValue();

        LocalDate to =
                dpTo.getValue();


        String challan =
                tfChallanNo.getText() != null
                        ? tfChallanNo.getText().trim()
                        : null;


        if (challan != null && challan.isEmpty()) {
            challan = null;
        }


        // ========================================================
        // LOAD MONEY RECEIPTS
        // ========================================================

        List<MoneyReceipt> results =
                moneyService.searchReport(
                        workerId,
                        from,
                        to,
                        challan
                );


        // ========================================================
        // SORT BY DATE
        // ========================================================

        results.sort(
                Comparator.comparing(
                        MoneyReceipt::getReceiptDate,
                        Comparator.nullsLast(
                                Comparator.naturalOrder()
                        )
                )
        );


        tblReport.setItems(
                FXCollections.observableArrayList(results)
        );


        // ========================================================
        // TOTAL PAID
        // ========================================================

        BigDecimal totalPaid =
                results.stream()

                        .filter(r ->
                                r.getStatus()
                                        != EntryStatus.CANCELLED
                        )

                        .map(r ->
                                r.getAmount() != null
                                        ? r.getAmount()
                                        : BigDecimal.ZERO
                        )

                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );


        // ========================================================
        // TOTAL WORK
        // ========================================================

        BigDecimal totalWork =
                productEntryRepo.sumTotal(
                        workerId,
                        from,
                        to
                );


        if (totalWork == null) {
            totalWork = BigDecimal.ZERO;
        }


        // ========================================================
        // BALANCE
        // ========================================================

        BigDecimal balance =
                totalWork.subtract(totalPaid);


        // ========================================================
        // UPDATE UI
        // ========================================================

        lblTotalWork.setText(
                "₹ " + totalWork.toPlainString()
        );

        lblTotalPaid.setText(
                "₹ " + totalPaid.toPlainString()
        );

        lblBalance.setText(
                "₹ " + balance.toPlainString()
        );
    }


    // ============================================================
    // EDIT COLUMN
    // ============================================================

    private void addEditColumn() {

        TableColumn<MoneyReceipt, Void> col =
                new TableColumn<>("Edit");


        TableCell<MoneyReceipt, Void> cell =
                new TableCell<>() {

                    final Button btn =
                            new Button("✏");


                    {
                        btn.setOnAction(e -> {

                            MoneyReceipt r =
                                    getTableView()
                                            .getItems()
                                            .get(getIndex());

                            openEditDialog(r);
                        });
                    }


                    @Override
                    protected void updateItem(
                            Void v,
                            boolean empty) {

                        super.updateItem(v, empty);

                        setGraphic(
                                empty
                                        ? null
                                        : btn
                        );
                    }
                };


        col.setCellFactory(c -> cell);

        tblReport.getColumns().add(col);
    }


    // ============================================================
    // DELETE COLUMN
    // ============================================================

    private void addDeleteColumn() {

        TableColumn<MoneyReceipt, Void> col =
                new TableColumn<>("Delete");


        TableCell<MoneyReceipt, Void> cell =
                new TableCell<>() {

                    final Button btn =
                            new Button("🗑");


                    {
                        btn.setOnAction(e -> {

                            MoneyReceipt r =
                                    getTableView()
                                            .getItems()
                                            .get(getIndex());


                            if (GlobalUI.confirm(
                                    "Delete",
                                    "Confirm delete?"
                            )) {

                                moneyService.delete(
                                        r.getId()
                                );

                                onSearch();
                            }
                        });
                    }


                    @Override
                    protected void updateItem(
                            Void v,
                            boolean empty) {

                        super.updateItem(v, empty);

                        setGraphic(
                                empty
                                        ? null
                                        : btn
                        );
                    }
                };


        col.setCellFactory(c -> cell);

        tblReport.getColumns().add(col);
    }


    // ============================================================
    // RESTORE COLUMN
    // ============================================================

    private void addRestoreColumn() {

        TableColumn<MoneyReceipt, Void> col =
                new TableColumn<>("Restore");


        col.setCellFactory(c ->
                new TableCell<>() {

                    final Button btn =
                            new Button("♻");


                    {
                        btn.setOnAction(e -> {

                            MoneyReceipt r =
                                    getTableView()
                                            .getItems()
                                            .get(getIndex());


                            moneyService.restorePayment(
                                    r.getId()
                            );


                            GlobalUI.success(
                                    "Restored"
                            );


                            onSearch();
                        });
                    }


                    @Override
                    protected void updateItem(
                            Void v,
                            boolean empty) {

                        super.updateItem(v, empty);


                        if (empty) {

                            setGraphic(null);

                            return;
                        }


                        MoneyReceipt r =
                                getTableView()
                                        .getItems()
                                        .get(getIndex());


                        setGraphic(
                                r.getStatus()
                                        == EntryStatus.CANCELLED
                                        ? btn
                                        : null
                        );
                    }
                }
        );


        tblReport.getColumns().add(col);
    }


    // ============================================================
    // EDIT DIALOG
    // ============================================================

    private void openEditDialog(
            MoneyReceipt r) {

        Dialog<MoneyReceipt> dialog =
                new Dialog<>();


        dialog.setTitle(
                "Edit Receipt"
        );


        ButtonType saveBtn =
                new ButtonType(
                        "Save",
                        ButtonBar.ButtonData.OK_DONE
                );


        dialog.getDialogPane()
                .getButtonTypes()
                .addAll(
                        saveBtn,
                        ButtonType.CANCEL
                );


        TextField tfAmt =
                new TextField(
                        r.getAmount() != null
                                ? r.getAmount().toPlainString()
                                : ""
                );


        TextField tfRemark =
                new TextField(
                        r.getRemark() != null
                                ? r.getRemark()
                                : ""
                );


        dialog.getDialogPane().setContent(
                new VBox(
                        10,
                        new Label("Amount"),
                        tfAmt,
                        new Label("Remark"),
                        tfRemark
                )
        );


        dialog.setResultConverter(btn -> {

            if (btn == saveBtn) {

                try {

                    r.setAmount(
                            new BigDecimal(
                                    tfAmt.getText().trim()
                            )
                    );

                } catch (Exception e) {

                    GlobalUI.warn(
                            "Invalid amount"
                    );

                    return null;
                }


                r.setRemark(
                        tfRemark.getText()
                );


                return r;
            }


            return null;
        });


        dialog.showAndWait()
                .ifPresent(updated -> {

                    moneyService.save(
                            updated
                    );

                    onSearch();
                });
    }


    // ============================================================
    // PDF
    // ============================================================

    @FXML
    public void onPdf() {

        try {

            FileChooser fc =
                    new FileChooser();


            fc.setTitle(
                    "Save PDF File"
            );


            fc.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(
                            "PDF Files (*.pdf)",
                            "*.pdf"
                    )
            );


            File file =
                    fc.showSaveDialog(null);


            if (file == null) {
                return;
            }


            pdfExporter.exportMoneyReport(
                    tblReport.getItems(),

                    cbWorker.getValue() != null
                            ? cbWorker.getValue().getName()
                            : "All",

                    dpFrom.getValue(),
                    dpTo.getValue(),

                    file.toPath()
            );


            GlobalUI.success(
                    "PDF exported successfully!"
            );


        } catch (Exception e) {

            GlobalUI.warn(
                    "PDF export failed: "
                            + e.getMessage()
            );
        }
    }


    // ============================================================
    // EXCEL
    // ============================================================

    @FXML
    public void onExcel() {

        try {

            FileChooser fc =
                    new FileChooser();


            fc.setTitle(
                    "Save Excel File"
            );


            fc.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(
                            "Excel Files (*.xlsx)",
                            "*.xlsx"
                    )
            );


            File file =
                    fc.showSaveDialog(null);


            if (file == null) {
                return;
            }


            excelExporter.exportMoneyReport(
                    tblReport.getItems(),

                    cbWorker.getValue() != null
                            ? cbWorker.getValue().getName()
                            : "All",

                    dpFrom.getValue(),
                    dpTo.getValue(),

                    file.toPath()
            );


            GlobalUI.success(
                    "Excel exported successfully!"
            );


        } catch (Exception e) {

            GlobalUI.warn(
                    "Excel export failed: "
                            + e.getMessage()
            );
        }
    }
}