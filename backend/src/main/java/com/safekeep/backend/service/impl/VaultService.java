package com.safekeep.backend.service.impl;

import com.safekeep.backend.dto.request.CreateVaultItemRequest;
import com.safekeep.backend.dto.request.UpdateVaultItemRequest;
import com.safekeep.backend.dto.response.RecipientResponse;
import com.safekeep.backend.dto.response.VaultItemResponse;
import com.safekeep.backend.entity.Recipient;
import com.safekeep.backend.entity.User;
import com.safekeep.backend.entity.VaultItem;
import com.safekeep.backend.enums.AuditEventType;
import com.safekeep.backend.exception.ResourceNotFoundException;
import com.safekeep.backend.repository.RecipientRepository;
import com.safekeep.backend.repository.UserRepository;
import com.safekeep.backend.repository.VaultItemRepository;
import com.safekeep.backend.service.AuditLogService;
import com.safekeep.backend.util.AesEncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Vault service — Phase 2 zero-knowledge implementation.
 *
 * Zero-knowledge invariant:
 *   The server NEVER performs decryption in user-facing paths.
 *   All user-path operations (create, get, update, delete) treat content
 *   as opaque encrypted blobs received from or returned to the browser.
 *
 * The ONLY server-side decryption occurs in the release path (ContentReleaseJob),
 * which uses the server master key — never the user's password.
 *
 * Key architecture:
 *   User path:    Browser derives key → encrypts content/DEK → sends ciphertext only
 *   Release path: Server derives key from serverSecret → decrypts server DEK copy → decrypts content
 *
 * rawDEK handling:
 *   The browser sends the raw DEK (plaintext, Base64) exactly ONCE at create/update time
 *   over HTTPS. The server wraps it with the server master key, stores the wrapped copy,
 *   and discards the raw bytes immediately after wrapping. It is NEVER stored raw.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VaultService {

    private final VaultItemRepository vaultItemRepository;
    private final UserRepository userRepository;
    private final RecipientRepository recipientRepository;
    private final AesEncryptionUtil aesEncryptionUtil;
    private final AuditLogService auditLogService;

    @Value("${app.release.token-secret}")
    private String serverSecret;

    // ==================== User-Facing Paths (Zero-Knowledge) ====================

    /**
     * Stores a new vault item from an already-encrypted payload.
     *
     * The browser has already performed:
     *   1. Argon2id key derivation (password + salt → master key)
     *   2. Random DEK generation
     *   3. AES-256-GCM content/file encryption with the DEK
     *   4. AES-256-GCM DEK wrapping with the master key
     *
     * This method:
     *   1. Stores the encrypted blobs as-is (no decryption, no inspection of content)
     *   2. Wraps the rawDEK with the server master key for the release path
     *   3. Discards the rawDEK immediately after wrapping
     */
    @Transactional
    public VaultItemResponse createVaultItem(UUID userId, CreateVaultItemRequest request) {
        User user = getUserOrThrow(userId);

        validateAtLeastOnePayload(request.getCiphertext(), request.getFileCiphertext());

        try {
            // Wrap rawDEK with server key for release path — discard rawDEK immediately
            byte[] rawDekBytes = Base64.getDecoder().decode(request.getRawDEK());
            SecretKey rawDek = new SecretKeySpec(rawDekBytes, "AES");
            java.util.Arrays.fill(rawDekBytes, (byte) 0); // zero from JVM heap immediately

            byte[] serverSalt = aesEncryptionUtil.generateSalt();
            byte[] serverMasterKeyBytes = deriveServerMasterKey(serverSalt);
            AesEncryptionUtil.EncryptionResult serverDekEncrypted =
                    aesEncryptionUtil.encryptDek(rawDek, serverMasterKeyBytes);
            java.util.Arrays.fill(serverMasterKeyBytes, (byte) 0);

            List<Recipient> recipients = resolveRecipients(request.getRecipientIds(), userId);

            VaultItem item = VaultItem.builder()
                    .user(user)
                    .label(request.getLabel())
                    .contentType(request.getContentType())
                    // Content blob (from browser, already encrypted)
                    .encryptedContent(request.getCiphertext())
                    .iv(request.getIv())
                    // User DEK envelope (from browser, only user can unwrap)
                    .encryptedDek(request.getEncryptedDEK())
                    .dekIv(request.getDekIv())
                    .dekSalt(request.getSalt())
                    // Server DEK envelope (for release path)
                    .encryptedDekServer(serverDekEncrypted.ciphertextBase64())
                    .dekIvServer(serverDekEncrypted.ivBase64())
                    .dekSaltServer(aesEncryptionUtil.toBase64(serverSalt))
                    // File attachment (from browser, already encrypted, stored in DB)
                    .originalFileName(request.getOriginalFileName())
                    .fileCiphertext(request.getFileCiphertext())
                    .fileIvB64(request.getFileIv())
                    .recipients(recipients)
                    .build();

            vaultItemRepository.save(item);
            auditLogService.log(userId, AuditEventType.VAULT_ITEM_CREATED, "USER",
                    "Vault item created: " + request.getLabel() + " [" + request.getContentType() + "]");

            return mapToResponse(item, false);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to store vault item for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to store vault item", e);
        }
    }

    /**
     * Returns a list of vault items with metadata only — no encrypted blobs.
     * Used for the vault list view where decryption is not needed.
     */
    @Transactional(readOnly = true)
    public List<VaultItemResponse> listVaultItems(UUID userId) {
        return vaultItemRepository.findAllByUserIdAndIsActiveTrue(userId)
                .stream()
                .map(item -> mapToResponse(item, false))
                .collect(Collectors.toList());
    }

    /**
     * Returns the full encrypted blob for a single vault item.
     * The browser will use the returned ciphertext, iv, encryptedDEK, dekIv, salt
     * to perform local AES-256-GCM decryption — the server does NOT decrypt.
     */
    @Transactional(readOnly = true)
    public VaultItemResponse getVaultItem(UUID userId, UUID itemId) {
        VaultItem item = vaultItemRepository.findByIdAndUserIdAndIsActiveTrue(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vault item not found"));
        // Return the full blob — browser handles decryption
        return mapToResponse(item, true);
    }

    /**
     * Updates an existing vault item with a new encrypted payload from the browser.
     * The browser re-encrypts content with the existing DEK and sends the new ciphertext.
     * The server re-wraps the server DEK copy using the provided rawDEK.
     */
    @Transactional
    public VaultItemResponse updateVaultItem(UUID userId, UUID itemId, UpdateVaultItemRequest request) {
        VaultItem item = vaultItemRepository.findByIdAndUserIdAndIsActiveTrue(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vault item not found"));

        try {
            // Re-wrap server DEK copy with new rawDEK
            byte[] rawDekBytes = Base64.getDecoder().decode(request.getRawDEK());
            SecretKey rawDek = new SecretKeySpec(rawDekBytes, "AES");
            java.util.Arrays.fill(rawDekBytes, (byte) 0);

            byte[] serverSalt = aesEncryptionUtil.generateSalt();
            byte[] serverMasterKeyBytes = deriveServerMasterKey(serverSalt);
            AesEncryptionUtil.EncryptionResult serverDekEncrypted =
                    aesEncryptionUtil.encryptDek(rawDek, serverMasterKeyBytes);
            java.util.Arrays.fill(serverMasterKeyBytes, (byte) 0);

            // Update content fields if new ciphertext provided
            item.setLabel(request.getLabel());
            item.setContentType(request.getContentType());
            if (request.getCiphertext() != null) {
                item.setEncryptedContent(request.getCiphertext());
                item.setIv(request.getIv());
            }

            // Update user DEK envelope
            item.setEncryptedDek(request.getEncryptedDEK());
            item.setDekIv(request.getDekIv());
            item.setDekSalt(request.getSalt());

            // Update server DEK envelope
            item.setEncryptedDekServer(serverDekEncrypted.ciphertextBase64());
            item.setDekIvServer(serverDekEncrypted.ivBase64());
            item.setDekSaltServer(aesEncryptionUtil.toBase64(serverSalt));

            // Update recipients if provided
            if (request.getRecipientIds() != null) {
                item.setRecipients(resolveRecipients(request.getRecipientIds(), userId));
            }

            vaultItemRepository.save(item);
            auditLogService.log(userId, AuditEventType.VAULT_ITEM_UPDATED, "USER",
                    "Vault item updated: " + request.getLabel());

            return mapToResponse(item, false);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update vault item {} for user {}: {}", itemId, userId, e.getMessage());
            throw new RuntimeException("Failed to update vault item", e);
        }
    }

    /**
     * Soft-deletes a vault item.
     * JWT authentication is the authorization gate — no password needed here.
     * The browser verifies the vault password client-side before calling this endpoint,
     * preventing accidental deletes without sacrificing zero-knowledge.
     */
    @Transactional
    public void deleteVaultItem(UUID userId, UUID itemId) {
        VaultItem item = vaultItemRepository.findByIdAndUserIdAndIsActiveTrue(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vault item not found"));

        item.setIsActive(false);
        vaultItemRepository.save(item);
        auditLogService.log(userId, AuditEventType.VAULT_ITEM_DELETED, "USER",
                "Vault item soft-deleted: " + item.getLabel());
    }

    // ==================== Release Path (Server Key — ContentReleaseJob only) ====================

    /**
     * Decrypts a vault item's text content using the server master key.
     * Used ONLY by ContentReleaseJob. The server secret never leaves the server.
     * This is the ONLY server-side decryption in the entire application.
     */
    @Transactional(readOnly = true)
    public VaultItemResponse decryptVaultItemForRelease(UUID userId, UUID itemId) {
        VaultItem item = vaultItemRepository.findByIdAndUserIdAndIsActiveTrue(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vault item not found"));

        if (item.getEncryptedDekServer() == null || item.getDekSaltServer() == null) {
            log.warn("Vault item {} has no server DEK — skipping content for release", itemId);
            return mapToResponse(item, false);
        }

        try {
            byte[] serverSalt = aesEncryptionUtil.fromBase64(item.getDekSaltServer());
            byte[] serverMasterKeyBytes = deriveServerMasterKey(serverSalt);
            SecretKey dek = aesEncryptionUtil.decryptDek(
                    item.getEncryptedDekServer(), item.getDekIvServer(), serverMasterKeyBytes);
            java.util.Arrays.fill(serverMasterKeyBytes, (byte) 0);

            VaultItemResponse response = mapToResponse(item, true);
            if (item.getEncryptedContent() != null) {
                byte[] contentBytes = aesEncryptionUtil.decrypt(
                        item.getEncryptedContent(), item.getIv(), dek);
                response.setCiphertext(new String(contentBytes, java.nio.charset.StandardCharsets.UTF_8));
            }
            return response;
        } catch (Exception e) {
            log.error("Failed to decrypt vault item {} for release: {}", itemId, e.getMessage());
            throw new RuntimeException("Failed to decrypt vault item for release", e);
        }
    }

    /**
     * Decrypts a vault item's file attachment using the server master key.
     * Used ONLY by ContentReleaseJob.
     */
    @Transactional(readOnly = true)
    public byte[] downloadVaultItemFileForRelease(UUID userId, UUID itemId) {
        VaultItem item = vaultItemRepository.findByIdAndUserIdAndIsActiveTrue(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vault item not found"));

        if (item.getEncryptedDekServer() == null || item.getDekSaltServer() == null) {
            throw new RuntimeException("Vault item " + itemId + " has no server DEK — cannot release file.");
        }

        try {
            byte[] serverSalt = aesEncryptionUtil.fromBase64(item.getDekSaltServer());
            byte[] serverMasterKeyBytes = deriveServerMasterKey(serverSalt);
            SecretKey dek = aesEncryptionUtil.decryptDek(
                    item.getEncryptedDekServer(), item.getDekIvServer(), serverMasterKeyBytes);
            java.util.Arrays.fill(serverMasterKeyBytes, (byte) 0);

            // New DB-backed file
            if (item.hasDbFile()) {
                String ivToUse = item.getFileIvB64();
                return aesEncryptionUtil.decrypt(item.getFileCiphertext(), ivToUse, dek);
            }
            // Legacy filesystem-backed file
            if (item.hasLegacyFile()) {
                byte[] encFileBytesBase64 = java.nio.file.Files.readAllBytes(
                        java.nio.file.Paths.get(item.getEncryptedFilePath()));
                String encFileBase64 = new String(encFileBytesBase64, java.nio.charset.StandardCharsets.UTF_8);
                String ivToUse = item.getFileIv() != null ? item.getFileIv() : item.getIv();
                return aesEncryptionUtil.decrypt(encFileBase64, ivToUse, dek);
            }
            throw new ResourceNotFoundException("No file data found for vault item " + itemId);

        } catch (Exception e) {
            log.error("Failed to decrypt file for vault item {} for release: {}", itemId, e.getMessage());
            throw new RuntimeException("Failed to decrypt file for release", e);
        }
    }

    // ==================== Private Helpers ====================

    private byte[] deriveServerMasterKey(byte[] serverSalt) {
        try {
            return aesEncryptionUtil.deriveServerKey(serverSecret, serverSalt);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive server master key", e);
        }
    }

    private User getUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private List<Recipient> resolveRecipients(List<UUID> recipientIds, UUID userId) {
        return recipientIds.stream()
                .map(rId -> recipientRepository.findByIdAndUserId(rId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Recipient not found: " + rId)))
                .collect(Collectors.toList());
    }

    private void validateAtLeastOnePayload(String ciphertext, String fileCiphertext) {
        if ((ciphertext == null || ciphertext.isBlank())
                && (fileCiphertext == null || fileCiphertext.isBlank())) {
            throw new IllegalArgumentException("Either encrypted content or an encrypted file must be provided.");
        }
    }

    private VaultItemResponse mapToResponse(VaultItem item, boolean includeBlob) {
        List<RecipientResponse> recipientResponses = item.getRecipients().stream()
                .map(r -> RecipientResponse.builder()
                        .id(r.getId())
                        .name(r.getName())
                        .email(r.getEmail())
                        .phone(r.getPhone())
                        .relationship(r.getRelationship())
                        .notifyOnRelease(r.getNotifyOnRelease())
                        .build())
                .collect(Collectors.toList());

        boolean hasFile = item.hasDbFile() || item.hasLegacyFile();

        VaultItemResponse.VaultItemResponseBuilder builder = VaultItemResponse.builder()
                .id(item.getId())
                .label(item.getLabel())
                .contentType(item.getContentType())
                .hasContent(item.getEncryptedContent() != null)
                .hasFile(hasFile)
                .originalFileName(item.getOriginalFileName())
                .recipients(recipientResponses)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt());

        if (includeBlob) {
            // Return full encrypted blob — browser will decrypt locally
            builder
                    .ciphertext(item.getEncryptedContent())
                    .iv(item.getIv())
                    .encryptedDEK(item.getEncryptedDek())
                    .dekIv(item.getDekIv())
                    .salt(item.getDekSalt());

            // Include file blob if present (DB-backed only; legacy file not supported in ZK path)
            if (item.hasDbFile()) {
                builder
                        .fileCiphertext(item.getFileCiphertext())
                        .fileIv(item.getFileIvB64());
            }
        }

        return builder.build();
    }
}
