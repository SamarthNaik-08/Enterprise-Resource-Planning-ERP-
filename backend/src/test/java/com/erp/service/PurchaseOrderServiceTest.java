package com.erp.service;

import com.erp.entity.*;
import com.erp.exception.BadRequestException;
import com.erp.repository.ProductRepository;
import com.erp.repository.PurchaseOrderRepository;
import com.erp.repository.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/** Unit tests for the procure-to-pay rules. */
@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private AuditLogService auditLogService;
    @InjectMocks
    private PurchaseOrderService service;

    private PurchaseOrder draftPo(Product p, int qty) {
        PurchaseOrder po = PurchaseOrder.builder()
                .id(1L).orderNumber("PO-2026-00001").status(OrderStatus.DRAFT)
                .supplier(Supplier.builder().id(1L).name("TechParts").build())
                .build();
        po.getItems().add(PurchaseOrderItem.builder()
                .order(po).product(p).quantity(qty).unitCost(p.getUnitPrice()).build());
        return po;
    }

    @Test
    void receive_addsStock_andMarksCompleted() {
        Product keyboard = Product.builder()
                .id(3L).sku("K").name("Keyboard")
                .unitPrice(new BigDecimal("10.00"))
                .quantityInStock(8).reorderLevel(5).build();
        PurchaseOrder po = draftPo(keyboard, 50);
        when(purchaseOrderRepository.findWithItemsById(1L)).thenReturn(Optional.of(po));

        String status = service.receive(1L).status();

        assertEquals("COMPLETED", status);
        assertEquals(58, keyboard.getQuantityInStock()); // 8 + 50
    }

    @Test
    void receive_rejects_alreadyReceivedOrder() {
        PurchaseOrder po = draftPo(Product.builder().id(1L).sku("A").name("A")
                .unitPrice(BigDecimal.ONE).quantityInStock(1).reorderLevel(1).build(), 1);
        po.setStatus(OrderStatus.COMPLETED);
        when(purchaseOrderRepository.findWithItemsById(1L)).thenReturn(Optional.of(po));

        assertThrows(BadRequestException.class, () -> service.receive(1L));
    }

    @Test
    void cancel_rejects_completedOrder() {
        PurchaseOrder po = draftPo(Product.builder().id(1L).sku("A").name("A")
                .unitPrice(BigDecimal.ONE).quantityInStock(1).reorderLevel(1).build(), 1);
        po.setStatus(OrderStatus.CANCELLED);
        when(purchaseOrderRepository.findWithItemsById(1L)).thenReturn(Optional.of(po));

        assertThrows(BadRequestException.class, () -> service.cancel(1L));
    }

    @Test
    void receive_aggregatesDuplicateProductLineItems_andIncreasesStock() {
        Product keyboard = Product.builder()
                .id(3L).sku("K").name("Keyboard")
                .unitPrice(new BigDecimal("10.00"))
                .quantityInStock(10).reorderLevel(5).build();
        PurchaseOrder po = PurchaseOrder.builder()
                .id(1L).orderNumber("PO-2026-00001").status(OrderStatus.DRAFT)
                .supplier(Supplier.builder().id(1L).name("TechParts").build())
                .build();
        // Line 1: qty 20, Line 2: qty 30 -> total 50 added to stock (10 + 50 = 60)
        po.getItems().add(PurchaseOrderItem.builder()
                .order(po).product(keyboard).quantity(20).unitCost(keyboard.getUnitPrice()).build());
        po.getItems().add(PurchaseOrderItem.builder()
                .order(po).product(keyboard).quantity(30).unitCost(keyboard.getUnitPrice()).build());

        when(purchaseOrderRepository.findWithItemsById(1L)).thenReturn(Optional.of(po));

        String status = service.receive(1L).status();

        assertEquals("COMPLETED", status);
        assertEquals(60, keyboard.getQuantityInStock());
    }

    @Test
    void create_rejectsDuplicateProductLineItems() {
        com.erp.dto.OrderDtos.OrderRequest req = new com.erp.dto.OrderDtos.OrderRequest(1L, java.util.List.of(
                new com.erp.dto.OrderDtos.OrderItemRequest(3L, 10, null),
                new com.erp.dto.OrderDtos.OrderItemRequest(3L, 20, null)
        ));
        when(supplierRepository.findById(1L))
                .thenReturn(Optional.of(Supplier.builder().id(1L).name("TechParts").build()));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("Duplicate product ID 3"));
    }
}
