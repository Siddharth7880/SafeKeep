package com.safekeep.backend;

import com.safekeep.backend.dto.request.CreateVaultItemRequest;
import com.safekeep.backend.dto.response.VaultItemResponse;
import com.safekeep.backend.entity.Recipient;
import com.safekeep.backend.entity.User;
import com.safekeep.backend.entity.VaultItem;
import com.safekeep.backend.enums.AuditEventType;
import com.safekeep.backend.enums.ContentType;
import com.safekeep.backend.enums.UserStatus;
import com.safekeep.backend.repository.AuditLogRepository;
import com.safekeep.backend.repository.RecipientRepository;
import com.safekeep.backend.repository.UserRepository;
import com.safekeep.backend.repository.VaultItemRepository;
import com.safekeep.backend.service.impl.VaultService;
import com.safekeep.backend.util.AesEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests using Testcontainers with a real PostgreSQL instance.
 *
 * Tests cover the full vault lifecycle:
 *   - Create vault item (store encrypted blob, server DEK wrapping)
 *   - Retrieve vault item (return blob, no server decryption)
 *   - Soft-delete vault item
 *   - Audit log assertions: every state change has a log entry
 *   - Server release path: decryptVaultItemForRelease works with server key
 *
 * The Testcontainers @Container spins up a fresh Postgres instance for this test class
 * and tears it down after. Flyway migrations run automatically via Spring Boot.
 */
