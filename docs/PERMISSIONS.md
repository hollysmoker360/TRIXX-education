<!-- Generated from code — regenerate when PermissionCatalog changes -->

# Permissions

Permission catalog and the **default role → permission matrix** (handbook §8.2 / §8.3),
transcribed from `backend/src/main/java/com/schoolsphere/tenants/api/PermissionCatalog.java`.
Roles are seeded per school (`school_role`) and granted via `school_membership` /
`membership_role`.

Regenerate this document whenever `PermissionCatalog` (constants, `SYSTEM_ROLES`, or
`ALL_PERMISSIONS`) changes. Enforcement is via
`@PreAuthorize("@policy.can('…')")` on controllers (see [API.md](API.md)); the policy bean is
`AuthorizationPolicy`.

**Everyone bundle:** `defaultPermissionsFor(role)` always merges
`{school.view, chat.use, tasks.own}` into every role, so these three are granted to **all**
roles even where a role's explicit set omits them.

## 1. Permission catalog

| Permission | Description | Roles that hold it |
|-----------|-------------|--------------------|
| `school.view` | Basic access to the current school; required by sync push/pull and lesson rooms | *all roles* (everyone bundle) |
| `school.settings.manage` | Change school settings; create/edit custom roles | owner, principal, admin, it_admin |
| `people.view` | List students, teachers, parents, guardians, enrollments, teacher-assignments, grading-scales, and school members | owner, principal, admin, dept_head, teacher, bursar, it_admin |
| `people.manage` | Create/edit/delete people; invite members; CSV import; enrollment transitions | owner, principal, admin |
| `people.manage.limited` | Limited people management for department heads | dept_head |
| `academics.view` | Read academic structure (years, terms, departments, classes, streams, subjects, classrooms, periods) | owner, principal, admin, dept_head, teacher |
| `academics.manage` | Create/edit/delete academic structure | owner, principal, admin |
| `timetable.view` | View timetable versions/entries, published timetable, and run validation | owner, principal, admin, dept_head, teacher, student, parent |
| `timetable.edit` | Create/edit/copy/generate timetable versions and entries | owner, principal, admin |
| `timetable.publish` | Publish and archive timetable versions | owner, principal, admin |
| `assignment.create` | Create/edit/publish assignments and quizzes; list them for staff | principal, admin, dept_head, teacher |
| `assignment.submit` | Draft/submit assignment submissions and submit quiz attempts (learner) | student |
| `assignment.grade` | View submissions/grades, grade submissions, publish/reopen grades, view quiz attempts | owner, principal, admin, dept_head, teacher |
| `grade.enter` | Enter grades | principal, admin, dept_head, teacher |
| `grade.publish` | Publish grades | principal, admin, dept_head, teacher |
| `grade.view.own` | Learner views their own grades | student |
| `child.data.view` | Parent/guardian views linked child data | parent |
| `attendance.take` | Open/close attendance sessions and mark attendance | principal, admin, dept_head, teacher |
| `attendance.correct` | Correct an attendance event | principal, admin, dept_head, teacher |
| `attendance.report` | Class attendance reports (JSON / CSV / PDF) | owner, principal, admin, dept_head, teacher, bursar |
| `learning.manage` | Manage learning resources (create/edit/publish/archive, versions, bookmarks, completions) | principal, admin, dept_head, teacher, librarian |
| `learning.view` | Read learning resources ("for me") | owner, principal, admin, dept_head, teacher, student, parent, librarian |
| `chat.use` | Use chat: channels, messages, search, report, read receipts | *all roles* (everyone bundle) |
| `chat.moderate` | View the reported-message moderation queue | owner, principal, admin |
| `tasks.own` | Manage personal tasks | *all roles* (everyone bundle) |
| `subscription.manage` | Manage school subscription / billing | owner, bursar |
| `audit.view` | View the audit log | owner, principal, admin, it_admin |
| `sync.device.manage` | Revoke registered sync devices | owner, it_admin |
| `notification.send` | Trigger/send notifications | owner, principal, admin, dept_head, teacher |

## 2. Default role matrix (system roles)

