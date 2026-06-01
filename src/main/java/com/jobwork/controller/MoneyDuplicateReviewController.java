package com.jobwork.controller;

import com.jobwork.domain.MoneyDuplicateReviewRow;
import com.jobwork.service.MoneyService;
import com.jobwork.util.GlobalUI;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class MoneyDuplicateReviewController {

    // ── TABLE ─────────────────────────────────────
    @FXML private TableView<MoneyDuplicateReviewRow> tblDup;

    @FXML private TableColumn<MoneyDuplicateReviewRow, Boolean> colSelect;
    @FXML private TableColumn<MoneyDuplicateReviewRow, String> colWorker;
    @FXML private TableColumn<MoneyDuplicateReviewRow, String> colChallan;
    @FXML private TableColumn<MoneyDuplicateReviewRow, String> colDate;
    @FXML private TableColumn<MoneyDuplicateReviewRow, String> colTxType;

    @FXML private TableColumn<MoneyDuplicateReviewRow, String> colExistAmount;
    @FXML private TableColumn<MoneyDuplicateReviewRow, String> colExistRemark;

    @FXML private TableColumn<MoneyDuplicateReviewRow, String> colNewAmount;
    @FXML private TableColumn<MoneyDuplicateReviewRow, String> colNewRemark;

    // ── LABELS ────────────────────────────────────
    @FXML private Label lblHeading;
    @FXML private Label lblSubHeading;

    @Autowired
    private MoneyService service;

    private List<MoneyDuplicateReviewRow> rows;
    private Runnable onDone;

    // ── INIT ──────────────────────────────────────
    public void init(List<MoneyDuplicateReviewRow> rows,
                     String title,
                     Runnable callback) {

        this.rows = rows;
        this.onDone = callback;
        if (rows == null || rows.isEmpty()) {
            lblHeading.setText("No duplicates found");
            lblSubHeading.setText("");
            return;
        }

        MoneyDuplicateReviewRow r = rows.get(0);
        lblHeading.setText("⚠ " + rows.size() + " Duplicate(s) Found");
        lblSubHeading.setText(
                "Challan \"" + r.getChallanNo() + "\" already exists.\n\n" +
                        "Existing → Amount: " + r.getExistingAmount() + "\n" +
                        "New      → Amount: " + r.getNewAmount() + "\n\n" +
                        "Click confirm to OVERWRITE or Skip to keep existing.");

        tblDup.setItems(FXCollections.observableArrayList(rows));
        rows.forEach(row -> {

            boolean amountChanged =
                    row.getExistingAmount() != null &&
                            row.getNewAmount() != null &&
                           row.getExistingAmount().compareTo(row.getNewAmount()) != 0;

            boolean remarkChanged =
                    row.getExistingRemark() != null &&
                            row.getNewRemark() != null &&
                            !row.getExistingRemark().equals(row.getNewRemark());

            row.setSelected(amountChanged || remarkChanged);
        });
    }

    // ── INITIALIZE ────────────────────────────────
    @FXML
    public void initialize() {

        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        colSelect.setCellValueFactory(d -> d.getValue().selectedProperty());
        colSelect.setCellFactory(CheckBoxTableCell.forTableColumn(colSelect));

        colWorker.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getWorkerName() != null
                                ? d.getValue().getWorkerName()
                                : ""
                ));

        colChallan.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getChallanNo() != null
                                ? d.getValue().getChallanNo()
                                : ""
                ));

        colDate.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getDate() != null
                                ? df.format(d.getValue().getDate())
                                : ""
                ));

        colTxType.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getTransferMode() != null
                                ? d.getValue().getTransferMode().name()
                                : ""
                ));

        colExistAmount.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getExistingAmount() != null
                                ? d.getValue().getExistingAmount().toString()
                                : ""
                ));
        colExistAmount.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setText(null);
                    setStyle("");
                    return;
                }

                setText(item);

                // 👇 Grey style for existing
                setStyle("-fx-background-color:#f3f4f6; -fx-text-fill:#6b7280;");
            }
        });

        colNewAmount.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getNewAmount() != null
                                ? d.getValue().getNewAmount().toString()
                                : ""
                ));

        colNewAmount.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || getTableRow() == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                MoneyDuplicateReviewRow row =
                        getTableRow().getItem();

                if (row == null) return;

                setText(item);

                boolean changed = row.getExistingAmount() != null &&
                        row.getNewAmount() != null &&
                        row.getExistingAmount().compareTo(row.getNewAmount()) != 0;

                if (changed) {
                    setStyle("-fx-background-color:#dcfce7; -fx-text-fill:#166534; -fx-font-weight:bold;");
                } else {
                    setStyle("");
                }
            }
        });

        colExistRemark.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getExistingRemark() != null
                                ? d.getValue().getExistingRemark()
                                : ""
                ));
        colExistRemark.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setText(null);
                    setStyle("");
                    return;
                }

                setText(item);

                setStyle("-fx-background-color:#f3f4f6; -fx-text-fill:#6b7280;");
            }
        });
        colNewRemark.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getNewRemark() != null
                                ? d.getValue().getNewRemark()
                                : ""
                ));
        colNewRemark.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || getTableRow() == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                MoneyDuplicateReviewRow row = getTableRow().getItem();

                if (row == null) return;

                setText(item);

                boolean changed = row.getExistingRemark() != null &&
                        row.getNewRemark() != null &&
                        !row.getExistingRemark().equals(row.getNewRemark());

                if (changed) {
                    setStyle("-fx-background-color:#dcfce7; -fx-text-fill:#166534;");
                } else {
                    setStyle("");
                }
            }
        });
    }

    // ── CONFIRM ───────────────────────────────────
    @FXML
    public void onConfirm() {

        int count = 0;

        for (MoneyDuplicateReviewRow r : rows) {
            if (r.isSelected()) {
                service.overwrite(r.getExistingEntry(), r.getIncomingEntry());
                count++;
            }
        }

        GlobalUI.success(count + " overwritten");

        if (onDone != null) onDone.run();

        close();
    }

    // ── SKIP ──────────────────────────────────────
    @FXML
    public void onSkip() {
        GlobalUI.warn("Skipped duplicates");
        close();
    }

    // ── SELECT ALL ────────────────────────────────
    @FXML
    private void onSelectAll() {
        if (rows == null) return;
        rows.forEach(r -> r.setSelected(true));
        tblDup.refresh();
    }

    // ── DESELECT ALL ──────────────────────────────
    @FXML
    private void onDeselectAll() {
        if (rows == null) return;
        rows.forEach(r -> r.setSelected(false));
        tblDup.refresh();
    }

    // ── CLOSE ─────────────────────────────────────
    private void close() {
        tblDup.getScene().getWindow().hide();
    }
    public TableView<MoneyDuplicateReviewRow> getTable() {
        return tblDup;
    }
}