# Operations

Run profiles, configuration, health, backups, observability, supply chain, and feature flags
for the Trixx Education backend. See [ARCHITECTURE.md](ARCHITECTURE.md) for the deployment model
and [runbooks/](runbooks/) for step-by-step procedures.

## 1. Run profiles

One artifact; select with `SPRING_PROFILES_ACTIVE` (or `-Dspring-boot.run.profiles=…`). The
default is `dev` (`spring.profiles.default: dev`).

| Profile | Datasource | Flyway user | Storage | Billing | Seeding | Logging |
|---------|-----------|-------------|---------|---------|---------|---------|
| `dev` | `jdbc:postgresql://localhost:5433/schoolsphere` | owner `schoolsphere` | local `./data/storage` | off | on | plain, DEBUG |
| `cloud` | `${SCHOOLSPHERE_DB_URL}` | `${SCHOOLSPHERE_DB_OWNER}` | s3 (default) | on | off (opt-in) | ECS |
| `edge` | `jdbc:postgresql://localhost:5432/schoolsphere` | owner `schoolsphere` | local | off | off | ECS |

Port is `8080` (`server.port`). JPA is `ddl-auto: validate`, `open-in-view: false`.

Local toolchain: JDK `D:\Trixx\tools\jdk21`, Maven `D:\Trixx\tools\maven` (repo
`D:\Trixx\m2repo`), Flutter `D:\Trixx\tools\flutter`, PostgreSQL 18 `D:\Trixx\tools\pgsql`
(data dir `D:\Trixx\pgdata`, dev port **5433**).

## 2. Configuration environment variables

### Datasource & migrations
| Env var | Purpose | Dev default |
|---------|---------|-------------|
| `SCHOOLSPHERE_DB_URL` | App JDBC URL (non-owner `schoolsphere_app`) | `jdbc:postgresql://localhost:5433/schoolsphere` |
| `SCHOOLSPHERE_DB_USER` | App DB user | `schoolsphere_app` |
| `SCHOOLSPHERE_DB_PASSWORD` | App DB password | `schoolsphere_app_dev` |
| `SCHOOLSPHERE_DB_OWNER` | Flyway migration role (owner) | `schoolsphere` |
| `SCHOOLSPHERE_DB_OWNER_PASSWORD` | Owner password | `schoolsphere_dev` |
| `SCHOOLSPHERE_DB_POOL` | Hikari max pool (cloud) | dev 10, cloud 20 |

### Security
| Env var | Purpose | Dev default |
|---------|---------|-------------|
| `SCHOOLSPHERE_JWT_SECRET` | JWT signing key (first key signs, all keys verify → rotation-ready) | baked dev key |
| `SCHOOLSPHERE_CORS_ORIGINS` | Allowed CORS origin patterns | `http://localhost:*,http://127.0.0.1:*` |
| field key (`schoolschools.security.field-key`) | AES-256-GCM field-encryption key | dev default |

### Storage & sync
| Env var | Purpose | Dev default |
|---------|---------|-------------|
| `SCHOOLSPHERE_STORAGE_TYPE` | `local` or `s3` | `local` (`cloud` defaults to `s3`) |
| `SCHOOLSPHERE_STORAGE_ROOT` | Local storage root | `./data/storage` |

### Profile-specific
| Env var | Purpose | Default |
|---------|---------|---------|
| `SCHOOLSPHERE_SEED_ENABLED` | Enable demo seeding (cloud) | `false` |
| `SCHOOLSPHERE_CLOUD_URL` | Cloud base URL (edge) | empty |
| `SCHOOLSPHERE_EDGE_SYNC` | Enable edge↔cloud sync (edge) | `false` |

