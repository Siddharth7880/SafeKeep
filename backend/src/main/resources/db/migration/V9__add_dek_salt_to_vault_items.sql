-- Add PBKDF2 salt column for user-password-based DEK encryption.
-- Existing rows get a placeholder; they were encrypted with the server secret
-- and will fail to decrypt (expected — re-encryption would require the old password).
ALTER TABLE vault_items ADD COLUMN dek_salt VARCHAR(64);
