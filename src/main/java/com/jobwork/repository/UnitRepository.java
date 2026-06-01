package com.jobwork.repository;

import com.jobwork.domain.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Integer> {

    // ✅ ADD THIS (REQUIRED FOR IMPORT)
    Optional<Unit> findByNameIgnoreCase(String name);

    // existing methods
    boolean existsByNameIgnoreCase(String name);
    List<Unit> findAllByOrderByNameAsc();
    //@Query("SELECT LOWER(u.name) FROM Unit u")
   // List<String> findAllNames();
}