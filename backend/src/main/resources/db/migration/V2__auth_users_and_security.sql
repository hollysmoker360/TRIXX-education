-- V2: authentication, identity, and device tables. Global tables are excluded from RLS (spec 7.2)
-- and protected by service-layer rules.

CREATE TABLE app_user (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email              text UNIQUE,
    phone              text,
    password_hash      text NOT NULL,
    display_name       text NOT NULL,
    locale             text NOT NULL DEFAULT 'en',
    status             text NOT NULL DEFAULT 'active',
    email_verified_at  timestamptz,
    mfa_secret         text,
    token_version      integer NOT NULL DEFAULT 0,
    is_platform_admin  boolean NOT NULL DEFAULT false,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    version            integer NOT NULL DEFAULT 0,
    deleted_at         timestamptz,
    CONSTRAINT app_user_status_check CHECK (status IN ('active', 'suspended', 'deactivated'))
);
CREATE INDEX idx_app_user_email_lower ON app_user (lower(email::text));

-- Device registration per user (spec 8.1).
CREATE TABLE device_registration (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid NOT NULL REFERENCES app_user (id),
    device_id    text NOT NULL,
    name         text,
    platform     text,
    last_seen_at timestamptz NOT NULL DEFAULT now(),
    revoked_at   timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now(),
    UNIQUE (user_id, device_id)
);

-- Opaque refresh tokens, hashed at rest, rotated on use, reuse detection revokes the family (spec 8.1).
CREATE TABLE refresh_token (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid NOT NULL REFERENCES app_user (id),
    family_id      uuid NOT NULL,
    token_hash     text NOT NULL UNIQUE,
    device_id      text,
    issued_at      timestamptz NOT NULL DEFAULT now(),
    expires_at     timestamptz NOT NULL,
    rotated_at     timestamptz,
    revoked_at     timestamptz,
    replaced_by_id uuid REFERENCES refresh_token (id),
    revoke_reason  text
);
CREATE INDEX idx_refresh_token_family ON refresh_token (family_id);
CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);

-- Single-use, expiring, hashed tokens for verification and password reset (spec 8.1).
CREATE TABLE verification_token (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid NOT NULL REFERENCES app_user (id),
    type       text NOT NULL,
    token_hash text NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT verification_token_type_check CHECK (type IN ('email_verify', 'password_reset'))
);

-- Table-backed feature flags per plan and per school (spec 3.2).
CREATE TABLE feature_flag (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    flag_key   text NOT NULL,
    school_id  uuid,
    enabled    boolean NOT NULL DEFAULT false,
    payload    jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_feature_flag_key_school ON feature_flag (flag_key, coalesce(school_id, '00000000-0000-0000-0000-000000000000'::uuid));
