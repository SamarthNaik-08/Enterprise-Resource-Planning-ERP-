package com.erp.controller;

import com.erp.dto.OrderDtos.*;
import com.erp.entity.OrderStatus;
import com.erp.service.SalesOrderService;
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
@RequestMapping("/api/sales-orders")
@RequiredArgsConstructor
public class SalesOrderController {

    private final SalesOrderService salesOrderService;

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
            return ResponseEntity.ok(salesOrderService.list());
        }
        int pageNum = page != null ? page : 0;
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(pageNum, Math.min(size, 100), Sort.by(dir, sort));
        return ResponseEntity.ok(salesOrderService.list(q, status, startDate, endDate, pageable));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return salesOrderService.get(id);
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody OrderRequest request) {
        OrderResponse created = salesOrderService.create(request);
        return ResponseEntity.created(URI.create("/api/sales-orders/" + created.id()))
                .body(created);
    }

    /** DRAFT -> COMPLETED; validates availability and deducts stock. */
    @PostMapping("/{id}/complete")
    public OrderResponse complete(@PathVariable Long id) {
        return salesOrderService.complete(id);
    }

    /** DRAFT -> CANCELLED. */
    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id) {
        return salesOrderService.cancel(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        salesOrderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
