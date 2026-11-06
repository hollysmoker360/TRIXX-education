# Module Specification: Student Success

**Priority:** P3 — Intelligence
**Placement:** New backend module `backend/src/main/java/com/schoolsphere/studentsuccess/` (`studentsuccess.api`, `studentsuccess.internal`, `studentsuccess.web`)
**Flutter:** `app/lib/features/student_success/`
**Status:** Not started

---

## 1. Module Overview

Student Success coordinates learner support, academic improvement, and intervention follow-ups. It provides early-warning indicators, teacher observations, evidence-backed referrals, individual learning-support plans, and progress tracking.

**Core principle:** The system recommends human review rather than automatically labeling learners or making high-impact decisions. All algorithmic indicators are advisory flags for staff review, not deterministic labels.

---

## 2. Package Structure (backend)

```
studentsuccess/
├── api/
│   ├── StudentProfileApi.java
│   ├── RiskIndicatorApi.java
│   ├── SupportReferralApi.java
│   ├── InterventionPlanApi.java
│   └── ProgressNoteApi.java
├── internal/
│   ├── entity/
│   │   ├── StudentProgressProfile.java
│   │   ├── RiskIndicator.java
│   │   ├── SupportReferral.java
│   │   ├── InterventionPlan.java
│   │   ├── SupportGoal.java
│   │   └── ProgressNote.java
│   ├── repository/
│   │   ├── StudentProgressProfileRepository.java
│   │   ├── RiskIndicatorRepository.java
│   │   ├── SupportReferralRepository.java
│   │   └── InterventionPlanRepository.java
│   ├── service/
│   │   ├── StudentSuccessService.java
│   │   ├── RiskIndicatorService.java
│   │   └── InterventionService.java
│   └── event/
│       └── InterventionRecommendedEvent.java
└── web/
    ├── StudentProfileController.java
    ├── SupportReferralController.java
    ├── InterventionPlanController.java
    └── ProgressNoteController.java
```

---

## 3. Database Schema

Flyway migration: `V24__student_success.sql`

### Table: student_progress_profile

One profile per student per school. Snapshot of support-relevant data refreshed on demand or via scheduled job.

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, UUIDv7 | Profile id |
| school_id | UUID | FK, NOT NULL, RLS | Owning school |
| student_id | UUID | FK, NOT NULL, unique | Student |
| overall_risk_level | varchar(20) | DEFAULT 'low' | low | moderate | high | critical (advisory only) |
| attendance_concern | boolean | DEFAULT false | Repeated absence/lateness flag |
| academic_decline | boolean | DEFAULT false | Declining results flag |
| incomplete_work | boolean | DEFAULT false | Missing assignments flag |
| last_reviewed_at | timestamp | | Last staff review |
| reviewed_by | UUID | FK | Membership who last reviewed |
| version | int | NOT NULL DEFAULT 0 | JPA optimistic lock |
| created_at / updated_at | | soft delete | TenantEntity columns |

### Table: risk_indicator

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, UUIDv7 | Indicator id |
| school_id | UUID | FK, NOT NULL, RLS | Owning school |
| student_id | UUID | FK, NOT NULL | Student |
| indicator_type | varchar(40) | NOT NULL | attendance_drop | grade_decline | missing_work | teacher_concern | guardian_concern |
| severity | varchar(20) | NOT NULL | low | moderate | high |
| description | varchar(500) | NOT NULL | Human-readable description |
| source_module | varchar(40) | | attendance | grades | assignments | teacher |
| source_entity_id | UUID | | Originating record id |
| acknowledged | boolean | NOT NULL DEFAULT false | Staff has reviewed |
| acknowledged_by | UUID | FK | Membership who acknowledged |
| acknowledged_at | timestamp | | Acknowledgement timestamp |
| resolved | boolean | NOT NULL DEFAULT false | Issue resolved |
| resolved_at | timestamp | | Resolution timestamp |
| created_at | timestamp | NOT NULL | TenantEntity column |

### Table: support_referral

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, UUIDv7 | Referral id |
| school_id | UUID | FK, NOT NULL, RLS | Owning school |
| student_id | UUID | FK, NOT NULL | Student |
| referred_by | UUID | FK, NOT NULL | Staff member making referral |
| referred_to | UUID | FK | Staff member receiving referral (counselor, dept head, etc.) |
| referral_type | varchar(40) | NOT NULL | academic | behavioral | health | family | learning_support |
| priority | varchar(20) | NOT NULL DEFAULT 'routine' | routine | urgent | immediate |
| reason | varchar(1000) | NOT NULL | Evidence-backed reason |
| supporting_evidence | jsonb | | References to attendance/grades/CBE records |
| status | varchar(20) | NOT NULL DEFAULT 'open' | open | in_progress | completed | declined |
| outcome_notes | text | | Resolution notes |
| created_at | timestamp | NOT NULL | TenantEntity column |
| resolved_at | timestamp | | Resolution timestamp |

