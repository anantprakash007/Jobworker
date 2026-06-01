package com.jobwork.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "product_entry")
public class ProductEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "job_worker_id", nullable = false)
    private JobWorker jobWorker;

    @Column(nullable = false)
    private String challanNo;

    @Column(nullable = false)
    private LocalDate entryDate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_type_id")
    private ProductType productType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_name_id")
    private ProductName productName;

    private BigDecimal quantity;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "unit_id")
    private Unit unit;

    private BigDecimal weight;

    // ✅ FIXED NAME (important)
    private BigDecimal weightPerPiece;

    private BigDecimal totalQuantity;
    private BigDecimal totalWeight;

    @Enumerated(EnumType.STRING)
    private EntryStatus status;


    private String receiptPath;
    public BigDecimal getTotalAmount() {

        if (quantity == null || productName == null) return BigDecimal.ZERO;

        BigDecimal rate = productName.getDefaultRate();
        if (rate == null) return BigDecimal.ZERO;

        return quantity.multiply(rate);
    }
}

// =====================================================
// 🔥 SAFE