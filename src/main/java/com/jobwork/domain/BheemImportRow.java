package com.jobwork.domain;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class BheemImportRow {

    // ── STATUS CONSTANTS (VERY IMPORTANT) ─────────────
    public static final String VALID     = "VALID";
    public static final String ERROR     = "ERROR";
    public static final String DUPLICATE = "DUPLICATE";
    public static final String IMPORTED  = "IMPORTED";

    // ── Raw Excel values ─────────────────────────────
    private final int rowNumber;
    private String challanNo;
    private String workerName;
    private String locationName;
    private String dateRaw;
    private String bheemNameRaw;
    private String taarRaw;
    private String wrapperRaw;
    private String yarnTypeRaw;
    private String colour;
    private String weightRaw;

    // ── Resolved objects ─────────────────────────────
    private JobWorker jobWorker;
    private DeliveryLocation deliveryLocation;
    private LocalDate entryDate;
    private BheemName bheemName;
    private Taar taar;
    private Wrapper wrapper;
    private YarnType yarnType;
    private BigDecimal weight;

    // ── Status system ────────────────────────────────
    private String status;
    private String errorDetail;

    // ── Selection (for duplicate UI) ─────────────────
    private final BooleanProperty selected = new SimpleBooleanProperty(true);

    public BheemImportRow(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    // ── Status helpers ───────────────────────────────
    public boolean isValid() {
        return VALID.equals(status);
    }

    public boolean isInvalid() {
        return ERROR.equals(status);
    }

    public boolean isDuplicate() {
        return DUPLICATE.equals(status);
    }

    public boolean isImported() {
        return IMPORTED.equals(status);
    }

    // ── Safe UI getters (prevents NullPointerException) ──
    public String getSafeWorker() {
        return workerName != null ? workerName : "";
    }

    public String getSafeChallan() {
        return challanNo != null ? challanNo : "";
    }

    public String getSafeDate() {
        return entryDate != null ? entryDate.toString() : "";
    }

    public String getSafeWeight() {
        return weight != null ? weight.toPlainString() : "";
    }

    public String getSafeStatus() {
        return status != null ? status : "";
    }

    public String getSafeError() {
        return errorDetail != null ? errorDetail : "";
    }
    // ✅ Display helper (ID + Name)
    public String getWorkerDisplay() {
        if (jobWorker == null) return workerName != null ? workerName : "";
        return jobWorker.getId() + " - " + jobWorker.getName();
    }
    // ── JavaFX ───────────────────────────────────────
    public BooleanProperty selectedProperty() { return selected; }
    public boolean isSelected() { return selected.get(); }
    public void setSelected(boolean v) { selected.set(v); }
}