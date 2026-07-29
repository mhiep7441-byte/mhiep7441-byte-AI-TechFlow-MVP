-- V7__job_orchestration.sql

CREATE TABLE workflow_runs (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT REFERENCES campaigns(id) ON DELETE CASCADE,
    episode_id BIGINT,
    status VARCHAR(50) NOT NULL DEFAULT 'CREATED',
    current_step VARCHAR(100),
    progress INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE generation_jobs (
    id BIGSERIAL PRIMARY KEY,
    workflow_run_id BIGINT REFERENCES workflow_runs(id) ON DELETE CASCADE,
    episode_id BIGINT,
    scene_id BIGINT, -- nullable
    job_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'QUEUED',
    progress INT DEFAULT 0,
    current_step VARCHAR(100),
    priority INT DEFAULT 0,
    idempotency_key VARCHAR(255),
    input_json TEXT,
    output_json TEXT,
    provider VARCHAR(50),
    model VARCHAR(100),
    attempt_count INT DEFAULT 0,
    max_attempts INT DEFAULT 3,
    error_code VARCHAR(100),
    error_message TEXT,
    worker_id VARCHAR(100),
    queued_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP WITH TIME ZONE,
    heartbeat_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE job_attempts (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT REFERENCES generation_jobs(id) ON DELETE CASCADE,
    worker_id VARCHAR(100),
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) NOT NULL,
    error_message TEXT
);

CREATE TABLE job_events (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT REFERENCES generation_jobs(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    event_data TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for efficient queue polling
CREATE INDEX idx_gen_jobs_status_priority_queued ON generation_jobs(status, priority DESC, queued_at ASC);
CREATE INDEX idx_gen_jobs_workflow ON generation_jobs(workflow_run_id);
CREATE INDEX idx_gen_jobs_episode_type ON generation_jobs(episode_id, job_type);
