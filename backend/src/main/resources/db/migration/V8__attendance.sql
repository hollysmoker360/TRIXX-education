-- V8: attendance (spec 11.6). Events are append-only with event_time (device clock) and
-- received_at (server clock). Corrections are new events referencing the original.

CREATE TABLE attendance_session (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id              uuid NOT NULL REFERENCES school (id),
    class_group_id         uuid NOT NULL,
    stream_id              uuid,
    subject_id             uuid,
    teacher_membership_id  uuid,
    session_type           text NOT NULL DEFAULT 'daily',
    session_date           date NOT NULL,
    period_seq             integer,
    status                 text NOT NULL DEFAULT 'open',
    opened_at              timestamptz NOT NULL DEFAULT now(),
    closed_at              timestamptz,
    notes                  text,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    created_by             uuid,
    updated_by             uuid,
    version                integer NOT NULL DEFAULT 0,
    deleted_at             timestamptz,
    origin_id              text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, class_group_id) REFERENCES class_group (school_id, id),
    FOREIGN KEY (school_id, stream_id) REFERENCES stream (school_id, id),
    FOREIGN KEY (school_id, subject_id) REFERENCES subject (school_id, id),
    FOREIGN KEY (school_id, teacher_membership_id) REFERENCES school_membership (school_id, id),
    CONSTRAINT attendance_session_type_check CHECK (session_type IN ('daily', 'lesson')),
    CONSTRAINT attendance_session_status_check CHECK (status IN ('open', 'closed'))
);
CREATE UNIQUE INDEX uq_attendance_session ON attendance_session
    (school_id, class_group_id, coalesce(stream_id, '00000000-0000-0000-0000-000000000000'::uuid), session_type,
     coalesce(subject_id, '00000000-0000-0000-0000-000000000000'::uuid), session_date,
     coalesce(period_seq, 0))
    WHERE deleted_at IS NULL;

CREATE TABLE attendance_event (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id              uuid NOT NULL REFERENCES school (id),
    session_id             uuid NOT NULL,
    student_id             uuid NOT NULL,
    status                 text NOT NULL,
    reason                 text,
    event_time             timestamptz NOT NULL,
    received_at            timestamptz NOT NULL DEFAULT now(),
    origin_device_id       text,
    corrects_event_id      uuid,
    recorded_by_membership_id uuid,
    created_at             timestamptz NOT NULL DEFAULT now(),
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, session_id) REFERENCES attendance_session (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id),
    FOREIGN KEY (school_id, corrects_event_id) REFERENCES attendance_event (school_id, id),
    CONSTRAINT attendance_event_status_check CHECK (status IN ('present', 'absent', 'late', 'excused'))
);
CREATE INDEX idx_attendance_event_session ON attendance_event (session_id, student_id, received_at DESC);
CREATE INDEX idx_attendance_event_student ON attendance_event (school_id, student_id, event_time);
