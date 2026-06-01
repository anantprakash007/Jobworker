package com.jobwork.repository;

import com.jobwork.domain.YarnType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface YarnTypeRepository
        extends JpaRepository<YarnType, Integer> {

    boolean existsByNameIgnoreCase(String name);

    // ✅ ADD THIS (REQUIRED)
    Optional<YarnType> findByNameIgnoreCase(String name);

    @Query("SELECT DISTINCT y.name FROM YarnType y ORDER BY y.name")
    List<String> findDistinctColours();

    List<YarnType> findAllByOrderByNameAsc();

}