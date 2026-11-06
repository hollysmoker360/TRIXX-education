package com.schoolsphere.bootstrap;

import com.schoolsphere.academics.internal.AcademicService;
import com.schoolsphere.attendance.internal.AttendanceService;
import com.schoolsphere.auth.api.UserDirectory;
import com.schoolsphere.auth.internal.AuthService;
import com.schoolsphere.auth.internal.UserRepository;
import com.schoolsphere.common.security.CurrentPrincipal;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.learning.internal.LearningService;
import com.schoolsphere.learning.internal.QuizService;
import com.schoolsphere.messaging.api.MessagingApi;
import com.schoolsphere.messaging.internal.MessagingService;
import com.schoolsphere.people.api.PeopleApi;
import com.schoolsphere.people.internal.PeopleService;
import com.schoolsphere.tasks.internal.TaskService;
import com.schoolsphere.tenants.api.TenantsApi;
import com.schoolsphere.tenants.internal.SchoolService;
import com.schoolsphere.timetable.internal.TimetableService;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Seeds a demo school on startup when {@code schoolsphere.seed.enabled=true} (spec 2.3: the MVP
 * must be immediately usable with realistic data). Idempotent: when the school already has a
 * published timetable the seeder logs the credentials and stops. All tenant-scoped work runs
 * through {@link TenantContext} with a fabricated principal per actor so services behave exactly
 * as they would for real requests (spec 7.1 / 8.2).
 */
