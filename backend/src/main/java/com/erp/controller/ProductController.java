package com.erp.controller;

import com.erp.dto.CatalogDtos.*;
import com.erp.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public Page<ProductResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return productService.list(q, page, Math.min(size, 100));
    }

    /** Products whose stock has fallen to/below their reorder level. */
    @GetMapping("/low-stock")
    public Map<String, Object> lowStock() {
        List<ProductResponse> items = productService.lowStock();
        return Map.of("count", items.size(), "items", items);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return productService.get(id);
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity
                .created(URI.create("/api/products/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id,
                                  @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
