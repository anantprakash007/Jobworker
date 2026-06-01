package com.jobwork.controller;

import com.jobwork.service.YarnImportService;
import com.jobwork.util.GlobalUI;
import javafx.fxml.FXML;
import javafx.stage.FileChooser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * YarnImportController
 * ─────────────────────────────────────────────────────────────────
 * Same pattern as ProductImportController.
 *
 * Tiny launcher controller — wired to a "📥 Import Excel" button
 * in yarn_entry.fxml. Opens yarn_import_preview.fxml in a popup
 * window and passes the selected file to YarnImportPreviewController.
 *
 * Add to yarn_entry.fxml:
 *   <Button text="📥 Import Excel" onAction="#onImportExcel"/>
 */
@Component
public class YarnImportController {

    @Autowired
    private YarnImportService importService;

    // ── Import Excel ──────────────────────────────────────────────
    @FXML
    public void onImportExcel() {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Yarn Import Excel File");
            fc.getExtensionFilters().addAll(
                    new javafx.stage.FileChooser.ExtensionFilter(
                            "Excel Files", "*.xlsx", "*.xls"),
                    new javafx.stage.FileChooser.ExtensionFilter(
                            "All Files", "*.*"));

            File file = fc.showOpenDialog(null);
            if (file == null) return;

            // Open the preview window and pass the selected file
            GlobalUI.openWindow(
                    "/fxml/yarn_import_preview.fxml",
                    "Yarn Import Preview",
                    controller -> {
                        YarnImportPreviewController c =
                                (YarnImportPreviewController) controller;
                        // Load and validate the Excel file
                        c.loadExcel(file);
                    }
            );

        } catch (Exception e) {
            GlobalUI.warn("Import failed: " + e.getMessage());
        }
    }
}