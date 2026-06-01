package com.jobwork.domain;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ImportRow {

    private Long workerId;
    private String workerName;
    private String challan;
    private LocalDate date;

    // 🔥 NEW FIELDS (MATCH UI)
    private String productType;
    private String product;
    private String unit;

    private BigDecimal qty;
    private BigDecimal weight;
    private BigDecimal wtPerPiece;

    // 🔥 STATUS
    private String status;       // VALID / ERROR / DUPLICATE
    private String errorDetail;
    private boolean duplicate;

    // =========================================================
    // ✅ UI SUPPORT (VERY IMPORTANT)
    // =========================================================

    // Row number (for display in table)
    private int rowNumber;

    // Checkbox selection (default = selected)
    private final BooleanProperty selected = new SimpleBooleanProperty(true);

    // 🔹 JavaFX property methods (REQUIRED)

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean value) {
        selected.set(value);
    }
}