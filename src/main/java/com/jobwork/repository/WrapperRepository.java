package com.jobwork.repository;

import com.jobwork.domain.Wrapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WrapperRepository
        extends JpaRepository<Wrapper, Long> {

    // Duplicate guard used in WrapperMasterController.onSave()
    boolean existsByNameIgnoreCase(String name);

    // Sorted list used in the master table and combo boxes
    List<Wrapper> findAllByOrderByNameAsc();

    // Distinct names for MasterService.findAllColours()
    @Query("SELECT DISTINCT w.name FROM Wrapper w ORDER BY w.name")
    List<String> findDistinctColours();
    Optional<Wrapper> findByNameIgnoreCase(String name);
}