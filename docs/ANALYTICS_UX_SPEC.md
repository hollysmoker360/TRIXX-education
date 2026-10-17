# Trixx Education — Educational Data Analysis Module Specification

Product specification · Flutter/Dart frontend · Java backend · Multi-tenant SaaS

Trixx Education should turn school data into clear, actionable insights that help school administrators, teachers, parents, and education leaders improve student performance, attendance, teaching quality, and school operations. The design should feel like a professional education-management platform, not a generic analytics dashboard, and integrate with Trixx Education's CBE curriculum, e-learning, examinations, student-success, attendance, and school-management modules.

## 1. Module overview

- **A. Executive analytics** — enrollment, attendance, academic performance, fee collection summaries where authorized, school comparisons, institution-wide trends. For school heads, directors, authorized administrators.
- **B. Academic performance intelligence** — examination results, competency mastery, subject performance, learning gaps, grade trends, assessment quality. For teachers, heads of department, academic coordinators.
- **C. Attendance and engagement intelligence** — daily attendance, absenteeism trends, lateness, e-learning participation, assignment completion, students who may need support. For administrators, teachers, authorized student-support staff.
- **D. Reports and decision support** — interactive reports, date and class filters, downloadable PDF/Excel/CSV, scheduled summaries, traceable recommendations. For school leadership, teachers, authorized education officers.

## 2. Navigation and information architecture

Dedicated Analytics section in the main navigation, modules shown based on the signed-in user's permissions:

Analytics overview · Academic performance · CBE competency mastery · Attendance & engagement · E-learning analytics · Student success & interventions · Class, subject & school comparisons · Reports & exports · Analytics configuration.

## 3. Screen 1 — Main analytics dashboard

Answers five questions immediately: How is the school performing? Is attendance improving or declining? Which classes and subjects need attention? Are students meeting their CBE competencies? What actions should staff take next?

KPIs: Enrolled learners, Attendance rate, Assessment average, Competencies on track. Charts: Academic performance trend (average score by term), Attendance by grade. Priority follow-ups: attendance below threshold, competency evidence incomplete, subject performance needs review. Dashboard interactions: clicking a KPI opens the relevant detailed report; selecting a class filters charts/tables/priority items consistently; selecting a chart point opens the underlying period's data; visible Last updated timestamp and data-quality indicator; save filters, export permitted results, drill down only as far as permissions allow.

## 4. Screen 2 — Academic performance analysis

Analyzes examination results and assessment evidence. Top filters: academic year, term, grade, class/stream, subject, assessment type, assessment status, comparison period. Visualizations: Performance trend, Score distribution, Subject comparison, Class comparison, Question analysis, Learner detail table. Switch between chart and table views. Rankings optional, permission-controlled, interpreted in context.

## 5. Screen 3 — CBE competency mastery

Curriculum selector (framework, grade, subject, strand, sub-strand, academic period). Mastery overview (meeting criteria, developing, not yet demonstrated, awaiting evidence). Competency heatmap (rows: competencies, columns: classes/learning groups). Evidence panel (assessments, rubric levels, projects, portfolios, observations, teacher feedback). Coverage report (planned vs assessed). Action panel (recommend additional practice, targeted teaching, reassessment). Mastery rules configurable; missing evidence displayed as "Not assessed" or "Insufficient evidence", not automatically as failure.

## 6. Screen 4 — Attendance and engagement

Daily, weekly, monthly, term attendance rates. Present/absent/late/excused/unrecorded counts. Attendance trends by grade, class, date. Repeated absence and lateness patterns. E-learning lesson completion, assignment submission, participation. Students requiring attendance review. Follow-up records and resolution status. Distinguish absent learner from unrecorded attendance.

## 7. Screen 5 — E-learning analytics

Course engagement (enrollment, lesson completion, active participation, completion rates by class/subject). Assignment analysis (submission rates, overdue work, resubmissions, marking turnaround, performance by assignment). Learning progress (progress over time, repeated attempts, feedback use, relationships between participation and assessment outcomes). Time spent in a lesson is only a rough engagement signal — not proof of attention, understanding, or learning.