Role codes: `owner`, `principal`, `admin`, `dept_head`, `teacher`, `student`, `parent`,
`bursar`, `librarian`, `it_admin`. (✔ = granted by the default matrix; the everyone bundle
adds `school.view`, `chat.use`, `tasks.own` to all.)

| Permission | owner | principal | admin | dept_head | teacher | student | parent | bursar | librarian | it_admin |
|-----------|:-----:|:---------:|:-----:|:---------:|:-------:|:-------:|:------:|:------:|:---------:|:--------:|
| `school.view` | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| `school.settings.manage` | ✔ | ✔ | ✔ | | | | | | | ✔ |
| `people.view` | ✔ | ✔ | ✔ | ✔ | ✔ | | | ✔ | | ✔ |
| `people.manage` | ✔ | ✔ | ✔ | | | | | | | |
| `people.manage.limited` | | | | ✔ | | | | | | |
| `academics.view` | ✔ | ✔ | ✔ | ✔ | ✔ | | | | | |
| `academics.manage` | ✔ | ✔ | ✔ | | | | | | | |
| `timetable.view` | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | | | |
| `timetable.edit` | ✔ | ✔ | ✔ | | | | | | | |
| `timetable.publish` | ✔ | ✔ | ✔ | | | | | | | |
| `assignment.create` | | ✔ | ✔ | ✔ | ✔ | | | | | |
| `assignment.submit` | | | | | | ✔ | | | | |
| `assignment.grade` | ✔ | ✔ | ✔ | ✔ | ✔ | | | | | |
| `grade.enter` | | ✔ | ✔ | ✔ | ✔ | | | | | |
| `grade.publish` | | ✔ | ✔ | ✔ | ✔ | | | | | |
| `grade.view.own` | | | | | | ✔ | | | | |
| `child.data.view` | | | | | | | ✔ | | | |
| `attendance.take` | | ✔ | ✔ | ✔ | ✔ | | | | | |
| `attendance.correct` | | ✔ | ✔ | ✔ | ✔ | | | | | |
| `attendance.report` | ✔ | ✔ | ✔ | ✔ | ✔ | | | ✔ | | |
| `learning.manage` | | ✔ | ✔ | ✔ | ✔ | | | | ✔ | |
| `learning.view` | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | | ✔ | |
| `chat.use` | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| `chat.moderate` | ✔ | ✔ | ✔ | | | | | | | |
| `tasks.own` | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| `subscription.manage` | ✔ | | | | | | | ✔ | | |
| `audit.view` | ✔ | ✔ | ✔ | | | | | | | ✔ |
| `sync.device.manage` | ✔ | | | | | | | | | ✔ |
| `notification.send` | ✔ | ✔ | ✔ | ✔ | ✔ | | | | | |

> Note: `owner` is the school-creator role and is seeded as the membership of whoever creates
> the school (`TenantsApi.createSchool`). The matrix above is the default set applied via
> `PermissionCatalog.SYSTEM_ROLES`; schools can add custom roles built from the same catalog.

## 3. Role quick reference

| Role | Typical holder |
|------|----------------|
| `owner` | School owner / creator |
| `principal` | Principal |
| `admin` | School administrator |
| `dept_head` | Department head |
| `teacher` | Teacher |
| `student` | Learner |
| `parent` | Parent / guardian |
| `bursar` | Bursar / finance |
| `librarian` | Librarian |
| `it_admin` | IT administrator |

## 4. Enforcement points

- **Method security:** `@EnableMethodSecurity` + `@PreAuthorize("@policy.can('<perm>')")` on
  controller methods ([API.md](API.md)).
- **Central policy:** `AuthorizationPolicy.can(permission)` / `can(schoolId, permission)`
  checks the principal's permission set for the selected school; mismatched school → deny.
- **Sync pull visibility:** permission-driven filters (e.g. `attendance.take` /
  `attendance.report` gate staff attendance visibility) — [SYNC.md](SYNC.md).
- **Client gating:** the Flutter home shell shows destinations based on the same permission
  strings (`home_shell.dart` `can('…')`).
