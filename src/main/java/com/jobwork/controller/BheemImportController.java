package com.jobwork.controller;

import com.jobwork.service.BheemImportService;
import com.jobwork.util.GlobalUI;
import javafx.fxml.FXML;
import javafx.stage.FileChooser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * BheemImportController
 * ─────────────────────────────────────────────────────────────────
 * Same pattern as ProductImportController.
 *
 * Tiny launcher controller — wired to a "📥 Import Excel" button
 * in bheem_entry.fxml. Opens bheem_import_preview.fxml in a popup
 * window and passes the selected file to BheemImportPreviewController.
 *
 * Add to bheem_entry.fxml:
 *   <Button text="📥 Import Excel" onAction="#onImportExcel"/>
 *
 * Wire in bheem_entry.fxml controller:
 *   fx:controller="com.jobwork.controller.BheemEntryController"
 *   (BheemEntryController already has onImportExcel() — this class
 *    is a standalone alternative if you want a separate Spring bean.)
 */
@Component
public class BheemImportController {

    @Autowired
    private BheemImportService importService;

    // ── Import Excel ──────────────────────────────────────────────
    @FXML
    public void onImportExcel() {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Bheem Import Excel File");
            fc.getExtensionFilters().addAll(
                    new javafx.stage.FileChooser.ExtensionFilter(
                            "Excel Files", "*.xlsx", "*.xls"),
                    new javafx.stage.FileChooser.ExtensionFilter(
                            "All Files", "*.*"));

            File file = fc.showOpenDialog(null);
            if (file == null) return;

            // Open the preview window and pass the selected file
            GlobalUI.openWindow(
                    "/fxml/bheem_import_preview.fxml",
                    "Bheem Import Preview",
                    controller -> {
                        BheemImportPreviewController c =
                                (BheemImportPreviewController) controller;
                        // Load and validate the Excel file
                        c.loadExcel(file);
                    }
            );

        } catch (Exception e) {
            GlobalUI.warn("Import failed: " + e.getMessage());
        }
    }
}