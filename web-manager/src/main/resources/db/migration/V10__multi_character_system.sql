CREATE TABLE character_profiles (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL,
    campaign_id BIGINT REFERENCES campaigns(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    role VARCHAR(80) DEFAULT 'MAIN',
    character_type VARCHAR(80) DEFAULT 'HUMAN',
    description TEXT,
    personality TEXT,
    species VARCHAR(80),
    apparent_age VARCHAR(80),
    gender_presentation VARCHAR(80),
    body_shape VARCHAR(80),
    height_description VARCHAR(80),
    face_description TEXT,
    eye_description TEXT,
    hair_description TEXT,
    clothing TEXT,
    accessories TEXT,
    primary_colors VARCHAR(160),
    secondary_colors VARCHAR(160),
    distinguishing_features TEXT,
    canonical_prompt TEXT,
    negative_prompt TEXT,
    front_reference_url TEXT,
    side_reference_url TEXT,
    back_reference_url TEXT,
    expression_sheet_url TEXT,
    pose_sheet_url TEXT,
    preferred_seed BIGINT,
    locked BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE character_reference_assets (
    id BIGSERIAL PRIMARY KEY,
    character_id BIGINT NOT NULL REFERENCES character_profiles(id) ON DELETE CASCADE,
    asset_type VARCHAR(80) NOT NULL,
    cloudinary_url TEXT NOT NULL,
    cloudinary_public_id VARCHAR(255),
    is_primary BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE campaign_characters (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    character_id BIGINT NOT NULL REFERENCES character_profiles(id) ON DELETE CASCADE,
    is_protagonist BOOLEAN DEFAULT FALSE,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_campaign_character UNIQUE (campaign_id, character_id)
);

CREATE TABLE episode_cast (
    id BIGSERIAL PRIMARY KEY,
    episode_id BIGINT NOT NULL REFERENCES work_tasks(id) ON DELETE CASCADE,
    character_id BIGINT NOT NULL REFERENCES character_profiles(id) ON DELETE CASCADE,
    role_in_episode VARCHAR(160),
    importance VARCHAR(80) DEFAULT 'PRIMARY',
    character_arc TEXT,
    costume_version VARCHAR(160),
    age_version VARCHAR(80),
    episode_notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_episode_character UNIQUE (episode_id, character_id)
);

CREATE TABLE scene_cast (
    id BIGSERIAL PRIMARY KEY,
    scene_number INT NOT NULL,
    task_id BIGINT NOT NULL REFERENCES work_tasks(id) ON DELETE CASCADE,
    character_id BIGINT NOT NULL REFERENCES character_profiles(id) ON DELETE CASCADE,
    role_in_scene VARCHAR(160),
    position VARCHAR(160),
    expression VARCHAR(160),
    action TEXT,
    visibility VARCHAR(80) DEFAULT 'VISIBLE',
    costume_override TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_character_profiles_owner ON character_profiles(owner_id);
CREATE INDEX idx_character_profiles_campaign ON character_profiles(campaign_id);
CREATE INDEX idx_scene_cast_task_scene ON scene_cast(task_id, scene_number);
