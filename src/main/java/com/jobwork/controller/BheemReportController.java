package com.jobwork.controller;

import com.jobwork.domain.BheemEntry;
import com.jobwork.domain.BheemName;
import com.jobwork.domain.EntryStatus;
import com.jobwork.domain.JobWorker;
import com.jobwork.domain.Wrapper;
import com.jobwork.service.BheemService;
import com.jobwork.service.JobWorkerService;
import com.jobwork.service.MasterService;
import com.jobwork.util.ExcelExporter;
import com.jobwork.util.GlobalUI;
import com.jobwork.util.JobWorkerComboBoxUtil;
import com.jobwork.util.PdfExporter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;

import javafx.scene.control.*;

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
public class BheemReportController implements Initializable {


    @Autowired private BheemService     bheemService;
    @Autowired private JobWorkerService workerService;
    @Autowired private MasterService    masterService;
    @Autowired private PdfExporter      pdfExporter;
    @Autowired private ExcelExporter    excelExporter;

    @FXML private ComboBox<JobWorker>  cbWorker;
    @FXML private ComboBox<BheemName>  cbBheemName;
    @FXML private ComboBox<Wrapper>    cbWrapper;
    @FXML private DatePicker           dpFrom;
    @FXML private DatePicker           dpTo;
    @FXML private TextField            tfChallanNo;

    @FXML private TableView<BheemEntry>           tblReport;
    @FXML private TableColumn<BheemEntry, String> colDate;
    @FXML private TableColumn<BheemEntry, String> colChallan;
    @FXML private TableColumn<BheemEntry, String> colBheem;
    @FXML private TableColumn<BheemEntry, String> colTaar;
    @FXML private TableColumn<BheemEntry, String> colWeight;
    @FXML private TableColumn<BheemEntry, String> colWrapper;
    @FXML private TableColumn<BheemEntry, String> colColour;

    @FXML private Label lblTotalBheem;
    @FXML private Label lblTotalWeight;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
       //cbWorker.setItems(FXCollections.observableArrayList(workerService.findAll()));
        JobWorkerComboBoxUtil.setup(
                cbWorker,
                workerService.findAll()
        );
        cbBheemName.setItems(FXCollections.observableArrayList(masterService.findAllBheemNames()));
        cbWrapper.setItems(FXCollections.observableArrayList(masterService.findAllWrappers()));

