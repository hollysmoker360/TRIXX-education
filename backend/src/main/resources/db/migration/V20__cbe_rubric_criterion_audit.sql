-- V20: add created_by/updated_by to rubric_criterion (omitted in V17).

ALTER TABLE rubric_criterion ADD COLUMN IF NOT EXISTS created_by uuid;
ALTER TABLE rubric_criterion ADD COLUMN IF NOT EXISTS updated_by uuid;
