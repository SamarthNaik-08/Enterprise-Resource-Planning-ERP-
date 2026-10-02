package com.erp.service;

import com.erp.dto.CatalogDtos.*;
import com.erp.entity.Product;
import com.erp.entity.Supplier;
import com.erp.exception.BadRequestException;
import com.erp.exception.NotFoundException;
import com.erp.repository.ProductRepository;
import com.erp.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        Page<Product> products = (query == null || query.isBlank())
                ? productRepository.findAll(pageable)
                : productRepository.search(query.trim(), pageable);
        return products.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return productRepository.findById(id).map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("Product", id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySkuIgnoreCase(request.sku())) {
            throw new BadRequestException("SKU already exists: " + request.sku());
        }
        Product product = Product.builder()
                .sku(request.sku().toUpperCase())
                .name(request.name())
                .category(request.category())
                .unitPrice(request.unitPrice())
                .quantityInStock(request.quantityInStock())
                .reorderLevel(request.reorderLevel())
                .supplier(resolveSupplier(request.supplierId()))
                .build();
        Product saved = productRepository.save(product);
        auditLogService.record("Product", saved.getId(), "CREATE",
                "Created product " + saved.getName() + " (SKU: " + saved.getSku() + ") with initial stock " + saved.getQuantityInStock());
        return toResponse(saved);
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product", id));
        productRepository.findBySkuIgnoreCase(request.sku())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new BadRequestException("SKU already exists: " + request.sku());
                });
        product.setSku(request.sku().toUpperCase());
        product.setName(request.name());
        product.setCategory(request.category());
        product.setUnitPrice(request.unitPrice());
        product.setQuantityInStock(request.quantityInStock());
        product.setReorderLevel(request.reorderLevel());
        product.setSupplier(resolveSupplier(request.supplierId()));
        Product saved = productRepository.save(product);
        auditLogService.record("Product", saved.getId(), "UPDATE",
                "Updated product " + saved.getName() + " (Stock: " + saved.getQuantityInStock() + ", Price: $" + saved.getUnitPrice() + ")");
        return toResponse(saved);
    }

    /** Products whose stock has fallen to/below their reorder level. */
    @Transactional(readOnly = true)
    public List<ProductResponse> lowStock() {
        return productRepository.findLowStock().stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product", id));
        productRepository.deleteById(id);
        auditLogService.record("Product", id, "DELETE",
                "Deleted product " + product.getName() + " (SKU: " + product.getSku() + ")");
    }

    private Supplier resolveSupplier(Long supplierId) {
        if (supplierId == null) {
            return null;
        }
        return supplierRepository.findById(supplierId)
                .orElseThrow(() -> new NotFoundException("Supplier", supplierId));
    }

    private ProductResponse toResponse(Product p) {
        return new ProductResponse(
                p.getId(), p.getSku(), p.getName(), p.getCategory(),
                p.getUnitPrice(), p.getQuantityInStock(), p.getReorderLevel(),
                p.getSupplier() != null ? p.getSupplier().getId() : null,
                p.getSupplier() != null ? p.getSupplier().getName() : null);
    }
}
