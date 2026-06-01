package com.jobwork.controller;

import com.jobwork.domain.ProductName;
import com.jobwork.domain.ProductType;
import com.jobwork.service.MasterService;
import com.jobwork.util.FormUtil;
import com.jobwork.util.GlobalUI;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URL;
import java.util.ResourceBundle;

import static com.jobwork.util.FormUtil.validateNumber;
import static com.jobwork.util.FormUtil.validateRequired;

@Component
public class ProductMasterController implements Initializable {

    @Autowired private MasterService masterService;

    // ── LEFT panel ────────────────────────────────────────────────
    @FXML private TextField              tfTypeName;
    @FXML private Label                  lblTypeStatus;
    @FXML private TableView<ProductType> tblTypes;
    @FXML private TableColumn<ProductType, String> colTypeName;

    // ── RIGHT panel ────────────────────────────────────────────────
    @FXML private Label   lblSelectedType;
    @FXML private Label   lblPanelTitle;
    @FXML private Label   lblNameStatus;
    @FXML private Button  btnAddName;

    @FXML private TextField tfNameValue;
    @FXML private TextField tfPick;
    @FXML private TextField tfLength;
    @FXML private TextField tfWidth;
    @FXML private TextField tfTotalPicks;
    @FXML private TextField tfReed;
    @FXML private TextField tfReedSpace;

    @FXML private TableView<ProductName>           tblNames;
    @FXML private TableColumn<ProductName, String> colNameValue;
    @FXML private TableColumn<ProductName, String> colPick;
    @FXML private TableColumn<ProductName, String> colLength;
    @FXML private TableColumn<ProductName, String> colWidth;
    @FXML private TableColumn<ProductName, String> colTotalPicks;
    @FXML private TableColumn<ProductName, String> colReed;
    @FXML private TableColumn<ProductName, String> colReedSpace;
    @FXML private TextField tfPaisa;
    @FXML private TextField tfRate;
    @FXML private TableColumn<ProductName, BigDecimal> colPaisa;
    @FXML private TableColumn<ProductName, BigDecimal> colRate;
    private ProductType selectedType;
    private final ObservableList<ProductType> types = FXCollections.observableArrayList();
    private final ObservableList<ProductName> names = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        FormUtil.allowNumeric(tfPick, false);
        FormUtil.allowNumeric(tfTotalPicks, false);
        FormUtil.allowNumeric(tfReed, false);
        FormUtil.allowNumeric(tfLength, true);
        FormUtil.allowNumeric(tfWidth, true);
        FormUtil.allowNumeric(tfReedSpace, true);

        setupLiveValidation();

        // ── Types table ───────────────────────────────────────────
        colTypeName.setCellValueFactory(c ->
                new SimpleStringProperty(GlobalUI.safeStr(c.getValue().getName())));
        tblTypes.setItems(types);

        // GlobalUI.actionColumn() — zero local style duplication
        tblTypes.getColumns().add(
                GlobalUI.actionColumn("EDIT", 72, true,
                        this::openTypeEditDialog));
        tblTypes.getColumns().add(
                GlobalUI.actionColumn("DELETE", 84, false, pt -> {
                    if (GlobalUI.confirm("Delete Type",
                            "Delete type '" + pt.getName() + "' and all its names?")) {
                        masterService.deleteProductType(pt.getId());
                        if (selectedType != null && selectedType.getId().equals(pt.getId())) {
                            selectedType = null;
                            names.clear();
                            setNamePanelDisabled(true);
                            if (lblPanelTitle != null)
                                lblPanelTitle.setText("Names for: (select a type)");
                            if (lblSelectedType != null)
                                lblSelectedType.setText("← Click a Product Type on the left");
                        }
                        GlobalUI.success("Deleted successfully");
                        refreshTypes();
                    }
                }));

