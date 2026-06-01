package com.jobwork.service;

import com.jobwork.domain.*;
import com.jobwork.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MasterService — COMPLETE VERSION
 * ──────────────────────────────────────────────────────────────────
 * Methods added in this version (previously missing):
 *   addProductType(name)            → create new ProductType
 *   updateProductType(id, newName)  → rename existing ProductType
 *   deleteProductType(id)           → delete type + cascade names
 *   addProductName(typeId, pn)      → create new ProductName under a type
 *   updateProductName(pn)           → save edited ProductName (all 7 fields)
 *   deleteProductName(id)           → delete a single ProductName
 *
 * Pre-existing read methods preserved:
 *   findAllTypes(), getProductNames(typeId),
 *   findAllUnits(), findAllWrappers(), findAllYarnTypes(),
 *   findAllLocations(), findAllBheemNames(),
 *   getTaarsByBheemName(id), findAllTaars(), findAllColours()
 *
 * Required repositories (add to AllRepositories.java if missing):
 *   ProductTypeRepository — extends JpaRepository<ProductType, Integer>
 *   ProductNameRepository — extends JpaRepository<ProductName, Integer>
 *   UnitRepository, WrapperRepository, YarnTypeRepository,
 *   BheemNameRepository, TaarRepository, ColourRepository
 * ──────────────────────────────────────────────────────────────────
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MasterService {

    private final ProductTypeRepository  productTypeRepo;
    private final ProductNameRepository  productNameRepo;
    private final UnitRepository         unitRepo;
    private final WrapperRepository      wrapperRepo;
    private final YarnTypeRepository     yarnTypeRepo;
    private final DeliveryLocationRepository locationRepo;
    private final BheemNameRepository    bheemNameRepo;
    private final TaarRepository         taarRepo;

    // ════════════════════════════════════════════════════════════
    //  PRODUCT TYPE — CRUD
    // ════════════════════════════════════════════════════════════

    /** Return all Product Types ordered by name */
    public List<ProductType> findAllTypes() {
        return productTypeRepo.findAllByOrderByNameAsc();
    }

    /**
     * Add a new Product Type.
     * Throws IllegalArgumentException if name is blank or already exists.
     */
    @Transactional
    public ProductType addProductType(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Type name cannot be empty.");

        // Check for duplicate name (case-insensitive)
        boolean exists = productTypeRepo.findAllByOrderByNameAsc().stream()
                .anyMatch(t -> t.getName().equalsIgnoreCase(name.trim()));
        if (exists)
            throw new IllegalArgumentException(
                    "Type '" + name + "' already exists.");

        ProductType pt = new ProductType();
        pt.setName(name.trim());
        return productTypeRepo.save(pt);
    }

    /**
     * Rename an existing Product Type.
     * @param id      the ProductType id to update
     * @param newName the new name to set
     */
    @Transactional
    public ProductType updateProductType(Integer id, String newName) {
        if (newName == null || newName.isBlank())
            throw new IllegalArgumentException("Type name cannot be empty.");

        ProductType pt = productTypeRepo.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Product Type not found: " + id));
        pt.setName(newName.trim());
        return productTypeRepo.save(pt);
    }

    /**
     * Delete a Product Type and all its associated Product Names.
     * @param id the ProductType id to delete
     */
    @Transactional
    public void deleteProductType(Integer id) {
        // Delete all names under this type first
        List<ProductName> names = productNameRepo.findByProductTypeIdOrderByNameAsc(id);
        if (!names.isEmpty())
            productNameRepo.deleteAll(names);

        productTypeRepo.deleteById(id);
    }

    // ════════════════════════════════════════════════════════════
    //  PRODUCT NAME — CRUD
    // ════════════════════════════════════════════════════════════

    /**
     * Return all Product Names for a given type, ordered by name.
     * @param typeId the ProductType id
     */
    public List<ProductName> getProductNames(Integer typeId) {
        return productNameRepo.findByProductTypeIdOrderByNameAsc(typeId);
    }

    /**
     * Add a new Product Name under a specific type.
     * The pn object should already have all 7 fields set by the controller:
     *   name, pick, length, width, totalPicks, reed, reedSpace
     * @param typeId the ProductType id to associate
     * @param pn     the ProductName to save (productType will be set here)
     */
    @Transactional
    public ProductName addProductName(Integer typeId, ProductName pn) {
        if (pn.getName() == null || pn.getName().isBlank())
            throw new IllegalArgumentException("Product Name cannot be empty.");

        ProductType pt = productTypeRepo.findById(typeId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Product Type not found: " + typeId));
        pn.setProductType(pt);
        return productNameRepo.save(pn);
    }

    /**
     * Update an existing Product Name — saves all 7 fields.
     * Controller sets: name, pick, length, width, totalPicks, reed, reedSpace
     * on the ProductName entity before calling this method.
     * @param pn the ProductName entity with updated field values
     */
    @Transactional
    public ProductName updateProductName(ProductName pn) {
        if (pn.getId() == null)
            throw new IllegalArgumentException("ProductName id is null — cannot update.");
        if (pn.getName() == null || pn.getName().isBlank())
            throw new IllegalArgumentException("Product Name cannot be empty.");

        return productNameRepo.save(pn);
    }

    /**
     * Delete a single Product Name by id.
     * @param id the ProductName id to delete
     */
    @Transactional
    public void deleteProductName(Integer id) {
        productNameRepo.deleteById(id);
    }

    // ════════════════════════════════════════════════════════════
    //  UNIT
    // ════════════════════════════════════════════════════════════

    public List<Unit> findAllUnits() {
        return unitRepo.findAllByOrderByNameAsc();
    }

    // ════════════════════════════════════════════════════════════
    //  WRAPPER
    // ════════════════════════════════════════════════════════════

    public List<Wrapper> findAllWrappers() {
        return wrapperRepo.findAllByOrderByNameAsc();
    }

    // ════════════════════════════════════════════════════════════
    //  YARN TYPE
    // ════════════════════════════════════════════════════════════

    public List<YarnType> findAllYarnTypes() {
        return yarnTypeRepo.findAllByOrderByNameAsc();
    }

    // ════════════════════════════════════════════════════════════
    //  DELIVERY LOCATION
    //  Returns distinct delivery location strings from the DB.
    //  YarnEntry and BheemEntry store deliveryLocation as String.
    // ════════════════════════════════════════════════════════════

    public List<DeliveryLocation> findAllLocations() {
        return locationRepo.findAllByOrderByNameAsc();
    }
    // ════════════════════════════════════════════════════════════
    //  BHEEM NAME
    // ════════════════════════════════════════════════════════════

    public List<BheemName> findAllBheemNames() {
        return bheemNameRepo.findAllByOrderByNameAsc();
    }

    // ════════════════════════════════════════════════════════════
    //  TAAR
    // ════════════════════════════════════════════════════════════

    public List<Taar> getTaarsByBheemName(Integer bheemNameId) {
        return taarRepo.findByBheemNameIdOrderByValueAsc(bheemNameId);
    }

    public List<Taar> findAllTaars() {
        return taarRepo.findAll();
    }

    // ════════════════════════════════════════════════════════════
    //  COLOURS
    //  Returns distinct colour strings from BheemEntry and YarnEntry.
    // ════════════════════════════════════════════════════════════

    public List<String> findAllColours() {
        // 🔥 UPDATED: clean + safe
        return wrapperRepo.findDistinctColours()
                .stream()
                .filter(c -> c != null && !c.trim().isEmpty()) // 🔥 NEW
                .map(String::trim)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}