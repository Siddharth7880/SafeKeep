package com.safekeep.backend.repository;

import com.safekeep.backend.entity.AuditLog;
import com.safekeep.backend.enums.AuditEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<AuditLog> findAllByUserIdAndEventTypeOrderByCreatedAtDesc(UUID userId, AuditEventType eventType, Pageable pageable);

    long countByUserId(UUID userId);
}
