# Trixx Education — Complete UI/UX Design Specification

Product design blueprint · Flutter/Dart frontend · Java backend · Hybrid SaaS platform

Trixx Education should feel like a mature enterprise education platform: professional enough for school administrators, efficient enough for teachers, approachable enough for students and parents, and powerful enough for a SaaS operator managing hundreds of schools.

The interface should prioritize clarity, speed, accessibility, secure school separation, and consistent behavior across desktop, tablet, and mobile. Light and dark themes must both be fully designed rather than treating dark mode as a simple color inversion.

## 1. Product design principles

### Role-first experiences

Every user sees relevant information and actions. A teacher lands on today's classes, a parent sees their children's updates, and an administrator sees school-wide operations.

### One design system, multiple layouts

Flutter provides shared components and business workflows, while layouts adapt to the available screen space instead of shrinking a desktop screen to fit a phone.

### Trustworthy and auditable

Sensitive records, payments, marks, attendance corrections, and permissions have explicit confirmation, traceable changes, and understandable status indicators.

### Reliable under poor connectivity

Selected workflows support offline drafts, cached read-only data, pending synchronization, and clear conflict resolution.

## 2. Design tokens

These tokens are the authoritative starting point for the Flutter theme and component library.

### 2.1 Color palette

**Core brand colors**

- Navy — `#172B4D`
- Teal — `#0F9F91`
- Canvas — `#F5F7FB`
- Slate — `#64748B`

| Semantic token | Light theme | Dark theme | Purpose |
|---|---|---|---|
| Primary | `#172B4D` | `#C4D5F5` | Primary emphasis, navigation |
| Action | `#087F73` | `#52D4C2` | Buttons, links, active controls |
| Background | `#F5F7FB` | `#101722` | Main application background |
| Surface | `#FFFFFF` | `#192433` | Cards, forms, menus |
| Elevated surface | `#FFFFFF` | `#222F40` | Dialogs, popovers |
| Text primary | `#182230` | `#F1F5F9` | Main content |
| Text secondary | `#64748B` | `#B0BDCC` | Descriptions, metadata |
| Border | `#DCE3EC` | `#354356` | Dividers, outlines |
| Success | `#18794E` | `#65D6A0` | Completed, paid |
| Warning | `#946200` | `#F3C66A` | Pending, attention |
| Error | `#B42318` | `#FF8A80` | Failed, rejected |
| Information | `#2459A9` | `#8CB7FF` | Informational notices |

Use the darker teal for small text and controls on light surfaces to preserve contrast. The brighter brand teal can be used for larger filled accents where contrast is sufficient.

### 2.2 Typography

Use Inter as the primary font, with a bundled font asset for predictable cross-platform rendering. A system sans-serif fallback is acceptable if font loading fails.

| Style | Size | Weight | Typical use |
|---|---|---|---|
| Display | 32 px | 700 | Major landing headings |
| Page title | 26 px | 700 | Main page heading |
| Section title | 20 px | 600 | Card or section heading |
| Subheading | 16 px | 600 | Form and panel headings |
| Body | 14 px | 400 | Default content |
| Label | 13 px | 500 | Fields and table headings |
| Caption | 12 px | 400 | Timestamps, metadata |
| Button | 14 px | 600 | Actions |

Use a 1.4–1.5 line-height for body text. Support system text scaling; do not enforce fixed-height text containers that truncate enlarged text.

### 2.3 Spacing, shapes, and elevation

Use a 4 px base spacing scale.

| Token | Value | Application |
|---|---|---|
| space-1 | 4 px | Icon/text separation |
| space-2 | 8 px | Compact control spacing |
| space-3 | 12 px | Form and card internals |
| space-4 | 16 px | Default component spacing |
| space-6 | 24 px | Section spacing |
| space-8 | 32 px | Major page separation |
| space-12 | 48 px | Large page sections |
| radius-sm | 6 px | Chips and compact fields |
| radius-md | 10 px | Inputs and buttons |
| radius-lg | 14 px | Cards and panels |
| radius-xl | 20 px | Major containers and dialogs |

Prefer subtle one-pixel borders over heavy shadows. Use elevation sparingly for menus, floating controls, and modal dialogs.

## 3. Responsive layout architecture

The layout should be driven by available window width, not device labels. A tablet in landscape may have enough space for a sidebar, while a tablet in portrait may need a drawer.

- **Desktop — 1200 px and above**: persistent 248 px navigation sidebar, top header, main content area, multi-column dashboards, advanced data tables, side panels, and keyboard-friendly workflows.
- **Tablet — 768–1199 px**: collapsible navigation, compact header, two-column content where useful, simplified table columns, responsive filters, and touch-friendly forms.
- **Mobile — below 768 px**: single-column pages, compact top bar, bottom navigation for primary tasks, full-screen forms where appropriate, list-based data views, and sticky contextual actions.

### Layout behavior by screen width

| Width | Layout rules |
|---|---|
| Under 360 px | Essential content only; avoid horizontal overflow |
| 360–599 px | Single column, bottom navigation, compact cards |
| 600–767 px | Single column or selected two-column card grids |
| 768–1023 px | Tablet drawer or rail, responsive tables |
| 1024–1199 px | Collapsible sidebar, compact multi-column content |
| 1200–1599 px | Full sidebar, standard dashboard layout |
| 1600 px and above | Wider data views with capped content width where appropriate |

These are initial breakpoints. Test actual Flutter window sizes and accessibility text scaling before finalizing them.

### Desktop shell

Persistent sidebar with destinations (Overview, Students, Attendance, Learning, Finance, Reports, Settings) plus a top header (page title, search, notifications, profile) and a main content area. A dashboard example shows metric cards (Students 1,248; Attendance 94.8%), recent activity, and a conceptual layout preview.

