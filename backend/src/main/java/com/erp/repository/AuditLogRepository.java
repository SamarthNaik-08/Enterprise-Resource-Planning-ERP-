package com.erp.repository;

import com.erp.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByEntityNameIgnoreCaseAndActionIgnoreCase(String entityName, String action, Pageable pageable);

    Page<AuditLog> findByEntityNameIgnoreCase(String entityName, Pageable pageable);

    Page<AuditLog> findByActionIgnoreCase(String action, Pageable pageable);
}
