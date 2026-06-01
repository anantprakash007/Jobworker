package com.jobwork.domain;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * View model for one Excel row in the Yarn Import Preview table.
 *
 * NOT a JPA entity — in-memory only for the JavaFX preview table.
 *
 * Expected Excel column order:
 *   A  Worker Name
 *   B  Delivery Location
 *   C  Challan No
 *   D  Date          (dd/MM/yyyy or dd-MM-yyyy)
 *   E  Bag / Piece   ("BAG" or "PIECE")
 *   F  Wt of Bags    ("50 kg","60 kg","70 kg","75 kg","90 kg" — only for BAG rows)
 *   G  Yarn Count    (name of YarnType in master)
 *   H  Colour
 *   I  No. of Bags   (integer — 0 for PIECE rows)
 *   J  No. of Cones  (integer — 0 for BAG rows)
 *   K  Net Weight    (decimal kg)
 *
 * validationError:
 *   null  = row passed all validations → green background
 *   text  = error message → shown in Status column, red background
 *   starts with "DUPLICATE" → amber background, user decides to overwrite
 */
@Getter
@Setter
public class YarnImportRow {

    // ── Excel row number (1-based, for error reporting) ───────────
    private final int rowNumber;

    // ── Raw string values as read from Excel ─────────────────────
    private String workerName;
    private String locationName;
    private String challanNo;
    private String dateRaw;
    private String bagPieceRaw;
    private String wtOfBagsRaw;
    private String yarnCountRaw;
    private String colour;
    private String noOfBagsRaw;
    private String noOfConesRaw;
    private String netWeightRaw;
    private boolean imported;
    // ── Resolved domain objects ───────────────────────────────────
    private JobWorker        jobWorker;
    private DeliveryLocation deliveryLocation;
    private LocalDate        entryDate;
    private YarnType         yarnType;

    // ── Resolved scalar values ────────────────────────────────────
    private String     bagPiece;    // "BAG" or "PIECE"
    private String     wtOfBags;    // "50 kg" etc. — null for PIECE rows
    private int        noOfBags;
    private int        noOfCones;
    private BigDecimal netWeight;

    // ── Validation ────────────────────────────────────────────────
    private String validationError; // null = valid

    // ── Checkbox ──────────────────────────────────────────────────
    private final BooleanProperty selected = new SimpleBooleanProperty(true);

    public YarnImportRow(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public boolean isValid()   { return validationError == null; }
    public boolean isInvalid() { return validationError != null
            && !validationError.startsWith("DUPLICATE"); }
    public boolean isDuplicate() { return validationError != null
            && validationError.startsWith("DUPLICATE"); }

    // ── JavaFX property accessors ─────────────────────────────────
    public BooleanProperty selectedProperty() { return selected; }
    public boolean isSelected()               { return selected.get(); }
    public void    setSelected(boolean v)     { selected.set(v); }

}