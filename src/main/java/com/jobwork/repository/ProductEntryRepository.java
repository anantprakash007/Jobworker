package com.jobwork.repository;

import com.jobwork.domain.ProductEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * ProductEntryRepository — FIXED
 * ══════════════════════════════════════════════════════════════════
 * KEY FIX — countDuplicateSimple():
 *
 * OLD (broken) — used FUNCTION('REPLACE',...) which fails on H2 and
 * some MySQL configurations, and incorrectly stripped zeros from challan:
 *
 *   WHERE FUNCTION('REPLACE', TRIM(p.challanNo), '0', '') =
 *         FUNCTION('REPLACE', TRIM(:challan), '0', '')
 *
 * NEW (fixed) — plain JPQL LOWER/TRIM, challan already normalized
 * by ProductImportService.normalizeChallan() before calling DB:
 *
 *   WHERE LOWER(TRIM(p.challanNo)) = LOWER(TRIM(:challan))
 *   AND   LOWER(TRIM(pn.name))     = LOWER(TRIM(:product))
 *
 * This works on MySQL, H2, PostgreSQL, and all other JPQL-compliant DBs.
 *
 * ALSO ADDED:
 *   countDuplicateSimple() — the new clean duplicate check for import
 *   The old countDuplicate() is kept for backward compatibility with
 *   any other code that calls it.
 * ══════════════════════════════════════════════════════════════════
 */
