package com.jobwork.controller;

import com.jobwork.config.FxmlView;
import com.jobwork.domain.*;
import com.jobwork.service.*;
import com.jobwork.util.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.*;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.stage.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.File;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.ResourceBundle;

@Slf4j
@Component
public class BheemEntryController implements Initializable {

    @Autowired private BheemService bheemService;
    @Autowired private JobWorkerService workerService;
    @Autowired private MasterService masterService;
    @Autowired private FileUploadUtil fileUploadUtil;
    @Autowired private ApplicationContext appContext;
    @Autowired private com.jobwork.config.StageManager stageManager;

    @FXML private ComboBox<JobWorker> cbJobWorker;
    @FXML private ComboBox<DeliveryLocation> cbDeliveryLocation;
    @FXML private TextField tfChallanNo;
    @FXML private DatePicker dpDate;

    @FXML private ComboBox<BheemName> cbBheemName;
    @FXML private ComboBox<Taar> cbTaar;
    @FXML private ComboBox<Wrapper> cbWrapper;
    @FXML private ComboBox<YarnType> cbYarnType;
    @FXML private TextField tfColour;
    @FXML private TextField tfWeight;

    @FXML private Label lblStatus;
    @FXML private Label lblReceiptFile;
    @FXML private Button btnViewReceipt;

    private String uploadedReceiptPath;
    private Long editingId;

    // ───────────────── INIT ─────────────────
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupLiveValidation();
        setupKeyboardFlow();