### Responsive navigation rules

- Desktop: expanded sidebar with icons and labels; allow collapse to an icon rail.
- Tablet: collapsible drawer or navigation rail, depending on available width.
- Mobile: show the four or five most relevant destinations in bottom navigation; put secondary destinations in a "More" screen.
- Preserve the current page and filters when navigating between sections.
- Show breadcrumbs on desktop for nested administrative pages; use a back button and concise page title on mobile.
- Hide navigation items the user's role cannot access, but always enforce permissions on the backend too.

## 4. Navigation architecture

The navigation must change by role. Do not expose every module to every user.

### Platform super-administrator

Platform overview, Schools and onboarding, Subscriptions and billing, Plans and entitlements, Platform analytics, Support and incidents, Integrations, Security and audit logs, System configuration.

### School administrator

Overview, Admissions and students, Staff and HR, Classes and subjects, Attendance, Timetable, Examinations and results, Learning management, Fees and finance, Parent communication, Library and inventory, Transport, Reports and analytics, School settings.

### Teacher

My dashboard, My classes, Attendance, Marks and assessments, Lesson plans and materials, Assignments and quizzes, Student progress, Timetable, Messages and announcements.

### Student

My learning dashboard, Subjects and lessons, Assignments, Quizzes and examinations, Grades and feedback, Timetable, Learning resources, Notifications.

### Parent or guardian

Family dashboard, Children and school profiles, Attendance, Academic results, Fee statements and payments, Announcements, Teacher communication, Meeting requests.

### Accountant, librarian, HR officer, and transport manager

Provide dedicated role-specific workspaces with only the tools needed for their responsibilities. School administrators may grant additional permissions through explicit role and permission management.

## 5. Page-by-page screen specification

The following is the full product screen inventory. Each screen should have loading, empty, error, success, and permission-denied states where relevant.

### A. Authentication and onboarding

| Screen | UI and behavior |
|---|---|
| Welcome | Branding, sign-in, account recovery, and organization selection where appropriate |
| Sign in | Email or configured identifier, password, password visibility, validation, optional MFA |
| Password recovery | Secure reset request and confirmation without exposing whether an account exists |
| MFA verification | Authenticator or approved verification method, recovery codes, retry limits |
| School selection | Schools associated with the user's authorized membership; never allow arbitrary tenant access |
| Invitation acceptance | Validate invitation, establish credentials, accept school membership |
| First-time setup | Profile, timezone, locale, notification preferences, permitted setup tasks |
| School onboarding | School profile, academic year, grading system, classes, staff invitations, configuration checklist |
| Subscription setup | Plan comparison, checkout, billing status, payment provider handoff |
| Session expiry | Explain expired session and offer secure reauthentication |

Design note: authentication should be calm and uncluttered, with one primary action per step.

### B. Dashboards

| Screen | UI and behavior |
|---|---|
| School overview | Student count, attendance, collections, upcoming events, pending actions |
| Teacher overview | Today's timetable, classes, attendance completion, marking tasks |
| Student overview | Today's lessons, due assignments, upcoming assessments, recent feedback |
| Parent overview | Child switcher, attendance, results, fee balance, announcements |
| Finance overview | Collections, outstanding balances, reconciliation, payment exceptions |
| Academic overview | Grade distribution, subject performance, assessment completion |
| Platform overview | Tenant count, subscriptions, service health, support queue, platform usage |

Dashboard cards should be clickable only when they lead to a useful, authorized detail view.

### C. Student and admissions management

Student directory, Add student, Student profile, Edit student, Admission applications, Enrollment, Guardian management, Transfers and exits, Student documents, Bulk import. Sensitive information should not appear on list cards by default.

### D. Staff and human resources

Staff directory, Staff profile, Add/edit staff, Teacher assignment, Leave management, Payroll records, Staff attendance, Performance reviews, Staff onboarding. Payroll and employment information require dedicated permissions and audit logging.

### E. Classes, subjects, timetable, and calendar

Class directory, Class profile, Subject management, Academic year and terms, Timetable builder, Timetable conflict view, Personal timetable, School calendar, Event details. For mobile, convert complex weekly grids to a day-by-day agenda.

### F. Attendance (including optional camera-assisted)

Attendance overview, Take attendance, Period attendance, Student attendance history, Absence management, Attendance reports, Correction requests, Device/camera configuration, Camera review queue, Sync queue. Never present a camera-based face match as unquestionable proof of attendance.

### G. Learning management system

Course and subject overview, Lesson editor, Lesson viewer, Resource library, Assignment list, Assignment creation, Assignment submission, Quiz builder, Quiz taking, Exam interface, Marking workspace, Discussion forum, Learning progress. Students see only their own permitted results.

### H. Examinations and assessment

Assessment setup, Exam timetable, Marks entry, Gradebook, Moderation, Result publication, Report card preview, Report card export, Academic history. Use explicit drafts and publication states.

### I. Finance and payments

Finance dashboard, Fee structures, Student fee statement, Invoice details, Payment checkout, M-Pesa payment flow, Payment history, Reconciliation, Discounts and scholarships, Installment plans, Expense management, Financial reports, Subscription billing. The interface must distinguish initiated, pending, confirmed, failed, and refunded transactions; a client-side callback must never be treated as authoritative payment confirmation.

### J. Communication and notifications

Message inbox, Conversation view, Compose announcement, Class announcements, Notification centre, Notification preferences, Email/SMS delivery log, Meeting requests, Emergency communication. Avoid duplicate alerts for the same event.

### K. Library, inventory, and transport