### Table: intervention_plan

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, UUIDv7 | Plan id |
| school_id | UUID | FK, NOT NULL, RLS | Owning school |
| student_id | UUID | FK, NOT NULL | Student |
| referral_id | UUID | FK | Source referral |
| title | varchar(200) | NOT NULL | Plan title |
| description | text | NOT NULL | Plan description |
| start_date | date | NOT NULL | Plan start |
| review_date | date | NOT NULL | Scheduled review date |
| end_date | date | | Planned end |
| status | varchar(20) | NOT NULL DEFAULT 'active' | active | paused | completed | discontinued |
| coordinator_membership_id | UUID | FK, NOT NULL | Staff member coordinating |
| guardian_notified | boolean | NOT NULL DEFAULT false | Guardian informed |
| guardian_consent | boolean | DEFAULT null | Guardian consent (null = not sought) |
| created_at | timestamp | NOT NULL | TenantEntity column |

### Table: support_goal

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, UUIDv7 | Goal id |
| plan_id | UUID | FK, NOT NULL | Parent intervention plan |
| description | varchar(500) | NOT NULL | Goal statement |
| target_date | date | | Target completion |
| achieved | boolean | NOT NULL DEFAULT false | Goal achieved |
| achieved_at | timestamp | | Achievement timestamp |
| evidence_ref | varchar(500) | | Reference to supporting evidence |

### Table: progress_note

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PK, UUIDv7 | Note id |
| plan_id | UUID | FK, NOT NULL | Parent plan |
| student_id | UUID | FK, NOT NULL | Student |
| author_membership_id | UUID | FK, NOT NULL | Staff author |
| note_type | varchar(40) | NOT NULL | observation | review | outcome | guardian_communication |
| content | text | NOT NULL | Note content |
| is_confidential | boolean | NOT NULL DEFAULT false | Restricted to coordinator + leadership |
| created_at | timestamp | NOT NULL | TenantEntity column |

---

## 4. Permissions

Add to `PermissionCatalog`:

| Permission | Description | Roles |
|------------|-------------|-------|
| `student_success.view` | View student success profiles and indicators | principal, admin, dept_head, teacher, counselor |
| `student_success.manage` | Create/edit indicators, referrals, plans | principal, admin, dept_head, teacher |
| `student_success.intervene` | Create/modify intervention plans and goals | principal, admin, dept_head, counselor |
| `student_success.view.confidential` | View confidential progress notes | principal, admin, counselor |
| `student_success.refer` | Create support referrals | teacher, dept_head, principal, admin |

A `counselor` role code may be added to the role matrix (not currently in default matrix; schools can create custom role).

---

## 5. API Endpoints

Base path: `/api/v1/student-success`

| Method | Path | Permission | Description |
|--------|------|-----------|-------------|
| GET | `/profiles` | `student_success.view` | List student progress profiles (filtered by risk level) |
| GET | `/profiles/{studentId}` | `student_success.view` | Get single student profile |
| GET | `/indicators` | `student_success.view` | List risk indicators |
| POST | `/indicators` | `student_success.manage` | Create manual indicator (teacher concern) |
| PATCH | `/indicators/{id}/acknowledge` | `student_success.manage` | Acknowledge indicator |
| PATCH | `/indicators/{id}/resolve` | `student_success.manage` | Resolve indicator |
| GET | `/referrals` | `student_success.view` | List support referrals |
| POST | `/referrals` | `student_success.refer` | Create support referral |
| PATCH | `/referrals/{id}/status` | `student_success.manage` | Update referral status |
| GET | `/intervention-plans` | `student_success.view` | List intervention plans |
| POST | `/intervention-plans` | `student_success.intervene` | Create intervention plan |
| PATCH | `/intervention-plans/{id}` | `student_success.intervene` | Update plan |
| GET | `/intervention-plans/{id}/goals` | `student_success.view` | List plan goals |
| POST | `/intervention-plans/{id}/goals` | `student_success.intervene` | Add goal |
| PATCH | `/goals/{goalId}/achieve` | `student_success.intervene` | Mark goal achieved |
| GET | `/progress-notes` | `student_success.view.confidential` | List progress notes (filtered by confidentiality) |
| POST | `/progress-notes` | `student_success.manage` | Add progress note |
| GET | `/students/{studentId}/timeline` | `student_success.view` | Combined timeline of indicators, referrals, plans, notes |

---

## 6. Backend Services

### StudentSuccessService
- `getProfile(studentId, schoolId)` — returns profile with latest indicators and active plans
- `refreshProfile(studentId, actorId)` — recomputes risk level from grades, attendance, assignments

### RiskIndicatorService
- `autoGenerateIndicators(schoolId)` — scheduled job: scans grades (declining), attendance (repeated absence), assignments (missing work); creates advisory indicators only
- `acknowledgeIndicator(indicatorId, actorId)` — marks acknowledged
- Indicators never auto-escalate to interventions; staff must create referral manually

