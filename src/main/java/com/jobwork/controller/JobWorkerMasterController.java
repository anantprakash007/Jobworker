package com.jobwork.controller;

import com.jobwork.domain.JobWorker;
import com.jobwork.service.JobWorkerService;
import com.jobwork.util.GlobalUI;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URL;
import java.util.ResourceBundle;

@Component
public class JobWorkerMasterController implements Initializable {

    @Autowired private JobWorkerService workerService;

    // ── Form fields ───────────────────────────────────────────────
    @FXML private TextField tfName;
    @FXML private TextField tfPhone;
    @FXML private TextField tfAddress;
    @FXML private TextField tfOpeningBalance;
    @FXML private Label lblStatus;

    // ── Table ─────────────────────────────────────────────────────
    @FXML private TableView<JobWorker> tblWorkers;
    @FXML private TableColumn<JobWorker, String> colName;
    @FXML private TableColumn<JobWorker, String> colPhone;
    @FXML private TableColumn<JobWorker, String> colAddress;
    @FXML private TableColumn<JobWorker, Void> colEdit;
    @FXML private TableColumn<JobWorker, Void> colDelete;
    @FXML private Label lblCount;

    // ── State ─────────────────────────────────────────────────────
    private Long editingId = null;
    private final ObservableList<JobWorker> workers =
            FXCollections.observableArrayList();

    // ════════════════════════════════════════════════════════════
    @Override
    public void initialize(URL url, ResourceBundle rb) {

        tblWorkers.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        colName.setCellValueFactory(c ->
                new SimpleStringProperty(GlobalUI.safeStr(c.getValue().getName())));
        colPhone.setCellValueFactory(c ->
                new SimpleStringProperty(GlobalUI.safeStr(c.getValue().getPhone())));
        colAddress.setCellValueFactory(c ->
                new SimpleStringProperty(GlobalUI.safeStr(c.getValue().getAddress())));

        tblWorkers.setItems(workers);

        // Edit
        colEdit.setCellFactory(
                GlobalUI.<JobWorker>actionColumn("EDIT", 120, true,
                        this::openEditDialog).getCellFactory());

        // Delete
        colDelete.setCellFactory(
                GlobalUI.<JobWorker>actionColumn("DELETE", 130, false, worker -> {
                    if (GlobalUI.confirm("Delete Worker",
                            "Delete '" + worker.getName() + "'?")) {
                        workerService.deleteById(worker.getId());
                        GlobalUI.success("Deleted successfully");
                        refreshTable();
                    }
                }).getCellFactory());

        refreshTable();
    }

    // ════════════════════════════════════════════════════════════
    // SAVE
    // ════════════════════════════════════════════════════════════
    @FXML
    public void onSave() {

        String name = tfName.getText().trim();

        if (name.isEmpty()) {
            lblStatus.setText("⚠ Name is required.");
            tfName.requestFocus();
            return;
        }

        JobWorker worker = editingId != null
                ? workerService.findById(editingId).orElse(new JobWorker())
                : new JobWorker();

        worker.setName(name);
        worker.setPhone(tfPhone.getText().trim().isEmpty()
                ? null : tfPhone.getText().trim());
        worker.setAddress(tfAddress.getText().trim().isEmpty()
                ? null : tfAddress.getText().trim());

        // 🔥 OPENING BALANCE FIX
        try {
            worker.setOpeningBalance(
                    tfOpeningBalance.getText() == null || tfOpeningBalance.getText().isBlank()
                            ? BigDecimal.ZERO
                            : new BigDecimal(tfOpeningBalance.getText().trim())
            );
            // 🔥 PREVENT CHANGE AFTER TRANSACTION
            if (editingId != null &&
                    workerService.hasTransactions(editingId)) {

                JobWorker existing = workerService.findById(editingId).orElse(null);

                if (existing != null) {
                    worker.setOpeningBalance(existing.getOpeningBalance());
                }
            }
        } catch (Exception e) {
            lblStatus.setText("⚠ Invalid Opening Balance");
            return;
        }

        workerService.save(worker);

        lblStatus.setText(editingId != null
                ? "✔ Updated: " + name
                : "✔ Added: " + name);

        onReset();
        refreshTable();
    }

    // ════════════════════════════════════════════════════════════
    // RESET
    // ════════════════════════════════════════════════════════════
    @FXML
    public void onReset() {
        tfName.clear();
        tfPhone.clear();
        tfAddress.clear();
        tfOpeningBalance.clear();
        editingId = null;
        tfName.requestFocus();
    }

    // ════════════════════════════════════════════════════════════
    // EDIT DIALOG
    // ════════════════════════════════════════════════════════════
    private void openEditDialog(JobWorker worker) {

        Dialog<JobWorker> dialog = new Dialog<>();
        dialog.setTitle("Edit Job Worker");
        dialog.setHeaderText("Editing: " + worker.getName());

        ButtonType saveBtn = new ButtonType("💾 Save",
                ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(10);
        grid.setStyle("-fx-padding:18;");

        TextField fName = GlobalUI.inputField(worker.getName(), 250);
        TextField fPhone = GlobalUI.inputField(worker.getPhone(), 200);
        TextField fAddress = GlobalUI.inputField(worker.getAddress(), 280);
        TextField fOpening = GlobalUI.inputField(
                worker.getOpeningBalance() != null
                        ? worker.getOpeningBalance().toString()
                        : "0",
                200
        );

// 🔥 LOCK IF TRANSACTION EXISTS
        if (worker.getId() != null &&
                workerService.hasTransactions(worker.getId())) {

            fOpening.setDisable(true);
            fOpening.setStyle("-fx-background-color:#e5e7eb;");

            Label note = new Label("Opening locked after transactions. Use Adjustment Entry.");
            note.setStyle("-fx-text-fill:#dc2626; -fx-font-size:11px;");

            grid.add(note, 1, 4);
        }
        grid.addRow(0, GlobalUI.boldLabel("Name *:"), fName);
        grid.addRow(1, GlobalUI.boldLabel("Phone:"), fPhone);
        grid.addRow(2, GlobalUI.boldLabel("Address:"), fAddress);
        grid.addRow(3, GlobalUI.boldLabel("Opening Balance:"), fOpening);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn != saveBtn) return null;

            try {
                worker.setName(fName.getText().trim());
                worker.setPhone(fPhone.getText().trim());
                worker.setAddress(fAddress.getText().trim());

                worker.setOpeningBalance(
                        fOpening.getText().isBlank()
                                ? BigDecimal.ZERO
                                : new BigDecimal(fOpening.getText())
                );

            } catch (Exception e) {
                GlobalUI.warn("Invalid Opening Balance");
                return null;
            }

            return worker;
        });

        dialog.showAndWait().ifPresent(updated -> {
            workerService.save(updated);
            lblStatus.setText("✔ Updated: " + updated.getName());
            refreshTable();
        });
    }

    // ════════════════════════════════════════════════════════════
    // REFRESH TABLE
    // ════════════════════════════════════════════════════════════
    private void refreshTable() {
        workers.setAll(workerService.findAll());
        if (lblCount != null)
            lblCount.setText(workers.size() + " workers");
    }
}