Library, Inventory, Procurement, Transport, Bus attendance, Transport reports. GPS tracking should be an optional transport capability with explicit permissions.

### L. AI, analytics, and reporting

AI assistant, Quiz generator, Study planner, Academic insights, At-risk student review, Attendance analytics, Financial analytics, Report builder, Scheduled reports, Audit explorer. AI-generated recommendations must be labelled as recommendations.

### M. Platform administration and configuration

School directory, Tenant detail, Subscription plans, Billing operations, Platform usage, Integration centre, Integration health, Role and permission editor, Audit logs, Backup and recovery, System health, Support centre. Cross-tenant support access should be explicitly authorized and auditable.

## 6. Reusable Flutter component library

Build the application from a shared set of components. Each component should inherit the active theme, respect text scaling, and expose clear loading and disabled states.

### 6.1 Core components

- **Buttons**: primary, secondary, outline, text, destructive, icon-only; loading, disabled, hover, focus, pressed states.
- **Status indicators**: Paid, Pending, Failed, In review, Draft — always pair color with readable text or an icon.
- **Form controls**: labels, required indicators, inline validation, helpful descriptions, explicit saved/unsaved state.

### 6.2 Required shared widgets

`AppShell`, `AppSidebar`, `AppTopBar`, `MobileNavigation`, `PageHeader`, `SectionCard`, `MetricCard`, `AppButton`, `AppTextField`, `AppSelectField`, `DateRangePicker`, `FilterBar`, `DataTable`, `ResponsiveDataList`, `StatusChip`, `ConfirmDialog`, `AppDrawer`, `EmptyState`, `ErrorState`, `LoadingSkeleton`, `NotificationBanner`, `OfflineStatusBanner`, `SyncQueueIndicator`, `FileUploadField`, `PermissionGate`, `AccessibleChart`.

### 6.3 Data table behavior

Search with a visible clear action; server-side sorting, filtering, and pagination; column configuration; select-all scoped to the current result set; bulk actions with preview and confirmation; export controls respecting permissions and filters; loading/empty/error/retry states; responsive conversion to stacked records on small screens; keyboard-accessible sorting and row actions. Avoid loading an entire school's student population into Flutter just to paginate locally.

## 7. Flutter project structure

Use a feature-oriented structure with separation between presentation, application logic, domain models, and data access.

```
lib/
├── main.dart
├── app/
├── core/
└── features/
```

Each substantial feature follows a consistent pattern: `presentation/`, `application/`, `domain/`, `data/`. Use dependency injection and a consistent state-management solution (Riverpod or Bloc). Use a declarative router (go_router) if it suits the chosen application structure.

### 7.1 Theme implementation

A starting point for the Dart design tokens is provided (colors, spacing, radius, light/dark `ThemeData`). Complete the button, text-field, menu, dialog, navigation, tooltip, focus, and chart themes as part of implementation. Verify the Flutter SDK version before choosing newer theme APIs.

### 7.2 Responsive shell example

A `ResponsiveShell` widget illustrates the layout decision: sidebar on desktop, drawer on tablet, bottom navigation on mobile. In production, implement drawer opening from the app bar, tablet navigation rail behavior, route preservation, mobile keyboard handling, and navigation visibility according to role and permissions.

## 8. Java backend and frontend integration contract

The UI should never contain business rules that the backend is responsible for enforcing. Flutter presents the workflow; Java validates and executes it.

- API version: `/api/v1/...`
- Authentication: secure session or short-lived access tokens with refresh and revocation controls
- Authorization: backend-enforced permissions for every protected operation
- Tenant isolation: derive tenant access from authenticated membership; validate school scope on every request
- Pagination: cursor or page-based pagination with explicit metadata
- Filtering: documented query parameters and validated filter values
- Validation errors: structured field-level errors and a readable summary
- Server errors: stable error codes, correlation IDs, no sensitive stack traces
- Mutations: clear success responses and conflict handling
- Payment state: server-verified provider status
- Offline synchronization: idempotency keys, version checks, conflict resolution
- Dates: ISO 8601 timestamps, explicit timezone handling
- Exports: permission-checked server jobs for large reports

Example endpoints: `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/me`, `/api/v1/schools/current`, `/api/v1/students`, `/api/v1/classes`, `/api/v1/attendance/sessions`, `/api/v1/assessments`, `/api/v1/finance/invoices`, `/api/v1/payments`, `/api/v1/notifications`, `/api/v1/reports`, `/api/v1/admin/schools`, `/api/v1/admin/subscriptions`.

### UI state conventions

Every API-connected screen should explicitly handle: Initial (skeleton), Success, Empty, Validation error, Unauthorized, Network error, Offline, Conflict, Stale data, and Success after mutation.

## 9. Offline-first and synchronization experience

SchoolSphere's offline UI should be designed into the core product, especially for attendance, learning materials, and permitted draft work. States: Online and synchronized, Offline with saved drafts, Synchronizing, Conflict or failure. Implement a durable local queue, idempotent server operations, record versioning, retry policies, and explicit conflict resolution. Do not cache sensitive student records indiscriminately.

## 10. Accessibility standards

Target WCAG 2.2 Level AA. Requirements: 4.5:1 contrast for normal text (3:1 large text); 44×44 logical pixel touch targets; screen-reader labels, roles, values, reading order; full keyboard navigation with visible focus; text scaling without clipping; never convey status by color alone; persistent labels with specific validation; accessible table headers; chart text summaries and table alternatives; respect reduced-motion; focus management after dialogs/navigation/validation failures; localization-ready.

## 11. Security and privacy requirements for the UI

