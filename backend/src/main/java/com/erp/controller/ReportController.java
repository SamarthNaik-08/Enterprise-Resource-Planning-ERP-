package com.erp.controller;

import com.erp.dto.ReportDtos.*;
import com.erp.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/inventory-valuation")
    public InventoryValuationReport getInventoryValuation() {
        return reportService.getInventoryValuation();
    }

    @GetMapping("/sales")
    public SalesSummaryReport getSalesSummary() {
        return reportService.getSalesSummary();
    }

    @GetMapping("/purchases")
    public PurchasesSummaryReport getPurchasesSummary() {
        return reportService.getPurchasesSummary();
    }
}
