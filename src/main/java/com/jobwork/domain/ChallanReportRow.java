package com.jobwork.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ChallanReportRow {

    private LocalDate date;
    private String challan;
    private String worker;
    private BigDecimal amount;

    public ChallanReportRow(LocalDate date, String challan, String worker, BigDecimal amount) {
        this.date = date;
        this.challan = challan;
        this.worker = worker;
        this.amount = amount;
    }

    public LocalDate getDate() { return date; }
    public String getChallan() { return challan; }
    public String getWorker() { return worker; }
    public BigDecimal getAmount() { return amount; }
}