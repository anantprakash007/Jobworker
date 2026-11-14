package com.jobwork.repository;

import com.jobwork.domain.DeliveryLocation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// ── DeliveryLocationRepository ──────────────────────────────
@Repository
public interface DeliveryLocationRepository extends JpaRepository<DeliveryLocation, Integer> {

  boolean existsByNameIgnoreCase(String name);

  List<DeliveryLocation> findAllByOrderByNameAsc();

  Optional<DeliveryLocation> findByNameIgnoreCase(String name);
}
