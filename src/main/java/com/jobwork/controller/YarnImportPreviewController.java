package com.jobwork.controller;

import com.jobwork.domain.*;
import com.jobwork.service.JobWorkerService;
import com.jobwork.service.MasterService;
import com.jobwork.service.YarnImportService;
import com.jobwork.service.YarnService;
import com.jobwork.util.GlobalUI;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import javafx.scene.Parent;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * YarnImportPreviewController
 * ─────────────────────────────────────────────────────────────────
 * Opened by YarnEntryController.onImportExcel().
 *
 * KEY: Uses workerId (Long) for duplicate checks — NOT worker name.
 * Column A accepts worker ID (integer) or worker name (fallback).
 *
 * Expected Excel columns (Row 1 = header, data from Row 2):
 *   A  Worker ID    (integer preferred, name accepted as fallback)
 *   B  Location Name
 *   C  Challan No
 *   D  Date         (dd/MM/yyyy)
 *   E  Bag/Piece    (BAG or PIECE)
 *   F  Wt of Bags   (50 kg/60 kg/70 kg/75 kg/90 kg — blank for PIECE)
 *   G  Yarn Count   (YarnType name in master)
 *   H  Colour
 *   I  No. of Bags  (blank/0 for PIECE)
 *   J  No. of Cones (blank/0 for BAG)
 *   K  Net Weight   (kg)
 */
@Slf4j
@Component
public class YarnImportPreviewController implements Initializable {

    // ── FXML ─────────────────────────────────────────────────────
    @FXML private Label lblFileName;
    @FXML private Label lblSummary;
    @FXML private ProgressBar progressBar;

    @FXML private TableView<YarnImportRow>              tblPreview;
    @FXML private TableColumn<YarnImportRow, Boolean>   colSelect;
    @FXML private TableColumn<YarnImportRow, String>    colRow;
    @FXML private TableColumn<YarnImportRow, String>    colWorker;
    @FXML private TableColumn<YarnImportRow, String>    colLocation;
    @FXML private TableColumn<YarnImportRow, String>    colChallan;
    @FXML private TableColumn<YarnImportRow, String>    colDate;
    @FXML private TableColumn<YarnImportRow, String>    colBagPiece;
    @FXML private TableColumn<YarnImportRow, String>    colWtOfBags;
    @FXML private TableColumn<YarnImportRow, String>    colYarnCount;
    @FXML private TableColumn<YarnImportRow, String>    colColour;
    @FXML private TableColumn<YarnImportRow, String>    colBags;
    @FXML private TableColumn<YarnImportRow, String>    colCones;
    @FXML private TableColumn<YarnImportRow, String>    colNetWeight;
    @FXML private TableColumn<YarnImportRow, String>    colStatus;

    @FXML private Button btnSelectAll;
    @FXML private Button btnSelectValid;
    @FXML private Button btnDeselectAll;
    @FXML private Button btnImport;
    @FXML private Button btnClose;

    // ── Spring ────────────────────────────────────────────────────
    @Autowired private YarnImportService yarnImportService;
    @Autowired private JobWorkerService  workerService;
    @Autowired private MasterService     masterService;
    @Autowired private YarnService yarnService;
    @Autowired private ApplicationContext appContext;

    // ── State ─────────────────────────────────────────────────────
    private final ObservableList<YarnImportRow> rows = FXCollections.observableArrayList();

    private static final Set<String> VALID_BAG_WEIGHTS =
            new LinkedHashSet<>(Arrays.asList("50 kg","60 kg","70 kg","75 kg","90 kg"));

