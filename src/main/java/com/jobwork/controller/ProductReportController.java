package com.jobwork.controller;

import com.jobwork.domain.EntryStatus;
import com.jobwork.domain.JobWorker;
import com.jobwork.domain.ProductEntry;
import com.jobwork.domain.ProductName;
import com.jobwork.domain.ProductType;
import com.jobwork.domain.Unit;
import com.jobwork.service.JobWorkerService;
import com.jobwork.service.MasterService;
import com.jobwork.service.ProductService;
import com.jobwork.util.ExcelExporter;
import com.jobwork.util.GlobalUI;
import com.jobwork.util.PdfExporter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

@Component
public class ProductReportController implements Initializable {
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired private ProductService   productService;
    @Autowired private JobWorkerService workerService;
    @Autowired private MasterService    masterService;
    @Autowired private PdfExporter      pdfExporter;
    @Autowired private ExcelExporter    excelExporter;

    @FXML private ComboBox<JobWorker>   cbWorker;
    @FXML private ComboBox<ProductType> cbProductType;
    @FXML private ComboBox<ProductName> cbProductName;
    @FXML private DatePicker            dpFrom;
    @FXML private DatePicker            dpTo;
    @FXML private TextField             tfChallanNo;
    @FXML
    private TableColumn<ProductEntry, String> colAmount;
    @FXML private TableView<ProductEntry>               tblReport;
    @FXML private TableColumn<ProductEntry, String>     colDate;
    @FXML private TableColumn<ProductEntry, String>     colChallan;
    @FXML private TableColumn<ProductEntry, String>     colWorker;
    @FXML private TableColumn<ProductEntry, String>     colProductName;
    @FXML private TableColumn<ProductEntry, String>     colQuantity;
    @FXML private TableColumn<ProductEntry, String>     colUnit;
    @FXML private TableColumn<ProductEntry, String>     colWeight;
    @FXML private TableColumn<ProductEntry, String>     colWeightPerPiece;

    @FXML private Label lblTotal;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        cbWorker.setItems(FXCollections.observableArrayList(workerService.findAll()));
        cbProductType.setItems(FXCollections.observableArrayList(masterService.findAllTypes()));
        cbProductType.valueProperty().addListener((obs, old, type) -> {
            if (type != null)
                cbProductName.setItems(FXCollections.observableArrayList(
                        masterService.getProductNames(type.getId())));
            else
                cbProductName.setItems(FXCollections.emptyObservableList());
            cbProductName.getSelectionModel().clearSelection();
        });

