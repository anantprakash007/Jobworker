package com.jobwork.controller;

import com.jobwork.config.FxmlView;
import com.jobwork.domain.*;
import com.jobwork.service.*;
import com.jobwork.util.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
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

/**
 * ProductEntryController
 * ──────────────────────────────────────────────────────────────────
 * DUPLICATE CHECK — fires inside onAdd() BEFORE the row enters the table.
 *
 * Flow when Add Row is clicked:
 *  1. Validate all fields (normal validation).
 *  2. Require that worker / challanNo / date are filled (they are, from step 1).
 *  3. Call productService.isDuplicate(workerId, challanNo, date, productNameId)
 *       → native COUNT(*) query — 100% reliable, no JPQL join issues.
 *  4a. count == 0  → add row to table, done.
 *  4b. count >  0  → fetch existing row, show alert, open review popup.
 *       In the popup the user either:
 *         ☑  Confirm Re-Entry  → existing DB row overwritten immediately
 *         ✖  Skip              → incoming row discarded
 *       Either way the form clears for the next row.
 *
 * KEY RULE: the row is NEVER added to the local table when a DB duplicate
 * is detected. The popup saves directly to DB via ProductService.applyReEntry().
 * ──────────────────────────────────────────────────────────────────
 */
@Slf4j
@Component
public class ProductEntryController implements Initializable {

    // ── Spring beans ─────────────────────────────────────────────
    @Autowired private ProductService       productService;
    @Autowired private JobWorkerService     workerService;
    @Autowired private MasterService        masterService;
    @Autowired private PdfExporter          pdfExporter;
    @Autowired private ExcelExporter        excelExporter;
    @Autowired private FileUploadUtil       fileUploadUtil;
    @Autowired private ProductImportService importService;
    @Autowired private ApplicationContext   appContext;
    @Autowired private com.jobwork.config.StageManager stageManager;
    @Autowired private MoneyService         moneyService;

    // ── Header ───────────────────────────────────────────────────
    @FXML private TextField             tfChallanNo;
    @FXML private ComboBox<JobWorker>   cbJobWorker;
    @FXML private DatePicker            dpDate;

    // ── Product row inputs ────────────────────────────────────────
    @FXML private ComboBox<ProductType> cbProductType;
    @FXML private ComboBox<ProductName> cbProductName;
    @FXML private TextField             tfQuantity;
    @FXML private ComboBox<Unit>        cbUnit;
    @FXML private TextField             tfWeight;
    @FXML private TextField             tfWeightPerPiece;

    // ── Table ────────────────────────────────────────────────────
    @FXML private TableView<ProductEntry>            tblEntries;
    @FXML private TableColumn<ProductEntry, String>  colProductType;
    @FXML private TableColumn<ProductEntry, String>  colProductName;
    @FXML private TableColumn<ProductEntry, String>  colQuantity;
    @FXML private TableColumn<ProductEntry, String>  colUnit;
    @FXML private TableColumn<ProductEntry, String>  colWeight;
    @FXML private TableColumn<ProductEntry, String>  colWeightPerPiece;

    // ── Footer ───────────────────────────────────────────────────
    @FXML private TextField tfTotalQty;
    @FXML private TextField tfTotalWeight;
    @FXML private Label     lblRowCount;
    @FXML private Label     lblStatus;
    @FXML private Button    btnAdd;
    @FXML private Button    btnExit;
    @FXML private Label     lblReceiptFile;
    @FXML private Button    btnViewReceipt;

    // ── State ────────────────────────────────────────────────────
    private final ObservableList<ProductEntry> entries =
            FXCollections.observableArrayList();
    private int     editingRowIndex  = -1;
    private boolean suppressAutoCalc = false;
    private String  uploadedReceiptPath;

