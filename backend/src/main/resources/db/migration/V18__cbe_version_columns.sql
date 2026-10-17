-- V18: add the optimistic-locking version column to the CBE tables (V17 omitted it).

ALTER TABLE curriculum_version ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 0;
ALTER TABLE competency ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 0;
ALTER TABLE rubric ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 0;
ALTER TABLE rubric_criterion ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 0;
