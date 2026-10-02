package com.erp.exception;

import lombok.Getter;
import java.util.List;

@Getter
public class InsufficientStockException extends BadRequestException {

    public record InsufficientItem(
            Long productId,
            String sku,
            String productName,
            int available,
            int required
    ) {}

    private final List<InsufficientItem> items;

    public InsufficientStockException(List<InsufficientItem> items) {
        super("Insufficient stock: " + formatSummary(items));
        this.items = items;
    }

    private static String formatSummary(List<InsufficientItem> items) {
        return items.stream()
                .map(i -> i.productName() + " (available: " + i.available() + ", required: " + i.required() + ")")
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }
}
