-- V3: tenants, memberships, and role catalog (spec 6, 7, 8.2).

CREATE TABLE school (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name           text NOT NULL,
    slug           text NOT NULL UNIQUE,
    motto          text,
    timezone       text NOT NULL DEFAULT 'Africa/Nairobi',
    default_locale text NOT NULL DEFAULT 'en',
    currency       text NOT NULL DEFAULT 'KES',
    settings       jsonb NOT NULL DEFAULT '{}'::jsonb,
    status         text NOT NULL DEFAULT 'active',
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    version        integer NOT NULL DEFAULT 0,
    deleted_at     timestamptz,
    origin_id      text,
    CONSTRAINT school_status_check CHECK (status IN ('active', 'suspended', 'closed'))
);

CREATE TABLE school_branch (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id  uuid NOT NULL REFERENCES school (id),
    name       text NOT NULL,
    address    text,
    phone      text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_by uuid,
    version    integer NOT NULL DEFAULT 0,
    deleted_at timestamptz,
    origin_id  text,
    UNIQUE (school_id, id)
);

-- School roles are a separate catalog from platform roles (spec 8.2). Built-in roles are seeded
-- per school; schools may create custom roles from the permission catalog.
CREATE TABLE school_role (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   uuid NOT NULL REFERENCES school (id),
    code        text NOT NULL,
    name        text NOT NULL,
    is_system   boolean NOT NULL DEFAULT false,
    permissions jsonb NOT NULL DEFAULT '[]'::jsonb,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_by  uuid,
    version     integer NOT NULL DEFAULT 0,
    deleted_at  timestamptz,
    origin_id   text,
    UNIQUE (school_id, id)
);
CREATE UNIQUE INDEX uq_school_role_code ON school_role (school_id, code) WHERE deleted_at IS NULL;

-- A membership links one global user to one school with one or more roles (spec 6.2).
-- Token invalidation for suspension uses token_version (spec 8.1).
CREATE TABLE school_membership (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id      uuid NOT NULL REFERENCES school (id),
    user_id        uuid NOT NULL REFERENCES app_user (id),
    status         text NOT NULL DEFAULT 'active',
    title          text,
    staff_number   text,
    token_version  integer NOT NULL DEFAULT 0,
    joined_at      timestamptz NOT NULL DEFAULT now(),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    created_by     uuid,
    updated_by     uuid,
    version        integer NOT NULL DEFAULT 0,
    deleted_at     timestamptz,
    origin_id      text,
    CONSTRAINT school_membership_status_check CHECK (status IN ('active', 'invited', 'suspended', 'revoked')),
    UNIQUE (school_id, id)
);
CREATE UNIQUE INDEX uq_school_membership_user ON school_membership (school_id, user_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_school_membership_user ON school_membership (user_id);

CREATE TABLE membership_role (
    membership_id uuid NOT NULL REFERENCES school_membership (id) ON DELETE CASCADE,
    role_id       uuid NOT NULL REFERENCES school_role (id),
    PRIMARY KEY (membership_id, role_id)
);

-- Tenant-level sequences for sync are created in V10.