### InterventionService
- `createPlan(planDTO, actorId)` — validates guardian consent flag if plan involves external services
- `addGoal(planId, goalDTO, actorId)` — appends goal to plan
- `recordOutcome(planId, outcomeNotes, actorId)` — marks plan completed/discontinued

### Events
- `InterventionRecommendedEvent` — emitted when a plan is created; consumed by notifications to alert coordinator and guardian (if consented)

---

## 7. Flutter Frontend

### Screens (`app/lib/features/student_success/`)

**StudentSuccessDashboardScreen** (`dashboard_screen.dart`)
- Role-aware view: teachers see their students; leaders see school-wide risk summary
- Cards: students at risk count, open referrals, active interventions, pending reviews
- Filter by risk level, grade, stream

**StudentProfileScreen** (`student_profile_screen.dart`)
- Timeline view: indicators, referrals, intervention plans, progress notes in chronological order
- Risk level advisory badge (color-coded, with "advisory — staff review recommended" caption)
- "Add Indicator" / "Create Referral" / "Start Intervention Plan" action buttons (permission-gated)

**SupportReferralScreen** (`support_referral_screen.dart`)
- Form: student, referral type, priority, reason, supporting evidence (links to attendance/grade records)
- Assign to staff member
- Status tracking: open → in_progress → completed/declined

**InterventionPlanScreen** (`intervention_plan_screen.dart`)
- Plan details: title, description, dates, coordinator
- Goals list with achievement checkboxes
- Progress notes section with confidentiality toggle
- Guardian notification/consent status

**ProgressNoteScreen** (`progress_note_screen.dart`)
- Note entry: type (observation/review/outcome/guardian communication), content, confidential flag
- List of existing notes with author and date

### Providers (`student_success_providers.dart`)
- `studentProfilesProvider` — FutureProvider with risk-level filter
- `riskIndicatorsProvider` — StreamProvider for real-time indicator updates
- `activeInterventionsProvider` — FutureProvider for plans due for review
- `referralsProvider` — FutureProvider filtered by status

### Widgets
- `RiskLevelBadge` — color-coded advisory badge (never red-alarm without context)
- `TimelineEvent` — compact event row for student timeline
- `EvidenceChip` — links to source record (attendance session, grade entry)
- `ConfidentialityNotice` — banner when viewing confidential notes

---

## 8. User Journeys

### Journey 1: Teacher notices declining grades and refers a student
1. Teacher views gradebook, notices student's marks dropping over two terms
2. Navigates to StudentProfileScreen for the student
3. Clicks "Create Support Referral", selects type=academic, priority=routine
4. Enters evidence-backed reason; system suggests linking grade entries as supporting_evidence
5. Assigns to school counselor; submits
6. Counselor receives notification, opens referral, changes status to in_progress

### Journey 2: Counselor creates an intervention plan
1. Counselor opens approved referral, clicks "Create Intervention Plan"
2. Enters plan title, description, start/review dates, assigns coordinator
3. Adds goals: "Improve math attendance to 90%", "Complete 3 missing assignments"
4. Sets guardian_notified=true, guardian_consent=true (after guardian conversation)
5. Saves plan as active; coordinator and guardian notified

### Journey 3: Staff reviews progress at scheduled review date
1. Coordinator receives reminder (notification) that plan review is due
2. Opens InterventionPlanScreen, reviews goal achievements
3. Adds progress note: "Student attending regularly; 2 of 3 assignments submitted"
4. Marks one goal as achieved with evidence reference
5. Updates plan status or extends review date

---

## 9. Integration Points

- **Grades:** GradeEnteredEvent triggers risk indicator recalculation (grade_decline)
- **Attendance:** Attendance events trigger attendance_drop indicator
- **Assignments:** Missing submissions trigger incomplete_work indicator
- **CBE:** Competency gaps can feed learning_support referral type
- **Notifications:** Intervention plan creation notifies coordinator; guardian notified if consented
- **Audit Log:** Referral creation, plan changes, progress notes, and indicator acknowledgements are audited
- **People:** student_id references people.student; staff references tenants.membership

---

## 10. Non-Functional Requirements

| Concern | Requirement |
|---------|-------------|
| Tenant isolation | RLS on all tables via school_id; `@TenantScoped` services |
| No automatic labeling | Risk levels are advisory only; UI must include "staff review recommended" context; no automated high-impact decisions |
| Confidentiality | `is_confidential` notes restricted via `student_success.view.confidential` permission; RLS row-level filter |
| Health/sensitive data | Progress notes may contain sensitive observations; access strictly role-based; no health diagnosis fields (clinic module separate) |
| Offline sync | Referrals and notes support offline create (sync entity `support_referral`, `progress_note`); indicators are server-generated |
| Accessibility | Risk badges use color + text label; timeline keyboard-navigable; sufficient contrast for severity levels |
| Audit | All referral status changes, plan modifications, and note creations write audit_log with actor and before/after |
| Performance | Profile queries join indicators/plans/notes; paginated by student |
