package com.jobwork.domain;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SummaryTotals {

  private BigDecimal totalWork;
  private BigDecimal totalPaid;

  private BigDecimal opening; // ✅ instead of balance
  private BigDecimal difference; // work - paid
}
