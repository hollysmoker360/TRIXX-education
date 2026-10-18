# Module Specification: Marks Gradebook

**Priority:** P1 — Core academics
**Placement:** New backend module `backend/src/main/java/com/schoolsphere/grades/` (`grades.api`, `grades.internal`, `grades.web`)
**Flutter:** `app/lib/features/grades/`
**Status:** Not started (ADR-013 notes marks built on assignment grade endpoints; no gradebook backend yet)

---

## 1. Module Overview

The Marks Gradebook is a complete assessment entry, marking, feedback, moderation, and results publication system. It supersedes the ad-hoc "marks built on assignment grade endpoints" approach by providing:

- Configurable assessments with maximum marks and weighting
- Per-student mark entry with grading scales and rubrics
- Feedback comments and missing-submission tracking
- Moderation workflow with draft → approval → publication
- Auditable correction history on published marks
- Class performance analytics

---

## 2. Package Structure (backend)

```
grades/
├── api/
│   ├── AssessmentApi.java
│   ├── GradeEntryApi.java
│   └── GradeScaleRef.java        (references people.grading-scales)
├── internal/
│   ├── entity/
│   │   ├── Assessment.java
│   │   ├── AssessmentComponent.java
│   │   ├── GradeEntry.java
│   │   ├── GradeBand.java
│   │   └── MarkCorrection.java
│   ├── repository/
│   │   ├── AssessmentRepository.java
│   │   ├── GradeEntryRepository.java
│   │   └── MarkCorrectionRepository.java
│   ├── service/
│   │   ├── AssessmentService.java
│   │   ├── GradeEntryService.java
│   │   ├── GradeCalculationService.java
│   │   └── ModerationService.java
│   └── event/
│       └── MarksPublishedEvent.java
└── web/
    ├── AssessmentController.java
    ├── GradeEntryController.java
    ├── ModerationController.java
    └── GradebookController.java
```

---

## 3. Database Schema

Flyway migration: `V23__marks_gradebook.sql`

### Table: assessment

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, UUIDv7 | Assessment id |
| school_id | UUID | FK, NOT NULL, RLS | Owning school |
| class_group_id | UUID | FK, NOT NULL | Class / stream being assessed |
| subject_id | UUID | FK, NOT NULL | Subject |
| teacher_membership_id | UUID | FK, NOT NULL | Teacher responsible |
| term_id | UUID | FK, NOT NULL | Academic term |
| title | varchar(200) | NOT NULL | Assessment name (e.g. "Mid-term Math") |
| type | varchar(40) | NOT NULL | test | quiz | exam | practical | project |
| max_marks | numeric(5,2) | NOT NULL | Maximum possible score |
| weight | numeric(4,2) | NOT NULL DEFAULT 1.00 | Weight in final grade |
| date_assessed | date | NOT NULL | Date assessment took place |
| due_date | date | | Deadline for submissions |
| grading_scale_id | UUID | FK | Optional grading scale override |
| published | boolean | NOT NULL DEFAULT false | Published to students/parents |
| published_at | timestamp | | When published |
| version | int | NOT NULL DEFAULT 0 | JPA optimistic lock |
| created_at / updated_at / deleted_at | | soft delete | TenantEntity columns |

### Table: assessment_component

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, UUIDv7 | Component id |
| assessment_id | UUID | FK, NOT NULL | Parent assessment |
| name | varchar(200) | NOT NULL | Component name (e.g. "Section A") |
| max_marks | numeric(5,2) | NOT NULL | Component max marks |
| weight | numeric(4,2) | NOT NULL DEFAULT 1.00 | Component weight |
| position | int | NOT NULL | Order within assessment |

### Table: grade_entry

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, UUIDv7 | Grade entry id |
| assessment_id | UUID | FK, NOT NULL | Assessment |
| student_id | UUID | FK, NOT NULL | Student |
| marks_obtained | numeric(5,2) | | Marks awarded |
| max_marks | numeric(5,2) | NOT NULL | Snapshot of assessment max marks |
| grade | varchar(10) | | Letter/level grade computed |
| feedback | text | | Teacher feedback |
| status | varchar(20) | NOT NULL DEFAULT 'draft' | draft | submitted | moderated | published |
| submitted_at | timestamp | | When teacher submitted |
| moderated_by | UUID | FK | Membership who moderated |
| moderated_at | timestamp | | Moderation timestamp |
| version | int | NOT NULL DEFAULT 0 | JPA optimistic lock |
| created_at / updated_at / deleted_at | | soft delete | TenantEntity columns |