    private Map<String, JobWorker>        workerByName;
    private Map<Long,   JobWorker>        workerById;
    private Map<String, DeliveryLocation> locationMap;
    private Map<String, YarnType>         yarnTypeMap;

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
        loadMasterMaps();
        tblPreview.setItems(rows);
    }

    public void loadExcel(File file) {
        lblFileName.setText("File: " + file.getName());
        rows.clear();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(0);
            int loaded  = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row er = sheet.getRow(i);
                if (er == null || isRowBlank(er)) continue;
                YarnImportRow r = parseRow(er, i + 1);
                validateRow(r);
                checkDuplicateInDb(r);
                rows.add(r);
                loaded++;
            }
            updateSummary();
            log.info("YarnImport → {} rows from {}", loaded, file.getName());
        } catch (Exception ex) {
            log.error("YarnImport → file read failed", ex);
            GlobalUI.warn("Failed to read Excel file:\n" + ex.getMessage());
        }
        onValidate();
    }
    private void updateProgress() {

        if (rows.isEmpty()) {
            progressBar.setProgress(0);
            return;
        }

        long valid = rows.stream().filter(YarnImportRow::isValid).count();
        long dup   = rows.stream().filter(YarnImportRow::isDuplicate).count();
        long error = rows.stream().filter(YarnImportRow::isInvalid).count();

        // ✔ Treat duplicate as “processable”
        long processed = valid + dup;

        double progress = (double) processed / rows.size();

        progressBar.setProgress(progress);

        // 🔥 Optional: dynamic color feedback
        if (error > 0) {
            progressBar.setStyle("-fx-accent:#ef4444;"); // red
        } else if (dup > 0) {
            progressBar.setStyle("-fx-accent:#f59e0b;"); // amber
        } else {
            progressBar.setStyle("-fx-accent:#10b981;"); // green
        }
    }
    @FXML
    public void onValidate() {

        for (YarnImportRow r : rows) {
            validateRow(r);
            checkDuplicateInDb(r);
        }

        updateSummary();
        updateProgress();
        tblPreview.refresh();
    }
    private void validateRowLive(YarnImportRow r) {
        validateRow(r);
        checkDuplicateInDb(r);
        updateSummary();
        updateProgress();
    }
    // ── Table setup ───────────────────────────────────────────────
    private void setupTable() {
        tblPreview.setEditable(true);
        tblPreview.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        colSelect.setCellValueFactory(c -> c.getValue().selectedProperty());
        colSelect.setCellFactory(CheckBoxTableCell.forTableColumn(colSelect));
        colSelect.setEditable(true);
        colSelect.setPrefWidth(60);

        colRow.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(c.getValue().getRowNumber())));
        colRow.setPrefWidth(45);
        colRow.setStyle("-fx-alignment:CENTER;");

        // Worker column: "ID (Name)"
        colWorker.setCellValueFactory(c -> {
            YarnImportRow r = c.getValue();
            if (r.getJobWorker() != null)
                return new SimpleStringProperty(
                        r.getJobWorker().getId() + " (" + r.getJobWorker().getName() + ")");
            return new SimpleStringProperty(s(r.getWorkerName()));
        });

        colLocation.setCellValueFactory(c  -> sv(c.getValue().getLocationName()));
        colChallan.setCellValueFactory(c   -> sv(c.getValue().getChallanNo()));
        colDate.setCellValueFactory(c      -> new SimpleStringProperty(
                c.getValue().getEntryDate() != null
                        ? c.getValue().getEntryDate().toString()
                        : s(c.getValue().getDateRaw())));
        colBagPiece.setCellValueFactory(c  -> sv(c.getValue().getBagPieceRaw()));
        colBagPiece.setStyle("-fx-alignment:CENTER;");
        colWtOfBags.setCellValueFactory(c  -> sv(c.getValue().getWtOfBagsRaw()));
        colYarnCount.setCellValueFactory(c -> sv(c.getValue().getYarnCountRaw()));
        colColour.setCellValueFactory(c    -> sv(c.getValue().getColour()));
        colBags.setCellValueFactory(c      -> new SimpleStringProperty(
                "BAG".equalsIgnoreCase(c.getValue().getBagPieceRaw())
                        ? s(c.getValue().getNoOfBagsRaw()) : "—"));
        colBags.setStyle("-fx-alignment:CENTER-RIGHT;");
        colCones.setCellValueFactory(c     -> new SimpleStringProperty(
                "PIECE".equalsIgnoreCase(c.getValue().getBagPieceRaw())
                        ? s(c.getValue().getNoOfConesRaw()) : "—"));
        colCones.setStyle("-fx-alignment:CENTER-RIGHT;");
        colNetWeight.setCellValueFactory(c -> sv(c.getValue().getNetWeightRaw()));
        colNetWeight.setStyle("-fx-alignment:CENTER-RIGHT;");

        colStatus.setCellValueFactory(c -> {
            YarnImportRow r = c.getValue();
            if (r.isImported())  return new SimpleStringProperty("✔ Imported");
            if (r.isInvalid())   return new SimpleStringProperty("❌ " + r.getValidationError());
            if (r.isDuplicate()) return new SimpleStringProperty("⚠ Duplicate");
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
            @Override protected void updateItem(YarnImportRow r, boolean empty) {
                super.updateItem(r, empty);
                if (empty || r == null) { setStyle(""); return; }
                setStyle(r.isImported()  ? "-fx-background-color:#ecfdf5;"   // 🟢 green
                        : r.isInvalid()  ? "-fx-background-color:#fef2f2;"   // 🔴 red
                        : r.isDuplicate()? "-fx-background-color:#fffbeb;"   // 🟡 amber
                        :                 "");
            }
        });
    }

    // ── Master maps ───────────────────────────────────────────────
    private void loadMasterMaps() {
        workerByName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        workerById   = new HashMap<>();
        locationMap  = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        yarnTypeMap  = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        workerService.findAll().forEach(w -> {
            workerByName.put(w.getName(), w);
            workerById.put(w.getId(), w);
        });
        masterService.findAllLocations().forEach(l -> locationMap.put(l.getName(), l));
        masterService.findAllYarnTypes().forEach(y -> yarnTypeMap.put(y.getName(), y));

        log.debug("YarnImport maps → workers={} locations={} yarnTypes={}",
                workerById.size(), locationMap.size(), yarnTypeMap.size());
    }
    private YarnEntry mapToEntity(YarnImportRow r) {

        return YarnEntry.builder()
                .jobWorker(r.getJobWorker())
                .deliveryLocation(r.getDeliveryLocation())
                .challanNo(r.getChallanNo())
                .entryDate(r.getEntryDate())
                .yarnType(r.getYarnType())
                .colour(r.getColour())
                .bagPiece(r.getBagPiece())
                .wtOfBags(r.getWtOfBags())
                .noOfBags(r.getNoOfBags())
                .noOfCones(r.getNoOfCones())
                .netWeight(r.getNetWeight())
                .status("SUBMITTED")
                .build();
    }
    // ── Parse row ─────────────────────────────────────────────────
    private YarnImportRow parseRow(Row er, int displayRow) {
        YarnImportRow r = new YarnImportRow(displayRow);
        r.setWorkerName(   cell(er, 0));
        r.setLocationName( cell(er, 1));
        r.setChallanNo(    cell(er, 2));
        r.setDateRaw(      cell(er, 3));
        r.setBagPieceRaw(  cell(er, 4));
        r.setWtOfBagsRaw(  cell(er, 5));
        r.setYarnCountRaw( cell(er, 6));
        r.setColour(       cell(er, 7));
        r.setNoOfBagsRaw(  cell(er, 8));
        r.setNoOfConesRaw( cell(er, 9));
        r.setNetWeightRaw( cell(er, 10));
        return r;
    }

    // ── Validate + resolve ────────────────────────────────────────
    private void validateRow(YarnImportRow r) {
        if (r.isImported()) return;
        if (blank(r.getChallanNo()))    { r.setValidationError("Challan No. required");      return; }
        if (blank(r.getWorkerName()))   { r.setValidationError("Worker ID required");        return; }
        if (blank(r.getLocationName())) { r.setValidationError("Location required");         return; }
        if (blank(r.getDateRaw()))      { r.setValidationError("Date required");             return; }
        if (blank(r.getBagPieceRaw()))  { r.setValidationError("Bag/Piece required");        return; }
        if (blank(r.getYarnCountRaw())) { r.setValidationError("Yarn Count required");       return; }
        if (blank(r.getColour()))       { r.setValidationError("Colour required");           return; }
        if (blank(r.getNetWeightRaw())) { r.setValidationError("Net Weight required");       return; }

        // Worker — try ID first, then name
        JobWorker worker = resolveWorker(r.getWorkerName().trim());
        if (worker == null) { r.setValidationError("Worker not found: \"" + r.getWorkerName() + "\""); return; }
        r.setJobWorker(worker);

        // Location
        DeliveryLocation loc = locationMap.get(r.getLocationName().trim());
        if (loc == null) { r.setValidationError("Location not found: \"" + r.getLocationName() + "\""); return; }
        r.setDeliveryLocation(loc);

        // Date
        LocalDate date = parseDate(r.getDateRaw().trim());
        if (date == null) { r.setValidationError("Invalid date: \"" + r.getDateRaw() + "\""); return; }
        r.setEntryDate(date);

        // Bag/Piece
        String bp = r.getBagPieceRaw().trim().toUpperCase();
        if (!bp.equals("BAG") && !bp.equals("PIECE")) {
            r.setValidationError("Bag/Piece must be BAG or PIECE"); return;
        }
        r.setBagPiece(bp);
        boolean isBag = bp.equals("BAG");

        // Wt of Bags
        if (isBag) {
            String wt = blank(r.getWtOfBagsRaw()) ? "" : r.getWtOfBagsRaw().trim();
            if (wt.isEmpty()) { r.setValidationError("Wt of Bags required for BAG rows"); return; }
            String canonical = normalizeWtOfBags(wt);
            if (canonical == null) { r.setValidationError("Invalid Wt of Bags: \"" + wt + "\""); return; }
            r.setWtOfBags(canonical);
        } else { r.setWtOfBags(null); }

        // Yarn Type
        YarnType yt = yarnTypeMap.get(r.getYarnCountRaw().trim());
        if (yt == null) { r.setValidationError("Yarn Type not found: \"" + r.getYarnCountRaw() + "\""); return; }
        r.setYarnType(yt);

        // Bags / Cones
        if (isBag) {
            if (blank(r.getNoOfBagsRaw())) { r.setValidationError("No. of Bags required for BAG rows"); return; }
            int bags = parseInt(r.getNoOfBagsRaw());
            if (bags <= 0) { r.setValidationError("No. of Bags must be > 0"); return; }
            r.setNoOfBags(bags); r.setNoOfCones(0);
        } else {
            if (blank(r.getNoOfConesRaw())) { r.setValidationError("No. of Cones required for PIECE rows"); return; }
            int cones = parseInt(r.getNoOfConesRaw());
            if (cones <= 0) { r.setValidationError("No. of Cones must be > 0"); return; }
            r.setNoOfCones(cones); r.setNoOfBags(0);
        }

        // Net Weight
        try {
            BigDecimal wt = new BigDecimal(r.getNetWeightRaw().trim());
            if (wt.compareTo(BigDecimal.ZERO) <= 0) { r.setValidationError("Net Weight must be > 0"); return; }
            r.setNetWeight(wt);
        } catch (NumberFormatException ex) {
            r.setValidationError("Invalid Net Weight: \"" + r.getNetWeightRaw() + "\"");
        }
    }

    private JobWorker resolveWorker(String raw) {
        try { return workerById.get(Long.parseLong(raw)); }
        catch (NumberFormatException ex) { return workerByName.get(raw); }
    }

    // ── DB duplicate check (uses workerId) ────────────────────────
    private void checkDuplicateInDb(YarnImportRow r) {
        if (r.isInvalid() || r.isImported()) return;
        if (r.getJobWorker() == null || r.getYarnType() == null || r.getBagPiece() == null) return;

        boolean dup = yarnImportService.isDuplicate(
                r.getJobWorker().getId(),   // ← workerId
                r.getChallanNo(),
                r.getEntryDate(),
                r.getYarnType().getId(),
                r.getBagPiece());

        if (dup) {
            r.setValidationError("DUPLICATE: already exists in DB (will OVERWRITE if selected)");
            log.info("YarnImport → dup row {}: workerId={} challanNo={} yarn={} bag={}",
                    r.getRowNumber(), r.getJobWorker().getId(),
                    r.getChallanNo(), r.getYarnCountRaw(), r.getBagPiece());
        }
    }

    // ── Buttons ───────────────────────────────────────────────────
    @FXML private void onSelectAll()   { rows.forEach(r -> r.setSelected(true));  tblPreview.refresh(); }
    @FXML private void onDeselectAll() { rows.forEach(r -> r.setSelected(false)); tblPreview.refresh(); }
    @FXML private void onSelectValid() {
        rows.forEach(r ->
                r.setSelected(!r.isImported() && (r.isValid() || r.isDuplicate()))
        );
        tblPreview.refresh();
    }

    /**  @FXML private void onImport() {
    long sel = rows.stream().filter(YarnImportRow::isSelected).count();
    if (sel == 0) { GlobalUI.warn("No rows selected."); return; }
    if (!GlobalUI.confirm("Confirm Import",
    "Import " + sel + " row(s)?\nDuplicates will OVERWRITE existing data.")) return;

    int saved = 0, overwritten = 0, skipped = 0;
    for (YarnImportRow r : rows) {
    if (!r.isSelected()) continue;
    if (r.isInvalid()) { skipped++; continue; }
    if (r.isDuplicate()) {
    if (yarnImportService.overwriteDuplicate(r)) overwritten++;
    else skipped++;
    } else {
    yarnImportService.saveFromImport(r);
    saved++;
    }
    }

    StringBuilder msg = new StringBuilder();
    if (saved       > 0) msg.append(saved).append(" new row(s) imported.\n");
    if (overwritten > 0) msg.append(overwritten).append(" duplicate row(s) overwritten.\n");
    if (skipped     > 0) msg.append(skipped).append(" row(s) skipped.");
    GlobalUI.success(msg.toString().trim());
    log.info("YarnImport done → saved={} overwritten={} skipped={}", saved, overwritten, skipped);
    ((Stage) btnClose.getScene().getWindow()).close();
    }*/
    @FXML
    private void onImport() {

        long selected = rows.stream()
                .filter(r -> r.isSelected() && !r.isInvalid())
                .count();

        if (selected == 0) {
            GlobalUI.warn("No valid rows selected");
            return;
        }

        if (!GlobalUI.confirm("Confirm Import",
                "Import " + selected + " row(s)?")) return;

        lblSummary.setText("⏳ Import in progress...");
        progressBar.setProgress(0);

        List<YarnEntry> entries = new ArrayList<>();

        for (YarnImportRow r : rows) {
            if (!r.isSelected() || r.isInvalid()) continue;
            entries.add(mapToEntity(r));
        }

        YarnDuplicateCheckResult result =
                yarnService.checkDuplicates(entries);

        if (result.hasDuplicates()) {

            openDuplicatePopup(result.rows(), () -> {

                List<YarnEntry> clean = result.nonDuplicates().stream()
                        .filter(e -> e.getId() == null)
                        .toList();

                yarnService.saveAll(clean, null);

                // ✅ Update UI status
                rows.forEach(r -> {
                    if (!r.isSelected() || r.isInvalid()) return;

                    r.setImported(true);
                    r.setValidationError(null);
                });

                tblPreview.refresh();
                updateSummary();
                updateProgress();

                GlobalUI.success("✔ Import completed (" + selected + " rows)");
                close();
            });

            return;
        }

        // ✅ NO DUPLICATES → DIRECT IMPORT WITH PROGRESS
        int total = entries.size();
        int done = 0;

        for (YarnEntry e : entries) {

            yarnService.save(e);

            done++;

            int finalDone = done;

            javafx.application.Platform.runLater(() ->
                    progressBar.setProgress((double) finalDone / total)
            );
        }

        rows.forEach(r -> {
            if (!r.isSelected()) return;

            if (r.isValid()) {
                r.setImported(true);
                r.setValidationError(null);
            }
        });

        tblPreview.refresh();
        updateSummary();
        updateProgress();

        GlobalUI.success("✔ Import completed (" + selected + " rows)");
        close();
    }
    private void close() {
        progressBar.setProgress(0);
        ((Stage) btnClose.getScene().getWindow()).close();
    }

    private void openDuplicatePopup(List<YarnDuplicateReviewRow> list,
                                    Runnable onDone) {

        try {

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/yarn_duplicate_review.fxml")
            );

            loader.setControllerFactory(appContext::getBean);

            Parent root = loader.load();

            YarnDuplicateReviewController ctrl = loader.getController();

            // ✅ PASS FULL LIST (IMPORTANT CHANGE)
            ctrl.init(list, "YARN IMPORT", () -> {

                int overwritten = 0;

                for (YarnDuplicateReviewRow r : list) {
                    if (r.isSelected()) {
                        yarnService.applyReEntry(r);
                        overwritten++;
                    }
                }

                GlobalUI.success(overwritten + " duplicate row(s) overwritten");

                if (onDone != null) {
                    onDone.run();
                   // tblPreview.refresh();
                }
            });

            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Duplicate Review");
            stage.showAndWait();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @FXML private void onClose() {
        progressBar.setProgress(0);
        ((Stage) btnClose.getScene().getWindow()).close(); }

    // ── Helpers ───────────────────────────────────────────────────
    private void updateSummary() {

        long valid = rows.stream().filter(YarnImportRow::isValid).count();
        long dup   = rows.stream().filter(YarnImportRow::isDuplicate).count();
        long err   = rows.stream().filter(YarnImportRow::isInvalid).count();

        lblSummary.setText(
                "✔ " + valid + "   ⚠ " + dup + "   ✖ " + err
        );
    }

    private String normalizeWtOfBags(String raw) {
        String num = raw.replaceAll("[^0-9]", "").trim();
        String canonical = num + " kg";
        return VALID_BAG_WEIGHTS.contains(canonical) ? canonical : null;
    }

    private String cell(Row row, int col) {
        Cell c = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (c == null) return "";
        return switch (c.getCellType()) {
            case STRING  -> c.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(c))
                    yield c.getLocalDateTimeCellValue().toLocalDate().toString();
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
        for (int i = 0; i < 11; i++) if (!cell(row, i).isBlank()) return false;
        return true;
    }

    private LocalDate parseDate(String raw) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(raw, fmt); } catch (Exception ignored) {}
        }
        return null;
    }

    private boolean blank(String s)            { return s == null || s.isBlank(); }
    private int     parseInt(String s) {
        try { return Integer.parseInt(s == null ? "0" : s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }
    private String s(String v)                { return v != null ? v : ""; }
    private SimpleStringProperty sv(String v) { return new SimpleStringProperty(s(v)); }
}