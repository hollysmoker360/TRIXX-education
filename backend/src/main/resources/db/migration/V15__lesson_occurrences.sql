-- V15: lesson occurrences and scheduled reminders (spec 12.2). A published timetable is expanded
-- into dated occurrences for a term, and each recipient gets one scheduled notification per
-- occurrence and lead-time offset. The notification dedupe key is the idempotency key
-- (occurrence, user, offset, channel), so materialisation can be re-run and a reminder can never be
-- sent twice.

CREATE TABLE lesson_occurrence (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id             uuid NOT NULL REFERENCES school (id),
    timetable_version_id  uuid NOT NULL,
    term_id               uuid NOT NULL,
    class_group_id        uuid NOT NULL,
    stream_id             uuid,
    subject_id            uuid NOT NULL,
    teacher_membership_id uuid,
    classroom_id          uuid,
    lesson_date           date NOT NULL,
    starts_at             timestamptz NOT NULL,
    ends_at               timestamptz NOT NULL,
    span                  integer NOT NULL DEFAULT 1,
    state                 text NOT NULL DEFAULT 'scheduled',
    cancelled_at          timestamptz,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    created_by            uuid,
    updated_by            uuid,
    version               integer NOT NULL DEFAULT 0,
    deleted_at            timestamptz,
    origin_id             text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, timetable_version_id) REFERENCES timetable_version (school_id, id),
    FOREIGN KEY (school_id, term_id) REFERENCES term (school_id, id),
    FOREIGN KEY (school_id, class_group_id) REFERENCES class_group (school_id, id),
    FOREIGN KEY (school_id, stream_id) REFERENCES stream (school_id, id),
    FOREIGN KEY (school_id, subject_id) REFERENCES subject (school_id, id),
    CONSTRAINT lesson_occurrence_state_check CHECK (state IN ('scheduled', 'cancelled')),
    CONSTRAINT lesson_occurrence_time_check CHECK (ends_at > starts_at)
);

-- One occurrence per class (+stream), subject and slot on a date, so a re-run of materialisation
-- refreshes rather than duplicates (spec 12.2).
CREATE UNIQUE INDEX uq_lesson_occurrence_slot ON lesson_occurrence (
    school_id, class_group_id, subject_id, lesson_date, starts_at,
    coalesce(stream_id, '00000000-0000-0000-0000-000000000000'::uuid)
) WHERE deleted_at IS NULL;
CREATE INDEX idx_lesson_occurrence_day ON lesson_occurrence (school_id, lesson_date, starts_at)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_lesson_occurrence_teacher
    ON lesson_occurrence (school_id, teacher_membership_id, lesson_date) WHERE deleted_at IS NULL;
CREATE INDEX idx_lesson_occurrence_future ON lesson_occurrence (school_id, starts_at)
    WHERE state = 'scheduled' AND deleted_at IS NULL;

ALTER TABLE lesson_occurrence ENABLE ROW LEVEL SECURITY;
ALTER TABLE lesson_occurrence FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON lesson_occurrence
    USING (school_id = current_school()) WITH CHECK (school_id = current_school());

-- Scheduled notifications (spec 11.7). A row whose due_at is in the future is invisible in the
-- inbox and is released by the due-notification worker at its due time; due_at also serves as the
-- claim marker, so a released row (due_at NULL) is never picked up twice.
ALTER TABLE notification ADD COLUMN due_at timestamptz;
ALTER TABLE notification ADD COLUMN dedupe_key text;
CREATE UNIQUE INDEX uq_notification_dedupe ON notification (school_id, dedupe_key)
    WHERE dedupe_key IS NOT NULL;
CREATE INDEX idx_notification_due ON notification (due_at, id) WHERE due_at IS NOT NULL;