## 8. Screen 6 — Student success and interventions

Identify learners who may benefit from support and track whether support is working. Each support record: student profile and authorized academic history; indicators (declining assessment results, repeated absence, incomplete work, unmet competencies); underlying records supporting each indicator; teacher observations and contextual information; support plan with goals, responsible staff, target dates, review dates; progress reviews and intervention outcomes. Workflow: identify pattern → review evidence → agree on support → review outcome. Avoid labeling students permanently; use neutral terms (Needs review, Support recommended, Review overdue). Automated indicators trigger human review, not disciplinary action.

## 9. Screen 7 — Class, subject, and school comparisons

Compare results fairly. Comparison: class-to-class (assessment average, participation, competency coverage), subject-to-subject (performance distribution, assessment completion, trends), term-to-term (changes in attendance, results, learning progress), school-to-school (aggregated performance, enrollment, attendance, data completeness), cohort over time (progress of same student group across periods). Account for cohort size, curriculum, grade, assessment difficulty, enrollment changes. Show denominator and reporting period alongside percentages. Multi-school comparisons only for authorized organization-level users; individual student records restricted to appropriate school and authorized roles.

## 10. Screen 8 — Report builder and exports

Generate custom reports without repeatedly asking developers. Report types: academic performance, CBE competency mastery, attendance and engagement, e-learning participation, student interventions. Reporting period: weekly, monthly, academic term, academic year, custom date range. Export format: PDF (presentation-ready), Excel (spreadsheet analysis), CSV (raw tabular data). Options: include charts and summaries; include student-level details if authorized. Requirements: save reusable templates, apply school/class/subject/date/grade/assessment filters, support PDF/Excel/CSV, include report title/filters/generated timestamp/data completeness/metric definitions, support scheduled reports and authorized delivery, track report generation/downloads/access to sensitive exports, exclude student-identifying information from aggregated reports unless specifically authorized.

## 11. Screen 9 — Analytics configuration and data quality

Administrative screen controlling how metrics are calculated: academic year/term/reporting-period configuration; school-specific attendance thresholds and assessment rules; versioned CBE mastery and rubric criteria; definitions for average, median, pass rate, completion, participation; data-quality checks for missing marks, duplicate records, missing attendance, inconsistent class assignments; data freshness indicators and background processing status; permissions for viewing/exporting/comparing analytics; audit history for changes to calculations and report settings. Metric definitions consistent across the platform.

## 12. Design system for Trixx Education Analytics

Calm, information-dense without being cluttered, usable by school staff who may not be data analysts.

**Color palette (semantic tokens):** Primary blue `#2457C5` (navigation, primary actions, selected filters); Teal green `#16836B` (positive trends, verified progress); Amber `#F2B544` (needs review, incomplete evidence); Red `#D94B55` (urgent exceptions, critical issues). Light and dark themes via semantic tokens; charts distinguishable in both themes, never color-only meaning.

**Typography and spacing:** Inter or clear readable sans-serif; page titles 24–28 px desktop scaled down for mobile; KPI values 26–32 px with consistent units/labels; body 14–16 px; table text 13–14 px with readable row spacing; card radius 10–14 px; 8 px base spacing; Lucide-style outline icons; subtle transitions with reduced-motion support.

**Responsive layouts:** Mobile under 600 px (single column, compact KPI cards, filter drawer, horizontally scrollable charts); Tablet 600–1023 px (two-column dashboard, collapsible navigation, condensed tables); Desktop 1024–1439 px (sidebar, top toolbar, two- or three-column analytics grid); Wide desktop 1440 px+ (expanded charts and data tables, sensible max content width). Do not simply shrink desktop; on mobile prioritize key indicators, quick filters, accessible chart summaries, drill-down screens.

## 13. Shared UI components

