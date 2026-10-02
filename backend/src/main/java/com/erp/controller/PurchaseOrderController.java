package com.erp.controller;

import com.erp.dto.OrderDtos.*;
import com.erp.entity.OrderStatus;
import com.erp.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "orderDate") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        if (page == null && q == null && status == null && startDate == null && endDate == null) {
            return ResponseEntity.ok(purchaseOrderService.list());
        }
        int pageNum = page != null ? page : 0;
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(pageNum, Math.min(size, 100), Sort.by(dir, sort));
        return ResponseEntity.ok(purchaseOrderService.list(q, status, startDate, endDate, pageable));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return purchaseOrderService.get(id);
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest request) {
        OrderResponse created = purchaseOrderService.create(request);
        return ResponseEntity.created(URI.create("/api/purchase-orders/" + created.id()))
                .body(created);
    }

    /** DRAFT -> COMPLETED; adds all line items to stock. */
    @PostMapping("/{id}/receive")
    public OrderResponse receive(@PathVariable Long id) {
        return purchaseOrderService.receive(id);
    }

    /** DRAFT -> CANCELLED. */
    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id) {
        return purchaseOrderService.cancel(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        purchaseOrderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
