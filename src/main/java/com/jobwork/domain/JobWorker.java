package com.jobwork.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "job_worker",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_job_worker_phone",
                        columnNames = "phone"
                )
        }
)
public class JobWorker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Central Job Worker name.
     *
     * This is the name used by every Entry and Report screen.
     */
    @Column(nullable = false, length = 150)
    private String name;

    /**
     * Mandatory 10-digit mobile number.
     *
     * Must be unique in the database.
     */
    @Column(nullable = false, unique = true, length = 10)
    private String phone;

    /**
     * Kept only if existing database/business logic still uses it.
     * It is NOT part of the Job Worker master screen.
     */
    @Column(length = 20)
    private String challanNo;

    @Column(length = 255)
    private String address;

    @Column(precision = 15, scale = 2)
    private BigDecimal openingBalance;

    /**
     * JavaFX ComboBox displays the centralized Job Worker name.
     */
    @Override
    public String toString() {
        return name == null ? "" : name;
    }
}