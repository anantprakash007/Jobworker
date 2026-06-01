package com.jobwork.controller;

import com.jobwork.domain.YarnDuplicateReviewRow;
import com.jobwork.service.YarnService;
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

/**
 * YarnDuplicateReviewController
 * ─────────────────────────────────────────────────────────────────
 * Popup shown when a duplicate YarnEntry is detected on Add Row click.
 * Always shows exactly ONE row.
 *
 * Confirm → YarnService.applyReEntry() overwrites DB + cleans extras
 * Skip    → existing record unchanged; incoming discarded
 */
@Slf4j
@Component
public class YarnDuplicateReviewController implements Initializable {

    // ── FXML ─────────────────────────────────────────────────────
    @FXML private Label  lblHeading;
    @FXML private Label  lblSubHeading;

    @FXML private TableView<YarnDuplicateReviewRow>            tblDup;
    @FXML private TableColumn<YarnDuplicateReviewRow, Boolean> colSelect;
    @FXML private TableColumn<YarnDuplicateReviewRow, String>  colYarnType;
    @FXML private TableColumn<YarnDuplicateReviewRow, String>  colBagPiece;
    @FXML private TableColumn<YarnDuplicateReviewRow, String>  colExistWtBags;
    @FXML private TableColumn<YarnDuplicateReviewRow, String>  colNewWtBags;
    @FXML private TableColumn<YarnDuplicateReviewRow, String>  colExistColour;
    @FXML private TableColumn<YarnDuplicateReviewRow, String>  colNewColour;
    @FXML private TableColumn<YarnDuplicateReviewRow, String>  colExistBags;
    @FXML private TableColumn<YarnDuplicateReviewRow, String>  colNewBags;
    @FXML private TableColumn<YarnDuplicateReviewRow, String>  colExistCones;
    @FXML private TableColumn<YarnDuplicateReviewRow, String>  colNewCones;
    @FXML private TableColumn<YarnDuplicateReviewRow, String>  colExistWeight;
    @FXML private TableColumn<YarnDuplicateReviewRow, String>  colNewWeight;

    @FXML private Button btnSkip;
    @FXML private Button btnConfirm;

    // ── Spring ────────────────────────────────────────────────────
    @Autowired private YarnService yarnService;

    // ── State ─────────────────────────────────────────────────────
    private List<YarnDuplicateReviewRow> rows;
    private Runnable               onCompleteCallback;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTable();

        // ✅ APPLY DIFF HIGHLIGHT HERE (CORRECT PLACE)
        applyDiffHighlight(colNewWeight,
                r -> r.getExistingWeight().toString(),
                r -> r.getNewWeight().toString());

        applyDiffHighlight(colNewColour,
                YarnDuplicateReviewRow::getColour,
                YarnDuplicateReviewRow::getNewColour);

