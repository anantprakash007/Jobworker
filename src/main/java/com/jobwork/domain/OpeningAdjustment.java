package com.jobwork.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "opening_adjustment")
public class OpeningAdjustment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne private JobWorker worker;

  private LocalDate date;

  private BigDecimal amount;

  // DR = increase balance, CR = decrease
  @Enumerated(EnumType.STRING)
  private EntryType entryType;

  private String remark;
}
