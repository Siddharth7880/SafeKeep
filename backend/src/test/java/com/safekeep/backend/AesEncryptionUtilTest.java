package com.safekeep.backend;

import com.safekeep.backend.util.AesEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for AesEncryptionUtil.
 *
 * Tests cover:
 *   - AES-256-GCM encrypt/decrypt round-trips
 *   - Wrong key rejection (AEADBadTagException — no partial plaintext leaked)
 *   - DEK envelope: encrypt DEK, decrypt DEK, wrong key rejection
 *   - Argon2id key derivation: determinism, uniqueness across salts
 *   - Salt uniqueness: two calls never produce the same salt
 */
@DisplayName("AesEncryptionUtil — AES-256-GCM + Argon2id tests")
class AesEncryptionUtilTest {

    private AesEncryptionUtil util;

    @BeforeEach
    void setUp() {
        util = new AesEncryptionUtil();
    }

    // ==================== Encrypt / Decrypt Round-Trips ====================

    @Test
    @DisplayName("Content round-trip: encrypt then decrypt returns original plaintext")
    void encryptDecryptRoundTrip() throws Exception {
        SecretKey key        = util.generateDek();
        String    plaintext  = "Super secret seed phrase: apple banana cherry";
        byte[]    inputBytes = plaintext.getBytes(StandardCharsets.UTF_8);

        AesEncryptionUtil.EncryptionResult result = util.encrypt(inputBytes, key);

        assertThat(result.ciphertextBase64()).isNotBlank();
        assertThat(result.ivBase64()).isNotBlank();
        assertThat(result.ciphertextBase64()).doesNotContain(plaintext); // sanity: ciphertext != plaintext

        byte[] decrypted = util.decrypt(result.ciphertextBase64(), result.ivBase64(), key);
        assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Binary data round-trip: encrypt then decrypt returns identical bytes")
    void binaryRoundTrip() throws Exception {
        SecretKey key      = util.generateDek();
        byte[]    original = new byte[1024];
        new java.security.SecureRandom().nextBytes(original);

        AesEncryptionUtil.EncryptionResult result = util.encrypt(original, key);
        byte[] decrypted = util.decrypt(result.ciphertextBase64(), result.ivBase64(), key);

        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    @DisplayName("Wrong key: decrypt throws AEADBadTagException before returning any bytes")
    void wrongKeyThrowsBeforeReturningPlaintext() throws Exception {
        SecretKey correctKey = util.generateDek();
        SecretKey wrongKey   = util.generateDek();
        byte[]    plaintext  = "Top secret".getBytes(StandardCharsets.UTF_8);

        AesEncryptionUtil.EncryptionResult result = util.encrypt(plaintext, correctKey);

        // GCM authentication tag verification fails — no plaintext ever produced
        assertThatThrownBy(() -> util.decrypt(result.ciphertextBase64(), result.ivBase64(), wrongKey))
                .isInstanceOfAny(AEADBadTagException.class, javax.crypto.BadPaddingException.class,
                        java.security.GeneralSecurityException.class);
    }

    @Test
    @DisplayName("Tampered ciphertext: decrypt throws on integrity violation")
    void tamperedCiphertextThrows() throws Exception {
        SecretKey key = util.generateDek();
        AesEncryptionUtil.EncryptionResult result =
                util.encrypt("Sensitive data".getBytes(StandardCharsets.UTF_8), key);

        // Corrupt one byte in the ciphertext
        byte[] ciphertextBytes = java.util.Base64.getDecoder().decode(result.ciphertextBase64());
        ciphertextBytes[0] ^= 0xFF;
        String tamperedBase64 = java.util.Base64.getEncoder().encodeToString(ciphertextBytes);

        assertThatThrownBy(() -> util.decrypt(tamperedBase64, result.ivBase64(), key))
                .isInstanceOfAny(AEADBadTagException.class, javax.crypto.BadPaddingException.class,
                        java.security.GeneralSecurityException.class);
    }

    // ==================== DEK Envelope ====================

    @Test
    @DisplayName("DEK envelope: encryptDek / decryptDek round-trip returns identical key")
    void dekEnvelopeRoundTrip() throws Exception {
        SecretKey dek           = util.generateDek();
        byte[]    salt          = util.generateSalt();
        byte[]    masterKeyBytes = util.deriveKeyFromPassword("vault-password".toCharArray(), salt);

        AesEncryptionUtil.EncryptionResult wrapped = util.encryptDek(dek, masterKeyBytes);
        SecretKey                          unwrapped = util.decryptDek(
                wrapped.ciphertextBase64(), wrapped.ivBase64(), masterKeyBytes);

        assertThat(unwrapped.getEncoded()).isEqualTo(dek.getEncoded());
    }

    @Test
    @DisplayName("DEK envelope: wrong master key throws on unwrap — GCM tag fail")
    void dekEnvelopeWrongKeyThrows() throws Exception {
        SecretKey dek             = util.generateDek();
        byte[]    salt            = util.generateSalt();
        byte[]    correctMasterKey = util.deriveKeyFromPassword("correct-password".toCharArray(), salt);
        byte[]    wrongMasterKey   = util.deriveKeyFromPassword("wrong-password".toCharArray(), salt);

        AesEncryptionUtil.EncryptionResult wrapped = util.encryptDek(dek, correctMasterKey);

        assertThatThrownBy(() -> util.decryptDek(wrapped.ciphertextBase64(), wrapped.ivBase64(), wrongMasterKey))
                .isInstanceOfAny(AEADBadTagException.class, javax.crypto.BadPaddingException.class,
                        java.security.GeneralSecurityException.class);
    }

    // ==================== Argon2id Key Derivation ====================

    @Test
    @DisplayName("Argon2id: same password + salt always produces the same key (deterministic)")
    void argon2idIsDeterministic() throws Exception {
        char[] password = "my-vault-password".toCharArray();
        byte[] salt     = util.generateSalt();

        byte[] key1 = util.deriveKeyFromPassword(password, salt);
        byte[] key2 = util.deriveKeyFromPassword(password, salt);

        assertThat(key1).isEqualTo(key2);
        assertThat(key1).hasSize(32); // 256-bit
    }

    @Test
    @DisplayName("Argon2id: different salts produce different keys (salt uniqueness matters)")
    void differentSaltsProduceDifferentKeys() throws Exception {
        char[] password = "my-vault-password".toCharArray();
        byte[] salt1    = util.generateSalt();
        byte[] salt2    = util.generateSalt();

        byte[] key1 = util.deriveKeyFromPassword(password, salt1);
        byte[] key2 = util.deriveKeyFromPassword(password, salt2);

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    @DisplayName("Argon2id: different passwords produce different keys")
    void differentPasswordsProduceDifferentKeys() throws Exception {
        byte[] salt = util.generateSalt();

        byte[] key1 = util.deriveKeyFromPassword("password-one".toCharArray(), salt);
        byte[] key2 = util.deriveKeyFromPassword("password-two".toCharArray(), salt);

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    @DisplayName("Argon2id: output is 256 bits (32 bytes) as required for AES-256")
    void argon2idOutputIs256Bits() throws Exception {
        byte[] key = util.deriveKeyFromPassword("any-password".toCharArray(), util.generateSalt());
        assertThat(key).hasSize(32);
    }

    // ==================== Salt Generation ====================

    @Test
    @DisplayName("generateSalt: produces 32-byte (256-bit) random output")
    void saltIs256Bits() {
        byte[] salt = util.generateSalt();
        assertThat(salt).hasSize(32);
    }

    @Test
    @DisplayName("generateSalt: two successive calls produce different salts")
    void saltIsUnique() {
        byte[] salt1 = util.generateSalt();
        byte[] salt2 = util.generateSalt();
        assertThat(salt1).isNotEqualTo(salt2);
    }

    // ==================== DEK Generation ====================

    @Test
    @DisplayName("generateDek: produces a 256-bit AES key")
    void dekIs256Bits() throws Exception {
        SecretKey dek = util.generateDek();
        assertThat(dek.getAlgorithm()).isEqualTo("AES");
        assertThat(dek.getEncoded()).hasSize(32); // 256-bit
    }

    @Test
    @DisplayName("generateDek: two successive calls produce different keys")
    void deksAreUnique() throws Exception {
        SecretKey dek1 = util.generateDek();
        SecretKey dek2 = util.generateDek();
        assertThat(dek1.getEncoded()).isNotEqualTo(dek2.getEncoded());
    }

    // ==================== Full Vault Item Encryption Simulation ====================

    @Test
    @DisplayName("Full vault item simulation: create → verify wrong password fails → correct password succeeds")
    void fullVaultItemSimulation() throws Exception {
        // Simulate what the browser does
        String correctPassword = "my-super-secret-vault-password";
        String wrongPassword   = "wrong-password";
        String plaintext       = "Bitcoin seed: abandon abandon abandon ... art";

        byte[] salt           = util.generateSalt();
        byte[] masterKeyBytes  = util.deriveKeyFromPassword(correctPassword.toCharArray(), salt);
        SecretKey dek         = util.generateDek();
        AesEncryptionUtil.EncryptionResult wrappedDek = util.encryptDek(dek, masterKeyBytes);
        AesEncryptionUtil.EncryptionResult encrypted  = util.encrypt(
                plaintext.getBytes(StandardCharsets.UTF_8), dek);

        // Simulate what the server stores (only ciphertext, wrappedDek, salt)
        String storedCiphertext    = encrypted.ciphertextBase64();
        String storedIv            = encrypted.ivBase64();
        String storedEncryptedDek  = wrappedDek.ciphertextBase64();
        String storedDekIv         = wrappedDek.ivBase64();
        String storedSalt          = util.toBase64(salt);

        // Wrong password attempt — must throw, not return garbage
        byte[] wrongSalt = util.fromBase64(storedSalt);
        byte[] wrongMasterKey = util.deriveKeyFromPassword(wrongPassword.toCharArray(), wrongSalt);
        assertThatThrownBy(() ->
                util.decryptDek(storedEncryptedDek, storedDekIv, wrongMasterKey))
                .isInstanceOfAny(AEADBadTagException.class, javax.crypto.BadPaddingException.class,
                        java.security.GeneralSecurityException.class);

        // Correct password — must succeed and return original plaintext
        byte[] correctSalt        = util.fromBase64(storedSalt);
        byte[] correctMasterKey   = util.deriveKeyFromPassword(correctPassword.toCharArray(), correctSalt);
        SecretKey decryptedDek    = util.decryptDek(storedEncryptedDek, storedDekIv, correctMasterKey);
        byte[] decryptedContent   = util.decrypt(storedCiphertext, storedIv, decryptedDek);
        assertThat(new String(decryptedContent, StandardCharsets.UTF_8)).isEqualTo(plaintext);
    }
}
