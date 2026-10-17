# Progress

Live status by area. Markers: ✅ done & verified on disk · 🟡 partial / in progress · ❌ not started / absent.

**Last updated: 2026-10-17**

## Backend modules (`backend/src/main/java/com/schoolsphere/…`)

| Module | Status | Notes |
|--------|--------|-------|
| auth (login, JWT, refresh rotation + reuse detection, security filters) | ✅ | `auth/…`, `SchoolContextFilter`, `JwtAuthenticationFilter` |
| tenants (schools, memberships, roles, PermissionCatalog) | ✅ | `tenants/…` |
| academics (years, terms, departments, classes, streams, subjects, classrooms, periods) | ✅ | `academics/…` (departments consolidated here — [ADR-002](DECISIONS.md)) |
| people (students, teachers, guardians, enrollment, grading scales, CSV import) | ✅ | `people/…`, `CsvImportService` |
| learning (resources, assignments, submissions, quizzes) | ✅ | `learning/…` (assignments/quizzes consolidated here — [ADR-002](DECISIONS.md)) |
| tasks (personal tasks) | ✅ | `tasks/…` |
| messaging (channels + messages, STOMP WebSocket) | ✅ | `messaging/…`, `ChatWebSocketConfig` endpoint `/ws` |
| lessons (Jitsi conferencing rooms) | ✅ | `lessons/…`, deterministic room names |
| attendance (sessions, events, PDF/CSV reports) | ✅ | `attendance/…`, OpenPDF |
| notifications (in-app + transactional outbox worker) | ✅ | `OutboxWorker` with `FOR UPDATE SKIP LOCKED`, backoff; scheduled reminders released when due by `ScheduledNotificationReleaser` ([ADR-019](DECISIONS.md)) |
| timetable (versions, entries, heuristic + CP-SAT generator + independent validator) | ✅ | CP-SAT engine (OR-Tools) with staged solve, assumption-based infeasibility diagnosis, relaxed unmet-requirements drafts that cannot be published, and second-step room assignment; heuristic remains for partial regeneration and as a fallback ([ADR-018](DECISIONS.md), [ADR-020](DECISIONS.md)); publishing expands the grid into dated occurrences and schedules the two-minute reminders ([ADR-019](DECISIONS.md)) |
| sync (push/pull, change feed, devices) | ✅ | `sync/…`, `ChangeFeedReader`, `sync_operation` idempotency |
| audit (append-only audit log + read API, data-subject export) | ✅ | `AuditLogServiceImpl`, `AuditLogQueryService`, `DataSubjectService` |
| feature flags (table-backed, per-school override + global default) | ✅ | `featureflags/…` reads `feature_flag` (spec 3.2) |
| common (tenancy, crypto, UUIDv7, errors, web config) | ✅ | `TenantTransactionAspect`, `FieldCrypto`, `Uuid7` |
| bootstrap (DemoSeeder) | ✅ | seeds Kilimani Academy, password `Demo1234!` |
| subscriptions / billing (ADR-007) | ✅ | `subscriptions/…`: plan catalogue, lifecycle, `PaymentProvider` seam with a mock provider; `SubscriptionsController` exposes the API |
| rate limiting | ❌ | Not implemented yet — see [ADR-004](DECISIONS.md) |

Build state: `backend/target/` is present, so the backend has been compiled. No build script or
wrapper is committed; run with `D:\Trixx\tools\maven\bin\mvn.cmd -Dmaven.repo.local=D:\Trixx\m2repo spring-boot:run`.

## Database migrations (`backend/src/main/resources/db/migration`)

| Status | Notes |
|--------|-------|
| ✅ | `V1`–`V16` present: extensions, auth/users, tenants, academics, people, learning, tasks+messaging, attendance, timetable, notifications+audit+sync, RLS policies + grants (`V11`), change-feed triggers (`V12`), subscriptions/billing + `feature_flag` isolation (`V13`), `feature_flag.version` column (`V14`), lesson occurrences + scheduled notifications (`V15`), `timetable_version.unmet_requirements` (`V16`). |

## Brand and design system (`app/lib/core`)

The product is branded **Trixx Education** ([ADR-012](DECISIONS.md)). Visual truth lives in
`core/theme`; screens do not hardcode colours, sizes, radii, or durations.

