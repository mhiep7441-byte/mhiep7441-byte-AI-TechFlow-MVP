CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(190) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    password_hash VARCHAR(255),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    avatar_url VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_app_users_role CHECK (role IN ('ADMIN','USER')),
    CONSTRAINT ck_app_users_provider CHECK (auth_provider IN ('LOCAL','GOOGLE'))
);

ALTER TABLE work_tasks ADD COLUMN owner_id BIGINT REFERENCES app_users(id) ON DELETE SET NULL;
ALTER TABLE work_tasks ADD COLUMN caption VARCHAR(2200) NOT NULL DEFAULT '';
ALTER TABLE work_tasks ADD COLUMN hashtags VARCHAR(500) NOT NULL DEFAULT '';

CREATE INDEX idx_app_users_email ON app_users(email);
CREATE INDEX idx_app_users_role ON app_users(role);
CREATE INDEX idx_work_tasks_owner_id ON work_tasks(owner_id);
