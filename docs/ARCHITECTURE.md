# Architecture

Logical and deployment architecture for Trixx Education, derived from the code as it exists on
disk. Companion documents: [DECISIONS.md](DECISIONS.md) (ADRs), [SYNC.md](SYNC.md),
[SECURITY.md](SECURITY.md), [PERMISSIONS.md](PERMISSIONS.md).

## 1. Logical view

```
                                 ┌──────────────────────────────────────────────┐
                                 │            Flutter client (app/)             │
                                 │  Riverpod · Dio · secure-storage · EN/SW    │
                                 │  offline mutation queue + change-feed cursor │
                                 └───────────────┬──────────────────────────────┘
                                                 │  HTTPS  (JWT Bearer +
                                                 │         X-School-Id,
                                                 │         X-Device-Id)
                        ┌────────────────────────┴─────────────────────────┐
                        │                                                  │
        ┌───────────────▼───────────────────┐        ┌─────────────────────▼─────────────────┐
        │   Cloud Spring Boot (profile=cloud)│        │   Edge Spring Boot (profile=edge)     │
        │   System of record, many tenants   │        │   One school / small set, local DB     │
        │   S3 storage, billing surface on   │        │   Local storage, billing off           │
        └───────────────┬───────────────────┘        └───────────────┬─────────────────────┘
                        │                                            │
        ┌───────────────▼───────────────────┐        ┌───────────────▼─────────────────────┐
        │   PostgreSQL (cloud, RLS)          │        │   PostgreSQL (edge, RLS)             │
        │   Flyway as owner role             │        │   Flyway as owner role               │
        │   change_log / sync_operation      │◄──────►│   change_log / sync_operation        │
        └────────────────────────────────────┘  sync  └───────────────────────────────────────┘
                                                 (push/pull over the same REST API,
                                                  transport secured by mTLS + JWT;
                                                  edge acts as a client of the cloud API)
```

Both deployments run the **same artifact**; behaviour is selected with
`spring.profiles.active`. STOMP-over-WebSocket (`/ws`) is served by both profiles for chat.
Notifications fan out through a transactional outbox worker that calls pluggable
`EmailProvider` / `SmsProvider` beans (logging no-ops in dev).

## 2. Deployment profiles

| Aspect | `dev` (default) | `cloud` | `edge` |
|--------|-----------------|---------|--------|
| Purpose | local development | multi-tenant system of record | on-premise single school, cloud replica |
| Datasource | `localhost:5433/schoolsphere` | `${SCHOOLSPHERE_DB_URL}` | `localhost:5432/schoolsphere` |
| Flyway user | owner `schoolsphere` | `${SCHOOLSPHERE_DB_OWNER}` | owner `schoolsphere` |
| App user | `schoolsphere_app` (non-owner, RLS) | `${SCHOOLSPHERE_DB_USER}` | `schoolsphere_app` |
| Storage | `local` (`./data/storage`) | `s3` (overridable) | `local` |
| Billing flag | `false` | `true` | `false` |
| Demo seeding | `true` | `false` (opt-in) | `false` |
| Structured logging | plain (DEBUG) | ECS | ECS |
| Extra config | — | — | `schoolschools.edge.cloud-base-url`, `schoolschools.edge.sync-enabled` |

Source: `application.yml`, `application-dev.yml`, `application-cloud.yml`,
`application-edge.yml`.

