package com.jobwork.domain;

import javafx.beans.property.*;

import java.math.BigDecimal;

public class BheemDuplicateReviewRow {

    private final BooleanProperty selected = new SimpleBooleanProperty(false);

    private final StringProperty bheemName = new SimpleStringProperty();
    private final StringProperty taar = new SimpleStringProperty();

    private BigDecimal existingWeight;
    private BigDecimal newWeight;

    private BheemEntry existingEntry;
    private BheemEntry incomingEntry;

    public BheemDuplicateReviewRow(BheemEntry existing, BheemEntry incoming) {

        this.existingEntry = existing;
        this.incomingEntry = incoming;

        this.bheemName.set(existing.getBheemName().getName());
        this.taar.set(String.valueOf(existing.getTaar().getValue()));

        this.existingWeight = existing.getWeight();
        this.newWeight = incoming.getWeight();
    }

    public BooleanProperty selectedProperty() { return selected; }
    public boolean isSelected() { return selected.get(); }
    public void setSelected(boolean val) { selected.set(val); }

    public StringProperty bheemNameProperty() { return bheemName; }
    public StringProperty taarProperty() { return taar; }

    public BigDecimal getExistingWeight() { return existingWeight; }
    public BigDecimal getNewWeight() { return newWeight; }

    public BheemEntry getExistingEntry() { return existingEntry; }
    public BheemEntry getIncomingEntry() { return incomingEntry; }
}