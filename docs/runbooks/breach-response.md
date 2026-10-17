# Runbook — Breach response

Incident response for a security or personal-data breach affecting SchoolSphere. SchoolSphere
processes **children's data**, which is sensitive personal data — treat any suspected breach of
student, guardian, or staff personal data as a reportable incident until assessed.

Companion: [SECURITY.md](../../SECURITY.md), [OPERATIONS.md](../../OPERATIONS.md).

## 1. Kenya Data Protection Act 2019 — notification timelines

Under the **Data Protection Act, 2019 (No. 24 of 2019)** and the ODPC (Office of the Data
Protection Commissioner) guidance:

| Who | When | To whom |
|-----|------|---------|
| Data controller / processor (the school / platform) | **Without undue delay, and where feasible within 72 hours** of becoming aware of a personal data breach | **ODPC** (Data Protection Commissioner) |
| Data controller / processor | **Without undue delay** where the breach is likely to result in a **high risk** to the rights and freedoms of the data subject (e.g. exposure of a child's personal data) | **Affected data subjects** (students / parents / guardians) |
| Data processor | Notify the controller without undue delay on becoming aware | The controller (school) |

The 72-hour clock starts when the organization **becomes aware** (has a reasonable belief a
breach occurred), not when the investigation completes. If notification is delayed past 72
hours, document the reasons. Notify ODPC even if the full impact is not yet known; provide
supplementary information as it becomes available.

The ODPC notification should include, per §43:
- Nature of the breach (categories and approximate number of records/data subjects).
- Contact details of the controller/processor (or representative).
- Likely consequences.
- Measures taken or proposed to address the breach and mitigate adverse effects.

**If in doubt, notify.** Late or missed notification is itself an enforcement risk.

## 2. Severity triage (first 30 minutes)

| Signal | Severity |
|--------|----------|
| Exposed student/guardian personal data, national IDs, medical notes, or contacts | **Critical** |
| Cross-tenant data exposure (one school seeing another) | **Critical** |
| Compromised admin/owner account or JWT signing key | **Critical** |
| Lost/stolen device with cached data; single-account compromise | High |
| Internal-only exposure, no external access | Medium/low |

## 3. Containment

1. **Stop the bleeding.**
   - Suspend affected accounts (`app_user.status`, `school_membership.status`) and **bump
     `token_version`** to invalidate live sessions/tokens instantly.
   - Revoke refresh-token families for the affected users (`AuthService.logout` /
     `revokeFamily`), and revoke registered sync devices (`DELETE /api/v1/sync/devices/{id}`).
   - If a signing key is suspected compromised, **rotate JWT keys** (`SCHOOLSPHERE_JWT_SECRET`
     `signing-keys`; first key signs, all keys verify) and redeploy so old tokens fail.
2. **Preserve evidence.** Snapshot/`pg_dump` the relevant tables (`audit_log`, `app_user`,
   `refresh_token`, `sync_device`, affected domain tables) before remediation. Do **not**
   delete audit rows — `audit_log` is append-only and is your timeline.
3. **Scope it.** Use `audit_log` (action, entity, actor, device, IP) and `change_log` to
   determine which records and tenants were accessed. Check `sync_operation` for abnormal
   push volume (exfiltration via sync).
4. **Revoke access paths.** Force password resets for affected accounts; rotate the field
   encryption key if key compromise is suspected (note: rotating it requires re-encrypting
   existing columns — plan a re-encryption job).
5. **Eradicate & recover.** Patch the root cause, restore from a known-good
   [backup](backup-restore.md) if data was tampered with, and verify RLS/authorization still
   isolate tenants.

## 4. Assessment & notification

1. Determine, for each affected data subject group, whether the breach is likely to result in
   **high risk** to their rights and freedoms (children's data is presumed high risk).
2. Prepare the **ODPC notification** (§43 content above) and file within **72 hours** of
   awareness, with reasons for any delay.
3. Notify **affected data subjects without undue delay** where high risk applies, in plain
   language: what happened, what data was involved, what they should do, and what you have
   done. For students, address parents/guardians.
4. Notify the affected **school(s)** (controllers) if you are acting as a processor.

## 5. Post-incident

- Root-cause analysis and timeline; feed findings into [SECURITY.md](../../SECURITY.md)
  "Known gaps".
- Update controls (enable the missing ones flagged there: rate limiting, MFA, verification /
  password reset).
- Record lessons; schedule a [restore-drill](restore-drill.md) if backups were involved.
- Retain the incident record for regulatory audit.

## 6. Roles & contacts

| Role | Responsibility |
|------|----------------|
| Incident lead | Coordinates triage, containment, decisions |
| DB / ops | Evidence snapshots, key rotation, restores |
| Legal / DPO | ODPC notification, data-subject notification, regulatory liaison |
| School liaison | Per-school communication to affected parents/guardians |

Keep a current contact list for the ODPC and the school's DPO/designated contact.
