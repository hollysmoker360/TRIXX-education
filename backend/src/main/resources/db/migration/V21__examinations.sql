-- V21: Examinations module — assessments with a review/approval workflow, scheduled sittings,
-- candidate scripts (marking + moderation) and result publication. Candidates are existing
-- student records; subjects, classes and terms reuse the academics structure.

CREATE TABLE exam_assessment (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id             uuid NOT NULL REFERENCES school (id),
    title                 text NOT NULL,
    assessment_type       text NOT NULL DEFAULT 'EXAM',
    subject_id            uuid,
    class_group_id        uuid,
    term_id               uuid,
    total_marks           numeric(7,2) NOT NULL,
    pass_mark             numeric(7,2),
    duration_minutes      integer,
    instructions          text,
    status                text NOT NULL DEFAULT 'DRAFT',
    approved_by           uuid,
    approved_at           timestamptz,
    results_published_by  uuid,
    results_published_at  timestamptz,
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
    FOREIGN KEY (school_id, term_id) REFERENCES term (school_id, id),
    CONSTRAINT exam_assessment_status_check CHECK (status IN ('DRAFT','IN_REVIEW','APPROVED','PUBLISHED','ARCHIVED')),
    CONSTRAINT exam_assessment_type_check CHECK (assessment_type IN ('EXAM','CAT','MOCK','PRACTICAL')),
    CONSTRAINT exam_assessment_marks_check CHECK (total_marks > 0
        AND (pass_mark IS NULL OR (pass_mark >= 0 AND pass_mark <= total_marks))),
    CONSTRAINT exam_assessment_duration_check CHECK (duration_minutes IS NULL OR duration_minutes > 0)
);
CREATE INDEX idx_exam_assessment_school ON exam_assessment (school_id, status) WHERE deleted_at IS NULL;

CREATE TABLE exam_session (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id      uuid NOT NULL REFERENCES school (id),
    assessment_id  uuid NOT NULL,
    title          text NOT NULL,
    starts_at      timestamptz NOT NULL,
    ends_at        timestamptz NOT NULL,
    venue          text,
    status         text NOT NULL DEFAULT 'SCHEDULED',
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    created_by     uuid,
    updated_by     uuid,
    version        integer NOT NULL DEFAULT 0,
    deleted_at     timestamptz,
    origin_id      text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, assessment_id) REFERENCES exam_assessment (school_id, id),
    CONSTRAINT exam_session_status_check CHECK (status IN ('SCHEDULED','IN_PROGRESS','COMPLETED','CANCELLED')),
    CONSTRAINT exam_session_window_check CHECK (ends_at > starts_at)
);
CREATE INDEX idx_exam_session_assessment ON exam_session (school_id, assessment_id) WHERE deleted_at IS NULL;

CREATE TABLE exam_script (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id      uuid NOT NULL REFERENCES school (id),
    session_id     uuid NOT NULL,
    assessment_id  uuid NOT NULL,
    student_id     uuid NOT NULL,
    seat_number    text,
    status         text NOT NULL DEFAULT 'PENDING',
    marks          numeric(7,2),
    remarks        text,
    marked_by      uuid,
    marked_at      timestamptz,
    moderated_by   uuid,
    moderated_at   timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    created_by     uuid,
    updated_by     uuid,
    version        integer NOT NULL DEFAULT 0,
    deleted_at     timestamptz,
    origin_id      text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, session_id) REFERENCES exam_session (school_id, id),
    FOREIGN KEY (school_id, assessment_id) REFERENCES exam_assessment (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id),
    CONSTRAINT exam_script_status_check CHECK (status IN ('PENDING','ABSENT','MARKED','MODERATED')),
    CONSTRAINT exam_script_marks_check CHECK (marks IS NULL OR marks >= 0)
);
-- A student sits a given assessment once; this is the duplicate-registration guard.
CREATE UNIQUE INDEX uq_exam_script_candidate ON exam_script (school_id, assessment_id, student_id)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_exam_script_session ON exam_script (school_id, session_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_exam_script_student ON exam_script (school_id, student_id) WHERE deleted_at IS NULL;

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['exam_assessment', 'exam_session', 'exam_script'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING (school_id = current_school()) WITH CHECK (school_id = current_school())',
            t);
    END LOOP;
END $$;

-- Existing schools: grant the new examination permissions to the built-in roles (new schools get
-- them from PermissionCatalog when their roles are seeded).
CREATE FUNCTION pg_temp.grant_role_permissions(role_codes text[], perms text[]) RETURNS void
    LANGUAGE sql AS $$
    UPDATE school_role r
       SET permissions = (SELECT jsonb_agg(DISTINCT p ORDER BY p)
                            FROM (SELECT jsonb_array_elements_text(r.permissions) AS p
                                  UNION SELECT unnest(perms)) merged),
           updated_at = now()
     WHERE r.is_system AND r.deleted_at IS NULL AND r.code = ANY (role_codes);
$$;

SELECT pg_temp.grant_role_permissions(ARRAY['owner','principal','admin','dept_head'],
                                      ARRAY['exam.manage','exam.mark','exam.approve']);
SELECT pg_temp.grant_role_permissions(ARRAY['teacher'], ARRAY['exam.manage','exam.mark']);
