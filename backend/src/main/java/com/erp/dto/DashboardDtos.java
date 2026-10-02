package com.erp.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class DashboardDtos {

    public record DashboardResponse(
            long totalProducts,
            long totalCustomers,
            long totalSuppliers,
            long totalUsers,
            long lowStockCount,
            long pendingPurchaseOrders,
            long pendingSalesOrders,
            BigDecimal totalRevenue,
            BigDecimal totalPurchaseSpend,
            BigDecimal totalInventoryValue,
            StockHealth stockHealth,
            List<CategoryBreakdown> categoryDistribution,
            List<LowStockItem> lowStockItems,
            List<RecentOrder> recentSalesOrders,
            List<RecentOrder> recentPurchaseOrders) {
    }

    public record CategoryBreakdown(
            String category,
            long productCount,
            BigDecimal inventoryValue) {
    }

    public record StockHealth(
            long inStockCount,
            long lowStockCount,
            long outOfStockCount,
            int healthyPercentage) {
    }

    public record LowStockItem(
            Long id,
            String sku,
            String name,
            Integer quantityInStock,
            Integer reorderLevel) {
    }

    public record RecentOrder(
            Long id,
            String orderNumber,
            String partyName,
            String status,
            LocalDateTime orderDate,
            BigDecimal total) {
    }
}
