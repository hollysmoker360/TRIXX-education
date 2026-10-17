# Runbook — Deploy an edge node

Provision and run a SchoolSphere **edge** node: one school (or a small set) served locally,
using the `edge` profile of the same backend artifact. Companion:
[OPERATIONS.md](../../OPERATIONS.md), [ARCHITECTURE.md](../../ARCHITECTURE.md),
[SYNC.md](../../SYNC.md#edge-server-duties).

## 1. What the edge is

- Same Spring Boot artifact, profile `edge`.
- Local PostgreSQL (default `localhost:5432/schoolsphere`), RLS enforced, Flyway migrations
  applied by the owner role.
- Local storage (`schoolschools.storage.type=local`), billing off, seeding off.
- Replicates to/from the cloud over the same REST API secured by **mTLS + JWT**.
- Structured (ECS) logging.

Config keys introduced by the edge profile (`application-edge.yml`):

| Key | Env var | Default | Purpose |
|-----|---------|---------|---------|
| `schoolschools.edge.cloud-base-url` | `SCHOOLSPHERE_CLOUD_URL` | empty | Cloud API base URL |
| `schoolschools.edge.sync-enabled` | `SCHOOLSPHERE_EDGE_SYNC` | `false` | Enable edge↔cloud sync |
| `schoolschools.datasource…` | `SCHOOLSPHERE_DB_URL` / `_USER` / `_PASSWORD` | `localhost:5432` | Local DB |
| Flyway | `SCHOOLSPHERE_DB_OWNER` / `_OWNER_PASSWORD` | `schoolsphere` | Migration role |

> **Honest caveat:** the edge↔cloud replication *client* is **not implemented yet**
> ([SYNC.md](../../SYNC.md#edge-server-duties)); `SCHOOLSPHERE_EDGE_SYNC` is a placeholder.
> Until it lands, run the edge as a standalone local instance and reconcile manually or via
> scheduled `pg_dump`/restore.

## 2. Prerequisites

- Java 21 (`D:\Trixx\tools\jdk21`) and the backend jar / Maven (`D:\Trixx\tools\maven`, repo
  `D:\Trixx\m2repo`).
- PostgreSQL 18 (`D:\Trixx\tools\pgsql`) with a data dir (dev uses `D:\Trixx\pgdata`, port
  5433; the edge default is 5432 — pick and keep consistent).
- A provisioned database `schoolsphere` and the non-owner app role.

## 3. Provision the database

Create the database, then create the non-owner application role so RLS applies
(the app connects as `schoolsphere_app`; Flyway connects as the owner):

```powershell
# 1. Create DB (as owner) if missing
D:\Trixx\tools\pgsql\bin\createdb.exe -h localhost -p 5432 -U schoolsphere schoolsphere

# 2. Create the non-owner app role + default grants (run as owner)
D:\Trixx\tools\pgsql\bin\psql.exe -h localhost -p 5432 -U schoolsphere -d schoolsphere `
  -f deploy\scripts\provision-db.sql
```

`provision-db.sql` creates `schoolsphere_app` (if absent), grants `CONNECT`/`USAGE`, and sets
`ALTER DEFAULT PRIVILEGES` so Flyway-created tables are readable/writable by the app role but
**not owned** by it. `V11` then applies `FORCE ROW LEVEL SECURITY` on all tenant tables.

## 4. Build & run the backend

```powershell
cd backend
$env:JAVA_HOME = 'D:\Trixx\tools\jdk21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

# Build the jar
D:\Trixx\tools\maven\bin\mvn.cmd -Dmaven.repo.local=D:\Trixx\m2repo -DskipTests package

# Run with the edge profile
$env:SPRING_PROFILES_ACTIVE = 'edge'
$env:SCHOOLSPHERE_DB_URL    = 'jdbc:postgresql://localhost:5432/schoolsphere'
$env:SCHOOLSPHERE_DB_USER   = 'schoolsphere_app'
$env:SCHOOLSPHERE_DB_PASSWORD = '<app-role-password>'
$env:SCHOOLSPHERE_DB_OWNER  = 'schoolsphere'
$env:SCHOOLSPHERE_DB_OWNER_PASSWORD = '<owner-password>'
$env:SCHOOLSPHERE_JWT_SECRET = '<strong-secret>'
$env:SCHOOLSPHERE_STORAGE_TYPE = 'local'
$env:SCHOOLSPHERE_STORAGE_ROOT = 'D:\schoolsphere\data\storage'
$env:SCHOOLSPHERE_EDGE_SYNC = 'false'

java -jar backend\target\schoolsphere-backend-0.1.0.jar
```

Flyway runs on startup as the owner and applies `V1`–`V12`. The app listens on **:8080**.

## 5. Health checks (post-deploy)

```powershell
# Liveness (should be 200 UP)
curl.exe http://localhost:8080/actuator/health/liveness

# Readiness includes the DB check — a bad datasource fails here first
curl.exe http://localhost:8080/actuator/health/readiness

# Aggregate + info
curl.exe http://localhost:8080/actuator/health
curl.exe http://localhost:8080/actuator/info
```

Readiness returning `UP` with the `db` component `UP` confirms the app role can read/write and
RLS context is functional. Then verify the API surface:

- `GET /api/v1/docs` loads (springdoc).
- Login as a school member (`POST /api/v1/auth/login`) and call an authenticated endpoint with
  `X-School-Id` set to confirm tenancy.

## 6. mTLS credential rotation (edge ↔ cloud)

The intended transport between edge and cloud is **mutual TLS** (edge presents a client cert;
cloud validates it) on top of the normal JWT auth. No automation exists yet, so follow this
manual outline:

1. **Issue** a new client certificate/chain for the edge node from the platform CA (short
   TTL, e.g. 90 days). Record the serial and expiry.
2. **Distribute** the new cert + key to the edge host over a secure channel; store under a
   restricted directory (e.g. `/etc/schoolsphere/mtls/`), `0600`, owned by the service user.
   Keep the old cert valid during the overlap window.
3. **Load** the new cert on the edge (reload/restart the TLS terminator or app). Confirm the
   edge can reach the cloud (`SCHOOLSPHERE_CLOUD_URL`) with the new cert.
4. **Revoke** the old cert at the CA once the overlap window closes; remove it from the edge.
5. **Verify** cloud-side: the edge authenticates with the new cert; logs show no TLS
   handshake failures; sync/health calls succeed.
6. **Record** the rotation (serial, dates, operator) in the change log.

Rotate the **JWT signing key** independently using the rotatable-key mechanism: add the new
key to `SCHOOLSPHERE_JWT_SECRET`/`signing-keys` (first key signs, all keys verify), deploy,
then retire the old key after tokens signed with it expire (access TTL 15 min; refresh up to
30 days).

## 7. Rollback / decommission

- Roll back by redeploying the previous jar and re-running Flyway only if the previous schema
  version is compatible (Flyway is forward-only; prefer a fix-forward migration).
- To decommission: stop the app, archive the local DB (`pg_dump`) and local storage, revoke
  the mTLS cert and any device tokens, then decommission the host.
