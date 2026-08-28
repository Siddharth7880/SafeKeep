package com.safekeep.backend.dto.request;

import com.safekeep.backend.enums.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Request body for creating a new vault item.
 *
 * All encryption is performed client-side (browser) before this request is sent.
 * The server NEVER receives plaintext content or the user's vault password.
 *
 * Fields:
 *   ciphertext      - AES-256-GCM encrypted content (Base64). Null if file-only item.
 *   iv              - GCM IV used for content encryption (Base64)
 *   encryptedDEK    - DEK wrapped with user master key (Base64). Only user can unwrap.
 *   dekIv           - GCM IV used during DEK wrapping (Base64)
 *   salt            - Argon2id salt used to derive user master key (Base64, stored for re-derivation)
 *   rawDEK          - Raw DEK bytes (Base64). Used ONCE by the server to wrap with server key
 *                     for the automated release path. Never stored raw. Sent over HTTPS.
 *   fileCiphertext  - AES-256-GCM encrypted file bytes (Base64). Null if text-only item.
 *   fileIv          - GCM IV used for file encryption (Base64)
 *   originalFileName- Original file name (stored unencrypted for display purposes only)
 */
@Data
public class CreateVaultItemRequest {

    @NotBlank(message = "Label is required")
    @Size(max = 255)
    private String label;

    @NotNull(message = "Content type is required")
    private ContentType contentType;

    // --- Client-side encrypted content ---
    private String ciphertext;       // AES-256-GCM encrypted content (Base64) — null if file-only
    private String iv;               // GCM IV for content (Base64)

    // --- DEK envelope (user key) ---
    @NotBlank(message = "Encrypted DEK is required")
    private String encryptedDEK;     // DEK wrapped with user master key
    @NotBlank(message = "DEK IV is required")
    private String dekIv;            // IV used during DEK wrapping
    @NotBlank(message = "Salt is required")
    private String salt;             // Argon2id salt (stored for future key re-derivation)

    // --- Raw DEK for server-side release-path wrapping ---
    // Sent once over HTTPS. Server wraps this with server key then discards it.
    // The server never stores this raw — only the server-wrapped copy is persisted.
    @NotBlank(message = "Raw DEK is required for server-side release path")
    private String rawDEK;

    // --- File attachment (optional) ---
    private String fileCiphertext;   // AES-256-GCM encrypted file bytes (Base64) — null if text-only
    private String fileIv;           // GCM IV for file (Base64)
    private String originalFileName; // Stored unencrypted — display name only, not sensitive

    private List<UUID> recipientIds = new ArrayList<>();
}
