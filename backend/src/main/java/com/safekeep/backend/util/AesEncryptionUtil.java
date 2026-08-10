package com.safekeep.backend.util;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.springframework.stereotype.Component;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Maximum-security AES-256-GCM encryption utility with envelope encryption.
 *
 * Key Derivation: Argon2id (RFC 9106)
 *   - Memory-hard: defeats GPU/ASIC brute-force attacks
 *   - Winner of Password Hashing Competition (2015)
 *   - Parameters: memory=65536 KB (64 MB), iterations=4, parallelism=2
 *   - These exceed OWASP 2024 minimum recommendations for Argon2id
 *
 * Symmetric Cipher: AES-256-GCM
 *   - 256-bit key, 96-bit IV, 128-bit authentication tag
 *   - Authenticated Encryption with Associated Data (AEAD)
 *   - Wrong key = GCM tag verification fails immediately - no plaintext leaked
 *
 * Envelope Encryption Design:
 *   1. User password  -> Argon2id(password, userSalt)   -> User Master Key (256-bit)
 *   2. Server secret  -> Argon2id(secret,  serverSalt)  -> Server Master Key (256-bit)
 *   3. Random 256-bit Data Encryption Key (DEK) generated per vault item
 *   4. DEK encrypted with User Master Key   -> User-encrypted DEK  (live vault access)
 *   5. DEK encrypted with Server Master Key -> Server-encrypted DEK (release path)
 *   6. Content/file encrypted with DEK
 *
 * DB compromise alone reveals nothing. Decryption requires either:
 *   (a) The user's password  - for live vault access, or
 *   (b) The server secret    - for scheduled release (never sent to client)
 */
@Component
@Slf4j
public class AesEncryptionUtil {

    private static final String ALGORITHM       = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM   = "AES";
    private static final int    GCM_IV_LENGTH   = 12;   // 96-bit IV - GCM standard
    private static final int    GCM_TAG_LENGTH  = 128;  // 128-bit authentication tag
    private static final int    KEY_LENGTH_BITS = 256;  // AES-256
    private static final int    SALT_LENGTH     = 32;   // 256-bit salt

    // Argon2id parameters - exceed OWASP 2024 minimum (m=46080, t=1, p=1)
    private static final int ARGON2_MEMORY_KB    = 65536; // 64 MB RAM cost per hash
    private static final int ARGON2_ITERATIONS   = 4;     // time cost
    private static final int ARGON2_PARALLELISM  = 2;     // lane parallelism
    private static final int ARGON2_OUTPUT_BYTES = 32;    // 256-bit derived key

    private final SecureRandom secureRandom = new SecureRandom();

    // ==================== KEY DERIVATION (Argon2id) ====================

    /**
     * Derives a 256-bit key from a password using Argon2id.
     * Uses 64 MB of RAM per derivation - a GPU cluster cannot meaningfully
     * speed up brute-force because each trial requires 64 MB of memory bandwidth.
     *
     * @param password the plaintext password (char[] to allow clearing after use)
     * @param salt     random 256-bit salt (unique per vault item)
     * @return 256-bit derived key bytes
     */
    public byte[] deriveKeyFromPassword(char[] password, byte[] salt) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withMemoryAsKB(ARGON2_MEMORY_KB)
                .withIterations(ARGON2_ITERATIONS)
                .withParallelism(ARGON2_PARALLELISM)
                .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);

        byte[] passwordBytes = toUtf8Bytes(password);
        byte[] result = new byte[ARGON2_OUTPUT_BYTES];
        try {
            generator.generateBytes(passwordBytes, result);
        } finally {
            Arrays.fill(passwordBytes, (byte) 0); // zero from memory immediately
        }
        return result;
    }

    /**
     * Derives a server master key from the server secret using Argon2id.
     * The serverSalt is stored per vault item so the derivation is unique per item.
     */
    public byte[] deriveServerKey(String serverSecret, byte[] serverSalt) {
        return deriveKeyFromPassword(serverSecret.toCharArray(), serverSalt);
    }

    /**
     * Generates a cryptographically random 256-bit Data Encryption Key (DEK).
     */
    public SecretKey generateDek() throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance(KEY_ALGORITHM);
        keyGen.init(KEY_LENGTH_BITS, secureRandom);
        return keyGen.generateKey();
    }

    /**
     * Generates a cryptographically random 256-bit salt (via SecureRandom).
     */
    public byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        return salt;
    }

    // ==================== ENCRYPTION (AES-256-GCM) ====================

    /**
     * Encrypts plaintext with AES-256-GCM.
     * The 128-bit GCM authentication tag is appended to the ciphertext by JCE automatically.
     *
     * @return EncryptionResult with Base64-encoded ciphertext (includes tag) and IV
     */
    public EncryptionResult encrypt(byte[] plaintext, SecretKey key) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);

        return new EncryptionResult(
                Base64.getEncoder().encodeToString(ciphertext),
                Base64.getEncoder().encodeToString(iv)
        );
    }

    /**
     * Encrypts a DEK's raw bytes with a master key.
     */
    public EncryptionResult encryptDek(SecretKey dek, byte[] masterKeyBytes) throws Exception {
        SecretKey masterKey = new SecretKeySpec(masterKeyBytes, KEY_ALGORITHM);
        return encrypt(dek.getEncoded(), masterKey);
    }

    // ==================== DECRYPTION (AES-256-GCM) ====================

    /**
     * Decrypts AES-256-GCM ciphertext.
     * If the key or IV is wrong, GCM throws AEADBadTagException before returning
     * any plaintext - no partial data is ever leaked for an invalid key.
     */
    public byte[] decrypt(String ciphertextBase64, String ivBase64, SecretKey key) throws Exception {
        byte[] ciphertext = Base64.getDecoder().decode(ciphertextBase64);
        byte[] iv         = Base64.getDecoder().decode(ivBase64);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return cipher.doFinal(ciphertext);
    }

    /**
     * Decrypts an encrypted DEK using master key bytes.
     * AEADBadTagException is thrown for any wrong key.
     */
    public SecretKey decryptDek(String encryptedDekBase64, String dekIvBase64, byte[] masterKeyBytes)
            throws Exception {
        SecretKey masterKey = new SecretKeySpec(masterKeyBytes, KEY_ALGORITHM);
        byte[] dekBytes = decrypt(encryptedDekBase64, dekIvBase64, masterKey);
        return new SecretKeySpec(dekBytes, KEY_ALGORITHM);
    }

    // ==================== HELPERS ====================

    public record EncryptionResult(String ciphertextBase64, String ivBase64) {}

    public String toBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public byte[] fromBase64(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    /**
     * Converts a char[] password to a UTF-8 byte[].
     * The caller MUST zero the returned array after use.
     */
    private byte[] toUtf8Bytes(char[] chars) {
        CharBuffer charBuffer = CharBuffer.wrap(chars);
        ByteBuffer byteBuffer = StandardCharsets.UTF_8.encode(charBuffer);
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        Arrays.fill(byteBuffer.array(), (byte) 0);
        return bytes;
    }
}
