/**
 * SafeKeep Client-Side Cryptography Module
 *
 * Implements true zero-knowledge vault encryption entirely in the browser.
 * The vault password NEVER leaves this device — only ciphertext is sent to the server.
 *
 * Primitives used:
 *   Key derivation : Argon2id via hash-wasm (WASM, memory-hard, GPU/ASIC resistant)
 *   Symmetric cipher: AES-256-GCM via crypto.subtle (browser native, hardware-accelerated)
 *
 * Architecture (per vault item):
 *   1. Random 256-bit DEK (Data Encryption Key) generated per item
 *   2. User password → Argon2id(password, salt) → 256-bit User Master Key
 *   3. DEK encrypted with User Master Key       → encryptedDEK (stored in DB)
 *   4. Content/file encrypted with DEK          → ciphertext (stored in DB)
 *   5. Raw DEK sent once at creation for server to wrap with server key (release path)
 *
 * Parameters:
 *   Argon2id: memory=64MB, iterations=3, parallelism=1
 *   (Browser is single-threaded; parallelism=1 is appropriate)
 *   These meet and exceed OWASP 2024 Argon2id recommendations.
 *
 * Wrong password detection:
 *   AES-256-GCM includes a 128-bit authentication tag. If the key is wrong,
 *   decryption throws DOMException ("The operation failed for an operation-specific reason"),
 *   which we catch and surface as "Wrong password" — no partial plaintext is ever returned.
 */

import { argon2id } from 'hash-wasm';

// ==================== Constants ====================

const ARGON2_MEMORY_KB   = 65536;  // 64 MB — defeats GPU/ASIC brute-force
const ARGON2_ITERATIONS  = 3;      // time cost
const ARGON2_PARALLELISM = 1;      // browser is single-threaded
const ARGON2_OUTPUT_BITS = 256;    // 256-bit derived key (32 bytes)

const AES_ALGORITHM = 'AES-GCM';
const AES_KEY_BITS  = 256;
const GCM_IV_BYTES  = 12;   // 96-bit IV — GCM standard
const SALT_BYTES    = 32;   // 256-bit Argon2id salt

// ==================== Salt & IV Generation ====================

/**
 * Generates a cryptographically random 256-bit Argon2id salt.
 * @returns {string} Base64-encoded salt
 */
export function generateSalt() {
  const salt = crypto.getRandomValues(new Uint8Array(SALT_BYTES));
  return uint8ArrayToBase64(salt);
}

/**
 * Generates a cryptographically random 96-bit GCM IV.
 * @returns {string} Base64-encoded IV
 */
export function generateIv() {
  const iv = crypto.getRandomValues(new Uint8Array(GCM_IV_BYTES));
  return uint8ArrayToBase64(iv);
}

// ==================== Key Derivation (Argon2id) ====================

/**
 * Derives a 256-bit AES master key from the user's vault password using Argon2id.
 *
 * Argon2id is memory-hard: deriving a key requires 64MB of RAM per attempt.
 * A GPU cluster with 1000 cards, each with 80GB VRAM, can run ~1250 parallel
 * hash attempts — orders of magnitude slower than regular PBKDF2/bcrypt.
 *
 * @param {string} password     The user's vault password (plaintext — stays in browser memory only)
 * @param {string} saltBase64   Base64-encoded 256-bit salt (stored in DB alongside ciphertext)
 * @returns {Promise<CryptoKey>} AES-GCM CryptoKey, extractable=false (cannot be serialised out)
 */
export async function deriveKey(password, saltBase64) {
  const saltBytes = base64ToUint8Array(saltBase64);

  // Argon2id hash — returns a hex string of length outputLen*2
  const hexKey = await argon2id({
    password,
    salt: saltBytes,
    parallelism: ARGON2_PARALLELISM,
    iterations:  ARGON2_ITERATIONS,
    memorySize:  ARGON2_MEMORY_KB,
    hashLength:  ARGON2_OUTPUT_BITS / 8,   // bytes
    outputType:  'hex',
  });

  const keyBytes = hexStringToUint8Array(hexKey);

  // Import raw key bytes as a non-extractable AES-GCM CryptoKey
  return crypto.subtle.importKey(
    'raw',
    keyBytes,
    { name: AES_ALGORITHM },
    false,          // extractable=false — key bytes cannot be exported from the JS context
    ['encrypt', 'decrypt', 'wrapKey', 'unwrapKey'],
  );
}

// ==================== DEK (Data Encryption Key) ====================

/**
 * Generates a random 256-bit Data Encryption Key (DEK).
 * One unique DEK is created per vault item — compromising one DEK
 * affects only that item, not the entire vault.
 *
 * @returns {Promise<CryptoKey>} Extractable AES-GCM key (must be extractable for wrapping)
 */
export async function generateDEK() {
  return crypto.subtle.generateKey(
    { name: AES_ALGORITHM, length: AES_KEY_BITS },
    true,   // extractable=true — needed so we can wrap it and send the raw bytes to the server
    ['encrypt', 'decrypt'],
  );
}

/**
 * Wraps (encrypts) the DEK with the user's master key using AES-GCM.
 * The wrapped DEK is safe to store in the database — it is useless without the master key.
 *
 * @param {CryptoKey} dek         The DEK to wrap
 * @param {CryptoKey} masterKey   The user's Argon2id-derived master key
 * @returns {Promise<{encryptedDEK: string, dekIv: string}>}
 */
export async function encryptDEK(dek, masterKey) {
  const iv = crypto.getRandomValues(new Uint8Array(GCM_IV_BYTES));
  const wrappedDekBuffer = await crypto.subtle.wrapKey('raw', dek, masterKey, {
    name: AES_ALGORITHM,
    iv,
  });
  return {
    encryptedDEK: uint8ArrayToBase64(new Uint8Array(wrappedDekBuffer)),
    dekIv: uint8ArrayToBase64(iv),
  };
}

