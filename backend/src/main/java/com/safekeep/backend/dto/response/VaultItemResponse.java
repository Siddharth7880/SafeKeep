package com.safekeep.backend.dto.response;

import com.safekeep.backend.enums.ContentType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response DTO for vault items.
 *
 * Zero-knowledge design:
 *   - In list view (GET /api/vault/items): only metadata fields are populated.
 *     ciphertext, encryptedDEK, etc. are null — no encrypted blobs in bulk listing.
 *   - In single-item view (GET /api/vault/items/{id}): all encrypted fields are returned.
 *     The browser uses these to perform local decryption — server never decrypts content.
 *
 * The server does NOT decrypt content in any user-facing path.
 * Content decryption happens exclusively in the browser using crypto/vault.js.
 */
@Data
@Builder
public class VaultItemResponse {

    // --- Metadata (always present) ---
    private UUID id;
    private String label;
    private ContentType contentType;
    private Boolean hasContent;       // true if text content exists (list view only)
    private Boolean hasFile;          // true if file attachment exists
    private String originalFileName;  // Unencrypted display name
    private List<RecipientResponse> recipients;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // --- Encrypted content blob (populated only on single-item GET) ---
    // These fields are returned to the browser for local decryption.
    // The server cannot decrypt them — only the user's vault password can.
    private String ciphertext;        // AES-256-GCM encrypted text content (Base64)
    private String iv;                // GCM IV for content (Base64)

    // --- DEK envelope (user key — populated on single-item GET) ---
    private String encryptedDEK;      // DEK wrapped with user master key (Base64)
    private String dekIv;             // IV used during DEK wrapping (Base64)
    private String salt;              // Argon2id salt for re-deriving user master key (Base64)

    // --- Encrypted file blob (populated on single-item GET if hasFile=true) ---
    private String fileCiphertext;    // AES-256-GCM encrypted file bytes (Base64)
    private String fileIv;            // GCM IV for file (Base64)
}
