-- V27: Fix column type for last_reviewed_at to match entity (LocalDate -> date)

ALTER TABLE student_progress_profile ALTER COLUMN last_reviewed_at TYPE date USING last_reviewed_at::date;
