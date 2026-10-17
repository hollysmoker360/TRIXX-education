-- V22: E-Learning courses. A course is an ordered path over existing learning items
-- (learning_resource, assignment, quiz) with learner enrollment; progress is derived from the
-- learning module's completion, submission and attempt records rather than stored twice.

CREATE TABLE course (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       uuid NOT NULL REFERENCES school (id),
    title           text NOT NULL,
    description     text,
    subject_id      uuid,
    class_group_id  uuid,
    status          text NOT NULL DEFAULT 'DRAFT',
    published_at    timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    version         integer NOT NULL DEFAULT 0,
    deleted_at      timestamptz,
    origin_id       text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, subject_id) REFERENCES subject (school_id, id),
    FOREIGN KEY (school_id, class_group_id) REFERENCES class_group (school_id, id),
    CONSTRAINT course_status_check CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED'))
);
CREATE INDEX idx_course_school ON course (school_id, status) WHERE deleted_at IS NULL;

CREATE TABLE course_item (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   uuid NOT NULL REFERENCES school (id),
    course_id   uuid NOT NULL,
    position    integer NOT NULL,
    item_type   text NOT NULL,
    ref_id      uuid NOT NULL,
    title       text NOT NULL,
    required    boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_by  uuid,
    version     integer NOT NULL DEFAULT 0,
    deleted_at  timestamptz,
    origin_id   text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, course_id) REFERENCES course (school_id, id),
    CONSTRAINT course_item_type_check CHECK (item_type IN ('RESOURCE','ASSIGNMENT','QUIZ'))
);
CREATE UNIQUE INDEX uq_course_item_ref ON course_item (school_id, course_id, item_type, ref_id) WHERE deleted_at IS NULL;

CREATE TABLE course_enrollment (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id     uuid NOT NULL REFERENCES school (id),
    course_id     uuid NOT NULL,
    student_id    uuid NOT NULL,
    status        text NOT NULL DEFAULT 'ACTIVE',
    enrolled_at   timestamptz NOT NULL DEFAULT now(),
    completed_at  timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_by    uuid,
    version       integer NOT NULL DEFAULT 0,
    deleted_at    timestamptz,
    origin_id     text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, course_id) REFERENCES course (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id),
    CONSTRAINT course_enrollment_status_check CHECK (status IN ('ACTIVE','COMPLETED','WITHDRAWN'))
);
CREATE UNIQUE INDEX uq_course_enrollment ON course_enrollment (school_id, course_id, student_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_course_enrollment_student ON course_enrollment (school_id, student_id) WHERE deleted_at IS NULL;

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['course', 'course_item', 'course_enrollment'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING (school_id = current_school()) WITH CHECK (school_id = current_school())',
            t);
    END LOOP;
END $$;