`AnalyticsAppScaffold`, `AnalyticsPageHeader`, `AnalyticsFilterBar`, `KpiCard`, `TrendChartCard`, `DistributionChartCard`, `AnalyticsDataTable`, `CompetencyHeatmap`, `InsightCard`, `DataFreshnessBadge`, `DataQualityBanner`, `ReportExportDialog`, `EmptyStatePanel`, `AnalyticsErrorPanel`, `PermissionBoundary`. Each with loading, empty, error, success states. Charts need accessible text summaries and should expose underlying data in a table.

## 14. Role-based analytics views

| User role | Default view | Access boundary |
|---|---|---|
| Platform administrator | Platform-wide operational analytics | Tenant-level aggregate data only where authorized; no unrestricted student data |
| Multi-school organization administrator | School comparison dashboard | Authorized schools in their organization |
| School administrator / principal | School-wide analytics | Assigned school and authorized records |
| Academic coordinator | Academic performance and curriculum coverage | Relevant grades, classes, subjects |
| Teacher | My classes and assigned subjects | Learners and records within teaching assignments |
| Parent / guardian | Individual learner progress | Only linked learner records |
| Student | Personal progress dashboard | Own learning records only |

Authorization based on actual permissions and assigned resources, not merely role label text.

## 15. Flutter/Dart frontend architecture

Feature-first structure under `lib/features/analytics/` with `presentation/pages`, `presentation/widgets`, `presentation/state`, `domain/entities`, `domain/repositories`, `domain/usecases`, `data/models`, `data/datasources`, `data/repositories`. Recommended: Flutter + Dart, Riverpod or Bloc, go_router, Dio, maintained charting library supporting tooltips/responsive rendering/accessible alternatives, shared Trixx Education design tokens for light/dark themes. Keep calculations affecting official reports or educational decisions on the backend; frontend handles display formatting, sorting, temporary filters, chart presentation.

## 16. Java backend architecture

Modular Java backend (Spring Boot) reading from existing Trixx Education modules rather than duplicating student, assessment, attendance records. Package: `analytics/` with `api/` (AnalyticsController, ReportController, InterventionAnalyticsController), `application/` (DashboardService, AcademicAnalyticsService, CompetencyAnalyticsService, AttendanceAnalyticsService, ReportGenerationService), `domain/` (AnalyticsMetric, MetricDefinition, Insight, ReportDefinition), `infrastructure/` (repository, query, export, jobs), `security/` (AnalyticsAuthorizationService).

Data flow: Source modules (Students, CBE, Exams, Attendance, E-learning) → Analytics query and calculation layer (validated records, metric definitions, tenant authorization) → Aggregates and background processing (scheduled summaries, cache, data-quality checks) → Secure analytics API (dashboard responses, drill-down, reports, exports) → Flutter analytics interface (KPIs, charts, tables, insights, report builder).

Start with optimized SQL queries and indexed PostgreSQL tables. Introduce precomputed aggregates when query volume or data size justifies. Redis can cache suitable dashboard results, with cache keys including authorized tenant and relevant filters.

## 17. API endpoints

All under `/api/v1/analytics`, using existing Trixx Education authentication and tenant-resolution.

| Endpoint | Purpose |
|---|---|
| GET /overview | Dashboard KPI summaries |
| GET /academic-performance | Assessment trends and distributions |
| GET /competency-mastery | CBE mastery and evidence coverage |
| GET /attendance | Attendance trends and exceptions |
| GET /elearning | Course and assignment engagement |
| GET /student-success | Support indicators and interventions |
| GET /comparisons | Authorized class and school comparisons |
| GET /data-quality | Missing or inconsistent records |
| GET /metrics/definitions | Explain how metrics are calculated |
| POST /reports | Create a report definition or request |
| GET /reports/{reportId} | Check report status and metadata |
| GET /reports/{reportId}/download | Download an authorized generated report |