Hide actions users cannot perform but enforce every permission on the Java backend. Explicit confirmation for publishing results, deleting records, issuing refunds, changing permissions. Show school context clearly when users can access multiple schools. Mask sensitive identifiers and payment information. Avoid exposing sensitive student data in notification previews, logs, URLs, or cached screens. Use secure storage for credentials and tokens. Apply session timeouts and reauthentication to sensitive operations. Audit changes to grades, attendance, payment status, user roles, student records. Provide retention, export, and deletion workflows. Minimize biometric collection and provide alternative workflows.

For a Kenyan deployment, include a privacy and data-protection review against applicable requirements, including the Data Protection Act, 2019. Technical controls and user consent alone do not replace a proper legal assessment.

## 12. Design handoff and implementation checklist

Use as the acceptance gate: design system tokens defined and tested (light/dark); responsive behavior verified across desktop/tablet/mobile; role-specific workflows implemented; backend and offline behavior handled; accessibility and keyboard navigation tested; automated widget and integration tests written; sensitive data exposure and audit flows reviewed.

## 13. Recommended implementation sequence

1. Foundation: design tokens, theme switching, responsive shell, routing, authentication, permission-aware navigation.
2. Core school operations: dashboard, student profiles, staff, classes, attendance, timetable.
3. Academics: assignments, assessments, marks, gradebook, report cards.
4. Finance: fee statements, invoices, payment integration, reconciliation.
5. Communication: notifications, announcements, parent and teacher messaging.
6. Additional modules: library, HR, inventory, transport, subscriptions.
7. Advanced features: AI-assisted learning, analytics, camera-assisted attendance, offline synchronization.
8. Hardening: accessibility, security, performance, regression testing, responsive QA.

Each phase should include working API integration and tests before moving to the next. Keep advanced features behind explicit feature flags.

Final design direction: use navy for structure, teal for purposeful actions, white and slate surfaces for readability, and consistent typography and spacing across the product. The result should feel like one cohesive enterprise platform while adapting its information density and navigation to the role, device, and task at hand.

---

## Advanced Features Roadmap

Trixx Education can go far beyond a traditional school management system. Since it is built with Flutter/Dart and Java as a hybrid multi-school SaaS platform, the next step is to introduce features that make it an intelligent school operating system: one platform connecting academic operations, finance, communication, student wellbeing, automation, and institutional analytics.

### 1. Advanced AI features

- **AI school intelligence assistant** — natural-language questions over permission-filtered school data, linking back to relevant reports.
- **Personalized learning engine** — recommend revision topics, exercises, resources based on demonstrated mastery, curriculum objectives, teacher feedback.
- **Teacher productivity copilot** — draft lesson plans, differentiated exercises, marking rubrics, report comments, quizzes, parent communications; teachers review and approve before publication.
- **Predictive school planning** — forecast enrollment, resource demand, fee collection, attendance trends with assumptions, uncertainty, supporting historical data.

### 2. Student experience and wellbeing

- Student goal tracker
- Achievements and rewards (configurable badges; avoid public rankings that shame struggling learners)
- Student wellbeing and pastoral care (strict confidentiality, limited access)
- Career and pathway planner
- Peer tutoring and study groups

### 3. Features tailored for Kenyan schools

- Competency-Based Education (CBE) portfolio — track competencies, learning outcomes, rubrics, learner projects, teacher observations, evidence uploads, progress across terms. Configurable assessment frameworks.
- Education reporting and data exchange — configurable school returns, validate learner identifiers, controlled exports for authorized education reporting systems. Add official integrations only when relevant APIs, formats, and permissions are confirmed.
- English and Kiswahili localization — interface labels, notification templates, report cards, parent messages; respect user language preference and local date/number/currency formats.
- Advanced payment reconciliation — match M-Pesa transactions against invoices, detect duplicate references, flag unmatched payments, allocate partial payments, traceable correction workflow.

### 4. Operational features schools often overlook

- Boarding and dormitory management (dormitory/bed allocation, occupancy, warden assignments, boarding attendance, meal plans, visitor records, maintenance requests)
- School canteen and meal management (meal plans, dietary requirements, meal attendance, stock consumption, canteen sales, cash reconciliation, food inventory, supplier tracking)
- School clinic management (restricted medical visit records, authorized emergency contact access, appointment/referral tracking, configurable medication administration records)
- Facilities and maintenance (classrooms/labs/equipment registers, maintenance tickets, inspection schedules, safety checklists, repair budgets, asset lifecycle)

### 5. Automation engine

Visual workflow builder: triggers, conditions, actions. Example: automated absence follow-up (trigger: teacher records absence; condition: unexcused + verified guardian; actions: notify guardian, create staff task). Additional configurable workflows: new admission → assign reviewer; invoice approaching due date → reminder; marks submitted → moderation/approval; student transferred → record-transfer checklist; inventory at reorder level → purchase request; integration fails → alert technical admin; subscription approaching expiry → notify billing contact. Include workflow editor, version history, test mode, execution logs, retry controls, safeguards against duplicate actions.

### 6. Advanced SaaS and enterprise capabilities

Multi-campus management, white-label branding, custom module builder, feature entitlements, school comparison analytics, API and webhook management, single sign-on, data import and migration centre, audit and compliance centre, disaster recovery centre.

### 7. More powerful user-interface features

Global command palette (Ctrl+K), universal search, customizable dashboards, activity timeline, background task centre, contextual detail panels, keyboard shortcuts and bulk actions.

### 8. Additional features worth considering later

Digital credentials, parent volunteer portal, alumni network, school admissions CRM, extracurricular management, laboratory management, school elections, document signing, multi-curriculum support, live class integrations.

### 9. Recommended priority

