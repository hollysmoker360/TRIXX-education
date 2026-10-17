# Architecture Decision Records

Numbered ADRs for Trixx Education. Status values: **Accepted** (current decision),
**Superseded**, or **Proposed**. Every ADR states what is true *in this repository*; where a
decision is accepted but not yet coded, the consequence says so explicitly. Regenerate the
handbook mapping (`PermissionCatalog` → `docs/PERMISSIONS.md`) when the catalog changes.

Related: [ARCHITECTURE.md](ARCHITECTURE.md) · [SECURITY.md](SECURITY.md) · [SYNC.md](SYNC.md)

---

## ADR-001 — Platform: Java 21 + Spring Boot 3.5.16 backend, Flutter client

- **Status:** Accepted
- **Context:** The handbook requires a mobile-first client usable on low-end Android devices
  and a backend that can run both as a cloud multi-tenant service and as an on-premise edge
  node. The team needed one backend codebase for both profiles and one client for
  Android/iOS/web.
- **Decision:**
  - Backend: **Java 21** with **Spring Boot 3.5.16** (Maven). One Spring Boot artifact
    produces all deployment profiles (`dev`, `cloud`, `edge`).
  - Client: **Flutter** (see [ADR-010](#adr-010--version-pins-actuals) for pins) with
    Riverpod for state and Dio for networking.
- **Consequences:**
  - One backend codebase and one client codebase serve both cloud and edge deployments.
  - The edge node inherits the same JPA/RLS/sync stack; edge-specific behaviour is limited to
    configuration (`application-edge.yml`) and a small `schoolschools.edge.*` block.
  - Java 21 language features (records, pattern matching, virtual threads where used) are
    available; Spring Boot 3.5.16 brings the Jakarta EE namespace and the current Spring
    Security filter model.

---

## ADR-002 — Module consolidation: departments inside `academics`, assignments/quizzes inside `learning`

- **Status:** Accepted
- **Context:** The handbook (§ module map) describes a flat set of ~16 modules including
  separate "departments", "assignments", and "quizzes" modules. During implementation these
  were found to share transactions, authorization, and lifecycle with their parent domains.
- **Decision:**
  - `department` lives inside the **`academics`** module
    (`academics/internal/Department.java`, exposed via `AcademicController`).
  - `assignment`, `submission`, `quiz`, and `quiz_question` live inside the **`learning`**
    module (`learning/internal/...`, exposed via `AssignmentController` and `QuizController`).
  - Module boundaries are still enforced by **ArchUnit** rules (see the `archunit-junit5`
    test dependency in `backend/pom.xml`) — *the ArchUnit test sources are not yet on disk*
    ([PROGRESS.md](PROGRESS.md)).
- **Consequences:**
  - Fewer modules, fewer cross-module APIs, and transactions stay local to a coherent
    bounded context.
  - The handbook's flat module map is a **deviation**; consumers should treat the Java package
    layout (see [ARCHITECTURE.md](ARCHITECTURE.md)) as the source of truth.
  - Boundary rules are declared but not yet executed in CI until the test sources land.

---

## ADR-003 — Timetable: heuristic seeded generator now; OR-Tools CP-SAT is a follow-up

- **Status:** Accepted
- **Context:** The timetable engine must produce a working schedule for a school without a
  heavyweight solver dependency in the first release. A deterministic, seedable solver is
  easier to test and reason about; a CP-SAT optimiser can improve quality later.
- **Decision:**
  - Use a **heuristic seeded greedy generator with randomized restarts and a repair pass**
    (`timetable/internal/TimetableGenerator.java`). Deterministic for a given seed.
  - Keep an **independent validator** (`timetable/internal/TimetableValidator.java`) that is
    mandated for publishing: hard issues block publish, soft issues are workload guidance.
  - Record full **OR-Tools CP-SAT** integration as a follow-up (the `TimetableGenerator`
    Javadoc documents this explicitly).
- **Consequences:**
  - Generation is fast, dependency-free, and reproducible (seed is persisted with the result).
  - Schedules are "good enough", not provably optimal; optimisation quality is deferred.
  - The validator keeps quality gates independent from the generator, so a future CP-SAT
    solver can be swapped in without weakening publish-time checks.

---

## ADR-004 — Rate limiting: in-process sliding window for MVP; Redis deferred

- **Status:** Accepted (design) — **not yet implemented in code**
- **Context:** The platform needs per-user/per-IP throttling on auth and expensive endpoints.
  A shared rate-limit store (Redis) would add an operational dependency that conflicts with
  the "single static binary for edge" goal.
- **Decision:**
  - For the MVP, apply **in-process sliding-window rate limiting** (no external store).
  - **Defer Redis**; there is no Redis dependency and no Redis configuration in any profile
    (verified against `backend/pom.xml` and `application-{dev,cloud,edge}.yml`).
- **Consequences:**
  - No new infrastructure dependency; edge stays a single process.
  - **Implementation status:** rate limiting is **not present in the codebase yet**. Known gap
    recorded in [SECURITY.md](SECURITY.md) ("roadmap"). Until it lands, exposure is bounded by
    JWT auth, refresh-token rotation, and (in production) an external reverse proxy/WAF.
  - Multiple backend replicas would each enforce their own window; acceptable for single-node
    cloud/edge deployments, revisited when horizontal scaling is planned.

---

## ADR-005 — Chat transport: server implements STOMP now; client uses polling, WebSocket upgrade is a follow-up *(client half superseded by ADR-015)*

- **Status:** **Superseded on the client side by ADR-015** (2026-10-17). The server-side
  decision below still stands unchanged.
- **Context:** The messaging module requires realtime chat. The server-side STOMP-over-
  WebSocket stack is fully wired, but the Flutter client shipped its first cut using REST +
  polling to avoid pulling in a WebSocket client dependency before the sync engine stabilized.
- **Decision:**
  - Server: **STOMP-over-WebSocket is implemented** (`messaging/web/ChatWebSocketConfig.java`,
    endpoint `/ws`, broker topics `/topic`, `/queue`, app prefix `/app`, heartbeat 10s).
  - Client: chat used **REST polling on a 15-second interval** (`chat_screen.dart`,
    `Timer.periodic(const Duration(seconds: 15), ...)`). **No longer the primary path — see
    ADR-015**; polling is retained only as the offline fallback.
  - Client upgrade to the STOMP WebSocket was an explicit **follow-up**: **done**.
- **Consequences:**
  - Chat works end-to-end today (REST history + polling), including offline queueing of
    sends via the sync engine.
  - Realtime latency was up to ~15s until the client connected to `/ws`; as of ADR-015 it is
    frame-latency while the socket is up.
  - Upgrading the client required **no server change**, which is what this ADR predicted.

---

## ADR-006 — Client data layer: network-first, sync queue today; Drift offline cache is a follow-up

- **Status:** Accepted (GoRouter navigation **implemented** 2026-10-16; Drift offline cache remains a follow-up)
- **Context:** The Flutter client was built network-first with a durable mutation queue for
  offline writes. A local relational cache (Drift) and a declarative router (GoRouter) were
  considered but deferred to keep the first release lean and avoid schema-migration burden.
- **Decision:**
  - **GoRouter** once backed client navigation
    ([app/router.dart](../app/lib/app/router.dart) holds the route table,
    `core/router/app_routes.dart` holds the paths): an auth-gated `/login` + `/` shell root via
    a session-watching provider, and path-based sub-routes for chat rooms (`/chat/:channelId`),
    quiz attempts (`/quizzes/:quizId/attempt`), attendance registration
    (`/attendance/register/:sessionId`), marks entry (`/marks/:assessmentId`), report cards
    (`/students/:studentId/report-card`) and the component gallery (`/gallery`). Auth state
    changes rebuild the router through the session provider; unauthenticated users are
    redirected to `/login`. `core/router/app_router.dart` was deleted — it held the same table
    and importing it from the shell created a cycle (see ADR-014).
  - **No Drift** yet (verified: `app/pubspec.yaml` has no `drift` dependency). Reads are
    network-first; writes go through the offline mutation queue when offline
    (`core/sync/offline_queue.dart` persisted in `SharedPreferences`).
  - Adding **Drift** for an offline read cache remains a follow-up.
- **Consequences:**
  - Offline support covers **writes** (queued ops) and a **change-feed cursor**, but there is
    no full offline read store yet — offline reads depend on what the last sync left in
    memory.
  - Navigation is declarative for auth + sub-screens; the home shell keeps its internal tab
    state (the `/` page) until the ShellRoute-based tab rewrite lands.
  - The sync protocol is already shaped for a local store (cursor + upsert payloads), so
    adding Drift later is additive. See [SYNC.md](SYNC.md).

---

## ADR-007 — Payments behind a `PaymentProvider` interface; school-fee collection out of scope

- **Status:** Accepted (design) — **no payment code exists yet**
- **Context:** The handbook scopes **school-fee collection out of MVP scope** and requires that
  any future payment integration be isolated behind a provider abstraction (Paystack as the
  first target) with its own tables and credentials, never sharing the school data plane's
  credentials.
- **Decision:**
  - Introduce a **`PaymentProvider`** interface when payments land; dev uses a **mock
    provider**, production targets **Paystack**.
  - Payments use **dedicated tables and dedicated credentials**, separate from core school
    data. School-fee collection stays **out of scope** for this release.
  - A `schoolschools.billing.enabled` flag exists per profile (`true` only in `cloud`) to gate
    billing surface area.
- **Consequences:**
  - **Implementation status:** there is **no `PaymentProvider` interface, no Paystack code,
    and no payment tables** in the repository today (verified by repo-wide search). Only the
    `schoolschools.billing.enabled` config key exists, and it is not consumed by any Java
    class yet.
  - When implemented, this ADR governs the seam: provider interface + mock in dev + Paystack
    in prod, isolated credentials/tables.
  - Nothing in the current API surface implies payments.

---

## ADR-008 — Data platform: UUIDv7 PKs, transactional outbox, per-tenant `change_seq`, soft-delete tombstones, field-level AES-GCM

- **Status:** Accepted
- **Context:** The handbook §3.1 mandates the core data-engineering primitives: time-ordered
  primary keys for index efficiency and sync ordering, a transactional outbox for reliable
  side-effects, a per-tenant change feed for sync, soft deletes so replicas converge, and
  field-level encryption for the most sensitive child data.
- **Decision (all present in code):**
  - **UUIDv7 primary keys** — `common/util/Uuid7.java` (RFC 9562: 48-bit ms timestamp +
    version/variant bits + random). Applied to JPA entities via `BaseEntity.@PrePersist`.
  - **Transactional outbox** — `notification_outbox` table written in the same transaction as
    the business change; drained by `notifications/internal/OutboxWorker.java`
    (`FOR UPDATE SKIP LOCKED`, 5-min lease, exponential backoff, max 8 attempts).
  - **Per-tenant change sequence** — `change_log.change_seq` from a monotonic sequence,
    populated by DB triggers (`V10`, `V12`) so the feed cannot drift from the data.
  - **Soft-delete tombstones** — `deleted_at` on tenant entities (`TenantEntity`); the change
    feed emits `delete` for tombstoned rows so pull replicates deletions.
  - **Field-level AES-256-GCM encryption** — `common/util/FieldCrypto.java` (random 12-byte
    nonce per value, configurable key, JPA `EncryptedStringConverter`) for national IDs,
    medical notes, guardian contacts.
- **Consequences:**
  - IDs are time-ordered, so primary-key indexes and sync ordering stay efficient.
  - Side-effects (notifications, external sends) are decoupled from request transactions and
    are retryable/replayable.
  - Offline replicas converge using the per-tenant feed and tombstones; no full-table scans.
  - Encrypted fields are opaque at rest; key management (secret manager) is an ops concern
    ([OPERATIONS.md](OPERATIONS.md)).

---

## ADR-009 — Identifier scheme: UUIDv7 for JPA entities; UUIDv4 where the client or DB default generates

- **Status:** Accepted (clarifies ADR-008)
- **Context:** ADR-008 states "UUIDv7 primary keys". Verification shows the scheme is not
  100% uniform, so this ADR records exactly what is implemented.
- **Decision / what the code actually does:**
  - **JPA-persisted entities get UUIDv7** — `BaseEntity.onPersist()` calls
    `Uuid7.generate()` when `id == null`. This covers the entity classes under `internal/`
    packages.
  - **SQL-side defaults use `gen_random_uuid()`** (PostgreSQL, produces UUIDv4) in the
    migrations (`V2`–`V10` table `DEFAULT`s). These defaults only apply for rows inserted by
    raw SQL that do not supply an explicit id.
  - **The sync client generates UUIDv4** — `app/lib/core/ids.dart` `newUuid()` (client-side
    `opId`/`entityId`/`batchId`). Sync push inserts use the client-supplied id.
- **Consequences:**
  - In practice, server-created domain entities are UUIDv7 (time-ordered benefits realized);
    client-originated sync ids and any raw-SQL inserts that rely on the DB default are
    UUIDv4.
  - Ids remain globally unique and opaque either way; nothing depends on id ordering for
    correctness (ordering uses `change_seq`, not id).
  - If strict UUIDv7 everywhere is desired later, replace the `gen_random_uuid()` defaults
    with a `uuidv7()` SQL function and switch the client id generator.

---

## ADR-010 — Version pins (actuals)

- **Status:** Accepted
- **Context:** Keep the documented toolchain in sync with what the build actually resolves.
- **Decision / actuals (verified from `backend/pom.xml`, `app/pubspec.yaml`, and the local
  toolchain in `D:\Trixx\tools`):**

  | Component | Version | Source |
  |-----------|---------|--------|
  | Java | **21** | `backend/pom.xml` `<java.version>`; toolchain `D:\Trixx\tools\jdk21` |
  | Spring Boot | **3.5.16** | parent POM |
  | PostgreSQL (local) | **18** | local toolchain `D:\Trixx\tools\pgsql`, dev port **5433** |
  | Flyway | via Spring Boot BOM | `flyway-core` + `flyway-database-postgresql`, no explicit version |
  | springdoc-openapi | **2.9.1** | `backend/pom.xml` |
  | JJWT | **0.12.7** | `backend/pom.xml` |
  | OpenPDF | **3.0.5** | `backend/pom.xml` |
  | ArchUnit | **1.5.1** | `backend/pom.xml` (test) |
  | Riverpod (`flutter_riverpod`) | **^2.6.1** | `app/pubspec.yaml` |
  | Dio | **^5.7.0** | `app/pubspec.yaml` |
  | GoRouter | **^18.1.0** | `app/pubspec.yaml` |
  | flutter_svg | **^2.3.0** | `app/pubspec.yaml` (brand SVGs) |
  | Flutter SDK | **3.47.7** (stable) | toolchain `D:\Trixx\tools\flutter` |
  | Dart SDK | **^3.13.5** | `app/pubspec.yaml` `<environment.sdk>` |
  | Other client deps | flutter_secure_storage ^9.2.4, shared_preferences ^2.3.0, url_launcher ^6.3.1, file_saver ^0.2.14, connectivity_plus ^6.1.0, intl any, cupertino_icons ^1.0.8, flutter_lints ^6.0.0, flutter_launcher_icons ^0.14.4 (dev) | `app/pubspec.yaml` |
  | Fonts | Inter 400/500/600/700, bundled under `app/assets/fonts/` (no runtime download) | `app/pubspec.yaml` |

- **Consequences:**
  - The pins above are the record of truth for what is installed. If `pubspec.yaml`/`pom.xml`
    change, update this table and `docs/OPERATIONS.md`.
  - Note: Dio resolves to the `^5.x` line (`pubspec.yaml` pins `^5.7.0`); exact patch is
    whatever `flutter pub get` resolves into `app/pubspec.lock`.

---

## ADR-011 — Client ID generation uses UUIDv4 (`newUuid`)

- **Status:** Accepted (see ADR-009)
- **Context:** Sync needs client-generated ids for offline-created entities and operation ids.
- **Decision:** The Flutter client generates ids with a hand-rolled UUIDv4
  (`app/lib/core/ids.dart`), no external dependency.
- **Consequences:** Client-originated ids are v4 (unordered); server entity ids are v7
  (ADR-009). Ordering is driven by `change_seq`, not by id, so this is safe.

---

## ADR-012 — Rebrand to Trixx Education: presentation renamed, identifiers kept

- **Status:** Accepted (implemented 2026-10-17)
- **Context:** The product is renamed from **SchoolSphere** to **Trixx Education**, with a new
  logo, a design system, new screens, English + Kiswahili copy, and an app icon. A running dev
  environment (PostgreSQL roles `schoolsphere` / `schoolsphere_app`, database `schoolsphere`,
  Maven `com.schoolsphere.*`, `SCHOOLSPHERE_*` environment variables, Docker/K8s manifests)
  already exists, and the earlier decision was explicit that **package/Java renaming was not
  selected**.
- **Decision:**
  - **Everything a user can read** says Trixx Education: client copy, the app title, the web
    shell (`index.html`, `manifest.json`, favicon, PWA icons), the Android launcher label and
    icons, the OpenAPI title, seed log messages, and the documentation.
  - **The display name is a constant, not runtime-configurable**: `AppBrand.name` in
    `core/brand/brand.dart`, mirrored by the `appName` catalogue entry. It is the same in both
    locales.
  - **Internal identifiers keep their original names**: Java packages, DB names/roles,
    `SCHOOLSPHERE_*` env vars, Maven artifact id, Docker/K8s resource names, and the
    `https://schoolsphere.dev/errors/<code>` problem-type prefix. Renaming them has no user
    benefit and would require a coordinated database + deployment migration.
  - Brand assets live in `app/assets/brand/` (SVG source) with PNG derivatives generated by
    `tools/brand/render-icons.ps1` (offline, GDI+) and launcher icons generated by
    `dart run flutter_launcher_icons`.
  - Domain, social-handle, store-listing, and trademark checks for the new name **have not
    been done**; `com.trixx.education` identifiers in the spec are unconfirmed placeholders.
- **Consequences:**
  - A future rename of the code identifiers is a separate, mechanical task
    (`com.schoolsphere` → `com.trixx`, env vars, DB roles) and is *not* blocked by this change.
  - Docs and code may legitimately contain the string "SchoolSphere" for internal identifiers
    (this ADR explains why); `app/lib` contains none in user-facing copy, enforced by
    `test/l10n_test.dart`.
  - The public problem-type URIs still say `schoolsphere.dev`, so error responses remain
    stable for existing API clients.

---

## ADR-013 — Marks and report cards are built on the assignment grade endpoints

- **Status:** Accepted (implemented 2026-10-17)
- **Context:** The brand/UI specification describes assessments, a gradebook, and printable
  report cards (section 11.3). The backend exposes no gradebook, assessment, or report-card
  module: grading lives in `learning/` as assignment submissions and grade publishing.
- **Decision:**
  - `features/assessment` (marks hub, marks entry, report card) is built on the endpoints that
    exist: `GET /learning/assignments`, `GET /learning/assignments/{id}/submissions`,
    `GET /learning/assignments/{id}/grades`, `PATCH /learning/submissions/{id}/grade`,
    `POST /learning/assignments/{id}/publish-grades`, `GET /learning/assignments/my-grades`,
    `GET /learning/assignments/for-me`.
  - Marks entry **joins** submissions (which carry the `submissionId` that grading is keyed by)
    with grade rows (which carry the student name and current mark), because neither response
    alone is sufficient.
  - The report card shows **published** results for the signed-in learner plus an attendance
    summary, and exports CSV. It degrades gracefully when a staff user has no per-student
    results endpoint.
- **Consequences:**
  - No new tables, endpoints, or migrations were added for marks; there is no term-report-card
    aggregate or weighted-grade computation yet.
  - When a real gradebook module lands, the presentation layer can stay and only its providers
    change.

---

## ADR-014 — Client presentation architecture: design tokens, `ScreenScaffold`, a hand-rolled catalogue, and a shell that keeps page state

- **Status:** Accepted (implemented 2026-10-17)
- **Context:** Implementing the brand/UI specification meant a large presentation rewrite:
  a design system, an adaptive shell, a component library, per-role screens, and two locales.
  Three sub-decisions shape the client.
- **Decision:**
  1. **Design tokens are the single source of visual truth.**
     `core/theme/tokens.dart` defines colours, spacing, radii, motion, layout constants, status
     kinds, a `ThemeExtension`, and WCAG `Contrast` helpers. Screens read `context.tokens` /
     `context.texts`; they do not hardcode values. `buildTheme(brightness, {schoolAccent})`
     builds light and dark themes **designed separately**, with school accents
     contrast-corrected and semantic colours non-overridable.
  2. **Every page uses `ScreenScaffold`.** It renders the page header (one H1, optional
     breadcrumbs from the expanded window class up, primary/secondary actions) and the correct
     gutters for the window class. Screens reached by `context.push` render as pushed pages;
     shell destinations render embedded. This is what makes the pages visually consistent and
     is exercised by `test/layout_test.dart`.
  3. **The string catalogue stays hand-rolled.** `core/l10n/strings.dart` is a map-backed
     EN + SW catalogue exposed as typed getters (`S.of(context).save`). ARB files
     (`lib/l10n/app_{en,sw}.arb`) are **generated** from it by `dart run tool/generate_arb.dart`
     and are the translator-facing source of record; `gen_l10n` is deliberately **not** wired
     in, so the build has no code-generation step. `test/l10n_test.dart` fails if the two
     catalogues diverge, if a key has no getter (or vice versa), if a `%placeholder%` is not
     declared, or if the ARBs drift from the catalogue.
  4. **Routing lives in `lib/app/router.dart`** with paths in the leaf
     `core/router/app_routes.dart`; `core/router/app_router.dart` was deleted because
     importing it from `HomeShell` created an import cycle. **`StatefulShellRoute` is
     deferred**: `AdaptiveShell` preserves page state by building destinations lazily on first
     visit and keeping them in a frozen `IndexedStack`, which also survives window resizes
     (bottom bar → rail → extended sidebar) without losing scroll position or form input.
- **Consequences:**
  - Adding a screen means: add a route path, add a `ScreenScaffold`, use the component library,
    add strings to both catalogues, re-run the ARB generator.
  - `flutter analyze` is clean and `flutter test` runs 61 tests (presentation plus the chat
    protocol suite added in ADR-015).
  - A future move to `gen_l10n` is additive: the ARB files already exist and are drift-tested;
    only the runtime lookup would change.
  - `StatefulShellRoute` (per-destination browser URLs and deep links into tabs) remains a
    follow-up; today the shell owns its own index.

---

## ADR-015 — Chat client speaks STOMP over WebSocket (supersedes the client half of ADR-005)

- **Status:** Accepted (implemented 2026-10-17)
- **Context:** ADR-005 shipped the server-side STOMP stack and left the Flutter client on a
  15-second REST poll as an explicit follow-up. Polling costs a request per open conversation
  per user, adds up to 15s of latency, and cannot express typing or delivery state.
- **Decision:**
  - The client now connects to the server's existing `/ws` endpoint over
    `web_socket_channel` and speaks **STOMP 1.2** directly. No new backend code was required.
  - The protocol lives in `core/chat/stomp.dart` (framing only, fully unit-testable) and
    `core/chat/chat_socket.dart` (transport: handshake, subscription bookkeeping, heartbeats,
    dead-link detection, reconnect with capped exponential backoff).
  - `features/chat/data/chat_providers.dart` owns **one socket per school**; channel screens
    subscribe and unsubscribe around it, so a reconnect does not restart on navigation.
  - Destinations follow the backend contract: subscribe `/topic/school.{schoolId}.channel.{channelId}`,
    send to `/app/channels/{id}/send`, signal typing to `/app/channels/{id}/typing`, and read
    `/user/queue/chat.ack` (duplicate suppression) and `/user/queue/chat.errors`.
  - **Polling is retained as the fallback**, switched on whenever the socket is not connected
    (and labelled in the app bar). The screen therefore degrades instead of breaking offline.
  - Sending is idempotent across all three paths (STOMP → REST → offline queue) because every
    attempt carries the same client-generated `clientId`, which the server treats as the
    idempotency key. A STOMP send that is not confirmed within 6 seconds falls back to REST.
- **Consequences:**
  - Chat is realtime, shows a typing indicator, and only exchanges frames when something
    happens; the REST poll no longer runs while the socket is up.
  - The client gained exactly one dependency (`web_socket_channel`, a Dart-team package) and a
    hand-rolled STOMP codec instead of a general STOMP library, so the surface is small and
    testable (`test/chat_test.dart`).
  - The token is passed as the `access_token` query parameter because browsers cannot set
    headers on a WebSocket upgrade; the socket re-reads the token on every reconnect so a
    refresh is picked up.

---

## ADR-016 — Shell destinations are addressed by id, not by route

- **Status:** Accepted (implemented 2026-10-17)
- **Context:** `AdaptiveShell` keeps its destinations in a lazy `IndexedStack` rather than
  giving each one a `GoRoute` (ADR-014). Screens that want to move the user — the landing-page
  hero, an empty state, a dashboard tile — had no way to do it, because there is no path to
  `context.go` to.
- **Decision:**
  - `core/navigation/destinations.dart` defines stable destination ids (`ShellDestination`) and
    a one-shot `shellDestinationProvider`.
  - `NavItem` gained an optional `id`. `HomeShell` listens to the provider, resolves the id to
    an index, focuses it, and immediately clears the request so the same destination can be
    requested twice.
  - Screens call `goToShellDestination(ref, ShellDestination.timetable)`.
- **Consequences:**
  - Cross-screen navigation works without inventing routes for tabs, and the shell keeps owning
    its own index (so a later `StatefulShellRoute` migration only needs to translate ids to
    paths).
  - The provider is deliberately not permission-aware: if the destination is not in the
    signed-in user's filtered `NavItem` list, the request is ignored.

---

## ADR-017 — Visual upgrade: gradients, glass, and motion as tokens (not one-off styling)

- **Status:** Accepted (implemented 2026-10-17)
- **Context:** Round two of the visual work needed a richer look — gradient hero panels, frosted
  chrome, shimmer skeletons, animated figures, and consistent page transitions — without
  letting ad-hoc colours and durations spread through the screens.
- **Decision:**
  - New token groups in `core/theme/tokens.dart`: `AppGradients` (hero, teal, attention, dark
    hero variant, hero glow), `AppGlass` (blur + per-theme alpha), `AppElevation` (card, raised,
    overlay, coloured glow), and an extended `AppMotion` (enter, stagger, count, emphasis).
  - New primitives: `HeroHeader` and `GlassPanel` (`core/widgets/surfaces.dart`),
    `AnimatedCount`, `EnterFade`, `StaggeredColumn` (`core/widgets/motion.dart`),
    `Shimmer`, `SkeletonBlock`, `SkeletonTileRow` (`core/widgets/states.dart`).
  - `MetricCard` gained `numericValue` (count-up), `spark` (sparkline) and `accent` (gradient
    rail); `TrendSparkline` gained a gradient area fill; `SimpleBarChart` animates its bars.
  - `ScreenScaffold` gained a `header` override so a page can replace its generated
    `PageHeader` with a hero while the shell's top bar still shows the page title.
  - Page transitions are unified on the Material 3 fade-through on every platform.
  - **Every animated primitive collapses to a static frame** when
    `MediaQuery.disableAnimations` is set, and `GlassPanel` drops its blur under the same
    signal, so reduce-motion and low-end devices pay nothing for the polish.
  - Sparklines are only ever fed real series data; the Today tiles use animated counts and
    accent rails rather than invented history.
- **Consequences:**
  - The look is consistent because the only place a new gradient or duration can be introduced
    is the token file.
  - `flutter analyze` stays clean and the widget tests still pass unmodified — the upgrades are
    additive optional parameters, not signature changes.

## ADR-018 — Consecutive-lesson rule: a teacher may not teach back-to-back unless it is a double

- **Status:** Accepted (implemented 2026-10-17)
- **Context:** Schools reported that the generator happily produced timetables where a teacher taught
  two different classes in adjacent periods. Staff consider that unworkable: back-to-back periods are
  only acceptable when the two halves belong to one double-period lesson. The handbook's constraint
  form is `busy[t] + busy[t+1] <= 1 + doubleStartsAt[t]` for every teacher and every adjacent pair
  inside the same session.
- **Decision:**
  - **Sessions, not just periods.** `timetable_period` rows flagged `is_break` end a session, so two
    periods are adjacent only when nothing sits between them in the ordered period list. A double
    period can never be placed across a break or lunch (new hard issue `double_spans_break`), and a
    lesson either side of a break is not "consecutive".
  - **The rule is enforced twice.** `TimetableValidator` reports `teacher_consecutive` as a hard issue
    by sweeping each teacher's day into runs of touching occupied slots and counting distinct lessons
    per run: one double period in a run is a single lesson and is legal, while a double followed by a
    single, or two doubles in a row, is not. `TimetableGenerator` refuses a slot whose preceding or
    following period already holds a lesson for that teacher, and `TimetableService.checkClash` rejects
    a manual edit or swap with the same message the editor can show.
  - **Strictness is a school setting, not code.** `school.settings` carries
    `timetable.maxConsecutivePeriods` (default 1), the per-teacher override map
    `timetable.teacherMaxConsecutivePeriods`, and `timetable.teachingDays`. This follows the existing
    `chat.directMessagesEnabled` convention, so no migration or people-module change is needed.
  - **Instant capacity pre-check.** Before solving, `TimetableService` compares each teacher's demanded
    periods with `TimetableRules.weeklyCapacityInPeriods()`, the exact maximum placeable under the rule
    (a four-slot session holds 3 periods with doubles or 2 without, so an eight-period day split by
    lunch holds 6 a day, 30 a week). Exceeding it fails fast with `teacher_capacity` and the setting to
    change, instead of returning a half-empty timetable.
  - **Gap goal.** The handbook's "few gaps in a teacher's day" objective is replaced by "avoid gaps
    longer than two periods": `teacher_long_gap` is a *soft* issue, gaps are measured inside a session
    (a break is not a gap), and the generator's tie-break prefers solutions with fewer long gaps.
  - Full CP-SAT/OR-Tools solving remains the documented follow-up ([ADR-003](DECISIONS.md)); the rule,
    the capacity bound and the gap objective are written so the same `TimetableRules` feeds either
    solver.
- **Consequences:**
  - A generated timetable now satisfies the rule by construction and the validator re-derives it
    independently, so the two implementations cannot share a bug.
  - **Bug found and fixed:** `TimetableGenerator` never added successfully placed lessons to its
    result list, so `generate` returned only locked placements: an empty timetable in production, and
    a crash-free failure that no existing test covered. Placed tokens are now kept, the comparison that
    ordered teachers no longer consults the random source (it was not a total order and could fail
    `List.sort`), and `TimetableGeneratorTest` asserts the invariant that the validator finds no hard
    violation in generated output.
  - Relaxed per-teacher limits are honoured by the validator and the capacity bound, but the heuristic
    generator always enforces the strict limit, which is conservative rather than wrong.

## ADR-019 — Lesson reminders: occurrences materialised from the published timetable

- **Status:** Accepted (implemented 2026-10-17)
- **Context:** The handbook requires a reminder two minutes before a lesson to the teacher and that
  class's students, parents off by default, one reminder for a double period, a school-wide default
  with a per-user override, and an idempotency key of (occurrence, user, offset).
- **Decision:**
  - **Occurrences.** `lesson_occurrence` holds one dated lesson (class, stream, subject, teacher,
    room, date, start, end, span) expanded from a published grid. A double period is one row with
    `span = 2`, so "one reminder at the start of a double" falls out of the model instead of needing
    a special case. A row is identified by (class, stream, subject, date, start), so materialisation
    refreshes in place and revives a cancelled row rather than duplicating.
  - **Reminders are scheduled notifications**, not a second delivery path: `notification` gained
    `due_at` and `dedupe_key`, and a unique index on (school, `dedupe_key`). A future-dated row is
    invisible in the inbox (`due_at is null or due_at <= now()`) and is released by
    `DueNotificationWorker` and `ScheduledNotificationReleaser`, which claim with
    `FOR UPDATE SKIP LOCKED` by **clearing `due_at`** — the claim marker itself — so a reminder cannot
    be taken twice even with several instances. In-app rows flip to sent; external channels are
    enqueued on the existing transactional outbox, so a reminder inherits the outbox's retries.
  - **Idempotency key** is `lesson.reminder:{occurrence}:{offset}`, with the channel appended by the
    notifications layer, so the same reminder on two channels stays idempotent per channel.
  - **Recipients** are the lesson's teacher, then the class's enrolled students. Guardians are
    included only when `timetable.reminderNotifyGuardians` is true, which is off by default.
  - **Settings** live in `school.settings`, following the established convention:
    `timetable.reminderLeadMinutes` (default 2), the per-user override map
    `timetable.userReminderLeadMinutes`, `timetable.reminderNotifyGuardians` and
    `timetable.scheduleHorizonDays` (default 30).
  - **Publishing rolls the timetable forward.** The term's future occurrences produced by any other
    version are cancelled and their pending reminders withdrawn through
    `NotificationsApi.cancelScheduled(prefix)`; the new version is then materialised.
    `LessonScheduleWorker` extends the horizon hourly, so a reminder is always scheduled well before
    its lesson.
  - **Pure cores.** The arithmetic (weekday matching, period times in the school's time zone, doubles
    that must not cross a break, the window bound) is `OccurrenceMaterialiser`, and the reminder set
    is `LessonReminderPlanner`. Both are free of Spring and the database and are unit tested directly.
  - `GET /timetable/occurrences` exposes the dated lessons, which is the agenda view for a class or a
    teacher.
- **Consequences:**
  - Verified against a real PostgreSQL instance: `V15` applies through Flyway, the cross-tenant
    isolation suite still passes with the new table under forced row-level security, and the dedupe
    key, the inbox filter and the claim were probed directly (the second identical insert is skipped,
    a future reminder is invisible, and claiming twice returns one row then none).
  - There is no holiday calendar yet, so a term's `starts_on`/`ends_on` bound the schedule; holidays
    and mid-term breaks are a documented follow-up rather than a silent assumption.
  - A per-user lead override lives in the school settings jsonb (like the consecutive-lesson
    overrides) instead of a user-preference table, so an override is stored per school.
  - Reminders are in-app today. Email/SMS work through the same path but are not enabled by default,
    because the school's guardian-consent and channel defaults are not yet expressed as settings.

