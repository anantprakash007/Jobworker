package com.jobwork.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "taar",
uniqueConstraints = @UniqueConstraint(
        columnNames = {"bheem_name_id", "value"}))
// same taar value can exist for different bheem names)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Taar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private Integer value;
   // private String name;   // 20s, 30s, 40s

    // Which bheem name this taar belongs to
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "bheem_name_id", nullable = true)
    @org.hibernate.annotations.NotFound(action = org.hibernate.annotations.NotFoundAction.IGNORE)
    private BheemName bheemName;
    // Optional: Add this to avoid issues when bheemName is null
    public String getBheemNameString() {
        return bheemName != null ? bheemName.getName() : "N/A";
    }
  //  @Column(nullable = false)
    //private Integer value;   // e.g. 16, 20, 24, 28 ...

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
