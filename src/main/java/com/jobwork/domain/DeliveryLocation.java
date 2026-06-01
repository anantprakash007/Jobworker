package com.jobwork.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data                    // ← this generates getName() automatically
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "delivery_location")
public class DeliveryLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @Override
    public String toString() {
        return name; // IMPORTANT for ComboBox display
    }
}