Unique constraint: (assessment_id, student_id)

### Table: mark_correction

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, UUIDv7 | Correction id |
| grade_entry_id | UUID | FK, NOT NULL | Entry being corrected |
| previous_marks | numeric(5,2) | NOT NULL | Marks before correction |
| new_marks | numeric(5,2) | NOT NULL | Marks after correction |
| reason | varchar(500) | NOT NULL | Correction justification |
| corrected_by | UUID | FK, NOT NULL | Membership who corrected |
| corrected_at | timestamp | NOT NULL | Correction timestamp |
| approved_by | UUID | FK | Membership who approved correction |
| approved_at | timestamp | | Approval timestamp |
| created_at | timestamp | NOT NULL | TenantEntity column |

### Table: grade_band

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, UUIDv7 | Band id |
| school_id | UUID | FK, NOT NULL | School |
| name | varchar(50) | NOT NULL | Band name (e.g. "Distinction") |
| min_score | numeric(5,2) | NOT NULL | Minimum percentage |
| max_score | numeric(5,2) | NOT NULL | Maximum percentage |
| grade_point | numeric(3,2) | | GPA equivalent |
| color_hex | varchar(7) | | UI color for band |

---

## 4. Permissions

Add to `PermissionCatalog`:

| Permission | Description | Roles |
|------------|-------------|-------|
| `grade.enter` | Enter grades for an assessment | principal, admin, dept_head, teacher |
| `grade.publish` | Publish grades to students/parents | principal, admin, dept_head, teacher |
| `grade.view.own` | Learner views their own grades | student |
| `grade.view` | View grades for classes managed | principal, admin, dept_head, teacher |
| `grade.moderate` | Moderate/correct published marks | principal, admin, dept_head |

The everyone bundle (`school.view`, `chat.use`, `tasks.own`) does not include grade permissions.

---

## 5. API Endpoints

Base path: `/api/v1/grades`

| Method | Path | Permission | Description |
|--------|------|-----------|-------------|
| GET | `/assessments` | `grade.view` | List assessments for class/term/subject |
| POST | `/assessments` | `grade.enter` | Create assessment |
| GET | `/assessments/{id}` | `grade.view` | Get assessment detail |
| PATCH | `/assessments/{id}` | `grade.enter` | Update assessment (unpublished only) |
| DELETE | `/assessments/{id}` | `grade.enter` | Delete assessment (unpublished only) |
| GET | `/assessments/{id}/entries` | `grade.view` | List grade entries |
| POST | `/assessments/{id}/entries` | `grade.enter` | Bulk enter marks |
| PATCH | `/entries/{entryId}` | `grade.enter` | Update single grade entry |
| POST | `/assessments/{id}/publish` | `grade.publish` | Publish assessment results |
| POST | `/entries/{entryId}/correct` | `grade.moderate` | Request mark correction |
| GET | `/students/{studentId}/grades` | `grade.view.own` | Student's own grades |
| GET | `/classes/{classId}/gradebook` | `grade.view` | Full gradebook grid view |
| GET | `/bands` | `grade.view` | List grade bands for school |
| POST | `/bands` | `grade.enter` | Create grade band |

Errors: RFC 9457 Problem Details. `409` if attempting to publish already-published assessment; `403` if teacher not assigned to class.

---

## 6. Backend Services

### AssessmentService
- `createAssessment(AssessmentDTO, schoolId, actorId)` — validates teacher assignment to class, sets max_marks from components if not provided
- `publishAssessment(assessmentId, actorId)` — sets published=true, published_at, emits MarksPublishedEvent, triggers notifications
- `validateAssessment(assessmentId)` — checks all required entries have marks if marked mandatory

### GradeEntryService
- `enterGrades(assessmentId, List<GradeEntryDTO>, actorId)` — upsert entries, compute grade from bands, recalculate assessment statistics
- `correctMark(entryId, correctionDTO, actorId)` — creates MarkCorrection row, requires moderation approval if entry already published

### GradeCalculationService
- `computeGrade(marks, maxMarks, gradingScaleId)` — returns letter/level + grade point
- `computeClassStatistics(assessmentId)` — mean, median, distribution, pass rate

