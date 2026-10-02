package com.erp.service;

import com.erp.dto.NotificationDtos.*;
import com.erp.entity.OrderStatus;
import com.erp.entity.Product;
import com.erp.entity.PurchaseOrder;
import com.erp.entity.SalesOrder;
import com.erp.repository.ProductRepository;
import com.erp.repository.PurchaseOrderRepository;
import com.erp.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ProductRepository productRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SalesOrderRepository salesOrderRepository;

    @Transactional(readOnly = true)
    public NotificationSummary getNotifications() {
        List<NotificationItem> items = new ArrayList<>();

        // 1. Low stock products
        List<Product> lowStock = productRepository.findLowStock();
        for (Product p : lowStock) {
            items.add(new NotificationItem(
                    "LS-" + p.getId(),
                    "LOW_STOCK",
                    "Low Stock Alert: " + p.getName(),
                    "Current stock (" + p.getQuantityInStock() + ") is at or below reorder level (" + p.getReorderLevel() + "). Reorder advised.",
                    "warning",
                    "/products",
                    LocalDateTime.now()
            ));
        }

        // 2. Pending purchase orders (DRAFT)
        List<PurchaseOrder> pendingPOs = purchaseOrderRepository.findWithSupplierByStatus(OrderStatus.DRAFT);
        for (PurchaseOrder po : pendingPOs) {
            items.add(new NotificationItem(
                    "PO-" + po.getId(),
                    "PENDING_PURCHASE",
                    "Purchase Order Draft: " + po.getOrderNumber(),
                    "Supplier: " + po.getSupplier().getName() + " · Total: $" + po.getTotal() + " pending receipt.",
                    "info",
                    "/purchase-orders",
                    po.getOrderDate()
            ));
        }

        // 3. Pending sales orders (DRAFT)
        List<SalesOrder> pendingSOs = salesOrderRepository.findWithCustomerByStatus(OrderStatus.DRAFT);
        for (SalesOrder so : pendingSOs) {
            items.add(new NotificationItem(
                    "SO-" + so.getId(),
                    "PENDING_SALES",
                    "Sales Order Draft: " + so.getOrderNumber(),
                    "Customer: " + so.getCustomer().getName() + " · Total: $" + so.getTotal() + " pending fulfillment.",
                    "info",
                    "/sales-orders",
                    so.getOrderDate()
            ));
        }

        return new NotificationSummary(
                items.size(),
                lowStock.size(),
                pendingPOs.size(),
                pendingSOs.size(),
                items
        );
    }
}
