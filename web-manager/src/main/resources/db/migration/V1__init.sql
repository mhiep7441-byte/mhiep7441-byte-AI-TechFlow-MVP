CREATE TABLE work_tasks (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NOT NULL DEFAULT '',
    topic VARCHAR(500) NOT NULL DEFAULT '',
    status VARCHAR(40) NOT NULL DEFAULT 'TODO',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    due_date DATE,
    output_path VARCHAR(1000),
    error_message VARCHAR(4000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_work_tasks_status CHECK (status IN ('TODO','IN_PROGRESS','GENERATING','DRAFT_REQUIRES_REVIEW','DONE','FAILED')),
    CONSTRAINT ck_work_tasks_priority CHECK (priority IN ('LOW','MEDIUM','HIGH'))
);

CREATE TABLE publications (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES work_tasks(id) ON DELETE CASCADE,
    platform VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    scheduled_at TIMESTAMP,
    published_at TIMESTAMP,
    external_id VARCHAR(255),
    note VARCHAR(1000) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_publications_platform CHECK (platform IN ('TIKTOK','YOUTUBE','OTHER')),
    CONSTRAINT ck_publications_status CHECK (status IN ('PENDING','READY','PUBLISHED','FAILED'))
);

CREATE INDEX idx_work_tasks_status ON work_tasks(status);
CREATE INDEX idx_work_tasks_updated_at ON work_tasks(updated_at DESC);
CREATE INDEX idx_publications_scheduled_at ON publications(scheduled_at);
CREATE INDEX idx_publications_status ON publications(status);
