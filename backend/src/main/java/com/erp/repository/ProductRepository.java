package com.erp.repository;

import com.erp.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySkuIgnoreCase(String sku);

    java.util.Optional<Product> findBySkuIgnoreCase(String sku);

    @Query("""
            SELECT p FROM Product p
            WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(p.category, '')) LIKE LOWER(CONCAT('%', :q, '%'))
            """)
    Page<Product> search(@Param("q") String q, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.quantityInStock <= p.reorderLevel " +
           "ORDER BY p.quantityInStock ASC")
    List<Product> findLowStock();
}
