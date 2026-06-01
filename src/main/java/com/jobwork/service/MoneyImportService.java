package com.jobwork.service;

import com.jobwork.domain.*;
import com.jobwork.repository.JobWorkerRepository;
import com.jobwork.repository.MoneyReceiptRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MoneyImportService {

    @Autowired
    private JobWorkerRepository workerRepo;

    @Autowired
    private MoneyReceiptRepository moneyRepo;

    // =====================================================
    // 🔥 READ EXCEL (FINAL CLEAN VERSION)
    // =====================================================
    public List<MoneyImportRow> readExcel(File file) throws Exception {

        List<MoneyImportRow> list = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(new FileInputStream(file))) {

            Sheet sheet = wb.getSheetAt(0);

            // 🔥 LOAD ALL WORKERS (FAST)
            Map<Long, JobWorker> workerMap =
                    workerRepo.findAll()
                            .stream()
                            .collect(Collectors.toMap(
                                    JobWorker::getId,
                                    w -> w
                            ));

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {

                Row row = sheet.getRow(i);
                if (row == null) continue;

                MoneyImportRow r = new MoneyImportRow(i);
                List<String> errors = new ArrayList<>();

                try {
                    // 🔹 Worker ID
                    Long workerId = getLong(row, 0);
                    r.setWorkerId(workerId);

                    if (workerId == null) {
                        errors.add("Worker ID required");
                    }

                    JobWorker worker = workerMap.get(workerId);

                    if (worker != null) {
                        r.setWorkerName(worker.getName());
                        r.setJobWorker(worker);
                    } else {
                        errors.add("Worker not found: " + workerId);
                    }

                    // 🔹 Challan
                    r.setChallanNo(getString(row, 1));

                    // 🔹 Date
                    LocalDate date = getDate(row, 2);
                    r.setEntryDate(date);

                    if (date == null) {
                        errors.add("Invalid date format");
                    }

                    // 🔹 Transfer Mode
                    String modeRaw = getString(row, 3);
                    r.setTransferModeRaw(modeRaw);
                    r.setTransferMode(modeRaw == null ? null : modeRaw.trim().toUpperCase());

                    // 🔹 Amount
                    BigDecimal amt = getDecimal(row, 4);
                    r.setAmount(amt);

                    if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                        errors.add("Invalid amount");
                    }

                    // 🔹 Remark
                    r.setRemark(getString(row, 5));

                } catch (Exception e) {
                    errors.add("Row error: " + e.getMessage());
                }

                if (!errors.isEmpty()) {
                    r.setValidationError(String.join(" | ", errors));
                }

                list.add(r);
            }
        }

        return list;
    }

    // =====================================================
    // 🔁 DUPLICATE CHECK
    // =====================================================
    public boolean isDuplicate(Long workerId, String challanNo, LocalDate date, TransferMode mode) {
        return moneyRepo.countDuplicate(workerId, challanNo, date) > 0;
    }

    // =====================================================
    // 💾 SAVE NEW ENTRY
    // =====================================================
    public void saveFromImport(MoneyImportRow row) {

        if (row.getJobWorker() == null) {
            throw new RuntimeException("Worker missing");
        }

        TransferMode mode = parseMode(row.getTransferMode());

        MoneyReceipt entry = MoneyReceipt.builder()
                .jobWorker(row.getJobWorker())
                .challanNo(row.getChallanNo())
                .receiptDate(row.getEntryDate())
                .transferMode(mode)
                .amount(row.getAmount())
                .remark(row.getRemark())
                .status(EntryStatus.SUBMITTED)
                .entryType(EntryType.ADVANCE)
                .build();

        moneyRepo.save(entry);
    }

    // =====================================================
    // 🔁 OVERWRITE EXISTING
    // =====================================================
    public boolean overwriteDuplicate(MoneyImportRow row) {

        TransferMode mode = parseMode(row.getTransferMode());

        List<MoneyReceipt> list = moneyRepo.findExistingDuplicates(
                row.getWorkerId(),
                row.getChallanNo(),
                row.getEntryDate()
        );

        if (list.isEmpty()) return false;

        MoneyReceipt existing = list.get(0);

        existing.setAmount(row.getAmount());
        existing.setRemark(row.getRemark());
        existing.setTransferMode(mode);

        moneyRepo.save(existing);
        return true;
    }

    public void saveAll(List<MoneyReceipt> list) {
        moneyRepo.saveAll(list);
    }

    // =====================================================
    // 🔄 ENUM PARSER
    // =====================================================
    private TransferMode parseMode(String raw) {
        try {
            return raw == null ? null : TransferMode.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    // =====================================================
    // 🔄 ROW → ENTITY
    // =====================================================
    public MoneyReceipt toEntity(MoneyImportRow r) {
        return MoneyReceipt.builder()
                .jobWorker(r.getJobWorker())
                .challanNo(r.getChallanNo())
                .receiptDate(r.getEntryDate())
                .transferMode(parseMode(r.getTransferMode()))
                .amount(r.getAmount())
                .remark(r.getRemark())
                .status(EntryStatus.SUBMITTED)
                .entryType(EntryType.ADVANCE)
                .build();
    }

    // =====================================================
    // 🔍 FIND EXISTING
    // =====================================================
    public MoneyReceipt findExisting(MoneyImportRow r) {

        if (r.getJobWorker() == null) return null;

        List<MoneyReceipt> list = moneyRepo.findExistingDuplicates(
                r.getWorkerId(),
                r.getChallanNo(),
                r.getEntryDate()
        );

        return list.isEmpty() ? null : list.get(0);
    }

    // =====================================================
    // 💾 SAVE SINGLE
    // =====================================================
    public void save(MoneyReceipt r) {
        moneyRepo.save(r);
    }

    // =====================================================
    // 🔧 HELPER METHODS
    // =====================================================
    private Long getLong(Row row, int i) {
        Cell cell = row.getCell(i);
        if (cell == null) return null;

        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return (long) cell.getNumericCellValue();
            }
            if (cell.getCellType() == CellType.STRING) {
                String val = cell.getStringCellValue().trim();
                return val.isEmpty() ? null : Long.parseLong(val);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private String getString(Row row, int i) {
        Cell cell = row.getCell(i);
        if (cell == null) return "";
        return cell.toString().trim();
    }

    private BigDecimal getDecimal(Row row, int i) {
        Cell cell = row.getCell(i);
        if (cell == null) return BigDecimal.ZERO;

        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            if (cell.getCellType() == CellType.STRING) {
                String val = cell.getStringCellValue().trim();
                return val.isEmpty() ? BigDecimal.ZERO : new BigDecimal(val);
            }
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }

    private LocalDate getDate(Row row, int i) {
        Cell cell = row.getCell(i);
        if (cell == null) return null;

        try {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue()
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
            }
            if (cell.getCellType() == CellType.STRING) {
                return LocalDate.parse(
                        cell.getStringCellValue().trim(),
                        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
                );
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}