        FormUtil.allowDecimal(tfWeight,         3);
        cbJobWorker.setItems(FXCollections.observableArrayList(workerService.findAll()));
        cbDeliveryLocation.setItems(FXCollections.observableArrayList(masterService.findAllLocations()));
        cbBheemName.setItems(FXCollections.observableArrayList(masterService.findAllBheemNames()));
        cbWrapper.setItems(FXCollections.observableArrayList(masterService.findAllWrappers()));
        cbYarnType.setItems(FXCollections.observableArrayList(masterService.findAllYarnTypes()));
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        dpDate.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(LocalDate d) { return d != null ? df.format(d) : ""; }
            @Override public LocalDate fromString(String s) {
                try { return (s == null || s.isBlank()) ? null : LocalDate.parse(s, df); }
                catch (Exception e) { return null; }
            }
        });
        cbTaar.setDisable(true);

        cbBheemName.valueProperty().addListener((obs, o, n) -> {
            if (n != null) {
                cbTaar.setItems(FXCollections.observableArrayList(
                        masterService.getTaarsByBheemName(n.getId())));
                cbTaar.setDisable(false);
            } else {
                cbTaar.getItems().clear();
                cbTaar.setDisable(true);
            }
        });

        dpDate.setValue(LocalDate.now());
    }

    // ───────────────── SAVE ─────────────────
    @FXML
    public void onSave() {
        if (!validateHeader()) return;
        saveWithDuplicateCheck(EntryStatus.DRAFT);
    }

    @FXML
    public void onFinalSubmit() {
        if (!validateHeader()) return;

        if (!GlobalUI.confirm("Submit", "Submit entry?")) return;

        saveWithDuplicateCheck(EntryStatus.SUBMITTED);
    }

    // ───────────────── DUPLICATE FLOW (FINAL) ─────────────────
   /** private void handleDuplicateFlow(EntryStatus status) {

        BheemEntry incoming = buildEntry(status);

        boolean duplicate = bheemService.isDuplicate(
                incoming.getJobWorker().getId(),
                incoming.getChallanNo(),
                incoming.getEntryDate(),
                incoming.getBheemName().getId(),
                incoming.getTaar().getId()
        );

        // ✅ NO DUPLICATE
        if (!duplicate) {
            bheemService.save(incoming);
            GlobalUI.success("Saved successfully");
            if (status == EntryStatus.SUBMITTED) onReset();
            return;
        }

        // 🔴 DUPLICATE FOUND
        Optional<BheemEntry> existingOpt = bheemService.getExistingDuplicate(
                incoming.getJobWorker().getId(),
                incoming.getChallanNo(),
                incoming.getEntryDate(),
                incoming.getBheemName().getId(),
                incoming.getTaar().getId()
        );

        if (existingOpt.isEmpty()) return;

        BheemEntry existing = existingOpt.get();

        // STEP 1 → ALERT
        boolean proceed = showDuplicateAlert(existing, incoming);
        if (!proceed) return;

        // STEP 2 → POPUP
        openDuplicatePopup(existing, incoming, status);
    }
*/
    // ───────────────── ALERT (PRODUCT STYLE) ─────────────────
    private boolean showDuplicateAlert(BheemEntry existing, BheemEntry incoming) {

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Duplicate Entry Detected");
        alert.setHeaderText(null);

        alert.setContentText(
                "Challan \"" + incoming.getChallanNo() + "\" already exists.\n\n" +
                        "Existing → Weight: " + safe(existing.getWeight()) + "\n" +
                        "New      → Weight: " + safe(incoming.getWeight()) + "\n\n" +
                        "Click OK to review and overwrite."
        );

        ButtonType ok = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(ok, cancel);

        Optional<ButtonType> result = alert.showAndWait();

        return result.isPresent() && result.get() == ok;
    }

    // ───────────────── POPUP ─────────────────
  /**  private void openDuplicatePopup(BheemEntry existing,
                                    BheemEntry incoming,
                                    EntryStatus status) {

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/bheem_duplicate_review.fxml")
            );

            loader.setControllerFactory(appContext::getBean);
            Parent root = loader.load();

            BheemDuplicateReviewController ctrl = loader.getController();

            ctrl.init(
                    java.util.List.of(new BheemDuplicateReviewRow(existing, incoming)),
                    incoming.getChallanNo(),
                    () -> {
                        bheemService.overwrite(existing, incoming);
                        GlobalUI.success("Overwritten successfully");
                        if (status == EntryStatus.SUBMITTED) onReset();
                    }
            );

            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Duplicate Review");
            stage.showAndWait();

        } catch (Exception e) {
            e.printStackTrace();
            GlobalUI.warn("Popup failed: " + e.getMessage());
        }
    }*/

    // ───────────────── IMPORT ─────────────────
    @FXML
    public void onImportExcel() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel", "*.xlsx"));
        File file = fc.showOpenDialog(null);
        if (file == null) return;

        GlobalUI.openWindow(
                "/fxml/bheem_import_preview.fxml",
                "Import Preview",
                c -> ((BheemImportPreviewController) c).loadExcel(file)
        );
    }

    // ───────────────── BUILD ENTRY ─────────────────
    private BheemEntry buildEntry(EntryStatus status) {
        return BheemEntry.builder()
                .jobWorker(cbJobWorker.getValue())
                .deliveryLocation(cbDeliveryLocation.getValue())
                .challanNo(tfChallanNo.getText())
                .entryDate(dpDate.getValue())
                .bheemName(cbBheemName.getValue())
                .taar(cbTaar.getValue())
                .wrapper(cbWrapper.getValue())
                .yarnType(cbYarnType.getValue())
                .colour(tfColour.getText())
                .weight(FormUtil.parseDecimal(tfWeight.getText()))
                .receiptPath(uploadedReceiptPath)
                .status(status)
                .build();
    }

    // ───────────────── VALIDATION ─────────────────
   /** private boolean validate() {
        ValidationUtil.start();
        ValidationUtil.required(tfChallanNo, "Required");
        ValidationUtil.required(cbJobWorker, "Required");
        ValidationUtil.required(dpDate, "Required");
        ValidationUtil.required(cbBheemName, "Required");
        ValidationUtil.required(cbTaar, "Required");
        ValidationUtil.required(cbWrapper, "Required");
        ValidationUtil.required(cbYarnType, "Required");
        ValidationUtil.number(tfWeight, "Invalid");
        return ValidationUtil.validate();
    }*/

    private String safe(Object v) {
        return v == null ? "0" : v.toString();

    }

    @FXML
    public void onReset() {
        tfChallanNo.clear();
        cbJobWorker.getSelectionModel().clearSelection();
        cbDeliveryLocation.getSelectionModel().clearSelection();
        cbBheemName.getSelectionModel().clearSelection();
        cbTaar.getItems().clear();
        cbWrapper.getSelectionModel().clearSelection();
        cbYarnType.getSelectionModel().clearSelection();
        tfColour.clear();
        tfWeight.clear();
        dpDate.setValue(LocalDate.now());
        lblStatus.setText("● New Entry");
    }

    @FXML
    public void onExit() {
        stageManager.showScene(FxmlView.MAIN_LAYOUT);
    }
    @FXML
    public void onUploadReceipt() {

        FileChooser fc = new FileChooser();
        fc.setTitle("Upload Receipt");

        File file = fc.showOpenDialog(null);

        if (file != null) {
            try {
                uploadedReceiptPath = fileUploadUtil.saveReceipt(file, tfChallanNo.getText());

                lblReceiptFile.setText(file.getName());
                btnViewReceipt.setVisible(true);

                GlobalUI.success("Receipt uploaded");

            } catch (Exception e) {
                e.printStackTrace();
                GlobalUI.warn("Upload failed: " + e.getMessage());
            }
        }
    }
    @FXML
    public void onViewReceipt() {

        if (uploadedReceiptPath == null || uploadedReceiptPath.isBlank()) {
            GlobalUI.warn("No receipt uploaded");
            return;
        }

        try {
            File file = new File(uploadedReceiptPath);

            if (!file.exists()) {
                GlobalUI.warn("File not found");
                return;
            }

            Desktop.getDesktop().open(file);

        } catch (Exception e) {
            e.printStackTrace();
            GlobalUI.warn("Cannot open file: " + e.getMessage());
        }
    }
    // ───────────────── ACTION BUTTON HANDLERS ─────────────────

    @FXML
    public void onEdit() {
        GlobalUI.warn("Edit feature coming soon");
    }

    @FXML
    public void onExportPDF() {
        GlobalUI.warn("Print/PDF feature coming soon");
    }
    private void setupLiveValidation() {
        tfChallanNo.textProperty().addListener((o,a,b) -> FormUtil.clearError(tfChallanNo));
        tfWeight.textProperty().addListener((o,a,b) -> FormUtil.clearError(tfWeight));

        cbJobWorker.valueProperty().addListener((o,a,b) -> FormUtil.clearError(cbJobWorker));
        cbBheemName.valueProperty().addListener((o,a,b) -> FormUtil.clearError(cbBheemName));
        cbTaar.valueProperty().addListener((o,a,b) -> FormUtil.clearError(cbTaar));
    }
    private void setupKeyboardFlow() {

        FormUtil.moveNext(tfChallanNo, cbJobWorker);
        FormUtil.moveNextCombo(cbJobWorker, dpDate.getEditor());
        FormUtil.moveNext(dpDate.getEditor(), cbBheemName);
        FormUtil.moveNextCombo(cbBheemName, cbTaar);
        FormUtil.moveNextCombo(cbTaar, cbWrapper);
        FormUtil.moveNextCombo(cbWrapper, cbYarnType);
        FormUtil.moveNextCombo(cbYarnType, tfColour);
        FormUtil.moveNext(tfColour, tfWeight);

        tfWeight.setOnAction(e -> onFinalSubmit());
    }
    private boolean validateHeader() {
        ValidationUtil.start();

        ValidationUtil.required(tfChallanNo, "Challan required");
        ValidationUtil.required(cbJobWorker, "Worker required");
        ValidationUtil.required(dpDate, "Date required");
        ValidationUtil.required(cbBheemName, "Bheem required");
        ValidationUtil.required(cbTaar, "Taar required");

        ValidationUtil.number(tfWeight, "Invalid weight");

        return ValidationUtil.validate();
    }
    private void saveWithDuplicateCheck(EntryStatus status) {

        BheemEntry incoming = buildEntry(status);

        boolean dup = bheemService.isDuplicate(
                incoming.getJobWorker().getId(),
                incoming.getChallanNo(),
                incoming.getEntryDate(),
                incoming.getBheemName().getId(),
                incoming.getTaar().getId()
        );

        // ✅ NO DUPLICATE
        if (!dup) {
            bheemService.save(incoming);
            GlobalUI.success("Saved successfully");

            if (status == EntryStatus.SUBMITTED) onReset();
            return;
        }

        // 🔴 DUPLICATE
        BheemEntry existing = bheemService.getExistingDuplicate(
                incoming.getJobWorker().getId(),
                incoming.getChallanNo(),
                incoming.getEntryDate(),
                incoming.getBheemName().getId(),
                incoming.getTaar().getId()
        ).orElse(null);

        if (existing == null) return;

        boolean proceed = showDuplicateAlert(existing, incoming);

        if (!proceed) return;

        openSingleDuplicatePopup(existing, incoming, status);
    }
    private void openSingleDuplicatePopup(BheemEntry existing,
                                          BheemEntry incoming,
                                          EntryStatus status) {

        try {

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/bheem_duplicate_review.fxml")
            );

            loader.setControllerFactory(appContext::getBean);
            Parent root = loader.load();

            BheemDuplicateReviewController ctrl = loader.getController();

            ctrl.init(
                    java.util.List.of(new BheemDuplicateReviewRow(existing, incoming)),
                    incoming.getChallanNo(),
                    () -> {
                        bheemService.overwrite(existing, incoming);
                        GlobalUI.success("Overwritten successfully");

                        if (status == EntryStatus.SUBMITTED) onReset();
                    }
            );

            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Duplicate Review");
            stage.showAndWait();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}