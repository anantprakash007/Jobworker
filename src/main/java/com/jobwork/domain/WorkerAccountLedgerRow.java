package com.jobwork.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkerAccountLedgerRow {

    private LocalDate date;
    private String particular;
    private BigDecimal debit;
    private BigDecimal credit;
    private BigDecimal balance;
    private String challanNo;
}