Example dashboard response includes period, metrics (enrolledLearners, attendanceRate, assessmentAverage, competencyOnTrackRate), dataQuality (status, missingAttendanceRecords), generatedAt. Production responses should also include metric definitions, denominators, data coverage, source freshness.

## 18. Define the calculations before building charts

Metric definitions must be consistent. Examples: Attendance rate = Present eligible sessions ÷ recorded eligible attendance sessions (following school policy). Assessment average = Mean of valid eligible scores, with weighting explicitly configured if applicable. Assessment participation = Learners with valid submissions ÷ learners expected to participate. Competency mastery rate = Competencies meeting configured mastery criteria ÷ competencies eligible for evaluation. Course completion = Learners meeting course completion rules ÷ learners enrolled and eligible. Intervention improvement = Change in defined outcome from baseline to review period. Always display numerator, denominator, date range, exclusions where they materially affect interpretation. Do not combine incompatible assessment scales or compare unmoderated results as equivalent.

## 19. Security, privacy, and reliability

Tenant isolation: every query, aggregate, export, cache entry respects authorized tenant and school scope. Permission checks enforced on backend for both dashboards and underlying records. Data minimization: prefer aggregates unless individual-level data required for authorized workflow. Audit logs: record access to sensitive reports, exports, metric configuration changes, intervention records. Secure exports: short-lived download links, access checks, expiry, controlled storage. Data quality: show stale or incomplete data rather than presenting as fully current. Explainability: provide underlying evidence and calculation behind an insight. Fairness: do not infer ability or make consequential decisions from attendance, device activity, or single assessment alone. Accessibility: keyboard navigation, readable contrast, screen readers, chart data tables.

## 20. Implementation roadmap

1. Phase 1 — Analytics foundation: design tokens, navigation, permission rules, metric definitions, filters, API contracts, dashboard shell.
2. Phase 2 — Core academic dashboards: overview KPIs, assessment trends, subject analysis, attendance reporting, class drill-down.
3. Phase 3 — CBE and e-learning intelligence: competency heatmaps, evidence coverage, course engagement, assignment analytics, learner progress.
4. Phase 4 — Student success and reports: support workflows, configurable indicators, intervention tracking, report builder, secure exports.
5. Phase 5 — Optimization and release: aggregates, caching, load tests, tenant-isolation tests, accessibility checks, audit review, production monitoring.

Recommended first release: five high-value screens — Analytics Overview, Academic Performance, CBE Competency Mastery, Attendance & Engagement, Report Builder.

## 21. UI/UX improvements for the existing Trixx Education app

The current Trixx Education interface already has a good foundation: clear sidebar, recognizable icons, top search bar, welcome banner, summary cards. The biggest opportunity is to make the dashboard more informative, actionable, easier to navigate, and visually consistent across every module.

**Improve first:**
1. Make the dashboard more useful — replace empty space with today's timetable, upcoming lessons, attendance reminders, pending marking, upcoming deadlines, recent activity. Show only information relevant to the signed-in user.
2. Organize the sidebar — group features under Home, Teaching & Learning, School Operations, Communication, Administration.
3. Turn Reports into real analytics — academic trends, competency mastery, attendance patterns, assignment completion, class comparisons. Each chart opens the underlying report.
4. Standardize all screens — consistent spacing, typography, buttons, page headers, filters, tables, loading states, empty states. Support desktop, tablet, mobile, light and dark.