Edge-specific behaviour is minimal today: `application-edge.yml` declares
`schoolschools.edge.cloud-base-url` and `schoolschools.edge.sync-enabled`; the edge↔cloud sync
client that would consume them is **not yet implemented in code** — see
[runbooks/deploy-edge.md](runbooks/deploy-edge.md) and [SYNC.md](SYNC.md#edge-server-duties).

## 3. Module map (actual packages)

Packages under `backend/src/main/java/com/schoolsphere/`. Convention: `api/` = cross-module
interface, `internal/` = domain + persistence, `web/` = REST/WS controllers.

| Module | Packages | Notes |
|--------|----------|-------|
| auth | `auth.api`, `auth.internal`, `auth.security`, `auth.web` | JWT issuance/parse, refresh rotation + reuse detection, `JwtAuthenticationFilter`, `SchoolContextFilter`, `SecurityConfig` |
| tenants | `tenants.api` (incl. `PermissionCatalog`), `tenants.internal`, `tenants.web` | schools, branches, memberships, roles; `AuthorizationPolicy` bean name `policy` |
| academics | `academics.api`, `academics.internal`, `academics.web` | years, terms, **departments**, classes, streams, subjects, classrooms, timetable periods |
| people | `people.api`, `people.internal`, `people.web` | students, teachers, parents, guardian links, enrollment + transitions, teacher assignments, grading scales, CSV import |
| learning | `learning.internal`, `learning.web` | resources + versions/bookmarks/completions, **assignments**, submissions, **quizzes** |
| tasks | `tasks.internal`, `tasks.web` | personal tasks; listens to assignment-published events |
| messaging | `messaging.api`, `messaging.internal`, `messaging.web` | channels, members, messages; STOMP config, handshake + channel interceptors, broadcast listener |
| lessons | `lessons.api`, `lessons.internal`, `lessons.web` | `ConferencingProvider` SPI; `JitsiConferencingProvider` with deterministic room names |
| attendance | `attendance.api`, `attendance.internal`, `attendance.web` | sessions, append-only events, corrections, summary/history, PDF/CSV reports |
| notifications | `notifications.api`, `notifications.internal`, `notifications.web` | in-app notifications, `notification_outbox`, `OutboxWorker`, `EmailProvider`/`SmsProvider` |
| timetable | `timetable.internal`, `timetable.web` | versions, entries, `TimetableGenerator` (heuristic), `TimetableValidator` (independent) |
| sync | `sync.internal`, `sync.web` | push (opId idempotency), pull (cursor over `change_log`), devices, `ChangeFeedReader` |
| audit | `audit.api`, `audit.internal` | append-only `audit_log`; no read endpoint (write-only in API surface) |
| common | `common.api`, `common.error`, `common.persistence`, `common.security`, `common.tenant`, `common.util`, `common.web` | `CursorPage`/`PageResponse`, `ApiException`/`GlobalExceptionHandler`, `BaseEntity`/`TenantEntity`, `TenantContext`/`TenantTransactionAspect`, `Uuid7`, `FieldCrypto`, CORS + OpenAPI config |
| bootstrap | `bootstrap` | `DemoSeeder` (idempotent demo school) |

Consolidation deviations from the handbook's flat 16-module map (departments inside
`academics`; assignments/quizzes inside `learning`) are recorded in
[ADR-002](DECISIONS.md). There is **no payments/billing module** yet
([ADR-007](DECISIONS.md)).

## 4. Tenancy model (five enforcement layers)

1. **Service authorization** — `AuthorizationPolicy` (`@Component("policy")`) answers
   `can(permission)` / `can(schoolId, permission)` for the current principal. Controllers use
   `@PreAuthorize("@policy.can('…')")`; a school id that does not match the principal's
   selected school is denied. Catalog in
   [PERMISSIONS.md](PERMISSIONS.md).
2. **Request-level tenant resolution** — `SchoolContextFilter` reads `X-School-Id`, validates
   it against the user's memberships, and populates `TenantContext` for the request. Without
   a school header the request runs without tenant context (global endpoints only).
3. **Repository scoping + RLS** — every tenant service method is `@TenantScoped`;
   `TenantTransactionAspect` opens a transaction and runs
   `set_config('app.current_school', …, true)` and `set_config('app.actor_user', …, true)`
   *transaction-locally* before any statement, so pooled connections cannot leak context.
   `V11` enables **FORCE ROW LEVEL SECURITY** with a `tenant_isolation` policy
   (`school_id = current_school()`) on all tenant tables; `audit_log` allows platform rows
   only when no tenant context is set. With no context set, RLS returns zero rows.
