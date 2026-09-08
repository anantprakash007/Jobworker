package com.jobwork.controller;

import com.jobwork.config.FxmlView;
import com.jobwork.domain.*;
import com.jobwork.service.*;
import com.jobwork.util.*;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.jobwork.util.JobWorkerComboBoxUtil;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class YarnEntryController implements Initializable {

    private static final LinkedHashMap<String, BigDecimal> BAG_WT = new LinkedHashMap<>();
    static {
        BAG_WT.put("50 kg", new BigDecimal("50"));
        BAG_WT.put("60 kg", new BigDecimal("60"));
        BAG_WT.put("70 kg", new BigDecimal("70"));
        BAG_WT.put("75 kg", new BigDecimal("75"));
        BAG_WT.put("90 kg", new BigDecimal("90"));
    }


    // ── Services ──────────────────────────────────────────────────
    @Autowired private JobWorkerService jobWorkerService;
    @Autowired private MasterService    masterService;
    @Autowired private YarnService      yarnService;
    @Autowired private PdfExporter      pdfExporter;
    @Autowired private ExcelExporter    excelExporter;
    @Autowired private FileUploadUtil   fileUploadUtil;
    @Autowired private com.jobwork.config.StageManager stageManager;

    // ── NEW: needed to open the import preview with Spring-managed controller
    @Autowired private org.springframework.context.ApplicationContext appContext;

    // ── Card 1: Header ────────────────────────────────────────────
    @FXML private ComboBox<JobWorker>        cbWorker;
    @FXML private TextField tfWorkerId;
    @FXML private ComboBox<DeliveryLocation> cbLocation;
    @FXML private TextField                  tfChallan;
    @FXML private DatePicker                 dpDate;

    // ── Card 2: Yarn detail ───────────────────────────────────────
    @FXML private ComboBox<String>   cbBagPiece;
    @FXML private HBox               wtOfBagsRow;
    @FXML private ComboBox<String>   cbWtOfBags;
    @FXML private ComboBox<YarnType> cbYarnCount;
    @FXML private TextField          tfColour;
    @FXML private TextField          tfNoOfBags;
    @FXML private TextField          tfNoOfCones;
    @FXML private TextField          tfNetWeight;
    @FXML private Label              lblReceiptFile;
    @FXML private Button             btnViewReceipt;

    // ── Table ─────────────────────────────────────────────────────
    @FXML private TableView<YarnRow>           tblYarn;
    @FXML private TableColumn<YarnRow, String> colSlNo;
    @FXML private TableColumn<YarnRow, String> colBagPiece;
    @FXML private TableColumn<YarnRow, String> colWtOfBags;
    @FXML private TableColumn<YarnRow, String> colYarnCount;
    @FXML private TableColumn<YarnRow, String> colColour;
    @FXML private TableColumn<YarnRow, String> colNoOfBags;
    @FXML private TableColumn<YarnRow, String> colNoOfCones;
    @FXML private TableColumn<YarnRow, String> colNetWeight;

    // ── Summary bar ───────────────────────────────────────────────
    @FXML private Label lblEntryBadge;
    @FXML private Label lblTotalQty;
    @FXML private Label lblTotalWeight;
    @FXML private Label lblTotalBags;
    @FXML private Label lblTotalCones;
    @FXML private Label lblRowCount;



    // ── Yarn-wise summary ─────────────────────────────────────────
    @FXML private VBox                              summarySection;
    @FXML private TableView<YarnSummaryRow>         tblSummary;
    @FXML private TableColumn<YarnSummaryRow, String> sumColYarnCount;
    @FXML private TableColumn<YarnSummaryRow, String> sumColBagPiece;
    @FXML private TableColumn<YarnSummaryRow, Number> sumColTotalBags;
    @FXML private TableColumn<YarnSummaryRow, Number> sumColTotalCones;
    @FXML private TableColumn<YarnSummaryRow, String> sumColTotalWeight;
    @FXML private Button btnAddRow;
    // ── State ─────────────────────────────────────────────────────
    private final ObservableList<YarnRow>        rows        = FXCollections.observableArrayList();
    private final ObservableList<YarnSummaryRow> summaryRows = FXCollections.observableArrayList();
    private int    editingRowIndex      = -1;
    private boolean suppressAutoCalc   = false;
    private String  uploadedReceiptPath;

    // ════════════════════════════════════════════════════════════
    //  INITIALIZE  (unchanged from original)
    // ════════════════════════════════════════════════════════════
    @Override
    public void initialize(URL url, ResourceBundle rb) {

        FormUtil.autoSelect(tfChallan);
        FormUtil.autoSelect(tfNoOfBags);
        FormUtil.autoSelect(tfNoOfCones);
        FormUtil.autoSelect(tfNetWeight);
        FormUtil.allowNumeric(tfNoOfBags, false);
        FormUtil.allowNumeric(tfNoOfCones, false);
        FormUtil.allowNumeric(tfNetWeight, true);
        FormUtil.allowDecimal(tfNetWeight, 3);
        setupAddButtonValidation();
        setupWorkerAutoFetch();
        // 🔥 FIX: force recalculation always
        // 🔥 FIX: handle both dropdown + typing
        cbWtOfBags.valueProperty().addListener((obs, old, val) -> calcNetWeight());

        if (cbWtOfBags.getEditor() != null) {
            cbWtOfBags.getEditor().textProperty().addListener((obs, old, val) -> calcNetWeight());
        }
        tfNoOfBags.textProperty().addListener((obs, old, val) -> calcNetWeight());
        cbBagPiece.valueProperty().addListener((obs, old, val) -> calcNetWeight());

        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        dpDate.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(LocalDate d) { return d != null ? df.format(d) : ""; }
            @Override public LocalDate fromString(String s) {
                try { return (s == null || s.isBlank()) ? null : LocalDate.parse(s, df); }
                catch (Exception e) { return null; }
            }
        });

       // cbWorker.setItems(FXCollections.observableArrayList(jobWorkerService.findAll()));
        JobWorkerComboBoxUtil.setup(
                cbWorker,
                jobWorkerService.findAll()
        );
        cbLocation.setItems(FXCollections.observableArrayList(masterService.findAllLocations()));
        cbYarnCount.setItems(FXCollections.observableArrayList(masterService.findAllYarnTypes()));
        cbBagPiece.setItems(FXCollections.observableArrayList("BAG", "PIECE"));
        cbWtOfBags.setItems(FXCollections.observableArrayList(new ArrayList<>(BAG_WT.keySet())));
        cbWtOfBags.setEditable(false);
        dpDate.setValue(LocalDate.now());

        tfNoOfBags.setDisable(true);
        tfNoOfCones.setDisable(true);
        if (wtOfBagsRow != null) { wtOfBagsRow.setManaged(false); wtOfBagsRow.setVisible(false); }

        // Table columns
        colSlNo.setCellValueFactory(c ->
                new SimpleStringProperty(String.valueOf(rows.indexOf(c.getValue()) + 1)));
        colBagPiece.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getBagPiece() != null ? c.getValue().getBagPiece() : ""));
        colWtOfBags.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getWtOfBags() != null ? c.getValue().getWtOfBags() : "—"));
        colYarnCount.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getYarnTypeName() != null ? c.getValue().getYarnTypeName() : ""));
        colColour.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getColour() != null ? c.getValue().getColour() : ""));
        colNoOfBags.setCellValueFactory(c -> {
            YarnRow r = c.getValue();
            return new SimpleStringProperty("BAG".equalsIgnoreCase(r.getBagPiece())
                    ? String.valueOf(r.getNoOfBags()) : "—");
        });
        colNoOfCones.setCellValueFactory(c -> {
            YarnRow r = c.getValue();
            return new SimpleStringProperty("PIECE".equalsIgnoreCase(r.getBagPiece())
                    ? String.valueOf(r.getNoOfCones()) : "—");
        });
        colNetWeight.setCellValueFactory(c -> new SimpleStringProperty(fmt2(c.getValue().getNetWeight())));

        tblYarn.setItems(rows);
        tfNoOfBags.textProperty().addListener((obs, old, val) -> calcNetWeight());
        tfNoOfCones.textProperty().addListener((obs, old, val) -> calcNetWeight());
        cbWtOfBags.valueProperty().addListener((obs, old, val) -> calcNetWeight());