**Detailed screen improvements:**
- Today page: personalized workspace with today's timetable, attendance registers requiring completion, marking/assignment deadlines, upcoming events, unread messages, quick actions by permission, recently accessed classes/students/courses. Make widgets configurable, role-aware. A student sees assignments and learning progress; a teacher sees lessons and marking; a principal sees school-wide priorities.
- Overview: school-wide dashboard with student/staff totals, enrollment by grade/class/academic year, attendance trends and exceptions, assessment performance summaries, CBE competency coverage, pending administrative activities, recent changes and announcements. Separate operational from academic metrics. Include date filters, data freshness, chart drill-down, role-specific layouts.
- Timetable: daily/weekly/term views, teacher/class/subject/room filters, lesson duration and break configuration, teacher and room conflict detection, substitution and change requests, exam schedules and special events, calendar export and schedule notifications. Drag-and-drop editor for authorized administrators with conflict warnings. Notify affected users when changes published. Retain change history.
- Attendance: class and lesson-based registers, present/absent/late/excused statuses, attendance by student/lesson/day/term, absence explanations and notes, authorized corrections and approval workflow, summaries and trend reports, guardian notifications and follow-up records, offline register capture with safe synchronization. Explicit register status: Not started, In progress, Submitted, Amended. Prevent duplicate submissions, preserve correction history, distinguish missing attendance records from confirmed absences.
- Marks: create assessments and set maximum marks; enter marks individually or in bulk; import marks from validated spreadsheets; configure rubrics and CBE assessment criteria; add comments and learner feedback; track missing submissions and marking progress; moderate and approve results; publish results to authorized students and guardians; analyze class averages and question-level performance. Autosave, validation, draft vs published states, missing-mark indicators, moderation workflows, immutable audit history for published results. Configurable grading rules rather than hard-coded grading bands.
- Tasks: personal and assigned tasks, descriptions, priorities, deadlines, assignment marking tasks, administrative approvals and follow-ups, recurring tasks and reminders, task comments/attachments/status history, completion reports and overdue lists. Separate personal tasks from student assignments. Statuses: To do, In progress, Waiting, Completed. Auto-generate tasks from workflows (unsubmitted register, pending moderation).
- Academics: academic years/terms/grades/streams, subjects and teacher assignments, curriculum frameworks and learning outcomes, CBE strands/sub-strands/competencies, lesson and assessment planning, rubrics, learning plans, evidence mapping, curriculum coverage monitoring, promotion and progression workflows. Structured curriculum hierarchy, versioned frameworks, configurable academic calendars. Connect each assessment to relevant learning outcomes so competency analysis reuses actual evidence.
- Reports: academic performance and grade distributions, attendance and engagement analysis, CBE competency mastery and curriculum coverage, enrollment and school operations, e-learning completion and participation, student support and intervention tracking, class and subject comparisons, PDF/Excel/CSV exports. Reusable filters, saved report templates, metric definitions, data-quality indicators, controlled drill-down to individual records. Clear distinction between observed results and predictive insights.
- Learning Hub: searchable repository of educational content (textbooks, documents, videos, presentations, links), curriculum and subject categorization, search/filters/tags/bookmarks, teacher-created and school-approved resources, upload and approval workflows, accessibility information and file previews, offline access where supported and permitted. Link resources to grades, subjects, competencies, lessons. Show content format, file size, language, attribution. Enforce access permissions and copyright restrictions.
- Lessons: lesson objectives and competencies, activities and teaching resources, lesson sequence and duration, attachments/presentations/embedded content, formative assessment activities, lesson publication and student access, teacher reflection and post-lesson notes, lesson reuse and version history. Structured lesson editor with autosave, draft previews, templates, curriculum mapping. Connect lessons to timetable and Learning Hub; attach evidence of competency development to relevant learning outcomes.
- Messages: direct and group conversations, teacher-guardian communication, class and school announcements, attachments and message search, read status and notification preferences, moderation and reporting tools, message retention and administrative policies. Distinguish announcements from private conversations. Restrict communication per school policy, protect student information, avoid exposing personal contact details to unauthorized users.
- People: student profiles and enrollment, staff profiles/roles/teaching assignments, guardian relationships and permissions, class and group membership, profile import and validation, enrollment and transfer history, account activation and access management, controlled student record access. Profile tabs for Overview, Academics, Attendance, Assessments, CBE Progress, Guardians, History. Bulk import with validation reports and duplicate detection. Keep sensitive personal records behind explicit permissions.
- Notifications: attendance and marking reminders, timetable changes, assessment deadlines and published results, assignment and lesson updates, school announcements, message notifications, read/unread status and notification preferences. Group related alerts, prioritize urgent items, mark as read, configurable email/in-app/other supported delivery channels. Avoid duplicate notifications when background jobs retry.
- Audit Log: user sign-in and security events, changes to student and staff records, mark edits/result publication/moderation, permission and role changes, data imports/exports/sensitive report access, timetable and curriculum changes, actor/timestamp/affected record/outcome, search/filtering/controlled export. Append-only audit records with restricted access. Capture changed fields and before/after values without logging passwords, tokens, or unnecessary sensitive information. Ordinary administrators cannot silently edit or delete audit events.
- Sync: online/offline connection state, last successful synchronization time, pending uploads/downloads, failed operations with understandable errors, retry and conflict resolution, sync history and operation status, device-specific queue information where relevant. Clear statuses: Up to date, Syncing, Pending, Failed, Conflict. Retry with backoff, idempotent backend operations, visible conflict resolution. Never show "Synced" merely because an item was queued locally; confirm server acceptance.