@Component
@ConditionalOnProperty(name = "schoolsphere.seed.enabled", havingValue = "true")
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    static final String PASSWORD = "Demo1234!";
    private static final String SCHOOL_NAME = "Kilimani Academy";
    private static final String SCHOOL_SLUG = "kilimani";
    private static final String TIMEZONE = "Africa/Nairobi";
    private static final ZoneId ZONE = ZoneId.of(TIMEZONE);

    private final AuthService authService;
    private final UserDirectory userDirectory;
    private final UserRepository userRepository;
    private final TenantsApi tenantsApi;
    private final SchoolService schoolService;
    private final AcademicService academicService;
    private final PeopleApi peopleApi;
    private final PeopleService peopleService;
    private final LearningService learningService;
    private final QuizService quizService;
    private final AttendanceService attendanceService;
    private final MessagingService messagingService;
    private final TaskService taskService;
    private final TimetableService timetableService;

    public DemoSeeder(AuthService authService,
                      UserDirectory userDirectory,
                      UserRepository userRepository,
                      TenantsApi tenantsApi,
                      SchoolService schoolService,
                      AcademicService academicService,
                      PeopleApi peopleApi,
                      PeopleService peopleService,
                      LearningService learningService,
                      QuizService quizService,
                      AttendanceService attendanceService,
                      MessagingService messagingService,
                      TaskService taskService,
                      TimetableService timetableService) {
        this.authService = authService;
        this.userDirectory = userDirectory;
        this.userRepository = userRepository;
        this.tenantsApi = tenantsApi;
        this.schoolService = schoolService;
        this.academicService = academicService;
        this.peopleApi = peopleApi;
        this.peopleService = peopleService;
        this.learningService = learningService;
        this.quizService = quizService;
        this.attendanceService = attendanceService;
        this.messagingService = messagingService;
        this.taskService = taskService;
        this.timetableService = timetableService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed();
        } catch (Exception e) {
            log.error("Demo seeding failed; the app will start without (complete) demo data.", e);
        }
    }

    // ------------------------------------------------------------------ flow

    private void seed() {
        ensureSuperAdmin();
        Map<String, UUID> userIds = new LinkedHashMap<>();
        for (DemoUser user : DEMO_USERS) {
            userIds.put(user.key(), ensureUser(user));
        }
        UUID principalUserId = userIds.get("principal");

        Optional<TenantsApi.MembershipSummary> existing = tenantsApi.listMemberships(principalUserId).stream()
                .filter(m -> SCHOOL_SLUG.equals(m.schoolSlug()))
                .findFirst();
        UUID schoolId;
        UUID ownerMembershipId;
        if (existing.isPresent()) {
            schoolId = existing.get().schoolId();
            ownerMembershipId = existing.get().membershipId();
        } else {
            TenantsApi.MembershipView owner = tenantsApi.createSchool(principalUserId, SCHOOL_NAME, SCHOOL_SLUG,
                    TIMEZONE);
            schoolId = owner.schoolId();
            ownerMembershipId = owner.membershipId();
            log.info("Created demo school '{}' (slug {}).", SCHOOL_NAME, SCHOOL_SLUG);
        }

        Map<String, Actor> actors = new LinkedHashMap<>();
        for (DemoUser user : DEMO_USERS) {
            Actor actor = "principal".equals(user.key())
                    ? actorOf(user, userIds.get(user.key()), schoolId, ownerMembershipId)
                    : ensureMembership(user, userIds.get(user.key()), schoolId, principalUserId,
                            ownerMembershipId);
            actors.put(user.key(), actor);
        }

        Actor principal = actors.get("principal");
        if (alreadySeeded(schoolId, principal)) {
            log.info("Demo school already has a published timetable; seeding skipped.");
            logCredentials();
            return;
        }

        seedAcademics(schoolId, principal, actors);
        logCredentials();
    }

    private boolean alreadySeeded(UUID schoolId, Actor principal) {
        return as(schoolId, principal, () -> {
            UUID yearId = academicService.listYears().stream()
                    .filter(y -> "2026".equals(y.name()))
                    .map(AcademicService.YearView::id)
                    .findFirst()
                    .orElse(null);
            if (yearId == null) {
                return false;
            }
            UUID termId = academicService.listTerms(yearId).stream()
                    .filter(t -> t.seq() == 3)
                    .map(AcademicService.TermView::id)
                    .findFirst()
                    .orElse(null);
            if (termId == null) {
                return false;
            }
            return timetableService.listVersions(termId).stream()
                    .anyMatch(v -> "published".equals(v.state()));
        });
    }

    private void seedAcademics(UUID schoolId, Actor principal, Map<String, Actor> actors) {
        log.info("Seeding demo school '{}' (schoolId={}).", SCHOOL_NAME, schoolId);
        Actor david = actors.get("david");
        Actor sarah = actors.get("sarah");

        AcademicService.YearView year = as(schoolId, principal, () -> {
            AcademicService.YearView existing = academicService.listYears().stream()
                    .filter(y -> "2026".equals(y.name()))
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                return existing;
            }
            AcademicService.YearView created = academicService.createYear("2026", LocalDate.of(2026, 1, 5),
                    LocalDate.of(2026, 11, 27), true);
            academicService.createTerm(created.id(), "Term 1", 1, LocalDate.of(2026, 1, 5),
                    LocalDate.of(2026, 4, 10), false);
            academicService.createTerm(created.id(), "Term 2", 2, LocalDate.of(2026, 5, 4),
                    LocalDate.of(2026, 8, 7), false);
            return created;
        });
        AcademicService.TermView term3 = as(schoolId, principal, () -> {
            AcademicService.TermView existing = academicService.listTerms(year.id()).stream()
                    .filter(t -> t.seq() == 3)
                    .findFirst()
                    .orElse(null);
            if (existing != null) {
                return existing;
            }
            return academicService.createTerm(year.id(), "Term 3", 3, LocalDate.of(2026, 8, 31),
                    LocalDate.of(2026, 11, 27), true);
        });

        Map<String, AcademicService.DepartmentView> departments = as(schoolId, principal, () -> {
            Map<String, AcademicService.DepartmentView> map = new LinkedHashMap<>();
            map.put("sciences",
                    academicService.createDepartment("Sciences", "SCI", actors.get("james").membershipId()));
            map.put("languages", academicService.createDepartment("Languages", "LANG", sarah.membershipId()));
            map.put("mathematics", academicService.createDepartment("Mathematics", "MATH", david.membershipId()));
            return map;
        });
        AcademicService.ClassView grade7 = as(schoolId, principal,
                () -> academicService.createClass("Grade 7", 7, departments.get("mathematics").id(),
                        david.membershipId(), 45));
        as(schoolId, principal, () -> {
            academicService.createStream(grade7.id(), "East");
            academicService.createStream(grade7.id(), "West");
            return null;
        });
        Map<String, AcademicService.SubjectView> subjects = as(schoolId, principal, () -> {
            Map<String, AcademicService.SubjectView> map = new LinkedHashMap<>();
            map.put("mathematics", academicService.createSubject("Mathematics", "MAT",
                    departments.get("mathematics").id(), true));
            map.put("english", academicService.createSubject("English", "ENG",
                    departments.get("languages").id(), true));
            map.put("kiswahili", academicService.createSubject("Kiswahili", "KIS",
                    departments.get("languages").id(), true));
            map.put("science", academicService.createSubject("Integrated Science", "SCI",
                    departments.get("sciences").id(), true));
            map.put("social", academicService.createSubject("Social Studies", "SST",
                    departments.get("languages").id(), true));
            return map;
        });
        as(schoolId, principal, () -> {
            academicService.createClassroom("Room A1", "standard", 45);
            academicService.createClassroom("Room B2", "standard", 45);
            academicService.createClassroom("Science Lab", "lab", 40);
            academicService.createPeriod(year.id(), "Period 1", 1, LocalTime.of(8, 0), LocalTime.of(8, 40), false);
            academicService.createPeriod(year.id(), "Period 2", 2, LocalTime.of(8, 40), LocalTime.of(9, 20), false);
            academicService.createPeriod(year.id(), "Period 3", 3, LocalTime.of(9, 20), LocalTime.of(10, 0), false);
            academicService.createPeriod(year.id(), "Period 4", 4, LocalTime.of(10, 20), LocalTime.of(11, 0), false);
            academicService.createPeriod(year.id(), "Period 5", 5, LocalTime.of(11, 0), LocalTime.of(11, 40), false);
            academicService.createPeriod(year.id(), "Period 6", 6, LocalTime.of(11, 40), LocalTime.of(12, 20), false);
            academicService.createPeriod(year.id(), "Period 7", 7, LocalTime.of(14, 0), LocalTime.of(14, 40), false);
            academicService.createPeriod(year.id(), "Period 8", 8, LocalTime.of(14, 40), LocalTime.of(15, 20), false);
            return null;
        });

        seedPeople(schoolId, principal, actors, year, term3, grade7, subjects);
    }

    private void seedPeople(UUID schoolId, Actor principal, Map<String, Actor> actors,
                            AcademicService.YearView year, AcademicService.TermView term3,
                            AcademicService.ClassView grade7,
                            Map<String, AcademicService.SubjectView> subjects) {
        Map<String, PeopleApi.TeacherRef> teachers = as(schoolId, principal, () -> {
            Map<String, PeopleApi.TeacherRef> map = new LinkedHashMap<>();
            map.put("david", peopleApi.createTeacher(schoolId, actors.get("david").userId(), "T-001", "David",
                    "Mwangi", "+254712000003", 28));
            map.put("sarah", peopleApi.createTeacher(schoolId, actors.get("sarah").userId(), "T-002", "Sarah",
                    "Achieng", "+254712000004", 28));
            map.put("james", peopleApi.createTeacher(schoolId, actors.get("james").userId(), "T-003", "James",
                    "Kariuki", "+254712000005", 28));
            return map;
        });
        Map<String, PeopleApi.StudentRef> students = as(schoolId, principal, () -> {
            Map<String, PeopleApi.StudentRef> map = new LinkedHashMap<>();
            map.put("faith", peopleApi.createStudent(schoolId, actors.get("faith").userId(), "KA-2026-001",
                    "Faith", "Njeri", null, LocalDate.of(2013, 3, 12), "female", null, null));
            map.put("brian", peopleApi.createStudent(schoolId, actors.get("brian").userId(), "KA-2026-002",
                    "Brian", "Omondi", null, LocalDate.of(2013, 7, 5), "male", null, null));
            map.put("mercy", peopleApi.createStudent(schoolId, actors.get("mercy").userId(), "KA-2026-003",
                    "Mercy", "Chebet", null, LocalDate.of(2013, 1, 22), "female", null, null));
            map.put("kevin", peopleApi.createStudent(schoolId, actors.get("kevin").userId(), "KA-2026-004",
                    "Kevin", "Mutua", null, LocalDate.of(2013, 9, 14), "male", null, null));
            map.put("diana", peopleApi.createStudent(schoolId, actors.get("diana").userId(), "KA-2026-005",
                    "Diana", "Atieno", null, LocalDate.of(2013, 5, 30), "female", null, null));
            map.put("samuel", peopleApi.createStudent(schoolId, actors.get("samuel").userId(), "KA-2026-006",
                    "Samuel", "Kiprop", null, LocalDate.of(2013, 11, 2), "male", null, null));
            for (PeopleApi.StudentRef student : map.values()) {
                peopleApi.enrollStudent(schoolId, student.id(), year.id(), grade7.id(), null, "enrolled");
            }
            return map;
        });
        Map<String, PeopleApi.ParentRef> parents = as(schoolId, principal, () -> {
            Map<String, PeopleApi.ParentRef> map = new LinkedHashMap<>();
            map.put("alice", peopleApi.createParent(schoolId, actors.get("alice").userId(), "Alice", "Njeri",
                    "+254712000010"));
            map.put("joseph", peopleApi.createParent(schoolId, actors.get("joseph").userId(), "Joseph", "Omondi",
                    "+254712000011"));
            return map;
        });
        as(schoolId, principal, () -> {
            Map<String, Boolean> visibilities = Map.of("attendance", true, "grades", true, "communication", true);
            peopleApi.createGuardianLink(schoolId, parents.get("alice").id(), students.get("faith").id(),
                    "mother", true, visibilities);
            peopleApi.createGuardianLink(schoolId, parents.get("joseph").id(), students.get("brian").id(),
                    "father", true, visibilities);
            return null;
        });

        as(schoolId, principal, () -> {
            peopleService.createTeacherAssignment(teachers.get("david").id(), grade7.id(), null,
                    subjects.get("mathematics").id(), term3.id(), 5);
            peopleService.createTeacherAssignment(teachers.get("david").id(), grade7.id(), null,
                    subjects.get("social").id(), term3.id(), 3);
            peopleService.createTeacherAssignment(teachers.get("sarah").id(), grade7.id(), null,
                    subjects.get("english").id(), term3.id(), 5);
            peopleService.createTeacherAssignment(teachers.get("sarah").id(), grade7.id(), null,
                    subjects.get("kiswahili").id(), term3.id(), 4);
            peopleService.createTeacherAssignment(teachers.get("james").id(), grade7.id(), null,
                    subjects.get("science").id(), term3.id(), 4);
            return null;
        });

        seedTimetable(schoolId, actors.get("david"), year, term3);
        seedAttendance(schoolId, actors.get("david"), grade7, students);
        seedMessaging(schoolId, actors, grade7);
        seedLearning(schoolId, actors, year, term3, grade7, subjects, students);
    }

    private void seedTimetable(UUID schoolId, Actor david, AcademicService.YearView year,
                               AcademicService.TermView term3) {
        as(schoolId, david, () -> {
            TimetableService.VersionView version = timetableService.createVersion(year.id(), term3.id(),
                    "Term 3 2026 – Baseline", "Generated by the demo seeder.");
            timetableService.generate(version.summary().id(),
                    new TimetableService.GenerateInput(20261016L, 40, null));
            timetableService.publish(version.summary().id());
            return null;
        });
    }

    private void seedAttendance(UUID schoolId, Actor david, AcademicService.ClassView grade7,
                                Map<String, PeopleApi.StudentRef> students) {
        List<LocalDate> days = recentWeekdays(5);
        LocalDate today = LocalDate.now(ZONE);
        for (LocalDate date : days) {
            boolean isToday = date.equals(today);
            as(schoolId, david, () -> {
                AttendanceService.SessionView session = attendanceService.openSession(grade7.id(), null, null,
                        "daily", date, null, "Morning register");
                List<AttendanceService.MarkInput> marks = new ArrayList<>();
                for (PeopleApi.StudentRef student : students.values()) {
                    marks.add(new AttendanceService.MarkInput(student.id(), "present", null));
                }
                if (isToday) {
                    replaceMark(marks, students.get("faith").id(), "absent", "Unwell");
                    replaceMark(marks, students.get("brian").id(), "late", "Matatu delay");
                } else if (date.equals(days.get(days.size() - 2))) {
                    replaceMark(marks, students.get("mercy").id(), "absent", "Family emergency");
                }
                attendanceService.mark(session.id(), marks);
                if (!isToday) {
                    attendanceService.closeSession(session.id(), null);
                }
                return null;
            });
        }
    }

    private static void replaceMark(List<AttendanceService.MarkInput> marks, UUID studentId, String status,
                                    String reason) {
        marks.replaceAll(m -> m.studentId().equals(studentId)
                ? new AttendanceService.MarkInput(studentId, status, reason)
                : m);
    }

    private void seedMessaging(UUID schoolId, Map<String, Actor> actors, AcademicService.ClassView grade7) {
        Actor david = actors.get("david");
        Actor principal = actors.get("principal");
        UUID classChannelId = as(schoolId, david, () -> messagingService
                .createChannel("class", "Grade 7 – Class Channel", null, grade7.id(), null, false, null)
                .id());
        as(schoolId, david, () -> {
            messagingService.addMembers(classChannelId, List.of(actors.get("sarah").membershipId(),
                    actors.get("james").membershipId(), principal.membershipId()));
            return null;
        });
        send(schoolId, david, classChannelId,
                "Good morning Grade 7! Remember your maths exercise books for the fractions lesson today.");
        send(schoolId, actors.get("sarah"), classChannelId,
                "Reminder: this week's reading log is due on Friday. Twenty minutes a day is enough!");
        send(schoolId, david, classChannelId,
                "Well done on yesterday's assessment — most of you improved. Keep practising those times tables.");

        UUID staffRoomId = as(schoolId, principal, () -> messagingService
                .createChannel("department", "Staff Room", null, null, null, true,
                        List.of(actors.get("admin").membershipId(), david.membershipId(),
                                actors.get("sarah").membershipId(), actors.get("james").membershipId()))
                .id());
        send(schoolId, principal, staffRoomId,
                "Staff meeting on Friday at 4 pm in the staffroom. We will review Term 3 assessment data.");
    }

    private void send(UUID schoolId, Actor sender, UUID channelId, String body) {
        as(schoolId, sender, () -> messagingService.sendMessage(new MessagingApi.MessageInput(null,
                UUID.randomUUID(), channelId, body, null, null)));
    }

    private void seedLearning(UUID schoolId, Map<String, Actor> actors, AcademicService.YearView year,
                              AcademicService.TermView term3, AcademicService.ClassView grade7,
                              Map<String, AcademicService.SubjectView> subjects,
                              Map<String, PeopleApi.StudentRef> students) {
        Actor david = actors.get("david");
        Instant now = Instant.now();

        as(schoolId, david, () -> {
            LearningService.ResourceView notes = learningService.createResource(
                    "Kiswahili Verbs – Revision Notes",
                    "Common verb patterns and practice questions for Grade 7 Kiswahili.",
                    "revision_pack", "Verbs", subjects.get("kiswahili").id(), grade7.id(), null, year.id(),
                    term3.id(), null);
            learningService.publishResource(notes.id(), null);
            LearningService.ResourceView safety = learningService.createResource(
                    "Integrated Science – Lab Safety",
                    "Rules for working safely in the science laboratory.",
                    "link", "Lab Safety", subjects.get("science").id(), grade7.id(), null, year.id(),
                    term3.id(), null);
            learningService.publishResource(safety.id(), null);
            return null;
        });

        UUID fractionsId = as(schoolId, david, () -> learningService.createAssignment(
                "Fractions and Decimals Worksheet",
                "Complete questions 1–15 in your exercise books. Show all your working.",
                subjects.get("mathematics").id(), grade7.id(), null, term3.id(),
                now.minusSeconds(2 * 86400L), now.plusSeconds(3 * 86400L), "penalized",
                BigDecimal.valueOf(20), null, null, null).id());
        as(schoolId, david, () -> learningService.publishAssignment(fractionsId));
        UUID readingId = as(schoolId, david, () -> learningService.createAssignment(
                "Reading Log – Term 3 Week 6",
                "Read for 20 minutes every evening and write a short summary of what you read.",
                subjects.get("english").id(), grade7.id(), null, term3.id(),
                now.minusSeconds(86400L), now.plusSeconds(5 * 86400L), "accepted",
                BigDecimal.valueOf(10), null, null, null).id());
        as(schoolId, david, () -> learningService.publishAssignment(readingId));

        as(schoolId, actors.get("faith"), () -> learningService.submit(fractionsId,
                "Q1: 1/2 + 1/4 = 3/4. Q2: 0.25 as a fraction is 1/4. Q3: 3/4 = 0.75.",
                null, null));
        as(schoolId, actors.get("brian"), () -> learningService.submit(readingId,
                "This week I read 'The River Between'. I learned about how education changed the village.",
                null, null));

        as(schoolId, david, () -> {
            List<LearningService.SubmissionView> submissions = learningService.listSubmissions(fractionsId);
            submissions.stream()
                    .filter(s -> s.studentId().equals(students.get("faith").id()))
                    .findFirst()
                    .ifPresent(s -> learningService.grade(s.id(), BigDecimal.valueOf(18),
                            "Excellent work — recheck question 7."));
            learningService.publishGrades(fractionsId);
            return null;
        });

        as(schoolId, david, () -> {
            QuizService.QuizView quiz = quizService.createQuiz(fractionsId, "Fractions Quick Check", null, null,
                    10, 1, true, List.of(
                            new QuizService.QuestionInput("mcq", "What is 1/2 + 1/4?",
                                    List.of("3/4", "2/6", "1/6", "1/4"), Map.of("index", 0), BigDecimal.ONE),
                            new QuizService.QuestionInput("true_false", "0.5 is equal to 1/2.",
                                    null, Map.of("value", true), BigDecimal.ONE),
                            new QuizService.QuestionInput("short_answer", "Write 3/4 as a decimal.",
                                    null, Map.of("text", "0.75"), BigDecimal.valueOf(2))));
            quizService.publishQuiz(quiz.id());
            return null;
        });

        as(schoolId, david, () -> taskService.createForOwner(david.userId(),
                "Mark fractions worksheet submissions", now.plusSeconds(2 * 86400L), "high", "grading",
                fractionsId, null));
    }

    // ------------------------------------------------------------- identities

    private void ensureSuperAdmin() {
        Optional<UserDirectory.UserRef> existing = userDirectory.findByEmail("orwafrancesc11@gmail.com");
        if (existing.isPresent()) {
            UUID superAdminId = existing.get().id();
            setPlatformAdmin(superAdminId, true);
            return;
        }
        try {
            AuthService.LoginResult result = authService.register("orwafrancesc11@gmail.com", "hollysmokerme",
                    "Platform Super Admin", null, "seeder", "server");
            setPlatformAdmin(result.user().id(), true);
            log.info("Created platform super admin: orwafrancesc11@gmail.com / hollysmokerme");
        } catch (Exception e) {
            // Already exists or conflict - ignore
            log.info("Super admin already exists or could not be created: {}", e.getMessage());
        }
    }

    private void setPlatformAdmin(UUID userId, boolean isAdmin) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setPlatformAdmin(isAdmin);
            userRepository.save(user);
        });
    }
    private UUID ensureUser(DemoUser user) {
        return userDirectory.findByEmail(user.email())
                .map(UserDirectory.UserRef::id)
                .orElseGet(() -> authService.register(user.email(), PASSWORD, user.displayName(), user.phone(),
                        "seeder", "server").user().id());
    }

    private Actor ensureMembership(DemoUser user, UUID userId, UUID schoolId, UUID inviterUserId,
                                   UUID inviterMembershipId) {
        Optional<TenantsApi.MembershipView> existing = tenantsApi.resolveMembership(userId, schoolId);
        if (existing.isPresent()) {
            return actorOf(user, userId, schoolId, existing.get().membershipId(), existing.get().permissions());
        }
        SchoolService.InviteResult invite = TenantContext.run(schoolId, inviterUserId, inviterMembershipId,
                () -> schoolService.inviteMember(user.email(), user.displayName(), List.of(user.role()),
                        user.title()));
        return actorOf(user, userId, schoolId, invite.membershipId(), null);
    }

    private Actor actorOf(DemoUser user, UUID userId, UUID schoolId, UUID membershipId) {
        return actorOf(user, userId, schoolId, membershipId, null);
    }

    private Actor actorOf(DemoUser user, UUID userId, UUID schoolId, UUID membershipId,
                          Set<String> permissions) {
        Set<String> resolved = permissions != null ? permissions
                : tenantsApi.resolveMembership(userId, schoolId)
                        .map(TenantsApi.MembershipView::permissions)
                        .orElse(Set.of());
        return new Actor(user.key(), userId, user.displayName(), user.email(), membershipId, resolved);
    }

    /** Runs a block as the actor: tenant context plus a principal, exactly like a real request. */
    private <T> T as(UUID schoolId, Actor actor, Callable<T> block) {
        CurrentPrincipal principal = new CurrentPrincipal(actor.userId(), actor.displayName(), actor.email(),
                schoolId, actor.membershipId(), actor.permissions(), null);
        return TenantContext.run(schoolId, actor.userId(), actor.membershipId(), () -> {
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
            try {
                return block.call();
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
    }

    private static List<LocalDate> recentWeekdays(int count) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate cursor = LocalDate.now(ZONE);
        while (days.size() < count) {
            DayOfWeek dow = cursor.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                days.add(cursor);
            }
            cursor = cursor.minusDays(1);
        }
        Collections.reverse(days);
        return days;
    }

    private void logCredentials() {
        log.info("""
                ============================================================
                Trixx Education demo school ready: {} (slug: {})
                Sign in at the Flutter app with any account below.
                Password for every demo account: {}
                  principal@kilimani.ac.ke    – Grace Wanjiru (principal)
                  admin@kilimani.ac.ke        – Peter Otieno (admin)
                  teacher.david@kilimani.ac.ke – David Mwangi (teacher)
                  teacher.sarah@kilimani.ac.ke – Sarah Achieng (teacher)
                  teacher.james@kilimani.ac.ke – James Kariuki (teacher)
                  student.faith@kilimani.ac.ke – Faith Njeri (student)
                  student.brian@kilimani.ac.ke – Brian Omondi (student)
                  student.mercy@kilimani.ac.ke – Mercy Chebet (student)
                  student.kevin@kilimani.ac.ke – Kevin Mutua (student)
                  student.diana@kilimani.ac.ke – Diana Atieno (student)
                  student.samuel@kilimani.ac.ke – Samuel Kiprop (student)
                  parent.alice@kilimani.ac.ke  – Alice Njeri (parent)
                  parent.joseph@kilimani.ac.ke – Joseph Omondi (parent)
                ============================================================
                PLATFORM SUPER ADMIN:
                  Email: orwafrancesc11@gmail.com
                  Password: hollysmokerme
                ============================================================""",
                SCHOOL_NAME, SCHOOL_SLUG, PASSWORD);
    }

    /** Actor identity captured for one demo user; permissions mirror the real membership. */
    private record Actor(String key, UUID userId, String displayName, String email, UUID membershipId,
                         Set<String> permissions) {
    }

    /** A demo account definition; role is the system role code applied at the school. */
    private record DemoUser(String key, String email, String displayName, String phone, String role, String title) {
    }

    private static final List<DemoUser> DEMO_USERS = List.of(
            new DemoUser("principal", "principal@kilimani.ac.ke", "Grace Wanjiru", "+254712000001", "principal",
                    "Principal"),
            new DemoUser("admin", "admin@kilimani.ac.ke", "Peter Otieno", "+254712000002", "admin",
                    "School Administrator"),
            new DemoUser("david", "teacher.david@kilimani.ac.ke", "David Mwangi", "+254712000003", "teacher",
                    "Mathematics Teacher"),
            new DemoUser("sarah", "teacher.sarah@kilimani.ac.ke", "Sarah Achieng", "+254712000004", "teacher",
                    "English Teacher"),
            new DemoUser("james", "teacher.james@kilimani.ac.ke", "James Kariuki", "+254712000005", "teacher",
                    "Science Teacher"),
            new DemoUser("faith", "student.faith@kilimani.ac.ke", "Faith Njeri", "+254712000006", "student",
                    "Learner"),
            new DemoUser("brian", "student.brian@kilimani.ac.ke", "Brian Omondi", "+254712000007", "student",
                    "Learner"),
            new DemoUser("mercy", "student.mercy@kilimani.ac.ke", "Mercy Chebet", "+254712000008", "student",
                    "Learner"),
            new DemoUser("kevin", "student.kevin@kilimani.ac.ke", "Kevin Mutua", "+254712000009", "student",
                    "Learner"),
            new DemoUser("diana", "student.diana@kilimani.ac.ke", "Diana Atieno", "+254712000012", "student",
                    "Learner"),
            new DemoUser("samuel", "student.samuel@kilimani.ac.ke", "Samuel Kiprop", "+254712000013", "student",
                    "Learner"),
            new DemoUser("alice", "parent.alice@kilimani.ac.ke", "Alice Njeri", "+254712000010", "parent",
                    "Parent"),
            new DemoUser("joseph", "parent.joseph@kilimani.ac.ke", "Joseph Omondi", "+254712000011", "parent",
                    "Parent"));
}
