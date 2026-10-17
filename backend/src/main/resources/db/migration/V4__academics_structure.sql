-- V4: academic structure (spec 6.1, 11.1): years, terms, departments, classes, streams, subjects,
-- classrooms, and period definitions.

CREATE TABLE academic_year (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id  uuid NOT NULL REFERENCES school (id),
    name       text NOT NULL,
    starts_on  date NOT NULL,
    ends_on    date NOT NULL,
    is_active  boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_by uuid,
    version    integer NOT NULL DEFAULT 0,
    deleted_at timestamptz,
    origin_id  text,
    UNIQUE (school_id, id),
    CONSTRAINT academic_year_dates_check CHECK (ends_on > starts_on)
);
CREATE UNIQUE INDEX uq_academic_year_name ON academic_year (school_id, name) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_academic_year_active ON academic_year (school_id) WHERE is_active AND deleted_at IS NULL;

CREATE TABLE term (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id        uuid NOT NULL REFERENCES school (id),
    academic_year_id uuid NOT NULL,
    name           text NOT NULL,
    seq            integer NOT NULL,
    starts_on      date NOT NULL,
    ends_on        date NOT NULL,
    is_current     boolean NOT NULL DEFAULT false,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    created_by     uuid,
    updated_by     uuid,
    version        integer NOT NULL DEFAULT 0,
    deleted_at     timestamptz,
    origin_id      text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, academic_year_id) REFERENCES academic_year (school_id, id),
    CONSTRAINT term_dates_check CHECK (ends_on > starts_on)
);
CREATE UNIQUE INDEX uq_term_name ON term (school_id, academic_year_id, name) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_term_current ON term (school_id) WHERE is_current AND deleted_at IS NULL;

CREATE TABLE department (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           uuid NOT NULL REFERENCES school (id),
    name                text NOT NULL,
    code                text,
    head_membership_id  uuid,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,
    version             integer NOT NULL DEFAULT 0,
    deleted_at          timestamptz,
    origin_id           text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, head_membership_id) REFERENCES school_membership (school_id, id)
);
CREATE UNIQUE INDEX uq_department_name ON department (school_id, name) WHERE deleted_at IS NULL;

CREATE TABLE class_group (
    id                         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id                  uuid NOT NULL REFERENCES school (id),
    name                       text NOT NULL,
    level                      integer,
    department_id              uuid,
    class_teacher_membership_id uuid,
    capacity                   integer,
    created_at                 timestamptz NOT NULL DEFAULT now(),
    updated_at                 timestamptz NOT NULL DEFAULT now(),
    created_by                 uuid,
    updated_by                 uuid,
    version                    integer NOT NULL DEFAULT 0,
    deleted_at                 timestamptz,
    origin_id                  text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, department_id) REFERENCES department (school_id, id),
    FOREIGN KEY (school_id, class_teacher_membership_id) REFERENCES school_membership (school_id, id)
);
CREATE UNIQUE INDEX uq_class_group_name ON class_group (school_id, name) WHERE deleted_at IS NULL;

CREATE TABLE stream (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id     uuid NOT NULL REFERENCES school (id),
    class_group_id uuid NOT NULL,
    name          text NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_by    uuid,
    version       integer NOT NULL DEFAULT 0,
    deleted_at    timestamptz,
    origin_id     text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, class_group_id) REFERENCES class_group (school_id, id)
);
CREATE UNIQUE INDEX uq_stream_name ON stream (school_id, class_group_id, name) WHERE deleted_at IS NULL;

CREATE TABLE subject (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id     uuid NOT NULL REFERENCES school (id),
    name          text NOT NULL,
    code          text,
    department_id uuid,
    is_core       boolean NOT NULL DEFAULT false,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid,
    updated_by    uuid,
    version       integer NOT NULL DEFAULT 0,
    deleted_at    timestamptz,
    origin_id     text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, department_id) REFERENCES department (school_id, id)
);
CREATE UNIQUE INDEX uq_subject_name ON subject (school_id, name) WHERE deleted_at IS NULL;

CREATE TABLE classroom (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id  uuid NOT NULL REFERENCES school (id),
    name       text NOT NULL,
    room_type  text NOT NULL DEFAULT 'standard',
    capacity   integer,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_by uuid,
    version    integer NOT NULL DEFAULT 0,
    deleted_at timestamptz,
    origin_id  text,
    UNIQUE (school_id, id),
    CONSTRAINT classroom_type_check CHECK (room_type IN ('standard', 'lab', 'workshop', 'library', 'field'))
);
CREATE UNIQUE INDEX uq_classroom_name ON classroom (school_id, name) WHERE deleted_at IS NULL;

-- Period definitions: school days, period start/duration, breaks and lunch (spec 12.1).
CREATE TABLE timetable_period (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id        uuid NOT NULL REFERENCES school (id),
    academic_year_id uuid,
    name             text NOT NULL,
    seq              integer NOT NULL,
    starts_at        time NOT NULL,
    ends_at          time NOT NULL,
    is_break         boolean NOT NULL DEFAULT false,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    created_by       uuid,
    updated_by       uuid,
    version          integer NOT NULL DEFAULT 0,
    deleted_at       timestamptz,
    origin_id        text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, academic_year_id) REFERENCES academic_year (school_id, id)
);
CREATE UNIQUE INDEX uq_timetable_period_seq ON timetable_period (school_id, coalesce(academic_year_id, '00000000-0000-0000-0000-000000000000'::uuid), seq) WHERE deleted_at IS NULL;
