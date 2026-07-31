package com.safekeep.backend.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * AES-256-GCM encryption utility with envelope encryption support.
 *
 * Envelope Encryption Design:
 *   1. User password → PBKDF2(SHA-256, 310,000 iterations, random salt) → Master Key (256-bit)
 *   2. Random Data Encryption Key (DEK) generated per vault item
 *   3. DEK encrypted with Master Key → Encrypted DEK (stored in DB)
 *   4. Content encrypted with DEK → Encrypted Content (stored in DB)
 *
 * Result: Even if DB is compromised, content is unreadable without the user's password.
 */
@Component
@Slf4j
public class AesEncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int GCM_IV_LENGTH = 12;    // 96-bit IV (GCM standard)
    private static final int GCM_TAG_LENGTH = 128;  // 128-bit authentication tag
    private static final int KEY_LENGTH = 256;      // AES-256
    private static final int SALT_LENGTH = 32;      // 256-bit salt for PBKDF2
    private static final int PBKDF2_ITERATIONS = 310_000;  // OWASP recommended minimum

    private final SecureRandom secureRandom = new SecureRandom();

    // ==================== KEY DERIVATION ====================

    /**
     * Derives a 256-bit master key from a password using PBKDF2-SHA256.
     * @param password user's password (char[] to allow clearing after use)
     * @param salt random salt (Base64 encoded)
     * @return 256-bit key as byte[]
     */
    public byte[] deriveKeyFromPassword(char[] password, byte[] salt)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return factory.generateSecret(spec).getEncoded();
    }

    /**
     * Generates a cryptographically random 256-bit Data Encryption Key.
     */
    public SecretKey generateDek() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance(KEY_ALGORITHM);
        keyGen.init(KEY_LENGTH, secureRandom);
        return keyGen.generateKey();
    }

    /**
     * Generates a random salt for PBKDF2.
     */
    public byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        return salt;
    }

    // ==================== ENCRYPTION ====================

    /**
     * Encrypts plaintext with AES-256-GCM.
     * @return EncryptionResult containing Base64-encoded ciphertext and IV
     */
    public EncryptionResult encrypt(byte[] plaintext, SecretKey key)
            throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

        byte[] ciphertext = cipher.doFinal(plaintext);

        return new EncryptionResult(
                Base64.getEncoder().encodeToString(ciphertext),
                Base64.getEncoder().encodeToString(iv)
        );
    }

    /**
     * Encrypts a DEK (Data Encryption Key) with the master key.
     */
    public EncryptionResult encryptDek(SecretKey dek, byte[] masterKeyBytes) throws Exception {
        SecretKey masterKey = new SecretKeySpec(masterKeyBytes, KEY_ALGORITHM);
        return encrypt(dek.getEncoded(), masterKey);
    }

    // ==================== DECRYPTION ====================

    /**
     * Decrypts AES-256-GCM ciphertext.
     */
    public byte[] decrypt(String ciphertextBase64, String ivBase64, SecretKey key)
            throws Exception {
        byte[] ciphertext = Base64.getDecoder().decode(ciphertextBase64);
        byte[] iv = Base64.getDecoder().decode(ivBase64);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

        return cipher.doFinal(ciphertext);
    }

    /**
     * Decrypts an encrypted DEK using the master key.
     */
    public SecretKey decryptDek(String encryptedDekBase64, String dekIvBase64, byte[] masterKeyBytes)
            throws Exception {
        SecretKey masterKey = new SecretKeySpec(masterKeyBytes, KEY_ALGORITHM);
        byte[] dekBytes = decrypt(encryptedDekBase64, dekIvBase64, masterKey);
        return new SecretKeySpec(dekBytes, KEY_ALGORITHM);
    }

    // ==================== HELPER ====================

    public record EncryptionResult(String ciphertextBase64, String ivBase64) {}

    public String toBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public byte[] fromBase64(String base64) {
        return Base64.getDecoder().decode(base64);
    }
}
