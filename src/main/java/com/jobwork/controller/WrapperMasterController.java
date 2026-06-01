package com.jobwork.controller;

import com.jobwork.domain.Wrapper;
import com.jobwork.repository.WrapperRepository;
import com.jobwork.util.GlobalUI;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

@Component
public class WrapperMasterController implements Initializable {

    @Autowired private WrapperRepository wrapperRepo;

    @FXML private TextField  tfName;
    @FXML private TextField  tfSearch;
    @FXML private Label      lblStatus;
    @FXML private Label      lblCount;

    @FXML private TableView<Wrapper>           tblWrappers;
    @FXML private TableColumn<Wrapper, Long>   colId;
    @FXML private TableColumn<Wrapper, String> colName;
    @FXML private TableColumn<Wrapper, Void>   colEdit;
    @FXML private TableColumn<Wrapper, Void>   colDelete;

    private ObservableList<Wrapper> masterList;
    private FilteredList<Wrapper>   filteredList;
    private Wrapper editingWrapper = null;

    // ════════════════════════════════════════════════════════════
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupColumns();
        refresh();
    }

    private void setupColumns() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));

        // ── Exact same pattern as UnitMasterController ────────────
        colEdit.setCellFactory(
                GlobalUI.<Wrapper>actionColumn("EDIT", 120, true,
                                this::loadForEdit)
                        .getCellFactory());

        colDelete.setCellFactory(
                GlobalUI.<Wrapper>actionColumn("DELETE", 130, false,
                                this::confirmDelete)
                        .getCellFactory());
    }

    // ════════════════════════════════════════════════════════════
    //  SAVE — handles both NEW and EDIT mode
    // ════════════════════════════════════════════════════════════
    @FXML
    public void onSave() {
        String name = tfName.getText() != null
                ? tfName.getText().trim() : "";
        if (name.isEmpty()) {
            GlobalUI.warn("Wrapper name cannot be empty"); return;
        }
        if (editingWrapper == null) {
            if (wrapperRepo.existsByNameIgnoreCase(name)) {
                GlobalUI.warn("'" + name + "' already exists"); return;
            }
            wrapperRepo.save(Wrapper.builder().name(name).build());
            GlobalUI.success("'" + name + "' added successfully");
        } else {
            editingWrapper.setName(name);
            wrapperRepo.save(editingWrapper);
            GlobalUI.success("'" + name + "' updated successfully");
            editingWrapper = null;
        }
        onClear();
        refresh();
    }

    // ════════════════════════════════════════════════════════════
    //  EDIT — load into form
    // ════════════════════════════════════════════════════════════
    private void loadForEdit(Wrapper w) {
        editingWrapper = w;
        tfName.setText(w.getName());
        tfName.requestFocus();
        GlobalUI.success("Editing: '" + w.getName() + "'");
    }

    // ════════════════════════════════════════════════════════════
    //  DELETE — confirm then remove
    // ════════════════════════════════════════════════════════════
    private void confirmDelete(Wrapper w) {
        String msg = "Delete wrapper '" + w.getName() + "'?\n"
                + "This will also affect Bheem entries using this wrapper.";
        if (GlobalUI.confirm("Delete Wrapper", msg)) {
            wrapperRepo.deleteById(w.getId());
            GlobalUI.success("'" + w.getName() + "' deleted.");
            if (editingWrapper != null
                    && editingWrapper.getId().equals(w.getId())) {
                editingWrapper = null;
                onClear();
            }
            refresh();
        }
    }

    // ════════════════════════════════════════════════════════════
    //  LIVE SEARCH
    // ════════════════════════════════════════════════════════════
    @FXML
    public void onSearch() {
        String keyword = tfSearch.getText() != null
                ? tfSearch.getText().trim().toLowerCase() : "";
        filteredList.setPredicate(w ->
                keyword.isEmpty()
                        || w.getName().toLowerCase().contains(keyword));
        lblCount.setText("Total: " + filteredList.size());
    }

    // ════════════════════════════════════════════════════════════
    //  CLEAR
    // ════════════════════════════════════════════════════════════
    @FXML
    public void onClear() {
        tfName.clear();
        editingWrapper = null;
        lblStatus.setText("");
    }

    // ════════════════════════════════════════════════════════════
    //  REFRESH
    // ════════════════════════════════════════════════════════════
    private void refresh() {
        List<Wrapper> all = wrapperRepo.findAll(
                org.springframework.data.domain.Sort.by("name"));
        masterList   = FXCollections.observableArrayList(all);
        filteredList = new FilteredList<>(masterList, w -> true);
        tblWrappers.setItems(filteredList);
        lblCount.setText("Total: " + masterList.size());
        onSearch();
    }
}