        applyDiffHighlight(colYarnType,
                YarnDuplicateReviewRow::getYarnTypeName,
                r -> r.getIncomingEntry().getYarnType().getName());
    }
    /**
     * Must be called after FXML load, before stage.show().
     *
    // * @param row                single duplicate row to review
    // * @param challanNo          shown in heading
     //* @param onCompleteCallback called after popup closes
     */
    public void init(List<YarnDuplicateReviewRow> rows,
                     String title,
                     Runnable callback) {

        this.rows = rows;
        this.onCompleteCallback = callback;

        // ✅ Auto-select changed rows
        rows.forEach(r -> r.setSelected(isChanged(r)));

        lblHeading.setText("⚠ " + rows.size() +
                " Duplicate Row(s) Found! (" + title + ")");

        tblDup.setItems(FXCollections.observableArrayList(rows));
    }
    private boolean isChanged(YarnDuplicateReviewRow r) {
        return
                !safe(r.getWtOfBags()).equals(safe(r.getNewWtOfBags())) ||
                        !safe(r.getColour()).equalsIgnoreCase(safe(r.getNewColour())) ||
                        r.getExistingBags() != r.getNewBags() ||
                        r.getExistingCones() != r.getNewCones() ||
                        !safeBd(r.getExistingWeight()).equals(safeBd(r.getNewWeight()));
    }
    private String safe(String v) {
        return v == null ? "" : v.trim();
    }
    private void applyDiffHighlight(
            TableColumn<YarnDuplicateReviewRow, String> col,
            java.util.function.Function<YarnDuplicateReviewRow, String> oldValFn,
            java.util.function.Function<YarnDuplicateReviewRow, String> newValFn) {

        col.setCellFactory(tc -> new TableCell<>() {

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || getTableRow() == null) {
                    setText(null);
                    setStyle("");
                    setTooltip(null);
                    return;
                }

                YarnDuplicateReviewRow row = getTableRow().getItem();
                if (row == null) {
                    setText(null);
                    setStyle("");
                    setTooltip(null);
                    return;
                }

                String oldV = oldValFn.apply(row);
                String newV = newValFn.apply(row);

                String oldStr = oldV == null ? "" : oldV.trim();
                String newStr = newV == null ? "" : newV.trim();

                setText(item);

                // ✅ TOOLTIP (Old → New)
                if (!oldStr.equalsIgnoreCase(newStr)) {
                    setTooltip(new Tooltip("Old: " + oldStr + "\nNew: " + newStr));
                } else {
                    setTooltip(null);
                }

                // ✅ COLOR LOGIC
                if (!oldStr.equalsIgnoreCase(newStr)) {

                    if (oldStr.isEmpty()) {
                        // 🟢 NEW VALUE ADDED
                        setStyle("-fx-background-color:#dcfce7; -fx-text-fill:#166534; -fx-font-weight:bold;");
                    } else if (newStr.isEmpty()) {
                        // 🔴 VALUE REMOVED
                        setStyle("-fx-background-color:#fee2e2; -fx-text-fill:#991b1b; -fx-font-weight:bold;");
                    } else {
                        // 🟡 MODIFIED
                        setStyle("-fx-background-color:#fef9c3; -fx-font-weight:bold;");
                    }

                } else {
                    setStyle("");
                }
            }
        });
    }
    private java.math.BigDecimal safeBd(java.math.BigDecimal v) {
        return v == null ? java.math.BigDecimal.ZERO : v;
    }

    // ── Table setup ───────────────────────────────────────────────
    private void setupTable() {
        tblDup.setEditable(true);
        tblDup.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        colSelect.setCellValueFactory(c -> c.getValue().selectedProperty());
        colSelect.setCellFactory(CheckBoxTableCell.forTableColumn(colSelect));
        colSelect.setEditable(true);
        colSelect.setPrefWidth(62);
        colSelect.setStyle("-fx-alignment:CENTER;");

        colYarnType.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getYarnTypeName()));
        colBagPiece.setCellValueFactory(c ->
                new SimpleStringProperty(s(c.getValue().getBagPiece())));
        colBagPiece.setStyle("-fx-alignment:CENTER;");

        // Existing (muted)
        colExistWtBags.setCellValueFactory(c ->
                new SimpleStringProperty(s(c.getValue().getWtOfBags())));
        colExistColour.setCellValueFactory(c ->
                new SimpleStringProperty(s(c.getValue().getColour())));
        colExistBags.setCellValueFactory(c ->
                new SimpleStringProperty(
                        "BAG".equalsIgnoreCase(c.getValue().getBagPiece())
                                ? String.valueOf(c.getValue().getExistingBags()) : "—"));
        colExistBags.setStyle("-fx-alignment:CENTER-RIGHT;");
        colExistCones.setCellValueFactory(c ->
                new SimpleStringProperty(
                        "PIECE".equalsIgnoreCase(c.getValue().getBagPiece())
                                ? String.valueOf(c.getValue().getExistingCones()) : "—"));
        colExistCones.setStyle("-fx-alignment:CENTER-RIGHT;");
        colExistWeight.setCellValueFactory(c ->
                new SimpleStringProperty(bd(c.getValue().getExistingWeight())));
        colExistWeight.setStyle("-fx-alignment:CENTER-RIGHT;");

        // New (green/bold)
        String green = "-fx-text-fill:#166534;-fx-font-weight:bold;";
        colNewWtBags.setCellValueFactory(c ->
                new SimpleStringProperty(s(c.getValue().getNewWtOfBags())));
        colNewWtBags.setStyle(green);
        colNewColour.setCellValueFactory(c ->
                new SimpleStringProperty(s(c.getValue().getNewColour())));
        colNewColour.setStyle(green);
        colNewBags.setCellValueFactory(c ->
                new SimpleStringProperty(
                        "BAG".equalsIgnoreCase(c.getValue().getBagPiece())
                                ? String.valueOf(c.getValue().getNewBags()) : "—"));
        colNewBags.setStyle("-fx-alignment:CENTER-RIGHT;" + green);
        colNewCones.setCellValueFactory(c ->
                new SimpleStringProperty(
                        "PIECE".equalsIgnoreCase(c.getValue().getBagPiece())
                                ? String.valueOf(c.getValue().getNewCones()) : "—"));
        colNewCones.setStyle("-fx-alignment:CENTER-RIGHT;" + green);
        colNewWeight.setCellValueFactory(c ->
                new SimpleStringProperty(bd(c.getValue().getNewWeight())));
        colNewWeight.setStyle("-fx-alignment:CENTER-RIGHT;" + green);

        // Row highlight when selected
        tblDup.setRowFactory(tv -> {
            TableRow<YarnDuplicateReviewRow> row = new TableRow<>();
            row.itemProperty().addListener((obs, o, item) -> {
                if (item != null) {
                    item.selectedProperty().addListener((ob, was, now) -> applyStyle(row, now));
                    applyStyle(row, item.isSelected());
                } else { row.setStyle(""); }
            });
            return row;
        });
    }

    private void applyStyle(TableRow<YarnDuplicateReviewRow> row, boolean sel) {
        row.setStyle(sel
                ? "-fx-background-color:#dbeafe;-fx-border-color:#3b82f6;-fx-border-width:0 0 1 0;"
                : "");
    }

    // ── Buttons ───────────────────────────────────────────────────
    @FXML
    private void onSkip() {
        GlobalUI.warn("All duplicates skipped");
        closeAndNotify();
    }
    @FXML
    private void onConfirm() {

        int count = 0;

        for (YarnDuplicateReviewRow r : rows) {

            if (r.isSelected()) {
                yarnService.applyReEntry(r);
                count++;
            }
        }

        GlobalUI.success(count + " duplicate row(s) overwritten");

        closeAndNotify();
    }

    private void closeAndNotify() {
        ((Stage) btnConfirm.getScene().getWindow()).close();
        if (onCompleteCallback != null) onCompleteCallback.run();
    }

    private String s(String v)                { return v != null ? v : "—"; }
    private String bd(java.math.BigDecimal v) { return v != null ? v.toPlainString() : "—"; }
}