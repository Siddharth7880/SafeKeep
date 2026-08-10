-- V10: Add server-side DEK columns (for ContentReleaseJob) and fix encrypted_content nullability.
--
-- encrypted_dek_server / dek_iv_server / dek_salt_server:
--   Stores a second copy of the DEK encrypted with Argon2id(serverSecret, dekSaltServer).
--   Used by ContentReleaseJob to decrypt vault content without the user's password.
--
-- encrypted_content nullability fix:
--   V1 declared this NOT NULL, but file-only vault items legitimately have no text content.

ALTER TABLE vault_items
    ADD COLUMN encrypted_dek_server VARCHAR(512),
    ADD COLUMN dek_iv_server        VARCHAR(64),
    ADD COLUMN dek_salt_server      VARCHAR(64);

ALTER TABLE vault_items
    ALTER COLUMN encrypted_content DROP NOT NULL;