        // ── Names table ───────────────────────────────────────────
        colNameValue.setCellValueFactory(c ->
                new SimpleStringProperty(GlobalUI.safeStr(c.getValue().getName())));
        colPick.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getPick() != null
                        ? String.valueOf(c.getValue().getPick()) : ""));
        colLength.setCellValueFactory(c ->
                new SimpleStringProperty(formatDouble(c.getValue().getLength())));
        colWidth.setCellValueFactory(c ->
                new SimpleStringProperty(formatDouble(c.getValue().getWidth())));
        colTotalPicks.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getTotalPicks() != null
                        ? String.valueOf(c.getValue().getTotalPicks()) : ""));
        colReed.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getReed() != null
                        ? String.valueOf(c.getValue().getReed()) : ""));
        colReedSpace.setCellValueFactory(c ->
                new SimpleStringProperty(formatDouble(c.getValue().getReedSpace())));
        // ================= PAISA & RATE DISPLAY =================

// value factory (already required)
        colPaisa.setCellValueFactory(c ->
                new javafx.beans.property.SimpleObjectProperty<>(
                        c.getValue().getDefaultPaisa()
                )
        );

        colRate.setCellValueFactory(c ->
                new javafx.beans.property.SimpleObjectProperty<>(
                        c.getValue().getDefaultRate()
                )
        );

