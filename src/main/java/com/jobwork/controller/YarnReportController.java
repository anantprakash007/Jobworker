package com.jobwork.controller;

import com.jobwork.domain.JobWorker;
import com.jobwork.domain.YarnEntry;
import com.jobwork.domain.YarnType;
import com.jobwork.service.JobWorkerService;
import com.jobwork.service.YarnService;
import com.jobwork.service.YarnTypeService;
import com.jobwork.util.ExcelExporter;
import com.jobwork.util.GlobalUI;
import com.jobwork.util.JobWorkerComboBoxUtil;
import com.jobwork.util.PdfExporter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import javafx.stage.FileChooser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

@Component
public class YarnReportController implements Initializable {


    @Autowired private YarnService      yarnService;
    @Autowired private JobWorkerService workerService;
    @Autowired private PdfExporter      pdfExporter;
    @Autowired private ExcelExporter    excelExporter;
    @Autowired  private YarnTypeService yarnTypeService;
    @FXML private ComboBox<JobWorker> cbWorker;
    @FXML private ComboBox<String>    cbYarnCount;
    @FXML private DatePicker          dpFrom;
    @FXML private DatePicker          dpTo;
    @FXML private TextField           tfChallanNo;

    @FXML private TableView<YarnEntry>            tblReport;
    @FXML private TableColumn<YarnEntry, String>  colDate;
    @FXML private TableColumn<YarnEntry, String>  colChallan;
    @FXML private TableColumn<YarnEntry, String>  colColour;
    @FXML private TableColumn<YarnEntry, String>  colCount;
    @FXML private TableColumn<YarnEntry, String>  colBags;
    @FXML private TableColumn<YarnEntry, String>  colCones;
    @FXML private TableColumn<YarnEntry, String>  colWeight;

    @FXML private Label lblTotalWeight;
    @FXML private Label lblTotalBags;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
       // cbWorker.setItems(FXCollections.observableArrayList(workerService.findAll()));
        JobWorkerComboBoxUtil.setup(
                cbWorker,
                workerService.findAll()
        );
        cbYarnCount.setItems(FXCollections.observableArrayList(
                "", "10s","14s","16s","20s","30s","40s","60s","80s","100s"));

