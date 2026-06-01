package com.jobwork.config;

import lombok.Getter;

@Getter
public enum FxmlView {

    PRODUCT_ENTRY  ("/fxml/product_entry.fxml",   "Entry of Product"),
    PRODUCT_REPORT ("/fxml/product_report.fxml",  "Product Report"),
    BHEEM_ENTRY    ("/fxml/bheem_entry.fxml",      "Entry of Bheem"),
    BHEEM_REPORT   ("/fxml/bheem_report.fxml",     "Bheem Report"),
    YARN_ENTRY     ("/fxml/yarn_entry.fxml",       "Entry of Yarn"),
    YARN_REPORT    ("/fxml/yarn_report.fxml",      "Yarn Report"),
    MONEY_RECEIPT  ("/fxml/money_receipt.fxml",    "Money Receipt"),
    MONEY_REPORT   ("/fxml/money_report.fxml",     "Money Report"),
    MASTER_WRAPPER  ("/fxml/master_wrapper.fxml",    "Wrapper Master"),
    MASTER_WORKER  ("/fxml/master_worker.fxml",    "Job Worker Master"),
    MASTER_PRODUCT ("/fxml/master_product.fxml",   "Product Master"),
    MAIN_LAYOUT ("/fxml/main_layout.fxml",   "Home Page"),
    MASTER_BHEEM   ("/fxml/master_bheem.fxml",      "Bheem Name Master"),
    WORKER_SUMMARY   ("/fxml/jobworker_summary.fxml",      "Job Worker Summary "),
    MASTER_UNIT    ("/fxml/master_unit.fxml",      "Unit & Yarn Type Master"),
    ADVANCE_LEDGER   ("/fxml/advance_ledger.fxml",      "Advance Ledger"),
    ACCOUNT_LEDGER   ("/fxml/account_ledger.fxml",      "ACCOUNT Ledger"),
    CHALLAN_DETAIL("/fxml/challan_detail.fxml",      "Challan Detail"),
    BHEEM_DUPLICATE_REVIEW("/fxml/bheem_duplicate_review.fxml",      "Bheem Duplicate Review"),
    //YARN_IMPORT_REVIEW("/fxml/yarn_import_review.fxml",      "Yarn Import Review"),
    DUPLICATE_REVIEW("/fxml/duplicate_review.fxml",      "Duplicate Review"),
    YARN_DUPLICATE_REVIEW("/fxml/yarn_duplicate_review.fxml",      "Yarn Duplicate Review"),
    MONEY_DUPLICATE_REVIEW("/fxml/money_duplicate_review.fxml",      "Money Duplicate Review"),
    PRODUCT_IMPORT_PREVIEW("/fxml/product_import_preview.fxml", "Product Import Preview"),
    CHALLAN_REPORT ("/fxml/challan_report.fxml",      "Challan Report");// ← semicolon HERE, after the LAST constant
    private final String fxmlPath;
    private final String title;

    FxmlView(String path, String title) {
        this.fxmlPath = path;
        this.title = title;
    }

    public String getFxmlPath() { return fxmlPath; }   // 🔥 FIX
    public String getTitle() { return title; }         // 🔥 FIX

}