| Item | Status | Notes |
|------|--------|-------|
| Brand constants | ✅ | `core/brand/brand.dart` (`AppBrand`: name, tagline, asset paths, minimum lockup width) |
| Logo assets | ✅ | `app/assets/brand/`: horizontal lockup (light + dark), mark, mono mark, app icon (SVG) + PNG derivatives |
| Launcher / PWA icons | ✅ | `dart run flutter_launcher_icons` (Android default + adaptive + monochrome, web icons); `tools/brand/render-icons.ps1` rasterises the web/PNG set offline |
| Design tokens | ✅ | `core/theme/tokens.dart`: `AppColors`, `AppSpacing`, `AppRadius`, `AppMotion`, `AppGradients`, `AppGlass`, `AppElevation`, `AppLayout`, `StatusKind`, `AppTokens` theme extension, WCAG `Contrast` helpers |
| Typography | ✅ | `core/theme/typography.dart`: Inter (bundled asset) with tabular figures for numbers |
| Themes | ✅ | `core/theme/app_theme.dart`: `buildTheme(brightness, {schoolAccent})`, light + dark designed separately; school accent is contrast-corrected, semantic colours are not overridable |
| Window classes | ✅ | `core/layout/window_class.dart`: Material breakpoints 600/840/1200; `usesRail`, `extendedRail`, `showsBreadcrumbs`, `contentColumns`, `gutter` |
| Adaptive shell | ✅ | `core/layout/adaptive_shell.dart`: bottom bar → rail → extended sidebar (with section headings) from width; lazily built pages kept alive across navigation and resize; command palette (Ctrl/⌘-K) |
| Top bar | ✅ | `core/layout/app_top_bar.dart`: page title, search, notifications badge, sync indicator, profile menu (theme + language) |
| Page frame | ✅ | `core/layout/{screen_scaffold,page_header}.dart`: one H1 per page, breadcrumbs from expanded up, primary/secondary actions |
| Component library | ✅ | `core/widgets/{states,surfaces,inputs,data,overlays,brand,charts,motion,offline,sync_indicator,common}.dart` |
| Hero / glass / motion primitives | ✅ | `HeroHeader` + `GlassPanel` (`surfaces.dart`), `AnimatedCount` + `EnterFade` + `StaggeredColumn` (`motion.dart`), `Shimmer` + `SkeletonBlock` + `SkeletonTileRow` (`states.dart`); gradient sparkline fill and animated bars in `charts.dart` ([ADR-017](DECISIONS.md)). All collapse to a static frame under reduced motion |
| Component gallery | ✅ | `features/gallery`, reachable in debug builds from the nav rail |
| Offline UX | ✅ | `OfflineBanner`, sync indicator with detail sheet, needs-review surfacing, low-bandwidth preference |

## Flutter client (`app/lib`)

| Feature | Status | Notes |
|---------|--------|-------|
| auth: sign in, password reset, MFA challenge, school picker, session expired | ✅ | `features/auth/presentation/` |
| session + token store (secure storage, theme/locale/column-view persistence) | ✅ | `core/session`, `core/networking/token_store.dart` |
| API client (Dio, JWT, single-flight refresh, RFC 7807 mapping) | ✅ | `core/networking/api_client.dart` |
| declarative navigation (GoRouter) | ✅ | `lib/app/router.dart` (routes) + `core/router/app_routes.dart` (paths) |
| home shell / navigation | ✅ | `features/home/presentation/home_shell.dart` (permission-filtered destinations in five sections: Workspace, Teaching & learning, School operations, Insights & communication, Administration); cross-screen jumps via `core/navigation/destinations.dart` ([ADR-016](DECISIONS.md)) |
| today (role-aware landing) | ✅ | `features/dashboard/presentation/today_screen.dart` — gradient `HeroHeader` with quick actions, animated KPI tiles with accent rails, staggered worklists |
| family (guardian view of children) | ✅ | `features/dashboard/presentation/parent_family_screen.dart` |
| overview | ✅ | `features/overview` |
| timetable | ✅ | `features/timetable` (published view + staff manage: create/generate/validate/publish/archive) |
| attendance | ✅ | `features/attendance` |
| assignments | ✅ | `features/assignments` |
| quizzes | ✅ | `features/quizzes` |
| marks (assessments hub, marks entry, report card) | ✅ | `features/assessment` — built on the assignment grade endpoints (no gradebook backend yet, [ADR-013](DECISIONS.md)) |
| examinations (assessments, sittings, marking queue, results) | ✅ | `features/examinations` on the `examinations` backend module: review/approval workflow, class registration, marking + moderation, result publication ([ADR-021](DECISIONS.md)). No question bank or timed online attempts yet |
| student profile | ✅ | `features/people/presentation/student_profile_screen.dart` (identity, enrolment, attendance, report card link) |
| tasks | ✅ | `features/tasks` |
| chat | ✅ | **Realtime over STOMP/WebSocket** (`/ws`): live messages, typing indicator, delivery confirmation, reconnect with backoff, and REST polling kept only as the offline fallback ([ADR-015](DECISIONS.md)). Protocol in `core/chat/{stomp,chat_socket}.dart`, wiring in `features/chat/` |
| people | ✅ | `features/people` |
| academics | ✅ | `features/academics` |
| reports | ✅ | `features/reports` |
| resources (learning hub) | ✅ | `features/resources` |
| notifications inbox | ✅ | `features/notifications` (unread badge in the top bar) |
| audit log viewer | ✅ | `features/audit` |
| sync center + offline queue | 🟡 | offline mutation queue + cursor ([SYNC.md](SYNC.md)); **no Drift read cache yet** ([ADR-006](DECISIONS.md)) |
| global sync status indicator + auto-sync on reconnect | ✅ | `core/sync/sync_status_provider.dart` (Notifier + connectivity listener) |
| lessons (Jitsi room launcher) | ✅ | `features/lessons`, launched from the timetable and standalone |
| subscriptions (plan catalogue, entitlements, checkout) | ✅ | `features/subscriptions` (mock provider in dev) |
| notification settings | ✅ | `features/settings`, device-local in-app/email/SMS toggles |
| settings (incl. EN/SW locale, theme, low-bandwidth) | ✅ | `features/settings`, `core/settings/preferences.dart` |
| l10n: EN + SW catalogue and translator ARBs | 🟡 | 442 keys in `core/l10n/strings.dart`, complete EN/SW parity; `lib/l10n/app_{en,sw}.arb` are generated (`dart run tool/generate_arb.dart`) and drift-tested. Every screen string is now sourced from the catalogue, including nav, section labels, form field labels, dialog titles, tooltips, metric pills and the chat realtime strings. **Residual gap (deliberate):** `features/gallery/presentation/component_gallery_screen.dart` (the developer component gallery, e.g. "Primary", "Typography", sample copy) and the `CSV`/`PDF` export acronyms stay English |
| accessibility | ✅ | Semantic headers/labels, ≥44 px targets, contrast-checked palettes, colour never the only signal, chart table alternatives |