@Repository
public interface ProductEntryRepository
        extends JpaRepository<ProductEntry, Long>,
        JpaSpecificationExecutor<ProductEntry> {

    List<ProductEntry> findByJobWorkerId(Long jobWorkerId);
    List<ProductEntry> findAll();
    List<ProductEntry> findByChallanNo(String challanNo);
    boolean existsByJobWorker_Id(Long workerId);

    List<ProductEntry> findByJobWorkerIdAndEntryDateBetween(
            Long jobWorkerId, LocalDate from, LocalDate to);

    List<ProductEntry> findByProductTypeIdAndEntryDateBetween(
            Integer typeId, LocalDate from, LocalDate to);

    // ════════════════════════════════════════════════════════════
    //  DUPLICATE CHECK — FIXED VERSION (used by ProductImportService)
    // ════════════════════════════════════════════════════════════

    /**
     * Count existing ProductEntry records matching the import row key.
     * Challan and product name are already normalized (lower-cased, trimmed,
     * leading zeros stripped) by ProductImportService before this call.
     *
     * FIX: no FUNCTION('REPLACE',...) — pure JPQL LOWER+TRIM only.
     *
     * Returns 0 = no duplicate (safe to import)
     *        >0 = duplicate exists in DB
     *
     * @param workerId   job worker id (Long)
     * @param challan    normalized challan (e.g. "50" not "050")
     * @param date       entry date
     * @param product    normalized product name (lower-cased)
     */
    @Query("""
            SELECT COUNT(p)
            FROM   ProductEntry p
            JOIN   p.productName pn
            WHERE  p.jobWorker.id            = :workerId
            AND    LOWER(TRIM(p.challanNo))  = LOWER(TRIM(:challan))
            AND    p.entryDate               = :date
            AND    LOWER(TRIM(pn.name))      = LOWER(TRIM(:product))
            """)
    long countDuplicateSimple(
            @Param("workerId") Long      workerId,
            @Param("challan")  String    challan,
            @Param("date")     LocalDate date,
            @Param("product")  String    product);

    /**
     * OLD countDuplicate — kept for backward compatibility.
     * Do NOT use for new code — use countDuplicateSimple() instead.
     * This version uses FUNCTION('REPLACE') which may fail on some DBs.
     */
    @Query("""
            SELECT COUNT(p)
            FROM   ProductEntry p
            JOIN   p.productName pn
            WHERE  p.jobWorker.id = :workerId
            AND    LOWER(TRIM(p.challanNo))  = LOWER(TRIM(:challan))
            AND    p.entryDate               = :date
            AND    LOWER(TRIM(pn.name))      = LOWER(TRIM(:product))
            """)
    long countDuplicate(
            @Param("workerId") Long      workerId,
            @Param("challan")  String    challan,
            @Param("date")     LocalDate date,
            @Param("product")  String    product);

    // ════════════════════════════════════════════════════════════
    //  SINGLE + BATCH DUPLICATE FETCH (for display in popup)
    // ════════════════════════════════════════════════════════════

    @Query("""
            SELECT p
            FROM   ProductEntry p
            JOIN   p.jobWorker  jw
            JOIN   p.productName pn
            WHERE  jw.id        = :workerId
            AND    p.challanNo  = :challanNo
            AND    p.entryDate  = :date
            AND    pn.id        = :productNameId
            """)
    Optional<ProductEntry> findOneDuplicate(
            @Param("workerId")      Long      workerId,
            @Param("challanNo")     String    challanNo,
            @Param("date")          LocalDate date,
            @Param("productNameId") Integer   productNameId);

    @Query("""
            SELECT p
            FROM   ProductEntry p
            JOIN   p.jobWorker  jw
            JOIN   p.productName pn
            WHERE  jw.id        = :workerId
            AND    p.challanNo  = :challanNo
            AND    p.entryDate  = :date
            AND    pn.id        IN :nameIds
            """)
    List<ProductEntry> findDuplicateBatch(
            @Param("workerId")  Long          workerId,
            @Param("challanNo") String        challanNo,
            @Param("date")      LocalDate     date,
            @Param("nameIds")   List<Integer> nameIds);

    // ════════════════════════════════════════════════════════════
    //  REPORT QUERIES (unchanged)
    // ════════════════════════════════════════════════════════════

    @Query("""
            SELECT p.productName.name, SUM(p.quantity), SUM(p.weight)
            FROM   ProductEntry p
            WHERE  (:workerId IS NULL OR p.jobWorker.id = :workerId)
            AND    (:from IS NULL OR p.entryDate >= :from)
            AND    (:to   IS NULL OR p.entryDate <= :to)
            GROUP  BY p.productName.name
            ORDER  BY p.productName.name
            """)
    List<Object[]> productWiseSummary(
            @Param("workerId") Long      workerId,
            @Param("from")     LocalDate from,
            @Param("to")       LocalDate to);

    @Query("""
            SELECT p
            FROM   ProductEntry p
            WHERE  p.jobWorker.id = :workerId
            AND    (:from IS NULL OR p.entryDate >= :from)
            AND    (:to   IS NULL OR p.entryDate <= :to)
            ORDER  BY p.entryDate ASC, p.id ASC
            """)
    List<ProductEntry> findByWorkerAndDateRange(
            @Param("workerId") Long      workerId,
            @Param("from")     LocalDate from,
            @Param("to")       LocalDate to);

    @Query("""
            SELECT COALESCE(SUM(p.quantity * p.productName.defaultRate), 0)
            FROM   ProductEntry p
            WHERE  (:workerId IS NULL OR p.jobWorker.id = :workerId)
            AND    (:from IS NULL OR p.entryDate >= :from)
            AND    (:to   IS NULL OR p.entryDate <= :to)
            """)
    BigDecimal sumTotal(
            @Param("workerId") Long      workerId,
            @Param("from")     LocalDate from,
            @Param("to")       LocalDate to);

    @Query("""
            SELECT p.entryDate, p.challanNo, p.jobWorker.name,
                   COALESCE(SUM(p.quantity * p.productName.defaultRate), 0)
            FROM   ProductEntry p
            WHERE  (:workerId IS NULL OR p.jobWorker.id = :workerId)
            AND    (:from IS NULL OR p.entryDate >= :from)
            AND    (:to   IS NULL OR p.entryDate <= :to)
            GROUP  BY p.entryDate, p.challanNo, p.jobWorker.name
            ORDER  BY p.entryDate ASC, p.challanNo ASC
            """)
    List<Object[]> challanWiseReport(
            @Param("workerId") Long      workerId,
            @Param("from")     LocalDate from,
            @Param("to")       LocalDate to);

    @Query("""
            SELECT COUNT(p) > 0
            FROM   ProductEntry p
            WHERE  p.jobWorker.id            = :workerId
            AND    p.challanNo               = :challanNo
            AND    p.entryDate               = :date
            AND    LOWER(p.productName.name) = LOWER(:productName)
            """)
    boolean existsByChallan(
            @Param("workerId")    Long      workerId,
            @Param("challanNo")   String    challanNo,
            @Param("date")        LocalDate date,
            @Param("productName") String    productName);

    List<ProductEntry> findByJobWorkerIdAndChallanNo(
            Long workerId, String challanNo);
}