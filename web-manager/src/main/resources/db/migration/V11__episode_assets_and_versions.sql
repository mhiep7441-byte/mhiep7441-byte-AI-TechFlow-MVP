CREATE TABLE episode_assets (
    id BIGSERIAL PRIMARY KEY,
    episode_id BIGINT NOT NULL REFERENCES work_tasks(id) ON DELETE CASCADE,
    scene_number INT,
    asset_type VARCHAR(80) NOT NULL,
    active_version_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE asset_versions (
    id BIGSERIAL PRIMARY KEY,
    episode_id BIGINT NOT NULL REFERENCES work_tasks(id) ON DELETE CASCADE,
    scene_number INT,
    asset_type VARCHAR(80) NOT NULL,
    version_number INT NOT NULL DEFAULT 1,
    provider VARCHAR(80),
    model VARCHAR(160),
    prompt TEXT,
    negative_prompt TEXT,
    seed BIGINT,
    width INT,
    height INT,
    duration_ms BIGINT,
    cloudinary_url TEXT,
    cloudinary_public_id VARCHAR(255),
    checksum VARCHAR(128),
    file_size BIGINT,
    status VARCHAR(80) DEFAULT 'GENERATED',
    approval_status VARCHAR(80) DEFAULT 'PENDING',
    rejection_reason TEXT,
    quality_score INT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_asset_versions_episode ON asset_versions(episode_id, asset_type);
CREATE INDEX idx_asset_versions_scene ON asset_versions(episode_id, scene_number);