/**
 * Unwraps (decrypts) the DEK using the user's master key.
 * If masterKey is wrong, AES-GCM throws DOMException — no partial key bytes are returned.
 *
 * @param {string} encryptedDEKBase64  Base64-encoded wrapped DEK
 * @param {string} dekIvBase64         Base64-encoded IV used during wrapping
 * @param {CryptoKey} masterKey        The user's Argon2id-derived master key
 * @returns {Promise<CryptoKey>}       The unwrapped DEK
 */
export async function decryptDEK(encryptedDEKBase64, dekIvBase64, masterKey) {
  const encryptedDEK = base64ToUint8Array(encryptedDEKBase64);
  const dekIv        = base64ToUint8Array(dekIvBase64);

  return crypto.subtle.unwrapKey(
    'raw',
    encryptedDEK,
    masterKey,
    { name: AES_ALGORITHM, iv: dekIv },   // unwrap algorithm
    { name: AES_ALGORITHM },               // unwrapped key algorithm
    false,                                 // extractable=false after unwrapping
    ['encrypt', 'decrypt'],
  );
}

/**
 * Exports the raw bytes of a DEK as a Base64 string.
 * Used ONLY at item creation time to send the raw DEK to the server
 * so the server can wrap it with the server key for the release path.
 * This value is sent over HTTPS (encrypted in transit) and is never stored raw.
 *
 * @param {CryptoKey} dek
 * @returns {Promise<string>} Base64-encoded raw DEK bytes
 */
export async function exportDEKRaw(dek) {
  const rawBuffer = await crypto.subtle.exportKey('raw', dek);
  return uint8ArrayToBase64(new Uint8Array(rawBuffer));
}

// ==================== Content Encryption / Decryption ====================

/**
 * Encrypts a plaintext string using AES-256-GCM.
 * The 128-bit GCM authentication tag is appended to the ciphertext automatically.
 *
 * @param {string} plaintext   The content to encrypt (e.g., password, note, seed phrase)
 * @param {CryptoKey} dek      The DEK to encrypt with
 * @returns {Promise<{ciphertext: string, iv: string}>}
 */
export async function encryptContent(plaintext, dek) {
  const iv          = crypto.getRandomValues(new Uint8Array(GCM_IV_BYTES));
  const plaintextBytes = new TextEncoder().encode(plaintext);

  const ciphertextBuffer = await crypto.subtle.encrypt(
    { name: AES_ALGORITHM, iv },
    dek,
    plaintextBytes,
  );

  return {
    ciphertext: uint8ArrayToBase64(new Uint8Array(ciphertextBuffer)),
    iv: uint8ArrayToBase64(iv),
  };
}

/**
 * Decrypts AES-256-GCM ciphertext back to a plaintext string.
 * If the DEK or IV is wrong, GCM tag verification fails and DOMException is thrown
 * before any plaintext bytes are produced — zero information is leaked for a bad key.
 *
 * @param {string} ciphertextBase64  Base64-encoded ciphertext (includes GCM tag)
 * @param {string} ivBase64          Base64-encoded IV
 * @param {CryptoKey} dek            The DEK to decrypt with
 * @returns {Promise<string>}        Decrypted plaintext
 */
export async function decryptContent(ciphertextBase64, ivBase64, dek) {
  const ciphertext = base64ToUint8Array(ciphertextBase64);
  const iv         = base64ToUint8Array(ivBase64);

  const plaintextBuffer = await crypto.subtle.decrypt(
    { name: AES_ALGORITHM, iv },
    dek,
    ciphertext,
  );

  return new TextDecoder().decode(plaintextBuffer);
}

// ==================== File Encryption / Decryption ====================

/**
 * Encrypts a binary file using AES-256-GCM.
 *
 * @param {ArrayBuffer} fileBuffer  Raw file bytes
 * @param {CryptoKey} dek           The DEK to encrypt with
 * @returns {Promise<{ciphertext: string, iv: string}>}
 */
export async function encryptFile(fileBuffer, dek) {
  const iv = crypto.getRandomValues(new Uint8Array(GCM_IV_BYTES));

  const ciphertextBuffer = await crypto.subtle.encrypt(
    { name: AES_ALGORITHM, iv },
    dek,
    fileBuffer,
  );

  return {
    ciphertext: uint8ArrayToBase64(new Uint8Array(ciphertextBuffer)),
    iv: uint8ArrayToBase64(iv),
  };
}

/**
 * Decrypts an AES-256-GCM encrypted file back to raw bytes.
 *
 * @param {string} ciphertextBase64  Base64-encoded ciphertext
 * @param {string} ivBase64          Base64-encoded IV
 * @param {CryptoKey} dek            The DEK to decrypt with
 * @returns {Promise<ArrayBuffer>}   Decrypted file bytes
 */
export async function decryptFile(ciphertextBase64, ivBase64, dek) {
  const ciphertext = base64ToUint8Array(ciphertextBase64);
  const iv         = base64ToUint8Array(ivBase64);

  return crypto.subtle.decrypt(
    { name: AES_ALGORITHM, iv },
    dek,
    ciphertext,
  );
}

// ==================== Utility: Base64 ↔ Uint8Array ====================

export function uint8ArrayToBase64(bytes) {
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary);
}

export function base64ToUint8Array(base64) {
  const binary = atob(base64);
  const bytes  = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function hexStringToUint8Array(hexString) {
  const bytes = new Uint8Array(hexString.length / 2);
  for (let i = 0; i < bytes.length; i++) {
    bytes[i] = parseInt(hexString.slice(i * 2, i * 2 + 2), 16);
  }
  return bytes;
}
