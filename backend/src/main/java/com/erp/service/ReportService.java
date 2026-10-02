package com.erp.service;

import com.erp.dto.ReportDtos.*;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ProductRepository productRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    @Transactional(readOnly = true)
    public InventoryValuationReport getInventoryValuation() {
        List<Product> products = productRepository.findAll();
        long totalProducts = products.size();
        long totalQuantity = products.stream().mapToLong(Product::getQuantityInStock).sum();
        long lowStockCount = products.stream().filter(p -> p.getQuantityInStock() <= p.getReorderLevel()).count();

        BigDecimal totalValuation = products.stream()
                .map(p -> p.getUnitPrice().multiply(BigDecimal.valueOf(p.getQuantityInStock())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, List<Product>> byCategory = products.stream()
                .collect(Collectors.groupingBy(p -> p.getCategory() == null || p.getCategory().isBlank() ? "Uncategorized" : p.getCategory()));

        List<CategoryValuation> categories = byCategory.entrySet().stream()
                .map(e -> {
                    long count = e.getValue().size();
                    long qty = e.getValue().stream().mapToLong(Product::getQuantityInStock).sum();
                    BigDecimal val = e.getValue().stream()
                            .map(p -> p.getUnitPrice().multiply(BigDecimal.valueOf(p.getQuantityInStock())))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new CategoryValuation(e.getKey(), count, qty, val);
                })
                .sorted(Comparator.comparing(CategoryValuation::valuation).reversed())
                .toList();

        return new InventoryValuationReport(totalProducts, totalQuantity, totalValuation, lowStockCount, categories);
    }

    @Transactional(readOnly = true)
    public SalesSummaryReport getSalesSummary() {
        List<SalesOrder> orders = salesOrderRepository.findWithItemsByOrderByOrderDateDesc();
        long totalOrders = orders.size();
        long completed = orders.stream().filter(o -> o.getStatus() == OrderStatus.COMPLETED).count();
        long draft = orders.stream().filter(o -> o.getStatus() == OrderStatus.DRAFT).count();
        long cancelled = orders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();

        BigDecimal totalRevenue = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.COMPLETED)
                .map(SalesOrder::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgOrderValue = completed > 0
                ? totalRevenue.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Top Customers
        Map<Long, List<SalesOrder>> byCustomer = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.COMPLETED)
                .collect(Collectors.groupingBy(o -> o.getCustomer().getId()));

        List<PartyPerformance> topCustomers = byCustomer.entrySet().stream()
                .map(e -> {
                    String name = e.getValue().get(0).getCustomer().getName();
                    long count = e.getValue().size();
                    BigDecimal sum = e.getValue().stream().map(SalesOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new PartyPerformance(e.getKey(), name, count, sum);
                })
                .sorted(Comparator.comparing(PartyPerformance::totalAmount).reversed())
                .limit(5)
                .toList();

        // Top Selling Products
        Map<Long, ProductPerformanceBuilder> productAgg = new HashMap<>();
        orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.COMPLETED)
                .flatMap(o -> o.getItems().stream())
                .forEach(item -> {
                    productAgg.computeIfAbsent(item.getProduct().getId(),
                            k -> new ProductPerformanceBuilder(k, item.getProduct().getSku(), item.getProduct().getName()))
                            .add(item.getQuantity(), item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                });

        List<ProductPerformance> topProducts = productAgg.values().stream()
                .map(ProductPerformanceBuilder::build)
                .sorted(Comparator.comparing(ProductPerformance::revenue).reversed())
                .limit(5)
                .toList();

        return new SalesSummaryReport(totalRevenue, totalOrders, completed, draft, cancelled, avgOrderValue, topCustomers, topProducts);
    }

    @Transactional(readOnly = true)
    public PurchasesSummaryReport getPurchasesSummary() {
        List<PurchaseOrder> orders = purchaseOrderRepository.findWithItemsByOrderByOrderDateDesc();
        long totalOrders = orders.size();
        long completed = orders.stream().filter(o -> o.getStatus() == OrderStatus.COMPLETED).count();
        long draft = orders.stream().filter(o -> o.getStatus() == OrderStatus.DRAFT).count();
        long cancelled = orders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();

        BigDecimal totalSpend = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.COMPLETED)
                .map(PurchaseOrder::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgOrderValue = completed > 0
                ? totalSpend.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<Long, List<PurchaseOrder>> bySupplier = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.COMPLETED)
                .collect(Collectors.groupingBy(o -> o.getSupplier().getId()));

        List<PartyPerformance> topSuppliers = bySupplier.entrySet().stream()
                .map(e -> {
                    String name = e.getValue().get(0).getSupplier().getName();
                    long count = e.getValue().size();
                    BigDecimal sum = e.getValue().stream().map(PurchaseOrder::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new PartyPerformance(e.getKey(), name, count, sum);
                })
                .sorted(Comparator.comparing(PartyPerformance::totalAmount).reversed())
                .limit(5)
                .toList();

        return new PurchasesSummaryReport(totalSpend, totalOrders, completed, draft, cancelled, avgOrderValue, topSuppliers);
    }

    private static class ProductPerformanceBuilder {
        private final Long id;
        private final String sku;
        private final String name;
        private long units = 0;
        private BigDecimal revenue = BigDecimal.ZERO;

        public ProductPerformanceBuilder(Long id, String sku, String name) {
            this.id = id;
            this.sku = sku;
            this.name = name;
        }

        public void add(int qty, BigDecimal rev) {
            this.units += qty;
            this.revenue = this.revenue.add(rev);
        }

        public ProductPerformance build() {
            return new ProductPerformance(id, sku, name, units, revenue);
        }
    }
}
