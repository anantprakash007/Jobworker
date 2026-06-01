package com.jobwork.repository;

import com.jobwork.domain.OpeningAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface OpeningAdjustmentRepository
        extends JpaRepository<OpeningAdjustment, Long> {

    List<OpeningAdjustment> findByWorkerId(Long workerId);

    List<OpeningAdjustment> findByWorkerIdAndDateBetween(
            Long workerId, LocalDate from, LocalDate to);
}