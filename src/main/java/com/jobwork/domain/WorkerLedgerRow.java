package com.jobwork.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkerLedgerRow {

  private LocalDate date;
  private String type;
  private BigDecimal amount;
  private BigDecimal balance;
  private String particular;

  private BigDecimal debit; // Challan
  private BigDecimal credit; // Payment
}
