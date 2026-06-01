package com.jobwork.domain;
import jakarta.persistence.*;
import jakarta.persistence.ManyToOne;
import lombok.*;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "bheem_entry")
public class BheemEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "job_worker_id", nullable = false)
    private JobWorker jobWorker;

    private String challanNo;
    private LocalDate entryDate;
    //@ManyToOne(fetch = FetchType.EAGER)
    //@JoinColumn(name = "yarn_type_id")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "bheem_name_id", nullable = false)
    private BheemName bheemName;  // e.g. "Dhoti"
   // private Integer taar;        // thread count
    private BigDecimal weight;
    private String colour;
    @ManyToOne
    @JoinColumn(name = "delivery_location_id")
    private DeliveryLocation deliveryLocation;
    private String receiptPath;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "yarn_type_id")
    private YarnType yarnType;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "wrapper_id")
    private Wrapper wrapper;
    @Enumerated(EnumType.STRING)
    private EntryStatus status;
    @ManyToOne
    @JoinColumn(name = "taar_id")
    private Taar taar;

}
