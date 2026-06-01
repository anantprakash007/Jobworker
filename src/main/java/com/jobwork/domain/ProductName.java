package com.jobwork.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "product_name")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductName {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_type_id", nullable = false)
    private ProductType productType;

    @Column(nullable = false, length = 150)
    private String name;

    // ==================== NEW FIELDS ====================

    @Column(name = "pick", nullable = true)
    private Integer pick;

    @Column(name = "length", nullable = true)
    private Double length;           // in meters or yards

    @Column(name = "width", nullable = true)
    private Double width;            // in inches or cm

    @Column(name = "total_picks", nullable = true)
    private Integer totalPicks;      // Total Peek → I renamed to totalPicks

    @Column(name = "reed", nullable = true)
    private Integer reed;

    @Column(name = "reed_space", nullable = true)
    private Double reedSpace;

    @Column(name = "default_paisa")
    private BigDecimal defaultPaisa;

    @Column(name = "default_rate")
    private BigDecimal defaultRate;
    // ==================== SAFE equals & hashCode ====================
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductName)) return false;
        ProductName that = (ProductName) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name;
    }
}