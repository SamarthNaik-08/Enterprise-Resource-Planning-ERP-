package com.erp.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderDtos {

    public record OrderItemRequest(
            @NotNull Long productId,
            @NotNull @Min(1) Integer quantity,
            BigDecimal unitPrice) {
    }

    public record OrderRequest(
            @NotNull Long partyId,
            @NotEmpty @Valid List<OrderItemRequest> items) {
    }

    public record OrderItemResponse(
            Long productId,
            String sku,
            String productName,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {
    }

    public record OrderResponse(
            Long id,
            String orderNumber,
            Long partyId,
            String partyName,
            String status,
            LocalDateTime orderDate,
            BigDecimal total,
            List<OrderItemResponse> items) {
    }
}
