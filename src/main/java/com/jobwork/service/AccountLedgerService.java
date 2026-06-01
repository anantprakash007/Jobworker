package com.jobwork.service;

import com.jobwork.domain.*;
import com.jobwork.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class AccountLedgerService {

    @Autowired private ProductEntryRepository productRepo;
    @Autowired private MoneyReceiptRepository moneyRepo;
    @Autowired private JobWorkerRepository workerRepo;
    @Autowired private OpeningAdjustmentRepository adjRepo;

    // =========================================================
    // 🔥 MAIN LEDGER
    // =========================================================
    public List<WorkerAccountLedgerRow> getLedger(
            Long workerId,
            LocalDate from,
            LocalDate to
    ) {

        List<ProductEntry> workList =
                productRepo.findByWorkerAndDateRange(workerId, from, to);

        List<MoneyReceipt> payList =
                moneyRepo.findByJobWorkerId(workerId);

        List<WorkerAccountLedgerRow> ledger = new ArrayList<>();

        // =========================================================
        // 🔥 1. OPENING BALANCE (FINAL CORRECT)
        // =========================================================

        // 🔥 GET MANUAL OPENING
        JobWorker worker = workerRepo.findById(workerId).orElse(null);

        BigDecimal manualOpening =
                (worker != null && worker.getOpeningBalance() != null)
                        ? worker.getOpeningBalance()
                        : BigDecimal.ZERO;

        // 🔥 BASE OPENING
        BigDecimal opening = manualOpening;

        // 🔥 ADD PREVIOUS TRANSACTIONS ONLY IF DATE FILTER EXISTS
        if (from != null) {

            BigDecimal workBefore =
                    productRepo.sumTotal(workerId, null, from);

            BigDecimal paidBefore =
                    moneyRepo.findOpeningBalance(workerId, from);

            if (workBefore != null)
                opening = opening.add(workBefore);

            if (paidBefore != null)
                opening = opening.add(paidBefore);
        }

        // 🔥 OPENING ROW
        WorkerAccountLedgerRow open = new WorkerAccountLedgerRow();
        open.setDate(from != null ? from.minusDays(1) : null);
        open.setParticular("Opening Balance");
        open.setDebit(BigDecimal.ZERO);
        open.setCredit(BigDecimal.ZERO);
        open.setBalance(opening);

        ledger.add(open);

        // =========================================================
        // 🔥 2. GROUP WORK (DEBIT)
        // =========================================================
        Map<String, BigDecimal> grouped = new LinkedHashMap<>();

        for (ProductEntry p : workList) {
            grouped.merge(
                    p.getChallanNo(),
                    p.getTotalAmount(),
                    BigDecimal::add
            );
        }

        for (Map.Entry<String, BigDecimal> e : grouped.entrySet()) {

            WorkerAccountLedgerRow row = new WorkerAccountLedgerRow();

            row.setDate(
                    workList.stream()
                            .filter(p -> p.getChallanNo().equals(e.getKey()))
                            .map(ProductEntry::getEntryDate)
                            .min(LocalDate::compareTo)
                            .orElse(null)
            );

            row.setParticular("Challan #" + e.getKey());
            row.setDebit(e.getValue());
            row.setCredit(BigDecimal.ZERO);
            row.setChallanNo(e.getKey());

            ledger.add(row);
        }

        // =========================================================
        // 🔥 3. PAYMENTS (CREDIT)
        // =========================================================
        for (MoneyReceipt m : payList) {

            if (m.getStatus() != null &&
                    m.getStatus().name().equalsIgnoreCase("CANCELLED"))
                continue;

            WorkerAccountLedgerRow row = new WorkerAccountLedgerRow();

            row.setDate(m.getReceiptDate());

            if (m.getEntryType() == EntryType.ADVANCE) {
                row.setParticular("Advance");
            } else {
                row.setParticular("Payment");
            }

            row.setDebit(BigDecimal.ZERO);
            row.setCredit(m.getAmount());

            ledger.add(row);
        }

        // =========================================================
        // 🔥 4. OPENING ADJUSTMENTS
        // =========================================================
        List<OpeningAdjustment> adjList =
                adjRepo.findByWorkerIdAndDateBetween(workerId, from, to);

        for (OpeningAdjustment a : adjList) {

            WorkerAccountLedgerRow row = new WorkerAccountLedgerRow();

            row.setDate(a.getDate());
            row.setParticular("Adjustment: " +
                    (a.getRemark() != null ? a.getRemark() : ""));

            if (a.getEntryType() == EntryType.ADVANCE) {
                row.setDebit(a.getAmount());   // increase balance
                row.setCredit(BigDecimal.ZERO);
            } else {
                row.setDebit(BigDecimal.ZERO);
                row.setCredit(a.getAmount());  // decrease balance
            }

            ledger.add(row);
        }

        // =========================================================
        // 🔥 5. SORT BY DATE
        // =========================================================
        ledger.sort((a, b) -> {
            if (a.getDate() == null) return -1;
            if (b.getDate() == null) return 1;
            return a.getDate().compareTo(b.getDate());
        });

        // =========================================================
        // 🔥 6. RUNNING BALANCE
        // =========================================================
        BigDecimal balance = opening;

        for (WorkerAccountLedgerRow r : ledger) {

            if ("Opening Balance".equals(r.getParticular())) {
                r.setBalance(opening);
                continue;
            }

            BigDecimal debit = r.getDebit() == null ? BigDecimal.ZERO : r.getDebit();
            BigDecimal credit = r.getCredit() == null ? BigDecimal.ZERO : r.getCredit();

            balance = balance.add(debit).subtract(credit);

            r.setBalance(balance);
        }

        // =========================================================
        // 🔥 7. CLOSING BALANCE
        // =========================================================
        WorkerAccountLedgerRow closing = new WorkerAccountLedgerRow();
        closing.setDate(to);
        closing.setParticular("Closing Balance");
        closing.setDebit(BigDecimal.ZERO);
        closing.setCredit(BigDecimal.ZERO);
        closing.setBalance(balance);

        ledger.add(closing);

        return ledger;
    }

    // =========================================================
    // 🔥 OPENING BALANCE API
    // =========================================================
    public BigDecimal getOpeningBalance(Long workerId, LocalDate fromDate) {

        List<WorkerAccountLedgerRow> list =
                getLedger(workerId, null, fromDate.minusDays(1));

        BigDecimal debit = list.stream()
                .map(r -> r.getDebit() == null ? BigDecimal.ZERO : r.getDebit())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal credit = list.stream()
                .map(r -> r.getCredit() == null ? BigDecimal.ZERO : r.getCredit())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return debit.subtract(credit);
    }
}