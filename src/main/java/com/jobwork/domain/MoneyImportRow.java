package com.jobwork.domain;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * MoneyImportRow  (MoneyReceipt)
 * ─────────────────────────────────────────────────────────────────
 * View model for one Excel row in Money Receipt Import Preview table.
 * NOT a JPA entity — in-memory only.
 *
 * Excel column order (Row 1 = header, data from Row 2):
 *   A  Worker ID          (Long integer preferred; name fallback)
 *   B  Challan No
 *   C  Date               (dd/MM/yyyy)
 *   D  Transaction Type   (ADVANCE / PAYMENT / DEDUCTION)
 *   E  Amount
 *   F  Remark             (optional)
 */
@Getter
@Setter
public class MoneyImportRow {

    private final int rowNumber;

    // ── Raw Excel values ──────────────────────────────────────────
    private String workerName;
    private Long workerId;
    private String challanNo;
    private String dateRaw;
    private String transferModeRaw;
    private String amountRaw;
    private String remark;
    private String workerIdRaw;
    // ── Resolved ─────────────────────────────────────────────────
    private JobWorker jobWorker;
    private LocalDate entryDate;
    private String    transferMode;
    private BigDecimal amount;

    // ── Validation ────────────────────────────────────────────────
    private String validationError;

    // ── Checkbox ──────────────────────────────────────────────────
    private final BooleanProperty selected = new SimpleBooleanProperty(true);

    public MoneyImportRow(int rowNumber) { this.rowNumber = rowNumber; }

    public boolean isValid()     { return validationError == null; }
    public boolean isInvalid()   { return validationError != null && !validationError.startsWith("DUPLICATE"); }
    public boolean isDuplicate() { return validationError != null &&  validationError.startsWith("DUPLICATE"); }

    public BooleanProperty selectedProperty() { return selected; }
    public boolean isSelected()               { return selected.get(); }
    public void    setSelected(boolean v)     { selected.set(v); }
}