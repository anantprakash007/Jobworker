package com.jobwork.service;

import com.jobwork.domain.*;
import com.jobwork.repository.JobWorkerRepository;
import com.jobwork.repository.MoneyReceiptRepository;
import com.jobwork.repository.ProductEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JobWorkerSummaryService  ── FIXED: GROUPED summary
 * ──────────────────────────────────────────────────────────────────
 * WHAT WAS WRONG:
 *   Previous version returned ONE ROW PER PRODUCT ENTRY (every DB row
 *   appeared separately). Screenshot showed 20 raw entries.
 *
 * WHAT IS CORRECT (as per user requirement):
 *   ONE ROW PER UNIQUE PRODUCT NAME, with:
 *     • Total Weight  = SUM of all weight values for that product name
 *     • Total Qty     = SUM of all quantity values for that product name
 *     • Total Picks   = from ProductName.totalPicks  (same for all entries
 *                       of the same product — taken from the ProductName entity)
 *     • Length        = from ProductName.length       (same logic)
 *     • Unit          = from ProductEntry.unit.name   (taken from first entry)
 *   Paisa, Rate, Total → start at 0, user fills them in the table
 *
 * Column order (from sketch):
 *   Sl.No | Product Name | Total Picks | Length | Wt (kg) | Qt | Unit
 *         | Paisa (₹) | Rate (₹) | Total (₹)
 *
 * Footer:
 *   Total          = SUM of all row Totals (driven by Rate × Qt edits)
 *   Advance Money  = editable TextField — pre-filled from MoneyReceipt
 *   Previous Money = editable TextField — pre-filled from MoneyReceipt
 *   Grand Total    = Total − Advance Money + Previous Money
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class JobWorkerSummaryService {

    private final ProductEntryRepository productRepo;
    private final MoneyReceiptRepository  moneyRepo;
    private final MoneyService moneyService;
    private final AccountLedgerService accountLedgerService;
    private final JobWorkerRepository workerRepo;

    /**
     * Build GROUPED summary rows.
     * Groups all ProductEntry records by ProductName, summing qty and weight.
     * One result row per unique ProductName.
     */
    public List<JobWorkerSummaryRow> buildSummaryRows(
            Long workerId, LocalDate from, LocalDate to) {

        List<ProductEntry> entries =
                productRepo.findAll().stream()
                        .filter(p -> p.getJobWorker() != null
                                && p.getJobWorker().getId().equals(workerId))
                        .filter(p -> p.getEntryDate() != null
                                && !p.getEntryDate().isBefore(from)
                                && !p.getEntryDate().isAfter(to))
                        .toList();
        // Use ProductName ID as the grouping key to preserve insertion order
        // Map key: ProductName.id (Integer), value: accumulated summary row
        Map<String, JobWorkerSummaryRow> grouped = new LinkedHashMap<>();

        for (ProductEntry pe : entries) {
            ProductName pn = pe.getProductName();

            // Group key: product name id (or name string if id is null)
          //  String key = pn != null
                //    ? (pn.getId() != null ? String.valueOf(pn.getId()) : pn.getName())
                //    : "__UNKNOWN__";
            String key =
                    (pn != null && pn.getId() != null ? pn.getId().toString() : "0") + "_" +
                            (pn != null && pn.getLength() != null ? pn.getLength().toString() : "0") + "_" +
                            (pe.getUnit() != null && pe.getUnit().getId() != null ? pe.getUnit().getId().toString() : "0");
            BigDecimal wt  = pe.getWeight()   != null ? pe.getWeight()   : BigDecimal.ZERO;
            BigDecimal qty = pe.getQuantity()  != null ? pe.getQuantity() : BigDecimal.ZERO;

            if (grouped.containsKey(key)) {
                // Accumulate into existing row
                JobWorkerSummaryRow existing = grouped.get(key);
                existing.setWeight(existing.getWeight().add(wt));
                existing.setQuantity(existing.getQuantity().add(qty));
               // 🔥 RECALCULATE TOTAL (VERY IMPORTANT)
                BigDecimal updatedTotal = existing.getQuantity()
                        .multiply(existing.getRate())
                        .setScale(2, RoundingMode.HALF_UP);

                existing.setTotal(updatedTotal);
            } else {
                // First entry for this ProductName — read metadata from entity
                String totalPicks = "";
                String length     = "";
                String unit       = "";
                String productNameStr = "";

                if (pn != null) {
                    productNameStr = pn.getName() != null ? pn.getName() : "";

                    // ── Total Picks from ProductName entity ──────────
                    // If your ProductName entity uses a different field name
                    // (e.g. totalPick, pickCount), update the getter here.
                    try {
                        if (pn.getTotalPicks() != null)
                            totalPicks = String.valueOf(pn.getTotalPicks());
                    } catch (Exception ignored) {}

                    // ── Length from ProductName entity ────────────────
                    // If your ProductName entity uses a different field name
                    // (e.g. fabricLength, meterLength), update the getter here.
                    try {
                        if (pn.getLength() != null)
                            length = String.valueOf(pn.getLength());
                    } catch (Exception ignored) {}
                }

                // ── Unit from ProductEntry ────────────────────────────
                if (pe.getUnit() != null && pe.getUnit().getName() != null)
                    unit = pe.getUnit().getName();
                BigDecimal paisa = pn.getDefaultPaisa() != null
                        ? pn.getDefaultPaisa()
                        : BigDecimal.ZERO;

                BigDecimal rate = pn.getDefaultRate() != null
                        ? pn.getDefaultRate()
                        : BigDecimal.ZERO;

// Total = Qty × Rate
                BigDecimal total = qty.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                grouped.put(key, new JobWorkerSummaryRow(
                        0,              // slNo — assigned below after grouping
                        productNameStr,
                        totalPicks,
                        length,
                        wt,
                        qty,
                        unit,
                        paisa,   // ✅ auto-filled
                        rate,    // ✅ auto-filled
                        total    // ✅ auto-calculated
                ));
            }
        }

        // Assign final Sl.No after grouping
        List<JobWorkerSummaryRow> result = new ArrayList<>(grouped.values());
        for (int i = 0; i < result.size(); i++)
            result.get(i).setSlNo(i + 1);
        // ================= PAYMENT INTEGRATION =================
/**
        BigDecimal totalWork = result.stream()
                .map(JobWorkerSummaryRow::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal paid = moneyService.totalPaidToWorker(workerId);

        BigDecimal balance = totalWork.subtract(paid);

        BigDecimal advance = BigDecimal.ZERO;

        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            advance = balance.abs();
            balance = BigDecimal.ZERO;
        }

        String status;
        if (balance.compareTo(BigDecimal.ZERO) == 0 && advance.compareTo(BigDecimal.ZERO) == 0) {
            status = "PAID";
        } else if (advance.compareTo(BigDecimal.ZERO) > 0) {
            status = "ADVANCE";
        } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
            status = "PARTIAL";
        } else {
            status = "UNPAID";
        }

// Apply to all rows
        for (JobWorkerSummaryRow row : result) {
            row.setPaid(paid);
            row.setBalance(balance);
            row.setAdvance(advance);
            row.setStatus(status);
        }*/
        return result;
    }

    /** Sum of all row totals (called after user edits rates/paisa). */
    public BigDecimal computeTotal(List<JobWorkerSummaryRow> rows) {
        return rows.stream()
                .map(JobWorkerSummaryRow::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Advance Money = total paid to worker in the date range.
     * Pre-fills the editable Advance Money TextField on Search.
     */
    //public BigDecimal advanceMoney(Long workerId, LocalDate from, LocalDate to) {
      // BigDecimal v = moneyRepo.sumAmountByWorkerAndDateRange(workerId, from, to);
        //return v != null ? v : BigDecimal.ZERO;
    //}

    /**
     * Previous Money = total paid BEFORE the from date.
     * Pre-fills the editable Previous Money TextField on Search.
     */
   // public BigDecimal previousMoney(Long workerId, LocalDate from) {
      // BigDecimal v = moneyRepo.sumAmountByWorkerBefore(workerId, from);
      //  return v != null ? v : BigDecimal.ZERO;
    //}

    /**
     * Grand Total formula (from sketch):
     *   Grand Total = Total − Advance Money + Previous Money
     */
    public BigDecimal grandTotal(
            BigDecimal total,
            BigDecimal advanceMoney,
            BigDecimal previousMoney) {
        return total.subtract(advanceMoney).add(previousMoney);
    }
    public SummaryTotals calculateTotals(
            Long workerId,
            LocalDate from,
            LocalDate to,
            List<JobWorkerSummaryRow> rows
    ) {

        // 🔥 TOTAL WORK
        BigDecimal totalWork = rows.stream()
                .map(JobWorkerSummaryRow::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 🔥 TOTAL PAID (WITH DATE FILTER)
        BigDecimal totalPaid =
                moneyService.totalPaidToWorker(workerId, from, to);

        // 🔥 OPENING FROM LEDGER (SOURCE OF TRUTH)
        JobWorker worker = workerRepo.findById(workerId).orElse(null);

        BigDecimal opening = (worker != null && worker.getOpeningBalance() != null)
                ? worker.getOpeningBalance()
                : BigDecimal.ZERO;
        // 🔥 DIFFERENCE
        BigDecimal difference = opening
                .add(totalWork)
                .subtract(totalPaid);

        return new SummaryTotals(
                totalWork,
                totalPaid,
                opening,
                difference
        );
    }
}