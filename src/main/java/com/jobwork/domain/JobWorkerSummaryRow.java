package com.jobwork.domain;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * JobWorkerSummaryRow
 * ──────────────────────────────────────────────────────────────────
 * One row in the Job Worker Summary Report table.
 *
 * Correct column order (as per hand-drawn sketch):
 *   Sl.No | Product Name | Total Picks | Length | Wt | Qt | Unit | Paisa | Rate | Total
 *
 * Key rules:
 *  • totalPicks → from ProductName.totalPicks  (loaded when product name selected)
 *  • length     → from ProductName.length      (loaded when product name selected)
 *  • unit       → from ProductName → Unit      (loaded when product name selected)
 *  • paisa      → EDITABLE in table cell (click, type, Enter)
 *  • rate       → EDITABLE in table cell (click, type, Enter)
 *  • total      → paisa × quantity (auto-calculated after paisa or rate edit)
 */
@Setter
public class JobWorkerSummaryRow {

    // ── Getters & Setters ────────────────────────────────────────
    @Getter
    private int        slNo;
    @Getter
    private String     productName;   // display name from ProductName entity

    // ── Columns from ProductName entity (auto-filled on product name load) ──
    private String     totalPicks;    // ProductName.totalPicks  (e.g. "2400")
    private String     length;        // ProductName.length      (e.g. "5.5 Mtr")
    private String     unit;          // Unit.name               (e.g. "Piece")

    // ── Measurement columns from ProductEntry ──
    private BigDecimal weight;        // total weight (kg)
    private BigDecimal quantity;      // total quantity

    // ── Editable columns ──
    private BigDecimal paisa;         // EDITABLE — user types paisa per piece
    private BigDecimal rate;          // EDITABLE — rate per unit

    // ── Calculated ──
    private BigDecimal total;         // = quantity × rate (recalculates on edit)
    private BigDecimal paid;
    private BigDecimal balance;
    private BigDecimal advance;
    private String status;

    // ── Constructors ────────────────────────────────────────────
    public JobWorkerSummaryRow() {}

    public JobWorkerSummaryRow(
            int slNo,
            String productName,
            String totalPicks,
            String length,
            BigDecimal weight,
            BigDecimal quantity,
            String unit,
            BigDecimal paisa,
            BigDecimal rate,
            BigDecimal total) {
        this.slNo        = slNo;
        this.productName = productName;
        this.totalPicks  = totalPicks;
        this.length      = length;
        this.weight      = weight;
        this.quantity    = quantity;
        this.unit        = unit;
        this.paisa       = paisa;
        this.rate        = rate;
        this.total       = total;
    }

    public String getTotalPicks()               { return totalPicks != null ? totalPicks : ""; }

    public String getLength()                   { return length != null ? length : ""; }

    public String getUnit()                     { return unit != null ? unit : ""; }

    public BigDecimal getWeight()               { return safe(weight); }

    public BigDecimal getQuantity()             { return safe(quantity); }

    public BigDecimal getPaisa()                { return safe(paisa); }

    public BigDecimal getRate()                 { return safe(rate); }

    public BigDecimal getTotalAmount()                { return safe(total); }
    public BigDecimal getPaid() {
        return paid != null ? paid : BigDecimal.ZERO;
    }
    public BigDecimal getBalance() {
        return balance != null ? balance : BigDecimal.ZERO;
    }

    public BigDecimal getAdvance() {
        return advance != null ? advance : BigDecimal.ZERO;
    }

    public String getStatus() {
        return status != null ? status : "";
    }

    private BigDecimal safe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}