- **P1 (build next)**: global search, activity timelines, workflow automation, CBE portfolios, data import tools, advanced reconciliation, configurable report generation.
- **P2 (after core stable)**: custom dashboards, multi-campus analytics, boarding, clinic, facilities, admissions CRM, integration management.
- **P3 (advanced expansion)**: personalized AI learning, predictive planning, verifiable credentials, advanced identity federation, configurable module building.

---

## CBE, E-Learning, Examinations & Student Success Expansion

Trixx Education should treat competency-based education (CBE), digital learning, examinations, and student success as one connected academic experience, rather than four disconnected modules.

The central workflow is:

1. CBE curriculum & competencies
2. Learning outcomes, strands, sub-strands and rubrics
3. Learning experiences
4. Lessons, digital resources, activities and projects
5. Evidence & assessment
6. Quizzes, assignments, practical work, portfolios and exams
7. Student success
8. Competency mastery, progress trends, intervention plans and parent engagement

The specification assumes Trixx Education is a multi-school cloud SaaS platform, with a Flutter application for desktop, tablet and mobile, and a Java REST API backed by a relational database. It supports Kenyan CBE workflows while keeping curriculum structures configurable for other education systems.

### 1. Product modules and access

| Module | Main capabilities | Primary users |
|---|---|---|
| CBE curriculum | Competencies, outcomes, rubrics, curriculum mapping, learning plans | Curriculum administrator, school admin, teacher |
| E-learning | Courses, lessons, resources, assignments, discussions, offline learning | Student, teacher |
| Examinations | Assessment authoring, schedules, online and paper exams, marking, moderation | Teacher, examiner, exam officer |
| Student success | Learner portfolio, mastery dashboard, progress trends, intervention plans | Student, teacher, parent, counsellor |
| Academic oversight | Cohort analytics, curriculum coverage, moderation, reports | School leadership, authorized academic staff |

Core design rules: use Trixx Education's existing design tokens, typography, spacing scale, cards, tables, charts, form fields, notification patterns. Support light and dark themes through semantic color tokens. Use desktop navigation rail/sidebar, tablet collapsible navigation, mobile navigation bar or drawer. Prefer structured data tables on desktop and compact cards on mobile. Keep teacher workflows optimized for bulk operations; student workflows prioritize clarity and low-friction learning. Enforce authorization on the backend for every tenant, school, class, subject, student, and assessment resource. Use mastery and progress as evidence-based educational indicators—not as unsupported AI predictions or a single ranking of students.

### 2. UI screens and responsive behavior

#### 2.1 CBE curriculum workspace

The curriculum workspace is where an authorized user creates and maintains the academic framework that drives teaching and assessment.

- **Curriculum explorer**: Curriculum → learning area → grade → strand → sub-strand → outcome.
- **Competency editor**: outcome description, learning indicators, prerequisite outcomes, resources, assessment criteria.
- **Rubric builder**: criteria, performance levels, level descriptions, evidence types, scoring rules.
- **Curriculum coverage planner**: map outcomes to terms, weekly plans, lessons, learning activities, assessments.

Key screens: Curriculum overview, Curriculum explorer, Outcome detail, Rubric builder, Scheme of work, Curriculum review. Curriculum definitions should be versioned. A published curriculum version is immutable for existing academic records; authorized users create a new draft version for changes.

#### 2.2 E-learning workspace

A complete learning environment, not just a repository of downloadable files.

| Screen | Student experience | Teacher experience |
|---|---|---|
| Learning home | Today's learning, continue lesson, upcoming work | Class activity, pending marking, course coverage |
| Course overview | Progress, modules, deadlines, instructor | Course structure, enrolled learners, publishing controls |
| Lesson player | Text, video, audio, files, activities, notes | Lesson editor, content preview, release settings |
| Assignment | Instructions, rubric, attachments, submission status | Instructions, rubric, deadlines, submission list |
| Discussion | Threaded questions and replies | Moderate, pin answers, flag inappropriate content |
| Learning library | Searchable resources, saved content, downloads | Upload, categorize, tag, version, assign resources |
| Offline learning | Downloadable permitted lessons, queued submissions | View sync status, unresolved submission issues |

Lesson player layout: Desktop: course outline left, lesson content center, contextual notes/activities right. Tablet: collapsible outline, single main content panel. Mobile: single-column lesson, sticky progress indicator, clear Previous/Next controls, compact activity drawer.

Content support: Text, PDFs, images, audio, video, interactive quizzes, SCORM packages if required, downloadable resources. Content should have metadata for curriculum outcomes, language, accessibility, file size, offline eligibility.

Offline submissions must retain their original creation time and sync state. The server remains authoritative for final receipt time.

#### 2.3 Examinations and assessment workspace

Handles both formative assessments and formal examinations.

**Examination workflow**: Assessment authoring → Blueprint and review → Scheduling and delivery → Submission and marking → Moderation and release → Learning follow-up.

**Question types**: Multiple choice and multiple select, True/false, Short answer and structured response, Essay and file upload, Matching and ordering, Image-based questions, Practical assessment with rubric, Project or portfolio submission.

For question banks: tags for grade, subject, topic, competency, difficulty, language, marks, source, review status. Randomized exams use server-generated question selection and store the exact delivered question set for each attempt.

Essential examination screens: Assessment dashboard, Assessment editor, Exam blueprint, Exam schedule, Candidate exam, Marking queue, Moderation screen, Results dashboard.

Integrity and reliability: a web or mobile exam cannot guarantee prevention of cheating on an ordinary unmanaged device. Use defensible controls: randomized question order, server timestamps, attempt limits, autosave, session auditing, explicit incident reporting, optional supervised exam mode. Never silently change marks or discard answers when connectivity fails.

#### 2.4 Student success and competency portfolio

The student success module turns evidence collected across lessons, assignments, projects and examinations into an understandable picture of learning.

