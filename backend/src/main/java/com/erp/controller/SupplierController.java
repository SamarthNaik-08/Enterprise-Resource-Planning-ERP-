package com.erp.controller;

import com.erp.dto.CatalogDtos.*;
import com.erp.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    public List<SupplierResponse> list() {
        return supplierService.list();
    }

    @PostMapping
    public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierRequest request) {
        SupplierResponse created = supplierService.create(request);
        return ResponseEntity.created(URI.create("/api/suppliers/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public SupplierResponse update(@PathVariable Long id,
                                   @Valid @RequestBody SupplierRequest request) {
        return supplierService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
