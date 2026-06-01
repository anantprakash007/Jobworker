package com.jobwork.controller;

import com.jobwork.domain.ProductEntry;
import com.jobwork.service.ProductService;
import com.jobwork.util.ExcelExporter;
import com.jobwork.util.PdfExporter;
import com.jobwork.util.FormatUtil;
import com.jobwork.util.GlobalUI;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.math.BigDecimal;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class ChallanDetailController {
    @Autowired private ExcelExporter excelExporter;
    @Autowired private PdfExporter pdfExporter;

    @FXML private Label lblChallan;
    @FXML private Label lblWorker;
    @FXML private Label lblDate;
    @FXML private Label lblTotal;

    @FXML private TableView<ProductEntry> tblDetail;

    @FXML private TableColumn<ProductEntry, String> colProduct;
    @FXML private TableColumn<ProductEntry, String> colQty;
    @FXML private TableColumn<ProductEntry, String> colRate;
    @FXML private TableColumn<ProductEntry, String> colAmount;

    @Autowired private ProductService productService;

    @FXML
    public void initialize() {

        colProduct.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getProductName().getName()));

        colQty.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getQuantity().toString()));

        colRate.setCellValueFactory(c ->
                new SimpleStringProperty(
                        FormatUtil.money(c.getValue().getProductName().getDefaultRate())
                ));

        colAmount.setCellValueFactory(c ->
                new SimpleStringProperty(
                        FormatUtil.money(c.getValue().getTotalAmount())
                ));

        colQty.setStyle("-fx-alignment: CENTER-RIGHT;");
        colRate.setStyle("-fx-alignment: CENTER-RIGHT;");
        colAmount.setStyle("-fx-alignment: CENTER-RIGHT;");
    }

    // 🔥 LOAD DATA FROM STAGEMANAGER
    public void loadData(Long workerId, String challanNo) {

        List<ProductEntry> list =
                productService.findByChallanAndWorker(workerId, challanNo);

        if (list.isEmpty()) return;

        tblDetail.setItems(FXCollections.observableArrayList(list));

        lblChallan.setText("Challan No: " + challanNo);

        lblWorker.setText("Worker: " +
                list.get(0).getJobWorker().getName());

        lblDate.setText("Date: " +
                list.get(0).getEntryDate()
                        .format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));

        BigDecimal total = list.stream()
                .map(ProductEntry::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        lblTotal.setText(FormatUtil.money(total));
    }
    @FXML
    public void onPdf() {
        try {
            File file = choose("PDF", "*.pdf");
            if (file == null) return;

            pdfExporter.exportChallanInvoice(
                    "ANANT TEXTILES",
                    lblWorker.getText(),
                    lblChallan.getText(),
                    lblDate.getText(),
                    tblDetail.getItems(),
                    file.toPath()
            );

        } catch (Exception e) {
            GlobalUI.warn(e.getMessage());
        }
    }

    @FXML
    public void onExcel() {
        try {
            File file = choose("Excel", "*.xlsx");
            if (file == null) return;

            excelExporter.exportChallanInvoiceExcel(
                    "ANANT TEXTILES",
                    lblWorker.getText(),
                    lblChallan.getText(),
                    lblDate.getText(),
                    tblDetail.getItems(),
                    file.toPath()
            );

        } catch (Exception e) {
            GlobalUI.warn(e.getMessage());
        }
    }
    private File choose(String title, String ext) {

        FileChooser fc = new FileChooser();
        fc.setTitle("Save " + title);

        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(title, ext)
        );

        return fc.showSaveDialog(null);
    }
}