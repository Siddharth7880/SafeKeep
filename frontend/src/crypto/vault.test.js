/**
 * Vitest unit tests for frontend/src/crypto/vault.js
 *
 * Tests cover:
 *   - deriveKey: determinism across calls, fails with wrong salt, 256-bit output
 *   - generateSalt: uniqueness
 *   - generateDEK: uniqueness
 *   - encryptContent / decryptContent: round-trip, wrong-password rejection
 *   - encryptFile / decryptFile: binary round-trip, wrong-key rejection
 *   - DEK envelope: encryptDEK / decryptDEK round-trip, wrong-key rejection
 *   - exportDEKRaw: returns 32 bytes
 *
 * Run with: npm test (or npx vitest)
 * Requires: vitest with jsdom environment so WebCrypto is available.
 */

import { describe, it, expect, beforeAll } from 'vitest';
import {
  generateSalt,
  generateIv,
  deriveKey,
  generateDEK,
  encryptDEK,
  decryptDEK,
  encryptContent,
  decryptContent,
  encryptFile,
  decryptFile,
  exportDEKRaw,
  uint8ArrayToBase64,
  base64ToUint8Array,
} from './vault.js';

// ==================== deriveKey ====================

describe('deriveKey', () => {
  it('is deterministic: same password + salt always produces the same CryptoKey', async () => {
    const salt = generateSalt();
    // We can't compare CryptoKeys directly, but we can test that the DEK encrypted
    // with key1 can be decrypted with key2 (proving they are identical)
    const dek  = await generateDEK();
    const key1 = await deriveKey('my-password', salt);
    const key2 = await deriveKey('my-password', salt);

    const { encryptedDEK, dekIv } = await encryptDEK(dek, key1);
    // If key2 === key1, unwrap must succeed
    await expect(decryptDEK(encryptedDEK, dekIv, key2)).resolves.toBeDefined();
  });

  it('produces different keys for different salts', async () => {
    const password = 'same-password';
    const salt1    = generateSalt();
    const salt2    = generateSalt();
    expect(salt1).not.toEqual(salt2); // sanity

    const dek  = await generateDEK();
    const key1 = await deriveKey(password, salt1);
    const key2 = await deriveKey(password, salt2);

    const { encryptedDEK, dekIv } = await encryptDEK(dek, key1);
    // key2 was derived from salt2 — decrypting with it must fail (GCM tag mismatch)
    await expect(decryptDEK(encryptedDEK, dekIv, key2)).rejects.toThrow();
  });

  it('produces different keys for different passwords', async () => {
    const salt = generateSalt();
    const dek  = await generateDEK();
    const key1 = await deriveKey('password-one', salt);
    const key2 = await deriveKey('password-two', salt);

    const { encryptedDEK, dekIv } = await encryptDEK(dek, key1);
    await expect(decryptDEK(encryptedDEK, dekIv, key2)).rejects.toThrow();
  });
});

// ==================== generateSalt ====================

describe('generateSalt', () => {
  it('produces a 44-char Base64 string (32 bytes encoded)', () => {
    const salt = generateSalt();
    expect(typeof salt).toBe('string');
    // 32 bytes → 44 chars in Base64 (with padding)
    expect(base64ToUint8Array(salt)).toHaveLength(32);
  });

  it('produces unique salts on each call', () => {
    const s1 = generateSalt();
    const s2 = generateSalt();
    expect(s1).not.toEqual(s2);
  });
});

// ==================== generateIv ====================

describe('generateIv', () => {
  it('produces a 12-byte (96-bit) IV', () => {
    const iv = generateIv();
    expect(base64ToUint8Array(iv)).toHaveLength(12);
  });

  it('produces unique IVs on each call', () => {
    expect(generateIv()).not.toEqual(generateIv());
  });
});

// ==================== generateDEK ====================

describe('generateDEK', () => {
  it('returns a CryptoKey', async () => {
    const dek = await generateDEK();
    expect(dek).toBeInstanceOf(CryptoKey);
  });

  it('raw export is 32 bytes (256-bit AES key)', async () => {
    const dek    = await generateDEK();
    const rawB64 = await exportDEKRaw(dek);
    expect(base64ToUint8Array(rawB64)).toHaveLength(32);
  });

  it('produces unique DEKs on each call', async () => {
    const raw1 = await exportDEKRaw(await generateDEK());
    const raw2 = await exportDEKRaw(await generateDEK());
    expect(raw1).not.toEqual(raw2);
  });
});

// ==================== encryptContent / decryptContent ====================

