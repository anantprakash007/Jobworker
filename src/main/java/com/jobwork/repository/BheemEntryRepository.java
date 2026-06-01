package com.jobwork.repository;

import com.jobwork.domain.BheemEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BheemEntryRepository
        extends JpaRepository<BheemEntry, Long>,
        JpaSpecificationExecutor<BheemEntry> {

    List<BheemEntry> findByJobWorkerId(Long jobWorkerId);

    // ── Quality-wise summary (unchanged) ──────────────────────────
    @Query("""
        SELECT b.bheemName.name,
               b.taar.value,
               COUNT(b),
               SUM(b.weight)
        FROM   BheemEntry b
        WHERE  (:workerId IS NULL OR b.jobWorker.id = :workerId)
        AND    (:from IS NULL OR b.entryDate >= :from)
        AND    (:to   IS NULL OR b.entryDate <= :to)
        AND    b.bheemName IS NOT NULL
        AND    b.taar IS NOT NULL
        GROUP  BY b.bheemName.name, b.taar.value
        ORDER  BY b.bheemName.name, b.taar.value
        """)
    List<Object[]> qualityWiseSummary(
            @Param("workerId") Long workerId,
            @Param("from")     LocalDate from,
            @Param("to")       LocalDate to);

    @Query("SELECT DISTINCT b.colour FROM BheemEntry b " +
            "WHERE b.colour IS NOT NULL ORDER BY b.colour")
    List<String> findDistinctColours();

    // ─────────────────────────────────────────────────────────────
    //  DUPLICATE CHECK QUERIES
    //
    //  Key: (jobWorker.id, challanNo, entryDate, bheemName.id, taar.id)
    //
    //  Uses JPQL with explicit JOIN — avoids:
    //    • "Unknown column" native SQL naming issues
    //    • TerminalPathException from path traversal on EAGER @ManyToOne
    //
    //  findExistingDuplicates returns List (not Optional) because the
    //  DB may already contain 2+ duplicates; getSingleResult() would
    //  crash with NonUniqueResultException. getResultList() is safe.
    // ─────────────────────────────────────────────────────────────

    /**
     * COUNT — called on every Save / Final Submit click.
     * 0 = safe to insert, >0 = duplicate found.
     */
    @Query("""
        SELECT COUNT(b)
        FROM   BheemEntry b
        JOIN   b.jobWorker jw
        JOIN   b.bheemName bn
        JOIN   b.taar      t
        WHERE  jw.id        = :workerId
        AND    b.challanNo  = :challanNo
        AND    b.entryDate  = :date
        AND    bn.id        = :bheemNameId
        AND    t.id         = :taarId
        """)
    long countDuplicate(
            @Param("workerId")    Long      workerId,
            @Param("challanNo")   String    challanNo,
            @Param("date")        LocalDate date,
            @Param("bheemNameId") Integer   bheemNameId,
            @Param("taarId")      Integer   taarId);

    /**
     * Fetch existing row(s) for side-by-side popup display.
     * Returns List — safe even when 2+ duplicates already exist.
     * Service takes first (lowest id); extras deleted on confirm.
     */
    @Query("""
        SELECT b
        FROM   BheemEntry b
        JOIN   b.jobWorker jw
        JOIN   b.bheemName bn
        JOIN   b.taar      t
        WHERE  jw.id        = :workerId
        AND    b.challanNo  = :challanNo
        AND    b.entryDate  = :date
        AND    bn.id        = :bheemNameId
        AND    t.id         = :taarId
        ORDER  BY b.id ASC
        """)
    List<BheemEntry> findExistingDuplicates(
            @Param("workerId")    Long      workerId,
            @Param("challanNo")   String    challanNo,
            @Param("date")        LocalDate date,
            @Param("bheemNameId") Integer   bheemNameId,
            @Param("taarId")      Integer   taarId);
    boolean existsByJobWorkerIdAndChallanNoAndEntryDateAndBheemNameIdAndTaarId(
            Long workerId,
            String challanNo,
            LocalDate entryDate,
            Long bheemId,
            Long taarId
    );


}