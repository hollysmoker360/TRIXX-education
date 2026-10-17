-- V7: personal tasks (spec 11.3) and messaging (spec 11.4).

-- Personal tasks. A task keyed by (owner, key) makes duplicates impossible for generated tasks
-- such as (student, assignment) suggestions.
CREATE TABLE task (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id            uuid NOT NULL REFERENCES school (id),
    owner_user_id        uuid NOT NULL REFERENCES app_user (id),
    owner_membership_id  uuid,
    title                text NOT NULL,
    description          text,
    due_at               timestamptz,
    priority             text NOT NULL DEFAULT 'medium',
    category             text,
    related_subject_id   uuid,
    related_assignment_id uuid,
    task_key             text,
    reminder_at          timestamptz,
    done_at              timestamptz,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    created_by           uuid,
    updated_by           uuid,
    version              integer NOT NULL DEFAULT 0,
    deleted_at           timestamptz,
    origin_id            text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, related_subject_id) REFERENCES subject (school_id, id),
    FOREIGN KEY (school_id, related_assignment_id) REFERENCES assignment (school_id, id),
    CONSTRAINT task_priority_check CHECK (priority IN ('low', 'medium', 'high'))
);
CREATE INDEX idx_task_owner ON task (school_id, owner_user_id, done_at, due_at);
CREATE UNIQUE INDEX uq_task_dedupe_key ON task (school_id, owner_user_id, task_key) WHERE task_key IS NOT NULL AND deleted_at IS NULL;

-- Channels: department (staff only), class and subject groups, permission-controlled direct (spec 11.4).
CREATE TABLE channel (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id      uuid NOT NULL REFERENCES school (id),
    channel_type   text NOT NULL,
    name           text NOT NULL,
    department_id  uuid,
    class_group_id uuid,
    subject_id     uuid,
    staff_only     boolean NOT NULL DEFAULT false,
    archived_at    timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    created_by     uuid,
    updated_by     uuid,
    version        integer NOT NULL DEFAULT 0,
    deleted_at     timestamptz,
    origin_id      text,
    UNIQUE (school_id, id),
    FOREIGN KEY (school_id, department_id) REFERENCES department (school_id, id),
    FOREIGN KEY (school_id, class_group_id) REFERENCES class_group (school_id, id),
    FOREIGN KEY (school_id, subject_id) REFERENCES subject (school_id, id),
    CONSTRAINT channel_type_check CHECK (channel_type IN ('department', 'class', 'subject', 'direct'))
);

CREATE TABLE channel_member (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id     uuid NOT NULL REFERENCES school (id),
    channel_id    uuid NOT NULL,
    membership_id uuid NOT NULL,
    member_role   text NOT NULL DEFAULT 'member',
    last_read_seq bigint NOT NULL DEFAULT 0,
    muted         boolean NOT NULL DEFAULT false,
    joined_at     timestamptz NOT NULL DEFAULT now(),
    UNIQUE (school_id, id),
    UNIQUE (channel_id, membership_id),
    FOREIGN KEY (school_id, channel_id) REFERENCES channel (school_id, id),
    FOREIGN KEY (school_id, membership_id) REFERENCES school_membership (school_id, id),
    CONSTRAINT channel_member_role_check CHECK (member_role IN ('member', 'moderator'))
);
CREATE INDEX idx_channel_member_membership ON channel_member (school_id, membership_id);

-- Messages are append-only; edits are versioned events; dedupe on client-generated id (spec 10.4, 11.4).
CREATE TABLE message (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id           uuid NOT NULL REFERENCES school (id),
    channel_id          uuid NOT NULL,
    client_id           uuid NOT NULL,
    sender_membership_id uuid NOT NULL,
    body                text NOT NULL,
    reply_to_id         uuid,
    attachments         jsonb NOT NULL DEFAULT '[]'::jsonb,
    seq                 bigint GENERATED ALWAYS AS IDENTITY,
    sent_at             timestamptz NOT NULL DEFAULT now(),
    edited_at           timestamptz,
    deleted_at          timestamptz,
    deleted_by_membership_id uuid,
    reported_at         timestamptz,
    report_reason       text,
    search_vector       tsvector GENERATED ALWAYS AS (to_tsvector('english', coalesce(body, ''))) STORED,
    UNIQUE (school_id, id),
    UNIQUE (channel_id, client_id),
    FOREIGN KEY (school_id, channel_id) REFERENCES channel (school_id, id),
    FOREIGN KEY (school_id, sender_membership_id) REFERENCES school_membership (school_id, id)
);
CREATE INDEX idx_message_channel_seq ON message (channel_id, seq);
CREATE INDEX idx_message_search ON message USING gin (search_vector);
