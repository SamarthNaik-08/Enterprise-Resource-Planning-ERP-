package com.erp.service;

import com.erp.dto.CatalogDtos.*;
import com.erp.entity.Customer;
import com.erp.exception.NotFoundException;
import com.erp.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<CustomerResponse> list() {
        return customerRepository.findAll().stream()
                .map(c -> new CustomerResponse(c.getId(), c.getName(), c.getEmail(),
                        c.getPhone(), c.getAddress()))
                .toList();
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        Customer c = Customer.builder()
                .name(request.name()).email(request.email())
                .phone(request.phone()).address(request.address())
                .build();
        Customer saved = customerRepository.save(c);
        auditLogService.record("Customer", saved.getId(), "CREATE",
                "Created customer " + saved.getName() + " (" + saved.getEmail() + ")");
        return new CustomerResponse(saved.getId(), saved.getName(), saved.getEmail(), saved.getPhone(), saved.getAddress());
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer c = customerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Customer", id));
        c.setName(request.name());
        c.setEmail(request.email());
        c.setPhone(request.phone());
        c.setAddress(request.address());
        Customer saved = customerRepository.save(c);
        auditLogService.record("Customer", saved.getId(), "UPDATE",
                "Updated customer " + saved.getName());
        return new CustomerResponse(saved.getId(), saved.getName(), saved.getEmail(), saved.getPhone(), saved.getAddress());
    }

    @Transactional
    public void delete(Long id) {
        Customer c = customerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Customer", id));
        customerRepository.delete(c);
        auditLogService.record("Customer", id, "DELETE",
                "Deleted customer " + c.getName());
    }
}
