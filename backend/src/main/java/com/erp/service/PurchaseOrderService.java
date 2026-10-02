package com.erp.service;

import com.erp.dto.OrderDtos.*;
import com.erp.entity.*;
import com.erp.exception.BadRequestException;
import com.erp.exception.NotFoundException;
import com.erp.repository.ProductRepository;
import com.erp.repository.PurchaseOrderRepository;
import com.erp.repository.SupplierRepository;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Purchase (procure-to-pay) workflow:
 *   DRAFT  --receive-->  COMPLETED  (stock is added to the warehouse)
 *   DRAFT  --cancel-->   CANCELLED
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<OrderResponse> list() {
        return purchaseOrderRepository.findWithItemsByOrderByOrderDateDesc().stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> list(String q, OrderStatus status, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Specification<PurchaseOrder> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.trim().toLowerCase() + "%";
                Predicate orderNum = cb.like(cb.lower(root.get("orderNumber")), pattern);
                Predicate supplierName = cb.like(cb.lower(root.join("supplier", JoinType.LEFT).get("name")), pattern);
                predicates.add(cb.or(orderNum, supplierName));
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

        return purchaseOrderRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return purchaseOrderRepository.findWithItemsById(id).map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("Purchase order", id));
    }

    @Transactional
    public OrderResponse create(OrderRequest request) {
        Supplier supplier = supplierRepository.findById(request.partyId())
                .orElseThrow(() -> new NotFoundException("Supplier", request.partyId()));

        PurchaseOrder order = PurchaseOrder.builder()
                .orderNumber(nextOrderNumber())
                .supplier(supplier)
                .status(OrderStatus.DRAFT)
                .build();

        addItems(order, request.items());
        order.recalculateTotal();
        PurchaseOrder saved = purchaseOrderRepository.save(order);
        auditLogService.record("PurchaseOrder", saved.getId(), "CREATE",
                "Created Purchase Order " + saved.getOrderNumber() + " from " + supplier.getName() + " (Total: $" + saved.getTotal() + ")");
        return toResponse(saved);
    }

    /** Receives the order: increases stock for every line item. */
    @Transactional
    public OrderResponse receive(Long id) {
        PurchaseOrder order = getEntity(id);
        if (order.getStatus() != OrderStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT purchase orders can be received (current: "
                    + order.getStatus() + ")");
        }
        order.getItems().stream()
                .collect(Collectors.groupingBy(PurchaseOrderItem::getProduct, Collectors.summingInt(PurchaseOrderItem::getQuantity)))
                .forEach((product, qty) -> product.setQuantityInStock(product.getQuantityInStock() + qty));

        order.setStatus(OrderStatus.COMPLETED);
        auditLogService.record("PurchaseOrder", order.getId(), "STATUS_CHANGE",
                "Received Purchase Order " + order.getOrderNumber() + " and added inventory for " + order.getItems().size() + " items");
        return toResponse(order);
    }

    @Transactional
    public OrderResponse cancel(Long id) {
        PurchaseOrder order = getEntity(id);
        if (order.getStatus() != OrderStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT purchase orders can be cancelled");
        }
        order.setStatus(OrderStatus.CANCELLED);
        auditLogService.record("PurchaseOrder", order.getId(), "STATUS_CHANGE",
                "Cancelled Purchase Order " + order.getOrderNumber());
        return toResponse(order);
    }

    @Transactional
    public void delete(Long id) {
        PurchaseOrder order = getEntity(id);
        if (order.getStatus() != OrderStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT purchase orders can be deleted");
        }
        purchaseOrderRepository.delete(order);
        auditLogService.record("PurchaseOrder", id, "DELETE",
                "Deleted Purchase Order " + order.getOrderNumber());
    }

    // ---------------------------------------------------------------------

    private PurchaseOrder getEntity(Long id) {
        return purchaseOrderRepository.findWithItemsById(id)
                .orElseThrow(() -> new NotFoundException("Purchase order", id));
    }

    private void addItems(PurchaseOrder order, List<OrderItemRequest> itemRequests) {
        Set<Long> seenProductIds = new HashSet<>();
        for (OrderItemRequest ir : itemRequests) {
            if (!seenProductIds.add(ir.productId())) {
                throw new BadRequestException("Duplicate product ID " + ir.productId() + " in order items. Please combine into a single line item.");
            }
        }
        for (OrderItemRequest ir : itemRequests) {
            Product product = productRepository.findById(ir.productId())
                    .orElseThrow(() -> new NotFoundException("Product", ir.productId()));
            BigDecimal unitCost = ir.unitPrice() != null ? ir.unitPrice() : product.getUnitPrice();
            PurchaseOrderItem item = PurchaseOrderItem.builder()
                    .order(order)
                    .product(product)
                    .quantity(ir.quantity())
                    .unitCost(unitCost)
                    .build();
            order.getItems().add(item);
        }
    }

    private String nextOrderNumber() {
        long next = purchaseOrderRepository.nextSequence() + 1;
        return "PO-%d-%05d".formatted(Year.now().getValue(), next);
    }

    private OrderResponse toResponse(PurchaseOrder o) {
        List<OrderItemResponse> items = o.getItems().stream()
                .map(i -> new OrderItemResponse(
                        i.getProduct().getId(), i.getProduct().getSku(), i.getProduct().getName(),
                        i.getQuantity(), i.getUnitCost(),
                        i.getUnitCost().multiply(BigDecimal.valueOf(i.getQuantity()))))
                .toList();
        return new OrderResponse(o.getId(), o.getOrderNumber(), o.getSupplier().getId(),
                o.getSupplier().getName(), o.getStatus().name(), o.getOrderDate(),
                o.getTotal(), items);
    }
}
