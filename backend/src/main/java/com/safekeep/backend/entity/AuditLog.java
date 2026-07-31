package com.safekeep.backend.entity;

import com.safekeep.backend.enums.AuditEventType;
import com.safekeep.backend.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Immutable  // Never updated — write-once audit trail
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private AuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private UserStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status")
    private UserStatus newStatus;

    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;  // "USER", "SCHEDULER", "SYSTEM"

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "details", length = 500)
    private String details;  // Non-sensitive context, never contains vault content

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
