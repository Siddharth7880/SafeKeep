ALTER TABLE users
DROP COLUMN pending_phone_number,
DROP COLUMN phone_verification_otp,
DROP COLUMN phone_otp_expiry,
DROP COLUMN phone_number_verified;
