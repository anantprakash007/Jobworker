package com.jobwork.controller;

import com.jobwork.domain.*;
import com.jobwork.service.JobWorkerService;
import com.jobwork.service.MoneyImportService;
import com.jobwork.util.GlobalUI;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * MoneyImportPreviewController  (MoneyReceipt)
 * ─────────────────────────────────────────────────────────────────
 * Opened by MoneyEntryController.onImportExcel().
 *
 * KEY: All duplicate checks use workerId (Long) — NOT worker name.
 * Column A accepts worker ID (integer) or worker name (fallback).
 *
 * Expected Excel columns (Row 1 = header, data from Row 2):
 *   A  Worker ID          (Long preferred; name accepted as fallback)
 *   B  Challan No
 *   C  Date               (dd/MM/yyyy)
 *   D  Transaction Type   (ADVANCE / PAYMENT / DEDUCTION)
 *   E  Amount
 *   F  Remark             (optional)
 *
 * Duplicate key: (workerId, challanNo, entryDate, transactionType)
 */
@Slf4j
@Component
public class MoneyImportPreviewController implements Initializable {

    // ── FXML ─────────────────────────────────────────────────────
    @FXML private Label lblFileName;
    @FXML private Label lblSummary;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblProgress;
    @FXML private TableView<MoneyImportRow>              tblPreview;
    @FXML private TableColumn<MoneyImportRow, Boolean>   colSelect;
    @FXML private TableColumn<MoneyImportRow, String>    colRow;
    @FXML private TableColumn<MoneyImportRow, String> colWorkerId;
    @FXML private TableColumn<MoneyImportRow, String> colWorkerName;
    @FXML private TableColumn<MoneyImportRow, String>    colChallan;
    @FXML private TableColumn<MoneyImportRow, String>    colDate;
    @FXML private TableColumn<MoneyImportRow, String>    colTxType;
    @FXML private TableColumn<MoneyImportRow, String>    colAmount;
    @FXML private TableColumn<MoneyImportRow, String>    colRemark;
    @FXML private TableColumn<MoneyImportRow, String>    colStatus;
    @FXML private TableView<MoneyDuplicateReviewRow> tblDup;
    @FXML private Button btnSelectAll;
    @FXML private Button btnSelectValid;
    @FXML private Button btnDeselectAll;
    @FXML private Button btnImport;
    @FXML private Button btnClose;

    // ── Spring ────────────────────────────────────────────────────
    @Autowired private MoneyImportService moneyImportService;
    @Autowired private JobWorkerService   workerService;

    // ── State ─────────────────────────────────────────────────────
    private final ObservableList<MoneyImportRow> rows = FXCollections.observableArrayList();

    private static final Set<String> VALID_TX_TYPES =
            new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    static {
        VALID_TX_TYPES.addAll(Arrays.asList(
                "CASH","NEFT","UPI","CHEQUE"
        ));
    }

    private Map<String, JobWorker> workerByName;
    private Map<Long,   JobWorker> workerById;

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
    };

    // ── Init ─────────────────────────────────────────────────────
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTable();
        loadWorkerMaps();
        tblPreview.setItems(rows);
    }

    public void loadExcel(File file) {
        lblFileName.setText("File: " + file.getName());
        rows.clear();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(0);
            int loaded = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row er = sheet.getRow(i);
                if (er == null || isRowBlank(er)) continue;
                MoneyImportRow r = parseRow(er, i + 1);
                validateRow(r);
                checkDuplicateInDb(r);
                rows.add(r);
                loaded++;
            }
            updateSummary();
            log.info("MoneyImport → {} rows from {}", loaded, file.getName());
        } catch (Exception ex) {
            log.error("MoneyImport → file read failed", ex);
            GlobalUI.warn("Failed to read Excel file:\n" + ex.getMessage());
        }
    }

    // ── Table setup ───────────────────────────────────────────────
    private void setupTable() {
        tblPreview.setEditable(true);
        tblPreview.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        colSelect.setCellValueFactory(c -> c.getValue().selectedProperty());
        colSelect.setCellFactory(CheckBoxTableCell.forTableColumn(colSelect));
        colSelect.setEditable(true);
        colSelect.setPrefWidth(62);

        colRow.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getRowNumber())));
        colRow.setPrefWidth(45);
        colRow.setStyle("-fx-alignment:CENTER;");

        // Worker ID
        colWorkerId.setCellValueFactory(c -> {
            MoneyImportRow r = c.getValue();
            if (r.getJobWorker() != null) {
                return new SimpleStringProperty(String.valueOf(r.getJobWorker().getId()));
            }
            return new SimpleStringProperty(s(r.getWorkerIdRaw()));
        });
        colWorkerId.setStyle("-fx-alignment:CENTER;");

