package com.jobwork.service;

import com.jobwork.domain.JobWorker;
import com.jobwork.repository.JobWorkerRepository;
import com.jobwork.repository.MoneyReceiptRepository;
import com.jobwork.repository.ProductEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JobWorkerService
 * ────────────────────────────────────────────────────────────────
 * Business logic for job worker master operations.
 *
 * Methods used by:
 *  All entry controllers (combo box population):
 *    → findAll()
 *
 *  All report controllers (combo box population):
 *    → findAll()
 *
 *  JobWorkerMasterController (CRUD):
 *    → findAll(), save(), delete(), findByChallanNo(),
 *      existsByChallanNo()
 */
@Service
@Transactional
@RequiredArgsConstructor
public class JobWorkerService {

    private final JobWorkerRepository repo;
    private final ProductEntryRepository productRepo;
    private final MoneyReceiptRepository moneyRepo;

    // ── Read operations ──────────────────────────────────────────

    /**
     * All job workers sorted by name.
     * Loaded into every worker ComboBox across all screens.
     */
    @Transactional(readOnly = true)
    public List<JobWorker> findAll() {
        return repo.findAllByOrderByNameAsc();
    }

    /** Find by ID — used by edit dialogs. */
    @Transactional(readOnly = true)
    public Optional<JobWorker> findById(Long id) {
        return repo.findById(id);
    }

    /**
     * Find by challan number (unique) — used for search-by-challan.
     * Returns Optional.empty() if not found.
     */
    @Transactional(readOnly = true)
    public Optional<JobWorker> findByChallanNo(String challanNo) {
        return repo.findByChallanNo(challanNo);
    }

    /**
     * Check duplicate challan — used by JobWorkerMasterController.onSave().
     */
    @Transactional(readOnly = true)
    public boolean existsByChallanNo(String challanNo) {
        return repo.existsByChallanNo(challanNo);
    }

    // ── Write operations ─────────────────────────────────────────

    /** Save or update a job worker. */
    public JobWorker save(JobWorker worker) {
        return repo.save(worker);
    }

    /** Hard-delete by ID. */
    public void deleteById(Long id) {
        repo.deleteById(id);
    }
    public boolean hasTransactions(Long workerId) {

        boolean hasWork = productRepo.existsByJobWorker_Id(workerId);
        boolean hasMoney = moneyRepo.existsByJobWorker_Id(workerId);

        return hasWork || hasMoney;
    }
}