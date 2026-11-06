-- V24: Student Success. Early-warning indicators, support referrals,
-- intervention plans, and progress tracking. Advisory only — staff review recommended.

CREATE TABLE student_progress_profile (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       uuid NOT NULL REFERENCES school (id),
    student_id      uuid NOT NULL,
    overall_risk_level varchar(20) NOT NULL DEFAULT 'low',
    attendance_concern boolean NOT NULL DEFAULT false,
    academic_decline boolean NOT NULL DEFAULT false,
    incomplete_work boolean NOT NULL DEFAULT false,
    last_reviewed_at date,
    reviewed_by     uuid,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    deleted_at      timestamptz,
    version         integer NOT NULL DEFAULT 0,
    origin_id       text,
    UNIQUE (school_id, id),
    UNIQUE (school_id, student_id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id),
    CONSTRAINT student_progress_profile_risk_check CHECK (
        overall_risk_level IN ('low','moderate','high','critical')
    )
);
CREATE INDEX idx_student_progress_profile_student ON student_progress_profile (school_id, student_id, deleted_at) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX idx_student_progress_profile_unique_active ON student_progress_profile (school_id, student_id) WHERE deleted_at IS NULL;

CREATE TABLE risk_indicator (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       uuid NOT NULL REFERENCES school (id),
    student_id      uuid NOT NULL,
    indicator_type  varchar(40) NOT NULL,
    severity        varchar(20) NOT NULL,
    description     varchar(500) NOT NULL,
    source_module   varchar(40),
    source_entity_id uuid,
    acknowledged    boolean NOT NULL DEFAULT false,
    acknowledged_by uuid,
    acknowledged_at timestamptz,
    resolved        boolean NOT NULL DEFAULT false,
    resolved_at     timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    deleted_at      timestamptz,
    version         integer NOT NULL DEFAULT 0,
    origin_id       text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id),
    CONSTRAINT risk_indicator_type_check CHECK (
        indicator_type IN ('attendance_drop','grade_decline','missing_work','teacher_concern','guardian_concern')
    ),
    CONSTRAINT risk_indicator_severity_check CHECK (
        severity IN ('low','moderate','high')
    )
);
CREATE INDEX idx_risk_indicator_student ON risk_indicator (school_id, student_id, deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_risk_indicator_ack ON risk_indicator (school_id, acknowledged, resolved) WHERE deleted_at IS NULL;

CREATE TABLE support_referral (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       uuid NOT NULL REFERENCES school (id),
    student_id      uuid NOT NULL,
    referred_by     uuid NOT NULL,
    referred_to     uuid,
    referral_type   varchar(40) NOT NULL,
    priority        varchar(20) NOT NULL DEFAULT 'routine',
    reason          varchar(1000) NOT NULL,
    supporting_evidence jsonb,
    status          varchar(20) NOT NULL DEFAULT 'open',
    outcome_notes   text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    deleted_at      timestamptz,
    version         integer NOT NULL DEFAULT 0,
    origin_id       text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id),
    FOREIGN KEY (school_id, referred_by) REFERENCES school_membership (school_id, id),
    FOREIGN KEY (school_id, referred_to) REFERENCES school_membership (school_id, id),
    CONSTRAINT support_referral_type_check CHECK (
        referral_type IN ('academic','behavioral','health','learning_support','family')
    ),
    CONSTRAINT support_referral_priority_check CHECK (
        priority IN ('routine','urgent','immediate')
    ),
    CONSTRAINT support_referral_status_check CHECK (
        status IN ('open','in_progress','completed','declined')
    )
);
CREATE INDEX idx_support_referral_student ON support_referral (school_id, student_id, deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_support_referral_status ON support_referral (school_id, status, created_at) WHERE deleted_at IS NULL;

CREATE TABLE intervention_plan (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       uuid NOT NULL REFERENCES school (id),
    student_id      uuid NOT NULL,
    referral_id     uuid,
    title           varchar(200) NOT NULL,
    description     text NOT NULL,
    start_date      date NOT NULL,
    review_date     date NOT NULL,
    end_date        date,
    status          varchar(20) NOT NULL DEFAULT 'active',
    coordinator_membership_id uuid NOT NULL,
    guardian_notified boolean NOT NULL DEFAULT false,
    guardian_consent boolean,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    deleted_at      timestamptz,
    version         integer NOT NULL DEFAULT 0,
    origin_id       text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id),
    FOREIGN KEY (school_id, referral_id) REFERENCES support_referral (school_id, id),
    FOREIGN KEY (school_id, coordinator_membership_id) REFERENCES school_membership (school_id, id),
    CONSTRAINT intervention_plan_status_check CHECK (
        status IN ('active','paused','completed','discontinued')
    )
);
CREATE INDEX idx_intervention_plan_student ON intervention_plan (school_id, student_id, deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_intervention_plan_review ON intervention_plan (school_id, review_date, status) WHERE deleted_at IS NULL;

CREATE TABLE support_goal (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       uuid NOT NULL REFERENCES school (id),
    plan_id         uuid NOT NULL,
    description     varchar(500) NOT NULL,
    target_date     date,
    achieved        boolean NOT NULL DEFAULT false,
    achieved_at     timestamptz,
    evidence_ref    varchar(500),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    deleted_at      timestamptz,
    version         integer NOT NULL DEFAULT 0,
    origin_id       text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, plan_id) REFERENCES intervention_plan (school_id, id)
);
CREATE INDEX idx_support_goal_plan ON support_goal (school_id, plan_id, deleted_at) WHERE deleted_at IS NULL;

CREATE TABLE progress_note (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       uuid NOT NULL REFERENCES school (id),
    plan_id         uuid NOT NULL,
    student_id      uuid NOT NULL,
    author_membership_id uuid NOT NULL,
    note_type       varchar(40) NOT NULL,
    content         text NOT NULL,
    is_confidential boolean NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_by      uuid,
    deleted_at      timestamptz,
    version         integer NOT NULL DEFAULT 0,
    origin_id       text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, plan_id) REFERENCES intervention_plan (school_id, id),
    FOREIGN KEY (school_id, student_id) REFERENCES student (school_id, id),
    FOREIGN KEY (school_id, author_membership_id) REFERENCES school_membership (school_id, id),
    CONSTRAINT progress_note_type_check CHECK (
        note_type IN ('observation','review','outcome','guardian_communication')
    )
);
CREATE INDEX idx_progress_note_plan ON progress_note (school_id, plan_id, deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_progress_note_student ON progress_note (school_id, student_id, created_at DESC) WHERE deleted_at IS NULL;

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['student_progress_profile', 'risk_indicator', 'support_referral', 'intervention_plan', 'support_goal', 'progress_note'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING (school_id = current_school()) WITH CHECK (school_id = current_school())',
            t);
    END LOOP;
END $$;
