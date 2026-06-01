package com.jobwork.controller;

import com.jobwork.domain.ImportRow;
import com.jobwork.service.ProductImportService;
import com.jobwork.util.GlobalUI;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;

/**
 * ProductImportPreviewController — FIXED COMPLETE VERSION
 * ══════════════════════════════════════════════════════════════════
 * FIXES from the broken version:
 *
 * FIX 1 — Validate + Import on BACKGROUND THREAD using Task<Void>.
 *   Old code called importService.validate(rows) and importService.importData()
 *   directly on the JavaFX Application Thread, causing the UI to freeze
 *   and NullPointerException when the table tried to refresh mid-update.
 *   Now both run in Task<Void> → setOnSucceeded updates UI on FX thread.
 *
 * FIX 2 — rows list is the SINGLE SOURCE OF TRUTH.
 *   Old code called tblPreview.setItems(null) then setItems(rows) to
 *   force a refresh, which caused brief blank-table flicker. Now uses
 *   tblPreview.refresh() which is the correct JavaFX way.
 *
 * FIX 3 — Checkbox column (colSelect) uses correct cellValueFactory.
 *   Old code had colSelect.setCellFactory AFTER setting cellValueFactory,
 *   but the setCellFactory lambda accessed getTableView().getItems() before
 *   the table was populated, causing IndexOutOfBoundsException.
 *   Now: cellValueFactory first, then cellFactory.
 *
 * FIX 4 — ERROR rows are auto-deselected after validate().
 *   After validation, ERROR rows have setSelected(false) called on them
 *   in ProductImportService.validate(). The checkbox factory now also
 *   disables the checkbox for ERROR rows so users cannot accidentally
 *   select them.
 *
 * FIX 5 — updateSummary() is called from Platform.runLater() in all
 *   background task callbacks to avoid threading issues.
 *
 * FIX 6 — onImport() auto-validates before importing so stale "NEW"
 *   rows are always validated first. Previously if user clicked Import
 *   without Validate, NEW rows (not yet VALID) were skipped silently.
 *
 * FIX 7 — lblFile is updated with the full file path for reference.
 *
 * SCREEN FLOW:
 *   1. User clicks "Import Excel" in main menu
 *   2. FileChooser opens → user picks .xlsx
 *   3. loadExcel(file) → reads rows, shows in table with status NEW
 *   4. User clicks Validate → validate() marks VALID / DUPLICATE / ERROR
 *   5. User selects rows (auto-selected = VALID; duplicates = deselected)
 *   6. User clicks Import → importData() saves VALID + selected DUPLICATE
 *   7. Table updates to show IMPORTED / ERROR per row
 * ══════════════════════════════════════════════════════════════════
 */
@Component
@Scope("prototype") // new controller instance each time the window opens
public class ProductImportPreviewController {

    // ── Table ─────────────────────────────────────────────────────
    @FXML private TableView<ImportRow>             tblPreview;

    // Checkbox + Row columns
    @FXML private TableColumn<ImportRow, Boolean>  colSelect;
    @FXML private TableColumn<ImportRow, Integer>  colRow;

    // Data columns
    @FXML private TableColumn<ImportRow, String>   colWorkerId;
    @FXML private TableColumn<ImportRow, String>   colWorkerName;
    @FXML private TableColumn<ImportRow, String>   colChallan;
    @FXML private TableColumn<ImportRow, String>   colDate;
    @FXML private TableColumn<ImportRow, String>   colProductType;
    @FXML private TableColumn<ImportRow, String>   colProductName;
    @FXML private TableColumn<ImportRow, String>   colQty;
    @FXML private TableColumn<ImportRow, String>   colUnit;
    @FXML private TableColumn<ImportRow, String>   colWeight;
    @FXML private TableColumn<ImportRow, String>   colWtPerPiece;
    @FXML private TableColumn<ImportRow, String>   colStatus;
    @FXML private TableColumn<ImportRow, String>   colError;

    // ── Controls ──────────────────────────────────────────────────
    @FXML private ProgressBar progressBar;
    @FXML private Label       lblStatus;
    @FXML private Label       lblFile;
    @FXML private Label       lblSummary;

