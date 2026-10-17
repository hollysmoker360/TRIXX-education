-- V19: add the origin_id column (from TenantEntity) to the CBE tables.

ALTER TABLE curriculum_version ADD COLUMN IF NOT EXISTS origin_id text;
ALTER TABLE competency ADD COLUMN IF NOT EXISTS origin_id text;
ALTER TABLE rubric ADD COLUMN IF NOT EXISTS origin_id text;
ALTER TABLE rubric_criterion ADD COLUMN IF NOT EXISTS origin_id text;
