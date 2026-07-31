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
    private final SmsNotificationService smsNotificationService;

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

        // Build recipient → items map
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

            List<ReleaseToken> tokens = new ArrayList<>();
            for (VaultItem item : recipientItems) {
                try {
                    String token = generateSignedToken(user.getId(), recipient.getId(), item.getId());
                    ReleaseToken releaseToken = ReleaseToken.builder()
                            .token(token)
                            .userId(user.getId())
                            .recipientId(recipient.getId())
                            .vaultItemId(item.getId())
                            .expiresAt(LocalDateTime.now().plusHours(tokenExpiryHours))
                            .build();
                    releaseTokenRepository.save(releaseToken);
                    tokens.add(releaseToken);
                } catch (Exception e) {
                    log.error("Failed to generate token for recipient {} item {}: {}",
                            recipient.getId(), item.getId(), e.getMessage());
                }
            }

            // Send email & SMS to recipient
            try {
                emailNotificationService.sendReleaseNotification(recipient, user, tokens);
                smsNotificationService.sendReleaseNotification(recipient, user);
            } catch (Exception e) {
                log.error("Failed to send release notification to {}: {}", recipient.getEmail(), e.getMessage());
            }
        }

        auditLogService.log(user.getId(), AuditEventType.CONTENT_RELEASED, "SCHEDULER",
                "Content released to " + recipientItemMap.size() + " recipients with " + items.size() + " items");
    }

    private String generateSignedToken(UUID userId, UUID recipientId, UUID vaultItemId) throws Exception {
        String payload = userId + ":" + recipientId + ":" + vaultItemId + ":" + System.currentTimeMillis();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(releaseTokenSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes()) + "." +
               Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
    }
}
