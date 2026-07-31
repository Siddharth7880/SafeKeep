ALTER TABLE users
ADD COLUMN pending_phone_number VARCHAR(20),
ADD COLUMN phone_verification_otp VARCHAR(10),
ADD COLUMN phone_otp_expiry TIMESTAMP,
ADD COLUMN phone_number_verified BOOLEAN DEFAULT FALSE NOT NULL;