        // ── Wire data columns ──
        colDate.setCellValueFactory(c -> new SimpleStringProperty(
                GlobalUI.formatDate(c.getValue().getEntryDate())
        ));
        colChallan.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getChallanNo() != null ? c.getValue().getChallanNo() : ""));
        colWorker.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getJobWorker() != null ? c.getValue().getJobWorker().getName() : ""));
        colProductName.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getProductName() != null ? c.getValue().getProductName().getName() : ""));
        colQuantity.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getQuantity() != null ? c.getValue().getQuantity().toPlainString() : ""));
        colUnit.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getUnit() != null ? c.getValue().getUnit().getName() : ""));
        colWeight.setCellValueFactory(c -> {
            BigDecimal v = c.getValue().getWeight();
            return new SimpleStringProperty(
                    v != null ? String.format("%.3f", v.doubleValue()) : ""
            );
        });
        colAmount.setCellValueFactory(c -> {
            BigDecimal v = c.getValue().getTotalAmount();
            return new SimpleStringProperty(
                    v != null ? "₹ " + String.format("%.2f", v.doubleValue()) : "₹ 0.00"
            );
        });
        colWeightPerPiece.setCellValueFactory(c -> {
            BigDecimal v = c.getValue().getWeightPerPiece();
            return new SimpleStringProperty(
                    v != null ? String.format("%.3f", v.doubleValue()) : ""
            );
        });

        // ── Add Edit column ONCE ──
       addEditColumn();
        // ── Add Delete column ONCE ──
       addDeleteColumn();
    }

    private void addEditColumn() {
        TableColumn<ProductEntry, Void> col = new TableColumn<>("Edit");
        col.setPrefWidth(70); col.setStyle("-fx-alignment:CENTER;");
        col.setCellFactory(c -> new TableCell<>() {
            final Button btn = new Button("✏ Edit");
            {
                btn.setStyle(
                        "-fx-background-color:linear-gradient(to right,#1e3a8a,#1d4ed8);" +
                                "-fx-text-fill:white;-fx-font-size:11px;-fx-font-weight:bold;" +
                                "-fx-padding:4 10;-fx-background-radius:6;-fx-cursor:hand;");
                btn.setOnAction(e -> openEditDialog(
                        getTableView().getItems().get(getIndex())));
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btn);
            }
        });
        tblReport.getColumns().add(col);
    }

    private void addDeleteColumn() {
        TableColumn<ProductEntry, Void> col = new TableColumn<>("Delete");
        col.setPrefWidth(80); col.setStyle("-fx-alignment:CENTER;");
        col.setCellFactory(c -> new TableCell<>() {
            final Button btn = new Button("🗑 Del");
            {
                btn.setStyle(
                        "-fx-background-color:linear-gradient(to right,#dc2626,#b91c1c);" +
                                "-fx-text-fill:white;-fx-font-size:11px;-fx-font-weight:bold;" +
                                "-fx-padding:4 10;-fx-background-radius:6;-fx-cursor:hand;");
                btn.setOnAction(e -> {
                    ProductEntry entry = getTableView().getItems().get(getIndex());
                    if (GlobalUI.confirm("Delete Entry",
                            "Are you sure you want to delete this entry?")) {

                        productService.delete(entry.getId());
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

    private void openEditDialog(ProductEntry entry) {
        Dialog<ProductEntry> dialog = new Dialog<>();
        dialog.setTitle("Edit Product Entry");
        dialog.setHeaderText("Edit — Challan: " + entry.getChallanNo());

        ButtonType saveBtn = new ButtonType("💾 Save", ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.setStyle("-fx-padding:16;");

        // Product Type
        ComboBox<ProductType> cbType = new ComboBox<>(
                FXCollections.observableArrayList(masterService.findAllTypes()));
        cbType.setValue(entry.getProductType()); cbType.setPrefWidth(200);

        // Product Name — cascades from type
        ComboBox<ProductName> cbName = new ComboBox<>();
        if (entry.getProductType() != null)
            cbName.setItems(FXCollections.observableArrayList(
                    masterService.getProductNames(entry.getProductType().getId())));
        cbName.setValue(entry.getProductName()); cbName.setPrefWidth(200);
        cbType.valueProperty().addListener((o, ov, nv) -> {
            if (nv != null)
                cbName.setItems(FXCollections.observableArrayList(
                        masterService.getProductNames(nv.getId())));
            cbName.getSelectionModel().clearSelection();
        });

        // Quantity
        TextField tfQty = new TextField(
                entry.getQuantity() != null ? entry.getQuantity().toPlainString() : "");
        tfQty.setPrefWidth(120);

        // Unit
        ComboBox<Unit> cbUnit = new ComboBox<>(
                FXCollections.observableArrayList(masterService.findAllUnits()));
        cbUnit.setValue(entry.getUnit()); cbUnit.setPrefWidth(120);

        // Weight
        TextField tfWt = new TextField(
                entry.getWeight() != null ? entry.getWeight().toPlainString() : "");
        tfWt.setPrefWidth(120);

        // Wt/Piece
        TextField tfWpp = new TextField(
                entry.getWeightPerPiece() != null
                        ? entry.getWeightPerPiece().toPlainString() : "");
        tfWpp.setPrefWidth(120);
        // 🔥 AUTO CALCULATION (ADD HERE)
        tfWt.textProperty().addListener((obs, oldVal, newVal) ->
                recalcWtPerPiece(tfWt, tfQty, tfWpp));

        tfQty.textProperty().addListener((obs, oldVal, newVal) ->
                recalcWtPerPiece(tfWt, tfQty, tfWpp));

        // Date
        DatePicker dp = new DatePicker(entry.getEntryDate()); dp.setPrefWidth(200);

        grid.addRow(0, new Label("Product Type:"), cbType);
        grid.addRow(1, new Label("Product Name:"), cbName);
        grid.addRow(2, new Label("Quantity:"),     tfQty);
        grid.addRow(3, new Label("Unit:"),          cbUnit);
        grid.addRow(4, new Label("Weight (kg):"),   tfWt);
        grid.addRow(5, new Label("Wt/Piece:"),      tfWpp);
        grid.addRow(6, new Label("Date:"),          dp);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setMinWidth(440);

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                try {
                    entry.setProductType(cbType.getValue());
                    entry.setProductName(cbName.getValue());
                    if (!tfQty.getText().isBlank())
                        entry.setQuantity(new BigDecimal(tfQty.getText().trim()));
                    entry.setUnit(cbUnit.getValue());
                    if (!tfWt.getText().isBlank())
                        entry.setWeight(new BigDecimal(tfWt.getText().trim()));
                    if (!tfWpp.getText().isBlank())
                        entry.setWeightPerPiece(new BigDecimal(tfWpp.getText().trim()));
                    entry.setEntryDate(dp.getValue());
                    entry.setStatus(EntryStatus.DRAFT);
                    return entry;
                } catch (NumberFormatException ex) { return null; }
            }
            return null;
        });

        Optional<ProductEntry> result = dialog.showAndWait();
        result.ifPresent(updated -> {
            productService.save(updated);
            GlobalUI.success("Entry updated successfully.");
            onSearch();
        });
    }
    private void recalcWtPerPiece(TextField tfWt, TextField tfQty, TextField tfWpp) {
        try {
            String w = tfWt.getText();
            String q = tfQty.getText();

            if (w == null || w.isBlank() || q == null || q.isBlank()) return;

            BigDecimal weight = new BigDecimal(w.trim());
            BigDecimal qty    = new BigDecimal(q.trim());

            if (qty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal result = weight.divide(qty, 3, RoundingMode.HALF_UP);
                tfWpp.setText(result.toPlainString());
            }

        } catch (Exception ignored) {
            // ignore typing errors
        }
    }
    @FXML
    public void onSearch() {
        Long workerId = cbWorker.getValue()      != null ? cbWorker.getValue().getId()      : null;
        Integer typeId= cbProductType.getValue() != null ? cbProductType.getValue().getId() : null;
        Integer nameId= cbProductName.getValue() != null ? cbProductName.getValue().getId() : null;
        LocalDate from= dpFrom.getValue();
        LocalDate to  = dpTo.getValue();
        String challan= tfChallanNo.getText() != null ? tfChallanNo.getText().trim() : null;
        if (challan != null && challan.isEmpty()) challan = null;

        List<ProductEntry> results = productService.searchReport(
                workerId, from, to, challan, typeId, nameId);
        tblReport.setItems(FXCollections.observableArrayList(results));

        BigDecimal tQty = results.stream()
                .map(e -> e.getQuantity() != null ? e.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tWt  = results.stream()
                .map(e -> e.getWeight() != null ? e.getWeight() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = results.stream()
                .map(ProductEntry::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        lblTotal.setText(
                "Rows: " + results.size()
                        + "   Total Qty: " + tQty.toPlainString()
                        + "   Total Wt: " + String.format("%.3f", tWt.doubleValue()) + " kg"
                        + "   Total Amount: ₹ " + String.format("%.2f", totalAmount.doubleValue())
        );
    }

    @FXML
    public void onProductWiseSummary() {
        Long wid = cbWorker.getValue() != null ? cbWorker.getValue().getId() : null;
        List<Object[]> summary = productService.productWiseSummary(
                wid, dpFrom.getValue(), dpTo.getValue());
        if (summary.isEmpty()) { GlobalUI.warn("No data for selected filter."); return; }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-32s %12s %14s %14s%n",
                "Product Name", "Total Qty", "Total Wt(kg)", "Grand Total"));
        sb.append("-".repeat(76)).append("\n");
        BigDecimal sumQty = BigDecimal.ZERO, sumWt = BigDecimal.ZERO;
        for (Object[] row : summary) {
            String name = row[0] != null ? row[0].toString() : "";
            BigDecimal qty = row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
            BigDecimal wt  = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
            sb.append(String.format("%-32s %12s %14s %14s%n",
                    name, qty.toPlainString(), wt.toPlainString(),
                    qty.add(wt).toPlainString()));
            sumQty = sumQty.add(qty); sumWt = sumWt.add(wt);
        }
        sb.append("-".repeat(76)).append("\n");
        sb.append(String.format("%-32s %12s %14s %14s%n",
                "GRAND TOTAL", sumQty.toPlainString(),
                sumWt.toPlainString(), sumQty.add(sumWt).toPlainString()));

        GlobalUI.showTextDialog("Product-wise Summary", sb.toString());
    }

    @FXML
    public void onPdf() throws IOException {
        if (!GlobalUI.validateExport(tblReport.getItems())) return;
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("product_report.pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File f = fc.showSaveDialog(tblReport.getScene().getWindow());
        if (f != null) {
            Long wid = cbWorker.getValue() != null ? cbWorker.getValue().getId() : null;
            String wName = cbWorker.getValue() != null ? cbWorker.getValue().getName() : null;
            List<Object[]> summary = productService.productWiseSummary(
                    wid, dpFrom.getValue(), dpTo.getValue());
            pdfExporter.exportProductReport(
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
        fc.setInitialFileName("product_report.xlsx");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        File f = fc.showSaveDialog(tblReport.getScene().getWindow());
        if (f != null) {
            excelExporter.exportProductReport(
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

    /**private void showSummaryDialog(String title, String content) {
        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle(title); info.setHeaderText(null);
        TextArea ta = new TextArea(content);
        ta.setEditable(false);
        ta.setFont(Font.font("Monospaced", 13));
        ta.setPrefHeight(400); ta.setPrefWidth(720);
        info.getDialogPane().setContent(ta);
        info.getDialogPane().setMinWidth(760);
        info.showAndWait();
    }*/

   // private void alert(String m) {
       // new Alert(Alert.AlertType.INFORMATION, m, ButtonType.OK).showAndWait();
   // }
}
