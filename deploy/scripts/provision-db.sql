-- SchoolSphere local database provisioning (run as the owner role schoolsphere).
-- Creates the non-owner application role used by the runtime so row-level security applies
-- (spec 7.2: the application role does not own the tables and is not a superuser).

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'schoolsphere_app') THEN
        CREATE ROLE schoolsphere_app LOGIN PASSWORD 'schoolsphere_app_dev';
    END IF;
END
$$;

GRANT CONNECT ON DATABASE schoolsphere TO schoolsphere_app;
GRANT USAGE ON SCHEMA public TO schoolsphere_app;

-- Objects created later by Flyway (as schoolsphere) are covered by default privileges.
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO schoolsphere_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO schoolsphere_app;
