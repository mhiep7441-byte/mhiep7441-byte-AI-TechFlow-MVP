CREATE TABLE video_feedback (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES work_tasks(id) ON DELETE CASCADE,
    owner_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    rating INTEGER NOT NULL,
    aspects VARCHAR(500) NOT NULL DEFAULT '',
    comment VARCHAR(2000) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_video_feedback_owner_task UNIQUE (owner_id, task_id),
    CONSTRAINT ck_video_feedback_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE INDEX idx_video_feedback_updated_at ON video_feedback(updated_at DESC);
CREATE INDEX idx_video_feedback_rating ON video_feedback(rating);
