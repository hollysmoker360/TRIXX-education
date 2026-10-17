# Running Trixx Education (end-to-end)

How to run the complete system locally: PostgreSQL, the Spring Boot backend, and the
Flutter client. This is the operational companion to [README.md](../README.md) (which has a
short Quickstart), [OPERATIONS.md](OPERATIONS.md) (profiles, configuration, health, backups),
and [runbooks/](runbooks/) (backup/restore, edge deploy).

The running system is three cooperating pieces:

```
PostgreSQL 18                          Spring Boot backend (:8080)                Flutter client
┌────────────────────┐              ┌──────────────────────────────────┐      ┌────────────────────┐
│ schoolsphere db    │  Flyway      │ REST + WebSocket (/api/v1, /ws) │◄────►│ phones / browser   │
│  (owner role)      │◄────────────►│ JPA with row-level security    │      │                  │
│  app role (RLS)    │              │ STOMP over WebSocket          │      └────────────────────┘
└────────────────────┘              └──────────────────────────────────┘
```

- The **backend** serves the REST API (`/api/v1`) plus a STOMP WebSocket for chat and
  notifications (`/ws`), and owns the system of record.
- The **Flutter client** talks to the backend over HTTP(S), shows the sync indicator when
  data is being pushed/pulled, and receives real-time updates over the WebSocket.
- **PostgreSQL** holds everything; row-level security keeps one school's data invisible to
  another school's app process.

## 1. Prerequisites

| Tool | Where | Notes |
|-----|-------|-------|
| JDK 21 | `D:\Trixx\tools\jdk21` | Required by the backend |
| Maven | `D:\Trixx\tools\maven` (repo `D:\Trixx\m2repo`) | One artifact is used for both run modes |
| Flutter | `D:\Trixx/tools/flutter` | Client; needs `flutter pub get` once |
| PostgreSQL 18 | `D:\Trixx\pgsql` | Dev uses data dir `D:\Trixx\pgdata`, port **5433** |
| Docker (optional) | any local Docker | Only needed for the Compose stacks (§7) |

