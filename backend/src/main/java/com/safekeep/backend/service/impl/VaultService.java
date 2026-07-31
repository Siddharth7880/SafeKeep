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

    @Transactional
    public VaultItemResponse createVaultItem(UUID userId, String userPassword, CreateVaultItemRequest request, MultipartFile file) {
        User user = getUserOrThrow(userId);

        try {
            // Ensure uploads directory exists
            Files.createDirectories(Paths.get(uploadDir));

            // Derive master key from user's password + stored salt
            byte[] salt = aesEncryptionUtil.fromBase64(user.getEncryptedMasterKeySalt());
            byte[] masterKeyBytes = aesEncryptionUtil.deriveKeyFromPassword(
                    userPassword.toCharArray(), salt);

            // Generate a random DEK for this vault item
            SecretKey dek = aesEncryptionUtil.generateDek();

            // Encrypt the content with DEK (if provided)
            String encryptedContentStr = null;
            String contentIvStr = null;
            if (request.getContent() != null && !request.getContent().trim().isEmpty()) {
                byte[] contentBytes = request.getContent().getBytes(StandardCharsets.UTF_8);
                AesEncryptionUtil.EncryptionResult contentEncrypted = aesEncryptionUtil.encrypt(contentBytes, dek);
                encryptedContentStr = contentEncrypted.ciphertextBase64();
                contentIvStr = contentEncrypted.ivBase64();
            }

            // Encrypt the file with DEK (if provided)
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
                
                // If content wasn't provided, use the file's IV for the main IV column 
                // since the DB schema requires `iv` to not be null.
                if (contentIvStr == null) {
                    contentIvStr = fileIvStr;
                }
            }
            
            if (encryptedContentStr == null && encryptedFilePath == null) {
                throw new IllegalArgumentException("Either content or a file must be provided.");
            }

            // Encrypt the DEK with master key (envelope encryption)
            AesEncryptionUtil.EncryptionResult dekEncrypted = aesEncryptionUtil.encryptDek(dek, masterKeyBytes);

            // Resolve recipients
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
                    .originalFileName(originalFileName)
                    .encryptedFilePath(encryptedFilePath)
                    .fileIv(fileIvStr)
                    .recipients(recipients)
                    .build();

            vaultItemRepository.save(item);
            auditLogService.log(userId, AuditEventType.VAULT_ITEM_CREATED, "USER",
                    "Vault item created: " + request.getLabel() + " [" + request.getContentType() + "]");

            return mapToResponse(item, false);

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

        User user = getUserOrThrow(userId);

        try {
            byte[] salt = aesEncryptionUtil.fromBase64(user.getEncryptedMasterKeySalt());
            byte[] masterKeyBytes = aesEncryptionUtil.deriveKeyFromPassword(
                    userPassword.toCharArray(), salt);

            // Decrypt DEK using master key
            SecretKey dek = aesEncryptionUtil.decryptDek(
                    item.getEncryptedDek(), item.getDekIv(), masterKeyBytes);

            VaultItemResponse response = mapToResponse(item, true);
            // Decrypt content using DEK (if exists)
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

        User user = getUserOrThrow(userId);

        try {
            byte[] salt = aesEncryptionUtil.fromBase64(user.getEncryptedMasterKeySalt());
            byte[] masterKeyBytes = aesEncryptionUtil.deriveKeyFromPassword(
                    userPassword.toCharArray(), salt);

            // Decrypt DEK using master key to verify password and to re-encrypt new content
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
                // Cannot remove text content if there's no file
                throw new IllegalArgumentException("Either content or a file must be provided.");
            }

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

        User user = getUserOrThrow(userId);

        try {
            byte[] salt = aesEncryptionUtil.fromBase64(user.getEncryptedMasterKeySalt());
            byte[] masterKeyBytes = aesEncryptionUtil.deriveKeyFromPassword(
                    userPassword.toCharArray(), salt);

            // Decrypt DEK using master key
            SecretKey dek = aesEncryptionUtil.decryptDek(
                    item.getEncryptedDek(), item.getDekIv(), masterKeyBytes);

            // Read encrypted file and decrypt
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
    public void deleteVaultItem(UUID userId, UUID itemId) {
        VaultItem item = vaultItemRepository.findByIdAndUserIdAndIsActiveTrue(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vault item not found"));
        item.setIsActive(false);  // Soft delete
        vaultItemRepository.save(item);
        auditLogService.log(userId, AuditEventType.VAULT_ITEM_DELETED, "USER",
                "Vault item soft-deleted: " + item.getLabel());
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
                .content(includeContent ? null : null) // Set by caller if needed
                .recipients(recipientResponses)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
