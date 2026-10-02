package com.erp.repository;

import com.erp.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:entityName IS NULL OR LOWER(a.entityName) = LOWER(:entityName)) AND " +
           "(:action IS NULL OR LOWER(a.action) = LOWER(:action)) " +
           "ORDER BY a.timestamp DESC")
    Page<AuditLog> filter(
            @Param("entityName") String entityName,
            @Param("action") String action,
            Pageable pageable);
}
