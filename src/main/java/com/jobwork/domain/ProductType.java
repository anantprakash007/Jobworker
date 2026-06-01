package com.jobwork.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "product_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    // Bidirectional — cascade keeps orphan names from lingering
    @Builder.Default
    @OneToMany(mappedBy = "productType",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<ProductName> productNames = new ArrayList<>();

    // ==================== SAFE equals & hashCode ====================
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductType)) return false;
        ProductType that = (ProductType) o;
        return Objects.equals(id, that.id);   // Only compare by ID
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // toString used by ComboBox / TableView
    @Override
    public String toString() {
        return name;
    }
}