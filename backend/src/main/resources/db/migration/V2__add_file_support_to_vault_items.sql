ALTER TABLE vault_items
ADD COLUMN original_file_name VARCHAR(255),
ADD COLUMN encrypted_file_path VARCHAR(255);

ALTER TABLE vault_items
ALTER COLUMN encrypted_content DROP NOT NULL;
