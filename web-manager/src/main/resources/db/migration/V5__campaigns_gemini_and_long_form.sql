CREATE TABLE campaigns (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    theme VARCHAR(500) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    episode_count INTEGER NOT NULL DEFAULT 5,
    target_duration_seconds INTEGER NOT NULL DEFAULT 60,
    visual_style VARCHAR(240) NOT NULL DEFAULT '',
    character_description VARCHAR(240) NOT NULL DEFAULT '',
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNING',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_campaign_episode_count CHECK (episode_count BETWEEN 1 AND 30),
    CONSTRAINT ck_campaign_duration CHECK (target_duration_seconds BETWEEN 30 AND 600),
    CONSTRAINT ck_campaign_status CHECK (status IN ('PLANNING','ACTIVE','COMPLETED'))
);

ALTER TABLE work_tasks ADD COLUMN target_duration_seconds INTEGER NOT NULL DEFAULT 60;
ALTER TABLE work_tasks ADD COLUMN campaign_id BIGINT REFERENCES campaigns(id) ON DELETE SET NULL;
ALTER TABLE work_tasks ADD COLUMN episode_number INTEGER;
ALTER TABLE work_tasks ADD COLUMN ai_provider VARCHAR(30) NOT NULL DEFAULT '';
ALTER TABLE work_tasks ADD COLUMN visual_style VARCHAR(240) NOT NULL DEFAULT '';
ALTER TABLE work_tasks ADD COLUMN character_description VARCHAR(240) NOT NULL DEFAULT '';

CREATE INDEX idx_campaigns_owner_id ON campaigns(owner_id);
CREATE INDEX idx_campaigns_updated_at ON campaigns(updated_at DESC);
CREATE INDEX idx_work_tasks_campaign_id ON work_tasks(campaign_id);
