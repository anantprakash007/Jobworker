package com.jobwork.controller;

import com.jobwork.domain.DuplicateCheckResult;
import com.jobwork.domain.DuplicateReviewRow;
import com.jobwork.service.ProductService;
import com.jobwork.util.GlobalUI;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * DuplicateReviewController
 * ─────────────────────────────────────────────────────────────────
 * Handles the duplicate review popup window.
 *
 * Can be opened with 1 row (from onAdd duplicate check) or
 * multiple rows (from onSave / onFinalSubmit batch check).
 *
 * On Confirm → calls ProductService.applyReEntry() which writes to DB directly.
 * On Skip All → calls applyReEntry with empty selected list (only saves non-dups).
 * In both cases → closes popup → fires onCompleteCallback to parent.
 */
@Slf4j
@Component
public class DuplicateReviewController implements Initializable {

    // ── FXML ─────────────────────────────────────────────────────
    @FXML private Label  lblHeading;
    @FXML private Label  lblSubHeading;

    @FXML private TableView<DuplicateReviewRow>            tblDuplicates;
    @FXML private TableColumn<DuplicateReviewRow, Boolean> colSelect;
    @FXML private TableColumn<DuplicateReviewRow, String>  colProductType;
    @FXML private TableColumn<DuplicateReviewRow, String>  colProductName;
    @FXML private TableColumn<DuplicateReviewRow, String>  colExistQty;
    @FXML private TableColumn<DuplicateReviewRow, String>  colNewQty;
    @FXML private TableColumn<DuplicateReviewRow, String>  colExistWt;
    @FXML private TableColumn<DuplicateReviewRow, String>  colNewWt;
    @FXML private TableColumn<DuplicateReviewRow, String>  colExistWtPc;
    @FXML private TableColumn<DuplicateReviewRow, String>  colNewWtPc;

    @FXML private Button btnSelectAll;
    @FXML private Button btnDeselectAll;
    @FXML private Button btnSkipAll;
    @FXML private Button btnConfirm;

    // ── Spring ────────────────────────────────────────────────────
    @Autowired private ProductService productService;

    // ── State ─────────────────────────────────────────────────────
    private DuplicateCheckResult checkResult;
    private Runnable             onCompleteCallback;

