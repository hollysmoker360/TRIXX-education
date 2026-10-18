-- V26: Add missing updated_at column to tenant tables (Hibernate @Version requires it)
-- Tables introduced in V23/V24 that were missing updated_at on existing databases.

ALTER TABLE mark_correction ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE risk_indicator ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE progress_note ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
