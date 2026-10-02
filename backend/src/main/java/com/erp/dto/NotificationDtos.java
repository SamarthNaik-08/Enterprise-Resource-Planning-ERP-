package com.erp.dto;

import java.time.LocalDateTime;

public final class NotificationDtos {

    public record NotificationItem(
            String id,
            String type,      // "LOW_STOCK", "PENDING_PURCHASE", "PENDING_SALES", "SYSTEM"
            String title,
            String message,
            String severity,  // "warning", "info", "error"
            String link,
            LocalDateTime timestamp
    ) {}

    public record NotificationSummary(
            int totalAlerts,
            int lowStockCount,
            int pendingPurchasesCount,
            int pendingSalesCount,
            java.util.List<NotificationItem> items
    ) {}
}
