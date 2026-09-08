package com.jobwork.repository;

import com.jobwork.domain.JobWorker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobWorkerRepository
        extends JpaRepository<JobWorker, Long>,
        JpaSpecificationExecutor<JobWorker> {

    /**
     * Find worker by challan number.
     *
     * Kept for backward compatibility with existing code.
     */
    Optional<JobWorker> findByChallanNo(String challanNo);

    /**
     * CENTRAL WORKER LIST.
     *
     * Every Entry/Report screen should get its JobWorker list
     * from JobWorkerService.findAll().
     */
    List<JobWorker> findAllByOrderByNameAsc();

    /**
     * Existing challan duplicate check.
     */
    boolean existsByChallanNo(String challanNo);

    /**
     * Case-insensitive worker name lookup.
     */
    Optional<JobWorker> findByNameIgnoreCase(String name);

    /**
     * Duplicate worker-name check.
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Duplicate worker-name check while editing.
     */
    boolean existsByNameIgnoreCaseAndIdNot(
            String name,
            Long id
    );

    /**
     * Duplicate mobile check.
     */
    boolean existsByPhone(String phone);

    /**
     * Duplicate mobile check while editing.
     */
    boolean existsByPhoneAndIdNot(
            String phone,
            Long id
    );

    /**
     * Used by existing application logic.
     */
    @Query("SELECT j.id FROM JobWorker j")
    List<Long> findAllIds();
}