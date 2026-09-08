package com.jobwork.service;

import com.jobwork.domain.BheemDuplicateReviewRow;
import com.jobwork.domain.BheemEntry;
import com.jobwork.domain.BheemImportRow;
import com.jobwork.repository.BheemEntryRepository;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class BheemService {

    private final BheemEntryRepository repo;

    // ── Write ─────────────────────────────────────────────────────
    @Transactional
    public BheemEntry save(BheemEntry entry) { return repo.save(entry); }
    public void delete(Long id)              { repo.deleteById(id); }

    // ── Read ──────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Optional<BheemEntry> findById(Long id) { return repo.findById(id); }

    @Transactional(readOnly = true)
    public List<BheemEntry> findByJobWorker(Long workerId) {
        return repo.findByJobWorkerId(workerId);
    }

    // ── Duplicate check  (called from onSave / onFinalSubmit) ────
    /**
     * Returns true if the DB already has a row with:
     *   (jobWorker.id, challanNo, entryDate, bheemName.id, taar.id)
     *
     * Uses JPQL COUNT with explicit JOIN — no native SQL, no path
     * traversal on EAGER @ManyToOne, no "Unknown column" risk.
     */
    @Transactional(readOnly = true)
    public boolean isDuplicate(Long workerId, String challanNo, LocalDate date,
                               Integer bheemNameId, Integer taarId) {

        if (workerId == null || challanNo == null || challanNo.isBlank()
                || date == null || bheemNameId == null || taarId == null) {
            log.warn("BheemService.isDuplicate skipped — null key");
            return false;
        }
        long count = repo.countDuplicate(workerId, challanNo, date, bheemNameId, taarId);
        log.debug("BheemService.isDuplicate → count={} for challanNo={} bheemNameId={} taarId={}",
                count, challanNo, bheemNameId, taarId);
        return count > 0;
    }

    /**
     * Fetches the first existing row for side-by-side display in popup.
     * Returns List.get(0) — safe when 2+ duplicates already exist in DB.
     */
    @Transactional(readOnly = true)
    public Optional<BheemEntry> getExistingDuplicate(
            Long workerId, String challanNo, LocalDate date,
            Integer bheemNameId, Integer taarId) {

        List<BheemEntry> results =
                repo.findExistingDuplicates(workerId, challanNo, date, bheemNameId, taarId);

        if (results.isEmpty()) {
            log.warn("BheemService.getExistingDuplicate → count>0 but list empty (race condition)");
            return Optional.empty();
        }
        if (results.size() > 1)
            log.warn("getExistingDuplicate → {} duplicates for challanNo={} — showing first id={}",
                    results.size(), challanNo, results.get(0).getId());

        return Optional.of(results.get(0));
    }

    /**
     * Overwrites the existing DB row with incoming values.
     * Also deletes any extra duplicate rows (DB cleanup for pre-existing duplicates).
     *
     * Called by BheemDuplicateReviewController when user clicks Confirm Re-Entry.
     */
    @Transactional
    public void applyReEntry(BheemDuplicateReviewRow row) {
        if (!row.isSelected()) return;

        BheemEntry existing = row.getExistingEntry();
        BheemEntry incoming = row.getIncomingEntry();

        existing.setDeliveryLocation(incoming.getDeliveryLocation());
        existing.setWrapper(incoming.getWrapper());
        existing.setYarnType(incoming.getYarnType());
        existing.setColour(incoming.getColour());
        existing.setWeight(incoming.getWeight());
        existing.setStatus(incoming.getStatus());
        if (incoming.getReceiptPath() != null)
            existing.setReceiptPath(incoming.getReceiptPath());

        repo.save(existing);
        log.info("BheemService.applyReEntry → overwrote id={}", existing.getId());

        // Delete any extra duplicate rows beyond the first
        if (incoming.getJobWorker() != null && incoming.getChallanNo() != null
                && incoming.getEntryDate() != null
                && incoming.getBheemName() != null && incoming.getTaar() != null) {

            List<BheemEntry> extras = repo.findExistingDuplicates(
                    incoming.getJobWorker().getId(),
                    incoming.getChallanNo(),
                    incoming.getEntryDate(),
                    incoming.getBheemName().getId(),
                    incoming.getTaar().getId());

            for (BheemEntry extra : extras) {
                if (!extra.getId().equals(existing.getId())) {
                    repo.deleteById(extra.getId());
                    log.info("BheemService.applyReEntry → deleted extra duplicate id={}", extra.getId());
                }
            }
        }
    }

    // ── Import: save a validated import row ───────────────────────
    /**
     * Converts a valid BheemImportRow into a BheemEntry and saves it.
     * Duplicate check is done upstream in BheemImportPreviewController.
     */
    @Transactional
    public void saveFromImport(BheemImportRow importRow, com.jobwork.domain.EntryStatus status) {
        BheemEntry entry = BheemEntry.builder()
                .jobWorker(importRow.getJobWorker())
                .deliveryLocation(importRow.getDeliveryLocation())
                .challanNo(importRow.getChallanNo())
                .entryDate(importRow.getEntryDate())
                .bheemName(importRow.getBheemName())
                .taar(importRow.getTaar())
                .wrapper(importRow.getWrapper())
                .yarnType(importRow.getYarnType())
                .colour(importRow.getColour())
                .weight(importRow.getWeight())
                .status(status)
                .build();
        repo.save(entry);
        log.info("BheemService.saveFromImport → saved challanNo={} bheemName={}",
                importRow.getChallanNo(),
                importRow.getBheemName() != null ? importRow.getBheemName().getName() : "?");
    }

    // ── Report search ─────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<BheemEntry> searchReport(Long workerId, Long wrapperId,
                                         LocalDate from, LocalDate to,
                                         String challan, Integer bheemNameId) {

        Specification<BheemEntry> spec = Specification.where(null);

        if (workerId != null)
            spec = spec.and((r, q, cb) -> cb.equal(r.get("jobWorker").get("id"), workerId));

        if (wrapperId != null)
            spec = spec.and((r, q, cb) -> {
                var join = r.join("wrapper", JoinType.LEFT);
                return cb.equal(join.get("id"), wrapperId);
            });

        if (from != null && to != null)
            spec = spec.and((r, q, cb) -> cb.between(r.get("entryDate"), from, to));
        else if (from != null)
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("entryDate"), from));
        else if (to != null)
            spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("entryDate"), to));

        if (challan != null && !challan.isBlank())
            spec = spec.and((r, q, cb) ->
                    cb.like(cb.lower(r.get("challanNo")), "%" + challan.toLowerCase() + "%"));

        if (bheemNameId != null)
            spec = spec.and((r, q, cb) -> {
                var join = r.join("bheemName", JoinType.LEFT);
                return cb.equal(join.get("id"), bheemNameId);
            });

        return repo.findAll(spec);
    }

    // ── Summary ───────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<Object[]> qualityWiseSummary(Long workerId, LocalDate from, LocalDate to) {
        return repo.qualityWiseSummary(workerId, from, to);
    }
    public void overwrite(BheemEntry existing, BheemEntry incoming) {

        existing.setWrapper(incoming.getWrapper());
        existing.setYarnType(incoming.getYarnType());
        existing.setColour(incoming.getColour());
        existing.setWeight(incoming.getWeight());

        repo.save(existing);
    }
    public long countDuplicate(Long workerId,
                               String challan,
                               LocalDate date,
                               Integer bheemId,
                               Integer taarId) {

        return repo.countDuplicate(workerId, challan, date, bheemId, taarId);
    }


}