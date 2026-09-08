package com.jobwork.service;

import com.jobwork.domain.ChallanReportRow;
import com.jobwork.domain.DuplicateCheckResult;
import com.jobwork.domain.DuplicateReviewRow;
import com.jobwork.domain.ProductEntry;
import com.jobwork.repository.ProductEntryRepository;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ProductService {

    private final ProductEntryRepository repo;

    // ── Write ────────────────────────────────────────────────────
    public ProductEntry save(ProductEntry entry) { return repo.save(entry); }
    public void delete(Long id)                  { repo.deleteById(id); }

    // ── Read ─────────────────────────────────────────────────────
    @Transactional(readOnly = true)
  //  public List<ProductEntry> findByChallan(String challanNo) {
        //return repo.findByChallanNo(challanNo);
    //}
    public List<ProductEntry> findByChallanAndWorker(Long workerId, String challanNo) {
        return repo.findByJobWorkerIdAndChallanNo(workerId, challanNo);
    }
    @Transactional(readOnly = true)
    public Optional<ProductEntry> findById(Long id) { return repo.findById(id); }

    @Transactional(readOnly = true)
    public List<ProductEntry> findByJobWorker(Long workerId) {
        return repo.findByJobWorkerId(workerId);
    }

    // ── Single duplicate check  (called on every Add Row click) ─────────
    /**
     * Returns true if a row with the same
     * (jobWorker.id, challanNo, entryDate, productName.id)
     * already exists in the database.
     *
     * Uses JPQL COUNT with explicit JOIN — avoids both the
     * "Unknown column" native-SQL failure and the JPQL
     * TerminalPathException from path-traversal on EAGER associations.
     */
    @Transactional(readOnly = true)
    public boolean isDuplicateQty(Long workerId, String challanNo,
                                  LocalDate date, Integer productId, Long currentId) {

        if (workerId == null || challanNo == null || date == null || productId == null) {
            return false;
        }

        long count = repo.countDuplicate(
                workerId,
                challanNo.trim(),
                date,
                productId,
                currentId   // 🔥 CRITICAL
        );

        return count > 0;
    }

    /**
     * Fetches the full existing ProductEntry for display in the review popup.
     * Only call this after isDuplicate() returns true.
     */
    @Transactional(readOnly = true)
    public Optional<ProductEntry> getExistingDuplicate(
            Long workerId, String challanNo, LocalDate date, Integer productNameId) {

        return repo.findOneDuplicate(workerId, challanNo, date, productNameId);
    }

    // ── Batch duplicate check  (called from onSave / onFinalSubmit) ──────
    /**
     * Checks ALL table rows at once before a bulk save.
     * Fires exactly ONE JPQL query regardless of how many rows are submitted.
     *
     * Returns:
     *   rows          → (existing DB entry, incoming entry) pairs needing review
     *   nonDuplicates → incoming rows with no DB match — safe to save directly
     */
    @Transactional(readOnly = true)
    public DuplicateCheckResult checkDuplicates(Long workerId, String challanNo,
                                                LocalDate date,
                                                List<ProductEntry> incoming) {

        if (workerId == null || challanNo == null || date == null || incoming.isEmpty())
            return new DuplicateCheckResult(List.of(), new ArrayList<>(incoming));

        List<Integer> nameIds = incoming.stream()
                .filter(e -> e.getProductName() != null && e.getProductName().getId() != null)
                .map(e -> e.getProductName().getId())
                .distinct()
                .collect(Collectors.toList());

        if (nameIds.isEmpty())
            return new DuplicateCheckResult(List.of(), new ArrayList<>(incoming));

        log.debug("checkDuplicates → workerId={} challanNo={} date={} nameIds={}",
                workerId, challanNo, date, nameIds);

        List<ProductEntry> existingList =
                repo.findDuplicateBatch(workerId, challanNo, date, nameIds);

        log.debug("checkDuplicates → {} existing rows found", existingList.size());

        Map<Integer, ProductEntry> existingMap = existingList.stream()
                .filter(e -> e.getProductName() != null)
                .collect(Collectors.toMap(
                        e -> e.getProductName().getId(),
                        e -> e,
                        (a, b) -> a));

        List<DuplicateReviewRow> duplicateRows = new ArrayList<>();
        List<ProductEntry>       nonDuplicates = new ArrayList<>();

        for (ProductEntry inc : incoming) {
            if (inc.getProductName() == null || inc.getProductName().getId() == null) {
                nonDuplicates.add(inc); continue;
            }
            ProductEntry existing = existingMap.get(inc.getProductName().getId());
            if (existing != null) {
                // 🔥 CRITICAL FIX → IGNORE SAME RECORD
                if (inc.getId() != null && existing.getId().equals(inc.getId())) {
                    nonDuplicates.add(inc);   // ✅ same row → NOT duplicate
                    continue;
                }


                duplicateRows.add(new DuplicateReviewRow(existing, inc));
            } else {
                nonDuplicates.add(inc);
            }
        }

        log.debug("checkDuplicates → duplicates={} clean={}",
                duplicateRows.size(), nonDuplicates.size());

        return new DuplicateCheckResult(duplicateRows, nonDuplicates);
    }

    // ── Apply re-entry decision ───────────────────────────────────────────
    /**
     * Overwrites checked (selected) duplicate rows in DB.
     * Saves non-duplicate rows as new entries.
     * Unchecked rows are silently skipped — existing data unchanged.
     */
    @Transactional
    public void applyReEntry(List<DuplicateReviewRow> selectedRows,
                             List<ProductEntry>       nonDuplicates) {

        for (DuplicateReviewRow row : selectedRows) {
            if (!row.isSelected()) continue;

            ProductEntry existing = row.getExistingEntry();
            ProductEntry incoming = row.getIncomingEntry();

            existing.setQuantity(incoming.getQuantity());
            existing.setWeight(incoming.getWeight());
            existing.setWeightPerPiece(incoming.getWeightPerPiece());
            existing.setProductType(incoming.getProductType());
            existing.setUnit(incoming.getUnit());
            existing.setStatus(incoming.getStatus());
            if (incoming.getReceiptPath() != null)
                existing.setReceiptPath(incoming.getReceiptPath());

            repo.save(existing);
            log.info("applyReEntry → overwrote id={} name={}",
                    existing.getId(),
                    existing.getProductName() != null
                            ? existing.getProductName().getName() : "?");
        }

        for (ProductEntry e : nonDuplicates) {
            repo.save(e);
            log.info("applyReEntry → saved new entry name={}",
                    e.getProductName() != null ? e.getProductName().getName() : "?");
        }
    }

    // ── Report search ─────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<ProductEntry> searchReport(Long workerId, LocalDate from, LocalDate to,
                                           String challan, Integer typeId, Integer nameId) {

        Specification<ProductEntry> spec = Specification.where(null);

        if (workerId != null)
            spec = spec.and((r, q, cb) -> cb.equal(r.get("jobWorker").get("id"), workerId));

        if (from != null && to != null)
            spec = spec.and((r, q, cb) -> cb.between(r.get("entryDate"), from, to));
        else if (from != null)
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("entryDate"), from));
        else if (to != null)
            spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("entryDate"), to));

        if (challan != null && !challan.isBlank())
            spec = spec.and((r, q, cb) ->
                    cb.like(cb.lower(r.get("challanNo")), "%" + challan.toLowerCase() + "%"));

        if (typeId != null)
            spec = spec.and((r, q, cb) -> {
                var join = r.join("productType", JoinType.LEFT);
                return cb.equal(join.get("id"), typeId);
            });

        if (nameId != null)
            spec = spec.and((r, q, cb) -> {
                var join = r.join("productName", JoinType.LEFT);
                return cb.equal(join.get("id"), nameId);
            });

        return repo.findAll(spec);
    }

    // ── Summary ───────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<Object[]> productWiseSummary(Long workerId, LocalDate from, LocalDate to) {
        return repo.productWiseSummary(workerId, from, to);
    }

    public List<ChallanReportRow> getChallanReport(Long workerId,
                                                   LocalDate from, LocalDate to) {
        List<Object[]> raw = repo.challanWiseReport(workerId, from, to);
        List<ChallanReportRow> list = new ArrayList<>();
        for (Object[] r : raw)
            list.add(new ChallanReportRow(
                    (LocalDate)  r[0],
                    (String)     r[1],
                    (String)     r[2],
                    (BigDecimal) r[3]));
        return list;
    }
}