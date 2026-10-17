-- V6: learning hub, assignments, quizzes (spec 11.2, 11.3).

CREATE TABLE learning_resource (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id            uuid NOT NULL REFERENCES school (id),
    title                text NOT NULL,
    description          text,
    resource_type        text NOT NULL,
    topic                text,
    subject_id           uuid,
    class_group_id       uuid,
    stream_id            uuid,
    academic_year_id     uuid,
    term_id              uuid,
    state                text NOT NULL DEFAULT 'draft',
    publish_at           timestamptz,
    audience             jsonb NOT NULL DEFAULT '{}'::jsonb,
    latest_version       integer NOT NULL DEFAULT 1,
    uploaded_by_membership_id uuid,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    created_by           uuid,
    updated_by           uuid,
    version              integer NOT NULL DEFAULT 0,
    deleted_at           timestamptz,
    origin_id            text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, subject_id) REFERENCES subject (school_id, id),
    FOREIGN KEY (school_id, class_group_id) REFERENCES class_group (school_id, id),
    FOREIGN KEY (school_id, stream_id) REFERENCES stream (school_id, id),
    FOREIGN KEY (school_id, academic_year_id) REFERENCES academic_year (school_id, id),
    FOREIGN KEY (school_id, term_id) REFERENCES term (school_id, id),
    CONSTRAINT learning_resource_type_check CHECK (resource_type IN
        ('document', 'presentation', 'image', 'video', 'audio', 'link', 'text_lesson', 'worksheet', 'revision_pack')),
    CONSTRAINT learning_resource_state_check CHECK (state IN ('draft', 'scheduled', 'published', 'archived'))
);
CREATE INDEX idx_learning_resource_scope ON learning_resource (school_id, class_group_id, subject_id, state);

-- Immutable versions; restore creates a new version (spec 11.2).
CREATE TABLE resource_version (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   uuid NOT NULL REFERENCES school (id),
    resource_id uuid NOT NULL,
    version_no  integer NOT NULL,
    storage_key text,
    mime_type   text,
    size_bytes  bigint,
    checksum    text,
    external_url text,
    text_body   text,
    scan_state  text NOT NULL DEFAULT 'clean',
    uploaded_by_membership_id uuid,
    note        text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (school_id, id),
    UNIQUE (resource_id, version_no),
    FOREIGN KEY (school_id, resource_id) REFERENCES learning_resource (school_id, id),
    CONSTRAINT resource_version_scan_check CHECK (scan_state IN ('pending', 'clean', 'quarantined'))
);

CREATE TABLE resource_bookmark (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   uuid NOT NULL REFERENCES school (id),
    resource_id uuid NOT NULL,
    student_id  uuid NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (school_id, id),
    UNIQUE (resource_id, student_id),
    FOREIGN KEY (school_id, resource_id) REFERENCES learning_resource (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id)
);

CREATE TABLE resource_completion (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id    uuid NOT NULL REFERENCES school (id),
    resource_id  uuid NOT NULL,
    student_id   uuid NOT NULL,
    completed_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (school_id, id),
    UNIQUE (resource_id, student_id),
    FOREIGN KEY (school_id, resource_id) REFERENCES learning_resource (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id)
);

CREATE TABLE assignment (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id             uuid NOT NULL REFERENCES school (id),
    title                 text NOT NULL,
    instructions          text,
    subject_id            uuid NOT NULL,
    class_group_id        uuid NOT NULL,
    stream_id             uuid,
    term_id               uuid,
    teacher_membership_id uuid,
    open_at               timestamptz,
    due_at                timestamptz,
    late_policy           text NOT NULL DEFAULT 'accepted',
    max_marks             numeric(7,2) NOT NULL DEFAULT 100,
    rubric                jsonb,
    audience              jsonb NOT NULL DEFAULT '{}'::jsonb,
    attachment_resource_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    grades_published      boolean NOT NULL DEFAULT false,
    state                 text NOT NULL DEFAULT 'draft',
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    created_by            uuid,
    updated_by            uuid,
    version               integer NOT NULL DEFAULT 0,
    deleted_at            timestamptz,
    origin_id             text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, subject_id) REFERENCES subject (school_id, id),
    FOREIGN KEY (school_id, class_group_id) REFERENCES class_group (school_id, id),
    FOREIGN KEY (school_id, stream_id) REFERENCES stream (school_id, id),
    FOREIGN KEY (school_id, term_id) REFERENCES term (school_id, id),
    CONSTRAINT assignment_late_policy_check CHECK (late_policy IN ('accepted', 'penalized', 'rejected')),
    CONSTRAINT assignment_state_check CHECK (state IN ('draft', 'published', 'archived'))
);
CREATE INDEX idx_assignment_class ON assignment (school_id, class_group_id, state, due_at);

