package com.jobwork.service;

import com.jobwork.domain.YarnDuplicateCheckResult;
import com.jobwork.domain.YarnDuplicateReviewRow;
import com.jobwork.domain.YarnEntry;
import com.jobwork.domain.YarnImportRow;
import com.jobwork.repository.YarnEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class YarnService {

    private final YarnEntryRepository repo;

    @Value("${app.upload.receipts-dir:uploads/receipts}")
    private String receiptsDir;

    // ─────────────────────────────────────────────
    // BASIC CRUD
    // ─────────────────────────────────────────────

    public YarnEntry save(YarnEntry entry) {
        return repo.save(entry);
    }

    public void deleteById(Long id) {
        repo.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Optional<YarnEntry> findById(Long id) {
        return repo.findById(id);
    }

    @Transactional(readOnly = true)
    public List<YarnEntry> findByJobWorker(Long workerId) {
        return repo.findByJobWorkerId(workerId);
    }

    // ─────────────────────────────────────────────
    // SEARCH / REPORT
    // ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<YarnEntry> searchReport(Long workerId, LocalDate from, LocalDate to,
                                        String challan, String yarnCount) {

        Specification<YarnEntry> spec = Specification.where(null);

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

        if (yarnCount != null && !yarnCount.isBlank())
            spec = spec.and((r, q, cb) ->
                    cb.equal(r.get("yarnType").get("name"), yarnCount));

        return repo.findAll(spec);
    }

    @Transactional(readOnly = true)
    public List<Object[]> yarnWiseSummary(Long workerId, LocalDate from, LocalDate to) {
        return repo.yarnWiseSummary(workerId, from, to);
    }

    // ─────────────────────────────────────────────
    // SAVE / SUBMIT
    // ─────────────────────────────────────────────

    @Transactional
    public List<YarnEntry> saveAll(List<YarnEntry> entries, String receiptPath) {
        if (entries == null || entries.isEmpty()) return List.of();

        String challan = entries.get(0).getChallanNo();

        repo.findByChallanNoOrderByIdAsc(challan).stream()
                .filter(e -> "DRAFT".equals(e.getStatus()))
                .forEach(repo::delete);

        for (YarnEntry e : entries) {
            e.setStatus("DRAFT");
            e.setReceiptPath(receiptPath);
        }

        return repo.saveAll(entries);
    }

    @Transactional
    public List<YarnEntry> finalSubmit(List<YarnEntry> entries, String receiptPath) {
        if (entries == null || entries.isEmpty()) return List.of();

        String challan = entries.get(0).getChallanNo();

        repo.findByChallanNoOrderByIdAsc(challan)
                .forEach(repo::delete);

        for (YarnEntry e : entries) {
            e.setStatus("SUBMITTED");
            e.setReceiptPath(receiptPath);
        }

        return repo.saveAll(entries);
    }

    // ─────────────────────────────────────────────
    // SUMMARY HELPERS
    // ─────────────────────────────────────────────

    public BigDecimal totalWeight(List<YarnEntry> entries) {
        return entries.stream()
                .map(e -> e.getNetWeight() != null ? e.getNetWeight() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public int totalBags(List<YarnEntry> entries) {
        return entries.stream()
                .mapToInt(e -> e.getNoOfBags() != null ? e.getNoOfBags() : 0)
                .sum();
    }

    public int totalCones(List<YarnEntry> entries) {
        return entries.stream()
                .mapToInt(e -> e.getNoOfCones() != null ? e.getNoOfCones() : 0)
                .sum();
    }

    // ─────────────────────────────────────────────
    // IMPORT METHODS
    // ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public boolean isDuplicate(Long workerId, String challanNo,
                               LocalDate date, Integer yarnTypeId, String bagPiece) {

        if (workerId == null || challanNo == null || challanNo.isBlank()
                || date == null || yarnTypeId == null || bagPiece == null) {
            return false;
        }

        long count = repo.countDuplicate(workerId, challanNo, date, yarnTypeId, bagPiece);
        return count > 0;
    }

    @Transactional
    public void saveFromImport(YarnImportRow r) {

        YarnEntry entry = YarnEntry.builder()
                .jobWorker(r.getJobWorker())
                .deliveryLocation(r.getDeliveryLocation())
                .challanNo(r.getChallanNo())
                .entryDate(r.getEntryDate())
                .yarnType(r.getYarnType())
                .colour(r.getColour())
                .bagPiece(r.getBagPiece())
                .wtOfBags(r.getWtOfBags())
                .noOfBags(r.getNoOfBags())
                .noOfCones(r.getNoOfCones())
                .netWeight(r.getNetWeight())
                .status("SUBMITTED")
                .build();

        repo.save(entry);
    }

    @Transactional
    public boolean overwriteDuplicate(YarnImportRow r) {

        List<YarnEntry> list = repo.findExistingDuplicates(
                r.getJobWorker().getId(),
                r.getChallanNo(),
                r.getEntryDate(),
                r.getYarnType().getId(),
                r.getBagPiece()
        );

        if (list.isEmpty()) {
            saveFromImport(r);
            return true;
        }

        YarnEntry e = list.get(0);

        e.setDeliveryLocation(r.getDeliveryLocation());
        e.setYarnType(r.getYarnType());
        e.setColour(r.getColour());
        e.setWtOfBags(r.getWtOfBags());
        e.setNoOfBags(r.getNoOfBags());
        e.setNoOfCones(r.getNoOfCones());
        e.setNetWeight(r.getNetWeight());
        e.setStatus("SUBMITTED");

        repo.save(e);

        for (int i = 1; i < list.size(); i++) {
            repo.deleteById(list.get(i).getId());
        }

        return true;
    }

    // 🔥 FINAL FIXED METHOD
    public void applyReEntry(YarnDuplicateReviewRow row) {

        YarnEntry existing = row.getExistingEntry();
        YarnEntry incoming = row.getIncomingEntry();

        existing.setNetWeight(incoming.getNetWeight());
        existing.setColour(incoming.getColour());
        existing.setYarnType(incoming.getYarnType());
        existing.setWtOfBags(incoming.getWtOfBags());
        existing.setNoOfBags(incoming.getNoOfBags());
        existing.setNoOfCones(incoming.getNoOfCones());

        repo.save(existing);
    }

    // ─────────────────────────────────────────────
    // FILE HANDLING
    // ─────────────────────────────────────────────

    private String copyReceipt(File src, String challan) {
        if (src == null || !src.exists()) return null;

        try {
            Path dir = Paths.get(receiptsDir);
            Files.createDirectories(dir);

            String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyyyy"));
            String safeChallan = challan.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            String fileName = safeChallan + "_" + datePart + "_" + src.getName();

            Path dest = dir.resolve(fileName);
            Files.copy(src.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);

            return dest.toString();

        } catch (IOException ex) {
            log.error("Receipt copy failed", ex);
            return null;
        }
    }
    public YarnDuplicateCheckResult checkDuplicates(List<YarnEntry> entries) {

        List<YarnDuplicateReviewRow> dupRows = new ArrayList<>();
        List<YarnEntry> nonDup = new ArrayList<>();

        for (YarnEntry e : entries) {

            boolean dup = repo.countDuplicate(
                    e.getJobWorker().getId(),
                    e.getChallanNo(),
                    e.getEntryDate(),
                    e.getYarnType().getId(),
                    e.getBagPiece()
            ) > 0;

            if (!dup) {
                nonDup.add(e);
                continue;
            }

            List<YarnEntry> existing = repo.findExistingDuplicates(
                    e.getJobWorker().getId(),
                    e.getChallanNo(),
                    e.getEntryDate(),
                    e.getYarnType().getId(),
                    e.getBagPiece()
            );

            if (!existing.isEmpty()) {
                dupRows.add(new YarnDuplicateReviewRow(existing.get(0), e));
            }
        }

        return new YarnDuplicateCheckResult(dupRows, nonDup);
    }
}