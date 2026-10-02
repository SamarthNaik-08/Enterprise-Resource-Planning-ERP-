package com.erp.dto;

import java.math.BigDecimal;
import java.util.List;

public class ReportDtos {

    public record CategoryValuation(
            String category,
            long productCount,
            long totalQuantity,
            BigDecimal valuation
    ) {}

    public record InventoryValuationReport(
            long totalProducts,
            long totalQuantityInStock,
            BigDecimal totalValuation,
            long lowStockCount,
            List<CategoryValuation> categories
    ) {}

    public record PartyPerformance(
            Long partyId,
            String partyName,
            long orderCount,
            BigDecimal totalAmount
    ) {}

    public record ProductPerformance(
            Long productId,
            String sku,
            String productName,
            long unitsSold,
            BigDecimal revenue
    ) {}

    public record SalesSummaryReport(
            BigDecimal totalRevenue,
            long totalOrders,
            long completedOrders,
            long draftOrders,
            long cancelledOrders,
            BigDecimal averageOrderValue,
            List<PartyPerformance> topCustomers,
            List<ProductPerformance> topProducts
    ) {}

    public record PurchasesSummaryReport(
            BigDecimal totalExpenditure,
            long totalOrders,
            long completedOrders,
            long draftOrders,
            long cancelledOrders,
            BigDecimal averageOrderValue,
            List<PartyPerformance> topSuppliers
    ) {}
}
