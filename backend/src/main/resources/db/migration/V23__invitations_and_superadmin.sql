-- V23: Account invitations for school onboarding and parent-child setup.

CREATE TABLE account_invitation (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    token            text NOT NULL UNIQUE,
    email            text NOT NULL,
    role_code        text NOT NULL,
    school_id        uuid REFERENCES school (id),
    child_student_id uuid REFERENCES student (id),
    invited_by_id    uuid REFERENCES app_user (id),
    status           text NOT NULL DEFAULT 'PENDING',
    expires_at       timestamptz NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    accepted_at      timestamptz,
    CONSTRAINT account_invitation_status_check CHECK (status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED'))
);

CREATE INDEX idx_account_invitation_token ON account_invitation (token);
CREATE INDEX idx_account_invitation_email ON account_invitation (lower(email));
CREATE INDEX idx_account_invitation_school ON account_invitation (school_id);
