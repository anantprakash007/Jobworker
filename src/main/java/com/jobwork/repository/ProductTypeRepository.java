package com.jobwork.repository;

import com.jobwork.domain.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductTypeRepository
        extends JpaRepository<ProductType, Integer> {

    Optional<ProductType> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
    List<ProductType> findAllByOrderByNameAsc();
   // @Query("SELECT LOWER(p.name) FROM ProductType p")
    //List<String> findAllNames();
}
