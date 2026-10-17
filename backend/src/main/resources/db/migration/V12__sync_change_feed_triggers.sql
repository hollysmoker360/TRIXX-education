-- V12: change-feed triggers. Every mutation on a synchronized table appends a row to change_log in
-- the same transaction, so the feed cannot drift from the data regardless of code path.

CREATE OR REPLACE FUNCTION schoolsphere_changelog() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
    v_school_id uuid;
    v_entity_id uuid;
    v_version   integer;
    v_op        text;
    v_actor     uuid;
BEGIN
    v_actor := nullif(current_setting('app.actor_user', true), '')::uuid;

    -- to_jsonb tolerates tables without version/deleted_at columns (append-only tables such as
    -- message or attendance_event); NEW.version would raise "record has no field" there.
    IF TG_OP = 'DELETE' THEN
        v_school_id := OLD.school_id; v_entity_id := OLD.id;
        v_version := (to_jsonb(OLD) ->> 'version')::integer; v_op := 'delete';
    ELSIF TG_OP = 'INSERT' THEN
        v_school_id := NEW.school_id; v_entity_id := NEW.id;
        v_version := (to_jsonb(NEW) ->> 'version')::integer;
        v_op := CASE WHEN (to_jsonb(NEW) ->> 'deleted_at') IS NOT NULL THEN 'delete' ELSE 'upsert' END;
    ELSE
        v_school_id := NEW.school_id; v_entity_id := NEW.id;
        v_version := (to_jsonb(NEW) ->> 'version')::integer;
        v_op := CASE WHEN (to_jsonb(NEW) ->> 'deleted_at') IS NOT NULL THEN 'delete' ELSE 'upsert' END;
    END IF;

    INSERT INTO change_log (school_id, entity, entity_id, op, version, actor_user_id)
    VALUES (v_school_id, TG_ARGV[0], v_entity_id, v_op, v_version, v_actor);

    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;

DO $$
DECLARE t text;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'student', 'teacher', 'parent_profile', 'guardian_link', 'enrollment',
        'teacher_assignment', 'grading_scale',
        'learning_resource', 'resource_version', 'resource_bookmark', 'resource_completion',
        'assignment', 'submission', 'quiz', 'quiz_question', 'quiz_attempt',
        'task', 'channel', 'channel_member', 'message',
        'attendance_session', 'attendance_event',
        'timetable_version', 'timetable_entry',
        'academic_year', 'term', 'department', 'class_group', 'stream', 'subject', 'classroom'
    ] LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_changelog AFTER INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION schoolsphere_changelog(%L)',
            t, t);
    END LOOP;
END $$;
