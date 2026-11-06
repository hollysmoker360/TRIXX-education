-- V23: Marks gradebook. Configurable assessments with components, per-student grade entries,
-- moderation workflow (draft -> submitted -> published), and auditable mark corrections.

CREATE TABLE assessment (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       uuid NOT NULL REFERENCES school (id),
    class_group_id  uuid NOT NULL,
    subject_id      uuid NOT NULL,
    teacher_membership_id uuid NOT NULL,
    term_id         uuid NOT NULL,
    title           varchar(200) NOT NULL,
    type            varchar(40) NOT NULL,
    max_marks       numeric(5,2) NOT NULL,
    weight          numeric(4,2) NOT NULL DEFAULT 1.00,
    date_assessed   date NOT NULL,
    due_date        date,
    grading_scale_id uuid,
    published       boolean NOT NULL DEFAULT false,
    published_at    timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    version         integer NOT NULL DEFAULT 0,
    deleted_at      timestamptz,
    origin_id       text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, class_group_id) REFERENCES class_group (school_id, id),
    FOREIGN KEY (school_id, subject_id) REFERENCES subject (school_id, id),
    FOREIGN KEY (school_id, teacher_membership_id) REFERENCES school_membership (school_id, id),
    FOREIGN KEY (school_id, term_id) REFERENCES term (school_id, id),
    CONSTRAINT assessment_type_check CHECK (type IN ('test','quiz','exam','practical','project')),
    CONSTRAINT assessment_max_marks_check CHECK (max_marks >= 0),
    CONSTRAINT assessment_weight_check CHECK (weight > 0)
);
CREATE INDEX idx_assessment_school_class ON assessment (school_id, class_group_id, deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_assessment_school_term ON assessment (school_id, term_id, deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_assessment_school_subject ON assessment (school_id, subject_id, deleted_at) WHERE deleted_at IS NULL;

CREATE TABLE assessment_component (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       uuid NOT NULL REFERENCES school (id),
    assessment_id   uuid NOT NULL,
    name            varchar(200) NOT NULL,
    max_marks       numeric(5,2) NOT NULL,
    weight          numeric(4,2) NOT NULL DEFAULT 1.00,
    position        integer NOT NULL,
    version         integer NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    deleted_at      timestamptz,
    origin_id       text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, assessment_id) REFERENCES assessment (school_id, id),
    CONSTRAINT component_max_marks_check CHECK (max_marks >= 0),
    CONSTRAINT component_weight_check CHECK (weight > 0)
);
CREATE INDEX idx_assessment_component_assessment ON assessment_component (school_id, assessment_id, position) WHERE deleted_at IS NULL;

CREATE TABLE grade_entry (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       uuid NOT NULL REFERENCES school (id),
    assessment_id   uuid NOT NULL,
    student_id      uuid NOT NULL,
    marks_obtained  numeric(5,2),
    max_marks       numeric(5,2) NOT NULL,
    grade           varchar(10),
    feedback        text,
    status          varchar(20) NOT NULL DEFAULT 'draft',
    submitted_at    timestamptz,
    moderated_by    uuid,
    moderated_at    timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    version         integer NOT NULL DEFAULT 0,
    deleted_at      timestamptz,
    origin_id       text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, assessment_id) REFERENCES assessment (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id),
    CONSTRAINT grade_entry_status_check CHECK (status IN ('draft','submitted','moderated','published')),
    CONSTRAINT grade_entry_marks_check CHECK (marks_obtained IS NULL OR marks_obtained >= 0),
    CONSTRAINT grade_entry_max_marks_check CHECK (max_marks >= 0)
);
CREATE INDEX idx_grade_entry_school_assessment ON grade_entry (school_id, assessment_id, deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_grade_entry_school_student ON grade_entry (school_id, student_id, deleted_at) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX idx_grade_entry_unique_active ON grade_entry (school_id, assessment_id, student_id) WHERE deleted_at IS NULL;

CREATE TABLE mark_correction (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       uuid NOT NULL REFERENCES school (id),
    grade_entry_id  uuid NOT NULL,
    previous_marks  numeric(5,2) NOT NULL,
    new_marks       numeric(5,2) NOT NULL,
    reason          varchar(500) NOT NULL,
    corrected_by    uuid NOT NULL,
    version         integer NOT NULL DEFAULT 0,
    corrected_at    timestamptz NOT NULL DEFAULT now(),
    approved_by     uuid,
    approved_at     timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    deleted_at      timestamptz,
    origin_id       text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, grade_entry_id) REFERENCES grade_entry (school_id, id),
    FOREIGN KEY (school_id, corrected_by) REFERENCES school_membership (school_id, id),
    CONSTRAINT mark_correction_marks_check CHECK (new_marks >= 0)
);
CREATE INDEX idx_mark_correction_entry ON mark_correction (school_id, grade_entry_id, created_at) WHERE deleted_at IS NULL;

CREATE TABLE grade_band (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       uuid NOT NULL REFERENCES school (id),
    name            varchar(50) NOT NULL,
    min_score       numeric(5,2) NOT NULL,
    max_score       numeric(5,2) NOT NULL,
    grade_point     numeric(3,2),
    color_hex       varchar(7),
    version         integer NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    deleted_at      timestamptz,
    origin_id       text,
    UNIQUE (school_id, id),
    CONSTRAINT grade_band_range_check CHECK (min_score <= max_score),
    CONSTRAINT grade_band_score_check CHECK (min_score >= 0 AND max_score <= 100)
);
CREATE INDEX idx_grade_band_school ON grade_band (school_id, min_score, deleted_at) WHERE deleted_at IS NULL;

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['assessment', 'assessment_component', 'grade_entry', 'mark_correction', 'grade_band'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING (school_id = current_school()) WITH CHECK (school_id = current_school())',
            t);
    END LOOP;
END $$;
