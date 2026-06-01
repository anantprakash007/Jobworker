package com.jobwork.controller;

import com.jobwork.service.MoneyImportService;
import com.jobwork.util.GlobalUI;
import javafx.fxml.FXML;
import javafx.stage.FileChooser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * MoneyImportController  (MoneyReceipt)
 * ─────────────────────────────────────────────────────────────────
 * Same pattern as ProductImportController.
 *
 * Tiny launcher controller — wired to a "📥 Import Excel" button
 * in money_entry.fxml. Opens money_import_preview.fxml in a popup
 * window and passes the selected file to MoneyImportPreviewController.
 *
 * Add to money_entry.fxml:
 *   <Button text="📥 Import Excel" onAction="#onImportExcel"/>
 */
@Component
public class MoneyImportController {

    @Autowired
    private MoneyImportService importService;

    // ── Import Excel ──────────────────────────────────────────────
    @FXML
    public void onImportExcel() {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Money Receipt Import Excel File");
            fc.getExtensionFilters().addAll(
                    new javafx.stage.FileChooser.ExtensionFilter(
                            "Excel Files", "*.xlsx", "*.xls"),
                    new javafx.stage.FileChooser.ExtensionFilter(
                            "All Files", "*.*"));

            File file = fc.showOpenDialog(null);
            if (file == null) return;

            // Open the preview window and pass the selected file
            GlobalUI.openWindow(
                    "/fxml/money_import_preview.fxml",
                    "Money Receipt Import Preview",
                    controller -> {
                        MoneyImportPreviewController c =
                                (MoneyImportPreviewController) controller;
                        // Load and validate the Excel file
                        c.loadExcel(file);
                    }
            );

        } catch (Exception e) {
            GlobalUI.warn("Import failed: " + e.getMessage());
        }
    }

}