Screens: Student overview, Competency portfolio, Progress timeline, Learning gaps, Intervention plan, Parent/guardian view, Academic team dashboard.

A competency should not be declared mastered from one low-quality or unrelated mark. Use configurable mastery rules, appropriate evidence types, recency rules, teacher review where required. Preserve the underlying observations so a mastery summary can always be explained.

#### 2.5 Interactive screen and role preview

A prototype to explore the intended information architecture (design preview, not a live connected application). Role switcher (Student/Teacher/Parent/Academic administrator), module switcher, light/dark theme, desktop/tablet/mobile layout. Shows a student workspace with progress indicators, evidence collected, learning goals updated, interventions reviewed — all illustrative prototype values.

### 3. User journeys and business rules

**Journey A: Set up and publish a CBE learning plan** — Curriculum administrator creates draft → Teacher maps outcomes to weekly lessons → Reviewer checks coverage → Approver publishes. Acceptance: teachers only edit plans assigned to them unless granted additional permissions; published plans cannot be silently overwritten; a plan cannot be published if mandatory fields or required approvals are missing; changes after publication create a new revision or tracked amendment; all curriculum records scoped to correct tenant and school.

**Journey B: Student completes an online lesson and assignment** — Student opens course dashboard, selects published lesson, app checks access rights, downloads lesson metadata, displays content, completes activities and embedded quiz, opens assignment, views instructions and rubric, uploads work, app saves draft locally where supported then submits to server, server records receipt time and submission version and returns receipt identifier, teacher receives submission in marking queue, student sees feedback after teacher publishes it. Rules: student cannot access another student's private submission; resubmissions follow assignment's configured policy; files scanned and validated before becoming available to other users.

**Journey C: Conduct an examination and publish results** — Teacher drafts assessment and associates questions with outcomes; authorized reviewer approves; exam officer schedules for selected classes and verifies candidate eligibility; student starts attempt after server validates schedule and attempt rules; app autosaves answers and shows last successful save; student submits or server closes attempt according to configured deadline policy; objective questions auto-marked; manual responses enter marking queue; marks moderated where required and results approved; authorized publisher releases results and competency evidence becomes available. Failure handling: network loss must not silently reset the exam; client keeps pending answers, retries idempotent saves, indicates when answers have not reached the server; final submission must have clear server acknowledgment; if exam closes during connection failure, configurable auditable grace policy determines how pending work is handled.

**Journey D: Identify and support a learner who is struggling** — Teacher opens learner's progress timeline; system highlights incomplete work, recent assessment evidence, outcomes requiring more evidence; teacher reviews underlying evidence; teacher creates intervention plan with learning goal, activities, support resources, responsible person, review date; student receives approved activities; guardian sees summary if school permissions allow; teacher reviews new evidence and records whether goal was met, partially met, or needs revision; system retains intervention history. Interventions must be supportive and private — avoid stigmatizing labels, public rankings, or automated high-stakes decisions based solely on predicted performance.

### 4. REST API contracts

Use a versioned REST API under `/api/v1`, JSON requests/responses, UUID identifiers, ISO 8601 timestamps, consistent pagination, structured error responses.

- Authentication: OIDC/OAuth 2.0 compatible access tokens or secure session authentication.
- Tenant context: resolved and validated by the backend; never trusted solely from a client-supplied tenant ID.
- Pagination: `page`, `size`, stable sorting; cursor pagination for large timelines if needed.
- Errors: consistent machine-readable error code, human-readable message, request ID, field errors.
- Concurrency: entity versions or ETags for editable records; reject stale writes.
- Example error response provided (validation, 422, with fieldErrors).

#### 4.2 CBE curriculum endpoints

`GET /curricula`, `GET /curricula/{curriculumId}/structure`, `POST /competencies`, `GET /competencies/{id}`, `PATCH /competencies/{id}`, `POST /rubrics`, `PUT /learning-plans/{id}/outcomes`, `POST /learning-plans/{id}/submit-review`, `POST /learning-plans/{id}/publish`. Example competency creation request/response provided. Server must verify curriculum is editable, learning area belongs to that curriculum, actor has permission.

#### 4.3 E-learning endpoints

`GET /courses?classId={id}`, `GET /courses/{id}`, `POST /courses`, `POST /courses/{id}/publish`, `GET /lessons/{id}`, `PUT /lessons/{id}/progress`, `POST /assignments`, `POST /assignments/{id}/submissions`, `GET /submissions/{id}`, `POST /submissions/{id}/attachments`, `POST /submissions/{id}/submit`, `POST /submissions/{id}/feedback`. Example submission with Idempotency-Key; creates a draft, separate submission action finalizes and returns receipt. `clientSavedAt` is informational, not authoritative.

#### 4.4 Examination endpoints

`POST /question-banks/questions`, `GET /question-banks/questions`, `POST /assessments`, `PUT /assessments/{id}/blueprint`, `POST /assessments/{id}/submit-review`, `POST /assessments/{id}/approve`, `POST /exam-sessions`, `POST /exam-sessions/{id}/attempts`, `PUT /attempts/{id}/answers/{questionId}`, `POST /attempts/{id}/submit`, `GET /marking-queues`, `POST /marking-tasks/{id}/mark`, `POST /results/{id}/moderate`, `POST /results/{id}/publish`. Example exam attempt creation response (attemptId, status, startedAt, deadlineAt, remainingSeconds, questions). Only questions and options intended for the candidate returned; correct answers never exposed through candidate endpoint. Example answer save with Idempotency-Key; server validates attempt ownership, session state, question membership, deadline policy.

#### 4.5 Student success endpoints