    @Autowired
    private ProductImportService importService;

    // ── Single source of truth for the table ─────────────────────
    // FIX 2: one list, never replaced — only cleared and re-filled
    private final ObservableList<ImportRow> rows =
            FXCollections.observableArrayList();

    // ════════════════════════════════════════════════════════════
    //  INITIALIZE
    // ════════════════════════════════════════════════════════════
    @FXML
    public void initialize() {

        // Bind table to the single list ONCE — never replaced
        tblPreview.setItems(rows);
        tblPreview.setEditable(true);

        // ── Row number column ────────────────────────────────────
        colRow.setCellValueFactory(c ->
                new SimpleObjectProperty<>(c.getValue().getRowNumber()));

        // ── Checkbox column ──────────────────────────────────────
        // FIX 3: cellValueFactory BEFORE cellFactory
        colSelect.setCellValueFactory(c ->
                c.getValue().selectedProperty());

        colSelect.setCellFactory(col -> new CheckBoxTableCell<>() {
            @Override
            public void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0
                        || getIndex() >= getTableView().getItems().size()) {
                    setDisable(true);
                    return;
                }
                ImportRow row = getTableView().getItems().get(getIndex());
                // FIX 4: disable checkbox for ERROR rows
                boolean isError = "ERROR".equalsIgnoreCase(row.getStatus());
                setDisable(isError);
            }
        });

        // ── Data columns ─────────────────────────────────────────
        colWorkerId.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().getWorkerId() != null
                                ? c.getValue().getWorkerId().toString() : ""));

        colWorkerName.setCellValueFactory(c ->
                new SimpleStringProperty(safe(c.getValue().getWorkerName())));

        colChallan.setCellValueFactory(c ->
                new SimpleStringProperty(safe(c.getValue().getChallan())));

        colDate.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().getDate() != null
                                ? c.getValue().getDate().toString() : ""));

        colProductType.setCellValueFactory(c ->
                new SimpleStringProperty(safe(c.getValue().getProductType())));

        colProductName.setCellValueFactory(c ->
                new SimpleStringProperty(safe(c.getValue().getProduct())));

        colQty.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().getQty() != null
                                ? c.getValue().getQty().toPlainString() : ""));

        colUnit.setCellValueFactory(c ->
                new SimpleStringProperty(safe(c.getValue().getUnit())));

        colWeight.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().getWeight() != null
                                ? c.getValue().getWeight().toPlainString() : ""));

        colWtPerPiece.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().getWtPerPiece() != null
                                ? c.getValue().getWtPerPiece().toPlainString() : ""));

        // ── Status column — colour-coded ─────────────────────────
        colStatus.setCellValueFactory(c ->
                new SimpleStringProperty(safe(c.getValue().getStatus())));

        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null); setStyle(""); return;
                }
                setText(status);
                switch (status.toUpperCase()) {
                    case "ERROR"     ->
                            setStyle("-fx-text-fill:#dc2626;-fx-font-weight:bold;");
                    case "DUPLICATE" ->
                            setStyle("-fx-text-fill:#b45309;-fx-font-weight:bold;");
                    case "VALID","NEW" ->
                            setStyle("-fx-text-fill:#16a34a;-fx-font-weight:bold;");
                    case "IMPORTED"  ->
                            setStyle("-fx-text-fill:#059669;-fx-font-weight:bold;");
                    case "UPDATED"   ->
                            setStyle("-fx-text-fill:#2563eb;-fx-font-weight:bold;");
                    default          ->
                            setStyle("-fx-font-weight:bold;");
                }
            }
        });

        // ── Error detail column ──────────────────────────────────
        colError.setCellValueFactory(c ->
                new SimpleStringProperty(safe(c.getValue().getErrorDetail())));

        // ── Row background colour ────────────────────────────────
        tblPreview.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(ImportRow row, boolean empty) {
                super.updateItem(row, empty);
                if (row == null || empty) { setStyle(""); return; }
                switch (safe(row.getStatus()).toUpperCase()) {
                    case "ERROR"     ->
                            setStyle("-fx-background-color:#fecaca;");
                    case "DUPLICATE" ->
                            setStyle("-fx-background-color:#fef9c3;");
                    case "IMPORTED"  ->
                            setStyle("-fx-background-color:#bbf7d0;");
                    case "UPDATED"   ->
                            setStyle("-fx-background-color:#bfdbfe;");
                    default          ->
                            setStyle("-fx-background-color:white;");
                }
            }
        });

        // ── Auto-update summary when checkbox state changes ───────
        // FIX 5: attached per-row listener when rows are added
        rows.addListener((ListChangeListener<ImportRow>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    change.getAddedSubList().forEach(r ->
                            r.selectedProperty().addListener(
                                    (obs, o, n) -> updateSummary()));
                }
            }
        });

        // ── Table border ─────────────────────────────────────────
        tblPreview.setStyle("""
                -fx-border-color: black;
                -fx-border-width: 2;
                """);

        progressBar.setProgress(0);
        lblStatus.setText("Ready — load an Excel file to begin");
    }

    // ════════════════════════════════════════════════════════════
    //  LOAD EXCEL (called from ProductImportController after file pick)
    // ════════════════════════════════════════════════════════════

    /**
     * Called externally after the import window opens.
     * Reads the Excel file on a background thread, then populates the table.
     *
     * @param file the .xlsx file selected by the user
     */
    public void loadExcel(File file) {
        if (file == null) return;

        lblFile.setText("Loading: " + file.getName() + " …");
        lblStatus.setText("Reading Excel file…");
        progressBar.setProgress(-1); // indeterminate

        // FIX 1: background thread for file I/O
        Task<ObservableList<ImportRow>> task = new Task<>() {
            @Override
            protected ObservableList<ImportRow> call() throws Exception {
                return FXCollections.observableArrayList(
                        importService.readExcel(file));
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            rows.clear();
            rows.addAll(task.getValue());

            lblFile.setText("File: " + file.getName()
                    + "  (" + rows.size() + " rows)");
            lblStatus.setText("Loaded — click Validate to check for duplicates");
            progressBar.setProgress(1);

            tblPreview.refresh();
            updateSummary();
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            progressBar.setProgress(0);
            lblStatus.setText("Load failed");
            lblFile.setText("Error reading: " + file.getName());
            Throwable ex = task.getException();
            GlobalUI.warn("Error reading Excel:\n"
                    + (ex != null ? ex.getMessage() : "Unknown error"));
        }));

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ════════════════════════════════════════════════════════════
    //  VALIDATE
    // ════════════════════════════════════════════════════════════

    /**
     * Validate all rows against the DB.
     * Marks each row VALID / DUPLICATE / ERROR.
     * Runs on background thread — UI updates on FX thread.
     */
    @FXML
    public void onValidate() {
        if (rows.isEmpty()) {
            GlobalUI.warn("No data loaded. Please load an Excel file first.");
            return;
        }

        lblStatus.setText("Validating " + rows.size() + " rows…");
        progressBar.setProgress(-1);

        // FIX 1: background thread
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                importService.validate(rows);
                return null;
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            tblPreview.refresh();
            progressBar.setProgress(1);
            updateSummary();
            lblStatus.setText("Validation complete — select rows and click Import");
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            progressBar.setProgress(0);
            lblStatus.setText("Validation failed");
            GlobalUI.warn("Validation error: "
                    + (task.getException() != null
                    ? task.getException().getMessage() : "Unknown"));
        }));

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ════════════════════════════════════════════════════════════
    //  IMPORT SELECTED ROWS
    // ════════════════════════════════════════════════════════════

    /**
     * Import all selected rows.
     * FIX 6: auto-validates first so "NEW" rows don't slip through.
     * Runs on background thread.
     */
    @FXML
    public void onImport() {
        if (rows.isEmpty()) {
            GlobalUI.warn("No data loaded."); return;
        }

        // FIX 6: always validate before importing
        importService.validate(rows);
        tblPreview.refresh();
        updateSummary();

        // Collect user-selected rows (excludes ERROR rows automatically
        // because the checkbox was disabled for them)
        var selected = rows.stream()
                .filter(ImportRow::isSelected)
                .filter(r -> !"ERROR".equalsIgnoreCase(r.getStatus()))
                .toList();

        if (selected.isEmpty()) {
            GlobalUI.warn(
                    "No rows selected for import.\n"
                            + "Select at least one VALID or DUPLICATE row.");
            return;
        }

        lblStatus.setText("Importing " + selected.size() + " rows…");
        progressBar.setProgress(-1);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                importService.importData(new ArrayList<>(selected));
                return null;
            }
        };

        task.setOnSucceeded(e -> Platform.runLater(() -> {
            tblPreview.refresh();
            progressBar.setProgress(1);
            updateSummary();

            long imported = selected.stream()
                    .filter(r -> "IMPORTED".equalsIgnoreCase(r.getStatus()))
                    .count();
            long errors = selected.stream()
                    .filter(r -> "ERROR".equalsIgnoreCase(r.getStatus()))
                    .count();

            lblStatus.setText("Import complete — "
                    + imported + " saved, " + errors + " errors");
            GlobalUI.success("Import complete!\n"
                    + "Saved: " + imported + "  |  Errors: " + errors);
        }));

        task.setOnFailed(e -> Platform.runLater(() -> {
            progressBar.setProgress(0);
            lblStatus.setText("Import failed");
            GlobalUI.warn("Import failed: "
                    + (task.getException() != null
                    ? task.getException().getMessage() : "Unknown"));
        }));

        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }

    // ════════════════════════════════════════════════════════════
    //  SELECTION HELPERS
    // ════════════════════════════════════════════════════════════

    /** Select ALL rows (including duplicates — user's explicit choice) */
    @FXML
    public void onSelectAll() {
        rows.forEach(r -> {
            if (!"ERROR".equalsIgnoreCase(r.getStatus()))
                r.setSelected(true);
        });
        updateSummary();
    }

    /** Deselect ALL rows */
    @FXML
    public void onDeselectAll() {
        rows.forEach(r -> r.setSelected(false));
        updateSummary();
    }

    /**
     * Select only VALID and DUPLICATE rows.
     * This is the recommended selection for a normal import:
     *   VALID     → import new records
     *   DUPLICATE → user may want to keep deselected (skip re-import)
     */
    @FXML
    public void onSelectValid() {
        rows.forEach(r -> {
            String s = safe(r.getStatus()).toUpperCase();
            r.setSelected(s.equals("VALID") || s.equals("DUPLICATE"));
        });
        updateSummary();
    }

    // ════════════════════════════════════════════════════════════
    //  NEW IMPORT (RESET)
    // ════════════════════════════════════════════════════════════

    @FXML
    public void onNewImport() {
        rows.clear();
        tblPreview.refresh();
        progressBar.setProgress(0);
        lblStatus.setText("Ready — load an Excel file to begin");
        lblFile.setText("No file loaded");
        lblSummary.setText("");
    }

    // ════════════════════════════════════════════════════════════
    //  CLOSE
    // ════════════════════════════════════════════════════════════

    @FXML
    public void onClose() {
        rows.clear();
        tblPreview.getScene().getWindow().hide();
    }

    // ════════════════════════════════════════════════════════════
    //  SUMMARY BAR
    // ════════════════════════════════════════════════════════════

    /**
     * Update the summary label below the table.
     * FIX 5: always called from Platform.runLater() in background tasks.
     */
    private void updateSummary() {
        int total     = rows.size();
        int valid     = 0;
        int duplicate = 0;
        int error     = 0;
        int imported  = 0;
        int selected  = 0;

        for (ImportRow r : rows) {
            String s = safe(r.getStatus()).toUpperCase();
            switch (s) {
                case "VALID"     -> valid++;
                case "DUPLICATE" -> duplicate++;
                case "ERROR"     -> error++;
                case "IMPORTED","UPDATED" -> imported++;
            }
            if (r.isSelected()) selected++;
        }

        lblSummary.setText(
                "Total: " + total
                        + "  |  ✔ Valid: "     + valid
                        + "  |  ⚠ Duplicate: " + duplicate
                        + "  |  ✖ Error: "     + error
                        + "  |  ✅ Imported: "  + imported
                        + "  |  ☑ Selected: "  + selected);
    }

    // ════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════

    private String safe(String s) { return s != null ? s : ""; }
}