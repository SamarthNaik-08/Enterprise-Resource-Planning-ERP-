package com.erp.repository;

import com.erp.entity.OrderStatus;
import com.erp.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long>, JpaSpecificationExecutor<PurchaseOrder> {

    Optional<PurchaseOrder> findWithItemsById(Long id);

    @Query("""
            SELECT DISTINCT po FROM PurchaseOrder po
            JOIN FETCH po.supplier
            LEFT JOIN FETCH po.items
            ORDER BY po.orderDate DESC
            """)
    List<PurchaseOrder> findWithItemsByOrderByOrderDateDesc();

    List<PurchaseOrder> findTop5ByOrderByOrderDateDesc();

    long countByStatus(OrderStatus status);

    @Query("SELECT po FROM PurchaseOrder po JOIN FETCH po.supplier WHERE po.status = :status ORDER BY po.orderDate DESC")
    List<PurchaseOrder> findWithSupplierByStatus(@Param("status") OrderStatus status);

    @Query("SELECT COALESCE(SUM(po.total), 0) FROM PurchaseOrder po WHERE po.status = :status")
    BigDecimal sumTotalByStatus(@Param("status") OrderStatus status);

    @Query("SELECT COALESCE(MAX(po.id), 0) FROM PurchaseOrder po")
    long nextSequence();
}
