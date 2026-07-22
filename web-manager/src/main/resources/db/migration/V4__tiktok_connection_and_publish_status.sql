CREATE TABLE tiktok_accounts (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL UNIQUE REFERENCES app_users(id) ON DELETE CASCADE,
    open_id VARCHAR(160) NOT NULL,
    display_name VARCHAR(160) NOT NULL DEFAULT '',
    encrypted_access_token TEXT NOT NULL,
    encrypted_refresh_token TEXT NOT NULL,
    scopes VARCHAR(1000) NOT NULL DEFAULT '',
    access_token_expires_at TIMESTAMP NOT NULL,
    refresh_token_expires_at TIMESTAMP,
    connected_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_tiktok_accounts_open_id ON tiktok_accounts(open_id);

ALTER TABLE publications DROP CONSTRAINT ck_publications_status;
ALTER TABLE publications ADD CONSTRAINT ck_publications_status
    CHECK (status IN ('PENDING','READY','PROCESSING','PUBLISHED','FAILED'));

