# Trixx Education

Trixx Education is a **hybrid multi-school management and learning platform**. It runs as a
cloud system-of-record serving many schools and can also run an **edge** node inside a single
school (or a small set) with local storage and a sync link back to the cloud. The platform
covers academics structure, people (students/teachers/guardians), learning resources and
assignments, quizzes, attendance, timetabling, chat, tasks, and notifications.

The implementation is a **Flutter** client plus a **Spring Boot** backend backed by
**PostgreSQL** with row-level security. It was built from the *"SchoolSphere Master
Specification and Engineering Handbook v1.0"* and the *"Trixx Education: Brand, Product, and
UI/UX Specification v1.0"*; where a specification and the code differ, the code wins and the
divergence is recorded in [docs/DECISIONS.md](docs/DECISIONS.md).

> The product was renamed from SchoolSphere to **Trixx Education**. User-visible names, the
> design system, and the client copy use the new brand; internal identifiers (Java packages,
> `SCHOOLSPHERE_*` environment variables, database roles) keep their original names so a
> running deployment is not broken. See ADR-012 in [docs/DECISIONS.md](docs/DECISIONS.md).

> Status: the backend and the Flutter app compile, run, and are covered by automated tests
> (`mvn verify`, `flutter analyze`, `flutter test`). See
> [docs/PROGRESS.md](docs/PROGRESS.md) for an honest, area-by-area status table.

## Repository layout

```
schoolsphere/
├── backend/               Spring Boot 3.5.16 / Java 21 backend (Maven)
│   └── src/main/java/com/schoolsphere/
│       ├── auth/          login, JWT, refresh rotation, security filters
│       ├── tenants/       schools, memberships, roles, PermissionCatalog
│       ├── academics/     years, terms, departments, classes, streams, subjects
│       ├── people/        students, teachers, guardians, enrollment, CSV import
│       ├── learning/      resources, assignments, submissions, quizzes
│       ├── tasks/         personal tasks
│       ├── messaging/     channels + messages, STOMP WebSocket
│       ├── lessons/       Jitsi conferencing rooms
│       ├── attendance/    sessions, events, PDF/CSV reports
│       ├── notifications/ in-app + transactional outbox worker
│       ├── timetable/     versions, entries, heuristic generator + validator
│       ├── sync/          push/pull, change feed, devices
│       ├── audit/         append-only audit log + read API, data-subject export
│       ├── featureflags/ table-backed feature flags (per-school override)
│       ├── subscriptions/ plans, subscription lifecycle, payment provider seam
│       ├── common/        tenancy, crypto, UUIDv7, error handling, web config
│       └── bootstrap/     DemoSeeder
│   └── src/main/resources/db/migration/   Flyway V1–V14
├── app/                   Flutter client (Riverpod + Dio)
│   └── lib/
│       ├── core/          config, networking, session, sync, l10n, theme, widgets
│       └── features/      auth, home, overview, timetable, attendance, assignments,
│                          quizzes, tasks, chat, people, academics, reports, resources,
│                          sync, lessons, subscriptions, settings
├── deploy/
│   └── scripts/provision-db.sql   creates the non-owner app role for RLS
├── docs/                  this documentation set
└── tools/                 (repo-local tooling notes; JDK/Maven/Flutter/PG live in D:\Trixx\tools)
```

## Quickstart (local development)

Toolchain lives under `D:\Trixx\tools` (`jdk21`, `maven`, `flutter`, `pgsql`), and the local
Maven repository is `D:\Trixx\m2repo`. PostgreSQL 18 runs on **port 5433** with data directory
`D:\Trixx\pgdata`.

### 1. Start PostgreSQL 18 (port 5433, data dir D:\Trixx\pgdata)

```powershell
# from a shell where pg_ctl is on PATH, e.g. D:\Trixx\tools\pgsql\bin
D:\Trixx\tools\pgsql\bin\pg_ctl.exe -D D:\Trixx\pgdata -o "-p 5433" start
```

Create the database if it does not exist yet:

```powershell
D:\Trixx\tools\pgsql\bin\createdb.exe -h localhost -p 5433 -U schoolsphere schoolsphere
```

### 2. Provision the application role (RLS requires a non-owner role)

Run once (owner role `schoolsphere`). This creates `schoolsphere_app` and grants it the
default privileges so row-level security applies at runtime:

```powershell
D:\Trixx\tools\pgsql\bin\psql.exe -h localhost -p 5433 -U schoolsphere -d schoolsphere -f deploy\scripts\provision-db.sql
```

### 3. Run the backend (dev profile, :8080)

The `dev` profile is the default (`spring.profiles.default: dev`). Flyway connects as the
owner role `schoolsphere`; the app connects as the non-owner `schoolsphere_app`.

```powershell
cd backend
$env:JAVA_HOME = 'D:\Trixx\tools\jdk21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
D:\Trixx\tools\maven\bin\mvn.cmd `
  -Dmaven.repo.local=D:\Trixx\m2repo `
  spring-boot:run
```