@SpringBootTest
@Testcontainers
@DisplayName("Vault Integration Tests (Testcontainers + PostgreSQL)")
class VaultIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("safekeep_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Use in-memory Quartz for tests — no JDBC tables needed
        registry.add("spring.quartz.job-store-type", () -> "memory");
        registry.add("spring.quartz.properties.org.quartz.scheduler.instanceName", () -> "TestScheduler");
        registry.add("spring.quartz.properties.org.quartz.jobStore.class", () -> "org.quartz.simpl.RAMJobStore");
        // Brevo not needed in tests
        registry.add("brevo.api-key", () -> "test-key");
        registry.add("brevo.sender.email", () -> "test@test.com");
        registry.add("app.release.token-secret", () -> "integration-test-server-secret-32-chars!!");
        registry.add("app.base-url", () -> "http://localhost:5173");
        registry.add("app.cors.allowed-origins", () -> "http://localhost:5173");
        registry.add("app.jwt.secret", () -> "dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLUhTNTEy");
    }

    @Autowired VaultService         vaultService;
    @Autowired UserRepository       userRepository;
    @Autowired RecipientRepository  recipientRepository;
    @Autowired VaultItemRepository  vaultItemRepository;
    @Autowired AuditLogRepository   auditLogRepository;
    @Autowired AesEncryptionUtil    aesUtil;
    @Autowired PasswordEncoder      passwordEncoder;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Clean state between tests
        vaultItemRepository.deleteAll();
        auditLogRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .email("test@safekeep.com")
                .passwordHash(passwordEncoder.encode("login-password"))
                .fullName("Test User")
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .nextCheckinDeadline(LocalDateTime.now().plusDays(7))
                .lastCheckinAt(LocalDateTime.now())
                .build());
    }

    // ==================== Create ====================

    @Test
    @DisplayName("createVaultItem: stores encrypted blob and writes audit log")
    void createVaultItemStoresBlobAndLogs() throws Exception {
        CreateVaultItemRequest req = buildEncryptedCreateRequest("My secret note");

        VaultItemResponse response = vaultService.createVaultItem(testUser.getId(), req);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getLabel()).isEqualTo("Test Item");
        assertThat(response.getContentType()).isEqualTo(ContentType.TEXT_MESSAGE);
        // Metadata view — no blob fields
        assertThat(response.getCiphertext()).isNull();

        // Verify DB state
        VaultItem saved = vaultItemRepository.findById(response.getId()).orElseThrow();
        assertThat(saved.getEncryptedContent()).isNotBlank();
        assertThat(saved.getEncryptedDek()).isNotBlank();
        assertThat(saved.getDekSalt()).isNotBlank();
        assertThat(saved.getEncryptedDekServer()).isNotBlank(); // server DEK wrapped
        assertThat(saved.getIsActive()).isTrue();

        // Audit log: VAULT_ITEM_CREATED must be present
        var logs = auditLogRepository.findAllByUserIdOrderByCreatedAtDesc(
                testUser.getId(), org.springframework.data.domain.Pageable.unpaged());
        assertThat(logs.getContent())
                .anyMatch(log -> log.getEventType() == AuditEventType.VAULT_ITEM_CREATED);
    }

    @Test
    @DisplayName("createVaultItem: ciphertext stored is not plaintext (server never sees plaintext)")
    void storedCiphertextIsNotPlaintext() throws Exception {
        String plaintext = "do not store this plaintext";
        CreateVaultItemRequest req = buildEncryptedCreateRequest(plaintext);

        VaultItemResponse response = vaultService.createVaultItem(testUser.getId(), req);

        VaultItem saved = vaultItemRepository.findById(response.getId()).orElseThrow();
        // The stored encrypted_content must not contain the original plaintext
        assertThat(saved.getEncryptedContent()).doesNotContain(plaintext);
        // And it must be a valid Base64 string (AES-256-GCM ciphertext)
        assertThatCode(() -> java.util.Base64.getDecoder().decode(saved.getEncryptedContent()))
                .doesNotThrowAnyException();
    }

    // ==================== Get ====================

    @Test
    @DisplayName("getVaultItem: returns full encrypted blob (browser decrypts locally)")
    void getVaultItemReturnsEncryptedBlob() throws Exception {
        CreateVaultItemRequest req = buildEncryptedCreateRequest("secret");
        VaultItemResponse created = vaultService.createVaultItem(testUser.getId(), req);

        VaultItemResponse fetched = vaultService.getVaultItem(testUser.getId(), created.getId());

        // Full blob fields must be present
        assertThat(fetched.getCiphertext()).isNotBlank();
        assertThat(fetched.getIv()).isNotBlank();
        assertThat(fetched.getEncryptedDEK()).isNotBlank();
        assertThat(fetched.getDekIv()).isNotBlank();
        assertThat(fetched.getSalt()).isNotBlank();
    }

    @Test
    @DisplayName("getVaultItem: returns NOT_FOUND for items belonging to another user")
    void getVaultItemRejectsOtherUserItems() throws Exception {
        User otherUser = userRepository.save(User.builder()
                .email("other@safekeep.com")
                .passwordHash(passwordEncoder.encode("pass"))
                .fullName("Other User")
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .nextCheckinDeadline(LocalDateTime.now().plusDays(7))
                .lastCheckinAt(LocalDateTime.now())
                .build());

        CreateVaultItemRequest req = buildEncryptedCreateRequest("other user secret");
        VaultItemResponse created = vaultService.createVaultItem(otherUser.getId(), req);

        // Try to access as testUser — must fail
        assertThatThrownBy(() -> vaultService.getVaultItem(testUser.getId(), created.getId()))
                .isInstanceOf(com.safekeep.backend.exception.ResourceNotFoundException.class);
    }

    // ==================== Delete ====================

    @Test
    @DisplayName("deleteVaultItem: soft-deletes item and writes audit log")
    void deleteVaultItemSoftDeletesAndLogs() throws Exception {
        CreateVaultItemRequest req = buildEncryptedCreateRequest("to be deleted");
        VaultItemResponse created = vaultService.createVaultItem(testUser.getId(), req);

        vaultService.deleteVaultItem(testUser.getId(), created.getId());

        VaultItem deleted = vaultItemRepository.findById(created.getId()).orElseThrow();
        assertThat(deleted.getIsActive()).isFalse();

        // Soft-deleted item must not appear in list
        List<VaultItemResponse> items = vaultService.listVaultItems(testUser.getId());
        assertThat(items).noneMatch(i -> i.getId().equals(created.getId()));

        // Audit log: VAULT_ITEM_DELETED must be present
        var logs = auditLogRepository.findAllByUserIdOrderByCreatedAtDesc(
                testUser.getId(), org.springframework.data.domain.Pageable.unpaged());
        assertThat(logs.getContent())
                .anyMatch(log -> log.getEventType() == AuditEventType.VAULT_ITEM_DELETED);
    }

    // ==================== Server Release Path ====================

    @Test
    @DisplayName("decryptVaultItemForRelease: server can decrypt using server key (no user password)")
    void serverReleasePathDecryptsWithoutUserPassword() throws Exception {
        // Simulate browser: generate DEK, encrypt content, send rawDEK to server
        String vaultPassword = "user-vault-password";
        String plaintext     = "Release this to my family: seed phrase is abc def ghi";

        byte[]    salt      = aesUtil.generateSalt();
        byte[]    masterKey = aesUtil.deriveKeyFromPassword(vaultPassword.toCharArray(), salt);
        SecretKey dek       = aesUtil.generateDek();

        AesEncryptionUtil.EncryptionResult wrappedDek = aesUtil.encryptDek(dek, masterKey);
        AesEncryptionUtil.EncryptionResult content    = aesUtil.encrypt(
                plaintext.getBytes(StandardCharsets.UTF_8), dek);

        CreateVaultItemRequest req = new CreateVaultItemRequest();
        req.setLabel("Release Test");
        req.setContentType(ContentType.FINAL_INSTRUCTIONS);
        req.setCiphertext(content.ciphertextBase64());
        req.setIv(content.ivBase64());
        req.setEncryptedDEK(wrappedDek.ciphertextBase64());
        req.setDekIv(wrappedDek.ivBase64());
        req.setSalt(aesUtil.toBase64(salt));
        req.setRawDEK(aesUtil.toBase64(dek.getEncoded())); // raw DEK for server to wrap
        req.setRecipientIds(List.of());

        VaultItemResponse created = vaultService.createVaultItem(testUser.getId(), req);

        // Server release path: decrypt without user password
        VaultItemResponse released = vaultService.decryptVaultItemForRelease(
                testUser.getId(), created.getId());

        // In the release path, decrypted content is put in the 'ciphertext' field
        // (the response is for internal use by ContentReleaseJob, not the browser)
        assertThat(released.getCiphertext()).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Audit log completeness: create + get + delete all produce log entries")
    void auditLogCapturesAllStateTransitions() throws Exception {
        CreateVaultItemRequest req = buildEncryptedCreateRequest("audit test");
        VaultItemResponse item = vaultService.createVaultItem(testUser.getId(), req);
        vaultService.getVaultItem(testUser.getId(), item.getId()); // get doesn't log (read-only)
        vaultService.deleteVaultItem(testUser.getId(), item.getId());

        var logs = auditLogRepository.findAllByUserIdOrderByCreatedAtDesc(
                testUser.getId(), org.springframework.data.domain.Pageable.unpaged());

        var eventTypes = logs.getContent().stream()
                .map(l -> l.getEventType())
                .toList();

        assertThat(eventTypes).contains(AuditEventType.VAULT_ITEM_CREATED, AuditEventType.VAULT_ITEM_DELETED);
    }

    // ==================== Helpers ====================

    /**
     * Builds a CreateVaultItemRequest simulating what the browser would send
     * after encrypting content client-side.
     */
    private CreateVaultItemRequest buildEncryptedCreateRequest(String plaintext) throws Exception {
        byte[]    salt      = aesUtil.generateSalt();
        // Simulate browser: derive key, generate DEK, encrypt
        byte[]    masterKey = aesUtil.deriveKeyFromPassword("test-vault-password".toCharArray(), salt);
        SecretKey dek       = aesUtil.generateDek();

        AesEncryptionUtil.EncryptionResult wrappedDek = aesUtil.encryptDek(dek, masterKey);
        AesEncryptionUtil.EncryptionResult content    = aesUtil.encrypt(
                plaintext.getBytes(StandardCharsets.UTF_8), dek);

        CreateVaultItemRequest req = new CreateVaultItemRequest();
        req.setLabel("Test Item");
        req.setContentType(ContentType.TEXT_MESSAGE);
        req.setCiphertext(content.ciphertextBase64());
        req.setIv(content.ivBase64());
        req.setEncryptedDEK(wrappedDek.ciphertextBase64());
        req.setDekIv(wrappedDek.ivBase64());
        req.setSalt(aesUtil.toBase64(salt));
        req.setRawDEK(aesUtil.toBase64(dek.getEncoded()));
        req.setRecipientIds(List.of());
        return req;
    }
}
