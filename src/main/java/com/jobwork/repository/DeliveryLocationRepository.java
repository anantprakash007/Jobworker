package com.jobwork.repository;

import com.jobwork.domain.DeliveryLocation;
import com.jobwork.domain.YarnType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;


// ── DeliveryLocationRepository ──────────────────────────────
@Repository
public interface DeliveryLocationRepository
        extends JpaRepository<DeliveryLocation, Integer> {

    boolean existsByNameIgnoreCase(String name);
    List<DeliveryLocation> findAllByOrderByNameAsc();
    Optional<DeliveryLocation> findByNameIgnoreCase(String name);

}

