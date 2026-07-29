CREATE TABLE script_versions (
    id BIGSERIAL PRIMARY KEY,
    episode_id BIGINT NOT NULL REFERENCES work_tasks(id) ON DELETE CASCADE,
    version_number INT NOT NULL DEFAULT 1,
    title VARCHAR(200),
    hook TEXT,
    script_text TEXT NOT NULL,
    scene_count INT,
    word_count INT,
    status VARCHAR(80) DEFAULT 'DRAFT',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE render_versions (
    id BIGSERIAL PRIMARY KEY,
    episode_id BIGINT NOT NULL REFERENCES work_tasks(id) ON DELETE CASCADE,
    version_number INT NOT NULL DEFAULT 1,
    profile_type VARCHAR(80) DEFAULT 'FINAL',
    resolution VARCHAR(80) DEFAULT '1920x1080',
    fps INT DEFAULT 30,
    manifest_json TEXT,
    render_url TEXT,
    duration_ms BIGINT,
    file_size BIGINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE project_exports (
    id BIGSERIAL PRIMARY KEY,
    episode_id BIGINT NOT NULL REFERENCES work_tasks(id) ON DELETE CASCADE,
    zip_url TEXT,
    manifest_url TEXT,
    script_url TEXT,
    image_set_url TEXT,
    narration_url TEXT,
    subtitle_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_script_versions_episode ON script_versions(episode_id);
CREATE INDEX idx_render_versions_episode ON render_versions(episode_id);
