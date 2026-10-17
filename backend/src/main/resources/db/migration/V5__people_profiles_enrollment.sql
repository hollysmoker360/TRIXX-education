-- V5: people management and enrollment (spec 6.2, 11.1). Profiles belong to a school; a person in two
-- schools has two profiles under one user. Sensitive child data fields are encrypted at field level
-- (AES-GCM envelope, spec 3.1) and stored as text.

CREATE TABLE student (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id         uuid NOT NULL REFERENCES school (id),
    user_id           uuid REFERENCES app_user (id),
    admission_number  text NOT NULL,
    first_name        text NOT NULL,
    last_name         text NOT NULL,
    other_names       text,
    date_of_birth     date,
    gender            text,
    upi_encrypted     text,
    medical_notes_encrypted text,
    photo_key         text,
    status            text NOT NULL DEFAULT 'active',
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    created_by        uuid,
    updated_by        uuid,
    version           integer NOT NULL DEFAULT 0,
    deleted_at        timestamptz,
    origin_id         text,
    UNIQUE (school_id, id),
    CONSTRAINT student_status_check CHECK (status IN ('active', 'alumni', 'left'))
);
CREATE UNIQUE INDEX uq_student_admission ON student (school_id, admission_number) WHERE deleted_at IS NULL;
CREATE INDEX idx_student_user ON student (user_id);

CREATE TABLE teacher (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id         uuid NOT NULL REFERENCES school (id),
    user_id           uuid NOT NULL REFERENCES app_user (id),
    staff_number      text,
    first_name        text NOT NULL,
    last_name         text NOT NULL,
    phone             text,
    qualifications    jsonb NOT NULL DEFAULT '[]'::jsonb,
    max_weekly_periods integer,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    created_by        uuid,
    updated_by        uuid,
    version           integer NOT NULL DEFAULT 0,
    deleted_at        timestamptz,
    origin_id         text,
    UNIQUE (school_id, id)
);
CREATE UNIQUE INDEX uq_teacher_user ON teacher (school_id, user_id) WHERE deleted_at IS NULL;

CREATE TABLE parent_profile (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id     uuid NOT NULL REFERENCES school (id),
    user_id       uuid NOT NULL REFERENCES app_user (id),
    first_name    text NOT NULL,
    last_name     text NOT NULL,
    phone_encrypted text,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_by    uuid,
    version       integer NOT NULL DEFAULT 0,
    deleted_at    timestamptz,
    origin_id     text,
    UNIQUE (school_id, id)
);
CREATE UNIQUE INDEX uq_parent_user ON parent_profile (school_id, user_id) WHERE deleted_at IS NULL;

-- GuardianLink: relationship type plus explicit granted visibilities (spec 6.2).
CREATE TABLE guardian_link (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id     uuid NOT NULL REFERENCES school (id),
    parent_id     uuid NOT NULL,
    student_id    uuid NOT NULL,
    relationship  text NOT NULL,
    is_primary    boolean NOT NULL DEFAULT false,
    visibilities  jsonb NOT NULL DEFAULT '{"attendance": true, "grades": true, "communication": true}'::jsonb,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_by    uuid,
    version       integer NOT NULL DEFAULT 0,
    deleted_at    timestamptz,
    origin_id     text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, parent_id) REFERENCES parent_profile (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id)
);
CREATE UNIQUE INDEX uq_guardian_link ON guardian_link (school_id, parent_id, student_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_guardian_link_student ON guardian_link (school_id, student_id);

-- Enrollment lifecycle: applied, admitted, enrolled, transferred, withdrawn, graduated (spec 11.1).
CREATE TABLE enrollment (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id         uuid NOT NULL REFERENCES school (id),
    student_id        uuid NOT NULL,
    academic_year_id  uuid NOT NULL,
    class_group_id    uuid NOT NULL,
    stream_id         uuid,
    status            text NOT NULL DEFAULT 'applied',
    decided_at        timestamptz,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    created_by        uuid,
    updated_by        uuid,
    version           integer NOT NULL DEFAULT 0,
    deleted_at        timestamptz,
    origin_id         text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id),
    FOREIGN KEY (school_id, academic_year_id) REFERENCES academic_year (school_id, id),
    FOREIGN KEY (school_id, class_group_id) REFERENCES class_group (school_id, id),
    FOREIGN KEY (school_id, stream_id) REFERENCES stream (school_id, id),
    CONSTRAINT enrollment_status_check CHECK (status IN ('applied', 'admitted', 'enrolled', 'transferred', 'withdrawn', 'graduated'))
);
CREATE UNIQUE INDEX uq_enrollment_student_year ON enrollment (school_id, student_id, academic_year_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_enrollment_class ON enrollment (school_id, class_group_id, status);

-- Every enrollment transition is audited (spec 11.1).
CREATE TABLE enrollment_transition (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           uuid NOT NULL REFERENCES school (id),
    enrollment_id       uuid NOT NULL,
    from_status         text,
    to_status           text NOT NULL,
    reason              text,
    actor_membership_id uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, enrollment_id) REFERENCES enrollment (school_id, id)
);
CREATE INDEX idx_enrollment_transition ON enrollment_transition (school_id, enrollment_id);

CREATE TABLE teacher_assignment (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           uuid NOT NULL REFERENCES school (id),
    teacher_id          uuid NOT NULL,
    class_group_id      uuid NOT NULL,
    stream_id           uuid,
    subject_id          uuid NOT NULL,
    term_id             uuid NOT NULL,
    periods_per_week    integer,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    version             integer NOT NULL DEFAULT 0,
    deleted_at          timestamptz,
    origin_id           text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, teacher_id) REFERENCES teacher (school_id, id),
    FOREIGN KEY (school_id, class_group_id) REFERENCES class_group (school_id, id),
    FOREIGN KEY (school_id, stream_id) REFERENCES stream (school_id, id),
    FOREIGN KEY (school_id, subject_id) REFERENCES subject (school_id, id),
    FOREIGN KEY (school_id, term_id) REFERENCES term (school_id, id)
);
CREATE UNIQUE INDEX uq_teacher_assignment ON teacher_assignment (school_id, teacher_id, class_group_id, subject_id, term_id, coalesce(stream_id, '00000000-0000-0000-0000-000000000000'::uuid)) WHERE deleted_at IS NULL;
CREATE INDEX idx_teacher_assignment_teacher ON teacher_assignment (school_id, teacher_id);

-- Configurable grading scales with a CBC-style preset (spec 11.1).
CREATE TABLE grading_scale (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id  uuid NOT NULL REFERENCES school (id),
    name       text NOT NULL,
    kind       text NOT NULL DEFAULT 'percentage',
    bands      jsonb NOT NULL DEFAULT '[]'::jsonb,
    is_default boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_by uuid,
    version    integer NOT NULL DEFAULT 0,
    deleted_at timestamptz,
    origin_id  text,
    UNIQUE (school_id, id),
    CONSTRAINT grading_scale_kind_check CHECK (kind IN ('percentage', 'letter', 'cbc'))
);
CREATE UNIQUE INDEX uq_grading_scale_name ON grading_scale (school_id, name) WHERE deleted_at IS NULL;
