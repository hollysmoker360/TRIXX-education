# API reference

Conventions plus a full endpoint inventory derived from the `@*Mapping` annotations in
`backend/src/main/java/com/schoolsphere/`. Live docs: Swagger UI at
`/api/v1/docs`, OpenAPI JSON at `/api/v1/openapi.json` (springdoc 2.9.1).

Related: [PERMISSIONS.md](PERMISSIONS.md) (permission catalog), [SYNC.md](SYNC.md) (sync),
[SECURITY.md](SECURITY.md).

## 1. Conventions

- **Base path:** `/api/v1`.
- **Resources:** plural nouns (`/people/students`, `/learning/assignments`,
  `/timetable/versions`).
- **Tenant selection:** send the access token as `Authorization: Bearer <jwt>` and select a
  school with the `X-School-Id` header. `X-Device-Id` is echoed for sync/audit bookkeeping.
- **Pagination:**
  - Offset pages → `PageResponse<T> { items, page, size, total }`
    (e.g. `/people/students?page=0&size=…`).
  - Cursor feeds → `CursorPage<T> { items, nextCursor }` (feed-style listings).
- **Errors:** RFC 9457 **Problem Details** (`application/problem+json`) with a `code`
  property, produced by `GlobalExceptionHandler`/`ApiException` and by the security entry
  point / access-denied handler. Types follow `https://schoolsphere.dev/errors/<code>`.
- **Idempotency:**
  - `POST /learning/assignments/{id}/submit` accepts an **`Idempotency-Key` header**
    (reused key → `409 idempotency_key_reused`).
  - `POST /sync/push` is idempotent per `opId` (`sync_operation`, 90-day retention).
  - There is no generic `Idempotency-Key` middleware on other endpoints.
- **Optimistic concurrency:** entities carry a JPA `@Version int version`. **ETag/If-Match:**
  the CORS config exposes `ETag` and `Location`, but no endpoint currently emits an `ETag`
  or validates `If-Match` — concurrency is via `version`. Considered a roadmap item.
- **Auth-required by default:** everything is `authenticated()` except
  `/api/v1/auth/login|register|refresh`, `/api/v1/openapi.json`, `/api/v1/docs/**`,
  `/actuator/health*`, `/actuator/info`, and `/ws/**` (`SecurityConfig`).

Permission column uses the shorthand from [PERMISSIONS.md](PERMISSIONS.md). “—” = any
authenticated user (record-level rules still apply in the service layer).

## 2. Endpoint inventory

### auth (`/api/v1/auth`)
| Method & path | Permission |
|---------------|-----------|
| `POST /login` | — (public) |
| `POST /register` | — (public) |
| `POST /refresh` | — (public) |
| `POST /logout` | — |
| `POST /password` (change current password) | — |

### me (`/api/v1/me`)
| Method & path | Permission |
|---------------|-----------|
| `GET /` (user, memberships, active school, permissions) | — |

### tenants / schools (`/api/v1/schools`)
| Method & path | Permission |
|---------------|-----------|
| `POST /` (create a school) | — |
| `GET /current` | `school.view` |
| `PUT /current` (update settings) | `school.settings.manage` |
| `GET /current/members` | `people.view` |
| `POST /current/members` (invite) | `people.manage` |
| `PATCH /current/members/{membershipId}/status` | `people.manage` |
| `PUT /current/members/{membershipId}/roles` | `people.manage` |
| `GET /current/roles` | `people.view` |
| `POST /current/roles` (create custom role) | `school.settings.manage` |
| `PATCH /current/roles/{roleId}` | `school.settings.manage` |

### academics (`/api/v1/academics`)
| Method & path | Permission |
|---------------|-----------|
| `GET /years`, `POST /years`, `PATCH /years/{id}`, `POST /years/{id}/activate`, `DELETE /years/{id}` | view=`academics.view`, write=`academics.manage` |
| `GET /terms`, `POST /terms`, `POST /terms/{id}/activate`, `DELETE /terms/{id}` | view=`academics.view`, write=`academics.manage` |
| `GET /departments`, `POST /departments`, `PATCH /departments/{id}`, `DELETE /departments/{id}` | view / manage |
| `GET /classes`, `POST /classes`, `PATCH /classes/{id}`, `DELETE /classes/{id}` | view / manage |
| `GET /streams`, `POST /streams`, `DELETE /streams/{id}` | view / manage |
| `GET /subjects`, `POST /subjects`, `PATCH /subjects/{id}`, `DELETE /subjects/{id}` | view / manage |
| `GET /classrooms`, `POST /classrooms`, `DELETE /classrooms/{id}` | view / manage |
| `GET /periods`, `POST /periods`, `DELETE /periods/{id}` | view / manage |

