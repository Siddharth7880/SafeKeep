ALTER TABLE users
ADD COLUMN email_verified BOOLEAN DEFAULT FALSE NOT NULL,
ADD COLUMN email_verification_token VARCHAR(255);

-- Mark existing users as verified so they can log in
UPDATE users SET email_verified = TRUE;