## Docs (`docs/`)

| Document | Status |
|----------|--------|
| README.md | ✅ |
| DECISIONS.md (ADRs) | ✅ |
| ARCHITECTURE.md | 🟡 |
| SYNC.md | 🟡 |
| SECURITY.md | 🟡 |
| API.md | 🟡 |
| OPERATIONS.md | 🟡 |
| PERMISSIONS.md | 🟡 |
| RUNNING.md (how to run the full system locally) | ✅ |
| runbooks/ (backup-restore, deploy-edge, breach-response, restore-drill) | ✅ |

*(This documentation set is being written in the current session; the 🟡 entries land as part
of this pass.)*

## Deploy

| Item | Status | Notes |
|------|--------|-------|
| DB provisioning script | ✅ | `deploy/scripts/provision-db.sql` creates non-owner role `schoolsphere_app` for RLS |
| Dockerfile (backend) | ✅ | `deploy/docker/Dockerfile` (multi-stage; JRE 21 + Spring Boot jar) |
| Dockerfile (Flutter web) | ✅ | `deploy/docker/Dockerfile.flutter` (builds bundle, serves via nginx) |
| Docker Compose (dev / edge profiles) | ✅ | `deploy/compose/dev/`, `deploy/compose/edge/` |
| Docker Compose (cloud profile) | ✅ | `deploy/compose/cloud/` (app container; DB/bucket come from the platform) |
| Kubernetes manifests | ❌ | Not on disk |
| mTLS edge↔cloud credential rotation | ❌ | Outline only, see [runbooks/deploy-edge.md](runbooks/deploy-edge.md) |

## CI / tests

| Item | Status | Notes |
|------|--------|-------|
| Backend unit/integration tests | ✅ | surefire unit tests + failsafe `isolation` integration tests (`TenantIsolationIT`, `AuditLogQueryServiceTest`, `DataSubjectServiceTest`); `mvn verify` green |
| ArchUnit boundary rules | ✅ | `ModuleBoundaryTest` enforces module boundaries, controller rules (also covers `subscriptions`/`featureflags`) |
| Flutter tests | ✅ | 64 tests in `app/test/`: `widget_test.dart` (brand/format/strings), `l10n_test.dart` (EN/SW parity, catalogue↔getter coverage, ARB drift, placeholder declarations), `theme_test.dart` (WCAG contrast, light/dark, school-accent correction), `layout_test.dart` (breakpoints and per-class behaviour), `components_test.dart` (states, data table + sensitive masking, buttons, brand logo selection, avatars), `navigation_test.dart` (sidebar section headings, rail fallback), `chat_test.dart` (STOMP framing incl. split frames/heartbeats/CRLF, destinations, socket subscription bookkeeping) |
| Flutter golden / accessibility tests | ❌ | Component gallery is screenshot-ready; goldens not yet captured |
| Flutter integration tests | ❌ | `app/integration_test/` present but empty |
| GitHub Actions workflows | ✅ | `.github/workflows/ci.yml` (backend + Flutter jobs) |
| SBOM / Dependency-Check | ❌ | Listed as CI follow-ups in [OPERATIONS.md](OPERATIONS.md) |
