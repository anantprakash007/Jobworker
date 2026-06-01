package com.jobwork.service;

import com.jobwork.domain.*;
import com.jobwork.repository.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class BheemImportService {

    @Autowired private BheemEntryRepository repo;
    @Autowired private JobWorkerRepository workerRepo;
    @Autowired private DeliveryLocationRepository locationRepo;
    @Autowired private BheemNameRepository bheemRepo;
    @Autowired private TaarRepository taarRepo;
    @Autowired private WrapperRepository wrapperRepo;
    @Autowired private YarnTypeRepository yarnRepo;

    // ─────────────────────────────────────────────
    // READ EXCEL
    // ─────────────────────────────────────────────
    public List<BheemImportRow> readExcel(File file) {

        List<BheemImportRow> list = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = WorkbookFactory.create(fis)) {

            Sheet sheet = wb.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {

                Row row = sheet.getRow(i);
                if (row == null) continue;

                BheemImportRow r = new BheemImportRow(i + 1);

                r.setWorkerName(getString(row, 0));
                r.setLocationName(getString(row, 1));
                r.setChallanNo(getString(row, 2));
                r.setDateRaw(getString(row, 3));
                r.setBheemNameRaw(getString(row, 4));
                r.setTaarRaw(getString(row, 5));
                r.setWrapperRaw(getString(row, 6));
                r.setYarnTypeRaw(getString(row, 7));
                r.setColour(getString(row, 8));
                r.setWeightRaw(getString(row, 9));

                list.add(r);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    // ─────────────────────────────────────────────
    // VALIDATE + MAP
    // ─────────────────────────────────────────────
    public void validate(List<BheemImportRow> rows) {

        for (BheemImportRow r : rows) {

            try {

                // ───────── WORKER (ID → ENTITY) ─────────
                Long workerId = Long.parseLong(r.getWorkerName().trim());

                JobWorker worker = workerRepo.findById(workerId)
                        .orElseThrow(() -> new RuntimeException("Worker not found: " + workerId));

                r.setJobWorker(worker);

                // ───────── DATE (FINAL FIX) ─────────
                if (r.getDateRaw() != null && !r.getDateRaw().isBlank()) {
                    r.setEntryDate(parseDateSafe(r.getDateRaw()));
                }

                // ───────── WEIGHT ─────────
                if (r.getWeightRaw() != null && !r.getWeightRaw().isBlank()) {
                    r.setWeight(new BigDecimal(r.getWeightRaw().trim()));
                }

                // ───────── LOCATION ─────────
                if (r.getLocationName() != null && !r.getLocationName().isBlank()) {
                    r.setDeliveryLocation(
                            locationRepo.findAll().stream()
                                    .filter(l -> l.getName().equalsIgnoreCase(r.getLocationName()))
                                    .findFirst()
                                    .orElse(null)
                    );
                }

                // ───────── BHEEM ─────────
                r.setBheemName(
                        bheemRepo.findByName(r.getBheemNameRaw())
                                .orElseThrow(() -> new RuntimeException("Bheem not found: " + r.getBheemNameRaw()))
                );

                // ───────── TAAR ─────────
                long taarValue = Long.parseLong(r.getTaarRaw().trim());

                r.setTaar(
                        taarRepo.findAll().stream()
                                .filter(t -> t.getValue() == taarValue)
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException("Taar not found: " + taarValue))
                );

                // ───────── WRAPPER ─────────
                if (r.getWrapperRaw() != null && !r.getWrapperRaw().isBlank()) {
                    r.setWrapper(
                            wrapperRepo.findAll().stream()
                                    .filter(w -> w.getName().equalsIgnoreCase(r.getWrapperRaw()))
                                    .findFirst()
                                    .orElse(null)
                    );
                }

                // ───────── YARN ─────────
                if (r.getYarnTypeRaw() != null && !r.getYarnTypeRaw().isBlank()) {
                    r.setYarnType(
                            yarnRepo.findAll().stream()
                                    .filter(y -> y.getName().equalsIgnoreCase(r.getYarnTypeRaw()))
                                    .findFirst()
                                    .orElse(null)
                    );
                }

                // ───────── DUPLICATE CHECK ─────────
                boolean dup = repo.countDuplicate(
                        r.getJobWorker().getId(),
                        r.getChallanNo(),
                        r.getEntryDate(),
                        r.getBheemName().getId(),
                        r.getTaar().getId()
                ) > 0;

                r.setStatus(dup ? BheemImportRow.DUPLICATE : BheemImportRow.VALID);

            } catch (Exception e) {
                r.setStatus(BheemImportRow.ERROR);
                r.setErrorDetail(e.getMessage());
                e.printStackTrace();
            }
        }
    }

    // ─────────────────────────────────────────────
    // SAFE DATE PARSER (FINAL FIX)
    // ─────────────────────────────────────────────
    private LocalDate parseDateSafe(String raw) {

        if (raw == null || raw.isBlank()) return null;

        raw = raw.trim();

        try {
            return LocalDate.parse(raw); // yyyy-MM-dd
        } catch (Exception ignored) {}

        try {
            return LocalDate.parse(raw, DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        } catch (Exception ignored) {}

        try {
            return LocalDate.parse(raw, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception ignored) {}

        throw new RuntimeException("Invalid date format: " + raw);
    }

    // ─────────────────────────────────────────────
    // DUPLICATE REVIEW
    // ─────────────────────────────────────────────
    public List<BheemDuplicateReviewRow> buildDuplicateRows(List<BheemImportRow> rows) {

        List<BheemDuplicateReviewRow> list = new ArrayList<>();

        for (BheemImportRow r : rows) {

            if (!r.isDuplicate()) continue;

            List<BheemEntry> existingList = repo.findExistingDuplicates(
                    r.getJobWorker().getId(),
                    r.getChallanNo(),
                    r.getEntryDate(),
                    r.getBheemName().getId(),
                    r.getTaar().getId()
            );

            if (existingList.isEmpty()) continue;

            BheemEntry existing = existingList.get(0);
            BheemEntry incoming = mapToEntity(r);

            list.add(new BheemDuplicateReviewRow(existing, incoming));
        }

        return list;
    }

    // ─────────────────────────────────────────────
    // IMPORT
    // ─────────────────────────────────────────────
    public void importData(List<BheemImportRow> rows) {

        for (BheemImportRow r : rows) {

            if (!r.isValid()) continue;

            BheemEntry e = mapToEntity(r);
            repo.save(e);

            r.setStatus(BheemImportRow.IMPORTED);
        }
    }

    // ─────────────────────────────────────────────
    // MAP ENTITY
    // ─────────────────────────────────────────────
    private BheemEntry mapToEntity(BheemImportRow r) {

        BheemEntry e = new BheemEntry();

        e.setJobWorker(r.getJobWorker());
        e.setChallanNo(r.getChallanNo());
        e.setEntryDate(r.getEntryDate());
        e.setBheemName(r.getBheemName());
        e.setTaar(r.getTaar());
        e.setWrapper(r.getWrapper());
        e.setYarnType(r.getYarnType());
        e.setColour(r.getColour());
        e.setWeight(r.getWeight());
        e.setDeliveryLocation(r.getDeliveryLocation());

        return e;
    }

    // ─────────────────────────────────────────────
    // EXCEL STRING READER
    // ─────────────────────────────────────────────
    private String getString(Row row, int col) {

        Cell cell = row.getCell(col);
        if (cell == null) return "";

        switch (cell.getCellType()) {

            case STRING:
                return cell.getStringCellValue().trim();

            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue()
                            .toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .toString();
                } else {
                    double val = cell.getNumericCellValue();
                    if (val == (long) val) {
                        return String.valueOf((long) val);
                    } else {
                        return String.valueOf(val);
                    }
                }

            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());

            case FORMULA:
                return cell.getCellFormula();

            default:
                return "";
        }
    }
}