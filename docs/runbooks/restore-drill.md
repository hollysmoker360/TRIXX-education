# Runbook — Restore drill (quarterly)

Quarterly procedure to prove that backups actually restore. A backup that has never been
restored is an assumption, not a backup. Run at least **quarterly**, and after any change to
the backup method, storage, or encryption.

Companion: [backup-restore.md](backup-restore.md), [OPERATIONS.md](../../OPERATIONS.md).

## 1. Scope & cadence

| Item | Value |
|------|-------|
| Frequency | Quarterly (minimum); monthly is better |
| Target | Restore the most recent cloud base backup **and** the most recent edge `pg_dump` |
| Duration budget | Cloud restore ≤ agreed RTO; edge restore ≤ 1 hour |
| Owner | DB/ops on-call |

## 2. Preconditions

- [ ] Backup artifacts exist and are not stale (latest base backup / dump within policy).
- [ ] Encryption keys available: field-encryption key, JWT keys
      ([OPERATIONS.md](../../OPERATIONS.md#4-backups)).
- [ ] A **scratch** restore target is provisioned (never restore the drill over production).
- [ ] Change window logged; production is unaffected.

## 3. Procedure

1. **Pick the artifact.** Choose the newest cloud base backup (+ WAL for a target time) and
   the newest edge `pg_dump`.
2. **Restore to a scratch DB** following [backup-restore.md](backup-restore.md#3-restore-pg_restore),
   e.g. `schoolsphere_restore_test`. For cloud/PITR, restore to a chosen point-in-time that
   includes a known transaction.
3. **Verify schema & RLS.**
   - `V1`–`V12` present in `flyway_schema_history`.
   - `FORCE ROW LEVEL SECURITY` is `ENABLED`+`FORCED` on tenant tables; the `tenant_isolation`
     policy exists.
   - `schoolsphere_app` is still non-owner (no ownership of tables), and
     `flyway_schema_history` is revoked from it.
4. **Verify data integrity.** Row counts for key tables (`app_user`, `school`,
   `school_membership`, `student`, `assignment`, `message`, `attendance_event`,
   `change_log`, `sync_operation`) match the source within expectation. Spot-check a known
   record.
5. **Verify the app against the restored DB.** Point a **non-production** backend at the
   scratch DB (`SCHOOLSPHERE_DB_URL`) with the `edge` or `dev` profile. Confirm:
   - `/actuator/health/readiness` → `UP` (DB component `UP`).
   - Login works and an authenticated call with `X-School-Id` returns tenant data.
   - RLS still isolates (a second school's id returns no data).
6. **Verify encrypted fields.** Confirm encrypted columns (e.g. national ID / guardian phone)
   decrypt correctly with the field key — proving key + data were backed up together.
7. **Verify attachments (cloud).** Restore one object-storage file referenced by
   `resource_version.storage_key`.
8. **Measure & record.** Note restore duration, artifact used, and any anomalies.

## 4. Pass / fail criteria

| Check | Pass | Fail |
|-------|------|------|
| Backup artifact freshness | Within policy (e.g. ≤ 24 h for daily, ≤ 35 d for monthly) | Stale or missing |
| Restore completes | Within RTO budget, no errors | Exceeds budget or errors |
| Schema/migrations `V1`–`V12` | All applied, `flyway_schema_history` intact | Missing/failed |
| RLS still enforced | `ENABLE`+`FORCE` on tenant tables; app role non-owner | RLS off or role owns tables |
| Data integrity | Key-table counts match source; known record present | Mismatch/corruption |
| App readiness against restore | `/actuator/health/readiness` `UP` incl. `db` | Not `UP` |
| Tenant isolation | Other-school query returns no rows | Cross-tenant rows visible |
| Encrypted fields decrypt | Spot-check passes with the field key | Decrypt fails |
| Attachments (cloud) | Sample file restored and readable | Missing/corrupt |

**Overall drill = PASS** only if every row passes. Any FAIL opens an incident, is logged, and
gets a fix with a re-run before the next quarter.

## 5. Sign-off & record

| Field | Value |
|-------|-------|
| Drill date | |
| Operator | |
| Artifacts used | (cloud base + time, edge dump filename) |
| Restore duration | |
| Result (PASS/FAIL) | |
| Failed checks | |
| Follow-up actions / owner | |

Retain the record for audit and reference it in
[OPERATIONS.md](../../OPERATIONS.md#4-backups).
