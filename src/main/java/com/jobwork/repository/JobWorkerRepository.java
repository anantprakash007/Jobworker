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

    // Challan number lookup — unique constraint guarantees at most one.
    Optional<JobWorker> findByChallanNo(String challanNo);

    // Used in all ComboBox population calls (alphabetical).
    List<JobWorker> findAllByOrderByNameAsc();
    // Duplicate guard in JobWorkerMasterController.
    boolean existsByChallanNo(String challanNo);
    // ✅ Case-insensitive lookup
    Optional<JobWorker> findByNameIgnoreCase(String name);
    @Query("SELECT j.id FROM JobWorker j")
    List<Long> findAllIds();

}