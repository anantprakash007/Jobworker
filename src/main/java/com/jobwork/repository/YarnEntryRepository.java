package com.jobwork.repository;

import com.jobwork.domain.YarnEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface YarnEntryRepository
        extends JpaRepository<YarnEntry, Long>,
        JpaSpecificationExecutor<YarnEntry> {

    List<YarnEntry> findByJobWorkerId(Long jobWorkerId);

    @Query("SELECT y.yarnType.name,       " +
            "       SUM(y.noOfBags),        " +
            "       SUM(y.noOfCones),       " +
            "       SUM(y.netWeight)        " +
            "FROM   YarnEntry y             " +
            "WHERE  (:workerId IS NULL OR y.jobWorker.id = :workerId) " +
            "AND    (:from IS NULL OR y.entryDate >= :from)           " +
            "AND    (:to   IS NULL OR y.entryDate <= :to)             " +
            "GROUP  BY y.yarnType.name                                " +
            "ORDER  BY y.yarnType.name")
    List<Object[]> yarnWiseSummary(
            @Param("workerId") Long workerId,
            @Param("from")     LocalDate from,
            @Param("to")       LocalDate to);

    @Query("SELECT DISTINCT y.colour FROM YarnEntry y " +
            "WHERE y.colour IS NOT NULL ORDER BY y.colour")
    List<String> findDistinctColours();

    List<YarnEntry> findByChallanNoOrderByIdAsc(String challanNo);

    @Query("""
        SELECT e FROM YarnEntry e
        WHERE e.jobWorker.id = :workerId
          AND e.entryDate BETWEEN :from AND :to
        ORDER BY e.entryDate ASC, e.id ASC
        """)
    List<YarnEntry> findByWorkerAndDateRange(
            @Param("workerId") Long workerId,
            @Param("from")     LocalDate from,
            @Param("to")       LocalDate to);

    @Query("""
        SELECT e.yarnType.name, e.bagPiece,
               SUM(e.noOfBags), SUM(e.noOfCones), SUM(e.netWeight)
        FROM YarnEntry e
        WHERE e.jobWorker.id = :workerId
          AND e.entryDate BETWEEN :from AND :to
        GROUP BY e.yarnType.name, e.bagPiece
        ORDER BY e.yarnType.name ASC
        """)
    List<Object[]> yarnwiseSummary(
            @Param("workerId") Long workerId,
            @Param("from")     LocalDate from,
            @Param("to")       LocalDate to);

    List<YarnEntry> findByEntryDateBetweenOrderByEntryDateAscIdAsc(
            LocalDate from, LocalDate to);

    List<YarnEntry> findByStatusOrderByEntryDateDescIdDesc(String status);

    // ─────────────────────────────────────────────────────────────
    //  DUPLICATE CHECK QUERIES
    //
    //  Key: (jobWorker.id, challanNo, entryDate, yarnType.id, bagPiece)
    //
    //  YarnEntry allows multiple rows per challan (one per yarn type +
    //  bag/piece combination), so the duplicate key includes yarnType
    //  AND bagPiece — same yarn count but different bag/piece is a
    //  different entry, not a duplicate.
    //
    //  Uses JPQL with explicit JOIN — avoids:
    //    • "Unknown column" from native SQL naming issues
    //    • NonUniqueResultException from Optional.getSingleResult()
    //    • TerminalPathException from path traversal on EAGER @ManyToOne
    // ─────────────────────────────────────────────────────────────

    /**
     * COUNT — called during import validation per row.
     * 0 = safe to insert, >0 = duplicate exists.
     */
    @Query("""
        SELECT COUNT(y)
        FROM   YarnEntry y
        JOIN   y.jobWorker jw
        JOIN   y.yarnType  yt
        WHERE  jw.id       = :workerId
        AND    y.challanNo = :challanNo
        AND    y.entryDate = :date
        AND    yt.id       = :yarnTypeId
        AND    y.bagPiece  = :bagPiece
        """)
    long countDuplicate(
            @Param("workerId")   Long      workerId,
            @Param("challanNo")  String    challanNo,
            @Param("date")       LocalDate date,
            @Param("yarnTypeId") Integer   yarnTypeId,
            @Param("bagPiece")   String    bagPiece);

    /**
     * FETCH existing row(s) — returns List (not Optional) to safely
     * handle the case where 2+ duplicates already exist in DB.
     * Service takes first (lowest id); extras deleted on overwrite.
     */
    @Query("""
        SELECT y
        FROM   YarnEntry y
        JOIN   y.jobWorker jw
        JOIN   y.yarnType  yt
        WHERE  jw.id       = :workerId
        AND    y.challanNo = :challanNo
        AND    y.entryDate = :date
        AND    yt.id       = :yarnTypeId
        AND    y.bagPiece  = :bagPiece
        ORDER  BY y.id ASC
        """)
    List<YarnEntry> findExistingDuplicates(
            @Param("workerId")   Long      workerId,
            @Param("challanNo")  String    challanNo,
            @Param("date")       LocalDate date,
            @Param("yarnTypeId") Integer   yarnTypeId,
            @Param("bagPiece")   String    bagPiece);
}