        colDate.setCellValueFactory(c -> new SimpleStringProperty(
                GlobalUI.formatDate(c.getValue().getEntryDate())
        ));
        colChallan.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getChallanNo() != null ? c.getValue().getChallanNo() : ""));
        colColour.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getColour() != null ? c.getValue().getColour() : ""));
        // ✅ FIX: Use YarnType instead of yarnCount
        colCount.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getYarnType() != null
                        ? c.getValue().getYarnType().getName()
                        : ""
        ));
        colBags.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getNoOfBags() != null ? String.valueOf(c.getValue().getNoOfBags()) : "0"));
        colCones.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getNoOfCones() != null ? String.valueOf(c.getValue().getNoOfCones()) : "0"));
        colWeight.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getNetWeight() != null ? c.getValue().getNetWeight().toPlainString() : ""));

        addEditColumn();
        addDeleteColumn();
    }

    private void addEditColumn() {
        TableColumn<YarnEntry, Void> col = new TableColumn<>("Edit");
        col.setPrefWidth(70); col.setStyle("-fx-alignment:CENTER;");
        col.setCellFactory(c -> new TableCell<>() {
            final Button btn = new Button("✏ Edit");
            {
                btn.setStyle(
                        "-fx-background-color:linear-gradient(to right,#059669,#047857);" +
                                "-fx-text-fill:white;-fx-font-size:11px;-fx-font-weight:bold;" +
                                "-fx-padding:4 10;-fx-background-radius:6;-fx-cursor:hand;");
                // ✅ SAFE ROW ACCESS
                btn.setOnAction(e -> {
                    YarnEntry item = (YarnEntry) getTableRow().getItem();
                    if (item != null) openEditDialog(item);
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btn);
            }
        });
        tblReport.getColumns().add(col);
    }

    private void addDeleteColumn() {
        TableColumn<YarnEntry, Void> col = new TableColumn<>("Delete");
        col.setPrefWidth(80); col.setStyle("-fx-alignment:CENTER;");
        col.setCellFactory(c -> new TableCell<>() {
            final Button btn = new Button("🗑 Del");
            {
                btn.setStyle(
                        "-fx-background-color:linear-gradient(to right,#dc2626,#b91c1c);" +
                                "-fx-text-fill:white;-fx-font-size:11px;-fx-font-weight:bold;" +
                                "-fx-padding:4 10;-fx-background-radius:6;-fx-cursor:hand;");
                // ✅ SAFE DELETE + CORRECT METHOD
                btn.setOnAction(e -> {

                    YarnEntry item = (YarnEntry) getTableRow().getItem();
                    if (item == null) return;

                    if (GlobalUI.confirm("Delete Entry",
                            "Delete this yarn entry?")) {

                        yarnService.deleteById(item.getId()); // ✅ FIXED

                        GlobalUI.success("Deleted successfully");

                        onSearch(); // refresh
                    }
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btn);
            }
        });
        tblReport.getColumns().add(col);
    }

    private void openEditDialog(YarnEntry entry) {
        Dialog<YarnEntry> dialog = new Dialog<>();
        dialog.setTitle("Edit Yarn Entry");
        dialog.setHeaderText("Edit — Challan: " + entry.getChallanNo());

        ButtonType saveBtn = new ButtonType("💾 Save", ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10); grid.setStyle("-fx-padding:16;");

        ComboBox<String> cbCount = new ComboBox<>(FXCollections.observableArrayList(
                "10s","14s","16s","20s","30s","40s","60s","80s","100s"));
        // ✅ FIX: Load from YarnType
        cbCount.setValue(
                entry.getYarnType() != null
                        ? entry.getYarnType().getName()
                        : null
        );
        TextField tfColour = new TextField(entry.getColour() != null ? entry.getColour() : "");
        tfColour.setPrefWidth(150);

        TextField tfBags  = new TextField(entry.getNoOfBags()  != null
                ? String.valueOf(entry.getNoOfBags())  : "0");
        TextField tfCones = new TextField(entry.getNoOfCones() != null
                ? String.valueOf(entry.getNoOfCones()) : "0");
        TextField tfWt    = new TextField(entry.getNetWeight() != null
                ? entry.getNetWeight().toPlainString() : "");
        for (TextField tf : new TextField[]{tfBags, tfCones, tfWt}) tf.setPrefWidth(150);

        DatePicker dp = new DatePicker(entry.getEntryDate()); dp.setPrefWidth(200);

        grid.addRow(0, new Label("Yarn Count:"),      cbCount);
        grid.addRow(1, new Label("Colour:"),           tfColour);
        grid.addRow(2, new Label("No. of Bags:"),      tfBags);
        grid.addRow(3, new Label("No. of Cones:"),     tfCones);
        grid.addRow(4, new Label("Net Weight (kg):"),  tfWt);
        grid.addRow(5, new Label("Date:"),             dp);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setMinWidth(400);

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                try {
                    // ✅ FIX: Convert String → YarnType
                    YarnType yt = yarnTypeService.findByName(cbCount.getValue());
                    entry.setYarnType(yt);
                    entry.setColour(tfColour.getText().trim());
                    entry.setNoOfBags(tfBags.getText().isBlank()
                            ? 0 : Integer.parseInt(tfBags.getText().trim()));
                    entry.setNoOfCones(tfCones.getText().isBlank()
                            ? 0 : Integer.parseInt(tfCones.getText().trim()));
                    if (!tfWt.getText().isBlank())
                        entry.setNetWeight(new BigDecimal(tfWt.getText().trim()));
                    entry.setEntryDate(dp.getValue());
                    return entry;
                } catch (NumberFormatException ex) { return null; }
            }
            return null;
        });

        Optional<YarnEntry> result = dialog.showAndWait();
        result.ifPresent(updated -> {
            yarnService.save(updated);
            GlobalUI.success("Entry updated successfully.");
            onSearch();
        });
    }

    @FXML
    public void onSearch() {
        Long workerId = cbWorker.getValue()   != null ? cbWorker.getValue().getId() : null;
        LocalDate from= dpFrom.getValue();
        LocalDate to  = dpTo.getValue();
        String challan= tfChallanNo.getText() != null ? tfChallanNo.getText().trim() : null;
        String count  = cbYarnCount.getValue();
        if (challan != null && challan.isEmpty()) challan = null;
        if (count   != null && count.isEmpty())   count   = null;

        List<YarnEntry> results = yarnService.searchReport(workerId, from, to, challan, count);
        tblReport.setItems(FXCollections.observableArrayList(results));

        BigDecimal totalWt = results.stream()
                .map(e -> e.getNetWeight() != null ? e.getNetWeight() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalBags = results.stream()
                .mapToInt(e -> e.getNoOfBags() != null ? e.getNoOfBags() : 0).sum();
        lblTotalWeight.setText("Total Wt: " + totalWt.toPlainString() + " kg");
        lblTotalBags.setText("Total Bags: " + totalBags);
    }

    @FXML
    public void onCountWise() {
        Long wid = cbWorker.getValue() != null ? cbWorker.getValue().getId() : null;
        List<Object[]> summary = yarnService.yarnWiseSummary(wid, dpFrom.getValue(), dpTo.getValue());
        if (summary.isEmpty()) { GlobalUI.warn("No data for the selected filter."); return; }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-12s %12s %12s %16s %16s%n",
                "Count", "Total Bags", "Total Cones", "Total Wt(kg)", "Grand Total"));
        sb.append("-".repeat(72)).append("\n");
        long sumBags = 0, sumCones = 0; BigDecimal sumWt = BigDecimal.ZERO;
        for (Object[] row : summary) {
            String count = row[0] != null ? row[0].toString() : "";
            long bags    = row[1] != null ? Long.parseLong(row[1].toString()) : 0L;
            long cones   = row[2] != null ? Long.parseLong(row[2].toString()) : 0L;
            BigDecimal wt= row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
            sb.append(String.format("%-12s %12d %12d %16s %16s%n",
                    count, bags, cones, wt.toPlainString(), wt.toPlainString()));
            sumBags += bags; sumCones += cones; sumWt = sumWt.add(wt);
        }
        sb.append("-".repeat(72)).append("\n");
        sb.append(String.format("%-12s %12d %12d %16s %16s%n",
                "GRAND TOTAL", sumBags, sumCones,
                sumWt.toPlainString(), sumWt.toPlainString()));

        GlobalUI.showTextDialog("Yarn-wise (Count-wise) Summary", sb.toString());
    }

    @FXML
    public void onPdf() throws IOException {
        if (!GlobalUI.validateExport(tblReport.getItems())) return;
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("yarn_report.pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File f = fc.showSaveDialog(tblReport.getScene().getWindow());
        if (f != null) {
            Long wid = cbWorker.getValue() != null ? cbWorker.getValue().getId() : null;
            String wName = cbWorker.getValue() != null ? cbWorker.getValue().getName() : null;
            List<Object[]> summary = yarnService.yarnWiseSummary(
                    wid, dpFrom.getValue(), dpTo.getValue());
            pdfExporter.exportYarnReport(
                    new ArrayList<>(tblReport.getItems()),
                    summary, wName, dpFrom.getValue(), dpTo.getValue(),
                    f.toPath());
            GlobalUI.success("PDF saved successfully.");
        }
    }

    @FXML
    public void onExcel() throws IOException {
        if (!GlobalUI.validateExport(tblReport.getItems())) return;
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("yarn_report.xlsx");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        File f = fc.showSaveDialog(tblReport.getScene().getWindow());
        if (f != null) {
            excelExporter.exportYarnReport(
                    new ArrayList<>(tblReport.getItems()),   // 🔥 FIXED
                    List.of(),
                    null,
                    dpFrom.getValue(),
                    dpTo.getValue(),
                    f.toPath()
            );
            GlobalUI.success("Excel saved successfully.");
        }
    }

   /** private void showSummaryDialog(String title, String content) {
        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle(title); info.setHeaderText(null);
        TextArea ta = new TextArea(content);
        ta.setEditable(false);
        ta.setFont(Font.font("Monospaced", 13));
        ta.setPrefHeight(380); ta.setPrefWidth(720);
        info.getDialogPane().setContent(ta);
        info.getDialogPane().setMinWidth(760);
        info.showAndWait();
    }

    private void alert(String m) {
        new Alert(Alert.AlertType.INFORMATION, m, ButtonType.OK).showAndWait();
    }*/
}
