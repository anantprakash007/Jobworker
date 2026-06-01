package com.jobwork.service;

import com.jobwork.domain.EntryStatus;
import com.jobwork.domain.JobWorker;
import com.jobwork.domain.MoneyReceipt;
import com.jobwork.domain.WorkerAccountLedgerRow;
import com.jobwork.repository.JobWorkerRepository;
import com.jobwork.repository.MoneyReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class MoneyService {

    private final MoneyReceiptRepository repo;
    private final JobWorkerRepository workerRepo;

    // =========================================================
    // 🔹 SAVE PAYMENT
    // =========================================================
    public MoneyReceipt save(MoneyReceipt receipt) {

        if (receipt.getJobWorker() == null) {
            throw new RuntimeException("Worker is required");
        }

        if (receipt.getAmount() == null) {
            throw new RuntimeException("Amount is required");
        }

        receipt.setAmount(receipt.getAmount().abs());

        if (receipt.getStatus() == null) {
            receipt.setStatus(EntryStatus.SUBMITTED);
        }

        return repo.save(receipt);
    }

    public long countDuplicate(Long workerId, String challanNo, LocalDate date) {
        return repo.countDuplicate(workerId, challanNo, date);
    }

    public MoneyReceipt getExistingDuplicate(Long workerId, String challanNo, LocalDate date) {
        List<MoneyReceipt> list = repo.findExistingDuplicates(workerId, challanNo, date);
        return list.isEmpty() ? null : list.get(0);
    }

    // =========================================================
    // 🔹 DELETE PAYMENT
    // =========================================================
    public void delete(Long id) {
        MoneyReceipt r = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        repo.deleteById(id);
    }

    // =========================================================
    // 🔹 FIND BY ID
    // =========================================================
    @Transactional(readOnly = true)
    public Optional<MoneyReceipt> findById(Long id) {
        return repo.findById(id);
    }

    // =========================================================
    // 🔹 SEARCH REPORT
    // =========================================================
    @Transactional(readOnly = true)
    public List<MoneyReceipt> searchReport(
            Long workerId,
            LocalDate from,
            LocalDate to,
            String challan) {

        Specification<MoneyReceipt> spec = Specification.where(null);

        if (workerId != null) {
            spec = spec.and((r, q, cb) ->
                    cb.equal(r.get("jobWorker").get("id"), workerId));
        }

        if (from != null && to != null) {
            spec = spec.and((r, q, cb) ->
                    cb.between(r.get("receiptDate"), from, to));
        } else if (from != null) {
            spec = spec.and((r, q, cb) ->
                    cb.greaterThanOrEqualTo(r.get("receiptDate"), from));
        } else if (to != null) {
            spec = spec.and((r, q, cb) ->
                    cb.lessThanOrEqualTo(r.get("receiptDate"), to));
        }

        if (challan != null && !challan.isBlank()) {
            spec = spec.and((r, q, cb) ->
                    cb.like(cb.lower(r.get("challanNo")),
                            "%" + challan.toLowerCase() + "%"));
        }

        return repo.findAll(spec);
    }

    // =========================================================
    // 🔹 RESTORE PAYMENT
    // =========================================================
    public void restorePayment(Long id) {
        MoneyReceipt r = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        r.setStatus(EntryStatus.SUBMITTED);
        repo.save(r);
    }

    public void overwrite(MoneyReceipt existing, MoneyReceipt incoming) {
        existing.setAmount(incoming.getAmount());
        existing.setRemark(incoming.getRemark());
        existing.setTransferMode(incoming.getTransferMode());
        existing.setReceiptDate(incoming.getReceiptDate());
        existing.setChallanNo(incoming.getChallanNo());
        repo.save(existing);
    }

    public void saveSafe(MoneyReceipt r) {
        long count = repo.countDuplicate(
                r.getJobWorker().getId(),
                r.getChallanNo(),
                r.getReceiptDate()
        );
        if (count > 0) {
            throw new RuntimeException("Duplicate blocked at service level");
        }
        repo.save(r);
    }

    public JobWorker findWorkerById(Long id) {
        return workerRepo.findById(id).orElse(null);
    }

    public JobWorker findByName(String name) {
        return workerRepo.findByNameIgnoreCase(name.trim()).orElse(null);
    }

    // =========================================================
    // 🔹 UNDO LAST PAYMENT
    // =========================================================
    public void undoLastPayment(Long workerId) {

        List<MoneyReceipt> list = repo.findByJobWorkerId(workerId);

        MoneyReceipt last = list.stream()
                .filter(r -> r.getStatus() != EntryStatus.CANCELLED)
                .max(Comparator.comparing(
                        MoneyReceipt::getReceiptDate,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .orElseThrow(() -> new RuntimeException("No payment found"));

        last.setStatus(EntryStatus.CANCELLED);
        repo.save(last);
    }

    public BigDecimal totalPaidToWorker(Long workerId) {
        return repo.sumPaidByWorker(workerId)
                .orElse(BigDecimal.ZERO);
    }

    // =========================================================
    // 🔹 ACCOUNT LEDGER (FINAL CORRECT)
    // =========================================================
    @Transactional(readOnly = true)
    public List<WorkerAccountLedgerRow> getLedger(
            Long workerId,
            LocalDate from,
            LocalDate to) {

        List<WorkerAccountLedgerRow> rows = new ArrayList<>();
        if (workerId == null) return rows;

        // 🔥 Opening Balance (correct)
        BigDecimal opening = (from != null)
                ? repo.findOpeningBalance(workerId, from)
                : BigDecimal.ZERO;
        LocalDate openDate = (from != null) ? from.minusDays(1) : null;

        rows.add(new WorkerAccountLedgerRow(
                openDate,
                "Opening Balance",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                opening,
                null
        ));

        List<MoneyReceipt> list;


        if (from != null && to != null) {
            list = repo.findByJobWorkerIdAndReceiptDateBetween(workerId, from, to);
        } else {
            list = repo.findByJobWorkerId(workerId);
        }

        list.sort(Comparator.comparing(
                MoneyReceipt::getReceiptDate,
                Comparator.nullsLast(Comparator.naturalOrder())
        ));

        BigDecimal balance = opening;

        for (MoneyReceipt m : list) {

            if (m.getStatus() == EntryStatus.CANCELLED) continue;

            BigDecimal debit = BigDecimal.ZERO;
            BigDecimal credit = BigDecimal.ZERO;

            switch (m.getEntryType()) {

                case ADVANCE -> {
                    credit = m.getAmount();
                    balance = balance.subtract(credit);
                }

                case PAYMENT -> {
                    debit = m.getAmount();
                    balance = balance.add(debit);
                }

                default -> {}
            }

            rows.add(new WorkerAccountLedgerRow(
                    m.getReceiptDate(),
                    m.getTransferMode().name(),
                    debit,
                    credit,
                    balance,
                    m.getChallanNo()
            ));
        }

        rows.add(new WorkerAccountLedgerRow(
                to,
                "Closing Balance",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                balance,
                null
        ));

        return rows;
    }
    // =========================================================
// 🔹 TOTAL PAID WITH DATE FILTER (VERY IMPORTANT)
// =========================================================
    public BigDecimal totalPaidToWorker(
            Long workerId,
            LocalDate from,
            LocalDate to
    ) {

        return repo.findByFilter(workerId, from, to)
                .stream()
                .filter(r -> r.getStatus() != EntryStatus.CANCELLED)
                .map(MoneyReceipt::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}