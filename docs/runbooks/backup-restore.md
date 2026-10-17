# Runbook — Backup & Restore

Backup and restore for the SchoolSphere PostgreSQL database (Flyway-managed, RLS-enforced).
Local toolchain paths and the **dev port 5433** are used throughout; for cloud/edge substitute
the target host/port and a dedicated backup role.

Companion: [restore-drill.md](restore-drill.md) (quarterly verification),
[OPERATIONS.md](OPERATIONS.md#4-backups).

## 1. Roles & connection

- **Owner role `schoolsphere`** owns the schema and runs Flyway — use it for backup/restore.
- **App role `schoolsphere_app`** is non-owner and only has DML — never use it for
  `pg_dump`/`pg_restore` of the whole database.
- Local dev: host `localhost`, port **5433**, database `schoolsphere`, data dir `D:\Trixx\pgdata`.
- Ensure the server is running: `D:\Trixx\tools\pgsql\bin\pg_ctl.exe -D D:\Trixx\pgdata status`.

Set a password for the tools if needed:

```powershell
$env:PGPASSWORD = 'schoolsphere_dev'   # owner password (dev default)
```

## 2. Logical backup (pg_dump) — dev / edge

```powershell
# Full database, custom format (compressed, restorable with pg_restore)
D:\Trixx\tools\pgsql\bin\pg_dump.exe -h localhost -p 5433 -U schoolsphere -d schoolsphere `
  -Fc -f D:\Trixx\backups\schoolsphere_$(Get-Date -Format yyyyMMdd_HHmmss).dump

# Plain-SQL variant (human readable / diffable)
D:\Trixx\tools\pgsql\bin\pg_dump.exe -h localhost -p 5433 -U schoolsphere -d schoolsphere `
  -Fp -f D:\Trixx\backups\schoolsphere.sql

# Schema only (no data) — useful for inspecting DDL without touching data
D:\Trixx\tools\pgsql\bin\pg_dump.exe -h localhost -p 5433 -U schoolsphere -d schoolsphere `
  --schema-only -f D:\Trixx\backups\schoolsphere_schema.sql
```

Notes:
- Back up the **owner** view so `flyway_schema_history` and RLS policies are included.
- Store backups encrypted at rest; they contain sensitive child data
  (see [SECURITY.md](../../SECURITY.md)).
- The app role is revoked from `flyway_schema_history`, so an app-role dump is incomplete.

## 3. Restore (pg_restore)

Restore into a **clean** database (or with `--clean --if-exists` onto an existing one).

```powershell
# Create a fresh target database (example: restore into a scratch DB first)
D:\Trixx\tools\pgsql\bin\psql.exe -h localhost -p 5433 -U schoolsphere -d postgres `
  -c "DROP DATABASE IF EXISTS schoolsphere_restore_test;"
D:\Trixx\tools\pgsql\bin\psql.exe -h localhost -p 5433 -U schoolsphere -d postgres `
  -c "CREATE DATABASE schoolsphere_restore_test OWNER schoolsphere;"

# Restore the custom-format dump into it
D:\Trixx\tools\pgsql\bin\pg_restore.exe -h localhost -p 5433 -U schoolsphere `
  -d schoolsphere_restore_test --no-owner --no-privileges `
  D:\Trixx\backups\schoolsphere_YYYYMMDD_HHmmss.dump
```

In-place restore of the live database (takes it offline):

```powershell
D:\Trixx\tools\pgsql\bin\pg_restore.exe -h localhost -p 5433 -U schoolsphere `
  -d schoolsphere --clean --if-exists --no-owner --no-privileges `
  D:\Trixx\backups\schoolsphere_YYYYMMDD_HHmmss.dump
```

After any restore:
1. Re-apply grants/RLS if the restore path skipped them (they are inside the dump, but verify
   `FORCE ROW LEVEL SECURITY` is still set and `schoolsphere_app` is still non-owner).
2. Confirm the app connects as `schoolsphere_app` and RLS still isolates tenants.
3. Run the app's readiness check (`/actuator/health/readiness`).

## 4. Continuous backup / PITR (cloud, recommended)

For the cloud system-of-record use **pgBackRest** with WAL archiving so any point in time can
be restored:

1. Enable `wal_level = replica` (default in modern PG) and configure `archive_mode`/`archive_command`
   (or pgBackRest `repo` + `archive-get`/`archive-put`).
2. Take a **base backup** with `pgBackRest` and schedule incremental/differential backups.
3. Keep WAL segments until the next base backup; set retention (e.g. 7–30 days).
4. Store the repo encrypted and off-host.

PITR restore sketch:

```powershell
# (pgBackRest) restore to a target time, then start Postgres
pgBackRest.exe --stanza=schoolsphere --delta --type=time --target="2026-10-16T02:00:00" restore
```

Verify archiving is healthy daily (no gaps in the archive, `pg_stat_archive`/pgBackRest
status). Details vary by pgBackRest config; see the pgBackRest docs for the stanza.

## 5. What else to back up

| Item | Why |
|------|-----|
| Object-storage bucket (learning-resource attachments) | Files referenced by `resource_version.storage_key` |
| Field-encryption key (`schoolschools.security.field-key`) | Losing it makes encrypted columns unrecoverable |
| JWT signing keys (`SCHOOLSPHERE_JWT_SECRET`) | Needed to keep issued tokens valid across a restore |

## 6. Schedule & retention

| Database | Method | Frequency | Retention |
|----------|--------|-----------|-----------|
| Cloud | pgBackRest WAL + base backups | continuous + daily base | 7–30 days + monthly archives |
| Edge | `pg_dump` to encrypted storage | daily | last N dumps + monthly |
| Dev | ad-hoc `pg_dump` | as needed | — |

Next: run the quarterly [restore-drill.md](restore-drill.md) to prove the backups work.
