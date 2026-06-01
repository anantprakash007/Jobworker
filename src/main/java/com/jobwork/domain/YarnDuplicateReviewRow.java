package com.jobwork.domain;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * View model for one row in the Yarn Duplicate Review popup.
 * NOT a JPA entity — purely in-memory for the JavaFX table.
 *
 * Duplicate key: (workerId, challanNo, entryDate, yarnType.id, bagPiece)
 *
 * selected = true  → existing DB row overwritten on confirm
 * selected = false → skip, keep existing data unchanged
 */
@Getter
public class YarnDuplicateReviewRow {

    // ── Existing DB snapshot ──────────────────────────────────────
    private final Long       existingId;
    private final LocalDate  date;
    private final String     challanNo;
    private final String     workerName;
    private final String     yarnTypeName;
    private final String     bagPiece;
    private final String     wtOfBags;
    private final String     colour;
    private final int        existingBags;
    private final int        existingCones;
    private final BigDecimal existingWeight;

    // ── New (incoming) values ─────────────────────────────────────
    private final String     newWtOfBags;
    private final String     newColour;
    private final int        newBags;
    private final int        newCones;
    private final BigDecimal newWeight;

    // ── Full entity references ────────────────────────────────────
    private final YarnEntry existingEntry;
    private final YarnEntry incomingEntry;

    // ── Checkbox ──────────────────────────────────────────────────
    private final BooleanProperty selected = new SimpleBooleanProperty(false);

    public YarnDuplicateReviewRow(YarnEntry existing, YarnEntry incoming) {
        this.existingId     = existing.getId();
        this.date           = existing.getEntryDate();
        this.challanNo      = existing.getChallanNo();
        this.workerName     = existing.getJobWorker() != null
                ? existing.getJobWorker().getName() : "";
        this.yarnTypeName   = existing.getYarnType() != null
                ? existing.getYarnType().getName() : "";
        this.bagPiece       = existing.getBagPiece();
        this.wtOfBags       = existing.getWtOfBags();
        this.colour         = existing.getColour();
        this.existingBags   = existing.getNoOfBags()   != null ? existing.getNoOfBags()   : 0;
        this.existingCones  = existing.getNoOfCones()  != null ? existing.getNoOfCones()  : 0;
        this.existingWeight = existing.getNetWeight();

        this.newWtOfBags    = incoming.getWtOfBags();
        this.newColour      = incoming.getColour();
        this.newBags        = incoming.getNoOfBags()   != null ? incoming.getNoOfBags()   : 0;
        this.newCones       = incoming.getNoOfCones()  != null ? incoming.getNoOfCones()  : 0;
        this.newWeight      = incoming.getNetWeight();

        this.existingEntry  = existing;
        this.incomingEntry  = incoming;
    }

    public BooleanProperty selectedProperty() { return selected; }
    public boolean isSelected()               { return selected.get(); }
    public void    setSelected(boolean v)     { selected.set(v); }
}