package com.erp.repository;

import com.erp.entity.OrderStatus;
import com.erp.entity.SalesOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

    Optional<SalesOrder> findWithItemsById(Long id);

    @Query("""
            SELECT DISTINCT so FROM SalesOrder so
            JOIN FETCH so.customer
            LEFT JOIN FETCH so.items
            ORDER BY so.orderDate DESC
            """)
    List<SalesOrder> findWithItemsByOrderByOrderDateDesc();

    List<SalesOrder> findTop5ByOrderByOrderDateDesc();

    long countByStatus(OrderStatus status);

    @Query("SELECT so FROM SalesOrder so JOIN FETCH so.customer WHERE so.status = :status ORDER BY so.orderDate DESC")
    List<SalesOrder> findWithCustomerByStatus(@Param("status") OrderStatus status);

    @Query("SELECT COALESCE(SUM(so.total), 0) FROM SalesOrder so WHERE so.status = :status")
    BigDecimal sumTotalByStatus(@Param("status") OrderStatus status);

    @Query("SELECT COALESCE(MAX(so.id), 0) FROM SalesOrder so")
    long nextSequence();
}
