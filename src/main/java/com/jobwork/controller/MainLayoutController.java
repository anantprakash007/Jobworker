package com.jobwork.controller;

import com.jobwork.config.FxmlView;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

@Component
public class MainLayoutController implements Initializable {


    @Autowired
    private ApplicationContext ctx;

    @FXML
    private StackPane contentArea;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Load Product Entry as the default screen on startup
        loadContent(FxmlView.PRODUCT_ENTRY);
    }
    @FXML public void goChallanDetail() {
        loadContent(FxmlView.CHALLAN_DETAIL);
    }
    @FXML public void goDuplicateReview() { loadContent(FxmlView.DUPLICATE_REVIEW);}
    @FXML public void goBheemDuplicateReview() { loadContent(FxmlView.BHEEM_DUPLICATE_REVIEW);}
    @FXML public void goYarnDuplicateReview() { loadContent(FxmlView.YARN_DUPLICATE_REVIEW);}
    @FXML public void goMoneyDuplicateReview() { loadContent(FxmlView.MONEY_DUPLICATE_REVIEW);}

    @FXML public void goAdvanceLedger() {
        loadContent(FxmlView.ADVANCE_LEDGER);
    }
    @FXML public void goAccountLedger() {
        loadContent(FxmlView.ACCOUNT_LEDGER);
    }
    // ── Entry ────────────────────────────────────────────────
    @FXML public void goProductEntry()  { loadContent(FxmlView.PRODUCT_ENTRY);  }
    @FXML public void goBheemEntry()    { loadContent(FxmlView.BHEEM_ENTRY);    }
    @FXML public void goYarnEntry()     { loadContent(FxmlView.YARN_ENTRY);     }
    @FXML public void goMoneyReceipt()  { loadContent(FxmlView.MONEY_RECEIPT);  }

    // ── Reports ──────────────────────────────────────────────
    @FXML public void goProductReport() { loadContent(FxmlView.PRODUCT_REPORT); }
    @FXML public void goBheemReport()   { loadContent(FxmlView.BHEEM_REPORT);   }
    @FXML public void goYarnReport()    { loadContent(FxmlView.YARN_REPORT);    }
    @FXML public void goMoneyReport()   { loadContent(FxmlView.MONEY_REPORT);   }

    // ── Masters ──────────────────────────────────────────────
    @FXML public void goJobWorkers()    { loadContent(FxmlView.MASTER_WORKER);  }
    @FXML public void goWrapperMaster()    { loadContent(FxmlView.MASTER_WRAPPER);  }
    @FXML public void goProductMaster() { loadContent(FxmlView.MASTER_PRODUCT); }
    @FXML public void goUnitMaster()    { loadContent(FxmlView.MASTER_UNIT);    }
    @FXML public void goChallanReport()    { loadContent(FxmlView.CHALLAN_REPORT);}
    @FXML public void goBheemMaster()    { loadContent(FxmlView.MASTER_BHEEM);    }

    public void goWorkerSummary() { loadContent(FxmlView.WORKER_SUMMARY);
    }

    // ── Loader ───────────────────────────────────────────────
    private void loadContent(FxmlView view) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(view.getFxmlPath()));
            loader.setControllerFactory(ctx::getBean);
            Parent content = loader.load();
            contentArea.getChildren().setAll(content);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Cannot load FXML: " + view.getFxmlPath(), e);
        }
    }
}