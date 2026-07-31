package com.safekeep.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "release_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReleaseToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "token", unique = true, nullable = false, length = 512)
    private String token;  // HMAC-SHA256 signed token

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "vault_item_id", nullable = false)
    private UUID vaultItemId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "accessed_at")
    private LocalDateTime accessedAt;

    @Column(name = "is_used")
    @Builder.Default
    private Boolean isUsed = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
