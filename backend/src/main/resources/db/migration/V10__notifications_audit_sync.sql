-- V10: notifications, transactional outbox, audit log, and sync infrastructure (spec 3.1, 10.2, 11.7, 13.2).

CREATE TABLE notification (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       uuid NOT NULL REFERENCES school (id),
    user_id         uuid NOT NULL REFERENCES app_user (id),
    notification_type text NOT NULL,
    title           text NOT NULL,
    body            text,
    data            jsonb NOT NULL DEFAULT '{}'::jsonb,
    channel         text NOT NULL DEFAULT 'in_app',
    state           text NOT NULL DEFAULT 'pending',
    created_at      timestamptz NOT NULL DEFAULT now(),
    sent_at         timestamptz,
    read_at         timestamptz,
    UNIQUE (school_id, id),
    CONSTRAINT notification_channel_check CHECK (channel IN ('in_app', 'email', 'sms', 'push')),
    CONSTRAINT notification_state_check CHECK (state IN ('pending', 'sent', 'failed', 'read'))
);
CREATE INDEX idx_notification_user ON notification (user_id, state, created_at DESC);

-- Transactional outbox: events are written in the same transaction as the business change and
-- published by a separate worker (spec 3.1). Never publish events directly inside business transactions.
CREATE TABLE notification_outbox (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id       uuid REFERENCES school (id),
    topic           text NOT NULL,
    payload         jsonb NOT NULL,
    status          text NOT NULL DEFAULT 'pending',
    attempts        integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    last_error      text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    processed_at    timestamptz,
    CONSTRAINT notification_outbox_status_check CHECK (status IN ('pending', 'processing', 'done', 'failed'))
);
CREATE INDEX idx_notification_outbox_pending ON notification_outbox (status, next_attempt_at);

-- Append-only audit log with restricted access (spec 13.2).
CREATE TABLE audit_log (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           uuid,
    actor_user_id       uuid,
    actor_membership_id uuid,
    action              text NOT NULL,
    entity              text,
    entity_id           uuid,
    before_state        jsonb,
    after_state         jsonb,
    device_id           text,
    ip_address          text,
    created_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_log_school ON audit_log (school_id, created_at DESC);
CREATE INDEX idx_audit_log_entity ON audit_log (school_id, entity, entity_id);

-- Per-tenant monotonic change feed for sync pull (spec 3.1, 10.2). A single global sequence is
-- monotonic within each tenant when filtered by school_id.
CREATE SEQUENCE change_seq_seq;

CREATE TABLE change_log (
    change_seq  bigint PRIMARY KEY DEFAULT nextval('change_seq_seq'),
    school_id   uuid NOT NULL,
    entity      text NOT NULL,
    entity_id   uuid NOT NULL,
    op          text NOT NULL,
    version     integer,
    changed_at  timestamptz NOT NULL DEFAULT now(),
    actor_user_id uuid,
    CONSTRAINT change_log_op_check CHECK (op IN ('upsert', 'delete'))
);
CREATE INDEX idx_change_log_school_seq ON change_log (school_id, change_seq);

-- Idempotency of sync push operations: every opId is stored with its result for at least the
-- retention window (spec 10.3).
CREATE TABLE sync_operation (
    op_id        uuid PRIMARY KEY,
    school_id    uuid NOT NULL,
    device_id    text,
    entity       text NOT NULL,
    entity_id    uuid NOT NULL,
    op_type      text NOT NULL,
    result       jsonb NOT NULL,
    result_kind  text NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now(),
    expires_at   timestamptz,
    CONSTRAINT sync_operation_result_check CHECK (result_kind IN ('applied', 'duplicate', 'conflict', 'rejected'))
);
CREATE INDEX idx_sync_operation_expiry ON sync_operation (expires_at);

-- Registered devices per user with pull cursors (spec 8.1, 10.7).
CREATE TABLE sync_device (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id      uuid NOT NULL REFERENCES school (id),
    user_id        uuid NOT NULL REFERENCES app_user (id),
    device_id      text NOT NULL,
    name           text,
    platform       text,
    last_pull_seq  bigint NOT NULL DEFAULT 0,
    last_seen_at   timestamptz NOT NULL DEFAULT now(),
    revoked_at     timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (school_id, user_id, device_id)
);
