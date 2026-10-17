-- V14: add the optimistic-locking version column to feature_flag.
-- feature_flag predates the shared BaseEntity (which carries the @Version column); every other
-- entity has had it since V1, so the table is brought in line. Forward-only: V2 stays untouched.

ALTER TABLE feature_flag ADD COLUMN version integer NOT NULL DEFAULT 0;
