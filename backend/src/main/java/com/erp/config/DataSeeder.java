package com.erp.config;

import com.erp.entity.*;
import com.erp.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds demo users and sample ERP data on first start-up so the
 * application is immediately explorable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final com.erp.repository.AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers();
        seedCatalog();
        seedOrders();
        seedAuditLogs();
    }

    private void seedUsers() {
        if (userRepository.count() > 0) {
            return;
        }
        userRepository.saveAll(List.of(
                User.builder().fullName("Alice Admin").email("admin@erp.com")
                        .password(passwordEncoder.encode("admin123")).role(Role.ADMIN).build(),
                User.builder().fullName("Mark Manager").email("manager@erp.com")
                        .password(passwordEncoder.encode("manager123")).role(Role.MANAGER).build(),
                User.builder().fullName("Eve Employee").email("employee@erp.com")
                        .password(passwordEncoder.encode("employee123")).role(Role.EMPLOYEE).build()));
        log.info("Seeded demo users: admin@erp.com / manager@erp.com / employee@erp.com");
    }

    private void seedCatalog() {
        if (supplierRepository.count() > 0) {
            return;
        }
        Supplier techParts = supplierRepository.save(Supplier.builder()
                .name("TechParts Global").email("sales@techparts.io")
                .phone("+1-555-0101").address("12 Industrial Park, Shenzhen").build());
        Supplier officeHub = supplierRepository.save(Supplier.builder()
                .name("OfficeHub Supplies").email("orders@officehub.com")
                .phone("+1-555-0202").address("88 Commerce St, Chicago").build());

        customerRepository.saveAll(List.of(
                Customer.builder().name("Acme Corporation").email("buy@acme.com")
                        .phone("+1-555-1001").address("1 Main St, New York").build(),
                Customer.builder().name("Globex Ltd").email("procurement@globex.com")
                        .phone("+44-20-7000-1000").address("10 Downing, London").build(),
                Customer.builder().name("Initech LLC").email("hello@initech.io")
                        .phone("+1-555-3003").address("4120 Freidrich Ln, Austin").build()));

        productRepository.saveAll(List.of(
                product("TP-LAP-001", "ThinkPad X1 Laptop", "Laptops", new BigDecimal("1299.00"),
                        25, 5, techParts),
                product("TP-DSK-002", "27\" 4K Monitor", "Monitors", new BigDecimal("349.99"),
                        40, 10, techParts),
                product("TP-KBD-003", "Mechanical Keyboard", "Accessories", new BigDecimal("89.50"),
                        8, 15, techParts),   // low stock
                product("TP-MSE-004", "Wireless Mouse", "Accessories", new BigDecimal("25.00"),
                        120, 20, techParts),
                product("OH-CHR-005", "Ergonomic Office Chair", "Furniture", new BigDecimal("219.00"),
                        6, 10, officeHub),   // low stock
                product("OH-DSK-006", "Standing Desk 140cm", "Furniture", new BigDecimal("399.00"),
                        18, 5, officeHub)));
        log.info("Seeded demo catalog (2 suppliers, 3 customers, 6 products)");
    }

    private Product product(String sku, String name, String category, BigDecimal price,
                            int qty, int reorder, Supplier supplier) {
        return Product.builder()
                .sku(sku).name(name).category(category).unitPrice(price)
                .quantityInStock(qty).reorderLevel(reorder).supplier(supplier)
                .build();
    }

    private void seedOrders() {
        if (purchaseOrderRepository.count() > 0) {
            return;
        }
        Supplier supplier = supplierRepository.findAll().get(0);
        Customer customer = customerRepository.findAll().get(0);
        Product keyboard = productRepository.findBySkuIgnoreCase("TP-KBD-003").orElseThrow();
        Product monitor = productRepository.findBySkuIgnoreCase("TP-DSK-002").orElseThrow();
        Product laptop = productRepository.findBySkuIgnoreCase("TP-LAP-001").orElseThrow();

        PurchaseOrder po = PurchaseOrder.builder()
                .orderNumber("PO-2026-00001").supplier(supplier).status(OrderStatus.DRAFT)
                .build();
        po.getItems().add(PurchaseOrderItem.builder().order(po).product(keyboard)
                .quantity(50).unitCost(keyboard.getUnitPrice()).build());
        po.getItems().add(PurchaseOrderItem.builder().order(po).product(monitor)
                .quantity(20).unitCost(monitor.getUnitPrice()).build());
        po.recalculateTotal();
        purchaseOrderRepository.save(po);

        SalesOrder so = SalesOrder.builder()
                .orderNumber("SO-2026-00001").customer(customer).status(OrderStatus.DRAFT)
                .build();
        so.getItems().add(SalesOrderItem.builder().order(so).product(laptop)
                .quantity(2).unitPrice(laptop.getUnitPrice()).build());
        so.recalculateTotal();
        salesOrderRepository.save(so);

        log.info("Seeded demo orders (1 purchase order, 1 sales order)");
    }

    private void seedAuditLogs() {
        if (auditLogRepository.count() > 0) {
            return;
        }
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        auditLogRepository.saveAll(List.of(
                com.erp.entity.AuditLog.builder()
                        .entityName("Product")
                        .entityId(1L)
                        .action("CREATE")
                        .performedBy("admin@erp.com")
                        .details("Created product ThinkPad X1 Laptop (SKU: TP-LAP-001) with initial stock 25")
                        .timestamp(now.minusHours(4))
                        .build(),
                com.erp.entity.AuditLog.builder()
                        .entityName("PurchaseOrder")
                        .entityId(1L)
                        .action("CREATE")
                        .performedBy("manager@erp.com")
                        .details("Created Purchase Order PO-2026-00001 from TechParts Global (Total: $11474.80)")
                        .timestamp(now.minusHours(3))
                        .build(),
                com.erp.entity.AuditLog.builder()
                        .entityName("SalesOrder")
                        .entityId(1L)
                        .action("CREATE")
                        .performedBy("admin@erp.com")
                        .details("Created Sales Order SO-2026-00001 for Acme Corporation (Total: $2598.00)")
                        .timestamp(now.minusHours(2))
                        .build(),
                com.erp.entity.AuditLog.builder()
                        .entityName("Product")
                        .entityId(3L)
                        .action("UPDATE")
                        .performedBy("manager@erp.com")
                        .details("Updated product Mechanical Keyboard (Stock: 8, Price: $89.50)")
                        .timestamp(now.minusHours(1))
                        .build()
        ));
        log.info("Seeded demo audit logs");
    }
}
