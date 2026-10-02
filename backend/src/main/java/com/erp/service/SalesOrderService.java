package com.erp.service;

import com.erp.dto.OrderDtos.*;
import com.erp.entity.*;
import com.erp.exception.BadRequestException;
import com.erp.exception.InsufficientStockException;
import com.erp.exception.NotFoundException;
import com.erp.repository.CustomerRepository;
import com.erp.repository.ProductRepository;
import com.erp.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sales (order-to-cash) workflow:
 *   DRAFT  --complete--> COMPLETED  (stock is validated and deducted)
 *   DRAFT  --cancel-->   CANCELLED
 */
@Service
@RequiredArgsConstructor
public class SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<OrderResponse> list() {
        return salesOrderRepository.findWithItemsByOrderByOrderDateDesc().stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> list(String q, OrderStatus status, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Specification<SalesOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.trim().toLowerCase() + "%";
                Predicate orderNum = cb.like(cb.lower(root.get("orderNumber")), pattern);
                Predicate custName = cb.like(cb.lower(root.join("customer", JoinType.LEFT).get("name")), pattern);
                predicates.add(cb.or(orderNum, custName));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("orderDate"), startDate.atStartOfDay()));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("orderDate"), endDate.atTime(23, 59, 59)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return salesOrderRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return salesOrderRepository.findWithItemsById(id).map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("Sales order", id));
    }

    @Transactional
    public OrderResponse create(OrderRequest request) {
        Customer customer = customerRepository.findById(request.partyId())
                .orElseThrow(() -> new NotFoundException("Customer", request.partyId()));

        SalesOrder order = SalesOrder.builder()
                .orderNumber(nextOrderNumber())
                .customer(customer)
                .status(OrderStatus.DRAFT)
                .build();

        addItems(order, request.items());
        order.recalculateTotal();
        SalesOrder saved = salesOrderRepository.save(order);
        auditLogService.record("SalesOrder", saved.getId(), "CREATE",
                "Created Sales Order " + saved.getOrderNumber() + " for " + customer.getName() + " (Total: $" + saved.getTotal() + ")");
        return toResponse(saved);
    }

    /** Fulfils the order: verifies stock availability, then deducts inventory. */
    @Transactional
    public OrderResponse complete(Long id) {
        SalesOrder order = getEntity(id);
        if (order.getStatus() != OrderStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT sales orders can be completed (current: "
                    + order.getStatus() + ")");
        }

        // Aggregate total required quantities per product to prevent negative stock if an order has duplicate products
        Map<Product, Integer> totalRequiredByProduct = order.getItems().stream()
                .collect(Collectors.groupingBy(SalesOrderItem::getProduct, Collectors.summingInt(SalesOrderItem::getQuantity)));

        List<InsufficientStockException.InsufficientItem> insufficient = totalRequiredByProduct.entrySet().stream()
                .filter(e -> e.getKey().getQuantityInStock() < e.getValue())
                .map(e -> new InsufficientStockException.InsufficientItem(
                        e.getKey().getId(),
                        e.getKey().getSku(),
                        e.getKey().getName(),
                        e.getKey().getQuantityInStock(),
                        e.getValue()))
                .toList();
        if (!insufficient.isEmpty()) {
            throw new InsufficientStockException(insufficient);
        }

        totalRequiredByProduct.forEach((product, requiredQty) -> {
            product.setQuantityInStock(product.getQuantityInStock() - requiredQty);
        });
        order.setStatus(OrderStatus.COMPLETED);
        auditLogService.record("SalesOrder", order.getId(), "STATUS_CHANGE",
                "Completed Sales Order " + order.getOrderNumber() + " and deducted inventory for " + order.getItems().size() + " items");
        return toResponse(order);
    }

    @Transactional
    public OrderResponse cancel(Long id) {
        SalesOrder order = getEntity(id);
        if (order.getStatus() != OrderStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT sales orders can be cancelled");
        }
        order.setStatus(OrderStatus.CANCELLED);
        auditLogService.record("SalesOrder", order.getId(), "STATUS_CHANGE",
                "Cancelled Sales Order " + order.getOrderNumber());
        return toResponse(order);
    }

    @Transactional
    public void delete(Long id) {
        SalesOrder order = getEntity(id);
        if (order.getStatus() != OrderStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT sales orders can be deleted");
        }
        salesOrderRepository.delete(order);
        auditLogService.record("SalesOrder", id, "DELETE",
                "Deleted Sales Order " + order.getOrderNumber());
    }

    // ---------------------------------------------------------------------

    private SalesOrder getEntity(Long id) {
        return salesOrderRepository.findWithItemsById(id)
                .orElseThrow(() -> new NotFoundException("Sales order", id));
    }

    private void addItems(SalesOrder order, List<OrderItemRequest> itemRequests) {
        Set<Long> seenProductIds = new HashSet<>();
        for (OrderItemRequest ir : itemRequests) {
            if (!seenProductIds.add(ir.productId())) {
                throw new BadRequestException("Duplicate product ID " + ir.productId() + " in order items. Please combine into a single line item.");
            }
        }
        for (OrderItemRequest ir : itemRequests) {
            Product product = productRepository.findById(ir.productId())
                    .orElseThrow(() -> new NotFoundException("Product", ir.productId()));
            BigDecimal unitPrice = ir.unitPrice() != null ? ir.unitPrice() : product.getUnitPrice();
            SalesOrderItem item = SalesOrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(ir.quantity())
                    .unitPrice(unitPrice)
                    .build();
            order.getItems().add(item);
        }
    }

    private String nextOrderNumber() {
        long next = salesOrderRepository.nextSequence() + 1;
        return "SO-%d-%05d".formatted(Year.now().getValue(), next);
    }

    private OrderResponse toResponse(SalesOrder o) {
        List<OrderItemResponse> items = o.getItems().stream()
                .map(i -> new OrderItemResponse(
                        i.getProduct().getId(), i.getProduct().getSku(), i.getProduct().getName(),
                        i.getQuantity(), i.getUnitPrice(),
                        i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()))))
                .toList();
        return new OrderResponse(o.getId(), o.getOrderNumber(), o.getCustomer().getId(),
                o.getCustomer().getName(), o.getStatus().name(), o.getOrderDate(),
                o.getTotal(), items);
    }
}
