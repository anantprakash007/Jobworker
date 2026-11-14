package com.jobwork.controller;

import com.jobwork.config.FxmlView;
import com.jobwork.config.StageManager;
import com.jobwork.service.MoneyService;
import com.jobwork.service.ProductImportService;
import java.io.File;
import javafx.fxml.FXML;
import javafx.stage.FileChooser;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProductImportController {
  @Setter @Autowired private ProductImportService importService;
  @Autowired private StageManager stageManager;

  @Autowired private MoneyService moneyService;

  // ================= IMPORT EXCEL =================
  @FXML
  public void onImportExcel() {
    try {
      FileChooser fc = new FileChooser();
      fc.setTitle("Select Excel File");

      File file = fc.showOpenDialog(null);
      if (file == null) return;

      /**
       * GlobalUI.openWindow( "/fxml/product_import_preview.fxml", "Import Preview", controller -> {
       *
       * <p>ProductImportPreviewController c = (ProductImportPreviewController) controller;
       *
       * <p>// ✅ FIXED // c.setImportService(importService);
       *
       * <p>// ✅ LOAD DATA c.loadExcel(file); } );
       */
      stageManager.showModalWithData(
          FxmlView.PRODUCT_IMPORT_PREVIEW,
          controller -> {
            ((ProductImportPreviewController) controller).loadExcel(file);
          });
    } catch (Exception e) {
      e.printStackTrace(); // 🔥 ADD THIS
      // GlobalUI.warn("Import failed: " + e.getMessage());
    }
  }
  // ================= FIX MISSING WORK =================

}