// Worker Name
        colWorkerName.setCellValueFactory(c -> {
            MoneyImportRow r = c.getValue();
            if (r.getJobWorker() != null) {
                return new SimpleStringProperty(r.getJobWorker().getName());
            }
            return new SimpleStringProperty(s(r.getWorkerName()));
        });

        colChallan.setCellValueFactory(c -> sv(c.getValue().getChallanNo()));
        colDate.setCellValueFactory(c    -> new SimpleStringProperty(
                c.getValue().getEntryDate() != null
                        ? c.getValue().getEntryDate().toString()
                        : s(c.getValue().getDateRaw())));
        colTxType.setCellValueFactory(c  -> sv(c.getValue().getTransferModeRaw()));
        colTxType.setStyle("-fx-alignment:CENTER;-fx-font-weight:bold;");
        colAmount.setCellValueFactory(c  -> sv(c.getValue().getAmountRaw()));
        colAmount.setStyle("-fx-alignment:CENTER-RIGHT;");
        colRemark.setCellValueFactory(c  -> sv(c.getValue().getRemark()));

        // Status
        colStatus.setCellValueFactory(c -> {
            MoneyImportRow r = c.getValue();
            if (r.isDuplicate()) return new SimpleStringProperty("⚠ " + r.getValidationError());
            if (r.isInvalid())   return new SimpleStringProperty("❌ " + r.getValidationError());
            return new SimpleStringProperty("✔ Ready");
        });
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); setStyle(""); return; }
                setText(v);
                setStyle(v.startsWith("❌") ? "-fx-text-fill:#b91c1c;-fx-font-weight:bold;"
                        : v.startsWith("⚠") ? "-fx-text-fill:#d97706;-fx-font-weight:bold;"
                        :                     "-fx-text-fill:#16a34a;-fx-font-weight:bold;");
            }
        });

        tblPreview.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(MoneyImportRow r, boolean empty) {
                super.updateItem(r, empty);
                if (empty || r == null) { setStyle(""); return; }
                setStyle(r.isInvalid()   ? "-fx-background-color:#fef2f2;"
                        : r.isDuplicate() ? "-fx-background-color:#fffbeb;"
                        :                  "");
            }
        });
    }

    // ── Worker maps ───────────────────────────────────────────────
    private void loadWorkerMaps() {
        workerByName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        workerById   = new HashMap<>();
        workerService.findAll().forEach(w -> {
            workerByName.put(w.getName(), w);
            workerById.put(w.getId(), w);
        });
        log.debug("MoneyImport worker map → {} workers", workerById.size());
    }

    // ── Parse row ─────────────────────────────────────────────────
    private MoneyImportRow parseRow(Row er, int displayRow) {

        MoneyImportRow r = new MoneyImportRow(displayRow);

        // ✅ CORRECT MAPPING (MATCH EXCEL)
        r.setWorkerIdRaw(cell(er, 0));        // A: Worker ID
        r.setChallanNo(cell(er, 1));          // B: Challan
        r.setDateRaw(cell(er, 2));            // C: Date
        r.setTransferModeRaw(cell(er, 3));    // D: Tx Type
        r.setAmountRaw(cell(er, 4));          // E: Amount
        r.setRemark(cell(er, 5));             // F: Remark

        // 🔥 IMPORTANT: workerName should NOT come from Excel
        r.setWorkerName(r.getWorkerIdRaw());  // use ID first

        return r;
    }
    // ── Validate + resolve ────────────────────────────────────────
    private void validateRow(MoneyImportRow r) {
        if (blank(r.getWorkerIdRaw()))          { r.setValidationError("Worker ID required");           return; }
        if (blank(r.getChallanNo()))            { r.setValidationError("Challan No. required");        return; }
        if (blank(r.getDateRaw()))              { r.setValidationError("Date required");               return; }
        if (blank(r.getTransferModeRaw()))   { r.setValidationError("Transaction Type required");   return; }
        if (blank(r.getAmountRaw()))            { r.setValidationError("Amount required");             return; }

        // Worker — ID first, name fallback
        // 🔥 Worker resolve (ID → Name fallback)

        String raw = r.getWorkerIdRaw();  // Column A

        if (raw != null && !raw.isBlank()) {

            raw = raw.trim();

            // Try ID first
            try {
                Long workerId = Long.parseLong(raw);

                JobWorker worker = workerById.get(workerId);

                if (worker == null) {
                    r.setValidationError("Invalid Worker ID: " + raw);
                    return;
                }

                r.setJobWorker(worker);
                r.setWorkerName(worker.getName()); // ✅ auto-fill name

            } catch (NumberFormatException ex) {

                // fallback → name
                JobWorker worker = workerByName.get(raw);

                if (worker == null) {
                    r.setValidationError("Worker not found: " + raw);
                    return;
                }

                r.setJobWorker(worker);
            }

        } else {
            r.setValidationError("Worker ID/Name required");
            return;
        }
        // Date
        LocalDate date = parseDate(r.getDateRaw().trim());
        if (date == null) { r.setValidationError("Invalid date: \"" + r.getDateRaw() + "\""); return; }
        r.setEntryDate(date);

        // Transaction Type
        String tx = r.getTransferModeRaw().trim().toUpperCase();
        if (!VALID_TX_TYPES.contains(tx)) {
            r.setValidationError( "Transaction Type must be CASH, NEFT, UPI, or CHEQUE " +
                    "(got \"" + r.getTransferModeRaw() + "\")");
            return;
        }
        r.setTransferMode(tx);

        // Amount
        try {
            BigDecimal amt = new BigDecimal(r.getAmountRaw().trim());
            if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                r.setValidationError("Amount must be > 0"); return;
            }
            r.setAmount(amt);
        } catch (NumberFormatException ex) {
            r.setValidationError("Invalid amount: \"" + r.getAmountRaw() + "\"");
        }
    }

    private JobWorker resolveWorker(String raw) {
        try { return workerById.get(Long.parseLong(raw)); }
        catch (NumberFormatException ex) { return workerByName.get(raw); }
    }

    // ── DB duplicate check (uses workerId) ────────────────────────
    private void checkDuplicateInDb(MoneyImportRow r) {

        if (r.isInvalid() || r.isDuplicate()) return;
        if (r.getJobWorker() == null || r.getTransferMode() == null) return;

        TransferMode mode = parseMode(r.getTransferMode());

        if (mode == null) {
            r.setValidationError("Invalid Transaction Type");
            return;
        }

        boolean dup = moneyImportService.isDuplicate(
                r.getJobWorker().getId(),
                r.getChallanNo(),
                r.getEntryDate(),
                mode
        );

        if (dup) {
            r.setValidationError("DUPLICATE: already exists in DB (will OVERWRITE if selected)");

            log.info("MoneyImport → dup row {}: workerId={} challanNo={} txType={}",
                    r.getRowNumber(),
                    r.getJobWorker().getId(),
                    r.getChallanNo(),
                    r.getTransferMode());
        }
    }

    private TransferMode parseMode(String mode) {
        try {
            return mode == null ? null :
                    TransferMode.valueOf(mode.trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    // ── Buttons ───────────────────────────────────────────────────
    @FXML private void onSelectAll()   { rows.forEach(r -> r.setSelected(true));  tblPreview.refresh(); }
    @FXML private void onDeselectAll() { rows.forEach(r -> r.setSelected(false)); tblPreview.refresh(); }
    @FXML private void onSelectValid() {
        rows.forEach(r -> r.setSelected(r.isValid() || r.isDuplicate()));
        tblPreview.refresh();
    }

    @FXML
    private void onImport() {

        List<MoneyImportRow> selected = rows.stream()
                .filter(MoneyImportRow::isSelected)
                .toList();

        if (selected.isEmpty()) {
            GlobalUI.warn("No rows selected.");
            return;
        }

        Task<Void> task = new Task<>() {

            @Override
            protected Void call() {

                int total = selected.size();
                int count = 0;

                int saved = 0;
                int updated = 0;

                for (MoneyImportRow r : selected) {

                    if (r.isInvalid()) {
                        count++;
                        updateProgress(count, total);
                        continue;
                    }

                    try {

                        if (r.isDuplicate()) {
                            moneyImportService.overwriteDuplicate(r);
                            updated++;
                        } else {
                            moneyImportService.saveFromImport(r);
                            saved++;
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    count++;
                    updateProgress(count, total);
                }

                int finalSaved = saved;
                int finalUpdated = updated;

                Platform.runLater(() -> {
                    GlobalUI.success("Saved: " + finalSaved + " | Updated: " + finalUpdated);
                    onClose();
                });

                return null; // ✅ VERY IMPORTANT
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());

        new Thread(task).start();
    }
    private void openDuplicatePopup(List<MoneyDuplicateReviewRow> rows, Runnable onDone) {

        GlobalUI.openWindow(
                "/fxml/money_import_review.fxml",
                "Duplicate Money",
                controller -> {

                    MoneyDuplicateReviewController c =
                            (MoneyDuplicateReviewController) controller;

                    c.init(rows, "Duplicate Found", () -> {

                        GlobalUI.success("Overwrite completed");

                        if (onDone != null) onDone.run();
                    });
                }
        );
    }

    @FXML private void onClose() { ((Stage) btnClose.getScene().getWindow()).close(); }

    // ── Helpers ───────────────────────────────────────────────────
    private void updateSummary() {
        long valid   = rows.stream().filter(MoneyImportRow::isValid).count();
        long dup     = rows.stream().filter(MoneyImportRow::isDuplicate).count();
        long invalid = rows.stream().filter(MoneyImportRow::isInvalid).count();
        lblSummary.setText("Total: " + rows.size()
                + "   ✔ Valid: " + valid + "   ⚠ Duplicate: " + dup + "   ❌ Error: " + invalid);
    }

    private String cell(Row row, int col) {
        Cell c = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (c == null) return "";
        return switch (c.getCellType()) {
            case STRING  -> c.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(c))
                    yield c.getLocalDateTimeCellValue()
                            .toLocalDate()
                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                double d = c.getNumericCellValue();
                yield d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            case FORMULA -> c.getCachedFormulaResultType() == CellType.NUMERIC
                    ? String.valueOf((long) c.getNumericCellValue())
                    : c.getStringCellValue().trim();
            default -> "";
        };
    }

    private boolean isRowBlank(Row row) {
        for (int i = 0; i < 6; i++) if (!cell(row, i).isBlank()) return false;
        return true;
    }

    private LocalDate parseDate(String raw) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(raw, fmt); } catch (Exception ignored) {}
        }
        return null;
    }

    private boolean blank(String s)            { return s == null || s.isBlank(); }
    private String  s(String v)                { return v != null ? v : ""; }
    private SimpleStringProperty sv(String v)  { return new SimpleStringProperty(s(v)); }

}