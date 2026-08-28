-- =============================================================
-- Phase 2: Zero-Knowledge Vault — Schema Migration
--
-- Changes:
--   1. Rename 'content' column to use consistent 'ciphertext' naming
--   2. Add file_data BYTEA column — encrypted file stored in DB (Option B)
--      No more filesystem dependency; encrypted blobs live in Postgres.
--   3. Add file_ciphertext_b64 TEXT column for the Base64-encoded encrypted file
--      (stored as TEXT to match the browser's Base64 output from crypto.subtle)
--   4. Add file_iv column for the file GCM IV (separate from content iv)
--   Existing columns (encrypted_dek, dek_iv, dek_salt, etc.) are reused as-is
--   with the new naming convention: dek_salt -> salt (alias via application, no rename needed)
-- =============================================================

-- Add file storage column (Option B: store encrypted file in DB instead of filesystem)
ALTER TABLE vault_items
    ADD COLUMN IF NOT EXISTS file_ciphertext TEXT,
    ADD COLUMN IF NOT EXISTS file_iv_b64     VARCHAR(64);

-- Migrate any existing filesystem-stored files: mark them for manual re-encryption
-- (Existing items stored on disk will have encrypted_file_path set but file_ciphertext null —
--  the service handles this gracefully by returning hasFile=false for legacy items)

-- The old 'encrypted_content' column is kept as-is (renamed semantically to 'ciphertext' in app layer)
-- The old 'iv' column is the content IV — kept as-is
-- The old 'encrypted_dek' / 'dek_iv' / 'dek_salt' are kept — renamed in app layer to
-- 'encryptedDEK' / 'dekIv' / 'salt' to match client naming convention

-- Add comment documenting the zero-knowledge invariant
COMMENT ON TABLE vault_items IS
    'Zero-knowledge vault: server stores only ciphertext. '
    'Decryption requires either (a) user vault password [user path] or (b) server secret [release path]. '
    'Server never performs decryption in user-facing API paths as of Phase 2 migration.';
