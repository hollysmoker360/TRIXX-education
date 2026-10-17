-- V16: solver outcome marker for CP-SAT relaxed drafts (spec 12.1, ADR-020).
-- A version with any unmet requirement is a relaxed draft: it satisfies the hard constraints
-- but omits part of the school's requirement, so it can be reviewed but never published.

ALTER TABLE timetable_version
    ADD COLUMN unmet_requirements integer NOT NULL DEFAULT 0;

COMMENT ON COLUMN timetable_version.unmet_requirements IS
    'Number of requirements the CP-SAT solver could not place. Zero for a complete timetable; a '
    'positive value marks a relaxed draft that must not be published.';
