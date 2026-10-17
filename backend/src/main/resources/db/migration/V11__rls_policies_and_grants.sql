-- V11: row-level security and role grants (spec 7.2). The application connects as schoolsphere_app,
-- which does not own the tables and is not a superuser. FORCE ROW LEVEL SECURITY additionally binds
-- the table owner. Every tenant-scoped transaction sets app.current_school (transaction-local).

CREATE OR REPLACE FUNCTION current_school() RETURNS uuid
    LANGUAGE sql STABLE AS
$$ SELECT nullif(current_setting('app.current_school', true), '')::uuid $$;

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'school_branch', 'academic_year', 'term', 'department', 'class_group', 'stream',
        'subject', 'classroom', 'timetable_period',
        'student', 'teacher', 'parent_profile', 'guardian_link', 'enrollment',
        'enrollment_transition', 'teacher_assignment', 'grading_scale',
        'learning_resource', 'resource_version', 'resource_bookmark', 'resource_completion',
        'assignment', 'submission', 'quiz', 'quiz_question', 'quiz_attempt',
        'task', 'channel', 'channel_member', 'message',
        'attendance_session', 'attendance_event',
        'timetable_version', 'timetable_entry',
        'notification', 'change_log', 'sync_operation', 'sync_device'
    ] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I USING (school_id = current_school()) WITH CHECK (school_id = current_school())',
            t);
    END LOOP;
END $$;

-- Audit log: tenant rows are isolated; platform-level rows (school_id IS NULL) are only visible
-- when no tenant context is set (platform/admin operations).
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;
CREATE POLICY audit_isolation ON audit_log
    USING (school_id = current_school() OR (school_id IS NULL AND current_school() IS NULL))
    WITH CHECK (school_id = current_school() OR (school_id IS NULL AND current_school() IS NULL));

-- Application role grants. The role itself is created during provisioning
-- (deploy/scripts/provision-db.sql) because creating roles requires superuser.
GRANT USAGE ON SCHEMA public TO schoolsphere_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO schoolsphere_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO schoolsphere_app;
REVOKE ALL ON TABLE flyway_schema_history FROM schoolsphere_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO schoolsphere_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO schoolsphere_app;
