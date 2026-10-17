-- V9: timetable engine (spec 12). Immutable versions with author and note; only a validated
-- version can be published; the previous published version is archived.

CREATE TABLE timetable_version (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           uuid NOT NULL REFERENCES school (id),
    academic_year_id    uuid NOT NULL,
    term_id             uuid NOT NULL,
    name                text NOT NULL,
    state               text NOT NULL DEFAULT 'draft',
    author_membership_id uuid,
    note                text,
    solver_seed         bigint,
    solver_score        numeric(10,2),
    constraint_report   jsonb,
    validated_at        timestamptz,
    published_at        timestamptz,
    archived_at         timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    version             integer NOT NULL DEFAULT 0,
    deleted_at          timestamptz,
    origin_id           text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, academic_year_id) REFERENCES academic_year (school_id, id),
    FOREIGN KEY (school_id, term_id) REFERENCES term (school_id, id),
    FOREIGN KEY (school_id, author_membership_id) REFERENCES school_membership (school_id, id),
    CONSTRAINT timetable_version_state_check CHECK (state IN ('draft', 'published', 'archived'))
);
CREATE UNIQUE INDEX uq_timetable_version_published ON timetable_version (school_id, term_id) WHERE state = 'published' AND deleted_at IS NULL;
CREATE INDEX idx_timetable_version_term ON timetable_version (school_id, term_id, state);

CREATE TABLE timetable_entry (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id            uuid NOT NULL REFERENCES school (id),
    version_id           uuid NOT NULL,
    class_group_id       uuid NOT NULL,
    stream_id            uuid,
    subject_id           uuid NOT NULL,
    teacher_membership_id uuid,
    classroom_id         uuid,
    day_of_week          integer NOT NULL,
    period_seq           integer NOT NULL,
    span                 integer NOT NULL DEFAULT 1,
    is_locked            boolean NOT NULL DEFAULT false,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    created_by           uuid,
    updated_by           uuid,
    version              integer NOT NULL DEFAULT 0,
    deleted_at           timestamptz,
    origin_id            text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, version_id) REFERENCES timetable_version (school_id, id),
    FOREIGN KEY (school_id, class_group_id) REFERENCES class_group (school_id, id),
    FOREIGN KEY (school_id, stream_id) REFERENCES stream (school_id, id),
    FOREIGN KEY (school_id, subject_id) REFERENCES subject (school_id, id),
    FOREIGN KEY (school_id, classroom_id) REFERENCES classroom (school_id, id),
    CONSTRAINT timetable_entry_day_check CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT timetable_entry_period_check CHECK (period_seq >= 1),
    CONSTRAINT timetable_entry_span_check CHECK (span IN (1, 2))
);
CREATE INDEX idx_timetable_entry_version ON timetable_entry (school_id, version_id);
CREATE INDEX idx_timetable_entry_teacher ON timetable_entry (school_id, teacher_membership_id, day_of_week, period_seq);
CREATE INDEX idx_timetable_entry_room ON timetable_entry (school_id, classroom_id, day_of_week, period_seq);