### people (`/api/v1/people`)
| Method & path | Permission |
|---------------|-----------|
| `GET /students` (page), `POST /students`, `GET /students/{id}`, `PATCH /students/{id}`, `DELETE /students/{id}` | view=`people.view`, write=`people.manage` |
| `POST /students/import` (CSV) | `people.manage` |
| `GET /teachers`, `POST /teachers`, `PATCH /teachers/{id}` | view / manage |
| `GET /parents`, `POST /parents` | view / manage |
| `GET /guardians`, `POST /guardians`, `PATCH /guardians/{id}` | view / manage |
| `GET /enrollments`, `POST /enrollments`, `POST /enrollments/{id}/transition` | view / manage |
| `GET /teacher-assignments`, `POST /teacher-assignments`, `DELETE /teacher-assignments/{id}` | view / manage |
| `GET /grading-scales`, `POST /grading-scales`, `POST /grading-scales/defaults`, `PATCH /grading-scales/{id}` | view / manage |
| `GET /me/student`, `GET /me/student-detail`, `GET /me/children` | — |

### learning — resources (`/api/v1/learning/resources…`)
| Method & path | Permission |
|---------------|-----------|
| `GET /resources`, `POST /resources`, `GET /resources/{id}`, `PATCH /resources/{id}`, `DELETE /resources/{id}` | `learning.manage` |
| `POST /resources/{id}/publish`, `POST /resources/{id}/archive` | `learning.manage` |
| `GET /resources/{id}/versions`, `POST /resources/{id}/versions`, `POST /resources/{id}/versions/{versionId}/restore` | `learning.manage` |
| `GET /resources/for-me`, `POST /resources/{id}/bookmark`, `POST /resources/{id}/complete` | — |

### learning — assignments (`/api/v1/learning/assignments…`)
| Method & path | Permission |
|---------------|-----------|
| `GET /assignments`, `POST /assignments`, `GET /assignments/{id}`, `PATCH /assignments/{id}`, `POST /assignments/{id}/publish` | `assignment.create` |
| `GET /assignments/{id}/submissions`, `GET /assignments/{id}/grades`, `POST /assignments/{id}/publish-grades`, `PATCH /submissions/{id}/grade`, `POST /submissions/{id}/reopen` | `assignment.grade` |
| `GET /assignments/for-me`, `GET /assignments/my-grades`, `GET /assignments/{id}/my-submission` | — |
| `POST /assignments/{id}/draft`, `POST /assignments/{id}/submit` (accepts `Idempotency-Key`) | `assignment.submit` |

### learning — quizzes (`/api/v1/learning/quizzes…`)
| Method & path | Permission |
|---------------|-----------|
| `GET /quizzes`, `POST /quizzes`, `GET /quizzes/{id}`, `POST /quizzes/{id}/publish`, `GET /quizzes/{id}/attempts`, `GET /quizzes/{id}/attempts/{attemptId}` | `assignment.create` |
| `GET /quizzes/for-me`, `POST /quizzes/{id}/attempts`, `GET /quizzes/{id}/my-attempts` | — |
| `POST /quizzes/{id}/attempts/{attemptId}/submit` | `assignment.submit` |

### tasks (`/api/v1/tasks`)
| Method & path | Permission |
|---------------|-----------|
| `GET /`, `POST /`, `PATCH /{id}`, `POST /{id}/complete`, `DELETE /{id}` | `tasks.own` |

### messaging / chat (`/api/v1/chat`)
| Method & path | Permission |
|---------------|-----------|
| `GET /channels`, `POST /channels` | `chat.use` |
| `GET /channels/{id}/members`, `POST /channels/{id}/members`, `DELETE /channels/{id}/members/{membershipId}` | `chat.use` |
| `POST /channels/{id}/archive`, `POST /channels/{id}/read` | `chat.use` |
| `GET /channels/{id}/messages`, `POST /channels/{id}/messages` | `chat.use` |
| `PATCH /messages/{id}`, `DELETE /messages/{id}`, `POST /messages/{id}/report` | `chat.use` |
| `GET /reported` (moderation queue) | `chat.moderate` |
| `GET /search` | `chat.use` |

Realtime: STOMP over WebSocket at `/ws` (broker `/topic`,`/queue`; app prefix `/app`;
user prefix `/user`). See [ADR-005](DECISIONS.md).

