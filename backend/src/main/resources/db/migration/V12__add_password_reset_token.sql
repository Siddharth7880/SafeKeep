-- V12: Add password reset token fields for the "Forgot Password" flow.
-- Tokens are cryptographically random (UUID-based), valid for 30 minutes.
ALTER TABLE users
    ADD COLUMN password_reset_token VARCHAR(255),
    ADD COLUMN password_reset_token_expiry TIMESTAMP;
