package com.erp.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public final class CatalogDtos {

    // ---------- Supplier ----------
    public record SupplierRequest(
            @NotBlank @Size(max = 120) String name,
            @Email String email,
            @Size(max = 40) String phone,
            @Size(max = 255) String address) {
    }

    public record SupplierResponse(
            Long id, String name, String email, String phone, String address) {
    }

    // ---------- Customer ----------
    public record CustomerRequest(
            @NotBlank @Size(max = 120) String name,
            @Email String email,
            @Size(max = 40) String phone,
            @Size(max = 255) String address) {
    }

    public record CustomerResponse(
            Long id, String name, String email, String phone, String address) {
    }

    // ---------- Product ----------
    public record ProductRequest(
            @NotBlank @Size(max = 40) String sku,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 80) String category,
            @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
            @NotNull @Min(0) Integer quantityInStock,
            @NotNull @Min(0) Integer reorderLevel,
            Long supplierId) {
    }

    public record ProductResponse(
            Long id,
            String sku,
            String name,
            String category,
            BigDecimal unitPrice,
            Integer quantityInStock,
            Integer reorderLevel,
            Long supplierId,
            String supplierName) {
    }
}
