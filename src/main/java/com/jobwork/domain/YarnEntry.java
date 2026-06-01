package com.jobwork.domain;
import jakarta.persistence.*;
import jakarta.persistence.ManyToOne;
import lombok.*;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "yarn_entry")
public class YarnEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "job_worker_id", nullable = false)
    private JobWorker jobWorker;

    private String challanNo;
    private LocalDate entryDate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "delivery_location_id")
    private DeliveryLocation deliveryLocation;

    @Enumerated(EnumType.STRING)
    private BagOrPiece bagOrPiece;   // BAG | PIECE
    @Builder.Default
    private BigDecimal amount = BigDecimal.ZERO;
    private Integer noOfBags;
    private Integer noOfCones;
   // private String yarnCount;        // e.g. "40s", "60s"
    private String colour;
    @Builder.Default
    private BigDecimal netWeight = BigDecimal.ZERO;
    private String receiptPath;
    private String bagPiece;
    private String wtOfBags;
    private String status = "DRAFT";
    @ManyToOne
    private YarnType yarnType;
}
