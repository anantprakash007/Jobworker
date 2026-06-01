package com.jobwork.controller;

import com.jobwork.config.FxmlView;
import com.jobwork.domain.*;

import com.jobwork.service.JobWorkerService;
import com.jobwork.service.MoneyService;
import com.jobwork.util.FileUploadUtil;
import com.jobwork.util.FormUtil;
import com.jobwork.util.GlobalUI;
import com.jobwork.util.ValidationUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;

import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * MoneyReceiptController
 * ──────────────────────────────────────────────────────────────────
 * Manages money receipt entry with:
 *  • All fields mandatory validation
 *  • Worker selection auto-loads total-paid-to-worker balance
 *  • Amount field styled amber for prominence
 *  • Transfer mode combo (CASH/NEFT/UPI/CHEQUE)
 *  • Receipt upload + view
 *  • Save (DRAFT), Submit, Print, Edit, Reset
 *  • Status badge updates
 */
@Component
public class MoneyReceiptController implements Initializable {


    @Autowired private MoneyService     moneyService;
    @Autowired private JobWorkerService workerService;
    @Autowired private FileUploadUtil   fileUploadUtil;
    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;
    @FXML private ComboBox<JobWorker> cbJobWorker;
    @FXML private DatePicker          dpDate;
    @FXML private TextField           tfChallanNo;
    @FXML private TextField           tfAmount;
    @FXML private ComboBox<String>    cbTransferMode;
    @FXML private TextField           tfRemark;

    @FXML private Label  lblTotalPaid;
    @FXML private Label  lblReceiptFile;
    @FXML private Button btnViewReceipt;
    @FXML private Label  lblStatus;
    @FXML
    private ComboBox<EntryType> cbEntryType;

    private String uploadedReceiptPath;
    private Long   editingReceiptId;
    private boolean saving = false;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        javafx.application.Platform.runLater(() -> tfChallanNo.requestFocus());
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        dpDate.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date != null ? df.format(date) : "";
            }

            @Override
            public LocalDate fromString(String string) {
                try {
                    return (string == null || string.isBlank()) ? null : LocalDate.parse(string, df);
                } catch (Exception e) {
                    return null;
                }
            }
        });
        FormUtil.allowNumeric(tfAmount, true);
        FormUtil.autoSelect(tfChallanNo);
        FormUtil.autoSelect(tfAmount);
        cbJobWorker.setCellFactory(cb -> new ListCell<>() {
            @Override
            protected void updateItem(JobWorker item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getId() + " - " + item.getName());
            }
        });
        cbEntryType.setItems(FXCollections.observableArrayList(EntryType.values()));

        cbJobWorker.setButtonCell(cbJobWorker.getCellFactory().call(null));
        cbJobWorker.setItems(FXCollections.observableArrayList(workerService.findAll()));
        cbTransferMode.setItems(FXCollections.observableArrayList(
                "CASH","NEFT","UPI","CHEQUE"));
        dpDate.setValue(LocalDate.now());

        // Load total paid whenever worker changes
        cbJobWorker.valueProperty().addListener((obs, old, worker) -> {
            if (worker != null) {
                BigDecimal total = moneyService.totalPaidToWorker(worker.getId());
                lblTotalPaid.setText("₹ " + (total != null ? total.toPlainString() : "0.00"));
            } else {
                lblTotalPaid.setText("₹ 0.00");
            }
        });
        FormUtil.moveNext(tfChallanNo, tfAmount);
        FormUtil.moveNext(tfAmount, cbTransferMode);
        FormUtil.moveNextCombo(cbTransferMode, tfRemark);
        setupLiveValidation();

