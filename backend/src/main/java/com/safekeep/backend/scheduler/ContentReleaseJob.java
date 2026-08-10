package com.safekeep.backend.scheduler;

import com.safekeep.backend.entity.Recipient;
import com.safekeep.backend.entity.ReleaseToken;
import com.safekeep.backend.entity.User;
import com.safekeep.backend.entity.VaultItem;
import com.safekeep.backend.enums.AuditEventType;
import com.safekeep.backend.repository.ReleaseTokenRepository;
import com.safekeep.backend.repository.VaultItemRepository;
import com.safekeep.backend.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Handles the actual content release process:
 * 1. Generates signed, expiring access tokens for each recipient
 * 2. Emails recipients their access links
 * 3. Records release in audit log
 *
 * Idempotent: checks releasedAt before executing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContentReleaseJob {

    private final VaultItemRepository vaultItemRepository;
    private final ReleaseTokenRepository releaseTokenRepository;
    private final AuditLogService auditLogService;
    private final EmailNotificationService emailNotificationService;
    private final com.safekeep.backend.service.impl.VaultService vaultService;

    @Value("${app.release.token-secret}")
    private String releaseTokenSecret;

    @Value("${app.release.token-expiry-hours}")
    private int tokenExpiryHours;

    @Transactional
    public void releaseContent(User user) {
        if (user.getReleasedAt() == null) {
            log.error("releaseContent called but releasedAt is null for user {} — aborting", user.getId());
            return;
        }

        List<VaultItem> items = vaultItemRepository.findAllByUserIdAndIsActiveTrue(user.getId());

        if (items.isEmpty()) {
            log.warn("User {} released but has no active vault items", user.getId());
            return;
        }

        Map<Recipient, List<VaultItem>> recipientItemMap = new HashMap<>();
        for (VaultItem item : items) {
            for (Recipient recipient : item.getRecipients()) {
                if (recipient.getNotifyOnRelease()) {
                    recipientItemMap.computeIfAbsent(recipient, k -> new ArrayList<>()).add(item);
                }
            }
        }

        for (Map.Entry<Recipient, List<VaultItem>> entry : recipientItemMap.entrySet()) {
            Recipient recipient = entry.getKey();
            List<VaultItem> recipientItems = entry.getValue();

            String randomPassword = UUID.randomUUID().toString().substring(0, 8);
            byte[] zipBytes = null;
            
            try {
                java.io.File zipTemp = java.io.File.createTempFile("SecureVault", ".zip");
                net.lingala.zip4j.model.ZipParameters zipParameters = new net.lingala.zip4j.model.ZipParameters();
                zipParameters.setEncryptFiles(true);
                zipParameters.setEncryptionMethod(net.lingala.zip4j.model.enums.EncryptionMethod.AES);
                zipParameters.setAesKeyStrength(net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256);

                try (net.lingala.zip4j.ZipFile zipFile = new net.lingala.zip4j.ZipFile(zipTemp, randomPassword.toCharArray())) {
                    for (VaultItem item : recipientItems) {
                        try {
                            // Use the server-key path — no user password required
                            com.safekeep.backend.dto.response.VaultItemResponse decryptedResponse =
                                    vaultService.decryptVaultItemForRelease(user.getId(), item.getId());
                            if (Boolean.TRUE.equals(decryptedResponse.getHasContent())
                                    && decryptedResponse.getContent() != null) {
                                net.lingala.zip4j.model.ZipParameters textParams = new net.lingala.zip4j.model.ZipParameters(zipParameters);
                                textParams.setFileNameInZip(item.getLabel().replaceAll("[^a-zA-Z0-9.-]", "_") + ".txt");
                                zipFile.addStream(new java.io.ByteArrayInputStream(decryptedResponse.getContent().getBytes(StandardCharsets.UTF_8)), textParams);
                            }
                            if (Boolean.TRUE.equals(decryptedResponse.getHasFile())) {
                                com.safekeep.backend.service.impl.VaultService.DecryptedFile decFile =
                                        vaultService.downloadVaultItemFileForRelease(user.getId(), item.getId());
                                net.lingala.zip4j.model.ZipParameters fileParams = new net.lingala.zip4j.model.ZipParameters(zipParameters);
                                fileParams.setFileNameInZip(decFile.originalFileName());
                                zipFile.addStream(new java.io.ByteArrayInputStream(decFile.bytes()), fileParams);
                            }
                        } catch (Exception e) {
                            log.error("Failed to decrypt and zip item {}: {}", item.getId(), e.getMessage());
                        }
                    }
                }
                zipBytes = java.nio.file.Files.readAllBytes(zipTemp.toPath());
                zipTemp.delete();
            } catch (Exception e) {
                log.error("Failed to create ZIP file for recipient {}: {}", recipient.getEmail(), e.getMessage());
            }

            if (zipBytes != null) {
                try {
                    emailNotificationService.sendReleaseNotification(recipient, user, zipBytes, randomPassword);
                } catch (Exception e) {
                    log.error("Failed to send release notification to {}: {}", recipient.getEmail(), e.getMessage());
                }
            }
        }

        auditLogService.log(user.getId(), AuditEventType.CONTENT_RELEASED, "SCHEDULER",
                "Content released to " + recipientItemMap.size() + " recipients with " + items.size() + " items via ZIP");
    }
}
