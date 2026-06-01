package com.jobwork.controller;

import com.jobwork.domain.BheemName;
import com.jobwork.domain.Taar;
import com.jobwork.repository.BheemNameRepository;
import com.jobwork.repository.TaarRepository;
import com.jobwork.util.GlobalUI;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.ResourceBundle;

@Component
public class BheemMasterController implements Initializable {

    @Autowired private BheemNameRepository bheemNameRepo;
    @Autowired private TaarRepository      taarRepo;

    // ── Left panel ─────────────────────────────────────────────
    @FXML private TextField tfBheemName;
    @FXML private Label     lblBheemStatus;
    @FXML private Label     lblBheemCount;

    @FXML private TableView<BheemName>            tblBheemNames;
    @FXML private TableColumn<BheemName, Integer> colBheemId;
    @FXML private TableColumn<BheemName, String>  colBheemName;
    @FXML private TableColumn<BheemName, Void>    colBheemEdit;
    @FXML private TableColumn<BheemName, Void>    colBheemDel;

    // ── Right panel ────────────────────────────────────────────
    @FXML private TextField tfTaarValue;
    @FXML private Label     lblSelectedBheem;
    @FXML private Label     lblTaarStatus;
    @FXML private Label     lblTaarCount;

    @FXML private TableView<Taar>            tblTaars;
    @FXML private TableColumn<Taar, Integer> colTaarId;
    @FXML private TableColumn<Taar, Integer> colTaarValue;
    @FXML private TableColumn<Taar, Void>    colTaarEdit;
    @FXML private TableColumn<Taar, Void>    colTaarDel;

    // ── State ──────────────────────────────────────────────────
    private BheemName selectedBheemName = null;
    private BheemName editingBheemName  = null;
    private Taar      editingTaar       = null;

