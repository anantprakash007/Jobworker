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

@Service
@Transactional
@RequiredArgsConstructor
public class JobWorkerService {

    private final JobWorkerRepository repo;
    private final ProductEntryRepository productRepo;
    private final MoneyReceiptRepository moneyRepo;

    // ═════════════════════════════════════════════════════════════
    // READ
    // ═════════════════════════════════════════════════════════════

    /**
     * CENTRAL Job Worker source.
     *
     * Every Entry and Report controller must use this method.
     *
     * Result:
     *     alphabetical by worker name
     */
    @Transactional(readOnly = true)
    public List<JobWorker> findAll() {
        return repo.findAllByOrderByNameAsc();
    }

    /**
     * Find worker by ID.
     */
    @Transactional(readOnly = true)
    public Optional<JobWorker> findById(Long id) {
        return repo.findById(id);
    }

    /**
     * Find worker by challan number.
     */
    @Transactional(readOnly = true)
    public Optional<JobWorker> findByChallanNo(String challanNo) {

        if (challanNo == null || challanNo.isBlank()) {
            return Optional.empty();
        }

        return repo.findByChallanNo(challanNo.trim());
    }

    /**
     * Existing challan duplicate check.
     */
    @Transactional(readOnly = true)
    public boolean existsByChallanNo(String challanNo) {

        if (challanNo == null || challanNo.isBlank()) {
            return false;
        }

        return repo.existsByChallanNo(challanNo.trim());
    }

    /**
     * Find worker by name.
     */
    @Transactional(readOnly = true)
    public Optional<JobWorker> findByNameIgnoreCase(String name) {

        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        return repo.findByNameIgnoreCase(name.trim());
    }

    /**
     * Check duplicate worker name.
     */
    @Transactional(readOnly = true)
    public boolean existsByNameIgnoreCase(String name) {

        if (name == null || name.isBlank()) {
            return false;
        }

        return repo.existsByNameIgnoreCase(name.trim());
    }

    /**
     * Check duplicate worker name during EDIT.
     */
    @Transactional(readOnly = true)
    public boolean existsByNameIgnoreCaseAndIdNot(
            String name,
            Long id) {

        if (name == null || name.isBlank() || id == null) {
            return false;
        }

        return repo.existsByNameIgnoreCaseAndIdNot(
                name.trim(),
                id
        );
    }

    /**
     * Check duplicate mobile number.
     */
    @Transactional(readOnly = true)
    public boolean existsByPhone(String phone) {

        if (phone == null || phone.isBlank()) {
            return false;
        }

        return repo.existsByPhone(phone.trim());
    }

    /**
     * Check duplicate mobile number during EDIT.
     */
    @Transactional(readOnly = true)
    public boolean existsByPhoneAndIdNot(
            String phone,
            Long id) {

        if (phone == null || phone.isBlank() || id == null) {
            return false;
        }

        return repo.existsByPhoneAndIdNot(
                phone.trim(),
                id
        );
    }

    // ═════════════════════════════════════════════════════════════
    // WRITE
    // ═════════════════════════════════════════════════════════════

    /**
     * Save or update Job Worker.
     */
    public JobWorker save(JobWorker worker) {

        if (worker == null) {
            throw new IllegalArgumentException("Worker cannot be null");
        }

        if (worker.getName() != null) {
            worker.setName(worker.getName().trim());
        }

        if (worker.getPhone() != null) {
            worker.setPhone(worker.getPhone().trim());
        }

        if (worker.getAddress() != null) {
            worker.setAddress(worker.getAddress().trim());
        }

        return repo.save(worker);
    }

    /**
     * Delete worker.
     */
    public void deleteById(Long id) {
        repo.deleteById(id);
    }

    /**
     * Check whether worker already has transactions.
     */
    @Transactional(readOnly = true)
    public boolean hasTransactions(Long workerId) {

        if (workerId == null) {
            return false;
        }

        boolean hasWork =
                productRepo.existsByJobWorker_Id(workerId);

        boolean hasMoney =
                moneyRepo.existsByJobWorker_Id(workerId);

        return hasWork || hasMoney;
    }
}