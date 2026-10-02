package com.erp.service;

import com.erp.entity.AuditLog;
import com.erp.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String entityName, Long entityId, String action, String details) {
        String username = getCurrentUser();
        AuditLog entry = AuditLog.builder()
                .entityName(entityName)
                .entityId(entityId)
                .action(action)
                .performedBy(username)
                .details(details != null && details.length() > 950 ? details.substring(0, 950) + "..." : details)
                .timestamp(LocalDateTime.now())
                .build();
        auditLogRepository.save(entry);
        log.info("AUDIT [{}] {} #{} by {}: {}", action, entityName, entityId, username, details);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> list(String entityName, String action, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        String cleanEntity = (entityName != null && !entityName.isBlank()) ? entityName.trim() : null;
        String cleanAction = (action != null && !action.isBlank()) ? action.trim() : null;
        return auditLogRepository.filter(cleanEntity, cleanAction, pageable);
    }

    private String getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "system";
    }
}
