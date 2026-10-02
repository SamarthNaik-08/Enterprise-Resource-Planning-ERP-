package com.erp.service;

import com.erp.dto.CatalogDtos.ProductRequest;
import com.erp.entity.Product;
import com.erp.entity.Supplier;
import com.erp.exception.BadRequestException;
import com.erp.repository.ProductRepository;
import com.erp.repository.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Unit tests for catalog invariants (SKU uniqueness etc.). */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private SupplierRepository supplierRepository;
    @Mock
    private AuditLogService auditLogService;
    @InjectMocks
    private ProductService service;

    private ProductRequest request(String sku) {
        return new ProductRequest(sku, "Test product", "Cat",
                new BigDecimal("9.99"), 10, 5, null);
    }

    @Test
    void create_rejects_duplicateSku() {
        when(productRepository.existsBySkuIgnoreCase("DUP-1")).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.create(request("DUP-1")));

        assertTrue(ex.getMessage().contains("SKU already exists"));
    }

    @Test
    void update_rejects_skuOwnedByAnotherProduct() {
        Product other = Product.builder().id(99L).sku("DUP-1").name("Other").build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(new Product()));
        when(productRepository.findBySkuIgnoreCase("DUP-1")).thenReturn(Optional.of(other));

        assertThrows(BadRequestException.class, () -> service.update(1L, request("DUP-1")));
    }

    @Test
    void create_resolvesSupplier_andUppercasesSku() {
        when(productRepository.existsBySkuIgnoreCase("abc-1")).thenReturn(false);
        Supplier supplier = Supplier.builder().id(3L).name("TechParts").build();
        when(supplierRepository.findById(3L)).thenReturn(Optional.of(supplier));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        var res = service.create(new ProductRequest("abc-1", "Test", "Cat",
                new BigDecimal("9.99"), 10, 5, 3L));

        assertEquals("ABC-1", res.sku());          // normalised
        assertEquals("TechParts", res.supplierName()); // relation resolved
    }
}
