package com.jobwork.service;

import com.jobwork.domain.*;
import com.jobwork.repository.YarnEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * YarnImportService
 * ─────────────────────────────────────────────────────────────────
 * Handles all DB operations for the Yarn Excel Import flow.
 * Separated from YarnService to keep import concerns isolated.
 *
 * Duplicate key: (workerId, challanNo, entryDate, yarnType.id, bagPiece)
 *
 * All checks use workerId (Long) — NOT worker name strings.
 * Worker resolution happens upstream in YarnImportPreviewController.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class YarnImportService {

    private final YarnEntryRepository repo;

    // ── Duplicate check ───────────────────────────────────────────

    /**
     * Returns true if a YarnEntry already exists for:
     *   (workerId, challanNo, entryDate, yarnType.id, bagPiece)
     *
     * Uses JPQL COUNT with explicit JOIN:
     *   • No native SQL → no "Unknown column" naming errors
     *   • No Optional.getSingleResult() → no NonUniqueResultException
     *   • No path traversal → no TerminalPathException on EAGER associations
     *
     * @param workerId resolved jobWorker.id — NOT worker name
     */
    @Transactional(readOnly = true)
    public boolean isDuplicate(Long workerId, String challanNo,
                               LocalDate date, Integer yarnTypeId, String bagPiece) {

        if (workerId == null || challanNo == null || challanNo.isBlank()
                || date == null || yarnTypeId == null || bagPiece == null) {
            log.warn("YarnImportService.isDuplicate skipped — null key: " +
                            "workerId={} challanNo={} date={} yarnTypeId={} bagPiece={}",
                    workerId, challanNo, date, yarnTypeId, bagPiece);
            return false;
        }

        long count = repo.countDuplicate(workerId, challanNo, date, yarnTypeId, bagPiece);
        log.debug("YarnImportService.isDuplicate → count={} workerId={} challanNo={} " +
                        "yarnTypeId={} bagPiece={}",
                count, workerId, challanNo, yarnTypeId, bagPiece);
        return count > 0;
    }

    /**
     * Fetches first existing row for overwrite.
     * Returns List.get(0) — safe when 2+ duplicates already exist in DB.
     */
    @Transactional(readOnly = true)
    public Optional<YarnEntry> getExistingDuplicate(Long workerId, String challanNo,
                                                    LocalDate date,
                                                    Integer yarnTypeId, String bagPiece) {

        List<YarnEntry> results =
                repo.findExistingDuplicates(workerId, challanNo, date, yarnTypeId, bagPiece);

        if (results.isEmpty()) {
            log.warn("YarnImportService.getExistingDuplicate → count>0 but fetch empty " +
                    "(race condition) workerId={} challanNo={}", workerId, challanNo);
            return Optional.empty();
        }
        if (results.size() > 1)
            log.warn("YarnImportService → {} duplicates workerId={} challanNo={} — using id={}",
                    results.size(), workerId, challanNo, results.get(0).getId());

        return Optional.of(results.get(0));
    }

    // ── Save from import ──────────────────────────────────────────

    /**
     * Saves a valid, non-duplicate import row as a new SUBMITTED YarnEntry.
     * Uses workerId from importRow.getJobWorker().getId() — NOT worker name.
     */
    @Transactional
    public void saveFromImport(YarnImportRow importRow) {
        YarnEntry entry = YarnEntry.builder()
                .jobWorker(importRow.getJobWorker())           // entity resolved upstream
                .deliveryLocation(importRow.getDeliveryLocation())
                .challanNo(importRow.getChallanNo())
                .entryDate(importRow.getEntryDate())
                .yarnType(importRow.getYarnType())
                .colour(importRow.getColour())
                .bagPiece(importRow.getBagPiece())
                .wtOfBags(importRow.getWtOfBags())
                .noOfBags(importRow.getNoOfBags())
                .noOfCones(importRow.getNoOfCones())
                .netWeight(importRow.getNetWeight())
                .status("SUBMITTED")
                .build();

        repo.save(entry);
        log.info("YarnImportService.saveFromImport → workerId={} challanNo={} yarn={} bagPiece={}",
                importRow.getJobWorker() != null ? importRow.getJobWorker().getId() : null,
                importRow.getChallanNo(),
                importRow.getYarnType() != null ? importRow.getYarnType().getName() : "?",
                importRow.getBagPiece());
    }

    // ── Overwrite duplicate ───────────────────────────────────────

    /**
     * Overwrites first existing duplicate with import data.
     * Also deletes any extra duplicate rows (pre-existing dirty data cleanup).
     *
     * Uses workerId (Long) from importRow.getJobWorker().getId() — NOT name.
     *
     * @return true if succeeded; false if keys missing
     */
    @Transactional
    public boolean overwriteDuplicate(YarnImportRow importRow) {
        if (importRow.getJobWorker() == null || importRow.getYarnType() == null
                || importRow.getBagPiece() == null) {
            log.warn("YarnImportService.overwriteDuplicate → missing keys at row {}",
                    importRow.getRowNumber());
            return false;
        }

        Long workerId = importRow.getJobWorker().getId();   // ← workerId

        List<YarnEntry> existing = repo.findExistingDuplicates(
                workerId,
                importRow.getChallanNo(),
                importRow.getEntryDate(),
                importRow.getYarnType().getId(),
                importRow.getBagPiece());

        if (existing.isEmpty()) {
            // Race condition — save as new entry
            log.warn("YarnImportService.overwriteDuplicate → fetch empty at row {}. Saving fresh.",
                    importRow.getRowNumber());
            saveFromImport(importRow);
            return true;
        }

        // Overwrite first (oldest) row — keep same DB id
        YarnEntry primary = existing.get(0);
        primary.setDeliveryLocation(importRow.getDeliveryLocation());
        primary.setYarnType(importRow.getYarnType());
        primary.setColour(importRow.getColour());
        primary.setWtOfBags(importRow.getWtOfBags());
        primary.setNoOfBags(importRow.getNoOfBags());
        primary.setNoOfCones(importRow.getNoOfCones());
        primary.setNetWeight(importRow.getNetWeight());
        primary.setStatus("SUBMITTED");
        repo.save(primary);

        log.info("YarnImportService.overwriteDuplicate → overwrote id={} workerId={} challanNo={}",
                primary.getId(), workerId, importRow.getChallanNo());

        // Delete extra duplicates beyond the first (pre-existing dirty data)
        for (int i = 1; i < existing.size(); i++) {
            repo.deleteById(existing.get(i).getId());
            log.info("YarnImportService → deleted extra dup id={}", existing.get(i).getId());
        }

        return true;
    }
}