describe('encryptContent / decryptContent', () => {
  it('round-trip: encrypt then decrypt returns original plaintext', async () => {
    const dek       = await generateDEK();
    const plaintext = 'My super secret seed phrase: apple banana cherry';
    const { ciphertext, iv } = await encryptContent(plaintext, dek);

    expect(ciphertext).not.toContain(plaintext); // sanity check
    const decrypted = await decryptContent(ciphertext, iv, dek);
    expect(decrypted).toBe(plaintext);
  });

  it('wrong password (wrong DEK): decryption throws before returning any plaintext', async () => {
    const correctDek = await generateDEK();
    const wrongDek   = await generateDEK();
    const { ciphertext, iv } = await encryptContent('secret', correctDek);

    await expect(decryptContent(ciphertext, iv, wrongDek)).rejects.toThrow();
  });

  it('wrong IV: decryption throws (GCM tag covers IV commitment)', async () => {
    const dek = await generateDEK();
    const { ciphertext } = await encryptContent('secret', dek);
    const wrongIv = generateIv(); // different random IV

    await expect(decryptContent(ciphertext, wrongIv, dek)).rejects.toThrow();
  });

  it('empty string round-trips cleanly', async () => {
    const dek = await generateDEK();
    const { ciphertext, iv } = await encryptContent('', dek);
    const decrypted = await decryptContent(ciphertext, iv, dek);
    expect(decrypted).toBe('');
  });

  it('multi-line content with unicode round-trips correctly', async () => {
    const dek     = await generateDEK();
    const content = 'Line 1\nLine 2\nEmoji: 🔐\nChinese: 你好';
    const { ciphertext, iv } = await encryptContent(content, dek);
    expect(await decryptContent(ciphertext, iv, dek)).toBe(content);
  });
});

// ==================== encryptFile / decryptFile ====================

describe('encryptFile / decryptFile', () => {
  it('binary file round-trip: encrypted then decrypted bytes are identical', async () => {
    const dek      = await generateDEK();
    const original = new Uint8Array(1024);
    crypto.getRandomValues(original);
    const buffer   = original.buffer;

    const { ciphertext, iv } = await encryptFile(buffer, dek);
    const decrypted = await decryptFile(ciphertext, iv, dek);
    expect(new Uint8Array(decrypted)).toEqual(original);
  });

  it('wrong DEK: file decryption throws', async () => {
    const correctDek = await generateDEK();
    const wrongDek   = await generateDEK();
    const buffer     = new ArrayBuffer(256);
    const { ciphertext, iv } = await encryptFile(buffer, correctDek);

    await expect(decryptFile(ciphertext, iv, wrongDek)).rejects.toThrow();
  });
});

// ==================== DEK envelope ====================

describe('encryptDEK / decryptDEK', () => {
  it('round-trip: wrapped then unwrapped DEK produces same key', async () => {
    const password = 'vault-password';
    const salt     = generateSalt();
    const dek      = await generateDEK();
    const masterKey = await deriveKey(password, salt);

    const { encryptedDEK, dekIv } = await encryptDEK(dek, masterKey);
    const unwrappedDek = await decryptDEK(encryptedDEK, dekIv, masterKey);

    // Verify the unwrapped DEK is functionally identical by using it to decrypt
    const { ciphertext, iv } = await encryptContent('test', dek);
    const decrypted = await decryptContent(ciphertext, iv, unwrappedDek);
    expect(decrypted).toBe('test');
  });

  it('wrong master key: unwrap throws (GCM tag failure — no partial DEK bytes returned)', async () => {
    const dek        = await generateDEK();
    const correctKey = await deriveKey('correct-password', generateSalt());
    const wrongKey   = await deriveKey('wrong-password', generateSalt());

    const { encryptedDEK, dekIv } = await encryptDEK(dek, correctKey);
    await expect(decryptDEK(encryptedDEK, dekIv, wrongKey)).rejects.toThrow();
  });
});

// ==================== Full vault item simulation ====================

describe('Full vault item simulation', () => {
  it('simulates browser ZK flow: wrong password fails, correct password succeeds', async () => {
    const correctPassword = 'correct-vault-password-123';
    const wrongPassword   = 'completely-wrong-password';
    const plaintext       = 'Bitcoin seed: abandon art zoo';

    // Create phase (browser before upload)
    const salt      = generateSalt();
    const dek       = await generateDEK();
    const masterKey = await deriveKey(correctPassword, salt);
    const { encryptedDEK, dekIv }     = await encryptDEK(dek, masterKey);
    const { ciphertext, iv }          = await encryptContent(plaintext, dek);

    // Simulated server storage (only encrypted data)
    const stored = { ciphertext, iv, encryptedDEK, dekIv, salt };

    // Wrong password attempt
    const wrongMasterKey = await deriveKey(wrongPassword, stored.salt);
    await expect(decryptDEK(stored.encryptedDEK, stored.dekIv, wrongMasterKey)).rejects.toThrow();

    // Correct password attempt
    const correctMasterKey = await deriveKey(correctPassword, stored.salt);
    const unwrappedDek = await decryptDEK(stored.encryptedDEK, stored.dekIv, correctMasterKey);
    const decrypted    = await decryptContent(stored.ciphertext, stored.iv, unwrappedDek);
    expect(decrypted).toBe(plaintext);
  });
});