    // ── Init ─────────────────────────────────────────────────────
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTable();
    }

    /**
     * Must be called after FXML load, before stage.show().
     *
     * @param result             from ProductService.checkDuplicates() or a single-row wrapper
     * @param challanNo          shown in the heading
     * @param onCompleteCallback called when the popup closes (clears form, updates status)
     */
    public void init(DuplicateCheckResult result,
                     String challanNo,
                     Runnable onCompleteCallback) {

        this.checkResult        = result;
        this.onCompleteCallback = onCompleteCallback;

        int dupCount   = result.rows().size();
        int cleanCount = result.nonDuplicates().size();

        lblHeading.setText("⚠  " + dupCount
                + " Duplicate Row" + (dupCount == 1 ? "" : "s") + " Found!");

        lblSubHeading.setText(
                "Challan  \"" + challanNo + "\"  already has "
                        + dupCount + " matching record" + (dupCount == 1 ? "" : "s")
                        + " in the database."
                        + (cleanCount > 0
                        ? "  (" + cleanCount + " new row"
                        + (cleanCount == 1 ? "" : "s") + " will be saved automatically.)"
                        : "")
                        + "\n\nTick rows to OVERWRITE with new values → click  ✔ Confirm Re-Entry.\n"
                        + "Unticked rows will be SKIPPED — existing data stays unchanged.");

        tblDuplicates.setItems(FXCollections.observableArrayList(result.rows()));

        log.debug("DuplicateReviewController.init → {} duplicate rows, {} non-duplicates",
                dupCount, cleanCount);
    }

    // ── Table setup ───────────────────────────────────────────────
    private void setupTable() {
        tblDuplicates.setEditable(true);
        tblDuplicates.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Checkbox column
        colSelect.setCellValueFactory(c -> c.getValue().selectedProperty());
        colSelect.setCellFactory(CheckBoxTableCell.forTableColumn(colSelect));
        colSelect.setEditable(true);
        colSelect.setPrefWidth(72);
        colSelect.setStyle("-fx-alignment:CENTER;");

        colProductType.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getProductType()));
        colProductName.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getProductName()));

        colExistQty.setCellValueFactory(c ->
                new SimpleStringProperty(bd(c.getValue().getExistingQty())));
        colExistQty.setStyle("-fx-alignment:CENTER-RIGHT;");

        colExistWt.setCellValueFactory(c ->
                new SimpleStringProperty(bd(c.getValue().getExistingWeight())));
        colExistWt.setStyle("-fx-alignment:CENTER-RIGHT;");

        colExistWtPc.setCellValueFactory(c ->
                new SimpleStringProperty(bd(c.getValue().getExistingWtPerPiece())));
        colExistWtPc.setStyle("-fx-alignment:CENTER-RIGHT;");

        colNewQty.setCellValueFactory(c ->
                new SimpleStringProperty(bd(c.getValue().getNewQty())));
        colNewQty.setStyle("-fx-alignment:CENTER-RIGHT;-fx-text-fill:#166534;-fx-font-weight:bold;");

        colNewWt.setCellValueFactory(c ->
                new SimpleStringProperty(bd(c.getValue().getNewWeight())));
        colNewWt.setStyle("-fx-alignment:CENTER-RIGHT;-fx-text-fill:#166534;-fx-font-weight:bold;");

        colNewWtPc.setCellValueFactory(c ->
                new SimpleStringProperty(bd(c.getValue().getNewWtPerPiece())));
        colNewWtPc.setStyle("-fx-alignment:CENTER-RIGHT;-fx-text-fill:#166534;-fx-font-weight:bold;");

        // Highlight selected rows blue
        tblDuplicates.setRowFactory(tv -> {
            TableRow<DuplicateReviewRow> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldItem, item) -> {
                if (item != null) {
                    item.selectedProperty().addListener((o, was, now) ->
                            applyRowStyle(row, now));
                    applyRowStyle(row, item.isSelected());
                } else {
                    row.setStyle("");
                }
            });
            return row;
        });
    }

    private void applyRowStyle(TableRow<DuplicateReviewRow> row, boolean selected) {
        row.setStyle(selected
                ? "-fx-background-color:#dbeafe;-fx-border-color:#3b82f6;-fx-border-width:0 0 1 0;"
                : "");
    }

    // ── Buttons ───────────────────────────────────────────────────
    @FXML private void onSelectAll() {
        tblDuplicates.getItems().forEach(r -> r.setSelected(true));
        tblDuplicates.refresh();
    }

    @FXML private void onDeselectAll() {
        tblDuplicates.getItems().forEach(r -> r.setSelected(false));
        tblDuplicates.refresh();
    }

    /**
     * Skip All — no rows overwritten.
     * Non-duplicates (if any) are still saved.
     */
    @FXML private void onSkipAll() {
        log.info("DuplicateReview → Skip All clicked. Saving {} non-duplicates.",
                checkResult.nonDuplicates().size());
        productService.applyReEntry(List.of(), checkResult.nonDuplicates());

        int skipped = checkResult.rows().size();
        int saved   = checkResult.nonDuplicates().size();
        GlobalUI.success(
                skipped + " duplicate row" + (skipped == 1 ? "" : "s") + " skipped.\n"
                        + (saved > 0 ? saved + " new row" + (saved == 1 ? "" : "s") + " saved." : ""));

        closeAndNotify();
    }

    /**
     * Confirm — overwrites ticked rows, saves non-duplicates, skips unchecked.
     */
    @FXML private void onConfirm() {
        List<DuplicateReviewRow> all      = tblDuplicates.getItems();
        List<DuplicateReviewRow> selected = all.stream()
                .filter(DuplicateReviewRow::isSelected)
                .collect(Collectors.toList());
        int skipped = all.size() - selected.size();

        log.info("DuplicateReview → Confirm: overwriting={} skipping={} saving={}",
                selected.size(), skipped, checkResult.nonDuplicates().size());

        productService.applyReEntry(selected, checkResult.nonDuplicates());

        int reEntered = selected.size();
        int saved     = checkResult.nonDuplicates().size();
        StringBuilder msg = new StringBuilder();
        if (reEntered > 0)
            msg.append(reEntered).append(" row").append(reEntered == 1 ? "" : "s")
                    .append(" re-entered (overwritten).\n");
        if (skipped > 0)
            msg.append(skipped).append(" duplicate row").append(skipped == 1 ? "" : "s")
                    .append(" skipped.\n");
        if (saved > 0)
            msg.append(saved).append(" new row").append(saved == 1 ? "" : "s")
                    .append(" saved.");

        GlobalUI.success(msg.toString().trim());
        closeAndNotify();
    }

    // ── Helpers ───────────────────────────────────────────────────
    private void closeAndNotify() {
        Stage stage = (Stage) btnConfirm.getScene().getWindow();
        stage.close();
        if (onCompleteCallback != null) onCompleteCallback.run();
    }

    private String bd(java.math.BigDecimal v) {
        return v != null ? v.toPlainString() : "—";
    }
}