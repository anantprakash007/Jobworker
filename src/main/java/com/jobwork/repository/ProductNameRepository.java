package com.jobwork.repository;

import com.jobwork.domain.ProductName;
import com.jobwork.domain.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductNameRepository
        extends JpaRepository<ProductName, Integer> {

    // Called whenever the ProductType combo-box selection changes
    List<ProductName> findByProductTypeIdOrderByNameAsc(Integer productTypeId);

    // Duplicate guard in the master screen
    boolean existsByNameIgnoreCaseAndProductTypeId(
            String name, Integer productTypeId);
    boolean existsByProductTypeIdAndNameIgnoreCase(Integer typeId, String name);
    List<ProductName> findByProductType(ProductType type);
    // ✅ Case-insensitive lookup
    Optional<ProductName> findByNameIgnoreCase(String name);
    //@Query("SELECT LOWER(p.name) FROM ProductName p")
   // List<String> findAllNames();
}
