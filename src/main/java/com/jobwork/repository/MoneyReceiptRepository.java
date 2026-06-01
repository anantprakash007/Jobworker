package com.jobwork.repository;

import com.jobwork.domain.MoneyReceipt;
import com.jobwork.domain.TransferMode;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface MoneyReceiptRepository
        extends JpaRepository<MoneyReceipt, Long>,
        JpaSpecificationExecutor<MoneyReceipt> {

    List<MoneyReceipt> findByJobWorkerId(Long workerId);
    List<MoneyReceipt> findByChallanNo(String challanNo);
    boolean existsByJobWorker_Id(Long workerId);

    // ✅ FIXED (receiptDate)
    List<MoneyReceipt> findByJobWorkerIdAndReceiptDateBetween(
            Long workerId, LocalDate from, LocalDate to);

    // ── Duplicate check ─────────────────────────────

    @Query("""
        SELECT COUNT(m)
        FROM MoneyReceipt m
        WHERE m.jobWorker.id = :workerId
       AND LOWER(m.challanNo) = LOWER(:challanNo)
        AND   m.receiptDate  = :date
  
    """)
    long countDuplicate(
            @Param("workerId") Long workerId,
            @Param("challanNo") String challanNo,
            @Param("date") LocalDate date

    );

    // ✅ FIXED (ALL FIELDS)

    @Query("""
SELECT m
FROM MoneyReceipt m
WHERE m.jobWorker.id = :workerId
AND LOWER(m.challanNo) = LOWER(:challanNo)
AND m.receiptDate = :date

ORDER BY m.id ASC
""")
    List<MoneyReceipt> findExistingDuplicates(
            @Param("workerId") Long workerId,
            @Param("challanNo") String challanNo,
            @Param("date") LocalDate date

    );

    // ── Reports ─────────────────────────────────────

    @Query("""
        SELECT m FROM MoneyReceipt m
        WHERE (:workerId IS NULL OR m.jobWorker.id = :workerId)
        AND   (:from IS NULL OR m.receiptDate >= :from)
        AND   (:to   IS NULL OR m.receiptDate <= :to)
        ORDER BY m.receiptDate ASC, m.id ASC
    """)
    List<MoneyReceipt> findByFilter(
            @Param("workerId") Long workerId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    // ✅ FIXED (transferMode)
    @Query("""
        SELECT COALESCE(SUM(m.amount), 0)
        FROM MoneyReceipt m
        WHERE m.jobWorker.id = :workerId
        AND   m.transferMode = :mode
        AND   (:from IS NULL OR m.receiptDate >= :from)
        AND   (:to   IS NULL OR m.receiptDate <= :to)
    """)
    BigDecimal sumByType(
            @Param("workerId") Long workerId,
            @Param("mode") TransferMode mode,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
        SELECT SUM(m.amount)
        FROM MoneyReceipt m
        WHERE m.jobWorker.id = :workerId
    """)
    Optional<BigDecimal> sumPaidByWorker(@Param("workerId") Long workerId);
    @Query("""
SELECT COALESCE(SUM(
    CASE 
        WHEN m.entryType = 'ADVANCE' THEN -m.amount
        WHEN m.entryType = 'PAYMENT' THEN  m.amount
        ELSE 0
    END
),0)
FROM MoneyReceipt m
WHERE m.jobWorker.id = :workerId
AND m.receiptDate < :fromDate
""")
    BigDecimal findOpeningBalance(
            @Param("workerId") Long workerId,
            @Param("fromDate") LocalDate fromDate
    );
}