// 🔥 FINAL FIX: ALWAYS trigger on change
        cbBagPiece.valueProperty().addListener((obs, old, val) -> onBagPieceChanged());

        // Summary table columns
        sumColYarnCount.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getYarnCount()));
        sumColBagPiece.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getBagPiece()));
        sumColTotalBags.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getTotalBags()));
        sumColTotalCones.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getTotalCones()));
        sumColTotalWeight.setCellValueFactory(c -> new SimpleStringProperty(fmt2(c.getValue().getTotalWeight())));
        tblSummary.setItems(summaryRows);
        cbBagPiece.setValue("BAG");

// 🔥 ensure UI fully loaded before applying
        javafx.application.Platform.runLater(this::onBagPieceChanged);

        addEditColumn();
        addDeleteColumn();

        FormUtil.moveNext(tfChallan, cbWorker);
        FormUtil.moveNextCombo(cbWorker, cbLocation);
        FormUtil.moveNextCombo(cbLocation, dpDate.getEditor());
        FormUtil.moveNext(dpDate.getEditor(), cbBagPiece);
        FormUtil.moveNextCombo(cbBagPiece, cbWtOfBags);
        FormUtil.moveNextCombo(cbWtOfBags, cbYarnCount);
        FormUtil.moveNextCombo(cbYarnCount, tfColour);
        FormUtil.moveNext(tfColour, tfNoOfBags);
        FormUtil.moveNext(tfNoOfBags, tfNoOfCones);
        FormUtil.moveNext(tfNoOfCones, tfNetWeight);

        javafx.application.Platform.runLater(() -> tfChallan.requestFocus());
        tfNetWeight.setOnAction(e -> {
            onAddRow();
        });
      //  tfNetWeight.setOnAction(e -> { onAddRow(); cbBagPiece.requestFocus(); });

        setupLiveValidation();


