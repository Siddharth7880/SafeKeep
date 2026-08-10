-- V11: Add email verification token expiry.
-- Without expiry, a 6-digit OTP never expires and is vulnerable to brute-force.
-- Tokens are now valid for 15 minutes from generation.

ALTER TABLE users
    ADD COLUMN email_verification_token_expiry TIMESTAMP;
