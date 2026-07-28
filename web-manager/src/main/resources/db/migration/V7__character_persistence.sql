-- Character image persistence for campaign consistency
ALTER TABLE campaigns ADD COLUMN character_image_url TEXT;
ALTER TABLE campaigns ADD COLUMN character_reference_prompt TEXT;

-- Store character reference on work tasks for video generation
ALTER TABLE work_tasks ADD COLUMN character_image_url TEXT;
