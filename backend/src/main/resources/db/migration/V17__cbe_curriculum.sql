-- V17: CBE curriculum module (spec: CBE, E-Learning, Examinations & Student Success).

CREATE TABLE curriculum_version (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id        uuid NOT NULL REFERENCES school (id),
    code             text NOT NULL,
    title            text NOT NULL,
    status           text NOT NULL DEFAULT 'DRAFT',
    version          integer NOT NULL DEFAULT 1,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    created_by       uuid,
    updated_by       uuid,
    deleted_at       timestamptz,
    CONSTRAINT curriculum_version_status_check CHECK (status IN ('DRAFT','IN_REVIEW','APPROVED','PUBLISHED','RETIRED'))
);

CREATE UNIQUE INDEX uq_curriculum_version_code ON curriculum_version (school_id, code) WHERE deleted_at IS NULL;

CREATE TABLE competency (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id             uuid NOT NULL REFERENCES school (id),
    curriculum_version_id uuid NOT NULL REFERENCES curriculum_version (id),
    parent_id             uuid REFERENCES competency (id),
    code                  text NOT NULL,
    title                 text NOT NULL,
    description           text,
    status                text NOT NULL DEFAULT 'DRAFT',
    evidence_types        text[] NOT NULL DEFAULT '{}',
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    created_by            uuid,
    updated_by            uuid,
    deleted_at            timestamptz,
    CONSTRAINT competency_status_check CHECK (status IN ('DRAFT','IN_REVIEW','APPROVED','PUBLISHED','RETIRED'))
);

CREATE TABLE rubric (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id  uuid NOT NULL REFERENCES school (id),
    title      text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_by uuid,
    deleted_at timestamptz
);

CREATE TABLE rubric_criterion (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   uuid NOT NULL REFERENCES school (id),
    rubric_id   uuid NOT NULL REFERENCES rubric (id),
    title       text NOT NULL,
    description text,
    levels      text[] NOT NULL DEFAULT '{}',
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted_at  timestamptz
);

-- RLS
ALTER TABLE curriculum_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE curriculum_version FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON curriculum_version
    USING (school_id = current_school()) WITH CHECK (school_id = current_school());

ALTER TABLE competency ENABLE ROW LEVEL SECURITY;
ALTER TABLE competency FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON competency
    USING (school_id = current_school()) WITH CHECK (school_id = current_school());

ALTER TABLE rubric ENABLE ROW LEVEL SECURITY;
ALTER TABLE rubric FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON rubric
    USING (school_id = current_school()) WITH CHECK (school_id = current_school());

ALTER TABLE rubric_criterion ENABLE ROW LEVEL SECURITY;
ALTER TABLE rubric_criterion FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON rubric_criterion
    USING (school_id = current_school()) WITH CHECK (school_id = current_school());
