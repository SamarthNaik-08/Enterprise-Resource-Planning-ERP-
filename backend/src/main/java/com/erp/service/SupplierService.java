package com.erp.service;

import com.erp.dto.CatalogDtos.*;
import com.erp.entity.Supplier;
import com.erp.exception.NotFoundException;
import com.erp.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<SupplierResponse> list() {
        return supplierRepository.findAll().stream()
                .map(s -> new SupplierResponse(s.getId(), s.getName(), s.getEmail(),
                        s.getPhone(), s.getAddress()))
                .toList();
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        Supplier s = Supplier.builder()
                .name(request.name()).email(request.email())
                .phone(request.phone()).address(request.address())
                .build();
        Supplier saved = supplierRepository.save(s);
        auditLogService.record("Supplier", saved.getId(), "CREATE",
                "Created supplier " + saved.getName() + " (" + saved.getEmail() + ")");
        return new SupplierResponse(saved.getId(), saved.getName(), saved.getEmail(), saved.getPhone(), saved.getAddress());
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierRequest request) {
        Supplier s = supplierRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Supplier", id));
        s.setName(request.name());
        s.setEmail(request.email());
        s.setPhone(request.phone());
        s.setAddress(request.address());
        Supplier saved = supplierRepository.save(s);
        auditLogService.record("Supplier", saved.getId(), "UPDATE",
                "Updated supplier " + saved.getName());
        return new SupplierResponse(saved.getId(), saved.getName(), saved.getEmail(), saved.getPhone(), saved.getAddress());
    }

    @Transactional
    public void delete(Long id) {
        Supplier s = supplierRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Supplier", id));
        supplierRepository.delete(s);
        auditLogService.record("Supplier", id, "DELETE",
                "Deleted supplier " + s.getName());
    }
}
