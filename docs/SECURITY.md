# Security

Threat model, implemented controls, children's-data considerations (Kenya DPA 2019), and
known gaps. Claims are mapped to the code that implements them. See also
[PERMISSIONS.md](PERMISSIONS.md), [SYNC.md](SYNC.md),
[runbooks/breach-response.md](runbooks/breach-response.md).

## 1. Threat model (STRIDE summary)

| Category | Threat | Mitigation (status) |
|----------|--------|---------------------|
| **S**poofing | Forged JWT / stolen access token | HS256 JWT signed with rotatable `kid` keys (`JwtService`); token version (`ver`) invalidates on password change/suspension; short TTL (15 min). ✅ |
| | Impersonating another school | `SchoolContextFilter` validates `X-School-Id` against the principal's memberships; `AuthorizationPolicy` denies mismatched school ids. ✅ |
| **T**ampering | Cross-tenant data writes | Row-level security with `FORCE ROW LEVEL SECURITY` on all tenant tables + transaction-local `set_config('app.current_school',…)`. ✅ |
| | Tampering with audit trail | `audit_log` append-only (no update/delete path); restricted grants. ✅ |
| **R**epudiation | Denying an action | `AuditLogService` records auth events and mutations with actor, membership, device, before/after state. 🟡 audit covers key events, not every endpoint |
| **I**nformation disclosure | Reading another tenant's data | RLS + `@PreAuthorize` + pull visibility filters ([SYNC.md](SYNC.md)). ✅ |
| | Sensitive child data at rest | `FieldCrypto` AES-256-GCM for national IDs / medical notes / guardian contacts. ✅ |
| | Credential leakage | Refresh tokens hashed at rest (SHA-256), single-use rotation with reuse detection; bcrypt passwords (`DelegatingPasswordEncoder`). ✅ |
| **D**enial of service | Brute force / flooding | **Gap:** in-process rate limiting designed but **not implemented** ([ADR-004](DECISIONS.md)); rely on auth design + external WAF for now. ❌ |
| | Oversized uploads | Multipart limits 25 MB file / 30 MB request. ✅ |
| **E**levation of privilege | Privilege escalation via roles | Roles are server-managed (`school_role`, `membership_role`); permission checks via `@policy.can(...)`; custom roles can only be assigned permissions from the catalog. ✅ |
| | Cross-module boundary violation | ArchUnit rules declared (dependency present); **test sources not committed yet** ([ADR-002](DECISIONS.md)). 🟡 |
| | WebSocket frame spoofing | `ChatChannelInterceptor` re-authorizes CONNECT/SUBSCRIBE/SEND with school + channel match. ✅ |

## 2. Controls checklist (mapped to implementation)

| Control | Implementation | Status |
|---------|----------------|--------|
| JWT access token, 15 min TTL | `application.yml` `access-ttl-minutes: 15`; `JwtService` | ✅ |
| Refresh token rotation + **reuse detection** (revokes token family) | `AuthService.refresh` — rotated token → `revokeFamily(..., "reuse_detected")` | ✅ |
| Token version invalidation | `app_user.token_version` / `school_membership.token_version`; bumped on password change; checked in `verifyAccessToken` | ✅ |
| Password hashing | Spring `DelegatingPasswordEncoder` (bcrypt) | ✅ |
| Stateless sessions | `SessionCreationPolicy.STATELESS` in `SecurityConfig` | ✅ |
| Row-level security (RLS) | `V11__rls_policies_and_grants.sql`, `TenantTransactionAspect` (`set_config`, transaction-local) | ✅ |
| Non-owner application role | `deploy/scripts/provision-db.sql` creates `schoolsphere_app`; Flyway runs as owner `schoolsphere`; `REVOKE` on `flyway_schema_history` | ✅ |
| Method-level authorization | `@EnableMethodSecurity` + `@PreAuthorize("@policy.can('…')")` on controllers | ✅ |
| Central authorization policy | `AuthorizationPolicy` (`@Component("policy")`) | ✅ |
| Field-level encryption (AES-256-GCM) | `FieldCrypto` + `EncryptedStringConverter` | ✅ |
| Append-only audit log | `AuditLogServiceImpl`, `audit_log` (no mutation endpoints) | ✅ |
| CORS allow-list | `CorsConfig` from `schoolschools.security.cors.allowed-origins` (env `SCHOOLSPHERE_CORS_ORIGINS`), credentials allowed, `ETag`/`Location` exposed | ✅ |
| Upload size limits | `spring.servlet.multipart` 25 MB / 30 MB | ✅ |
| RBAC permission catalog | `PermissionCatalog` + default role matrix | ✅ |
| WebSocket authorization | `ChatHandshakeInterceptor` + `ChatChannelInterceptor` | ✅ |
| Refresh token opacity / hashing | SHA-256 hashed at rest (`AuthService.sha256`) | ✅ |
| Sync op idempotency | `sync_operation` keyed by `op_id`, 90-day retention | ✅ |
| Rate limiting | **Gap** — designed ([ADR-004](DECISIONS.md)), **not implemented** | ❌ |
| MFA | Schema exists (`app_user.mfa_secret`, `verification_token`) but **not wired into any flow** | ❌ |
| Email verification / password reset | `verification_token` table + types exist; **no endpoints implemented** | ❌ |
| Secret manager for field key / JWT key | Keys come from env/config with **dev defaults**; production secret-manager integration is an ops roadmap item | 🟡 |

