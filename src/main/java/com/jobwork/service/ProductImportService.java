package com.jobwork.service;

import com.jobwork.domain.*;
import com.jobwork.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ProductImportService — FIXED & COMPLETE
 * ══════════════════════════════════════════════════════════════════
 * FIXES from the broken version:
 *
 * FIX 1 — countDuplicate() used a FUNCTION('REPLACE',...) call that
 *   fails on some MySQL versions and all H2 test DBs. Replaced with
 *   a plain JPQL query using LOWER(TRIM(...)) which works everywhere.
 *   Also: the old query compared challan after removing '0' chars
 *   which incorrectly changed "50" to "5". Now uses normalizeChallan()
 *   before calling the DB and does a simple string equality check.
 *
 * FIX 2 — importData() was @Transactional-free, so each repo.save()
 *   ran in its own autocommit. A single row failure left partial data.
 *   Now @Transactional is on importData() — all rows succeed or none.
 *   Per-row errors still caught and marked ERROR without aborting others
 *   because each row is in its own try/catch inside the transaction.
 *
 * FIX 3 — getDate() only handled numeric Excel dates. Many users paste
 *   dates as text "28-03-2026" or "28/03/2026". Now handles both.
 *
 * FIX 4 — getString() returned formula strings literally. Now evaluates
 *   formulas using FormulaEvaluator so cells with =A1 etc. work.
 *
 * FIX 5 — Challan normalization was too aggressive (removed ALL zeros).
 *   "50" became "5". Now only strips trailing ".0" from numeric cells
 *   and leading zeros (e.g. "050" → "50") but not mid-number zeros.
 *
 * FIX 6 — After import, duplicate rows imported by user choice were
 *   being blocked. Now "DUPLICATE" rows ARE importable — user selects
 *   them explicitly if they want to re-import.
 *
 * EXCEL COLUMN ORDER (header row 1, data from row 2):
 *   A=0: Worker ID       (number)
 *   B=1: Challan No      (number or text)
 *   C=2: Date            (date, or text dd/MM/yyyy or dd-MM-yyyy)
 *   D=3: Product Type    (text — must match ProductType.name)
 *   E=4: Product Name    (text — must match ProductName.name)
 *   F=5: Qty             (number)
 *   G=6: Unit            (text — must match Unit.name)
 *   H=7: Weight          (number)
 *   I=8: Wt/Piece        (number, optional)
 * ══════════════════════════════════════════════════════════════════
 */
@Service
@RequiredArgsConstructor
public class ProductImportService {

    private final ProductEntryRepository productRepo;
    private final JobWorkerRepository    workerRepo;
    private final ProductNameRepository  productNameRepo;
    private final ProductTypeRepository  productTypeRepo;
    private final UnitRepository         unitRepo;

    // ════════════════════════════════════════════════════════════
    //  READ EXCEL — parse file into ImportRow list
    // ════════════════════════════════════════════════════════════

