package com.jobwork.repository;

import com.jobwork.domain.Taar;
import com.jobwork.domain.BheemName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaarRepository extends JpaRepository<Taar, Integer> {

   // List<Taar> findByBheemName(BheemName bheemName);
   // Load taars for a specific bheem name — used in Bheem Entry cascade
   List<Taar> findByBheemNameIdOrderByValueAsc(long bheemNameId);

    // All taars sorted — used in master table
    List<Taar> findAllByOrderByBheemNameNameAscValueAsc();

    // Duplicate guard: same value under same bheem name
    boolean existsByBheemNameIdAndValue(long bheemNameId, long value);
    Optional<Taar> findByValue(long value);
}