**Global improvements across the entire application:**

| Area | Recommended change |
|---|---|
| Navigation | Group links, add collapse/expand, preserve active section, accessible labels |
| Top bar | Global search, school switcher when authorized, academic period selector, notifications, profile menu |
| Forms | Clear validation, save state, unsaved-change warnings, helpful error messages |
| Tables | Search, filters, sorting, pagination, column configuration, responsive fallback |
| Feedback | Toast messages, confirmation dialogs, loading skeletons, empty states, retry actions |
| Accessibility | Keyboard navigation, focus indicators, contrast, screen-reader labels |
| Responsiveness | Sidebar drawer on mobile, adaptive cards, usable tables and charts |
| Themes | Complete light/dark support through shared design tokens |
| Security | Server-side authorization, tenant isolation, audit logging, safe file access |
| Performance | Pagination, efficient queries, caching where safe, background report generation |

**Technical direction:** Flutter feature-first modules, reusable components, responsive layouts, centralized theme, Riverpod or Bloc. Java Spring Boot controllers/services, validation, authorization, consistent API responses, database transactions. Preserve relationships among schools, classes, students, attendance records, assessments, curriculum outcomes. Calculate official metrics on the backend via dedicated analytics endpoints. Offline: persist only data necessary for authorized offline workflows, synchronize through controlled queue. Testing: widget tests, API integration tests, tenant-isolation tests, responsive-layout checks. Avoid a full rewrite; incrementally replace inconsistent layouts while preserving working functionality.

**Recommended implementation order:**
1. First: UI foundation — sidebar, top bar, typography, colors, responsive breakpoints, shared buttons, cards, tables, light/dark themes.
2. Second: Daily workflows — Today, Timetable, Attendance, Marks, Tasks.
3. Third: Academic intelligence — Academics, Lessons, Learning Hub, Reports, including CBE competency tracking and assessment analysis.
4. Fourth: Communication and administration — Messages, People, Notifications, Audit Log, Sync, with special attention to security and reliability.
5. Fifth: Advanced capabilities — student-success interventions, advanced analytics, report scheduling, additional integrations once core workflows are stable.

My main recommendation: keep the clean visual foundation already in place, but transform the interface from a collection of navigation links and summary cards into a role-aware workspace where every page answers three questions: What is happening? What needs my attention? What can I do next?

## Cross-platform support

Trixx Education runs on Android, Web, Windows, and Linux. iOS and macOS require a macOS host for building (scaffolding can be added via `flutter create --platforms=ios,macos` on a Mac). The responsive design system (window classes, adaptive shell, light/dark themes) ensures consistent behavior across all form factors.

*Internal code identifiers (Java packages `com.schoolsphere`, database schemas, service names) are intentionally retained per ADR-012; only the user-facing product name is "Trixx Education".*