        // ── Wire data columns ──
        colDate.setCellValueFactory(c -> new SimpleStringProperty(
                GlobalUI.formatDate(c.getValue().getEntryDate())
        ));
        colChallan.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getChallanNo() != null ? c.getValue().getChallanNo() : ""));
        colBheem.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getBheemName() != null ? c.getValue().getBheemName().getName() : ""));
        colTaar.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTaar() != null ? String.valueOf(c.getValue().getTaar().getValue()) : ""));
        colWeight.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getWeight() != null ? c.getValue().getWeight().toPlainString() : ""));
        colWrapper.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getWrapper() != null ? c.getValue().getWrapper().getName() : ""));
        colColour.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getColour() != null ? c.getValue().getColour() : ""));

        // ── Add Edit column ONCE ──
        addEditColumn();
        // ── Add Delete column ONCE ──
        addDeleteColumn();
    }

    // ── SINGLE Edit column ─────────────────────────────────────────────
    private void addEditColumn() {
        TableColumn<BheemEntry, Void> col = new TableColumn<>("Edit");
        col.setPrefWidth(70);
        col.setStyle("-fx-alignment:CENTER;");
        col.setCellFactory(c -> new TableCell<>() {
            final Button btn = new Button("✏ Edit");
            {
                btn.setStyle(
                        "-fx-background-color:linear-gradient(to right,#1e3a8a,#1d4ed8);" +
                                "-fx-text-fill:white;-fx-font-size:11px;-fx-font-weight:bold;" +
                                "-fx-padding:4 10;-fx-background-radius:6;-fx-cursor:hand;");
                btn.setOnAction(e -> {
                    BheemEntry entry = getTableView().getItems().get(getIndex());
                    openEditDialog(entry);
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btn);
            }
        });
        tblReport.getColumns().add(col);
    }

    // ── SINGLE Delete column ────────────────────────────────────────────
    private void addDeleteColumn() {
        TableColumn<BheemEntry, Void> col = new TableColumn<>("Delete");
        col.setPrefWidth(80);
        col.setStyle("-fx-alignment:CENTER;");
        col.setCellFactory(c -> new TableCell<>() {
            final Button btn = new Button("🗑 Del");
            {
                btn.setStyle(
                        "-fx-background-color:linear-gradient(to right,#dc2626,#b91c1c);" +
                                "-fx-text-fill:white;-fx-font-size:11px;-fx-font-weight:bold;" +
                                "-fx-padding:4 10;-fx-background-radius:6;-fx-cursor:hand;");
                btn.setOnAction(e -> {
                    BheemEntry entry = getTableView().getItems().get(getIndex());
                    if (GlobalUI.confirm("Delete Entry",
                            "Are you sure you want to delete this entry?")) {

                        bheemService.delete(entry.getId());
                        onSearch();
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

    // ── Edit Dialog — inline editing without navigating away ──────────
    private void openEditDialog(BheemEntry entry) {
        Dialog<BheemEntry> dialog = new Dialog<>();
        dialog.setTitle("Edit Bheem Entry");
        dialog.setHeaderText("Edit entry — Challan: " + entry.getChallanNo());

        ButtonType saveBtn = new ButtonType("💾 Save", ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.setStyle("-fx-padding:16;");

        // Bheem Name
        Label lbName = new Label("Bheem Name:");
        ComboBox<BheemName> cbB = new ComboBox<>(
                FXCollections.observableArrayList(masterService.findAllBheemNames()));
        cbB.setValue(entry.getBheemName());
        cbB.setPrefWidth(200);

        // Taar — refreshed when BheemName changes
        Label lbTaar = new Label("Taar:");
        ComboBox<com.jobwork.domain.Taar> cbT = new ComboBox<>();
        if (entry.getBheemName() != null)
            cbT.setItems(FXCollections.observableArrayList(
                    masterService.getTaarsByBheemName(entry.getBheemName().getId())));
        cbT.setValue(entry.getTaar());
        cbT.setPrefWidth(120);
        cbB.valueProperty().addListener((o, ov, nv) -> {
            if (nv != null)
                cbT.setItems(FXCollections.observableArrayList(
                        masterService.getTaarsByBheemName(nv.getId())));
            cbT.getSelectionModel().clearSelection();
        });

        // Wrapper
        Label lbWrapper = new Label("Wrapper:");
        ComboBox<Wrapper> cbW = new ComboBox<>(
                FXCollections.observableArrayList(masterService.findAllWrappers()));
        cbW.setValue(entry.getWrapper()); cbW.setPrefWidth(200);

        // Colour
        Label lbColour = new Label("Colour:");
        TextField tfC = new TextField(entry.getColour() != null ? entry.getColour() : "");
        tfC.setPrefWidth(200);

        // Weight
        Label lbWeight = new Label("Weight (kg):");
        TextField tfW = new TextField(
                entry.getWeight() != null ? entry.getWeight().toPlainString() : "");
        tfW.setPrefWidth(120);

        // Date
        Label lbDate = new Label("Date:");
        DatePicker dp = new DatePicker(entry.getEntryDate()); dp.setPrefWidth(200);

        grid.addRow(0, lbName,    cbB);
        grid.addRow(1, lbTaar,    cbT);
        grid.addRow(2, lbWrapper, cbW);
        grid.addRow(3, lbColour,  tfC);
        grid.addRow(4, lbWeight,  tfW);
        grid.addRow(5, lbDate,    dp);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setMinWidth(420);

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                // Validate
                if (tfW.getText().isBlank()) return null;
                try { new BigDecimal(tfW.getText().trim()); }
                catch (NumberFormatException ex) { return null; }
                entry.setBheemName(cbB.getValue());
                entry.setTaar(cbT.getValue());
                entry.setWrapper(cbW.getValue());
                entry.setColour(tfC.getText().trim());
                entry.setWeight(new BigDecimal(tfW.getText().trim()));
                entry.setEntryDate(dp.getValue());
                entry.setStatus(EntryStatus.DRAFT);
                return entry;
            }
            return null;
        });

        Optional<BheemEntry> result = dialog.showAndWait();
        result.ifPresent(updated -> {
            bheemService.save(updated);
            GlobalUI.success("Entry updated successfully.");
            onSearch();
        });
    }

    // ── Search ─────────────────────────────────────────────────────────
    @FXML
    public void onSearch() {
        Long workerId    = cbWorker.getValue()   != null ? cbWorker.getValue().getId()   : null;
        Long wrapperId   = cbWrapper.getValue()  != null ? cbWrapper.getValue().getId()  : null;
        Integer bheemId  = cbBheemName.getValue()!= null ? cbBheemName.getValue().getId(): null;
        LocalDate from   = dpFrom.getValue();
        LocalDate to     = dpTo.getValue();
        String challan   = tfChallanNo.getText() != null ? tfChallanNo.getText().trim()  : null;
        if (challan != null && challan.isEmpty()) challan = null;

        List<BheemEntry> results = bheemService.searchReport(
                workerId, wrapperId, from, to, challan, bheemId);

        tblReport.setItems(FXCollections.observableArrayList(results));
        updateTotals(results);
    }

    private void updateTotals(List<BheemEntry> rows) {
        BigDecimal totalWt = rows.stream()
                .map(e -> e.getWeight() != null ? e.getWeight() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        lblTotalBheem.setText("Total Bheem: " + rows.size());
        lblTotalWeight.setText("Total Wt: " + totalWt.toPlainString() + " kg");
    }

    // ── Quality-wise Summary ────────────────────────────────────────────
   /** @FXML
    public void onQualityWise() {
        Long wid = cbWorker.getValue() != null ? cbWorker.getValue().getId() : null;
        List<Object[]> summary = bheemService.qualityWiseSummary(wid, dpFrom.getValue(), dpTo.getValue());
        if (summary.isEmpty()) {GlobalUI.warn("No data for the selected filter."); return; }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-25s %8s %14s %16s %16s%n",
                "Bheem Name", "Taar", "No. of Bheem", "Total Wt(kg)", "Grand Total"));
        sb.append("-".repeat(82)).append("\n");
        long totalBheem = 0; BigDecimal totalWt = BigDecimal.ZERO;
        for (Object[] row : summary) {
            String name  = row[0] != null ? row[0].toString() : "";
            String taar  = row[1] != null ? row[1].toString() : "";
            long count   = row[2] != null ? Long.parseLong(row[2].toString()) : 0L;
            BigDecimal wt= row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
            sb.append(String.format("%-25s %8s %14d %16s %16s%n",
                    name, taar, count, wt.toPlainString(), wt.toPlainString()));
            totalBheem += count; totalWt = totalWt.add(wt);
        }
        sb.append("-".repeat(82)).append("\n");
        sb.append(String.format("%-25s %8s %14d %16s %16s%n",
                "GRAND TOTAL", "", totalBheem,
                totalWt.toPlainString(), totalWt.toPlainString()));

        GlobalUI.showTextDialog("Quality-wise Summary", sb.toString());
    }

*/
   @FXML
   public void onQualityWise() {

       Long wid = cbWorker.getValue() != null ? cbWorker.getValue().getId() : null;

       List<Object[]> summary =
               bheemService.qualityWiseSummary(wid, dpFrom.getValue(), dpTo.getValue());

       if (summary.isEmpty()) {
           GlobalUI.warn("No data for the selected filter.");
           return;
       }

       showSummaryTable(summary);
   }
    private void showSummaryTable(List<Object[]> summary) {

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Quality-wise Summary");

        ButtonType okBtn = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().add(okBtn);

        TableView<Object[]> table = new TableView<>();

        TableColumn<Object[], String> colName = new TableColumn<>("Bheem Name");
        TableColumn<Object[], String> colTaar = new TableColumn<>("Taar");
        TableColumn<Object[], String> colCount = new TableColumn<>("No of Bheem");
        TableColumn<Object[], String> colWeight = new TableColumn<>("Total Wt (kg)");

        colName.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue()[0])));
        colTaar.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue()[1])));
        colCount.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue()[2])));
        colWeight.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue()[3])));

        table.getColumns().addAll(colName, colTaar, colCount, colWeight);

        long totalCount = 0;
        BigDecimal totalWeight = BigDecimal.ZERO;

        for (Object[] row : summary) {
            long count = row[2] != null ? Long.parseLong(row[2].toString()) : 0;
            BigDecimal wt = row[3] != null ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;

            totalCount += count;
            totalWeight = totalWeight.add(wt);
        }

        // ADD TOTAL ROW
        Object[] totalRow = new Object[]{
                "GRAND TOTAL", "", totalCount, totalWeight
        };

        List<Object[]> data = new ArrayList<>(summary);
        data.add(totalRow);

        table.setItems(FXCollections.observableArrayList(data));

        // 🎨 STYLE
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Object[] item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setStyle("");
                } else if ("GRAND TOTAL".equals(item[0])) {
                    setStyle("-fx-background-color:#d1fae5; -fx-font-weight:bold;");
                } else {
                    setStyle("-fx-border-color:#e5e7eb; -fx-border-width:0 0 1 0;");
                }
            }
        });

        // ALIGNMENT
        colCount.setStyle("-fx-alignment:CENTER;");
        colWeight.setStyle("-fx-alignment:CENTER-RIGHT;");

        table.setPrefHeight(400);
        table.setPrefWidth(650);

        dialog.getDialogPane().setContent(table);
        dialog.showAndWait();
    }
    // ── PDF Export ──────────────────────────────────────────────────────
    @FXML
    public void onPdf() throws IOException {
        if (tblReport.getItems().isEmpty()) { GlobalUI.warn("No data to export."); return; }
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("bheem_report.pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File f = fc.showSaveDialog(tblReport.getScene().getWindow());
        if (f != null) {
            Long wid = cbWorker.getValue() != null ? cbWorker.getValue().getId() : null;
            String wName = cbWorker.getValue() != null ? cbWorker.getValue().getName() : null;
            List<Object[]> summary = bheemService.qualityWiseSummary(
                    wid, dpFrom.getValue(), dpTo.getValue());
            pdfExporter.exportBheemReport(
                    new ArrayList<>(tblReport.getItems()),
                    summary, wName, dpFrom.getValue(), dpTo.getValue(),
                    f.toPath());
            GlobalUI.success("PDF saved successfully.");
        }
    }

    // ── Excel Export ────────────────────────────────────────────────────
    @FXML
    public void onExcel() throws IOException {
        if (!GlobalUI.validateExport(tblReport.getItems())) return;
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("bheem_report.xlsx");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        File f = fc.showSaveDialog(tblReport.getScene().getWindow());
        if (f != null) {
            excelExporter.exportBheemReport(
                    new ArrayList<>(tblReport.getItems()),
                    List.of(),                         // summary
                    null,                              // worker
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
        ta.setPrefHeight(400); ta.setPrefWidth(750);
        info.getDialogPane().setContent(ta);
        info.getDialogPane().setMinWidth(800);
        info.showAndWait();
    }*/

    //private void alert(String m) {
      //  new Alert(Alert.AlertType.INFORMATION, m, ButtonType.OK).showAndWait();

}