`GET /students/{id}/progress`, `GET /students/{id}/competencies`, `GET /students/{id}/timeline`, `GET /students/{id}/portfolio`, `POST /interventions`, `PATCH /interventions/{id}`, `POST /interventions/{id}/reviews`, `GET /analytics/cohorts/{id}`. Example intervention request. API validates student enrollment, staff assignment, competency applicability, caller permissions. Guardian visibility separately authorized and configurable by school policy.

#### 4.6 Core domain statuses

Curriculum: DRAFT → IN_REVIEW → APPROVED → PUBLISHED → RETIRED
Course: DRAFT → PUBLISHED → ARCHIVED
Assignment submission: DRAFT → SUBMITTED → RETURNED or GRADED
Assessment: DRAFT → IN_REVIEW → APPROVED → SCHEDULED → CLOSED
Exam attempt: IN_PROGRESS → SUBMITTED or EXPIRED
Result: PENDING_MARKING → MARKED → MODERATION → APPROVED → PUBLISHED
Intervention: DRAFT → ACTIVE → UNDER_REVIEW → COMPLETED or CANCELLED

Treat transitions as explicit backend commands with validation and audit logging.

### 5. Java backend architecture

#### 5.1 Recommended architecture

Start with a modular monolith. Flutter clients → API edge and identity (HTTPS, authentication, rate limits, request validation, tenant context) → Spring Boot application (CBE module, Learning module, Assessment module, Student success module, Shared platform modules: identity, tenancy, enrollment, notifications, audit, files, reporting) → PostgreSQL (transactional records), Object storage (learning files and evidence), Redis optional (cache and short-lived state), Background workers (reports, notifications, analytics).

#### 5.2 Technology choices

Java 21 or newer supported LTS; Spring Boot 3.x; Spring MVC, Bean Validation, OpenAPI; Spring Security, OAuth 2.0/OIDC; PostgreSQL with Flyway; Spring Data JPA (explicit SQL/JDBC for performance-sensitive queries); S3-compatible object storage with short-lived signed URLs; transactional outbox plus queue when async processing required; Redis for non-authoritative short-lived cached data; structured logs, metrics, tracing, error monitoring; JUnit, Spring integration tests, Testcontainers, API contract tests. Pin compatible framework and dependency versions. Do not treat Redis or the Flutter client as the authoritative store for marks, exam deadlines, access rights or payment-related records.

#### 5.3 Package structure

