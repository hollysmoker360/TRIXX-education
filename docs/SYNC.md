# Offline sync

Reference for the implemented sync protocol (spec §10). Server code:
`backend/src/main/java/com/schoolsphere/sync/` (`SyncService`, `ChangeFeedReader`,
`SyncController`). Client code: `app/lib/core/sync/offline_queue.dart`.

Both cloud and edge expose the same `/api/v1/sync/**` endpoints; the edge acts as a client of
the cloud API for replication (see [Edge server duties](#edge-server-duties)).

## 1. Concepts

| Concept | Meaning |
|---------|---------|
| `change_seq` | Per-tenant monotonic sequence (`change_log.change_seq`, global sequence filtered by `school_id`). Cursor value for pull. |
| `opId` | Client-generated UUID (v4) identifying one queued operation. Primary key of `sync_operation`; the idempotency key for push. |
| `entity` | Sync entity type, e.g. `task`, `message`, `attendance_event`. |
| `op` / `type` | `upsert`/`delete` in the feed; `create`/`update`/`delete` in push operations. |
| Result kind | `applied`, `duplicate`, `rejected` (also `conflict` is reserved; see [Conflict policy](#4-conflict-policy-as-implemented)). |

## 2. Push — `POST /api/v1/sync/push`

Auth: JWT + `X-School-Id` (`@policy.can('school.view')`). Device id in body and/or
`X-Device-Id` header.

```jsonc
{
  "deviceId": "…",           // client-generated device id
  "batchId": "…",            // UUIDv4 for the batch
  "operations": [
    {
      "opId": "…",           // UUIDv4, idempotency key
      "entity": "task",      // task | message | attendance_event
      "entityId": "…",       // UUID of the entity (client-generated for create)
      "type": "create",      // create | update | delete
      "baseVersion": null,   // optional optimistic-concurrency hint
      "fields": { },         // entity-type-specific payload
      "clientTime": "2026-10-16T09:00:00Z"
    }
  ]
}
```

Response: `{ "batchId": "…", "results": [ … ] }` with one result per operation:

| Field | Meaning |
|-------|---------|
| `opId`, `entity`, `entityId` | echo of the operation |
| `result` | `applied` \| `duplicate` \| `rejected` (reserved: `conflict`) |
| `reason` | for `rejected`: `code: message` (e.g. `missing_title: A task title is required.`) |
| `payload` | current server snapshot of the entity (for `applied`/`duplicate`) |
| `version` | current entity `version` after apply |

**Idempotency:** before applying, the server looks up `op_id` in `sync_operation` (scoped to
`school_id`). If the op was already processed, the stored result is replayed verbatim. Every
processed op is recorded with `ON CONFLICT (op_id) DO NOTHING` and
`expires_at = now() + interval '90 days'`. Retention: **90 days**
(`schoolschools.sync.op-log-retention-days: 90`).

**Supported entity types on push:**

| Entity | Supported operations | Notes |
|--------|---------------------|-------|
| `task` | create, update, delete | Owner-scoped (`owner_user_id` must match actor). Update fields whitelisted: `title`, `description`, `dueAt`, `priority`, `category`, `reminderAt`, `done`. Delete is a soft tombstone. |
| `message` | create only | Append-only, idempotent on client-generated id; edits/deletes require an online connection (`offline_edit_unsupported`). |
| `attendance_event` | create only | Append-only event; optional `correctsEventId` for corrections; requires `sessionId`, `studentId`, `status`. |
| `timetable_version`, `timetable_entry` | — | Always rejected: `timetable_edits_online_only`. |
| anything else | — | Rejected: `unsupported_entity`. |

## 3. Pull — `GET /api/v1/sync/pull`

Auth: JWT + `X-School-Id` (`@policy.can('school.view')`).

Query params: `cursor` (long, default `0`), `limit` (default **200**, capped at **500** —
`SyncService` constants; note the `schoolschools.sync.pull-default-limit` /
`pull-max-limit` keys exist in `application.yml` but are not read by `SyncService` yet),
`deviceId` (optional; when sent, updates `sync_device.last_pull_seq` / `last_seen_at`).

```jsonc
{
  "cursor": 0,             // server returns changes with change_seq > cursor
  "hasMore": false,        // true when more rows remain
  "resyncRequired": false, // true -> client must discard local state and re-pull from 0
  "changes": [
    {
      "seq": 123,
      "entity": "task",
      "entityId": "…",
      "op": "upsert",      // upsert | delete
      "version": 4,
      "changedAt": "…",
      "actorUserId": "…",
      "payload": { }       // to_jsonb snapshot for upsert; null for delete
    }
  ]
}
```

The feed is produced by DB triggers (`V12__sync_change_feed_triggers.sql`) appending to
`change_log` in the same transaction as the business change, so it cannot drift. Snapshot
payloads are fetched for upsert rows via `ChangeFeedReader.snapshotPayloads` (`to_jsonb` of
the source table).

**`resyncRequired` is set when:**
- `cursor > max(change_seq)` for the school (client cursor is in the future), or
- `cursor > 0` and `cursor < min(change_seq) - 1` (the feed rows the client needs have been
  trimmed away — e.g. after a feed reset).

**Visibility filtering per entity** (rows are read under RLS, then filtered to the actor):

| Entity | Visible to |
|--------|-----------|
| `task` | only tasks owned by the requesting user |
| `message` | messages in channels where the actor's membership is a member |
| `channel` | non-`direct` channels |
| `channel_member` | only the actor's own membership rows |
| `attendance_event` | staff with `attendance.take`/`attendance.report`: all; otherwise the student's own events + guardian-linked students where the `attendance` visibility is not disabled |
| `attendance_session` | staff with `attendance.take`/`attendance.report` only |
| `timetable_version`, `timetable_entry` | all (school-wide) |

## 4. Conflict policy (as implemented)

| Entity | Policy |
|--------|--------|
| `task` | Owner-only writes; `version` increments on update/delete. Update/delete of a task you don't own → `task_not_found` (rejected). Re-create of an existing id owned by you → `duplicate` with the current snapshot; owned by someone else → `id_conflict` (rejected). No field-level merge — last write wins on whitelisted fields. |
| `message` | Append-only. Replay of the same client id returns `duplicate` (created=false), so retries are safe. No edits/deletes offline. |
| `attendance_event` | Append-only. Replay returns `duplicate`. Corrections are new events referencing `correctsEventId`; original rows are never mutated. |
| `timetable_*` | Always rejected online-only (`timetable_edits_online_only`). |
| Feed-level | `baseVersion` is accepted on push but **not enforced** as a hard optimistic-concurrency check today; ordering is driven by `change_seq`. |

Reserved kind `conflict` exists in the DB constraint (`V10`) and the client handles it, but
no server path currently returns it — the current rejection codes cover conflicts.

## 5. Offline scope v1

| Queued offline (writes) | Not queued (online-only) |
|-------------------------|--------------------------|
| Tasks (create / update / delete) | Timetable edits |
| Messages (create) | Message edits / deletes |
| Attendance events (marks, corrections) | Everything else — academics, people, learning, roles, settings, reports, resource uploads |

Reads are **not** cached in a local database yet (no Drift) — offline reads depend on what
the last sync left in memory ([ADR-006](DECISIONS.md)).

## 6. Client sync engine (`app/lib/core/sync/offline_queue.dart`)

- **`OfflineQueue`** persists pending operations as JSON in `SharedPreferences` under
  `sync.queue.<userId>.<schoolId>`, and the pull cursor under `sync.cursor.<schoolId>`
  (`TokenStore`). Queue and cursor are **per user+school**.
- **`SyncService` (client)** orchestrates:
  1. `pushPending()` — POST all queued ops in one batch. Ops that come back `applied` or
     `duplicate` are removed; `rejected`/`conflict` ops are removed and surfaced in
     `SyncStatus.rejected` (they will never succeed unchanged).
  2. `pullChanges()` — GET `/sync/pull` from the stored cursor, store the new cursor, return
     the raw changes for the caller to cache.
  3. `syncNow()` — push first, then pull (only if push had no error). Guarded against
     concurrent runs.
- Status is broadcast on a stream (`SyncStatus`: `pending`, `syncing`, `lastSyncedAt`,
  `lastError`, `rejected`, `cursor`) and rendered by `features/sync/sync_screen.dart`.
- Writes made while offline are enqueued by feature screens (e.g. chat shows an
  “offline queued” snackbar when a send is queued).
- Chat uses **15s REST polling** today rather than the server's STOMP WebSocket; upgrading
  the client is a follow-up ([ADR-005](DECISIONS.md)).

## 7. Devices

- `POST /api/v1/sync/devices` registers a device (upsert on
  `(school_id, user_id, device_id)`), `GET /api/v1/sync/devices` lists the caller's devices,
  `DELETE /api/v1/sync/devices/{id}` revokes one (`@policy.can('sync.device.manage')`).
- `sync_device` tracks `last_pull_seq` and `last_seen_at` for observability and revocation.

## 8. Edge server duties

The edge deployment (`profile=edge`) is the system-of-record for its school *locally* and a
replica of the cloud for shared/platform data. The configuration knobs exist
(`schoolschools.edge.cloud-base-url`, `schoolschools.edge.sync-enabled`) but the **edge↔cloud
replication client is not implemented yet**. The intended duties, consistent with the code
above, are:

| Duty | Status |
|------|--------|
| Serve the school's users locally with the same REST/WS API | ✅ (same artifact, `edge` profile) |
| Enforce RLS + tenant context locally | ✅ (same `V11`/`V12` migrations) |
| Act as a sync **client** of the cloud (push local changes, pull cloud changes) using the same `/sync` protocol | ❌ Not implemented yet |
| Store files locally (`storage.type=local`) | config present; no storage service yet |
| Rotate mTLS credentials to the cloud | outline in [deploy-edge.md](runbooks/deploy-edge.md) |

Because the edge uses the same `change_log`/`sync_operation`/`sync_device` tables, the
replication client can reuse the existing push/pull code paths rather than inventing a
second protocol.