Defaults baked into `application-dev.yml` (override with env vars):
`jdbc:postgresql://localhost:5433/schoolsphere`, app user `schoolsphere_app` /
`schoolsphere_app_dev`, Flyway owner `schoolsphere` / `schoolsphere_dev`.

The API is at `http://localhost:8080/api/v1`. Live OpenAPI docs are at
`http://localhost:8080/api/v1/docs` (JSON at `/api/v1/openapi.json`).

### 4. Run the Flutter client

```powershell
cd app
D:\Trixx\tools\flutter\bin\flutter.bat pub get
D:\Trixx\tools\flutter\bin\flutter.bat run        # picks a connected device/emulator
```

The client defaults to `http://localhost:8080` (web/desktop) or `http://10.0.2.2:8080`
(Android emulator). Override with `--dart-define=API_BASE_URL=...`.

For a browser demo, build the release bundle and serve it (opening `index.html` from disk does
not work — the app needs an HTTP origin):

```powershell
cd app
D:\Trixx\tools\flutter\bin\flutter.bat build web --release
python -m http.server 8090 --directory build\web
```

Then open <http://localhost:8090> and sign in with a demo account (see
[docs/RUNNING.md §6](docs/RUNNING.md), all demo passwords are `Demo1234!`). The full
walkthrough — the adaptive shell, the marks and report-card screens, brand-asset regeneration,
and the checks to run — is in [docs/RUNNING.md](docs/RUNNING.md).

## Demo data and accounts

On the `dev` profile the `DemoSeeder` runs on startup (`schoolsphere.seed.enabled=true`) and
creates the demo school **Kilimani Academy** (slug `kilimani`) with an academic year, terms,
departments, classes, streams, subjects, teachers, students, parents, guardian links, a
published timetable, attendance, chat channels, and learning content.

**Password for every demo account: `Demo1234!`**

| Email | Name | Role |
|-------|------|------|
| principal@kilimani.ac.ke | Grace Wanjiru | principal |
| admin@kilimani.ac.ke | Peter Otieno | admin |
| teacher.david@kilimani.ac.ke | David Mwangi | teacher |
| teacher.sarah@kilimani.ac.ke | Sarah Achieng | teacher |
| teacher.james@kilimani.ac.ke | James Kariuki | teacher |
| student.faith@kilimani.ac.ke | Faith Njeri | student |
| student.brian@kilimani.ac.ke | Brian Omondi | student |
| student.mercy@kilimani.ac.ke | Mercy Chebet | student |
| student.kevin@kilimani.ac.ke | Kevin Mutua | student |
| student.diana@kilimani.ac.ke | Diana Atieno | student |
| student.samuel@kilimani.ac.ke | Samuel Kiprop | student |
| parent.alice@kilimani.ac.ke | Alice Njeri | parent |
| parent.joseph@kilimani.ac.ke | Joseph Omondi | parent |

The seeder is idempotent — if the demo school already has a published timetable it logs the
credentials and stops.

## Profiles (one build, `spring.profiles.active`)

| Profile | Purpose | Datasource | Storage | Billing | Seeding |
|---------|---------|-----------|---------|---------|---------|
| `dev` (default) | local development | `localhost:5433` | local (`./data/storage`) | off | on |
| `cloud` | multi-tenant system-of-record | `${SCHOOLSPHERE_DB_URL}` | S3 | on | off (opt-in) |
| `edge` | single-school local server, cloud replica | `localhost:5432` | local | off | off |

All three profiles are the **same artifact**; select with `-Dspring-boot.run.profiles=...` or
`SPRING_PROFILES_ACTIVE=...`. See [docs/OPERATIONS.md](docs/OPERATIONS.md) for the environment
variables each profile expects and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the
deployment model.

## Documentation

| Document | Contents |
|----------|----------|
| [docs/PROGRESS.md](docs/PROGRESS.md) | live status by area (backend, app, docs, deploy, CI, tests) |
| [docs/DECISIONS.md](docs/DECISIONS.md) | ADRs — technology and architecture decisions |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | logical view, deployment profiles, module map, tenancy, tech stack |
| [docs/SYNC.md](docs/SYNC.md) | offline sync protocol, conflict policy, offline scope, client engine |
| [docs/SECURITY.md](docs/SECURITY.md) | threat model, controls, children's data / Kenya DPA, known gaps |
| [docs/API.md](docs/API.md) | API conventions + endpoint inventory by module |
| [docs/PERMISSIONS.md](docs/PERMISSIONS.md) | permission catalog and default role matrix |
| [docs/OPERATIONS.md](docs/OPERATIONS.md) | run profiles, env vars, actuator, backups, observability |
| [docs/RUNNING.md](docs/RUNNING.md) | how to run the full system locally (PostgreSQL, backend, Flutter client) |
| [docs/runbooks/](docs/runbooks/) | backup-restore, deploy-edge, breach-response, restore-drill |