### lessons (`/api/v1/lessons`)
| Method & path | Permission |
|---------------|-----------|
| `GET /rooms?classGroupId=&subjectId=&topic=&date=` (Jitsi room) | `school.view` |

### examinations (`/api/v1/examinations`)
| Method & path | Permission |
|---------------|-----------|
| `GET /assessments?status=`, `GET /assessments/{id}`, `GET /exam-sessions?assessmentId=`, `GET /exam-sessions/{id}/candidates`, `GET /results`, `GET /results/{assessmentId}/candidates` | `exam.manage` or `exam.mark` or `exam.approve` |
| `POST /assessments`, `PATCH /assessments/{id}` (draft only), `DELETE /assessments/{id}` (draft, no sittings), `POST /assessments/{id}/submit`, `POST /assessments/{id}/archive` | `exam.manage` |
| `POST /assessments/{id}/return`, `POST /assessments/{id}/approve`, `POST /assessments/{id}/publish` | `exam.approve` |
| `POST /exam-sessions` (approved/published assessment; registers the class when `studentIds` is empty), `POST /exam-sessions/{id}/candidates`, `POST /exam-sessions/{id}/{start,complete,cancel}` | `exam.manage` |
| `GET /marking-queue` | `exam.mark` or `exam.approve` |
| `POST /marking-queue/{scriptId}/mark` (`{marks, absent, remarks}`) | `exam.mark` |
| `POST /marking-queue/{scriptId}/moderate` (different examiner from the marker) | `exam.approve` |
| `POST /results/{assessmentId}/publish` (every candidate marked or absent) | `exam.approve` |
| `GET /my-results` (published results only) | `grade.view.own` |

### attendance (`/api/v1/attendance`)
| Method & path | Permission |
|---------------|-----------|
| `POST /sessions`, `GET /sessions`, `GET /sessions/{id}`, `POST /sessions/{id}/close`, `DELETE /sessions/{id}`, `POST /sessions/{id}/marks` | `attendance.take` |
| `POST /events/{id}/correct` | `attendance.correct` |
| `GET /students/{studentId}/summary`, `GET /students/{studentId}/history` | — (record-level rules) |
| `GET /reports/class`, `GET /reports/class.csv`, `GET /reports/class.pdf` | `attendance.report` |
| `GET /students/{studentId}/report.pdf` | — (record-level rules) |

### notifications (`/api/v1/notifications`)
| Method & path | Permission |
|---------------|-----------|
| `GET /` (`?unreadOnly=&limit=`) | — |
| `GET /unread-count` | — |
| `POST /{id}/read` | — |
| `POST /read-all` | — |

### timetable (`/api/v1/timetable`)
| Method & path | Permission |
|---------------|-----------|
| `GET /versions`, `GET /versions/{id}`, `GET /versions/{id}/entries`, `GET /published` | `timetable.view` |
| `POST /versions/{id}/validate` | `timetable.view` |
| `POST /versions`, `PATCH /versions/{id}`, `DELETE /versions/{id}`, `POST /versions/{id}/copy` | `timetable.edit` |
| `POST /versions/{id}/generate`, `POST /versions/{id}/entries`, `PATCH /entries/{entryId}`, `DELETE /entries/{entryId}` | `timetable.edit` |
| `POST /versions/{id}/publish`, `POST /versions/{id}/archive` | `timetable.publish` |
| `GET /occurrences` (`?classGroupId=&teacherMembershipId=&from=&to=`) | `timetable.view` |
| `POST /versions/{id}/schedule`, `POST /versions/{id}/schedule/roll-forward` | `timetable.publish` |

`GET /occurrences` returns the dated lessons expanded from a published timetable. Publishing
rolls the timetable forward automatically and schedules the two-minute lesson reminders, so this
endpoint is the day/agenda view for a class or a teacher ([ADR-019](DECISIONS.md)).

### sync (`/api/v1/sync`)
| Method & path | Permission |
|---------------|-----------|
| `POST /push`, `GET /pull`, `POST /devices`, `GET /devices` | `school.view` |
| `DELETE /devices/{id}` (revoke) | `sync.device.manage` |

Full protocol: [SYNC.md](SYNC.md).

### audit
**No REST endpoints.** The audit log is write-only (`AuditLogService`); there is no read API
on the API surface.

## 3. Actuator / ops endpoints

| Path | Auth |
|------|------|
| `/actuator/health`, `/actuator/health/**` (incl. liveness/readiness groups), `/actuator/info` | public |
| `/actuator/metrics`, `/actuator/prometheus` | authenticated (exposed) |

See [OPERATIONS.md](OPERATIONS.md).
