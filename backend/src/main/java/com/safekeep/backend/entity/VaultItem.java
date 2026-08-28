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

/**
 * Vault item entity — stores only encrypted blobs.
 *
 * Zero-knowledge invariant:
 *   The server NEVER stores plaintext content. All content fields hold
 *   AES-256-GCM ciphertext that can only be decrypted with either:
 *   (a) The user's vault password  → user-facing vault access
 *   (b) The server secret          → automated release path (ContentReleaseJob)
 *
 * Naming convention (matches client/crypto/vault.js):
 *   encryptedContent  = AES-256-GCM ciphertext of text content (Base64)
 *   iv                = GCM IV for content encryption (Base64)
 *   encryptedDek      = DEK wrapped with user master key (Base64)
 *   dekIv             = IV for DEK wrapping (Base64)
 *   dekSalt           = Argon2id salt for deriving user master key (Base64)
 *   encryptedDekServer = DEK wrapped with server master key (Base64)
 *   dekIvServer       = IV for server DEK wrapping (Base64)
 *   dekSaltServer     = Argon2id salt for deriving server master key (Base64)
 *   fileCiphertext    = AES-256-GCM ciphertext of file attachment (Base64, stored in DB)
 *   fileIv            = GCM IV for file encryption (Base64)
 */
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

    // AES-256-GCM encrypted text content (Base64). Set by browser before upload.
    @Column(name = "encrypted_content", columnDefinition = "TEXT")
    private String encryptedContent;

    // GCM IV for content encryption (Base64)
    @Column(name = "iv", length = 64)
    private String iv;

    // DEK wrapped with user master key (Base64) — only user can unwrap
    @Column(name = "encrypted_dek", nullable = false, length = 512)
    private String encryptedDek;

    // IV used during DEK wrapping (Base64)
    @Column(name = "dek_iv", nullable = false, length = 64)
    private String dekIv;

    // Argon2id salt used to derive the user master key (Base64)
    // Stored so the browser can re-derive the same key on future decryption
    @Column(name = "dek_salt", length = 64)
    private String dekSalt;

    // Server-side DEK copy: encrypted with Argon2id(serverSecret, dekSaltServer)
    // Used ONLY by ContentReleaseJob — allows automated release without user password
    @Column(name = "encrypted_dek_server", length = 512)
    private String encryptedDekServer;

    @Column(name = "dek_iv_server", length = 64)
    private String dekIvServer;

    @Column(name = "dek_salt_server", length = 64)
    private String dekSaltServer;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    // ---- File attachment (Option B: stored in DB as Base64 TEXT) ----
    // The browser encrypts the file with AES-256-GCM before upload.
    // The server stores only ciphertext — never the plaintext file.

    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    // New DB-backed file storage (Phase 2): AES-256-GCM encrypted file bytes, Base64-encoded
    @Column(name = "file_ciphertext", columnDefinition = "TEXT")
    private String fileCiphertext;

    @Column(name = "file_iv_b64", length = 64)
    private String fileIvB64;

    // Legacy filesystem path — kept for backward compatibility with pre-Phase-2 items.
    // New items use fileCiphertext/fileIvB64 instead.
    @Column(name = "encrypted_file_path", length = 255)
    private String encryptedFilePath;

    // Legacy file IV for filesystem-stored items
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

    /** Returns true if this item has a DB-backed encrypted file (Phase 2 item). */
    public boolean hasDbFile() {
        return fileCiphertext != null && !fileCiphertext.isBlank();
    }

    /** Returns true if this item has a legacy filesystem-backed file (pre-Phase-2 item). */
    public boolean hasLegacyFile() {
        return encryptedFilePath != null && !encryptedFilePath.isBlank();
    }
}
