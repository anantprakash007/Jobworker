package com.jobwork.controller;

import com.jobwork.domain.DeliveryLocation;
import com.jobwork.domain.Unit;
import com.jobwork.domain.YarnType;
import com.jobwork.repository.DeliveryLocationRepository;
import com.jobwork.repository.UnitRepository;
import com.jobwork.repository.YarnTypeRepository;
import com.jobwork.util.GlobalUI;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.ResourceBundle;

@Component
public class UnitMasterController implements Initializable {

    @Autowired private UnitRepository             unitRepo;
    @Autowired private YarnTypeRepository         yarnTypeRepo;
    @Autowired private DeliveryLocationRepository locationRepo;

    @FXML private TextField tfUnit, tfYarnType, tfLocation;

    @FXML private TableView<Unit>               tblUnits;
    @FXML private TableColumn<Unit, String>     colUnitName;
    @FXML private TableColumn<Unit, Void>       colUnitDel;

    @FXML private TableView<YarnType>           tblYarnTypes;
    @FXML private TableColumn<YarnType, String> colYarnName;
    @FXML private TableColumn<YarnType, Void>   colYarnDel;

    @FXML private TableView<DeliveryLocation>           tblLocations;
    @FXML private TableColumn<DeliveryLocation, String> colLocName;
    @FXML private TableColumn<DeliveryLocation, Void>   colLocDel;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // 🔥 ADD THIS BLOCK FIRST
        tblUnits.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tblYarnTypes.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        tblLocations.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        colUnitName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colYarnName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colLocName.setCellValueFactory(new PropertyValueFactory<>("name"));

        // ── GlobalUI.actionColumn() — no local style constants needed ──
        colUnitDel.setCellFactory(
                GlobalUI.<Unit>actionColumn("DELETE", 130, false, u -> {
                    if (GlobalUI.confirm("Delete Unit", "Delete Unit '" + u.getName() + "'?")) {
                        unitRepo.deleteById(u.getId());
                        GlobalUI.success("Deleted successfully");
                        refreshAll();
                    }
                }).getCellFactory());

        colYarnDel.setCellFactory(
                GlobalUI.<YarnType>actionColumn("DELETE", 130, false, y -> {
                    if (GlobalUI.confirm("Delete Yarn Type",
                            "Delete YarnType '" + y.getName() + "'?")) {
                        yarnTypeRepo.deleteById(y.getId());
                        GlobalUI.success("Deleted successfully");
                        refreshAll();
                    }
                }).getCellFactory());

        colLocDel.setCellFactory(
                GlobalUI.<DeliveryLocation>actionColumn("DELETE", 130, false, l -> {
                    if (GlobalUI.confirm("Delete Location",
                            "Delete Location '" + l.getName() + "'?")) {
                        locationRepo.deleteById(l.getId());
                        GlobalUI.success("Deleted successfully");
                        refreshAll();
                    }
                }).getCellFactory());

        refreshAll();
    }

    @FXML
    public void onAddUnit() {
        String v = tfUnit.getText().trim();
        if (v.isEmpty()) return;
        if (unitRepo.existsByNameIgnoreCase(v)) {
            GlobalUI.warn("Unit already exists"); return;
        }
        unitRepo.save(Unit.builder().name(v).build());
        tfUnit.clear();
        GlobalUI.success("Unit added successfully");
        refreshAll();
    }

    @FXML
    public void onAddYarnType() {
        String v = tfYarnType.getText().trim();
        if (v.isEmpty()) return;
        if (yarnTypeRepo.existsByNameIgnoreCase(v)) {
            GlobalUI.warn("Yarn type already exists."); return;
        }
        yarnTypeRepo.save(YarnType.builder().name(v).build());
        tfYarnType.clear();
        GlobalUI.success("Yarn added successfully");
        refreshAll();
    }

    @FXML
    public void onAddLocation() {
        String v = tfLocation.getText().trim();
        if (v.isEmpty()) return;
        if (locationRepo.existsByNameIgnoreCase(v)) {
            GlobalUI.warn("Location already exists."); return;
        }
        locationRepo.save(DeliveryLocation.builder().name(v).build());
        tfLocation.clear();
        GlobalUI.success("DeliveryLocation added successfully");
        refreshAll();
    }

    private void refreshAll() {
        tblUnits.setItems(FXCollections.observableArrayList(unitRepo.findAll()));
        tblYarnTypes.setItems(FXCollections.observableArrayList(yarnTypeRepo.findAll()));
        tblLocations.setItems(FXCollections.observableArrayList(locationRepo.findAll()));
    }
}