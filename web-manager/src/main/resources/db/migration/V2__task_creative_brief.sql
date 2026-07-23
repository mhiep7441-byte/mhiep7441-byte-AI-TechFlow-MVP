-- Persist the creative brief submitted with each video task.
-- These fields are deliberately plain text: URLs and prompts remain editable
-- until a human reviews the generated draft.
ALTER TABLE work_tasks ADD COLUMN IF NOT EXISTS visual_style VARCHAR(240) NOT NULL DEFAULT '';
ALTER TABLE work_tasks ADD COLUMN IF NOT EXISTS character_description VARCHAR(240) NOT NULL DEFAULT '';
ALTER TABLE work_tasks ADD COLUMN IF NOT EXISTS research_sources VARCHAR(1000) NOT NULL DEFAULT '';
