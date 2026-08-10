package com.safekeep.backend.service.impl;

import com.safekeep.backend.dto.request.CreateVaultItemRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class VaultService {

    private final VaultItemRepository vaultItemRepository;
    private final UserRepository userRepository;
    private final RecipientRepository recipientRepository;
    private final AesEncryptionUtil aesEncryptionUtil;
    private final AuditLogService auditLogService;
    private final String uploadDir = "uploads/";

    public record DecryptedFile(byte[] bytes, String originalFileName) {}

    @org.springframework.beans.factory.annotation.Value("${app.release.token-secret}")
    private String serverSecret;

    /**
     * Derives a 256-bit master key from the user's plaintext password and a random per-item salt
     * using Argon2id (memory-hard, 64 MB RAM cost). Supplying a wrong password will cause
     * GCM tag verification to fail and throw an AEADBadTagException.
     */
    private byte[] deriveMasterKey(String userPassword, byte[] salt) {
        try {
            return aesEncryptionUtil.deriveKeyFromPassword(userPassword.toCharArray(), salt);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive master key from password", e);
        }
    }

    /**
     * Derives a 256-bit server master key from the app secret using Argon2id.
     * This key is used exclusively by the ContentReleaseJob to decrypt vault items
     * without needing the user's password. The server secret is never sent to clients.
     */
    private byte[] deriveServerMasterKey(byte[] serverSalt) {
        try {
            return aesEncryptionUtil.deriveServerKey(serverSecret, serverSalt);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive server master key", e);
        }
    }

    @Transactional
    public VaultItemResponse createVaultItem(UUID userId, String userPassword, CreateVaultItemRequest request, MultipartFile file) {
        User user = getUserOrThrow(userId);

        if (userPassword == null || userPassword.isBlank()) {
            throw new IllegalArgumentException("Vault password is required");
        }

        try {
            Files.createDirectories(Paths.get(uploadDir));

            // Derive master key from user's actual password + fresh random salt
            byte[] salt = aesEncryptionUtil.generateSalt();
            byte[] masterKeyBytes = deriveMasterKey(userPassword, salt);
            String saltBase64 = aesEncryptionUtil.toBase64(salt);

            SecretKey dek = aesEncryptionUtil.generateDek();

            String encryptedContentStr = null;
            String contentIvStr = null;
            if (request.getContent() != null && !request.getContent().trim().isEmpty()) {
                byte[] contentBytes = request.getContent().getBytes(StandardCharsets.UTF_8);
                AesEncryptionUtil.EncryptionResult contentEncrypted = aesEncryptionUtil.encrypt(contentBytes, dek);
                encryptedContentStr = contentEncrypted.ciphertextBase64();
                contentIvStr = contentEncrypted.ivBase64();
            }

            String originalFileName = null;
            String encryptedFilePath = null;
            String fileIvStr = null;
            if (file != null && !file.isEmpty()) {
                originalFileName = file.getOriginalFilename();
                AesEncryptionUtil.EncryptionResult fileEncrypted = aesEncryptionUtil.encrypt(file.getBytes(), dek);
                
                String fileId = UUID.randomUUID().toString();
                Path path = Paths.get(uploadDir, fileId + ".enc");
                Files.write(path, fileEncrypted.ciphertextBase64().getBytes(StandardCharsets.UTF_8));
                encryptedFilePath = path.toString();
                fileIvStr = fileEncrypted.ivBase64();
                
                if (contentIvStr == null) {
                    contentIvStr = fileIvStr;
                }
            }
            
            if (encryptedContentStr == null && encryptedFilePath == null) {
                throw new IllegalArgumentException("Either content or a file must be provided.");
            }

            AesEncryptionUtil.EncryptionResult dekEncrypted = aesEncryptionUtil.encryptDek(dek, masterKeyBytes);

            // Also encrypt DEK with server key (Argon2id + server secret) for the release path
            byte[] serverSalt = aesEncryptionUtil.generateSalt();
            byte[] serverMasterKeyBytes = deriveServerMasterKey(serverSalt);
            AesEncryptionUtil.EncryptionResult dekEncryptedServer = aesEncryptionUtil.encryptDek(dek, serverMasterKeyBytes);

            List<Recipient> recipients = request.getRecipientIds().stream()
                    .map(rId -> recipientRepository.findByIdAndUserId(rId, userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Recipient not found: " + rId)))
                    .collect(Collectors.toList());

            VaultItem item = VaultItem.builder()
                    .user(user)
                    .label(request.getLabel())
                    .contentType(request.getContentType())
                    .encryptedContent(encryptedContentStr)
                    .iv(contentIvStr)
                    .encryptedDek(dekEncrypted.ciphertextBase64())
                    .dekIv(dekEncrypted.ivBase64())
                    .dekSalt(saltBase64)
                    .encryptedDekServer(dekEncryptedServer.ciphertextBase64())
                    .dekIvServer(dekEncryptedServer.ivBase64())
                    .dekSaltServer(aesEncryptionUtil.toBase64(serverSalt))
                    .originalFileName(originalFileName)
                    .encryptedFilePath(encryptedFilePath)
                    .fileIv(fileIvStr)
                    .recipients(recipients)
                    .build();

            vaultItemRepository.save(item);
            auditLogService.log(userId, AuditEventType.VAULT_ITEM_CREATED, "USER",
                    "Vault item created: " + request.getLabel() + " [" + request.getContentType() + "]");

            return mapToResponse(item, false);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create vault item for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to encrypt and store vault item", e);
        }
    }

    @Transactional(readOnly = true)
    public List<VaultItemResponse> listVaultItems(UUID userId) {
        return vaultItemRepository.findAllByUserIdAndIsActiveTrue(userId)
                .stream()
                .map(item -> mapToResponse(item, false))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VaultItemResponse getVaultItem(UUID userId, UUID itemId, String userPassword) {
        VaultItem item = vaultItemRepository.findByIdAndUserIdAndIsActiveTrue(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vault item not found"));

        if (userPassword == null || userPassword.isBlank()) {
            throw new IllegalArgumentException("Vault password is required");
        }
        if (item.getDekSalt() == null) {
            throw new RuntimeException("This vault item was created with an older encryption scheme and cannot be decrypted.");
        }

        try {
            byte[] salt = aesEncryptionUtil.fromBase64(item.getDekSalt());
            byte[] masterKeyBytes = deriveMasterKey(userPassword, salt);
            SecretKey dek = aesEncryptionUtil.decryptDek(
                    item.getEncryptedDek(), item.getDekIv(), masterKeyBytes);

            VaultItemResponse response = mapToResponse(item, true);
            if (item.getEncryptedContent() != null) {
                byte[] contentBytes = aesEncryptionUtil.decrypt(
                        item.getEncryptedContent(), item.getIv(), dek);
                response.setContent(new String(contentBytes, StandardCharsets.UTF_8));
            }
            return response;
        } catch (Exception e) {
            log.error("Failed to decrypt vault item {} for user {}: {}", itemId, userId, e.getMessage());
            throw new RuntimeException("Failed to decrypt vault item — wrong password or corrupted data", e);
        }
    }

    @Transactional
    public VaultItemResponse updateVaultItem(UUID userId, UUID itemId, String userPassword, com.safekeep.backend.dto.request.UpdateVaultItemRequest request) {
        VaultItem item = vaultItemRepository.findByIdAndUserIdAndIsActiveTrue(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vault item not found"));

        if (userPassword == null || userPassword.isBlank()) {
            throw new IllegalArgumentException("Vault password is required");
        }
        if (item.getDekSalt() == null) {
            throw new RuntimeException("This vault item was created with an older encryption scheme and cannot be updated.");
        }

        try {
            byte[] salt = aesEncryptionUtil.fromBase64(item.getDekSalt());
            byte[] masterKeyBytes = deriveMasterKey(userPassword, salt);
            SecretKey dek = aesEncryptionUtil.decryptDek(
                    item.getEncryptedDek(), item.getDekIv(), masterKeyBytes);

            item.setLabel(request.getLabel());
            item.setContentType(request.getContentType());

            if (request.getContent() != null && !request.getContent().trim().isEmpty()) {
                byte[] contentBytes = request.getContent().getBytes(StandardCharsets.UTF_8);
                AesEncryptionUtil.EncryptionResult contentEncrypted = aesEncryptionUtil.encrypt(contentBytes, dek);
                item.setEncryptedContent(contentEncrypted.ciphertextBase64());
                item.setIv(contentEncrypted.ivBase64());
            } else if (item.getEncryptedFilePath() == null) {
                throw new IllegalArgumentException("Either content or a file must be provided.");
            }

            // Re-encrypt server DEK so release path stays valid after this update
            byte[] serverSalt = aesEncryptionUtil.generateSalt();
            byte[] serverMasterKeyBytes = deriveServerMasterKey(serverSalt);
            AesEncryptionUtil.EncryptionResult dekEncryptedServer = aesEncryptionUtil.encryptDek(dek, serverMasterKeyBytes);
            item.setEncryptedDekServer(dekEncryptedServer.ciphertextBase64());
            item.setDekIvServer(dekEncryptedServer.ivBase64());
            item.setDekSaltServer(aesEncryptionUtil.toBase64(serverSalt));

            if (request.getRecipientIds() != null) {
                List<Recipient> recipients = request.getRecipientIds().stream()
                        .map(rId -> recipientRepository.findByIdAndUserId(rId, userId)
                                .orElseThrow(() -> new ResourceNotFoundException("Recipient not found: " + rId)))
                        .collect(Collectors.toList());
                item.setRecipients(recipients);
            }

            vaultItemRepository.save(item);
            auditLogService.log(userId, com.safekeep.backend.enums.AuditEventType.VAULT_ITEM_UPDATED, "USER",
                    "Vault item updated: " + request.getLabel());

            return mapToResponse(item, false);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update vault item {} for user {}: {}", itemId, userId, e.getMessage());
            throw new RuntimeException("Failed to update vault item — wrong password or corrupted data", e);
        }
    }

    @Transactional(readOnly = true)
    public DecryptedFile downloadVaultItemFile(UUID userId, UUID itemId, String userPassword) {
        VaultItem item = vaultItemRepository.findByIdAndUserIdAndIsActiveTrue(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vault item not found"));

        if (item.getEncryptedFilePath() == null) {
            throw new ResourceNotFoundException("No file attached to this vault item");
        }
        if (userPassword == null || userPassword.isBlank()) {
            throw new IllegalArgumentException("Vault password is required");
        }
        if (item.getDekSalt() == null) {
            throw new RuntimeException("This vault item was created with an older encryption scheme and cannot be downloaded.");
        }

        try {
            byte[] salt = aesEncryptionUtil.fromBase64(item.getDekSalt());
            byte[] masterKeyBytes = deriveMasterKey(userPassword, salt);
            SecretKey dek = aesEncryptionUtil.decryptDek(
                    item.getEncryptedDek(), item.getDekIv(), masterKeyBytes);

            byte[] encryptedFileBytesBase64 = Files.readAllBytes(Paths.get(item.getEncryptedFilePath()));
            String encryptedFileBase64String = new String(encryptedFileBytesBase64, StandardCharsets.UTF_8);
            
            String ivToUse = item.getFileIv() != null ? item.getFileIv() : item.getIv();
            byte[] decryptedBytes = aesEncryptionUtil.decrypt(encryptedFileBase64String, ivToUse, dek);

            return new DecryptedFile(decryptedBytes, item.getOriginalFileName());
        } catch (Exception e) {
            log.error("Failed to decrypt file for vault item {} for user {}: {}", itemId, userId, e.getMessage());
            throw new RuntimeException("Failed to decrypt file — wrong password or corrupted data", e);
        }
    }

    @Transactional
    public void deleteVaultItem(UUID userId, UUID itemId, String userPassword) {
        VaultItem item = vaultItemRepository.findByIdAndUserIdAndIsActiveTrue(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vault item not found"));

        if (userPassword == null || userPassword.isBlank()) {
            throw new IllegalArgumentException("Vault password is required");
        }
        if (item.getDekSalt() == null) {
            throw new RuntimeException("This vault item was created with an older encryption scheme and cannot be verified.");
        }

        // Verify password by attempting to decrypt the DEK — GCM tag failure = wrong password
        try {
            byte[] salt = aesEncryptionUtil.fromBase64(item.getDekSalt());
            byte[] masterKeyBytes = deriveMasterKey(userPassword, salt);
            aesEncryptionUtil.decryptDek(item.getEncryptedDek(), item.getDekIv(), masterKeyBytes);
        } catch (Exception e) {
            throw new RuntimeException("Wrong vault password — cannot delete item", e);
        }

        item.setIsActive(false);
        vaultItemRepository.save(item);
        auditLogService.log(userId, AuditEventType.VAULT_ITEM_DELETED, "USER",
                "Vault item soft-deleted: " + item.getLabel());
    }

    // ==================== RELEASE PATH (server-key only, no user password) ====================

    /**
     * Decrypts a vault item's text content using the server master key (Argon2id + server secret).
     * Used ONLY by ContentReleaseJob. The server secret never leaves the server.
     */
    @Transactional(readOnly = true)
    public VaultItemResponse decryptVaultItemForRelease(UUID userId, UUID itemId) {
        VaultItem item = vaultItemRepository.findByIdAndUserIdAndIsActiveTrue(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vault item not found"));

        if (item.getEncryptedDekServer() == null || item.getDekSaltServer() == null) {
            log.warn("Vault item {} has no server DEK (created before dual-DEK upgrade)", itemId);
            return mapToResponse(item, false); // return metadata only, skip content
        }

        try {
            byte[] serverSalt = aesEncryptionUtil.fromBase64(item.getDekSaltServer());
            byte[] serverMasterKeyBytes = deriveServerMasterKey(serverSalt);
            SecretKey dek = aesEncryptionUtil.decryptDek(
                    item.getEncryptedDekServer(), item.getDekIvServer(), serverMasterKeyBytes);

            VaultItemResponse response = mapToResponse(item, true);
            if (item.getEncryptedContent() != null) {
                byte[] contentBytes = aesEncryptionUtil.decrypt(
                        item.getEncryptedContent(), item.getIv(), dek);
                response.setContent(new String(contentBytes, StandardCharsets.UTF_8));
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
    public DecryptedFile downloadVaultItemFileForRelease(UUID userId, UUID itemId) {
        VaultItem item = vaultItemRepository.findByIdAndUserIdAndIsActiveTrue(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vault item not found"));

        if (item.getEncryptedFilePath() == null) {
            throw new ResourceNotFoundException("No file attached to this vault item");
        }
        if (item.getEncryptedDekServer() == null || item.getDekSaltServer() == null) {
            throw new RuntimeException("Vault item " + itemId + " has no server DEK — cannot release file.");
        }

        try {
            byte[] serverSalt = aesEncryptionUtil.fromBase64(item.getDekSaltServer());
            byte[] serverMasterKeyBytes = deriveServerMasterKey(serverSalt);
            SecretKey dek = aesEncryptionUtil.decryptDek(
                    item.getEncryptedDekServer(), item.getDekIvServer(), serverMasterKeyBytes);

            byte[] encryptedFileBytesBase64 = Files.readAllBytes(Paths.get(item.getEncryptedFilePath()));
            String encryptedFileBase64String = new String(encryptedFileBytesBase64, StandardCharsets.UTF_8);

            String ivToUse = item.getFileIv() != null ? item.getFileIv() : item.getIv();
            byte[] decryptedBytes = aesEncryptionUtil.decrypt(encryptedFileBase64String, ivToUse, dek);

            return new DecryptedFile(decryptedBytes, item.getOriginalFileName());
        } catch (Exception e) {
            log.error("Failed to decrypt file for vault item {} for release: {}", itemId, e.getMessage());
            throw new RuntimeException("Failed to decrypt file for release", e);
        }
    }


    private User getUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private VaultItemResponse mapToResponse(VaultItem item, boolean includeContent) {
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

        return VaultItemResponse.builder()
                .id(item.getId())
                .label(item.getLabel())
                .contentType(item.getContentType())
                .hasContent(item.getEncryptedContent() != null)
                .originalFileName(item.getOriginalFileName())
                .hasFile(item.getEncryptedFilePath() != null)
                .content(null)  // populated by caller when includeContent=true
                .recipients(recipientResponses)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
