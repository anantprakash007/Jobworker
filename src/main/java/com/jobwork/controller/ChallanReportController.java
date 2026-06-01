package com.jobwork.controller;

import com.jobwork.config.StageManager;
import com.jobwork.domain.ChallanReportRow;
import com.jobwork.domain.JobWorker;
import com.jobwork.service.JobWorkerService;
import com.jobwork.service.ProductService;
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

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class ChallanReportController {

    @FXML private ComboBox<JobWorker> cbWorker;
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private Label lblTotal;
    @FXML private TableView<ChallanReportRow> tblReport;

    @FXML private TableColumn<ChallanReportRow, String> colDate;
    @FXML private TableColumn<ChallanReportRow, String> colChallan;
    @FXML private TableColumn<ChallanReportRow, String> colWorker;
    @FXML private TableColumn<ChallanReportRow, String> colAmount;

    @Autowired private ProductService productService;
    @Autowired private JobWorkerService workerService;
    @Autowired private ExcelExporter excelExporter;
    @Autowired private PdfExporter pdfExporter;
    @Autowired private StageManager stageManager;

    @FXML
    public void initialize() {

        cbWorker.setItems(FXCollections.observableArrayList(workerService.findAll()));

        colDate.setCellValueFactory(c ->
                new SimpleStringProperty(GlobalUI.formatDate(c.getValue().getDate()))
        );

        colChallan.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getChallan())
        );

        colWorker.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getWorker())
        );

        colAmount.setCellValueFactory(c ->
                new SimpleStringProperty("₹ " + format(c.getValue().getAmount()))
        );

        colChallan.setStyle("-fx-alignment: CENTER;");
        colAmount.setStyle("-fx-alignment: CENTER-RIGHT;");
        tblReport.setRowFactory(tv -> {
            TableRow<ChallanReportRow> row = new TableRow<>();

            row.setOnMouseClicked(event -> {

                if (event.getClickCount() == 2 && !row.isEmpty()) {

                    ChallanReportRow data = row.getItem();

                    if (data.getChallan() != null && cbWorker.getValue() != null) {

                        try {
                            stageManager.showChallanDetail(
                                    cbWorker.getValue().getId(),   // ✅ correct
                                    data.getChallan()
                            );
                        } catch (Exception e) {
                            GlobalUI.warn("Cannot open challan: " + e.getMessage());
                        }
                    }
                }
            });

            return row;
        });
    }

    @FXML
    public void onSearch() {

        Long workerId = cbWorker.getValue() != null
                ? cbWorker.getValue().getId()
                : null;

        LocalDate from = dpFrom.getValue();
        LocalDate to = dpTo.getValue();

        List<ChallanReportRow> data =
                productService.getChallanReport(workerId, from, to);

        tblReport.setItems(FXCollections.observableArrayList(data));
        applyAutoResize(tblReport);

        BigDecimal total = data.stream()
                .map(ChallanReportRow::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        lblTotal.setText("₹ " + format(total));
    }

    @FXML
    public void onExcel() {

        try {
            File file = choose("Excel", "*.xlsx");
            if (file == null) return;

            excelExporter.exportChallanReport(
                    new ArrayList<>(tblReport.getItems()),
                    file.toPath(),
                    "ANANT TEXTILES",
                    cbWorker.getValue() != null ? cbWorker.getValue().getName() : "ALL",
                    dpFrom.getValue(),
                    dpTo.getValue()
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

            pdfExporter.exportChallanReport(
                    new ArrayList<>(tblReport.getItems()),
                    file.toPath(),
                    "ANANT TEXTILES",
                    cbWorker.getValue() != null ? cbWorker.getValue().getName() : "ALL",
                    dpFrom.getValue(),
                    dpTo.getValue()
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