//        cbWorker.setButtonCell(new ListCell<>() {
//            @Override
//            protected void updateItem(JobWorker item, boolean empty) {
//                super.updateItem(item, empty);
//                setText(empty || item == null
//                        ? null
//                        : item.getName() + " - " + item.getPhone());
//            }
//        });
//        cbWorker.setCellFactory(cb -> new ListCell<>() {
//            @Override
//            protected void updateItem(JobWorker item, boolean empty) {
//                super.updateItem(item, empty);
//                setText(empty || item == null
//                        ? null
//                        : item.getName() + " - " + item.getPhone());
//            }
//        });
    }
    private void setupWorkerAutoFetch() {

        if (tfWorkerId == null) return;

        tfWorkerId.textProperty().addListener((obs, old, val) -> {

            if (val == null || val.isBlank()) {
                cbWorker.getSelectionModel().clearSelection();
                FormUtil.clearError(tfWorkerId);
                return;
            }

            try {
                Long id = Long.parseLong(val.trim());

                JobWorker worker = cbWorker.getItems()
                        .stream()
                        .filter(w -> w.getId().equals(id))
                        .findFirst()
                        .orElse(null);

                if (worker != null) {
                    cbWorker.setValue(worker);
                    FormUtil.clearError(tfWorkerId);
                } else {
                    cbWorker.getSelectionModel().clearSelection();
                    FormUtil.setError(tfWorkerId, "Worker not found");
                }

            } catch (Exception e) {
                cbWorker.getSelectionModel().clearSelection();
                FormUtil.setError(tfWorkerId, "Invalid ID");
            }
        });

        // 🔥 REVERSE SYNC (VERY IMPORTANT UX)
        cbWorker.valueProperty().addListener((obs, old, val) -> {
            if (val != null) {
                tfWorkerId.setText(String.valueOf(val.getId()));
            }
        });
    }
    private void setupAddButtonValidation() {

        if (btnAddRow == null) return;

        btnAddRow.disableProperty().bind(
                cbWorker.valueProperty().isNull()
                        .or(cbLocation.valueProperty().isNull())
                        .or(tfChallan.textProperty().isEmpty())
                        .or(dpDate.valueProperty().isNull())
                        .or(cbBagPiece.valueProperty().isNull())
                        .or(cbYarnCount.valueProperty().isNull())
                        .or(tfColour.textProperty().isEmpty())
                        .or(tfNetWeight.textProperty().isEmpty())

                        // 🔥 BAG validation
                        .or(
                                cbBagPiece.valueProperty().isEqualTo("BAG")
                                        .and(tfNoOfBags.textProperty().isEmpty())
                        )

                        // 🔥 PIECE validation
                        .or(
                                cbBagPiece.valueProperty().isEqualTo("PIECE")
                                        .and(tfNoOfCones.textProperty().isEmpty())
                        )
        );
    }

    private void setupLiveValidation() {
        tfChallan.textProperty().addListener((o,a,b)->FormUtil.clearError(tfChallan));
        tfNetWeight.textProperty().addListener((o,a,b)->FormUtil.clearError(tfNetWeight));
        tfNoOfBags.textProperty().addListener((o,a,b)->FormUtil.clearError(tfNoOfBags));
        tfNoOfCones.textProperty().addListener((o,a,b)->FormUtil.clearError(tfNoOfCones));
        cbWorker.valueProperty().addListener((o,a,b)->FormUtil.clearError(cbWorker));
        cbLocation.valueProperty().addListener((o,a,b)->FormUtil.clearError(cbLocation));
        cbBagPiece.valueProperty().addListener((o,a,b)->FormUtil.clearError(cbBagPiece));
        cbYarnCount.valueProperty().addListener((o,a,b)->FormUtil.clearError(cbYarnCount));
        cbWtOfBags.valueProperty().addListener((o,a,b)->FormUtil.clearError(cbWtOfBags));
    }

    // ════════════════════════════════════════════════════════════
    //  BAG / PIECE CHANGED
    // ════════════════════════════════════════════════════════════

    @FXML
    public void onBagPieceChanged() {

        String sel = cbBagPiece.getValue();
        boolean isBag = "BAG".equalsIgnoreCase(sel);

        // ✅ ENABLE / DISABLE
        tfNoOfBags.setDisable(!isBag);
        tfNoOfCones.setDisable(isBag);
        cbWtOfBags.setDisable(!isBag);

        // 🔥 SHOW / HIDE WT OF BAGS ROW (IMPORTANT FIX)
        if (wtOfBagsRow != null) {
            wtOfBagsRow.setManaged(isBag);
            wtOfBagsRow.setVisible(isBag);
        }

        // 🔥 CLEAR WRONG FIELD
        if (isBag) {
            tfNoOfCones.clear();
        } else {
            tfNoOfBags.clear();
            cbWtOfBags.getSelectionModel().clearSelection();
        }

        // 🔥 NET WEIGHT BEHAVIOR
        tfNetWeight.setDisable(isBag); // BAG = auto, PIECE = manual

        // 🔥 AUTO FOCUS
        if (isBag) {
            tfNoOfBags.requestFocus();
        } else {
            tfNoOfCones.requestFocus();
        }

        // 🔥 RECALCULATE
        calcNetWeight();
    }
    @FXML public void onWtOfBagsChanged() { calcNetWeight(); }
    @FXML public void onBagsChanged()     { calcNetWeight(); }

    private void calcNetWeight() {

        // only calculate for BAG
        if (!"BAG".equalsIgnoreCase(cbBagPiece.getValue())) {
            return;
        }

        String wtLabel = cbWtOfBags.getValue();
        if (wtLabel == null || wtLabel.isBlank()) return;

        BigDecimal wtPerBag = BAG_WT.getOrDefault(wtLabel.trim(), BigDecimal.ZERO);
        if (wtPerBag == null) return;

        int bags = parseInt(tfNoOfBags.getText());

        BigDecimal result = wtPerBag
                .multiply(BigDecimal.valueOf(bags))
                .setScale(2, RoundingMode.HALF_UP);

        tfNetWeight.setText(result.toPlainString());
    }
    // ════════════════════════════════════════════════════════════
    //  ADD ROW
    // ════════════════════════════════════════════════════════════
    @FXML public void onAddRow() {
        if (!FormUtil.validateRequired(cbWorker))   return;
        if (!FormUtil.validateRequired(cbLocation)) return;
        if (!FormUtil.validateRequired(tfChallan))  return;
        if (dpDate.getValue() == null) { FormUtil.setError(dpDate, "Select Date"); return; }

        ValidationUtil.start();
        ValidationUtil.required(cbWorker, "Worker required");
        ValidationUtil.required(cbLocation, "Location required");
        ValidationUtil.required(tfChallan, "Challan required");
        ValidationUtil.required(dpDate, "Select Date");
        ValidationUtil.required(cbBagPiece, "Select Bag/Piece");
        ValidationUtil.required(cbYarnCount, "Select Yarn");
        ValidationUtil.required(tfColour, "Colour required");

        boolean isBag = "BAG".equalsIgnoreCase(cbBagPiece.getValue());
        if (isBag) {
            ValidationUtil.required(cbWtOfBags, "Select Wt of Bags");
            ValidationUtil.number(tfNoOfBags, "Invalid bags");
        } else {
            ValidationUtil.number(tfNoOfCones, "Invalid cones");
        }
        ValidationUtil.number(tfNetWeight, "Invalid weight");
        if (!ValidationUtil.validate()) return;
// ── Build single entry ──
        YarnEntry entry = new YarnEntry();

        entry.setJobWorker(cbWorker.getValue());
        entry.setDeliveryLocation(cbLocation.getValue());
        entry.setChallanNo(tfChallan.getText().trim());
        entry.setEntryDate(dpDate.getValue());
        entry.setYarnType(cbYarnCount.getValue());
        entry.setColour(tfColour.getText().trim());
        entry.setBagPiece(cbBagPiece.getValue());
        entry.setWtOfBags(isBag ? cbWtOfBags.getValue() : null);
        entry.setNoOfBags(isBag ? parseInt(tfNoOfBags.getText()) : 0);
        entry.setNoOfCones(!isBag ? parseInt(tfNoOfCones.getText()) : 0);
        entry.setNetWeight(parseBd(tfNetWeight.getText()));

        List<YarnEntry> singleList = List.of(entry);

// ── DUPLICATE CHECK ──
        YarnDuplicateCheckResult result =
                yarnService.checkDuplicates(singleList);

// ── DUPLICATE FOUND ──
        if (result.hasDuplicates()) {

            // 🔴 ALERT (like Product)
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Duplicate Entry");
            alert.setHeaderText("Duplicate Yarn Entry Found");
            alert.setContentText(
                    "Duplicate found for:\n\n" +
                            "Worker: " + cbWorker.getValue().getName() + "\n" +
                            "Challan: " + tfChallan.getText() + "\n" +
                            "Yarn: " + cbYarnCount.getValue().getName() + "\n\n" +
                            "Please review before saving."
            );
            alert.showAndWait();

            openDuplicatePopup(result.rows(), () -> {

                if (!result.nonDuplicates().isEmpty()) {
                    yarnService.saveAll(result.nonDuplicates(), uploadedReceiptPath);
                }
            });
            GlobalUI.success("Duplicate review completed");
            clearInputFields();
            return;
        }

// ── NO DUPLICATE → ADD TO TABLE ──
        YarnRow row = new YarnRow();
        row.setSlNo(rows.size() + 1);
        row.setBagPiece(entry.getBagPiece());
        row.setWtOfBags(entry.getWtOfBags());
        row.setYarnType(entry.getYarnType());
        row.setYarnTypeName(entry.getYarnType().getName());
        row.setColour(entry.getColour());
        row.setNoOfBags(entry.getNoOfBags());
        row.setNoOfCones(entry.getNoOfCones());
        row.setNetWeight(entry.getNetWeight());;
        boolean exists = rows.stream().anyMatch(r ->
                Objects.equals(r.getYarnType(), entry.getYarnType()) &&
                        Objects.equals(
                                r.getColour() == null ? "" : r.getColour().toLowerCase(),
                                entry.getColour() == null ? "" : entry.getColour().toLowerCase()
                        ) &&
                        Objects.equals(r.getBagPiece(), entry.getBagPiece())
        );

        if (exists) {
            GlobalUI.warn("Duplicate row already added in table");
            return;
        }
        rows.add(row);

        refreshSummaryBar();
        rebuildYarnwiseSummary();
        showSummarySection(true);
        clearInputFields();
        cbYarnCount.requestFocus();
    }

    // ════════════════════════════════════════════════════════════
    //  IMPORT EXCEL  ← NEW METHOD
    // ════════════════════════════════════════════════════════════
    /**
     * Opens the Yarn Excel import preview window.
     * Wire to a Button in yarn_entry.fxml: onAction="#onImportExcel"
     */
    @FXML
    public void onImportExcel() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Excel File");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));

        File file = fc.showOpenDialog(
                tblYarn != null ? tblYarn.getScene().getWindow() : null);
        if (file == null) return;

        try {
            GlobalUI.openWindow(
                    "/fxml/yarn_import_preview.fxml",
                    "Yarn Import Preview",
                    controller ->
                            ((YarnImportPreviewController) controller).loadExcel(file));
        } catch (Exception ex) {
            GlobalUI.warn("Import failed: " + ex.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    //  EDIT + DELETE COLUMNS
    // ════════════════════════════════════════════════════════════
    private void addEditColumn() {
        TableColumn<YarnRow, Void> col = new TableColumn<>("Edit");
        col.setPrefWidth(72); col.setStyle("-fx-alignment:CENTER;");
        col.setCellFactory(c -> new TableCell<>() {
            final Button btn = new Button("✏ Edit");
            { btn.setStyle("-fx-background-color:linear-gradient(to right,#1e3a8a,#1d4ed8);" +
                    "-fx-text-fill:white;-fx-font-size:11px;-fx-font-weight:bold;" +
                    "-fx-padding:4 10;-fx-background-radius:6;-fx-cursor:hand;");
                btn.setOnAction(e -> { YarnRow item = (YarnRow) getTableRow().getItem();
                    if (item != null) openEditDialog(item); }); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty); setGraphic(empty ? null : btn); }
        });
        tblYarn.getColumns().add(col);
    }

    private void addDeleteColumn() {
        TableColumn<YarnRow, Void> col = new TableColumn<>("Del");
        col.setPrefWidth(65); col.setStyle("-fx-alignment:CENTER;");
        col.setCellFactory(c -> new TableCell<>() {
            final Button btn = new Button("🗑 Del");
            { btn.setStyle("-fx-background-color:linear-gradient(to right,#dc2626,#b91c1c);" +
                    "-fx-text-fill:white;-fx-font-size:11px;-fx-font-weight:bold;" +
                    "-fx-padding:4 10;-fx-background-radius:6;-fx-cursor:hand;");
                btn.setOnAction(e -> { YarnRow item = (YarnRow) getTableRow().getItem();
                    if (item == null) return;
                    if (GlobalUI.confirm("Delete Row","Delete this yarn entry row?")) {
                        rows.remove(item);
                        for (int i = 0; i < rows.size(); i++) rows.get(i).setSlNo(i + 1);
                        tblYarn.refresh(); refreshSummaryBar(); rebuildYarnwiseSummary();
                        if (rows.isEmpty()) showSummarySection(false); } }); }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty); setGraphic(empty ? null : btn); }
        });
        tblYarn.getColumns().add(col);
    }

    private void openEditDialog(YarnRow row) {
        Dialog<YarnRow> dialog = new Dialog<>();
        dialog.setTitle("Edit Yarn Row");
        dialog.setHeaderText("Editing: " + row.getYarnTypeName() + " — " + row.getColour());
        ButtonType saveBtn = new ButtonType("💾 Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        javafx.scene.layout.GridPane g = new javafx.scene.layout.GridPane();
        g.setHgap(12); g.setVgap(10); g.setStyle("-fx-padding:16;");

        ComboBox<String> dlgBP = new ComboBox<>(FXCollections.observableArrayList("BAG","PIECE"));
        dlgBP.setValue(row.getBagPiece());
        ComboBox<String> dlgWt = new ComboBox<>(FXCollections.observableArrayList(new ArrayList<>(BAG_WT.keySet())));
        dlgWt.setValue(row.getWtOfBags());
        TextField dlgColour = new TextField(row.getColour());
        TextField dlgBags   = new TextField(String.valueOf(row.getNoOfBags()));
        TextField dlgCones  = new TextField(String.valueOf(row.getNoOfCones()));
        TextField dlgNet    = new TextField(fmt2(row.getNetWeight()));

        boolean isBag0 = "BAG".equalsIgnoreCase(row.getBagPiece());
        dlgWt.setDisable(!isBag0); dlgBags.setDisable(!isBag0); dlgCones.setDisable(isBag0);
        dlgBP.setOnAction(e -> { boolean b = "BAG".equalsIgnoreCase(dlgBP.getValue());
            dlgWt.setDisable(!b); dlgBags.setDisable(!b); dlgCones.setDisable(b); });

        g.addRow(0, new Label("Bag/Piece:"),    dlgBP);
        g.addRow(1, new Label("Wt of Bags:"),   dlgWt);
        g.addRow(2, new Label("Colour:"),       dlgColour);
        g.addRow(3, new Label("No. of Bags:"),  dlgBags);
        g.addRow(4, new Label("No. of Cones:"), dlgCones);
        g.addRow(5, new Label("Net Wt (kg):"),  dlgNet);
        dialog.getDialogPane().setContent(g);
        dialog.getDialogPane().setMinWidth(380);

        dialog.setResultConverter(b -> {
            if (b == saveBtn) {
                try {
                    boolean bag = "BAG".equalsIgnoreCase(dlgBP.getValue());
                    row.setBagPiece(dlgBP.getValue());
                    row.setWtOfBags(bag ? dlgWt.getValue() : null);
                    row.setColour(dlgColour.getText().trim());
                    row.setNoOfBags(parseInt(dlgBags.getText()));
                    row.setNoOfCones(parseInt(dlgCones.getText()));
                    row.setNetWeight(parseBd(dlgNet.getText()));
                    return row;
                } catch (Exception ex) { GlobalUI.warn("Invalid input: " + ex.getMessage()); return null; }
            }
            return null;
        });
        dialog.showAndWait().ifPresent(r -> { GlobalUI.success("Row updated"); refreshSummaryBar(); rebuildYarnwiseSummary(); });
    }

    // ════════════════════════════════════════════════════════════
    //  SUMMARY
    // ════════════════════════════════════════════════════════════
    private void refreshSummaryBar() {
        int totalBags  = rows.stream().mapToInt(YarnRow::getNoOfBags).sum();
        int totalCones = rows.stream().mapToInt(YarnRow::getNoOfCones).sum();
        BigDecimal totalWt = rows.stream()
                .map(r -> r.getNetWeight() != null ? r.getNetWeight() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        lblTotalQty.setText(String.valueOf(totalBags + totalCones));
        lblTotalWeight.setText(fmt2(totalWt));
        lblTotalBags.setText(String.valueOf(totalBags));
        lblTotalCones.setText(String.valueOf(totalCones));
        lblRowCount.setText(String.valueOf(rows.size()));
    }

    private void rebuildYarnwiseSummary() {
        Map<String, YarnSummaryRow> grouped = new LinkedHashMap<>();
        for (YarnRow r : rows) {
            String key = r.getYarnTypeName() + "|" + r.getBagPiece();
            YarnSummaryRow s = grouped.computeIfAbsent(key, k -> {
                YarnSummaryRow sr = new YarnSummaryRow();
                sr.setYarnCount(r.getYarnTypeName()); sr.setBagPiece(r.getBagPiece());
                sr.setTotalBags(0); sr.setTotalCones(0); sr.setTotalWeight(BigDecimal.ZERO);
                return sr;
            });
            s.setTotalBags(s.getTotalBags() + r.getNoOfBags());
            s.setTotalCones(s.getTotalCones() + r.getNoOfCones());
            s.setTotalWeight(s.getTotalWeight().add(r.getNetWeight() != null ? r.getNetWeight() : BigDecimal.ZERO));
        }
        summaryRows.setAll(grouped.values());
    }

    private void showSummarySection(boolean show) {
        if (summarySection != null) { summarySection.setManaged(show); summarySection.setVisible(show); }
    }

    // ════════════════════════════════════════════════════════════
    //  RECEIPT / SAVE / SUBMIT / PDF / EXCEL / EDIT / RESET / EXIT
    // ════════════════════════════════════════════════════════════
    @FXML public void onUploadReceipt() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Receipt");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images / PDF","*.jpg","*.jpeg","*.png","*.pdf"));
        File f = fc.showOpenDialog(tblYarn.getScene().getWindow());
        if (f != null) {
            try {
                uploadedReceiptPath = fileUploadUtil.saveReceipt(f, tfChallan.getText().trim());
                lblReceiptFile.setText("✔ " + f.getName());
                lblReceiptFile.setStyle("-fx-text-fill:#16a34a;-fx-font-weight:bold;");
                if (btnViewReceipt != null) btnViewReceipt.setVisible(true);
                lblEntryBadge.setText("● Receipt uploaded");
            } catch (IOException ex) { GlobalUI.warn("Upload failed: " + ex.getMessage()); }
        }
    }

    @FXML public void onViewReceipt() {
        if (uploadedReceiptPath != null) {
            try { fileUploadUtil.openReceipt(uploadedReceiptPath); }
            catch (IOException ex) { GlobalUI.warn("Cannot open receipt: " + ex.getMessage()); }
        }
    }

    @FXML
    public void onSave() {

        if (!validateHeader()) return;

        List<YarnEntry> entries = buildEntries();

        YarnDuplicateCheckResult result =
                yarnService.checkDuplicates(entries);

        // 🔴 DUPLICATES → POPUP
        if (result.hasDuplicates()) {

            openDuplicatePopup(result.rows(), () -> {
                yarnService.saveAll(result.nonDuplicates(), uploadedReceiptPath);
            });

            return;
        }

        // ✅ NORMAL SAVE
        yarnService.saveAll(entries, uploadedReceiptPath);
        GlobalUI.success("Saved successfully");
    }
    private void openDuplicatePopup(List<YarnDuplicateReviewRow> rows,
                                    Runnable onDone) {

        try {

            javafx.fxml.FXMLLoader loader =
                    new javafx.fxml.FXMLLoader(
                            getClass().getResource("/fxml/yarn_duplicate_review.fxml")
                    );

            loader.setControllerFactory(appContext::getBean);

            javafx.scene.Parent root = loader.load();

            YarnDuplicateReviewController controller = loader.getController();

            controller.init(
                    rows,
                    tfChallan.getText().trim(),
                    onDone
            );

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Duplicate Review");
            stage.setScene(new javafx.scene.Scene(root));
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.showAndWait();

        } catch (Exception e) {
            GlobalUI.warn("Duplicate popup failed: " + e.getMessage());
        }
    }

    @FXML public void onFinalSubmit() {
        if (!validateHeader()) return;
        if (rows.isEmpty()) { GlobalUI.warn("Add at least one yarn row."); return; }
        if (!GlobalUI.confirm("Final Submit","Submit all " + rows.size() + " rows for challan " + tfChallan.getText().trim() + "?")) return;
        try {
            yarnService.finalSubmit(buildEntries(), uploadedReceiptPath);
            lblEntryBadge.setText("● Submitted");
            GlobalUI.success("Final submit complete.");
            onReset();
        } catch (Exception ex) { GlobalUI.warn("Submit failed: " + ex.getMessage()); }
    }

    @FXML public void onPdf() {
        if (rows.isEmpty()) { GlobalUI.warn("No data to export."); return; }
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("yarn_" + tfChallan.getText().trim() + ".pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files","*.pdf"));
        File f = fc.showSaveDialog(tblYarn.getScene().getWindow());
        if (f != null) {
            try {
                pdfExporter.exportYarnReport(buildEntries(), null,
                        cbWorker.getValue() != null ? cbWorker.getValue().getName() : "",
                        dpDate.getValue(), dpDate.getValue(), f.toPath());
                GlobalUI.success("PDF saved: " + f.getName());
            } catch (IOException ex) { GlobalUI.warn("PDF failed: " + ex.getMessage()); }
        }
    }

    @FXML public void onExcel() {
        if (rows.isEmpty()) { GlobalUI.warn("No data to export."); return; }
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("yarn_" + tfChallan.getText().trim() + ".xlsx");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files","*.xlsx"));
        File f = fc.showSaveDialog(tblYarn.getScene().getWindow());
        if (f != null) {
            try {
                excelExporter.exportYarnReport(buildEntries(), null,
                        cbWorker.getValue() != null ? cbWorker.getValue().getName() : "",
                        dpDate.getValue(), dpDate.getValue(), f.toPath());
                GlobalUI.success("Excel saved: " + f.getName());
            } catch (IOException ex) { GlobalUI.warn("Excel failed: " + ex.getMessage()); }
        }
    }

    @FXML public void onEdit() {
        YarnRow selected = tblYarn.getSelectionModel().getSelectedItem();
        if (selected == null) { GlobalUI.success("Select a row and use the ✏ Edit button on that row."); return; }
        openEditDialog(selected);
    }

    @FXML public void onReset() {
        cbWorker.getSelectionModel().clearSelection();
        tfWorkerId.clear();
        cbLocation.getSelectionModel().clearSelection();
        tfChallan.clear();
        dpDate.setValue(LocalDate.now());
        rows.clear(); summaryRows.clear();
        showSummarySection(false); refreshSummaryBar(); clearInputFields();
        uploadedReceiptPath = null;
        lblReceiptFile.setText("No file selected");
        lblReceiptFile.setStyle("-fx-text-fill:#94a3b8;-fx-font-style:italic;");
        lblEntryBadge.setText("● New Entry");
    }

    @FXML public void onPrint() { GlobalUI.success("Use PDF export and print from your PDF viewer."); }
    @FXML public void onExit()  { stageManager.showScene(FxmlView.MAIN_LAYOUT); }

    // ════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════
    private void clearInputFields() {

        // ❌ DO NOT CLEAR BAG TYPE → keeps flow smooth
        // cbBagPiece.getSelectionModel().clearSelection();

        cbWtOfBags.getSelectionModel().clearSelection();
        cbYarnCount.getSelectionModel().clearSelection();

        tfColour.clear();
        tfNoOfBags.clear();
        tfNoOfCones.clear();
        tfNetWeight.clear();

        // 🔥 KEEP correct enable/disable state
        onBagPieceChanged();
        // 🔥 SHOW / HIDE WT ROW
        boolean isBag = "BAG".equalsIgnoreCase(cbBagPiece.getValue());

        if (wtOfBagsRow != null) {
            wtOfBagsRow.setManaged(isBag);
            wtOfBagsRow.setVisible(isBag);
        }

        // 🔥 FOCUS back to Yarn Count (next logical step)
        cbYarnCount.requestFocus();
    }
    private boolean validateHeader() {
        ValidationUtil.start();
        ValidationUtil.required(cbWorker,   "Worker required");
        ValidationUtil.required(cbLocation, "Location required");
        ValidationUtil.required(tfChallan,  "Challan required");
        ValidationUtil.required(dpDate,     "Select Date");
        return ValidationUtil.validate();
    }

    private List<YarnEntry> buildEntries() {
        String challan = tfChallan.getText().trim();
        LocalDate date = dpDate.getValue();
        JobWorker worker = cbWorker.getValue();
        List<YarnEntry> list = new ArrayList<>();
        for (YarnRow r : rows) {
            YarnEntry e = new YarnEntry();
            e.setChallanNo(challan); e.setEntryDate(date); e.setJobWorker(worker);
            e.setDeliveryLocation(cbLocation.getValue());
            e.setYarnType(r.getYarnType()); e.setColour(r.getColour());
            e.setNoOfBags(r.getNoOfBags()); e.setNoOfCones(r.getNoOfCones());
            e.setNetWeight(r.getNetWeight()); e.setBagPiece(r.getBagPiece());
            e.setWtOfBags(r.getWtOfBags());
            list.add(e);
        }
        return list;
    }

    private String fmt2(BigDecimal v) {
        return v == null ? "0.00" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    private int parseInt(String s) {
        try { return Integer.parseInt(s == null ? "0" : s.trim()); } catch (NumberFormatException e) { return 0; }
    }
    private BigDecimal parseBd(String s) {
        try { return new BigDecimal(s == null || s.isBlank() ? "0" : s.trim()); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    // ════════════════════════════════════════════════════════════
    //  INNER MODELS  (unchanged)
    // ════════════════════════════════════════════════════════════
    public static class YarnRow {
        private int slNo; private String bagPiece; private String wtOfBags;
        private YarnType yarnType; private String yarnTypeName; private String colour;
        private int noOfBags; private int noOfCones; private BigDecimal netWeight;

        public int getSlNo() { return slNo; } public void setSlNo(int v) { slNo = v; }
        public String getBagPiece() { return bagPiece; } public void setBagPiece(String v) { bagPiece = v; }
        public String getWtOfBags() { return wtOfBags; } public void setWtOfBags(String v) { wtOfBags = v; }
        public YarnType getYarnType() { return yarnType; } public void setYarnType(YarnType v) { yarnType = v; }
        public String getYarnTypeName() { return yarnTypeName; } public void setYarnTypeName(String v) { yarnTypeName = v; }
        public String getColour() { return colour; } public void setColour(String v) { colour = v; }
        public int getNoOfBags() { return noOfBags; } public void setNoOfBags(int v) { noOfBags = v; }
        public int getNoOfCones() { return noOfCones; } public void setNoOfCones(int v) { noOfCones = v; }
        public BigDecimal getNetWeight() { return netWeight; } public void setNetWeight(BigDecimal v) { netWeight = v; }
    }

    public static class YarnSummaryRow {
        private String yarnCount; private String bagPiece;
        private int totalBags; private int totalCones; private BigDecimal totalWeight;

        public String getYarnCount() { return yarnCount; } public void setYarnCount(String v) { yarnCount = v; }
        public String getBagPiece() { return bagPiece; } public void setBagPiece(String v) { bagPiece = v; }
        public int getTotalBags() { return totalBags; } public void setTotalBags(int v) { totalBags = v; }
        public int getTotalCones() { return totalCones; } public void setTotalCones(int v) { totalCones = v; }
        public BigDecimal getTotalWeight() { return totalWeight; } public void setTotalWeight(BigDecimal v) { totalWeight = v; }
    }
}