// 🔥 FORMAT DISPLAY (PUT EXACTLY HERE)
        colPaisa.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal val, boolean empty) {
                super.updateItem(val, empty);
                setText(empty || val == null ? "" : val.toString());
            }
        });

        colRate.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal val, boolean empty) {
                super.updateItem(val, empty);
                setText(empty || val == null ? "" : val.toString());
            }
        });
        tblNames.setItems(names);
        tblNames.getColumns().add(
                GlobalUI.actionColumn("EDIT", 72, true,
                        this::openNameEditDialog));
        tblNames.getColumns().add(
                GlobalUI.actionColumn("DELETE", 84, false, pn -> {
                    if (GlobalUI.confirm("Delete Product Name",
                            "Delete '" + pn.getName() + "'?")) {
                        masterService.deleteProductName(pn.getId());
                        GlobalUI.success("Deleted successfully");
                        refreshNames();
                    }
                }));

        setNamePanelDisabled(true);
        refreshTypes();

        // ── Keyboard flow ─────────────────────────────────────────
        FormUtil.moveNext(tfNameValue, tfPick);
        FormUtil.moveNext(tfPick,      tfLength);
        FormUtil.moveNext(tfLength,    tfWidth);
        FormUtil.moveNext(tfWidth,     tfTotalPicks);
        FormUtil.moveNext(tfTotalPicks, tfReed);
        FormUtil.moveNext(tfReed,      tfReedSpace);
    }

    // ════════════════════════════════════════════════════════════
    //  LEFT PANEL
    // ════════════════════════════════════════════════════════════

    @FXML
    public void onTypeSelected(MouseEvent event) {
        ProductType sel = tblTypes.getSelectionModel().getSelectedItem();
        if (sel != null) {
            selectedType = sel;
            setNamePanelDisabled(false);
            if (lblSelectedType != null)
                lblSelectedType.setText("← Showing names for:  " + sel.getName());
            if (lblPanelTitle != null)
                lblPanelTitle.setText("Names for: " + sel.getName());
            refreshNames();
        }
    }

    @FXML
    public void onAddType() {
        String name = tfTypeName.getText().trim();
        if (name.isEmpty()) { lblTypeStatus.setText("⚠ Enter a type name."); return; }
        try {
            masterService.addProductType(name);
            tfTypeName.clear();
            lblTypeStatus.setText("✔ Type '" + name + "' added.");
            refreshTypes();
        } catch (Exception ex) {
            lblTypeStatus.setText("⚠ " + ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    //  RIGHT PANEL
    // ════════════════════════════════════════════════════════════

    @FXML
    public void onAddName() {


        if (selectedType == null) {
            lblNameStatus.setText("⚠ Select a product type first.");
            return;
        }

        String name = tfNameValue.getText().trim();

        if (name.isEmpty()) {
            lblNameStatus.setText("⚠ Enter a product name.");
            return;
        }

        ProductName pn = new ProductName();
        pn.setName(name);
        pn.setProductType(selectedType);

// Existing fields
        applyFields(pn, tfPick, tfLength, tfWidth, tfTotalPicks, tfReed, tfReedSpace);

// 🔥 NEW: Paisa & Rate
        BigDecimal paisa = parseDecimal(tfPaisa.getText());
        BigDecimal rate  = parseDecimal(tfRate.getText());

        pn.setDefaultPaisa(paisa);
        pn.setDefaultRate(rate);

        try {
            masterService.addProductName(selectedType.getId(), pn);

            clearNameFields();
            lblNameStatus.setText("✔ Name '" + name + "' added.");
            refreshNames();

        } catch (Exception ex) {
            lblNameStatus.setText("⚠ " + ex.getMessage());
        }


    }
    private BigDecimal parseDecimal(String val) {
        try {
            return (val == null || val.trim().isEmpty())
                    ? BigDecimal.ZERO
                    : new BigDecimal(val.trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private void setupLiveValidation() {
        for (TextField tf : new TextField[]{
                tfNameValue, tfPick, tfLength, tfWidth, tfTotalPicks, tfReed, tfReedSpace}) {
            tf.textProperty().addListener((obs, o, n) -> validateForm());
        }
    }

    private boolean validateForm() {
        boolean valid = validateRequired(tfNameValue)
                & validateNumber(tfPick)   & validateNumber(tfLength)
                & validateNumber(tfWidth)  & validateNumber(tfTotalPicks)
                & validateNumber(tfReed)   & validateNumber(tfReedSpace);
        btnAddName.setDisable(!valid);
        return valid;
    }

    // ════════════════════════════════════════════════════════════
    //  EDIT DIALOGS  — use GlobalUI.boldLabel() / inputField()
    // ════════════════════════════════════════════════════════════

    private void openNameEditDialog(ProductName pn) {
        Dialog<ProductName> dialog = new Dialog<>();
        dialog.setTitle("Edit Product Entry");
        dialog.setHeaderText("Edit — " + GlobalUI.safeStr(pn.getName()));

        ButtonType saveBtn = new ButtonType("💾 Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
        dialog.getDialogPane().setMinWidth(440);

        GridPane grid = new GridPane();
        grid.setHgap(14); grid.setVgap(10);
        grid.setStyle("-fx-padding:18;");

        // GlobalUI.inputField() — no local field() helper needed
        TextField fName       = GlobalUI.inputField(GlobalUI.safeStr(pn.getName()), 260);
        TextField fPick       = GlobalUI.inputField(pn.getPick() != null ? String.valueOf(pn.getPick()) : "", 260);
        TextField fLength     = GlobalUI.inputField(formatDouble(pn.getLength()), 260);
        TextField fWidth      = GlobalUI.inputField(formatDouble(pn.getWidth()), 260);
        TextField fTotalPicks = GlobalUI.inputField(pn.getTotalPicks() != null ? String.valueOf(pn.getTotalPicks()) : "", 260);
        TextField fReed       = GlobalUI.inputField(pn.getReed() != null ? String.valueOf(pn.getReed()) : "", 260);
        TextField fReedSpace  = GlobalUI.inputField(formatDouble(pn.getReedSpace()), 260);
        TextField fPaisa = GlobalUI.inputField(pn.getDefaultPaisa() != null ? pn.getDefaultPaisa().toString() : "", 260);
        TextField fRate = GlobalUI.inputField(pn.getDefaultRate() != null ? pn.getDefaultRate().toString() : "", 260);
        // GlobalUI.boldLabel() — no local bold() helper needed
        grid.addRow(0, GlobalUI.boldLabel("Product Name:"), fName);
        grid.addRow(1, GlobalUI.boldLabel("Pick:"),         fPick);
        grid.addRow(2, GlobalUI.boldLabel("Length:"),       fLength);
        grid.addRow(3, GlobalUI.boldLabel("Width:"),        fWidth);
        grid.addRow(4, GlobalUI.boldLabel("Total Picks:"),  fTotalPicks);
        grid.addRow(5, GlobalUI.boldLabel("Reed:"),         fReed);
        grid.addRow(6, GlobalUI.boldLabel("Reed Space:"),   fReedSpace);
        grid.addRow(7, GlobalUI.boldLabel("Paisa:"), fPaisa);
        grid.addRow(8, GlobalUI.boldLabel("Rate:"),  fRate);
        dialog.getDialogPane().setContent(grid);
        javafx.application.Platform.runLater(fName::requestFocus);

        dialog.setResultConverter(btn -> {
            if (btn != saveBtn) return null;
            String newName = fName.getText().trim();
            if (newName.isEmpty()) { GlobalUI.warn("Product Name cannot be empty."); return null; }
            pn.setName(newName);
            applyFields(pn,
                    fPick, fLength, fWidth, fTotalPicks, fReed, fReedSpace);
            // 🔥🔥 THIS IS YOUR FIX
            pn.setDefaultPaisa(parseDecimal(fPaisa.getText()));
            pn.setDefaultRate(parseDecimal(fRate.getText()));

            return pn;
        });

        dialog.showAndWait().ifPresent(updated -> {
            try {
                masterService.updateProductName(updated);
                lblNameStatus.setText("✔ Updated: " + updated.getName());
                refreshNames();
            } catch (Exception ex) { GlobalUI.warn("Save failed: " + ex.getMessage()); }
        });
    }

    private void openTypeEditDialog(ProductType pt) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Edit Product Type");
        dialog.setHeaderText("Rename: " + pt.getName());

        ButtonType saveBtn = new ButtonType("💾 Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        TextField tf = GlobalUI.inputField(pt.getName(), 280);
        tf.setStyle("-fx-padding:8 12;");

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.setStyle("-fx-padding:16;");
        grid.addRow(0, GlobalUI.boldLabel("Type Name:"), tf);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setMinWidth(380);
        javafx.application.Platform.runLater(tf::requestFocus);

        dialog.setResultConverter(btn -> {
            if (btn != saveBtn) return null;

            String newName = tf.getText().trim();

            if (newName.isEmpty()) {
                GlobalUI.warn("Type name cannot be empty.");
                return null;
            }

            return newName;
        });

        dialog.showAndWait().ifPresent(newName -> {
            try {
                masterService.updateProductType(pt.getId(), newName);
                GlobalUI.success("Updated successfully");
                refreshTypes();
            } catch (Exception ex) {
                GlobalUI.warn("Update failed: " + ex.getMessage());
            }
        });
    }

    // ════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════

    private void refreshTypes() { types.setAll(masterService.findAllTypes()); }

    private void refreshNames() {
        if (selectedType == null) { names.clear(); return; }
        names.setAll(masterService.getProductNames(selectedType.getId()));
    }

    private void setNamePanelDisabled(boolean disabled) {
        for (Control c : new Control[]{
                tfNameValue, tfPick, tfLength, tfWidth,
                tfTotalPicks, tfReed, tfReedSpace, btnAddName}) {
            if (c != null) c.setDisable(disabled);
        }
    }

    /** Read TextFields → parse → apply to entity. */
    private void applyFields(ProductName pn,
                             TextField fPick, TextField fLength, TextField fWidth,
                             TextField fTotalPicks, TextField fReed, TextField fReedSpace) {
        pn.setPick(parseInteger(fPick.getText().trim()));
        pn.setLength(parseDouble(fLength.getText().trim()));
        pn.setWidth(parseDouble(fWidth.getText().trim()));
        pn.setTotalPicks(parseInteger(fTotalPicks.getText().trim()));
        pn.setReed(parseInteger(fReed.getText().trim()));
        pn.setReedSpace(parseDouble(fReedSpace.getText().trim()));
    }

    private String formatDouble(Double v) {
        if (v == null) return "";
        return new BigDecimal(v.toString()).stripTrailingZeros().toPlainString();
    }

    private Integer parseInteger(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException ignored) { return null; }
    }

    private Double parseDouble(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException ignored) { return null; }
    }

    private void clearNameFields() {
        tfNameValue.clear(); tfPick.clear();       tfLength.clear();
        tfWidth.clear();     tfTotalPicks.clear(); tfReed.clear();
        tfReedSpace.clear();tfPaisa.clear();
        tfRate.clear();
        tfNameValue.requestFocus();
    }
}