## 3. Children's data and Kenya Data Protection Act 2019

Trixx Education processes data about children, which is **sensitive personal data** under the
Kenya DPA 2019. Controls and posture:

- **Data minimization** — profiles collect only what the school flow needs (DemoSeeder and
  `PeopleService` pass minimal fields; medical/national-id fields are optional and
  field-encrypted when present).
- **Purpose limitation & access control** — parents see only guardian-linked children and only
  the fields enabled by the link's `visibilities` (attendance / grades / communication);
  students see only their own records (`grade.view.own`, own tasks, own attendance events).
- **Retention** — soft-delete tombstones (`deleted_at`) rather than physical deletes for
  domain rows; `sync_operation` retains for 90 days (`expires_at`); audit log is append-only.
  A formal retention/deletion schedule per table is a roadmap item.
- **Storage & transfers** — cloud DB in the system-of-record region; edge keeps data
  on-premise with local storage. RLS isolates tenants in a shared database.
- **Breach response** — see [runbooks/breach-response.md](runbooks/breach-response.md) for
  containment and the DPA 2019 notification timelines.
- **Rights (access/rectification/erasure)** — audit log + soft delete support access and
  rectification; a formal data-subject-request workflow is a roadmap item.
- **DPIA / lawful basis** — a Data Protection Impact Assessment for the school deployment is
  an operational responsibility of the deploying organization; this codebase provides the
  technical controls above.

## 4. Known gaps (roadmap — stated honestly)

| Gap | Impact | Notes |
|-----|--------|-------|
| **MFA not wired** | Weaker account assurance | `mfa_secret` and `verification_token` exist in schema (`V2`); no TOTP/enrollment/reset endpoints yet |
| **Rate limiting minimal / absent** | Exposure to brute force & flooding | [ADR-004](DECISIONS.md): in-process sliding window chosen for MVP but **not implemented**; mitigate with external WAF/proxy limits |
| **Email verification & password reset pending** | Accounts are not email-verified; no self-service reset | `verification_token` table + `password_reset`/`email_verify` types exist; endpoints not implemented (`/api/v1/auth/password` currently only changes password for an authenticated user) |
| **Field key / JWT key in config** | Key management is manual | Dev defaults present; production should inject from a secret manager (`SCHOOLSPHERE_JWT_SECRET`, field key) and rotate |
| **No ArchUnit rules on disk** | Module boundaries not enforced in CI | Dependency (`archunit-junit5` 1.5.1) present; rules pending |
| **No backend tests on disk** | Regressions not caught | Test dependencies declared; test authoring in progress ([PROGRESS.md](PROGRESS.md)) |
| **Audit coverage partial** | Some reads/writes not audited | Auth events + key mutations audited; not every endpoint |
| **No SBOM / Dependency-Check in CI** | Supply-chain blind spot | Listed as CI follow-ups in [OPERATIONS.md](OPERATIONS.md) |

## 5. Security-relevant configuration

| Env var | Purpose | Default (dev) |
|---------|---------|---------------|
| `SCHOOLSPHERE_JWT_SECRET` | JWT signing key (first entry signs; all verify — rotation-ready) | baked dev key |
| `SCHOOLSPHERE_CORS_ORIGINS` | Allowed CORS origin patterns | `http://localhost:*,http://127.0.0.1:*` |
| `SCHOOLSPHERE_DB_URL` / `_USER` / `_PASSWORD` | App datasource (non-owner `schoolsphere_app`) | `localhost:5433` |
| `SCHOOLSPHERE_DB_OWNER` / `_OWNER_PASSWORD` | Flyway owner role | `schoolsphere` / `schoolsphere_dev` |
| `SCHOOLSPHERE_STORAGE_TYPE` / `_ROOT` | Storage backend (`local`/`s3`) and local root | `local` / `./data/storage` |
| field key (`schoolschools.security.field-key`) | AES-256-GCM field encryption key | dev default (change in prod) |

Full environment list: [OPERATIONS.md](OPERATIONS.md).
