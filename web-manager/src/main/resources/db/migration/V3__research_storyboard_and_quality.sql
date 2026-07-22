ALTER TABLE work_tasks ADD COLUMN research_json TEXT NOT NULL DEFAULT '{}';
ALTER TABLE work_tasks ADD COLUMN storyboard_json TEXT NOT NULL DEFAULT '{}';
ALTER TABLE work_tasks ADD COLUMN source_urls TEXT NOT NULL DEFAULT '';
ALTER TABLE work_tasks ADD COLUMN fact_check_status VARCHAR(40) NOT NULL DEFAULT 'NOT_CHECKED';
ALTER TABLE work_tasks ADD COLUMN quality_score INTEGER;

