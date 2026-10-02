package com.erp.service;

import com.erp.dto.DashboardDtos.*;
import com.erp.entity.OrderStatus;
import com.erp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SalesOrderRepository salesOrderRepository;

    @Transactional(readOnly = true)
    public DashboardResponse getStats() {
        List<LowStockItem> lowStock = productRepository.findLowStock().stream()
                .map(p -> new LowStockItem(p.getId(), p.getSku(), p.getName(),
                        p.getQuantityInStock(), p.getReorderLevel()))
                .toList();

        BigDecimal revenue = salesOrderRepository.sumTotalByStatus(OrderStatus.COMPLETED);
        BigDecimal spend = purchaseOrderRepository.sumTotalByStatus(OrderStatus.COMPLETED);

        List<RecentOrder> recentSales = salesOrderRepository.findTop5ByOrderByOrderDateDesc().stream()
                .map(o -> new RecentOrder(o.getId(), o.getOrderNumber(), o.getCustomer().getName(),
                        o.getStatus().name(), o.getOrderDate(), o.getTotal()))
                .toList();

        List<RecentOrder> recentPurchases =
                purchaseOrderRepository.findTop5ByOrderByOrderDateDesc().stream()
                        .map(o -> new RecentOrder(o.getId(), o.getOrderNumber(),
                                o.getSupplier().getName(), o.getStatus().name(),
                                o.getOrderDate(), o.getTotal()))
                        .toList();

        List<com.erp.entity.Product> allProducts = productRepository.findAll();
        long totalProductCount = allProducts.size();

        BigDecimal inventoryValue = allProducts.stream()
                .map(p -> p.getUnitPrice().multiply(BigDecimal.valueOf(p.getQuantityInStock())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long outOfStockCount = allProducts.stream().filter(p -> p.getQuantityInStock() <= 0).count();
        long lowStockCount = allProducts.stream().filter(p -> p.getQuantityInStock() > 0 && p.getQuantityInStock() <= p.getReorderLevel()).count();
        long inStockCount = allProducts.stream().filter(p -> p.getQuantityInStock() > p.getReorderLevel()).count();
        int healthyPercentage = totalProductCount > 0 ? (int) ((inStockCount * 100) / totalProductCount) : 100;
        StockHealth stockHealth = new StockHealth(inStockCount, lowStockCount, outOfStockCount, healthyPercentage);

        // Category breakdown
        java.util.Map<String, List<com.erp.entity.Product>> byCategory = allProducts.stream()
                .collect(java.util.stream.Collectors.groupingBy(p -> p.getCategory() != null ? p.getCategory() : "General"));

        List<CategoryBreakdown> categoryDistribution = byCategory.entrySet().stream()
                .map(e -> {
                    BigDecimal catVal = e.getValue().stream()
                            .map(p -> p.getUnitPrice().multiply(BigDecimal.valueOf(p.getQuantityInStock())))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new CategoryBreakdown(e.getKey(), e.getValue().size(), catVal);
                })
                .sorted((a, b) -> b.inventoryValue().compareTo(a.inventoryValue()))
                .toList();

        return new DashboardResponse(
                totalProductCount,
                customerRepository.count(),
                supplierRepository.count(),
                userRepository.count(),
                lowStock.size(),
                purchaseOrderRepository.countByStatus(OrderStatus.DRAFT),
                salesOrderRepository.countByStatus(OrderStatus.DRAFT),
                revenue,
                spend,
                inventoryValue,
                stockHealth,
                categoryDistribution,
                lowStock,
                recentSales,
                recentPurchases);
    }
}
