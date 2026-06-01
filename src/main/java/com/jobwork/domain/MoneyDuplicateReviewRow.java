package com.jobwork.domain;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * MoneyDuplicateReviewRow  (MoneyReceipt)
 * ─────────────────────────────────────────────────────────────────
 * View model for one row in the Money Duplicate Review popup.
 * NOT a JPA entity — purely in-memory for the JavaFX table.
 *
 * Duplicate key: (workerId, challanNo, entryDate, transactionType)
 */
@Getter
public class MoneyDuplicateReviewRow {

    private final Long         existingId;
    private final LocalDate    date;
    private final String       challanNo;
    private final String       workerName;
    private final TransferMode transferMode;
    private final BigDecimal   existingAmount;
    private final String       existingRemark;
    private final BigDecimal   newAmount;
    private final String       newRemark;

    private final MoneyReceipt existingEntry;
    private final MoneyReceipt incomingEntry;

    private final BooleanProperty selected = new SimpleBooleanProperty(false);

    public MoneyDuplicateReviewRow(MoneyReceipt existing, MoneyReceipt incoming) {
        this.existingId      = existing.getId();
        this.date            = existing.getReceiptDate();
        this.challanNo       = existing.getChallanNo();
        this.workerName      = existing.getJobWorker() != null
                ? existing.getJobWorker().getName() : "";
        this.transferMode = existing.getTransferMode();
        this.existingAmount  = existing.getAmount();
        this.existingRemark  = existing.getRemark();
        this.newAmount       = incoming.getAmount();
        this.newRemark       = incoming.getRemark();
        this.existingEntry   = existing;
        this.incomingEntry   = incoming;
    }

    public BooleanProperty selectedProperty() { return selected; }
    public boolean isSelected()               { return selected.get(); }
    public void    setSelected(boolean v)     { selected.set(v); }
}