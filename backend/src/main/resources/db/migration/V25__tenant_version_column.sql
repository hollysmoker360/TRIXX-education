-- V25: Add optimistic-lock version column to tenant tables introduced in V23/V24
-- that were missing it (Hibernate @Version on TenantEntity/BaseEntity requires it).

ALTER TABLE assessment_component ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 0;
ALTER TABLE mark_correction ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 0;
ALTER TABLE grade_band ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 0;

ALTER TABLE student_progress_profile ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 0;
ALTER TABLE risk_indicator ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 0;
ALTER TABLE support_referral ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 0;
ALTER TABLE intervention_plan ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 0;
ALTER TABLE support_goal ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 0;
ALTER TABLE progress_note ADD COLUMN IF NOT EXISTS version integer NOT NULL DEFAULT 0;