Feature-based packages with `api`, `application`, `domain`, `infrastructure` subpackages under `com.trixx` (mapped from the spec's `com.schoolsphere`): curriculum, learning, assessment, student_success, reporting, plus platform (security, tenancy, auditing, errors, files, notifications, configuration). Controllers thin; use cases orchestrate.

#### 5.4 Domain model and relationships

Tenant → School → Academic year → Class / Enrollment. Enrollment links students to academic context and access scope. CurriculumVersion → LearningArea → Competency. LearningPlan → Lesson / Course → Assignment. Assessment → QuestionVersion → ExamAttempt / Submission. AssessmentEvidence → MasterySummary → Intervention.

Suggested core tables: tenants, schools, users, school_memberships, student_enrollments, curriculum_versions, competencies, rubrics/rubric_criteria, learning_plans/plan_outcomes, courses/lessons, assignments/submissions, questions/question_versions, assessments/exam_sessions, exam_attempts/attempt_answers, marks/moderation_records, assessment_evidence, mastery_summaries, interventions/intervention_reviews, audit_events.

For multi-tenant tables, use non-null tenant and school ownership fields, composite constraints, indexes beginning with common scope columns. Enforce foreign-key relationships so records cannot accidentally connect students, assessments or evidence from unrelated schools.

#### 5.5 Multi-tenant security

Authenticate the user; validate token/session and resolve internal identity. Resolve active membership; check active tenant and school memberships. Authorize the action; apply role, resource, enrollment, assignment, ownership checks. Scope data access; every relevant query constrained by tenant and where appropriate school and class. Validate relationships; referenced students, outcomes, questions, resources belong to permitted academic context. Audit sensitive actions. Protect files; authorize every upload/download, scan files, use expiring URLs. Test isolation; prove a user in Tenant A cannot access Tenant B data through IDs, filters, exports, search, cached responses, background jobs. For PostgreSQL, consider row-level security as defense-in-depth. Do not assume a tenantId in the request body is valid.

#### 5.6 Evidence and mastery engine

Separate raw evidence from interpretation. Evidence record: source assessment/activity, student, competency, evidence type, rubric result, teacher, timestamp, curriculum version. Mastery rule: configurable threshold, required evidence types, minimum evidence count, recency, optional human approval. Mastery summary: calculated status, rule version, evidence references, last calculation time. Teacher override: reason, actor, date, audit trail. Recalculate summaries whenever relevant evidence changes, but retain historical evidence and rule version used. Dashboard distinguishes not yet assessed, insufficient evidence, developing, mastered.

### 6. Flutter/Dart frontend architecture

Feature-first organization with shared design-system components.

#### 6.1 Project structure

`lib/main.dart`, `lib/app/`, `lib/core/` (api, auth, tenancy, design_system, responsive, offline, utilities), `lib/features/` (curriculum, elearning, examinations, student_success, reporting), `lib/shared/`. Recommended: Riverpod or Bloc (choose one consistently), go_router with route guards, Dio or comparable typed API client, immutable request/response models with generated JSON serialization, centralized form validation, local database for explicitly supported cached content and queued operations, unit/widget/integration/accessibility tests. Route guards improve UX but the Java backend remains the actual security boundary.

#### 6.2 Responsive breakpoints

| Layout | Width | Navigation | Main content |
|---|---|---|---|
| Compact mobile | Below 600 px | Bottom navigation or drawer | Single column, stacked cards |
| Tablet | 600–1023 px | Collapsible rail | One or two columns |
| Desktop | 1024 px and above | Persistent sidebar | Multi-column dashboard, data tables |
| Wide desktop | 1440 px and above | Persistent sidebar | Wider work area with capped content width |

Do not select layout based on device type alone.

#### 6.3 Design-system requirements

Keep tokens in a shared theme configuration. Suggested visual language: primary blue (navigation, primary actions, links), progress teal (progress indicators, positive status), attention amber (deadlines, review-required), error red (validation errors, critical failures). Additional tokens for surface, elevated surface, border, muted text, focus-ring colors; spacing on 4-point grid; consistent card radii, elevation, form-control heights; heading/body/caption/numerical typography; loading skeletons, empty states, offline banners, error states; focus indicators, screen-reader labels, text scaling, keyboard navigation. Use the same component API in light and dark modes.

#### 6.4 Reusable Flutter components

`AppScaffold`, `PageHeader`, `FilterBar`, `DataTableShell`, `StatusChip`, `CompetencyProgressCard`, `EvidenceTimeline`, `RubricEditor`, `QuestionEditor`, `ExamTimer`, `AutosaveIndicator`, `SubmissionReceipt`, `EmptyState`, `PermissionBoundary`. Use domain-specific names for complex widgets instead of a single generic widget with dozens of Boolean flags.

### 7. Integration and background processing

When a teacher publishes assessment results, the system may need to update evidence records, recalculate mastery, notify students and guardians, refresh reporting data. Do not make all of these depend on one fragile chain of synchronous HTTP calls.

Recommended event flow: Result publication transaction → Persist approved result and outbox event → Outbox publisher → Evidence update → Analytics refresh → Notifications. Useful domain events: CurriculumPlanPublished, LessonCompleted, AssignmentSubmitted, ExamAttemptSubmitted, AssessmentMarked, ResultPublished, CompetencyEvidenceRecorded, InterventionReviewCompleted. Each event includes event ID, event type, schema version, occurrence time, tenant scope, references to relevant records. Consumers must be idempotent.

Use a database outbox from the start if reliable asynchronous processing matters. Introduce a dedicated message broker when throughput or independent deployment requirements justify it.

### 8. Non-functional requirements and acceptance tests

Security and privacy: least-privilege access by role, school, class, enrollment, resource ownership; prevent students from accessing answer keys, other students' submissions, private intervention notes; separate draft/approved/published result permissions; protect learner information in exports, reports, logs, notifications; define retention and deletion policies; record who changed marks, moderation decisions, result publication states; rate-limit sensitive endpoints and validate uploaded files; never place secrets or long-lived access tokens in source control or ordinary client logs.

Reliability and performance (initial targets, to be confirmed by load testing): normal API read/write response p95 below 500 ms excluding large file transfers and heavy reports; autosave feedback visible immediately, server confirmation target p95 below 1 second on healthy connection; dashboard loading useful first view within 2 seconds under agreed test conditions; exam answer persistence every acknowledged answer durably persisted; background event handling retryable, observable, idempotent; availability set an explicit production SLO.

Required acceptance tests: teacher accesses another tenant's assessment ID → access denied, no data leakage; student requests another learner's portfolio → access denied; teacher edits published curriculum version → rejected unless authorized revision workflow; two markers edit same marking record → version conflict surfaced, no silent overwrite; student loses connectivity during exam → pending answers retained locally where supported, sync state clear; student retries exam submission request → no duplicate submission or second attempt; question edited after exam scheduled → existing attempt content remains reproducible from immutable snapshot; result published twice → no duplicate publication side effects; mastery rules change → existing evidence intact, recalculation versioned and traceable; guardian opens progress report → only linked learner's approved information returned; user switches light/dark themes → controls, charts, tables, validation messages readable; screen narrowed to mobile width → no critical workflow requires horizontal scrolling across entire page; background event delivered more than once → evidence and notifications remain consistent and deduplicated.

### 9. Delivery plan

Build in dependency order:

- Phase 1 — Platform foundation: tenant authorization, memberships, academic years, enrollments, API error handling, audit logging, design-system components.
- Phase 2 — CBE and learning: curriculum versioning, competencies, rubrics, learning plans, course delivery, assignments, uploads, offline-safe draft handling.
- Phase 3 — Examinations: question bank, blueprints, scheduling, secure attempts, autosave, marking, moderation, controlled result release.
- Phase 4 — Student success: evidence aggregation, mastery rules, portfolios, progress timeline, intervention workflows, guardian reporting.
- Phase 5 — Production hardening: tenant-isolation tests, load testing, monitoring, backup/restore drills, accessibility, security review, school pilot.

Definition of done: a school can configure curriculum outcomes and map them to learning plans; teachers can publish lessons, collect assignment submissions and assess evidence; an exam can run through scheduling, candidate attempts, marking, moderation and result release; student and guardian views show only authorized, understandable progress information; competency summaries traceable to original evidence and rules used; all core workflows work across desktop, tablet, mobile with light and dark themes; cross-tenant access tests pass and key actions auditable; API contracts, deployment instructions, migrations, test data, operational runbooks available.

Final architectural recommendation: build Trixx Education's CBE, e-learning, examinations and student-success modules around a shared academic domain and a traceable evidence model. That foundation will make future additions—AI-assisted feedback, curriculum analytics, personalized learning recommendations and parent engagement—more trustworthy and substantially easier to integrate.

---

*Internal code identifiers (Java packages `com.schoolsphere`, database schemas, service names) are intentionally retained per ADR-012; only the user-facing product name is "Trixx Education".*