    // ════════════════════════════════════════════════════════════
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupBheemColumns();
        setupTaarColumns();
        refreshBheemNames();
    }

    // ════════════════════════════════════════════════════════════
    //  LEFT PANEL — BheemName CRUD
    // ════════════════════════════════════════════════════════════

    private void setupBheemColumns() {
        colBheemId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colBheemName.setCellValueFactory(new PropertyValueFactory<>("name"));

        // ── Exact same pattern as UnitMasterController ────────────
        colBheemEdit.setCellFactory(
                GlobalUI.<BheemName>actionColumn("EDIT", 120, true,
                                this::loadBheemForEdit)
                        .getCellFactory());

        colBheemDel.setCellFactory(
                GlobalUI.<BheemName>actionColumn("DELETE", 130, false,
                                this::confirmDeleteBheem)
                        .getCellFactory());
    }

    @FXML
    public void onSaveBheemName() {
        String name = tfBheemName.getText() != null
                ? tfBheemName.getText().trim() : "";
        if (name.isEmpty()) { GlobalUI.warn("Name cannot be empty"); return; }

        if (editingBheemName == null) {
            if (bheemNameRepo.existsByName(name)) {
                GlobalUI.warn("'" + name + "' already exists."); return;
            }
            bheemNameRepo.save(BheemName.builder().name(name).build());
            GlobalUI.success("'" + name + "' added.");
        } else {
            editingBheemName.setName(name);
            bheemNameRepo.save(editingBheemName);
            GlobalUI.success("'" + name + "' updated.");
            editingBheemName = null;
        }
        onClearBheemName();
        refreshBheemNames();
    }

    @FXML
    public void onBheemNameSelected() {
        BheemName sel = tblBheemNames.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        selectedBheemName = sel;
        lblSelectedBheem.setText("Taar values for: " + sel.getName());
        lblSelectedBheem.setStyle("-fx-text-fill:#1a237e;-fx-font-weight:bold;");
        refreshTaars();
    }

    private void loadBheemForEdit(BheemName b) {
        editingBheemName = b;
        tfBheemName.setText(b.getName());
        tfBheemName.requestFocus();
        GlobalUI.success("Editing '" + b.getName() + "' — change and click Save.");
    }

    private void confirmDeleteBheem(BheemName b) {
        long taarCount = taarRepo
                .findByBheemNameIdOrderByValueAsc(b.getId()).size();
        String msg = taarCount > 0
                ? "Delete '" + b.getName() + "'?\n"
                + taarCount + " taar(s) will also be deleted."
                : "Delete '" + b.getName() + "'?";
        if (GlobalUI.confirm("Delete Bheem", msg)) {
            bheemNameRepo.deleteById(b.getId());
            GlobalUI.success("'" + b.getName() + "' deleted");
            if (selectedBheemName != null
                    && selectedBheemName.getId().equals(b.getId())) {
                selectedBheemName = null;
                tblTaars.setItems(FXCollections.emptyObservableList());
                lblTaarCount.setText("Total: 0");
            }
            refreshBheemNames();
        }
    }

    @FXML
    public void onClearBheemName() {
        tfBheemName.clear();
        editingBheemName = null;
        lblBheemStatus.setText("");
    }

    private void refreshBheemNames() {
        ObservableList<BheemName> list = FXCollections.observableArrayList(
                bheemNameRepo.findAllByOrderByNameAsc());
        tblBheemNames.setItems(list);
        lblBheemCount.setText("Total: " + list.size());
    }

    // ════════════════════════════════════════════════════════════
    //  RIGHT PANEL — Taar CRUD
    // ════════════════════════════════════════════════════════════

    private void setupTaarColumns() {
        colTaarId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colTaarValue.setCellValueFactory(new PropertyValueFactory<>("value"));

        // ── Exact same pattern as UnitMasterController ────────────
        colTaarEdit.setCellFactory(
                GlobalUI.<Taar>actionColumn("EDIT", 120, true,
                                this::loadTaarForEdit)
                        .getCellFactory());

        colTaarDel.setCellFactory(
                GlobalUI.<Taar>actionColumn("DELETE", 130, false,
                                this::confirmDeleteTaar)
                        .getCellFactory());
    }

    @FXML
    public void onAddTaar() {
        if (selectedBheemName == null) {
            GlobalUI.warn("Select a bheem name on the left first."); return;
        }
        String raw = tfTaarValue.getText() != null
                ? tfTaarValue.getText().trim() : "";
        if (raw.isEmpty()) { GlobalUI.warn("Enter a taar value."); return; }

        int value;
        try {
            value = Integer.parseInt(raw);
            if (value <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            GlobalUI.warn("Enter a valid positive integer."); return;
        }

        if (editingTaar == null) {
            if (taarRepo.existsByBheemNameIdAndValue(
                    selectedBheemName.getId(), value)) {
                GlobalUI.warn("Taar " + value + " already exists for "
                        + selectedBheemName.getName() + "."); return;
            }
            taarRepo.save(Taar.builder()
                    .bheemName(selectedBheemName).value(value).build());
            GlobalUI.success("Taar " + value + " added.");
        } else {
            editingTaar.setValue(value);
            taarRepo.save(editingTaar);
            GlobalUI.success("Taar " + value + " updated.");
            editingTaar = null;
        }
        onClearTaar();
        refreshTaars();
    }

    private void loadTaarForEdit(Taar t) {
        editingTaar = t;
        tfTaarValue.setText(String.valueOf(t.getValue()));
        tfTaarValue.requestFocus();
        GlobalUI.warn("Editing Taar " + t.getValue()
                + " — change and click Add Taar.");
    }

    private void confirmDeleteTaar(Taar t) {
        if (GlobalUI.confirm("Delete Taar",
                "Delete Taar " + t.getValue() + "?")) {
            taarRepo.deleteById(t.getId());
            GlobalUI.success("Deleted successfully");
            refreshTaars();
        }
    }

    @FXML
    public void onClearTaar() {
        tfTaarValue.clear();
        editingTaar = null;
        lblTaarStatus.setText("");
    }

    private void refreshTaars() {
        if (selectedBheemName == null) return;
        ObservableList<Taar> list = FXCollections.observableArrayList(
                taarRepo.findByBheemNameIdOrderByValueAsc(
                        selectedBheemName.getId()));
        tblTaars.setItems(list);
        lblTaarCount.setText("Total: " + list.size());
    }
}