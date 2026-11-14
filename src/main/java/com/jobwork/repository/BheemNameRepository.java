package com.jobwork.repository;

import com.jobwork.domain.BheemName;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BheemNameRepository extends JpaRepository<BheemName, Integer> {

  Optional<BheemName> findByName(String name);

  boolean existsByName(String name);

  // ✅ CORRECT METHOD (NO PARAMETER)
  List<BheemName> findAllByOrderByNameAsc();

  Optional<BheemName> findByNameIgnoreCase(String name);
}
