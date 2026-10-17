-- V13: subscriptions, plans, and payments (spec 3.2, 13.2; ADR-007). Billing lives in its own
-- schema so payment data is separable from the school data plane and can later be served by
-- dedicated credentials without touching core tables. School-fee collection stays out of scope;
-- these tables only carry platform subscription billing.

CREATE SCHEMA IF NOT EXISTS billing;

-- Plan catalogue: platform-owned, not tenant data, therefore no row-level security.
CREATE TABLE billing.plan (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code          text NOT NULL UNIQUE,
    name          text NOT NULL,
    description   text,
    price_minor   bigint NOT NULL DEFAULT 0 CHECK (price_minor >= 0),
    currency      text NOT NULL DEFAULT 'KES',
    interval_unit text NOT NULL DEFAULT 'month' CHECK (interval_unit IN ('month', 'year')),
    max_students  integer CHECK (max_students IS NULL OR max_students > 0),
    max_teachers  integer CHECK (max_teachers IS NULL OR max_teachers > 0),
    features      jsonb NOT NULL DEFAULT '[]'::jsonb,
    trial_days    integer NOT NULL DEFAULT 14 CHECK (trial_days >= 0),
    sort_order    integer NOT NULL DEFAULT 0,
    active        boolean NOT NULL DEFAULT true,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    version       integer NOT NULL DEFAULT 0
);

-- One subscription per school. Lifecycle: trial -> active -> past_due -> suspended -> cancelled.
CREATE TABLE billing.school_subscription (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id              uuid NOT NULL UNIQUE REFERENCES school (id),
    plan_id                uuid NOT NULL REFERENCES billing.plan (id),
    status                 text NOT NULL CHECK (status IN ('trial', 'active', 'past_due', 'suspended', 'cancelled')),
    trial_started_at       timestamptz,
    trial_ends_at          timestamptz,
    current_period_start   timestamptz,
    current_period_end     timestamptz,
    grace_ends_at          timestamptz,
    cancel_at_period_end   boolean NOT NULL DEFAULT false,
    cancelled_at           timestamptz,
    provider_customer_code text,
    created_by             uuid,
    updated_by             uuid,
    deleted_at             timestamptz,
    origin_id              text,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    version                integer NOT NULL DEFAULT 0
);

CREATE INDEX ix_school_subscription_status ON billing.school_subscription (status, current_period_end);

CREATE TABLE billing.payment (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id          uuid NOT NULL,
    subscription_id    uuid REFERENCES billing.school_subscription (id),
    plan_id            uuid REFERENCES billing.plan (id),
    provider           text NOT NULL,
    provider_reference text,
    amount_minor       bigint NOT NULL CHECK (amount_minor >= 0),
    currency           text NOT NULL,
    status             text NOT NULL CHECK (status IN ('pending', 'success', 'failed', 'refunded')),
    channel            text,
    failure_reason     text,
    paid_at            timestamptz,
    raw_payload        jsonb,
    created_by         uuid,
    updated_by         uuid,
    deleted_at         timestamptz,
    origin_id          text,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    version            integer NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_payment_provider_reference
    ON billing.payment (provider, provider_reference) WHERE provider_reference IS NOT NULL;
CREATE INDEX ix_payment_school_created ON billing.payment (school_id, created_at DESC);

-- Row-level security (spec 7.2): billing rows follow the same tenant isolation as school data.
ALTER TABLE billing.school_subscription ENABLE ROW LEVEL SECURITY;
ALTER TABLE billing.school_subscription FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON billing.school_subscription
    USING (school_id = current_school()) WITH CHECK (school_id = current_school());

ALTER TABLE billing.payment ENABLE ROW LEVEL SECURITY;
ALTER TABLE billing.payment FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON billing.payment
    USING (school_id = current_school()) WITH CHECK (school_id = current_school());

-- Feature flags were created in V2 without isolation. Global flags (school_id IS NULL) stay
-- readable everywhere; school overrides are tenant-isolated like the rest of the data plane.
ALTER TABLE feature_flag ENABLE ROW LEVEL SECURITY;
ALTER TABLE feature_flag FORCE ROW LEVEL SECURITY;
CREATE POLICY feature_flag_isolation ON feature_flag
    USING (school_id = current_school() OR school_id IS NULL)
    WITH CHECK (school_id = current_school() OR (school_id IS NULL AND current_school() IS NULL));

-- Application role grants (default privileges are set per schema, so billing is granted explicitly).
GRANT USAGE ON SCHEMA billing TO schoolsphere_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA billing TO schoolsphere_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA billing TO schoolsphere_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA billing
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO schoolsphere_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA billing
    GRANT USAGE, SELECT ON SEQUENCES TO schoolsphere_app;

-- Plan catalogue (prices in minor units: 250000 = KES 2,500.00).
INSERT INTO billing.plan (code, name, description, price_minor, currency, max_students, max_teachers,
                          features, trial_days, sort_order)
VALUES
    ('starter', 'Starter', 'Single-campus essentials', 0, 'KES', 150, 25,
     '["timetable", "attendance", "gradebook"]'::jsonb, 14, 10),
    ('standard', 'Standard', 'Multi-stream schools running the full academic cycle', 250000, 'KES', 800, 120,
     '["timetable", "attendance", "gradebook", "learning", "quizzes", "messaging"]'::jsonb, 14, 20),
    ('premium', 'Premium', 'Groups and large schools, including offline edge nodes', 750000, 'KES', NULL, NULL,
     '["timetable", "attendance", "gradebook", "learning", "quizzes", "messaging", "edge_sync", "analytics", "api_access"]'::jsonb,
     14, 30);

-- Global feature flags (school_id IS NULL) used by clients to bootstrap optional surfaces.
INSERT INTO feature_flag (flag_key, school_id, enabled, payload)
VALUES
    ('chat.enabled', NULL, true, NULL),
    ('assignments.offline_submit', NULL, true, NULL),
    ('lessons.recording', NULL, false, NULL);