4. **Object storage prefixes** — declared via `schoolschools.storage.type`
   (`local`/`s3`) and `schoolschools.storage.local-root`. **Honest caveat:** the config keys
   exist in the profiles but **no storage service implementation exists in the backend yet**;
   uploads currently only persist a `storageKey` string (e.g. on `resource_version`). Actual
   byte storage and per-school prefix isolation is a roadmap item.
5. **WebSocket authorization** — `ChatHandshakeInterceptor` + `ChatChannelInterceptor`
   authorize every STOMP frame: membership at `CONNECT`, school+channel match at `SUBSCRIBE`
   (topic pattern `/topic/school.{schoolId}.channel.{channelId}`), and again at `SEND`
   (app destinations `/app/channels/{id}/send|typing`).

## 5. Tech stack

| Layer | Technology | Version | Rationale |
|-------|-----------|---------|-----------|
| Backend language | Java | 21 | Records, pattern matching; LTS; matches Spring Boot 3.5 baseline |
| Backend framework | Spring Boot | 3.5.16 | One artifact for cloud + edge; embedded server, security, JPA, actuator |
| Build | Maven | toolchain `D:\Trixx\tools\maven`, repo `D:\Trixx\m2repo` | BOM-managed dependency versions |
| Persistence | Spring Data JPA + JDBC | via Boot | Entities under each module's `internal/` |
| Database | PostgreSQL | 18 (local) | RLS, JSONB, `citext`, sequences; drives tenancy and sync |
| Migrations | Flyway | via Boot BOM | Versioned `V1`–`V12`; run as owner role |
| Security | Spring Security + JJWT | 0.12.7 | HS256 JWT, rotatable `kid` keys, bcrypt via delegating encoder |
| Realtime | STOMP over WebSocket | via Boot starter | Chat broker `/topic`, `/queue`, app `/app` |
| Docs | springdoc-openapi | 2.9.1 | `/api/v1/docs` (Swagger UI) + `/api/v1/openapi.json` |
| PDF | OpenPDF | 3.0.5 | Attendance reports |
| Architecture tests | ArchUnit | 1.5.1 (test) | Module boundary enforcement (rules pending — [ADR-002](DECISIONS.md)) |
| Client framework | Flutter | 3.47.7 stable | One codebase for Android/iOS/web |
| Client language | Dart | ^3.13.5 | Matches Flutter 3.47.7 |
| Client state | flutter_riverpod | ^2.6.1 | Compile-safe DI + reactive state |
| Client networking | Dio | ^5.7.0 | Interceptors for JWT refresh + problem mapping |
| Client storage | flutter_secure_storage / shared_preferences | ^9.2.4 / ^2.3.0 | Tokens in secure storage; queue + cursor in prefs |
| Client l10n | hand-rolled `core/l10n/strings.dart` | EN + SW | No codegen; explicit EN/SW tables |

See [ADR-010](DECISIONS.md#adr-010--version-pins-actuals) for the full pin table.

## 6. Cross-cutting concerns

- **Errors:** RFC 9457 Problem Details via `GlobalExceptionHandler` / `ApiException`; the
  security entry point and access-denied handler also emit `application/problem+json`
  ([API.md](API.md)).
- **Pagination:** cursor pages (`CursorPage`) for feeds, offset pages (`PageResponse`) for
  admin lists.
- **Events:** Spring `ApplicationEventPublisher` in-process (e.g. `AssignmentPublishedEvent`
  → `tasks`), plus the durable `notification_outbox` for anything with external side-effects.
- **Audit:** `AuditLogService` writes append-only rows (login, mutations) with actor,
  device, and before/after state.
- **Observability:** actuator (`health`, `info`, `metrics`, `prometheus`) enabled; ECS
  structured logging on `cloud`/`edge`.
