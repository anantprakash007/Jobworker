package com.jobwork.controller;

import com.jobwork.domain.BheemDuplicateReviewRow;
import com.jobwork.service.BheemService;
import com.jobwork.util.GlobalUI;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BheemDuplicateReviewController {

    @FXML private TableView<BheemDuplicateReviewRow> table;

    @FXML private TableColumn<BheemDuplicateReviewRow, Boolean> colSelect;
    @FXML private TableColumn<BheemDuplicateReviewRow, String> colBheem;
    @FXML private TableColumn<BheemDuplicateReviewRow, String> colTaar;

    @FXML private TableColumn<BheemDuplicateReviewRow, String> colOldWeight;
    @FXML private TableColumn<BheemDuplicateReviewRow, String> colNewWeight;

    @FXML private Label lblHeader;

    @Autowired
    private BheemService service;

    private List<BheemDuplicateReviewRow> rows;
    private Runnable onDone;

    // ─────────────────────────────────────────────
    public void init(List<BheemDuplicateReviewRow> rows,
                     String challan,
                     Runnable callback) {

        this.rows = rows;
        this.onDone = callback;

        lblHeader.setText("⚠ " + rows.size() + " Duplicate Row Found! (Challan: " + challan + ")");
        table.setItems(FXCollections.observableArrayList(rows));
    }

    // ─────────────────────────────────────────────
    @FXML
    public void initialize() {

        colSelect.setCellValueFactory(d -> d.getValue().selectedProperty());
        colSelect.setCellFactory(CheckBoxTableCell.forTableColumn(colSelect));

        colBheem.setCellValueFactory(d -> d.getValue().bheemNameProperty());
        colTaar.setCellValueFactory(d -> d.getValue().taarProperty());

        colOldWeight.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        d.getValue().getExistingWeight().toString()
                ));

        colNewWeight.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        d.getValue().getNewWeight().toString()
                ));
    }

    // ─────────────────────────────────────────────
    @FXML
    public void onSelectAll() {
        rows.forEach(r -> r.setSelected(true));
        table.refresh();
    }

    @FXML
    public void onDeselectAll() {
        rows.forEach(r -> r.setSelected(false));
        table.refresh();
    }

    // ─────────────────────────────────────────────
    @FXML
    public void onSkipAll() {
        GlobalUI.warn("All duplicates skipped");
        close();
    }

    // ─────────────────────────────────────────────
    @FXML
    public void onConfirm() {

        int count = 0;

        for (BheemDuplicateReviewRow r : rows) {

            if (r.isSelected()) {
                service.overwrite(r.getExistingEntry(), r.getIncomingEntry());
                count++;
            }
        }

        GlobalUI.success(count + " row(s) overwritten");

        if (onDone != null) onDone.run();

        close();
    }

    private void close() {
        table.getScene().getWindow().hide();
    }
}