### ModerationService
- `submitForModeration(entryId, actorId)` — sets status=moderated
- `approveCorrection(correctionId, actorId)` — applies correction, re-publishes if needed

### Events
- `MarksPublishedEvent` — consumed by notifications module to alert students/parents
- `GradeEnteredEvent` — consumed by Student Success module for risk indicators

---

## 7. Flutter Frontend

### Screens (`app/lib/features/grades/`)

**GradebookScreen** (`gradebook_screen.dart`)
- Grid view: students × assessments
- Each cell shows marks, grade badge, status chip (draft/submitted/published)
- Inline editing with progress indicator
- "Publish" action button (requires `grade.publish`)
- Filter by term, subject, class
- Export to CSV/PDF (permission-gated)

**AssessmentEntryScreen** (`assessment_entry_screen.dart`)
- Form: title, type, class, subject, term, max marks, weight, date, due date
- Components section: add/remove assessment components with weights
- Save as draft / submit for moderation / publish

**GradeEntryDetailScreen** (`grade_entry_detail_screen.dart`)
- Per-student view of all assessments
- Mark entry field with validation (0 ≤ marks ≤ max)
- Feedback text area
- Correction request form (if published)

**StudentGradesScreen** (`student_grades_screen.dart`)
- Student view: own grades only (`grade.view.own`)
- Grouped by term/subject with GPA/grade band summary

**GradeBandScreen** (`grade_band_screen.dart`)
- Manage grade bands (school settings)

### Providers (`grades_providers.dart`)
- `assessmentsProvider` — FutureProvider filtered by class/term/subject
- `gradeEntriesProvider` — StreamProvider for real-time updates during entry
- `gradebookProvider` — Notifier for the grid state (optimistic updates)
- `gradeBandsProvider` — FutureProvider for school's bands

### Widgets
- `GradeCell` — editable cell with status chip
- `GradeBandBadge` — colored grade indicator
- `MarksChart` — distribution bar chart (collapses to table under reduced motion)

---

## 8. User Journeys

### Journey 1: Teacher enters marks for a test
1. Teacher opens GradebookScreen, selects class + subject + term
2. Clicks "Create Assessment", fills form: "Unit Test 1", type=test, max_marks=50, weight=1.0
3. Adds components: "Multiple Choice" (20), "Short Answer" (30)
4. Saves as draft
5. Enters marks per student inline; cells turn green on save
6. Clicks "Publish" — system validates all entries present, emits MarksPublishedEvent
7. Students/parents receive notification; grades visible in StudentGradesScreen

### Journey 2: Correction of a published mark
1. Principal spots data-entry error in published mark
2. Opens GradeEntryDetailScreen, clicks "Request Correction"
3. Enters previous_marks, new_marks, reason
4. Creates MarkCorrection row with status pending
5. Moderator reviews, clicks "Approve Correction"
6. Grade entry updated, correction history preserved in mark_correction table
7. Student/parent notified of grade change

### Journey 3: Student views own grades
1. Student logs in, navigates to Grades (permission `grade.view.own`)
2. Sees term-wise grade cards with grade band badges
3. Taps an assessment to see feedback and component breakdown

---

## 9. Integration Points

- **People:** student_id references people.student; teacher_membership_id references tenants.membership
- **Academics:** class_group_id, subject_id, term_id reference academics tables
- **Notifications:** MarksPublishedEvent triggers in-app notifications to students + guardians
- **Student Success:** GradeEnteredEvent feeds risk indicators (declining results, missing submissions)
- **Reports:** GradebookController endpoints feed Reports & Analytics module
- **Audit Log:** All mark entry, publish, and correction actions write audit_log rows (actor, before/after state)

---

## 10. Non-Functional Requirements

| Concern | Requirement |
|---------|-------------|
| Tenant isolation | RLS on all tables via `school_id = current_school()`; `@TenantScoped` on all services |
| Concurrency | JPA `@Version` on Assessment and GradeEntry; ETag/If-Match considered roadmap |
| Offline sync | Grade entries support `entity: 'grade_entry'` in sync push (create/update only); delete online-only |
| Accessibility | Grade cells ≥44px touch target, color never the only signal for pass/fail, chart table alternative |
| Audit | Mark corrections append-only; no update/delete on MarkCorrection |
| Performance | Gradebook grid paginated by class; cursor-based for large cohorts |
| Security | Teachers can only enter grades for classes they are assigned to (validated in service layer) |
