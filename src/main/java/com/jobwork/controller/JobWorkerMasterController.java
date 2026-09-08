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

    @Autowired
    private JobWorkerService workerService;

    // ═════════════════════════════════════════════════════════════
    // FORM
    // ═════════════════════════════════════════════════════════════

    @FXML
    private TextField tfName;

    @FXML
    private TextField tfPhone;

    @FXML
    private TextField tfAddress;

    @FXML
    private TextField tfOpeningBalance;

    @FXML
    private Label lblStatus;

    // ═════════════════════════════════════════════════════════════
    // TABLE
    // ═════════════════════════════════════════════════════════════

    @FXML
    private TableView<JobWorker> tblWorkers;

    @FXML
    private TableColumn<JobWorker, String> colName;

    @FXML
    private TableColumn<JobWorker, String> colPhone;

    @FXML
    private TableColumn<JobWorker, String> colAddress;

    @FXML
    private TableColumn<JobWorker, Void> colEdit;

    @FXML
    private TableColumn<JobWorker, Void> colDelete;

    @FXML
    private Label lblCount;

    // ═════════════════════════════════════════════════════════════
    // STATE
    // ═════════════════════════════════════════════════════════════

    private Long editingId = null;

    private final ObservableList<JobWorker> workers =
            FXCollections.observableArrayList();

    // ═════════════════════════════════════════════════════════════
    // INITIALIZE
    // ═════════════════════════════════════════════════════════════

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        tblWorkers.setColumnResizePolicy(
                TableView.UNCONSTRAINED_RESIZE_POLICY
        );

        colName.setCellValueFactory(c ->
                new SimpleStringProperty(
                        GlobalUI.safeStr(
                                c.getValue().getName()
                        )
                )
        );

        colPhone.setCellValueFactory(c ->
                new SimpleStringProperty(
                        GlobalUI.safeStr(
                                c.getValue().getPhone()
                        )
                )
        );

        colAddress.setCellValueFactory(c ->
                new SimpleStringProperty(
                        GlobalUI.safeStr(
                                c.getValue().getAddress()
                        )
                )
        );

        tblWorkers.setItems(workers);

        // ════════════════════════════════════════════════════════
        // EDIT
        // ════════════════════════════════════════════════════════

        colEdit.setCellFactory(
                GlobalUI.<JobWorker>actionColumn(
                        "EDIT",
                        120,
                        true,
                        this::openEditDialog
                ).getCellFactory()
        );

        // ════════════════════════════════════════════════════════
        // DELETE
        // ════════════════════════════════════════════════════════

        colDelete.setCellFactory(
                GlobalUI.<JobWorker>actionColumn(
                        "DELETE",
                        130,
                        false,
                        worker -> {

                            if (worker == null ||
                                    worker.getId() == null) {
                                return;
                            }

                            if (GlobalUI.confirm(
                                    "Delete Worker",
                                    "Delete '" +
                                            worker.getName() +
                                            "'?"
                            )) {

                                if (workerService.hasTransactions(
                                        worker.getId())) {

                                    GlobalUI.warn(
                                            "This Job Worker has transactions.\n\n" +
                                                    "Worker cannot be deleted."
                                    );

                                    return;
                                }

                                try {

                                    workerService.deleteById(
                                            worker.getId()
                                    );

                                    GlobalUI.success(
                                            "Deleted successfully"
                                    );

                                    refreshTable();

                                } catch (Exception ex) {

                                    GlobalUI.warn(
                                            "Delete failed: " +
                                                    ex.getMessage()
                                    );
                                }
                            }
                        }
                ).getCellFactory()
        );

        refreshTable();
    }

    // ═════════════════════════════════════════════════════════════
    // SAVE
    // ═════════════════════════════════════════════════════════════

    @FXML
    public void onSave() {

        String name =
                tfName.getText() == null
                        ? ""
                        : tfName.getText().trim();

        String phone =
                tfPhone.getText() == null
                        ? ""
                        : tfPhone.getText().trim();

        String address =
                tfAddress.getText() == null
                        ? ""
                        : tfAddress.getText().trim();

        // ════════════════════════════════════════════════════════
        // NAME REQUIRED
        // ════════════════════════════════════════════════════════

        if (name.isEmpty()) {

            lblStatus.setText(
                    "⚠ Name is required."
            );

            tfName.requestFocus();
            return;
        }

        // ════════════════════════════════════════════════════════
        // PHONE REQUIRED
        // ════════════════════════════════════════════════════════

        if (phone.isEmpty()) {

            lblStatus.setText(
                    "⚠ Mobile number is required."
            );

            tfPhone.requestFocus();
            return;
        }

        // ════════════════════════════════════════════════════════
        // PHONE FORMAT
        // ════════════════════════════════════════════════════════

        if (!phone.matches("\\d{10}")) {

            lblStatus.setText(
                    "⚠ Mobile number must be exactly 10 digits."
            );

            tfPhone.requestFocus();
            return;
        }

        // ════════════════════════════════════════════════════════
        // DUPLICATE NAME
        // ════════════════════════════════════════════════════════

        boolean duplicateName;

        if (editingId == null) {

            duplicateName =
                    workerService.existsByNameIgnoreCase(name);

        } else {

            duplicateName =
                    workerService.existsByNameIgnoreCaseAndIdNot(
                            name,
                            editingId
                    );
        }

        if (duplicateName) {

            lblStatus.setText(
                    "⚠ Job Worker name already exists."
            );

            tfName.requestFocus();
            return;
        }

        // ════════════════════════════════════════════════════════
        // DUPLICATE PHONE
        // ════════════════════════════════════════════════════════

        boolean duplicatePhone;

        if (editingId == null) {

            duplicatePhone =
                    workerService.existsByPhone(phone);

        } else {

            duplicatePhone =
                    workerService.existsByPhoneAndIdNot(
                            phone,
                            editingId
                    );
        }

        if (duplicatePhone) {

            lblStatus.setText(
                    "⚠ Mobile number already exists."
            );

            tfPhone.requestFocus();
            return;
        }

        // ════════════════════════════════════════════════════════
        // LOAD EXISTING OR CREATE NEW
        // ════════════════════════════════════════════════════════

        JobWorker worker;

        if (editingId != null) {

            worker =
                    workerService.findById(editingId)
                            .orElse(null);

            if (worker == null) {

                GlobalUI.warn(
                        "Worker record not found."
                );

                editingId = null;
                return;
            }

        } else {

            worker = new JobWorker();
        }

        // ════════════════════════════════════════════════════════
        // BASIC FIELDS
        // ════════════════════════════════════════════════════════

        worker.setName(name);
        worker.setPhone(phone);
        worker.setAddress(
                address.isEmpty()
                        ? null
                        : address
        );

        // ════════════════════════════════════════════════════════
        // OPENING BALANCE
        // ════════════════════════════════════════════════════════

        try {

            BigDecimal openingBalance =
                    tfOpeningBalance.getText() == null ||
                            tfOpeningBalance.getText().isBlank()
                            ? BigDecimal.ZERO
                            : new BigDecimal(
                            tfOpeningBalance
                                    .getText()
                                    .trim()
                    );

            if (openingBalance.scale() > 2) {

                openingBalance =
                        openingBalance.setScale(
                                2,
                                java.math.RoundingMode.HALF_UP
                        );
            }

            // Opening balance cannot be changed after transactions.
            if (editingId != null &&
                    workerService.hasTransactions(editingId)) {

                JobWorker existing =
                        workerService.findById(editingId)
                                .orElse(null);

                if (existing != null) {

                    worker.setOpeningBalance(
                            existing.getOpeningBalance()
                    );
                }

            } else {

                worker.setOpeningBalance(
                        openingBalance
                );
            }

        } catch (Exception e) {

            lblStatus.setText(
                    "⚠ Invalid Opening Balance."
            );

            tfOpeningBalance.requestFocus();
            return;
        }

        // ════════════════════════════════════════════════════════
        // SAVE
        // ════════════════════════════════════════════════════════

        try {

            workerService.save(worker);

            lblStatus.setText(
                    editingId != null
                            ? "✔ Updated: " + name
                            : "✔ Added: " + name
            );

            onReset();
            refreshTable();

        } catch (Exception ex) {

            GlobalUI.warn(
                    "Save failed: " +
                            ex.getMessage()
            );
        }
    }

    // ═════════════════════════════════════════════════════════════
    // RESET
    // ═════════════════════════════════════════════════════════════

    @FXML
    public void onReset() {

        tfName.clear();
        tfPhone.clear();
        tfAddress.clear();
        tfOpeningBalance.clear();

        editingId = null;

        tfName.requestFocus();
    }

    // ═════════════════════════════════════════════════════════════
    // EDIT DIALOG
    // ═════════════════════════════════════════════════════════════

    private void openEditDialog(JobWorker worker) {

        if (worker == null) {
            return;
        }

        Dialog<JobWorker> dialog =
                new Dialog<>();

        dialog.setTitle(
                "Edit Job Worker"
        );

        dialog.setHeaderText(
                "Editing: " +
                        GlobalUI.safeStr(
                                worker.getName()
                        )
        );

        ButtonType saveBtn =
                new ButtonType(
                        "💾 Save",
                        ButtonBar.ButtonData.OK_DONE
                );

        dialog.getDialogPane()
                .getButtonTypes()
                .addAll(
                        saveBtn,
                        ButtonType.CANCEL
                );

        GridPane grid =
                new GridPane();

        grid.setHgap(14);
        grid.setVgap(10);
        grid.setStyle(
                "-fx-padding:18;"
        );

        TextField fName =
                GlobalUI.inputField(
                        GlobalUI.safeStr(
                                worker.getName()
                        ),
                        250
                );

        TextField fPhone =
                GlobalUI.inputField(
                        GlobalUI.safeStr(
                                worker.getPhone()
                        ),
                        200
                );

        TextField fAddress =
                GlobalUI.inputField(
                        GlobalUI.safeStr(
                                worker.getAddress()
                        ),
                        280
                );

        TextField fOpening =
                GlobalUI.inputField(
                        worker.getOpeningBalance() != null
                                ? worker.getOpeningBalance()
                                .toString()
                                : "0",
                        200
                );

        // Mobile number must remain exactly 10 digits.
        fPhone.setTextFormatter(
                new TextFormatter<String>(
                        change -> {

                            String text =
                                    change.getControlNewText();

                            if (text.matches("\\d{0,10}")) {
                                return change;
                            }

                            return null;
                        }
                )
        );

        // ════════════════════════════════════════════════════════
        // LOCK OPENING BALANCE AFTER TRANSACTIONS
        // ════════════════════════════════════════════════════════

        if (worker.getId() != null &&
                workerService.hasTransactions(
                        worker.getId()
                )) {

            fOpening.setDisable(true);

            fOpening.setStyle(
                    "-fx-background-color:#e5e7eb;"
            );

            Label note =
                    new Label(
                            "Opening locked after transactions. " +
                                    "Use Adjustment Entry."
                    );

            note.setStyle(
                    "-fx-text-fill:#dc2626;" +
                            "-fx-font-size:11px;"
            );

            grid.add(
                    note,
                    1,
                    4
            );
        }

        grid.addRow(
                0,
                GlobalUI.boldLabel("Name *:"),
                fName
        );

        grid.addRow(
                1,
                GlobalUI.boldLabel("Mobile *:"),
                fPhone
        );

        grid.addRow(
                2,
                GlobalUI.boldLabel("Address:"),
                fAddress
        );

        grid.addRow(
                3,
                GlobalUI.boldLabel("Opening Balance:"),
                fOpening
        );

        dialog.getDialogPane()
                .setContent(grid);

        // ════════════════════════════════════════════════════════
        // RESULT
        // ════════════════════════════════════════════════════════

        dialog.setResultConverter(btn -> {

            if (btn != saveBtn) {
                return null;
            }

            String name =
                    fName.getText() == null
                            ? ""
                            : fName.getText().trim();

            String phone =
                    fPhone.getText() == null
                            ? ""
                            : fPhone.getText().trim();

            String address =
                    fAddress.getText() == null
                            ? ""
                            : fAddress.getText().trim();

            // NAME
            if (name.isEmpty()) {

                GlobalUI.warn(
                        "Job Worker name is required."
                );

                return null;
            }

            // PHONE
            if (phone.isEmpty()) {

                GlobalUI.warn(
                        "Mobile number is required."
                );

                return null;
            }

            if (!phone.matches("\\d{10}")) {

                GlobalUI.warn(
                        "Mobile number must be exactly 10 digits."
                );

                return null;
            }

            // DUPLICATE NAME
            if (workerService.existsByNameIgnoreCaseAndIdNot(
                    name,
                    worker.getId()
            )) {

                GlobalUI.warn(
                        "Job Worker name already exists."
                );

                return null;
            }

            // DUPLICATE PHONE
            if (workerService.existsByPhoneAndIdNot(
                    phone,
                    worker.getId()
            )) {

                GlobalUI.warn(
                        "Mobile number already exists."
                );

                return null;
            }

            try {

                worker.setName(name);
                worker.setPhone(phone);

                worker.setAddress(
                        address.isEmpty()
                                ? null
                                : address
                );

                // Opening balance is locked if transactions exist.
                if (worker.getId() == null ||
                        !workerService.hasTransactions(
                                worker.getId()
                        )) {

                    worker.setOpeningBalance(
                            fOpening.getText() == null ||
                                    fOpening.getText().isBlank()
                                    ? BigDecimal.ZERO
                                    : new BigDecimal(
                                    fOpening
                                            .getText()
                                            .trim()
                            )
                    );
                }

            } catch (Exception e) {

                GlobalUI.warn(
                        "Invalid Opening Balance."
                );

                return null;
            }

            return worker;
        });

        dialog.showAndWait()
                .ifPresent(updated -> {

                    try {

                        workerService.save(
                                updated
                        );

                        lblStatus.setText(
                                "✔ Updated: " +
                                        updated.getName()
                        );

                        refreshTable();

                    } catch (Exception ex) {

                        GlobalUI.warn(
                                "Update failed: " +
                                        ex.getMessage()
                        );
                    }
                });
    }

    // ═════════════════════════════════════════════════════════════
    // REFRESH TABLE
    // ═════════════════════════════════════════════════════════════

    private void refreshTable() {

        workers.setAll(
                workerService.findAll()
        );

        if (lblCount != null) {

            lblCount.setText(
                    workers.size() +
                            " workers"
            );
        }
    }
}