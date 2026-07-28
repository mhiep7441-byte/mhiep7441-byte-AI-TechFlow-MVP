ALTER TABLE campaigns ADD COLUMN audience VARCHAR(160) NOT NULL DEFAULT '';
ALTER TABLE campaigns ADD COLUMN cadence VARCHAR(20) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE campaigns ADD COLUMN production_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE campaigns ADD COLUMN next_run_at TIMESTAMP;
ALTER TABLE campaigns ADD COLUMN last_run_at TIMESTAMP;
ALTER TABLE campaigns ADD COLUMN series_plan_json TEXT NOT NULL DEFAULT '';

ALTER TABLE campaigns ADD CONSTRAINT ck_campaign_cadence
    CHECK (cadence IN ('MANUAL','HOURLY','DAILY'));

CREATE INDEX idx_campaigns_due
    ON campaigns(production_enabled, status, next_run_at);

CREATE TABLE youtube_accounts (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL UNIQUE REFERENCES app_users(id) ON DELETE CASCADE,
    channel_id VARCHAR(160) NOT NULL DEFAULT '',
    channel_title VARCHAR(160) NOT NULL DEFAULT '',
    encrypted_access_token TEXT NOT NULL,
    encrypted_refresh_token TEXT NOT NULL,
    scopes VARCHAR(1000) NOT NULL DEFAULT '',
    access_token_expires_at TIMESTAMP NOT NULL,
    connected_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