-- Submissions: draft, submitted, returned, resubmitted, graded. First final submit wins (spec 10.4).
CREATE TABLE submission (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id             uuid NOT NULL REFERENCES school (id),
    assignment_id         uuid NOT NULL,
    student_id            uuid NOT NULL,
    state                 text NOT NULL DEFAULT 'draft',
    content_text          text,
    attachment_resource_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    submitted_at          timestamptz,
    grade                 numeric(7,2),
    feedback              text,
    graded_by_membership_id uuid,
    graded_at             timestamptz,
    reopened_at           timestamptz,
    idempotency_key       text,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    created_by            uuid,
    updated_by            uuid,
    version               integer NOT NULL DEFAULT 0,
    deleted_at            timestamptz,
    origin_id             text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, assignment_id) REFERENCES assignment (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id),
    CONSTRAINT submission_state_check CHECK (state IN ('draft', 'submitted', 'returned', 'resubmitted', 'graded'))
);
CREATE UNIQUE INDEX uq_submission_assignment_student ON submission (school_id, assignment_id, student_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_submission_idempotency ON submission (school_id, idempotency_key) WHERE idempotency_key IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE quiz (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           uuid NOT NULL REFERENCES school (id),
    assignment_id       uuid,
    title               text NOT NULL,
    subject_id          uuid,
    class_group_id      uuid,
    time_limit_minutes  integer,
    max_attempts        integer NOT NULL DEFAULT 1,
    shuffle             boolean NOT NULL DEFAULT false,
    state               text NOT NULL DEFAULT 'draft',
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    version             integer NOT NULL DEFAULT 0,
    deleted_at          timestamptz,
    origin_id           text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, assignment_id) REFERENCES assignment (school_id, id),
    FOREIGN KEY (school_id, subject_id) REFERENCES subject (school_id, id),
    FOREIGN KEY (school_id, class_group_id) REFERENCES class_group (school_id, id),
    CONSTRAINT quiz_state_check CHECK (state IN ('draft', 'published', 'archived'))
);

CREATE TABLE quiz_question (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id  uuid NOT NULL REFERENCES school (id),
    quiz_id    uuid NOT NULL,
    position   integer NOT NULL,
    qtype      text NOT NULL,
    prompt     text NOT NULL,
    options    jsonb NOT NULL DEFAULT '[]'::jsonb,
    answer_key jsonb,
    points     numeric(6,2) NOT NULL DEFAULT 1,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, quiz_id) REFERENCES quiz (school_id, id),
    CONSTRAINT quiz_question_type_check CHECK (qtype IN ('mcq', 'true_false', 'short_answer', 'manual'))
);

CREATE TABLE quiz_attempt (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id    uuid NOT NULL REFERENCES school (id),
    quiz_id      uuid NOT NULL,
    student_id   uuid NOT NULL,
    attempt_no   integer NOT NULL DEFAULT 1,
    state        text NOT NULL DEFAULT 'in_progress',
    started_at   timestamptz NOT NULL DEFAULT now(),
    submitted_at timestamptz,
    score        numeric(7,2),
    answers      jsonb NOT NULL DEFAULT '{}'::jsonb,
    UNIQUE (school_id, id),
    UNIQUE (quiz_id, student_id, attempt_no),
    FOREIGN KEY (school_id, quiz_id) REFERENCES quiz (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id),
    CONSTRAINT quiz_attempt_state_check CHECK (state IN ('in_progress', 'submitted', 'graded'))
);