// FINAL FIELD
        tfRemark.setOnAction(e -> onSave());
    }
    @FXML
    public void onImportExcel() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Excel File");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fc.showOpenDialog(
                tfAmount != null ? tfAmount.getScene().getWindow() : null
        );

        if (file == null) return;

        try {
            GlobalUI.openWindow(
                    "/fxml/money_import_preview.fxml",   // create this if not exists
                    "Money Import Preview",
                    controller -> ((MoneyImportPreviewController) controller).loadExcel(file)
            );
        } catch (Exception ex) {
            GlobalUI.warn("Import failed: " + ex.getMessage());
        }
    }
    private void setupLiveValidation() {

        tfChallanNo.textProperty().addListener((o,a,b)->FormUtil.clearError(tfChallanNo));
        tfAmount.textProperty().addListener((o,a,b)->FormUtil.clearError(tfAmount));

        cbJobWorker.valueProperty().addListener((o,a,b)->FormUtil.clearError(cbJobWorker));
        cbTransferMode.valueProperty().addListener((o,a,b)->FormUtil.clearError(cbTransferMode));
    }
    // ════════════════════════════════════════════════════════════
    //  SAVE — DRAFT
    // ════════════════════════════════════════════════════════════

    @FXML
    public void onSave() {

        if (saving) return;
        saving = true;

        try {

            if (!validate()) {
                saving = false;
                return;
            }

            MoneyReceipt incoming = buildReceipt(EntryStatus.DRAFT);

            // ✅ VALIDATE MODE FIRST
            TransferMode mode = incoming.getTransferMode();

            if (mode == null) {
                GlobalUI.warn("Invalid Transaction Type");
                saving = false;
                return;
            }

            // ✅ DUPLICATE CHECK
            long count = moneyService.countDuplicate(
                    incoming.getJobWorker().getId(),
                    incoming.getChallanNo().trim(),
                    incoming.getReceiptDate()

            );

            // ✅ NO DUPLICATE → SAVE
            if (count == 0) {
                moneyService.save(incoming);
                GlobalUI.success("Saved");
                onReset();
                saving = false;
                return;
            }

            // ❗ DUPLICATE → FETCH EXISTING
            MoneyReceipt existing = moneyService.getExistingDuplicate(
                    incoming.getJobWorker().getId(),
                    incoming.getChallanNo().trim(),
                    incoming.getReceiptDate()

            );

            if (existing == null) {
                GlobalUI.warn("Duplicate detected but existing record not found (data mismatch)");
                saving = false;
                return;
            }

            // ❗ SHOW POPUP
           // GlobalUI.warn("Duplicate found → Please review");

            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Duplicate Entry Detected");

            alert.setHeaderText(
                    "Challan \"" + incoming.getChallanNo() + "\" already exists.\n\n" +
                            "Existing → Amount: " + existing.getAmount() + "\n" +
                            "New      → Amount: " + incoming.getAmount() + "\n\n" +
                            "Click OK to open review window"
            );

            alert.showAndWait();

// 👉 THEN open detailed popup
            openDuplicatePopup(existing, incoming);
        } catch (Exception e) {
            GlobalUI.warn("Error: " + e.getMessage());
            saving = false;
        }
    }
    private void openDuplicatePopup(MoneyReceipt existing, MoneyReceipt incoming) {

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(FxmlView.MONEY_DUPLICATE_REVIEW.getFxmlPath())
            );

            loader.setControllerFactory(applicationContext::getBean);

            Parent root = loader.load();

            MoneyDuplicateReviewController c = loader.getController();

            List<MoneyDuplicateReviewRow> rows = List.of(
                    new MoneyDuplicateReviewRow(existing, incoming)
            );

            c.init(rows, "Duplicate Found", () -> {

                moneyService.overwrite(existing, incoming);

                onReset();

                GlobalUI.success("Overwrite completed");
                saving = false;
            });

            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(FxmlView.MONEY_DUPLICATE_REVIEW.getTitle());

            stage.showAndWait();

        } catch (Exception e) {
            e.printStackTrace();
            GlobalUI.warn("Popup failed: " + e.getMessage());
            saving = false;
        }
    }
    // ════════════════════════════════════════════════════════════
    //  SUBMIT
    // ════════════════════════════════════════════════════════════

    @FXML
    public void onSubmit() {

        if (!validate()) return;

        MoneyReceipt incoming = buildReceipt(EntryStatus.SUBMITTED);

        // ✅ DUPLICATE CHECK (SAME AS SAVE)
        long count = moneyService.countDuplicate(
                incoming.getJobWorker().getId(),
                incoming.getChallanNo().trim(),
                incoming.getReceiptDate()
        );

        if (count > 0) {

            MoneyReceipt existing = moneyService.getExistingDuplicate(
                    incoming.getJobWorker().getId(),
                    incoming.getChallanNo().trim(),
                    incoming.getReceiptDate()
            );

            if (existing != null) {
                openDuplicatePopup(existing, incoming);
            } else {
                GlobalUI.warn("Duplicate detected but record not found");
            }

            return; // 🚨 VERY IMPORTANT — STOP SUBMIT
        }

        // ✅ CONFIRM AFTER CHECK
        if (!GlobalUI.confirm("Submit Receipt",
                "Submit this receipt? This cannot be undone."))
            return;

        moneyService.save(incoming);

        lblStatus.setText("● Submitted");
        GlobalUI.success("Receipt submitted successfully.");
        onReset();
    }
    // ── EDIT — reload saved receipt into form ────────────────────
    @FXML
    public void onEdit() {
        if (editingReceiptId == null) { GlobalUI.warn("Save the receipt first."); return; }
        Optional<MoneyReceipt> opt = moneyService.findById(editingReceiptId);
        opt.ifPresent(r -> {
            cbJobWorker.setValue(r.getJobWorker());
            dpDate.setValue(r.getReceiptDate());
            tfChallanNo.setText(r.getChallanNo() != null ? r.getChallanNo() : "");
            tfAmount.setText(r.getAmount() != null ? r.getAmount().toPlainString() : "");
            cbTransferMode.setValue(r.getTransferMode() != null
                    ? r.getTransferMode().name() : null);
            tfRemark.setText(r.getRemark() != null ? r.getRemark() : "");
            lblStatus.setText("● Editing");
        });
    }

    @FXML
    public void onReset() {
        cbJobWorker.getSelectionModel().clearSelection();
        dpDate.setValue(LocalDate.now());
        tfChallanNo.clear();
        tfAmount.clear();
        cbTransferMode.getSelectionModel().clearSelection();
        tfRemark.clear();
        uploadedReceiptPath = null;
        editingReceiptId = null;
        lblReceiptFile.setText("No file selected");
        lblReceiptFile.setStyle("-fx-text-fill:#94a3b8;-fx-font-style:italic;");
        if (btnViewReceipt != null) btnViewReceipt.setVisible(false);
        lblTotalPaid.setText("₹ 0.00");
        lblStatus.setText("● New Receipt");
        javafx.application.Platform.runLater(() -> tfChallanNo.requestFocus());
    }

    @FXML public void onPrint() {GlobalUI.success("Print — not yet implemented."); }
    @FXML public void onExit()  { /* handled by menu */ }

    @FXML
    public void onUploadReceipt() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Images / PDF","*.jpg","*.jpeg","*.png","*.pdf"));
        File f = fc.showOpenDialog(tfAmount.getScene().getWindow());
        if (f != null) {
            try {
                uploadedReceiptPath = fileUploadUtil.saveReceipt(
                        f, tfChallanNo.getText().trim());
                lblReceiptFile.setText("✔ " + f.getName());
                lblReceiptFile.setStyle("-fx-text-fill:#16a34a; -fx-font-weight:bold;");
                if (btnViewReceipt != null) btnViewReceipt.setVisible(true);
            } catch (IOException ex) { GlobalUI.warn("Upload failed: " + ex.getMessage()); }
        }
    }

    @FXML
    public void onViewReceipt() {
        if (uploadedReceiptPath != null) {
            try {
                fileUploadUtil.openReceipt(uploadedReceiptPath);
            } catch (java.io.IOException ex) {
                GlobalUI.warn("Cannot open receipt: " + ex.getMessage());
            }
        }
    }

    private MoneyReceipt buildReceipt(EntryStatus status) {
        String mode = cbTransferMode.getValue();
        return MoneyReceipt.builder()
                .jobWorker(cbJobWorker.getValue())
                .receiptDate(dpDate.getValue())
                .challanNo(tfChallanNo.getText() == null ? "" : tfChallanNo.getText().trim())
                .amount(new BigDecimal(
                        tfAmount.getText() == null || tfAmount.getText().isBlank()
                                ? "0"
                                : tfAmount.getText().trim()
                ))
                .transferMode(parseTransferMode(mode))
                .remark(tfRemark.getText() == null ? "" : tfRemark.getText().trim())
                .receiptPath(uploadedReceiptPath)
                .status(status)
                .build();
    }
    private com.jobwork.domain.TransferMode parseTransferMode(String mode) {
        try {
            return mode == null ? null
                    : com.jobwork.domain.TransferMode.valueOf(mode.trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean validate() {

        ValidationUtil.start();

        ValidationUtil.required(cbJobWorker, "Job Worker required");
        ValidationUtil.required(tfChallanNo, "Challan No required");
       // ValidationUtil.number(tfAmount, "Enter valid amount");
        if (!FormUtil.validateNumber(tfAmount)) {
            return false;
        }
        String amtText = tfAmount.getText();

        if (amtText == null || amtText.isBlank()) {
            FormUtil.setError(tfAmount, "Amount required");
            return false;
        }

        BigDecimal amt;
        try {
            amt = new BigDecimal(amtText.trim());
        } catch (Exception e) {
            FormUtil.setError(tfAmount, "Invalid amount");
            return false;
        }
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            FormUtil.setError(tfAmount, "Amount must be > 0");
            return false;
        }
        ValidationUtil.required(cbTransferMode, "Select transfer mode");

        ValidationUtil.custom(dpDate.getValue() != null, dpDate, "Select Date");

        return ValidationUtil.validate();
    }
   /** private void infoPopup(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Success");
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }

    private void warnPopup(String message) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("Warning");
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }*/

}
