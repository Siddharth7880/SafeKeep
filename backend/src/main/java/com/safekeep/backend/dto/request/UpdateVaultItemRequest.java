package com.safekeep.backend.dto.request;

import com.safekeep.backend.enums.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Request body for updating an existing vault item.
 *
 * The existing DEK is re-used — only the content ciphertext changes (re-encrypted
 * in the browser with the same DEK). The DEK envelope (encryptedDEK, dekIv, salt)
 * are passed back unchanged so the server can update them if needed.
 *
 * rawDEK is included so the server can re-wrap the server-side release DEK copy
 * after an update — ensuring the release path stays valid.
 */
@Data
public class UpdateVaultItemRequest {

    @NotBlank(message = "Label is required")
    private String label;

    @NotNull(message = "Content Type is required")
    private ContentType contentType;

    // --- Updated encrypted content ---
    private String ciphertext;       // New ciphertext (may be same as before if content unchanged)
    private String iv;               // New GCM IV for content

    // --- DEK envelope (unchanged from create, passed back) ---
    @NotBlank(message = "Encrypted DEK is required")
    private String encryptedDEK;
    @NotBlank(message = "DEK IV is required")
    private String dekIv;
    @NotBlank(message = "Salt is required")
    private String salt;

    // --- Raw DEK for server release-path re-wrap ---
    @NotBlank(message = "Raw DEK is required")
    private String rawDEK;

    private List<UUID> recipientIds;
}
