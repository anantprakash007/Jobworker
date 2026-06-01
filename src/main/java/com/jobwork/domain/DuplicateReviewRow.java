package com.jobwork.domain;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * View model for the Duplicate Review popup table.
 *
 * NOT a JPA entity — this is a pure JavaFX view model.
 * It is constructed in-memory from two ProductEntry objects:
 *   - existing : the row already saved in the database
 *   - incoming : the new row the user is trying to submit
 *
 * The `selected` BooleanProperty drives the CheckBoxTableCell —
 * when true the existing DB record will be OVERWRITTEN with the
 * incoming values when the user clicks "Confirm Re-Entry".
 */
@Getter
public class DuplicateReviewRow {

    // ── Existing DB record (display values) ──────────────────────
    private final Long       existingId;
    private final LocalDate  date;
    private final String     challanNo;
    private final String     productType;
    private final String     productName;
    private final BigDecimal existingQty;
    private final BigDecimal existingWeight;
    private final BigDecimal existingWtPerPiece;
    private int rowNumber;

    // ── New (incoming) values ─────────────────────────────────────
    private final BigDecimal newQty;
    private final BigDecimal newWeight;
    private final BigDecimal newWtPerPiece;

    // ── Full entity references (used by applyReEntry in service) ──
    private final ProductEntry existingEntry;
    private final ProductEntry incomingEntry;

    // ── Checkbox: tick = overwrite, untick = skip ─────────────────
    // NOT declared final in spirit — SimpleBooleanProperty is mutable internally
    private final BooleanProperty selected = new SimpleBooleanProperty(false);

    // ── Constructor ───────────────────────────────────────────────

    /**
     * Builds one popup-table row from a (existing, incoming) pair.
     *
     * @param existing  the ProductEntry already in the DB
     * @param incoming  the ProductEntry the user is submitting now
     */
    public DuplicateReviewRow(ProductEntry existing, ProductEntry incoming) {

        // Existing DB snapshot
        this.existingId         = existing.getId();
        this.date               = existing.getEntryDate();
        this.challanNo          = existing.getChallanNo();
        this.productType        = existing.getProductType() != null
                ? existing.getProductType().getName() : "";
        this.productName        = existing.getProductName() != null
                ? existing.getProductName().getName() : "";
        this.existingQty        = existing.getQuantity();
        this.existingWeight     = existing.getWeight();
        this.existingWtPerPiece = existing.getWeightPerPiece();

        // New incoming values
        this.newQty             = incoming.getQuantity();
        this.newWeight          = incoming.getWeight();
        this.newWtPerPiece      = incoming.getWeightPerPiece();

        // Keep full references so the service can merge them on confirm
        this.existingEntry      = existing;
        this.incomingEntry      = incoming;

    }

    // ── JavaFX property accessors (required by CheckBoxTableCell) ─

    /** Returns the JavaFX property — required by CheckBoxTableCell.forTableColumn(). */
    public BooleanProperty selectedProperty() { return selected; }

    /** Convenience getter — Lombok @Getter is skipped for this field
     *  because BooleanProperty needs isSelected(), not getSelected(). */
    public boolean isSelected() { return selected.get(); }

    /** Convenience setter — delegates to the internal property. */
    public void setSelected(boolean value) { selected.set(value); }
}