package com.jobwork.controller;

import com.jobwork.domain.BheemImportRow;
import com.jobwork.domain.JobWorker;
import com.jobwork.service.BheemImportService;
import com.jobwork.repository.JobWorkerRepository;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class BheemImportPreviewController {

    @FXML private TableView<BheemImportRow> tblPreview;
    @FXML private TableColumn<BheemImportRow, Boolean> colSelect;
    @FXML private TableColumn<BheemImportRow, String> colWorker;
    @FXML private TableColumn<BheemImportRow, String> colLocation;
    @FXML private TableColumn<BheemImportRow, String> colChallan;
    @FXML private TableColumn<BheemImportRow, String> colDate;
    @FXML private TableColumn<BheemImportRow, String> colBheemName;
    @FXML private TableColumn<BheemImportRow, String> colTaar;
    @FXML private TableColumn<BheemImportRow, String> colWrapper;
    @FXML private TableColumn<BheemImportRow, String> colYarn;
    @FXML private TableColumn<BheemImportRow, String> colColour;
    @FXML private TableColumn<BheemImportRow, String> colWeight;
    @FXML private TableColumn<BheemImportRow, String> colStatus;

    @FXML private ProgressBar progressBar;
    @FXML private Label lblSummary;

    @Autowired private BheemImportService service;
    @Autowired private JobWorkerRepository workerRepo;

    private List<BheemImportRow> rows;
    private List<JobWorker> workers;

    // ─────────────────────────────────────────────
    // INIT
    // ─────────────────────────────────────────────
    @FXML
    public void initialize() {

        workers = workerRepo.findAll();

        // ✅ Select checkbox
        colSelect.setCellValueFactory(data -> data.getValue().selectedProperty());
        colSelect.setCellFactory(CheckBoxTableCell.forTableColumn(colSelect));

        // ✅ Worker column (DISPLAY)
        colWorker.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getWorkerDisplay())
        );

        // ✅ Worker DROPDOWN (editable)
        colWorker.setCellFactory(col -> new TableCell<>() {

            private final ComboBox<JobWorker> combo = new ComboBox<>();

            {
                combo.setItems(FXCollections.observableArrayList(workers));

                combo.setConverter(new StringConverter<>() {
                    @Override
                    public String toString(JobWorker w) {
                        return w == null ? "" : w.getId() + " - " + w.getName();
                    }

                    @Override
                    public JobWorker fromString(String s) { return null; }
                });

                combo.setOnAction(e -> {
                    BheemImportRow row = getTableView().getItems().get(getIndex());
                    JobWorker selected = combo.getValue();

                    if (selected != null) {
                        row.setJobWorker(selected);
                        row.setWorkerName(selected.getName());
                        validateRow(row);
                        tblPreview.refresh();
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                } else {
                    BheemImportRow row = getTableView().getItems().get(getIndex());
                    combo.setValue(row.getJobWorker());
                    setGraphic(combo);
                }
            }
        });

        // ───────── NORMAL COLUMNS ─────────
        colLocation.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getLocationName()));
        colChallan.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getChallanNo()));
        colDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSafeDate()));
        colBheemName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getBheemNameRaw()));
        colTaar.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTaarRaw()));
        colWrapper.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getWrapperRaw()));
        colYarn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getYarnTypeRaw()));
        colColour.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getColour()));
        colWeight.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSafeWeight()));
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSafeStatus()));

        // ───────── ROW COLOR (LIKE PRODUCT) ─────────
        tblPreview.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(BheemImportRow row, boolean empty) {
                super.updateItem(row, empty);

                if (row == null || empty) {
                    setStyle("");
                    return;
                }

                switch (row.getSafeStatus()) {
                    case BheemImportRow.ERROR ->
                            setStyle("-fx-background-color:#fee2e2;");
                    case BheemImportRow.DUPLICATE ->
                            setStyle("-fx-background-color:#fef3c7;");
                    case BheemImportRow.VALID ->
                            setStyle("-fx-background-color:#dcfce7;");
                    default -> setStyle("");
                }
            }
        });
    }

    // ─────────────────────────────────────────────
    // LOAD
    // ─────────────────────────────────────────────
    public void loadExcel(File file) {

        rows = service.readExcel(file);

        tblPreview.setItems(FXCollections.observableArrayList(rows));

        // ✅ Auto validate
        onValidate();
    }

    // ─────────────────────────────────────────────
    // VALIDATE
    // ─────────────────────────────────────────────
    @FXML
    public void onValidate() {

        service.validate(rows);
        updateSummary();
        updateProgress();
        tblPreview.refresh();
    }

    // ─────────────────────────────────────────────
    // SINGLE ROW VALIDATION (LIVE)
    // ─────────────────────────────────────────────
    private void validateRow(BheemImportRow r) {
        service.validate(List.of(r));
        updateSummary();
        updateProgress();
    }

    // ─────────────────────────────────────────────
    // PROGRESS BAR
    // ─────────────────────────────────────────────
    private void updateProgress() {

        long done = rows.stream().filter(r ->
                r.isValid() || r.isDuplicate()).count();

        progressBar.setProgress((double) done / rows.size());
    }

    // ─────────────────────────────────────────────
    // SUMMARY
    // ─────────────────────────────────────────────
    private void updateSummary() {

        long valid = rows.stream().filter(BheemImportRow::isValid).count();
        long dup = rows.stream().filter(BheemImportRow::isDuplicate).count();
        long err = rows.stream().filter(BheemImportRow::isInvalid).count();

        lblSummary.setText("✔ " + valid + "   ⚠ " + dup + "   ✖ " + err);
    }

    // ─────────────────────────────────────────────
    // SELECT BUTTONS
    // ─────────────────────────────────────────────
    @FXML
    public void onSelectAll() {
        rows.forEach(r -> r.setSelected(true));
        tblPreview.refresh();
    }

    @FXML
    public void onSelectValid() {
        rows.forEach(r -> r.setSelected(r.isValid()));
        tblPreview.refresh();
    }

    @FXML
    public void onDeselectAll() {
        rows.forEach(r -> r.setSelected(false));
        tblPreview.refresh();
    }

    // ─────────────────────────────────────────────
    // IMPORT
    // ─────────────────────────────────────────────
    @FXML
    public void onImport() {

        service.importData(rows);
        onValidate();
    }

    @FXML
    public void onClose() {
        tblPreview.getScene().getWindow().hide();
    }
}