Defaults in `application-dev.yml` (the default profile) point at `localhost:5433` and the
local storage directory `./data/storage`. All of these can be overridden with environment
variables — see [OPERATIONS.md](OPERATIONS.md#2-configuration-environment-variables).

## 2. Start PostgreSQL 18 (dev database)

If PostgreSQL is not running:

```powershell
D:\Trixx\tools\pgsql\bin\pg_ctl.exe -D D:\Trixx\pgdata -o "-p 5433" start
```

Create the `schoolsphere` database once if it does not exist yet:

```powershell
D:\Trixx\tools\pgsql\bin\createdb.exe -h localhost -p 5433 -U schoolsphere schoolsphere
```

Verify the server answers:

```powershell
D:\Trixx\tools\pgsql\bin\psql.exe -h localhost -p 5433 -U schoolsphere -d schoolsphere -c "select version();"
```

## 3. Provision the application role (required for row-level security)

Row-level security only works if the app connects with a non-owner, non-superuser role.
Run once (as owner `schoolsphere`):

```powershell
D:\Trixx\tools\pgsql\bin\psql.exe -h localhost -p 5433 -U schoolsphere -d schoolsphere -f deploy\scripts\provision-db.sql
```

This creates `schoolsphere_app` and grants it the default privileges. If you skip this step,
login fails or reads come back empty.

## 4. Run the backend

The `dev` profile is the default (`spring.profiles.default: dev`). Flyway validates and
applies all migrations automatically on startup (current schema: v16). The app connects as
the non-owner `schoolsphere_app`; Flyway itself connects as the owner.

The CP-SAT timetable engine (OR-Tools) extracts its native library into `java.io.tmpdir`
the first time a timetable is generated. On a machine whose system temp directory is full
or unwritable, set `JAVA_TOOL_OPTIONS` or pass `-Djava.io.tmpdir=D:\\Trixx\\tmp` (or any
writable directory on a drive with free space) before starting the backend.

Run it from the `backend/` directory:

```powershell
D:\Trixx\tools\maven\bin\mvn.cmd spring-boot:run
```

(On Windows the local Maven is `D:\Trixx\tools\maven\bin\mvn.cmd`; on macOS/Linux use `mvn`.)

The server listens on `http://localhost:8080`.

To verify it is up before starting the client:

```powershell
curl http://localhost:8080/actuator/health
```

Expected: `"status":"UP"`. The readiness probe additionally checks the database:

```powershell
curl http://localhost:8080/actuator/health/readiness
```

Interactive API documentation is served by springdoc:

- Swagger UI: <http://localhost:8080/api/v1/docs>
- OpenAPI JSON: <http://localhost:8080/api/v1/openapi.json>

To stop it, press `Ctrl+C` in the same terminal. Alternatively build and run the jar:

```powershell
cd backend
D:\Trixx\tools\maven\bin\mvn.cmd -Dmaven.repo.local=D:\Trixx\m2repo package
java -jar target\schoolsphere-backend-0.1.0.jar
```

Both modes use the same dev defaults.

## 5. Run the Flutter web client (online app)

The Flutter client already builds as a web application (PWA) with an offline-capable
service worker. To produce a production web bundle:

```powershell
cd app
flutter build web --release --no-wasm-dry-run --dart-define=API_BASE_URL=https://your-backend.example.com
```

The output lands in `app/build/web/` and can be served by any static host (Nginx,
Cloudflare Pages, GitHub Pages, Vercel, etc.). The `API_BASE_URL` dart-define points the
client at the deployed Java backend; omit it to keep the localhost dev default.

The bundle includes `manifest.json` and `flutter_service_worker.js`, so it installs as a
PWA on supported browsers and works offline for cached, read-only screens.

## 5. Run the Flutter client

From `app/`:

```powershell
D:\Trixx\tools\flutter\bin\flutter pub get
D:\Trixx\tools\flutter\bin\flutter run
```

`flutter run` opens a device picker and starts on the first connected device or emulator.

### 5.1 Debug in a browser (fastest loop, hot reload)

```powershell
D:\Trixx\tools\flutter\bin\flutter run -d chrome
```

The API base URL defaults per platform — `http://localhost:8080` for web/desktop, and
`http://10.0.2.2:8080` for the Android emulator. To point the client at another backend, pass:

```powershell
D:\Trixx\tools\flutter\bin\flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:9090
```

### 5.2 Release build served by a static host (what to use for a demo)

The release bundle is a static site, so serve `build/web` over HTTP — opening
`index.html` from the filesystem does **not** work (the app needs an origin for its fonts,
SVG assets, and the service worker).

```powershell
cd app
D:\Trixx\tools\flutter\bin\flutter build web --release
python -m http.server 8090 --directory build\web
```

Then open <http://localhost:8090>.

Notes that save time:

- `flutter build web --release` **exits with status 1** on this project because
  `flutter_secure_storage_web` still uses `dart:html`/`dart:js_util`, which the wasm dry-run
  reports as incompatibilities. The build itself succeeds — `✓ Built build\web` is printed
  first. Add `--no-wasm-dry-run` to silence the warnings, or treat exit 1 as expected.
- Port **8090** is used deliberately. A previously-installed service worker can keep serving a
  cached bundle on a port you have used before; if you see a stale app, load in a private
  window or rebuild with `--pwa-strategy=none`.
- To point the release bundle at a non-default backend, use
  `flutter build web --release --dart-define=API_BASE_URL=…`.

### 5.3 What you should see

The first screen is the sign-in card: the Trixx mark and wordmark, the heading **Trixx
Education**, the tagline, email and password fields, a teal **Sign in** button, a *Demo
accounts* expander, and an English/Kiswahili switch. Sign in with a demo account (§6) to reach
the shell.

Once signed in, the shell adapts to the window width: a bottom navigation bar and drawer on
narrow windows, a labelled icon rail from 600 px, a labelled extended sidebar from 1200 px.
The top bar carries search (Ctrl/⌘-K opens the command palette), notifications, the sync
indicator, and the profile menu (theme and language). Destinations are filtered by the signed-in
user's permissions, so a parent, a teacher, and a principal see different navigation.

### 5.4 Verifying the bundle without signing in

Flutter web paints into a canvas, so the page DOM has no inspectable text — a screenshot is the
quickest end-to-end check that the logo asset, the bundled Inter font, and the theme all load:

```powershell
& 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe' --headless --disable-gpu `
  --no-first-run --user-data-dir="$env:TEMP\edge-verify" --window-size=1440,1000 `
  --virtual-time-budget=25000 --screenshot="$env:TEMP\trixx-login.png" http://localhost:8090/
```

Any Chromium build works (`msedge`, `chrome`). The screenshot should show the sign-in card
described above.

### 5.5 Verifying realtime chat (STOMP over WebSocket)

Chat no longer polls while the socket is up ([ADR-015](DECISIONS.md)). The transport is the
server's existing endpoint, so the only thing worth checking after a change is that the upgrade
succeeds and messages flow:

```powershell
# 1. The STOMP endpoint answers a WebSocket upgrade (401/403 without a token is expected,
#    101 Switching Protocols with one).
curl.exe -i -N -H "Connection: Upgrade" -H "Upgrade: websocket" `
  -H "Sec-WebSocket-Version: 13" -H "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==" `
  http://localhost:8080/ws

# 2. In the browser, sign in and open Messages -> a channel. The app bar should read
#    "Live" once the handshake completes; sending from a second browser window appears
#    without a refresh, and the typing indicator shows while the other side types.
# 3. If the backend is stopped the label falls back to "Updates every 15 seconds" and the
#    screen keeps working; restart the backend and it returns to "Live" on its own.
```

Open DevTools → Network → WS to watch the frames: `CONNECT`, then `SUBSCRIBE` to
`/topic/school.<schoolId>.channel.<channelId>`, and a `MESSAGE` frame per message.

## 6. Demo data and accounts

With the `dev` profile, seeding is enabled (`SCHOOLSPHERE_SEED_ENABLED=true`). On backend
startup the `DemoSeeder` creates the demo school **Kilimani Academy** (slug `kilimani`) with an
academic year, terms, departments, classes, streams, subjects, teachers, students, parents,
a published timetable, attendance, chat channels, learning content, and more.

Seeding is idempotent: if the demo school already exists, it logs the credentials and stops
without touching existing data.

Every demo account uses the password **`Demo1234!`**:

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

## 7. Run with Docker Compose (optional)

Instead of running each piece yourself, the `dev` Compose stack runs PostgreSQL 18 and the
backend together:

```powershell
cd deploy\compose\dev
docker compose up --build
```

This starts PostgreSQL on `5433` and the backend on `8080` with the dev defaults and demo
seeding. Stop with `docker compose down`. See [OPERATIONS.md](OPERATIONS.md) for the
`cloud` and `edge` Compose stacks (multi-tenant and edge profiles).

## 8. Typical development loop

1. Start/refresh PostgreSQL (§2) and provision the app role (§3) — once per database.
2. Start the backend (§4). It applies new migrations on startup; watch the console for
   Flyway output.
3. Start the Flutter client (§5) against `http://localhost:8080`.
4. Log in as a demo account, exercise the feature screens, and watch the sync indicator.
5. When backend or client code changes, restart only that process (backend) or hot-restart
   the client; the database needs no restart.

## 9. Brand assets, launcher icons, and translations

Three generated artefacts come from brand/string sources. Re-run the matching command after
changing a source; a test fails if you forget the ARB step.

| Artefact | Source of truth | Regenerate with |
|----------|-----------------|-----------------|
| Logo/app-icon PNGs (web icons, favicon, `assets/brand/*-1024.png`) | `app/assets/brand/*.svg` + `tools/brand/render-icons.ps1` | `powershell -ExecutionPolicy Bypass -File tools\brand\render-icons.ps1` (from the repo root; offline, GDI+, no extra toolchain) |
| Android + web launcher icons | `assets/brand/trixx-app-icon-1024.png`, `trixx-mark-1024.png`, `trixx-mark-mono-1024.png` + the `flutter_launcher_icons` block in `app/pubspec.yaml` | `cd app` then `D:\Trixx\tools\flutter\bin\dart run flutter_launcher_icons` |
| Translator ARB files | `app/lib/core/l10n/strings.dart` | `cd app` then `D:\Trixx\tools\flutter\bin\dart run tool\generate_arb.dart` |

To add a string: add the key to **both** `_en` and `_sw` in
`app/lib/core/l10n/strings.dart`, add a getter next to the other getters, then re-run the ARB
generator. `app/test/l10n_test.dart` checks EN/SW parity, getter↔key coverage, `%placeholder%`
declarations, and ARB drift, so a mistake fails `flutter test` rather than shipping.

## 10. Checks before you call it working

Run these from `backend/` and `app/` respectively:

```powershell
# Backend: unit tests, ArchUnit boundary rules, and the RLS tenant-isolation integration test
D:\Trixx\tools\maven\bin\mvn.cmd -Dmaven.repo.local=D:\Trixx\m2repo verify

# Client: static analysis, then the presentation test suite
D:\Trixx\tools\flutter\bin\flutter analyze
D:\Trixx\tools\flutter\bin\flutter test

# Client: a release build that actually compiles for the browser
D:\Trixx\tools\flutter\bin\flutter build web --release
```

`flutter analyze` should report **no issues** and `flutter test` should end with
**All tests passed!** (61 tests as of this revision.)

> **Disk space:** the Flutter tool writes temporary files under `%TEMP%`. On a machine where
> `C:` is full the test runner fails with *"The target device is full"*; point the tool at a
> drive with room first:
>
> ```powershell
> $env:TEMP = 'D:\Trixx\tmp'; $env:TMP = 'D:\Trixx\tmp'
> ```

