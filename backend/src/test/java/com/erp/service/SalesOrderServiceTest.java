package com.erp.service;

import com.erp.dto.OrderDtos.OrderItemRequest;
import com.erp.dto.OrderDtos.OrderRequest;
import com.erp.dto.OrderDtos.OrderResponse;
import com.erp.entity.*;
import com.erp.exception.BadRequestException;
import com.erp.repository.CustomerRepository;
import com.erp.repository.ProductRepository;
import com.erp.repository.SalesOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for the order-to-cash business rules.
 * No Spring context, no database — millisecond feedback.
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderServiceTest {

    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private AuditLogService auditLogService;
    @InjectMocks
    private SalesOrderService service;

    // ---------- helpers ----------

    private Product product(long id, String name, int stock) {
        return Product.builder()
                .id(id).sku("SKU-" + id).name(name)
                .unitPrice(new BigDecimal("10.00"))
                .quantityInStock(stock).reorderLevel(5)
                .build();
    }

    private SalesOrder draftOrder(Product p, int qty) {
        SalesOrder order = SalesOrder.builder()
                .id(1L).orderNumber("SO-2026-00001").status(OrderStatus.DRAFT)
                .customer(Customer.builder().id(1L).name("Acme").build())
                .build();
        order.getItems().add(SalesOrderItem.builder()
                .order(order).product(p).quantity(qty).unitPrice(p.getUnitPrice()).build());
        return order;
    }

    // ---------- complete() ----------

    @Test
    void complete_deductsStock_andMarksCompleted() {
        Product keyboard = product(1L, "Keyboard", 10);
        SalesOrder order = draftOrder(keyboard, 4);
        when(salesOrderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        OrderResponse res = service.complete(1L);

        assertEquals("COMPLETED", res.status());
        assertEquals(6, keyboard.getQuantityInStock());
    }

    @Test
    void complete_rejects_insufficientStock_withoutTouchingInventory() {
        Product keyboard = product(1L, "Keyboard", 8);
        SalesOrder order = draftOrder(keyboard, 999);
        when(salesOrderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.complete(1L));

        assertTrue(ex.getMessage().contains("Insufficient stock"));
        assertEquals(8, keyboard.getQuantityInStock()); // nothing deducted
    }

    @Test
    void complete_rejects_nonDraftOrder() {
        SalesOrder completed = draftOrder(product(1L, "K", 10), 1);
        completed.setStatus(OrderStatus.COMPLETED);
        when(salesOrderRepository.findWithItemsById(1L)).thenReturn(Optional.of(completed));

        assertThrows(BadRequestException.class, () -> service.complete(1L));
    }

    // ---------- cancel() ----------

    @Test
    void cancel_worksFromDraft() {
        SalesOrder order = draftOrder(product(1L, "K", 10), 1);
        when(salesOrderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        assertEquals("CANCELLED", service.cancel(1L).status());
    }

    @Test
    void cancel_rejects_completedOrder() {
        SalesOrder done = draftOrder(product(1L, "K", 10), 1);
        done.setStatus(OrderStatus.COMPLETED);
        when(salesOrderRepository.findWithItemsById(1L)).thenReturn(Optional.of(done));

        assertThrows(BadRequestException.class, () -> service.cancel(1L));
    }

    // ---------- create() ----------

    @Test
    void create_buildsDraftWithNumberAndCalculatedTotal() {
        when(customerRepository.findById(5L))
                .thenReturn(Optional.of(Customer.builder().id(5L).name("Acme").build()));
        when(productRepository.findById(7L)).thenReturn(Optional.of(product(7L, "Mouse", 100)));
        when(salesOrderRepository.nextSequence()).thenReturn(0L);
        when(salesOrderRepository.save(any(SalesOrder.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        OrderRequest req = new OrderRequest(5L, List.of(new OrderItemRequest(7L, 3, null)));
        OrderResponse res = service.create(req);

        assertTrue(res.orderNumber().startsWith("SO-2026-"));
        assertEquals("DRAFT", res.status());
        assertEquals(0, new BigDecimal("30.00").compareTo(res.total())); // 3 x 10.00
    }

    @Test
    void create_rejectsDuplicateProductLineItems() {
        OrderRequest req = new OrderRequest(5L, List.of(
                new OrderItemRequest(7L, 2, null),
                new OrderItemRequest(7L, 3, null)
        ));
        when(customerRepository.findById(5L))
                .thenReturn(Optional.of(Customer.builder().id(5L).name("Acme").build()));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("Duplicate product ID 7"));
    }

    @Test
    void complete_aggregatesDuplicateProductLineItems_andDetectsShortage() {
        Product keyboard = product(1L, "Keyboard", 8);
        SalesOrder order = SalesOrder.builder()
                .id(1L).orderNumber("SO-2026-00001").status(OrderStatus.DRAFT)
                .customer(Customer.builder().id(1L).name("Acme").build())
                .build();
        // Line 1: qty 5, Line 2: qty 5 -> Total 10 required, but stock is 8
        order.getItems().add(SalesOrderItem.builder()
                .order(order).product(keyboard).quantity(5).unitPrice(keyboard.getUnitPrice()).build());
        order.getItems().add(SalesOrderItem.builder()
                .order(order).product(keyboard).quantity(5).unitPrice(keyboard.getUnitPrice()).build());

        when(salesOrderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        com.erp.exception.InsufficientStockException ex = assertThrows(
                com.erp.exception.InsufficientStockException.class,
                () -> service.complete(1L));

        assertEquals(1, ex.getItems().size());
        assertEquals(10, ex.getItems().get(0).required());
        assertEquals(8, ex.getItems().get(0).available());
        assertEquals(8, keyboard.getQuantityInStock()); // Untouched
    }

    @Test
    void complete_aggregatesDuplicateProductLineItems_andDeductsCorrectly() {
        Product keyboard = product(1L, "Keyboard", 15);
        SalesOrder order = SalesOrder.builder()
                .id(1L).orderNumber("SO-2026-00001").status(OrderStatus.DRAFT)
                .customer(Customer.builder().id(1L).name("Acme").build())
                .build();
        // Line 1: qty 5, Line 2: qty 5 -> Total 10 required, stock 15 -> resulting stock 5
        order.getItems().add(SalesOrderItem.builder()
                .order(order).product(keyboard).quantity(5).unitPrice(keyboard.getUnitPrice()).build());
        order.getItems().add(SalesOrderItem.builder()
                .order(order).product(keyboard).quantity(5).unitPrice(keyboard.getUnitPrice()).build());

        when(salesOrderRepository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        OrderResponse res = service.complete(1L);

        assertEquals("COMPLETED", res.status());
        assertEquals(5, keyboard.getQuantityInStock());
    }
}