    // ════════════════════════════════════════════════════════════
    //  INITIALIZE
    // ════════════════════════════════════════════════════════════
    @Override
    public void initialize(URL url, ResourceBundle rb) {

        FormUtil.allowDecimal(tfQuantity,       2);
        FormUtil.allowDecimal(tfWeight,         3);
        FormUtil.allowDecimal(tfWeightPerPiece, 3);

        Platform.runLater(() -> tfChallanNo.requestFocus());

        tblEntries.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tblEntries.setFixedCellSize(32);
        tblEntries.prefHeightProperty().bind(
                tblEntries.fixedCellSizeProperty().multiply(
                        javafx.beans.binding.Bindings.size(tblEntries.getItems()).add(1.01)));

        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        dpDate.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(LocalDate d) { return d != null ? df.format(d) : ""; }
            @Override public LocalDate fromString(String s) {
                try { return (s == null || s.isBlank()) ? null : LocalDate.parse(s, df); }
                catch (Exception e) { return null; }
            }
        });

        cbJobWorker.setItems(FXCollections.observableArrayList(workerService.findAll()));
        cbProductType.setItems(FXCollections.observableArrayList(masterService.findAllTypes()));
        cbUnit.setItems(FXCollections.observableArrayList(masterService.findAllUnits()));

        cbProductType.valueProperty().addListener((obs, old, type) -> {
            cbProductName.setItems(type != null
                    ? FXCollections.observableArrayList(masterService.getProductNames(type.getId()))
                    : FXCollections.emptyObservableList());
            cbProductName.getSelectionModel().clearSelection();
        });

        dpDate.setValue(LocalDate.now());

        // Table columns
        colProductType.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getProductType() != null ? c.getValue().getProductType().getName() : ""));
        colProductName.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getProductName() != null ? c.getValue().getProductName().getName() : ""));
        colQuantity.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getQuantity() != null ? c.getValue().getQuantity().toPlainString() : ""));
        colUnit.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getUnit() != null ? c.getValue().getUnit().getName() : ""));
        colWeight.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getWeight() != null ? c.getValue().getWeight().toPlainString() : ""));
        colWeightPerPiece.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getWeightPerPiece() != null
                        ? c.getValue().getWeightPerPiece().toPlainString() : ""));

        tfQuantity.focusedProperty().addListener((obs, o, n) -> {
            if (n) Platform.runLater(tfQuantity::selectAll);
        });
        tfWeight.focusedProperty().addListener((obs, o, n) -> {
            if (n) Platform.runLater(tfWeight::selectAll);
        });

        addEditRowColumn();
        addDeleteRowColumn();
        setupLiveValidation();
        setupKeyboardFlow();

        tblEntries.setItems(entries);

        tfWeight.textProperty().addListener((obs, o, n) -> {
            if (!suppressAutoCalc) autoCalcWeightPerPiece();
        });
        tfQuantity.textProperty().addListener((obs, o, n) -> {
            if (!suppressAutoCalc) autoCalcWeightPerPiece();
        });
    }

    private void setupLiveValidation() {
        tfChallanNo.textProperty().addListener((o,a,b)    -> FormUtil.clearError(tfChallanNo));
        tfQuantity.textProperty().addListener((o,a,b)     -> FormUtil.clearError(tfQuantity));
        tfWeight.textProperty().addListener((o,a,b)       -> FormUtil.clearError(tfWeight));
        tfWeightPerPiece.textProperty().addListener((o,a,b)-> FormUtil.clearError(tfWeightPerPiece));
        cbProductType.valueProperty().addListener((o,a,b) -> FormUtil.clearError(cbProductType));
        cbProductName.valueProperty().addListener((o,a,b) -> FormUtil.clearError(cbProductName));
        cbUnit.valueProperty().addListener((o,a,b)        -> FormUtil.clearError(cbUnit));
    }

    private void setupKeyboardFlow() {
        FormUtil.moveNext(tfChallanNo, cbJobWorker);
        FormUtil.moveNextCombo(cbJobWorker, dpDate.getEditor());
        FormUtil.moveNext(dpDate.getEditor(), cbProductType);
        FormUtil.moveNextCombo(cbProductType, cbProductName);
        FormUtil.moveNextCombo(cbProductName, tfQuantity);
        FormUtil.moveNext(tfQuantity, cbUnit);
        FormUtil.moveNextCombo(cbUnit, tfWeight);
        FormUtil.moveNext(tfWeight, tfWeightPerPiece);
        tfWeightPerPiece.setOnAction(e -> onAdd());
    }

    // ── Auto-calc ─────────────────────────────────────────────────
    private void autoCalcWeightPerPiece() {
        String wtText  = tfWeight.getText()   == null ? "" : tfWeight.getText().trim();
        String qtyText = tfQuantity.getText() == null ? "" : tfQuantity.getText().trim();
        if (wtText.isEmpty() || qtyText.isEmpty()) { tfWeightPerPiece.clear(); return; }
        try {
            BigDecimal weight   = new BigDecimal(wtText);
            BigDecimal quantity = new BigDecimal(qtyText);
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) { tfWeightPerPiece.clear(); return; }
            suppressAutoCalc = true;
            tfWeightPerPiece.setText(
                    weight.divide(quantity, 3, RoundingMode.HALF_UP).toPlainString());
            suppressAutoCalc = false;
        } catch (NumberFormatException | ArithmeticException ex) {
            tfWeightPerPiece.clear();
        }
    }

    @FXML public void onProductTypeSelected() { /* cascade via listener */ }

    // ════════════════════════════════════════════════════════════
    //  ADD ROW  ← DUPLICATE CHECK LIVES HERE
    // ════════════════════════════════════════════════════════════
    @FXML
    public void onAdd() {

        // ── Step 1: Full field validation ─────────────────────────
        ValidationUtil.start();
        ValidationUtil.required(tfChallanNo,  "Challan required");
        ValidationUtil.required(cbJobWorker,  "Worker required");
        ValidationUtil.required(dpDate,        "Select Date");
        ValidationUtil.required(cbProductType, "Product Type required");
        ValidationUtil.required(cbProductName, "Product Name required");
        ValidationUtil.number(tfQuantity,      "Invalid quantity");
        ValidationUtil.required(cbUnit,        "Unit required");
        ValidationUtil.number(tfWeight,        "Invalid weight");
        if (!ValidationUtil.validate()) return;

        BigDecimal qty = new BigDecimal(tfQuantity.getText().trim());
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            FormUtil.setError(tfQuantity, "Quantity must be > 0"); return;
        }

        BigDecimal wt;
        try {
            wt = new BigDecimal(tfWeight.getText().trim());
            if (wt.compareTo(BigDecimal.ZERO) < 0) {
                FormUtil.setError(tfWeight, "Weight cannot be negative"); return;
            }
        } catch (NumberFormatException ex) {
            FormUtil.setError(tfWeight, "Invalid number"); return;
        }

        BigDecimal wtpc = BigDecimal.ZERO;
        String wtpcText = tfWeightPerPiece.getText().trim();
        if (!wtpcText.isEmpty()) {
            try { wtpc = new BigDecimal(wtpcText); }
            catch (NumberFormatException ex) {
                FormUtil.setError(tfWeightPerPiece, "Invalid number"); return;
            }
        }

        // ── Step 2: Build the candidate ProductEntry ──────────────
        ProductEntry candidate = ProductEntry.builder()
                .productType(cbProductType.getValue())
                .productName(cbProductName.getValue())
                .quantity(qty)
                .unit(cbUnit.getValue())
                .weight(wt)
                .weightPerPiece(wtpc)
                .status(EntryStatus.DRAFT)
                .build();

        // ── Step 3: If updating an existing unsaved table row, skip DB check ─
        //            These rows aren't in the DB yet — no possible duplicate.
        if (editingRowIndex >= 0) {
            entries.set(editingRowIndex, candidate);
            editingRowIndex = -1;
            if (btnAdd != null) btnAdd.setText("＋  Add Row");
            lblStatus.setText("● Row updated");
            clearEntryFields();
            refreshTotals();
            GlobalUI.success("Row updated.\nTotal rows: " + entries.size());
            return;
        }

        // ── Step 4: Extract header values (validated above — never null) ──────
        JobWorker worker    = cbJobWorker.getValue();
        String    challanNo = tfChallanNo.getText().trim();
        LocalDate date      = dpDate.getValue();
        Integer   nameId    = cbProductName.getValue().getId();

        log.debug("onAdd → checking duplicate: workerId={} challanNo={} date={} nameId={}",
                worker.getId(), challanNo, date, nameId);

        // ── Step 5: DB duplicate check — native COUNT(*) ──────────
        boolean dup = productService.isDuplicateQty(
                worker.getId(),
                challanNo,
                date,
                cbProductName.getValue().getName(),
                qty
        );

        if (!dup) {
            // ── No duplicate → add to table normally ──────────────
            log.debug("onAdd → no duplicate found, adding to table");
            entries.add(candidate);
            lblStatus.setText("● " + entries.size() + " row(s) added");
            clearEntryFields();
            refreshTotals();
            GlobalUI.success("1 row added.\nTotal rows: " + entries.size());
            return;
        }

        // ── Step 6: Duplicate found → alert + popup ───────────────
        log.info("onAdd → duplicate detected for challanNo={} productName={}",
                challanNo, cbProductName.getValue().getName());

        // Fetch the full existing row for side-by-side display
        Optional<ProductEntry> existingOpt =
                productService.getExistingDuplicate(worker.getId(), challanNo, date, nameId);

        if (existingOpt.isEmpty()) {
            // Extremely rare: count said >0 but fetch returned nothing (race condition)
            // Safe to treat as no-duplicate and proceed
            log.warn("onAdd → countDuplicate > 0 but findOneDuplicate returned empty. Proceeding.");
            entries.add(candidate);
            clearEntryFields();
            refreshTotals();
            return;
        }

        ProductEntry existing = existingOpt.get();

        // Stamp header onto candidate so the popup shows complete info
        candidate.setJobWorker(worker);
        candidate.setChallanNo(challanNo);
        candidate.setEntryDate(date);
        candidate.setReceiptPath(uploadedReceiptPath);

        // Alert — shown BEFORE the popup so the user reads it first
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("⚠  Duplicate Entry Detected");
        alert.setHeaderText(
                "Challan  \"" + challanNo + "\"  already has\n"
                        + "\"" + cbProductName.getValue().getName()
                        + "\"  recorded in the database.");
        alert.setContentText(
                "Existing →  Qty: " + fmt(existing.getQuantity())
                        + "   Weight: " + fmt(existing.getWeight()) + " kg"
                        + "   Wt/Pc: " + fmt(existing.getWeightPerPiece()) + "\n"
                        + "New      →  Qty: " + fmt(qty)
                        + "   Weight: " + fmt(wt) + " kg"
                        + "   Wt/Pc: " + fmt(wtpc) + "\n\n"
                        + "Click OK to open the review window.\n"
                        + "Tick the row to OVERWRITE, or click Skip to keep existing data.");
        alert.showAndWait();

        // Open popup — row NOT added to table; popup handles DB write directly
        openSingleDuplicatePopup(new DuplicateReviewRow(existing, candidate), challanNo);
    }

    // ════════════════════════════════════════════════════════════
    //  SINGLE-ROW DUPLICATE POPUP  (called from onAdd)
    // ════════════════════════════════════════════════════════════
    private void openSingleDuplicatePopup(DuplicateReviewRow reviewRow, String challanNo) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/duplicate_review.fxml"));
            loader.setControllerFactory(appContext::getBean);
            Parent root = loader.load();

            DuplicateReviewController ctrl = loader.getController();
            ctrl.init(
                    new DuplicateCheckResult(List.of(reviewRow), List.of()),
                    challanNo,
                    () -> {
                        // Popup closed — clear form so user can enter next row
                        clearEntryFields();
                        refreshTotals();
                        lblStatus.setText("● Duplicate reviewed. Enter next row.");
                        Platform.runLater(() -> cbProductType.requestFocus());
                    }
            );

            Stage popup = new Stage();
            popup.setTitle("⚠  Duplicate Found — Challan: " + challanNo);
            popup.initModality(Modality.APPLICATION_MODAL);
            popup.initOwner(tblEntries.getScene().getWindow());
            popup.setScene(new Scene(root));
            popup.setResizable(true);
            popup.show();

        } catch (IOException ex) {
            GlobalUI.warn("Could not open review window:\n" + ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    //  SAVE  (batch duplicate check for any remaining table rows)
    // ════════════════════════════════════════════════════════════
    @FXML
    public void onSave() {
        if (!validateHeader()) return;
        if (entries.isEmpty()) { GlobalUI.warn("Add at least one product row first."); return; }
        saveWithDuplicateCheck(stampEntries(EntryStatus.DRAFT), false);
    }

    // ════════════════════════════════════════════════════════════
    //  FINAL SUBMIT
    // ════════════════════════════════════════════════════════════
    @FXML
    public void onFinalSubmit() {
        if (!validateHeader()) return;
        if (entries.isEmpty()) { GlobalUI.warn("Add at least one product row first."); return; }
        if (!GlobalUI.confirm("Submit Entry",
                "Submit all " + entries.size() + " rows?\nThis cannot be undone.")) return;
        saveWithDuplicateCheck(stampEntries(EntryStatus.SUBMITTED), true);
    }

    // ── Core batch-save with duplicate check ──────────────────────
    private void saveWithDuplicateCheck(List<ProductEntry> stamped, boolean isFinal) {
        Long      workerId  = cbJobWorker.getValue().getId();
        String    challanNo = tfChallanNo.getText().trim();
        LocalDate date      = dpDate.getValue();

        DuplicateCheckResult result =
                productService.checkDuplicates(workerId, challanNo, date, stamped);

        if (!result.hasDuplicates()) {
            for (ProductEntry e : result.nonDuplicates()) productService.save(e);
            String mode = isFinal ? "submitted" : "saved as Draft";
            lblStatus.setText(isFinal ? "● Submitted" : "● Saved — Draft");
            GlobalUI.success("All " + stamped.size() + " row(s) " + mode + ".");
            if (isFinal) onReset();
            return;
        }

        int dupCount = result.rows().size();
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Duplicate Entries Detected");
        alert.setHeaderText("Challan  \"" + challanNo + "\"  already has  "
                + dupCount + "  matching record" + (dupCount == 1 ? "" : "s") + ".");
        alert.setContentText("Review window will open.\n"
                + "Tick rows to OVERWRITE → click Confirm Re-Entry.\n"
                + "Unticked rows will be skipped.");
        alert.showAndWait();

        openBatchDuplicatePopup(result, challanNo, isFinal);
    }

    private void openBatchDuplicatePopup(DuplicateCheckResult result,
                                         String challanNo, boolean isFinal) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/duplicate_review.fxml"));
            loader.setControllerFactory(appContext::getBean);
            Parent root = loader.load();

            DuplicateReviewController ctrl = loader.getController();
            ctrl.init(result, challanNo, () -> {
                lblStatus.setText(isFinal ? "● Submitted" : "● Saved — Draft");
                if (isFinal) onReset();
            });

            Stage popup = new Stage();
            popup.setTitle("⚠  Duplicate Review — Challan: " + challanNo);
            popup.initModality(Modality.APPLICATION_MODAL);
            popup.initOwner(tblEntries.getScene().getWindow());
            popup.setScene(new Scene(root));
            popup.setResizable(true);
            popup.show();

        } catch (IOException ex) {
            GlobalUI.warn("Could not open review window:\n" + ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    //  EDIT (row-level ✏ button)
    // ════════════════════════════════════════════════════════════
    @FXML
    public void onEdit() {
        GlobalUI.warn("Use the  ✏  button on any table row to load it for editing.");
    }

    private void loadRowForEdit(int index) {
        ProductEntry e = entries.get(index);
        editingRowIndex  = index;
        suppressAutoCalc = true;
        try {
            if (e.getJobWorker() != null) cbJobWorker.setValue(e.getJobWorker());
            if (e.getChallanNo()  != null) tfChallanNo.setText(e.getChallanNo());
            if (e.getEntryDate()  != null) dpDate.setValue(e.getEntryDate());
            cbProductType.setValue(e.getProductType());
            cbProductName.setValue(e.getProductName());
            cbUnit.setValue(e.getUnit());
            tfQuantity.setText(e.getQuantity()         != null ? e.getQuantity().toPlainString()       : "");
            tfWeight.setText(e.getWeight()             != null ? e.getWeight().toPlainString()          : "");
            tfWeightPerPiece.setText(e.getWeightPerPiece() != null ? e.getWeightPerPiece().toPlainString() : "");
        } finally { suppressAutoCalc = false; }
        if (btnAdd != null) btnAdd.setText("✎  Update Row");
        lblStatus.setText("● Editing row " + (index + 1));
        cbProductType.requestFocus();
        Platform.runLater(tfQuantity::selectAll);
    }

    // ════════════════════════════════════════════════════════════
    //  RESET / EXIT
    // ════════════════════════════════════════════════════════════
    @FXML
    public void onReset() {
        tfChallanNo.clear();
        cbJobWorker.getSelectionModel().clearSelection();
        dpDate.setValue(LocalDate.now());
        clearEntryFields();
        entries.clear();
        refreshTotals();
        uploadedReceiptPath = null;
        lblReceiptFile.setText("No file selected");
        if (btnViewReceipt != null) btnViewReceipt.setVisible(false);
        if (btnAdd != null) btnAdd.setText("＋  Add Row");
        editingRowIndex = -1;
        lblStatus.setText("● New Entry");
        Platform.runLater(() -> tfChallanNo.requestFocus());
    }

    @FXML
    public void onExit() { stageManager.showScene(FxmlView.MAIN_LAYOUT); }

    // ════════════════════════════════════════════════════════════
    //  RECEIPT
    // ════════════════════════════════════════════════════════════
    @FXML
    public void onUploadReceipt() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Receipt File");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Images / PDF","*.jpg","*.jpeg","*.png","*.pdf"));
        File file = fc.showOpenDialog(
                tblEntries != null ? tblEntries.getScene().getWindow() : null);
        if (file != null) {
            try {
                uploadedReceiptPath = fileUploadUtil.saveReceipt(
                        file, tfChallanNo.getText().trim());
                lblReceiptFile.setText("✔ " + file.getName());
                lblReceiptFile.setStyle("-fx-text-fill:#16a34a; -fx-font-weight:bold;");
                if (btnViewReceipt != null) btnViewReceipt.setVisible(true);
                lblStatus.setText("● Receipt uploaded");
            } catch (IOException ex) { GlobalUI.warn("Upload failed: " + ex.getMessage()); }
        }
    }

    @FXML
    public void onViewReceipt() {
        if (uploadedReceiptPath != null) {
            try { fileUploadUtil.openReceipt(uploadedReceiptPath); }
            catch (IOException ex) { GlobalUI.warn("Cannot open receipt: " + ex.getMessage()); }
        }
    }

    // ════════════════════════════════════════════════════════════
    //  PDF / EXCEL EXPORT
    // ════════════════════════════════════════════════════════════
    @FXML
    public void onGeneratePdf() {
        if (entries.isEmpty()) { GlobalUI.warn("No data to export."); return; }
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("product_entry_" + tfChallanNo.getText().trim() + ".pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files","*.pdf"));
        File f = fc.showSaveDialog(tblEntries != null ? tblEntries.getScene().getWindow() : null);
        if (f != null) {
            try {
                pdfExporter.exportProductReport(new ArrayList<>(entries), null,
                        cbJobWorker.getValue() != null ? cbJobWorker.getValue().getName() : null,
                        dpDate.getValue(), dpDate.getValue(), f.toPath());
                GlobalUI.success("PDF saved to:\n" + f.getAbsolutePath());
            } catch (IOException ex) { GlobalUI.warn("PDF export failed: " + ex.getMessage()); }
        }
    }

    @FXML
    public void onExcel() {
        if (entries.isEmpty()) { GlobalUI.warn("No data to export."); return; }
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("product_entry_" + tfChallanNo.getText().trim() + ".xlsx");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files","*.xlsx"));
        File f = fc.showSaveDialog(tblEntries != null ? tblEntries.getScene().getWindow() : null);
        if (f != null) {
            try {
                excelExporter.exportProductReport(new ArrayList<>(entries), List.of(),
                        cbJobWorker.getValue() != null ? cbJobWorker.getValue().getName() : null,
                        dpDate.getValue(), dpDate.getValue(), f.toPath());
                GlobalUI.success("Excel saved to:\n" + f.getAbsolutePath());
            } catch (IOException ex) { GlobalUI.warn("Excel export failed: " + ex.getMessage()); }
        }
    }

    // ════════════════════════════════════════════════════════════
    //  IMPORT EXCEL
    // ════════════════════════════════════════════════════════════
    @FXML
    public void onImportExcel() {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Excel File");
            File file = fc.showOpenDialog(null);
            if (file == null) return;
            GlobalUI.openWindow("/fxml/product_import_preview.fxml", "Import Preview",
                    controller -> ((ProductImportPreviewController) controller).loadExcel(file));
        } catch (Exception e) { GlobalUI.warn("Import failed: " + e.getMessage()); }
    }

    // ════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ════════════════════════════════════════════════════════════

    private void addEditRowColumn() {
        TableColumn<ProductEntry, Void> col = new TableColumn<>("Edit");
        col.setPrefWidth(70);
        col.setStyle("-fx-alignment:CENTER;");
        col.setCellFactory(c -> new TableCell<>() {
            final Button btn = new Button("✏");
            { btn.setStyle(
                    "-fx-background-color:linear-gradient(to right,#1e3a8a,#1d4ed8);" +
                            "-fx-text-fill:white;-fx-font-size:12px;-fx-padding:4 10;" +
                            "-fx-background-radius:6;-fx-cursor:hand;");
                btn.setOnAction(e -> { int i = getIndex();
                    if (i >= 0 && i < entries.size()) loadRowForEdit(i); }); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty); setGraphic(empty ? null : btn); }
        });
        tblEntries.getColumns().add(col);
    }

    private void addDeleteRowColumn() {
        TableColumn<ProductEntry, Void> col = new TableColumn<>("Del");
        col.setPrefWidth(65);
        col.setStyle("-fx-alignment:CENTER;");
        col.setCellFactory(c -> new TableCell<>() {
            final Button btn = new Button("🗑");
            { btn.setStyle(
                    "-fx-background-color:linear-gradient(to right,#dc2626,#b91c1c);" +
                            "-fx-text-fill:white;-fx-font-size:12px;-fx-padding:4 10;" +
                            "-fx-background-radius:6;-fx-cursor:hand;");
                btn.setOnAction(e -> { int i = getIndex();
                    if (i >= 0 && i < entries.size()) {
                        entries.remove(i);
                        if (editingRowIndex == i) {
                            editingRowIndex = -1;
                            if (btnAdd != null) btnAdd.setText("＋  Add Row");
                        }
                        refreshTotals();
                        lblStatus.setText("● Row deleted. Total: " + entries.size()); } }); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty); setGraphic(empty ? null : btn); }
        });
        tblEntries.getColumns().add(col);
    }

    private void refreshTotals() {
        BigDecimal tQty = entries.stream()
                .map(e -> e.getQuantity() != null ? e.getQuantity() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tWt = entries.stream()
                .map(e -> e.getWeight() != null ? e.getWeight() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        tfTotalQty.setText(tQty.toPlainString());
        tfTotalWeight.setText(tWt.toPlainString());
        lblRowCount.setText(String.valueOf(entries.size()));
    }

    private void clearEntryFields() {
        suppressAutoCalc = true;
        try {
            cbProductType.getSelectionModel().clearSelection();
            cbProductName.getItems().clear();
            cbProductName.getSelectionModel().clearSelection();
            cbUnit.getSelectionModel().clearSelection();
            tfQuantity.clear();
            tfWeight.clear();
            tfWeightPerPiece.clear();
            editingRowIndex = -1;
        } finally { suppressAutoCalc = false; }
    }

    private List<ProductEntry> stampEntries(EntryStatus status) {
        JobWorker worker    = cbJobWorker.getValue();
        String    challanNo = tfChallanNo.getText().trim();
        LocalDate date      = dpDate.getValue();
        List<ProductEntry> stamped = new ArrayList<>();
        for (ProductEntry e : entries) {
            e.setJobWorker(worker);
            e.setChallanNo(challanNo);
            e.setEntryDate(date);
            e.setReceiptPath(uploadedReceiptPath);
            e.setStatus(status);
            stamped.add(e);
        }
        return stamped;
    }

    private boolean validateHeader() {
        ValidationUtil.start();
        ValidationUtil.required(tfChallanNo, "Challan required");
        ValidationUtil.required(cbJobWorker,  "Worker required");
        ValidationUtil.required(dpDate,        "Select Date");
        return ValidationUtil.validate();
    }

    private String fmt(BigDecimal v) { return v != null ? v.toPlainString() : "—"; }
}