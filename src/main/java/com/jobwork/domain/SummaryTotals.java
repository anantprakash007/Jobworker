package com.jobwork.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class SummaryTotals {

    private BigDecimal totalWork;
    private BigDecimal totalPaid;

    private BigDecimal opening;     // ✅ instead of balance
    private BigDecimal difference;  // work - paid
}