### Notification providers
External delivery sits behind `EmailProvider` / `SmsProvider` beans; **dev uses logging
no-ops**. Wire a real provider (email / Africa's Talking SMS) by providing a bean that
replaces the `@ConditionalOnMissingBean` dev provider.

## 3. Health & liveness

Actuator is exposed with `health,info,metrics,prometheus`.

| Endpoint | Auth | Notes |
|----------|------|-------|
| `/actuator/health` | public | aggregate |
| `/actuator/health/liveness` | public | `livenessState` group |
| `/actuator/health/readiness` | public | `readinessState` + `db` group |
| `/actuator/info` | public | app info |
| `/actuator/metrics` | authenticated | Micrometer metrics |
| `/actuator/prometheus` | authenticated | Prometheus scrape |

Probes are enabled (`management.endpoint.health.probes.enabled: true`). Readiness includes a
DB check, so a broken datasource fails readiness but not liveness.

## 4. Backups

Recommended: **pgBackRest** with WAL archiving for continuous PITR on the cloud database;
`pg_dump`/`pg_restore` for the edge and for logical copies. Keep the backup host and the
database in different failure domains. Full commands, including the local toolchain paths and
port 5433, are in [runbooks/backup-restore.md](runbooks/backup-restore.md).

- **Cloud:** continuous WAL archiving (pgBackRest) + periodic full base backups; encrypted at
  rest; retention per policy (suggest 7–30 days daily + monthly archives).
- **Edge:** scheduled `pg_dump` of the school database to removable/encrypted storage; the
  edge DB is small.
- **Restore drill:** run quarterly — [runbooks/restore-drill.md](runbooks/restore-drill.md)
  with pass/fail criteria.
- **What to back up besides Postgres:** the object-storage bucket (learning-resource
  attachments) and the field-encryption key / JWT keys (secret manager). Losing the field key
  makes encrypted columns unrecoverable.

## 5. Observability

Present today:
- **Actuator + Micrometer:** `metrics` and `prometheus` endpoints.
- **Structured logs:** ECS format on `cloud`/`edge`
  (`logging.structured.format.console: ecs`); plain DEBUG on `dev`. Logger
  `com.schoolsphere`.

Roadmap (not yet wired):
- **OpenTelemetry** tracing/metrics export.
- **Prometheus + Grafana** dashboards, ideally as a Compose profile alongside the app.
- **Alerting** on readiness failures, outbox backlog (`notification_outbox` pending/failed),
  and sync anomalies (`sync_operation` rejection rate).

## 6. Supply chain

Roadmap / CI follow-ups (none on disk yet — see [PROGRESS.md](PROGRESS.md)):
- **SBOM** generation (CycloneDX) for the backend artifact and the Flutter bundle.
- **OWASP Dependency-Check** (or equivalent) in CI, failing on known critical CVEs.
- Pin and audit the Flutter dependency graph (`pubspec.lock` committed; currently not).
- Base-image scanning for any container image once Dockerfiles/compose land.

## 7. Feature flags

Two mechanisms exist:

1. **Config-based toggles** (immediate, per-deployment) — read via `@Value`:
   | Key | Effect | Default |
   |-----|--------|---------|
   | `schoolschools.seed.enabled` | Run `DemoSeeder` on startup | `true` (dev), `false` elsewhere |
   | `schoolschools.billing.enabled` | Gate billing surface | `false` (only `true` on cloud) |
   | `schoolschools.edge.sync-enabled` | Enable edge↔cloud sync | `false` |
   | `schoolschools.attendance.absence-alerts` | Absence alert notifications | `true` |
   | `schoolschools.attendance.absence-alert-channels` | Alert channels | `in_app,sms` |
   | `schoolschools.chat.edit-window-minutes` | Message edit window | `15` |
   | `schoolschools.notifications.outbox-delay-ms` | Outbox poll interval | `3000` |
   | `schoolschools.conferencing.base-url` / `room-prefix` | Jitsi | `https://meet.jit.si` / `schoolsphere` |
   | `schoolschools.sync.pull-default-limit` / `pull-max-limit` | Sync pull limits (config present; **not read by `SyncService` yet**) | `500` / `1000` |
   | `schoolschools.sync.op-log-retention-days` | `sync_operation` retention | `90` |
   | `schoolschools.timezone` | School default timezone | `Africa/Nairobi` |

2. **Table-backed flags** — a `feature_flag` table exists (`V2`: `flag_key`, optional
   `school_id`, `enabled`, `payload`) for per-plan/per-school flags. **No feature-flag service
   or endpoint consumes it yet**; wiring it is a roadmap item.

## 8. Runbooks

| Runbook | When |
|---------|------|
| [backup-restore.md](runbooks/backup-restore.md) | Backing up / restoring Postgres |
| [deploy-edge.md](runbooks/deploy-edge.md) | Provisioning and running an edge node |
| [breach-response.md](runbooks/breach-response.md) | Security/privacy incident (incl. Kenya DPA 2019 timelines) |
| [restore-drill.md](runbooks/restore-drill.md) | Quarterly restore verification |
