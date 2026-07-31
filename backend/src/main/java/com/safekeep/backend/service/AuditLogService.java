package com.safekeep.backend.service;

import com.safekeep.backend.entity.AuditLog;
import com.safekeep.backend.enums.AuditEventType;
import com.safekeep.backend.enums.UserStatus;
import com.safekeep.backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void log(UUID userId, AuditEventType eventType, String triggeredBy, String details) {
        log(userId, eventType, null, null, triggeredBy, details, null);
    }

    @Transactional
    public void log(UUID userId, AuditEventType eventType, UserStatus from, UserStatus to,
                    String triggeredBy, String details, String ipAddress) {
        AuditLog entry = AuditLog.builder()
                .userId(userId)
                .eventType(eventType)
                .previousStatus(from)
                .newStatus(to)
                .triggeredBy(triggeredBy)
                .details(details)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getUserLogs(UUID userId, Pageable pageable) {
        return auditLogRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
    }
}