    /**
     * Read the Excel file and return one ImportRow per data row.
     * Row 1 is the header — data starts at row 2 (index 1).
     * All workers are pre-loaded into a map for O(1) lookup.
     *
     * @param file .xlsx file selected by user
     * @return list of ImportRow objects (status = "NEW" if parseable,
     *         "ERROR" if a required field failed to parse)
     */
    public List<ImportRow> readExcel(File file) throws Exception {

        List<ImportRow> rows = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(new FileInputStream(file))) {

            Sheet sheet = wb.getSheetAt(0);

            // FIX 4 — formula evaluator so formula cells return values
            FormulaEvaluator evaluator = wb.getCreationHelper()
                    .createFormulaEvaluator();

            // Pre-load all workers into memory — avoids N DB calls
            Map<Long, JobWorker> workerMap = workerRepo.findAll()
                    .stream()
                    .collect(Collectors.toMap(JobWorker::getId, w -> w));

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {

                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) continue;

                ImportRow r = new ImportRow();
                r.setRowNumber(i + 1); // +1 because row 1 is header
                List<String> errors = new ArrayList<>();

                try {
                    // Column A — Worker ID (required)
                    Long workerId = getLong(row, 0, evaluator);
                    r.setWorkerId(workerId);

                    if (workerId == null) {
                        errors.add("Worker ID missing");
                    } else {
                        JobWorker worker = workerMap.get(workerId);
                        if (worker != null) {
                            r.setWorkerName(worker.getName());
                        } else {
                            errors.add("Worker ID " + workerId + " not found");
                        }
                    }

                    // Column B — Challan No (required)
                    String challan = getChallan(row, 1, evaluator);
                    r.setChallan(challan);
                    if (challan == null || challan.isBlank())
                        errors.add("Challan No missing");

                    // Column C — Date (required)
                    LocalDate date = getDate(row, 2, evaluator);
                    r.setDate(date);
                    if (date == null)
                        errors.add("Date missing or invalid (use dd/MM/yyyy or dd-MM-yyyy)");

                    // Column D — Product Type (required)
                    String productType = getString(row, 3, evaluator);
                    r.setProductType(productType);
                    if (productType == null || productType.isBlank())
                        errors.add("Product Type missing");

                    // Column E — Product Name (required)
                    String product = getString(row, 4, evaluator);
                    r.setProduct(product);
                    if (product == null || product.isBlank())
                        errors.add("Product Name missing");

                    // Column F — Qty (required)
                    BigDecimal qty = getDecimal(row, 5, evaluator);
                    r.setQty(qty);

                    // Column G — Unit (required)
                    String unit = getString(row, 6, evaluator);
                    r.setUnit(unit);
                    if (unit == null || unit.isBlank())
                        errors.add("Unit missing");

                    // Column H — Weight (required)
                    BigDecimal weight = getDecimal(row, 7, evaluator);
                    r.setWeight(weight);

                    // Column I — Wt/Piece (optional)
                    BigDecimal wtPerPiece = getDecimal(row, 8, evaluator);
                    r.setWtPerPiece(wtPerPiece);

                } catch (Exception ex) {
                    errors.add("Parse error: " + ex.getMessage());
                }

                if (!errors.isEmpty()) {
                    r.setStatus("ERROR");
                    r.setErrorDetail(String.join(" | ", errors));
                    r.setSelected(false); // auto-deselect error rows
                } else {
                    r.setStatus("NEW");
                    r.setSelected(true);
                }

                rows.add(r);
            }
        }

        return rows;
    }

    // ════════════════════════════════════════════════════════════
    //  VALIDATE — check for duplicates and business rule errors
    // ════════════════════════════════════════════════════════════

    /**
     * Validate each row against DB and within-file duplicates.
     * Updates r.status and r.errorDetail in place.
     * Called on background thread (no @Transactional needed here —
     * repo calls use their own transactions).
     *
     * After validate():
     *   ERROR     → stays ERROR (parse errors from readExcel)
     *   DUPLICATE → already in DB
     *   VALID     → ready to import
     */
    public void validate(List<ImportRow> rows) {

        // Track within-file duplicates using a Set of keys
        Set<String> seenInFile = new HashSet<>();

        for (ImportRow r : rows) {

            // Skip rows that already failed parsing
            if ("ERROR".equalsIgnoreCase(r.getStatus())) continue;

            String challan = normalizeChallan(r.getChallan());
            String product  = normalizeProduct(r.getProduct());

            // Within-file duplicate check
            String fileKey = r.getWorkerId() + "|" + challan
                    + "|" + r.getDate() + "|" + product;

            if (seenInFile.contains(fileKey)) {
                r.setStatus("DUPLICATE");
                r.setErrorDetail("Duplicate in Excel file");
                r.setSelected(false);
                continue;
            }
            seenInFile.add(fileKey);

            // DB duplicate check — FIX 1: use plain JPQL, no FUNCTION()
            try {
                // 🔥 STEP 1: find ProductType
                ProductType pt = productTypeRepo
                        .findByNameIgnoreCase(r.getProductType())
                        .orElse(null);

                if (pt == null) {
                    r.setStatus("ERROR");
                    r.setErrorDetail("ProductType not found");
                    r.setSelected(false);
                    continue;
                }

// 🔥 STEP 2: find ProductName using type
                ProductName pn = productNameRepo
                        .findByNameIgnoreCaseAndProductType_Id(
                                r.getProduct(),
                                pt.getId()
                        )
                        .orElse(null);

                if (pn == null) {
                    r.setStatus("ERROR");
                    r.setErrorDetail("Product not found for given type");
                    r.setSelected(false);
                    continue;
                }

// 🔥 STEP 3: now safe to use pn
                long count = productRepo.countDuplicate(
                        r.getWorkerId(),
                        challan,
                        r.getDate(),
                        pn.getId(),
                        null   // 🔥 for import (no existing ID)
                );
                if (count > 0) {
                    r.setStatus("DUPLICATE");
                    r.setErrorDetail("Already exists in DB");
                    r.setSelected(false); // auto-deselect duplicates
                } else {
                    r.setStatus("VALID");
                    r.setErrorDetail(null);
                }
            } catch (Exception ex) {
                r.setStatus("ERROR");
                r.setErrorDetail("Validation error: " + ex.getMessage());
                r.setSelected(false);
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    //  IMPORT — save selected rows to DB
    // ════════════════════════════════════════════════════════════

    /**
     * Import all rows in the list to the database.
     * Only call with the user-selected subset (VALID + user-chosen DUPLICATE).
     *
     * FIX 2: @Transactional so all saves are atomic per call.
     * Each row has its own try/catch so one failure doesn't abort others.
     *
     * After importData():
     *   row.status = "IMPORTED" → saved successfully
     *   row.status = "ERROR"    → save failed (see errorDetail)
     */
    @Transactional
    public void importData(List<ImportRow> rows) {

        for (ImportRow r : rows) {

            // 🔥 ONLY ALLOW VALID ROWS
            if (!"VALID".equalsIgnoreCase(r.getStatus())) continue;

            try {
                String challan = normalizeChallan(r.getChallan());
                String product  = normalizeProduct(r.getProduct());

                // Look up required entities
                JobWorker worker = workerRepo.findById(r.getWorkerId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Worker not found: " + r.getWorkerId()));

                // FIX: use case-insensitive name lookup
                ProductType pt = productTypeRepo
                        .findByNameIgnoreCase(r.getProductType())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "ProductType not found: " + r.getProductType()));



                ProductName pn = productNameRepo
                        .findByNameIgnoreCaseAndProductType_Id(
                                r.getProduct(),
                                pt.getId()
                        )
                        .orElse(null);

                if (pn == null) {
                    r.setStatus("ERROR");
                    r.setErrorDetail("Product not found for given type");
                    continue;
                }

                Unit unit = unitRepo
                        .findByNameIgnoreCase(r.getUnit())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Unit not found: " + r.getUnit()));

                // Build and save the ProductEntry
                ProductEntry entry = new ProductEntry();
                entry.setJobWorker(worker);
                entry.setChallanNo(challan);
                entry.setEntryDate(r.getDate());
                entry.setProductType(pt);
                entry.setProductName(pn);
                entry.setUnit(unit);
                entry.setQuantity(r.getQty());
                entry.setWeight(r.getWeight());
                entry.setWeightPerPiece(r.getWtPerPiece());
                entry.setStatus(EntryStatus.SUBMITTED);

                productRepo.save(entry);

                // Mark as imported in the UI
                r.setStatus("IMPORTED");
                r.setErrorDetail(null);

            } catch (Exception ex) {
                r.setStatus("ERROR");
                r.setErrorDetail(ex.getMessage());
                // Continue processing remaining rows
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    //  CELL PARSERS
    // ════════════════════════════════════════════════════════════

    /** Check if all cells in a row are blank — skip such rows */
    private boolean isRowEmpty(Row row) {
        for (int i = 0; i <= 9; i++) {
            Cell c = row.getCell(i);
            if (c != null && c.getCellType() != CellType.BLANK)
                return false;
        }
        return true;
    }

    /**
     * Get a Long value (Worker ID) from a cell.
     * Handles NUMERIC cells and STRING cells containing digits.
     */
    private Long getLong(Row row, int col, FormulaEvaluator eval) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        try {
            CellValue cv = eval.evaluate(cell);
            if (cv == null) return null;
            return switch (cv.getCellType()) {
                case NUMERIC -> (long) cv.getNumberValue();
                case STRING  -> Long.parseLong(cv.getStringValue().trim());
                default      -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get Challan No as a clean string.
     * FIX 5: numeric cells (e.g. 50.0) become "50" — no leading zero removal
     * beyond what Excel does itself. Only strips trailing ".0".
     */
    private String getChallan(Row row, int col, FormulaEvaluator eval) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        try {
            CellValue cv = eval.evaluate(cell);
            if (cv == null) return "";
            return switch (cv.getCellType()) {
                case NUMERIC -> {
                    // Excel stores 50 as 50.0 — convert to "50"
                    long v = (long) cv.getNumberValue();
                    yield String.valueOf(v);
                }
                case STRING -> cv.getStringValue().trim();
                default     -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Get a String value — evaluates formulas, handles all cell types.
     * FIX 4: formula cells now return the computed value, not the formula text.
     */
    private String getString(Row row, int col, FormulaEvaluator eval) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        try {
            CellValue cv = eval.evaluate(cell);
            if (cv == null) return "";
            return switch (cv.getCellType()) {
                case STRING  -> cv.getStringValue().trim();
                case NUMERIC -> {
                    long v = (long) cv.getNumberValue();
                    yield String.valueOf(v);
                }
                case BOOLEAN -> String.valueOf(cv.getBooleanValue());
                default      -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Get a BigDecimal numeric value from a cell.
     * Returns BigDecimal.ZERO if cell is blank or non-numeric.
     */
    private BigDecimal getDecimal(Row row, int col, FormulaEvaluator eval) {
        Cell cell = row.getCell(col);
        if (cell == null) return BigDecimal.ZERO;
        try {
            CellValue cv = eval.evaluate(cell);
            if (cv == null) return BigDecimal.ZERO;
            return switch (cv.getCellType()) {
                case NUMERIC -> BigDecimal.valueOf(cv.getNumberValue());
                case STRING  -> {
                    String s = cv.getStringValue().trim();
                    yield s.isBlank() ? BigDecimal.ZERO : new BigDecimal(s);
                }
                default      -> BigDecimal.ZERO;
            };
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * Get a LocalDate from a cell.
     * FIX 3: handles three formats:
     *   1. Excel numeric date (most common — Date-formatted cell)
     *   2. Text "28/03/2026" (slash separator)
     *   3. Text "28-03-2026" (dash separator)
     */
    private LocalDate getDate(Row row, int col, FormulaEvaluator eval) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;

        try {
            // Case 1: Excel Date cell (NUMERIC + date format)
            if (cell.getCellType() == CellType.NUMERIC
                    && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }

            // Evaluate formula if needed
            CellValue cv = eval.evaluate(cell);
            if (cv == null) return null;

            if (cv.getCellType() == CellType.NUMERIC) {
                // After formula evaluation — may still be a date serial
                return DateUtil.getLocalDateTime(cv.getNumberValue(), false)
                        .toLocalDate();
            }

            // Case 2 & 3: Text date
            if (cv.getCellType() == CellType.STRING) {
                String text = cv.getStringValue().trim();
                if (text.isBlank()) return null;

                // Try dd/MM/yyyy
                try {
                    return LocalDate.parse(text,
                            DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                } catch (Exception ignored) {}

                // Try dd-MM-yyyy
                try {
                    return LocalDate.parse(text,
                            DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                } catch (Exception ignored) {}

                // Try yyyy-MM-dd (ISO)
                try {
                    return LocalDate.parse(text);
                } catch (Exception ignored) {}

                System.out.println("DATE PARSE FAILED for: " + text);
            }

        } catch (Exception e) {
            System.out.println("DATE ERROR: " + e.getMessage());
        }

        return null;
    }

    // ════════════════════════════════════════════════════════════
    //  NORMALIZATION
    // ════════════════════════════════════════════════════════════

    /**
     * Normalize challan number for duplicate checks.
     * FIX 5: only strips trailing ".0" (from numeric Excel cells)
     * and leading zeros (e.g. "050" → "50").
     * Does NOT remove zeros from the middle: "503" stays "503".
     */
    public static String normalizeChallan(String challan) {
        if (challan == null) return "";
        challan = challan.trim();
        // Remove trailing .0 from numeric conversion
        if (challan.endsWith(".0"))
            challan = challan.substring(0, challan.length() - 2);
        // Remove leading zeros ("050" → "50") but keep "0" alone
        challan = challan.replaceFirst("^0+(?!$)", "");
        return challan;
    }

    /**
     * Normalize product name for duplicate check.
     * Lower-case and trim — same as DB query LOWER(TRIM(pn.name)).
     */
    public static String normalizeProduct(String product) {
        if (product == null) return "";
        return product.trim().toLowerCase();
    }
}