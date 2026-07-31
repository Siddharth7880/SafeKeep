package com.safekeep.backend.entity;

import com.safekeep.backend.enums.ContentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "vault_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VaultItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "label", nullable = false, length = 255)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private ContentType contentType;

    // AES-256-GCM encrypted content (Base64 encoded)
    @Column(name = "encrypted_content", columnDefinition = "TEXT")
    private String encryptedContent;

    // Envelope encryption: DEK encrypted with master key (Base64 encoded)
    @Column(name = "encrypted_dek", nullable = false, length = 512)
    private String encryptedDek;

    // GCM Initialization Vector (Base64 encoded)
    @Column(name = "iv", nullable = false, length = 64)
    private String iv;

    // DEK IV (for envelope layer)
    @Column(name = "dek_iv", nullable = false, length = 64)
    private String dekIv;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    @Column(name = "encrypted_file_path", length = 255)
    private String encryptedFilePath;

    @Column(name = "file_iv", length = 64)
    private String fileIv;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToMany
    @JoinTable(
        name = "vault_item_recipients",
        joinColumns = @JoinColumn(name = "vault_item_id"),
        inverseJoinColumns = @JoinColumn(name = "recipient_id")
    )
    @Builder.Default
    private List<Recipient> recipients = new ArrayList<>();
}
