package com.jobwork.repository;

import com.jobwork.domain.OpeningAdjustment;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpeningAdjustmentRepository extends JpaRepository<OpeningAdjustment, Long> {

  List<OpeningAdjustment> findByWorkerId(Long workerId);

  List<OpeningAdjustment> findByWorkerIdAndDateBetween(Long workerId, LocalDate from, LocalDate to);
}
