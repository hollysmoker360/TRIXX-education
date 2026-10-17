package com.schoolsphere.timetable.internal;

import com.schoolsphere.academics.api.AcademicsApi;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import com.schoolsphere.tenants.api.TenantsApi;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Timetable versions, entries, validation, and generation (spec 12.1). Draft versions are freely
 * editable; publishing validates hard constraints and archives the previously published version.
 */
@Service
@TenantScoped
public class TimetableService {

    private static final int DEFAULT_PERIODS_PER_WEEK = 3;
    private static final List<Integer> DEFAULT_DAYS = List.of(1, 2, 3, 4, 5);
    private static final List<Integer> DEFAULT_PERIOD_SEQS = List.of(1, 2, 3, 4, 5, 6, 7, 8);

    private final TimetableVersionRepository versionRepository;
    private final TimetableEntryRepository entryRepository;
    private final TimetableValidator validator;
    private final TimetableGenerator generator;
    private final TimetableCpSolver cpSolver;
    private final LessonScheduleService lessonScheduleService;
    private final com.schoolsphere.people.api.PeopleApi peopleApi;
    private final AcademicsApi academicsApi;
    private final TenantsApi tenantsApi;
    private final JdbcTemplate jdbc;

    public TimetableService(TimetableVersionRepository versionRepository, TimetableEntryRepository entryRepository,
                            TimetableValidator validator, TimetableGenerator generator, TimetableCpSolver cpSolver,
                            LessonScheduleService lessonScheduleService,
                            com.schoolsphere.people.api.PeopleApi peopleApi, AcademicsApi academicsApi,
                            TenantsApi tenantsApi, JdbcTemplate jdbc) {
        this.versionRepository = versionRepository;
        this.entryRepository = entryRepository;
        this.validator = validator;
        this.generator = generator;
        this.cpSolver = cpSolver;
        this.lessonScheduleService = lessonScheduleService;
        this.peopleApi = peopleApi;
        this.academicsApi = academicsApi;
        this.tenantsApi = tenantsApi;
        this.jdbc = jdbc;
    }

    // ----- versions -----

    public List<VersionSummary> listVersions(UUID termId) {
        UUID schoolId = schoolId();
        List<TimetableVersion> versions = termId == null
                ? versionRepository.findBySchoolIdAndDeletedAtIsNullOrderByCreatedAtDesc(schoolId)
                : versionRepository.findBySchoolIdAndTermIdAndDeletedAtIsNullOrderByCreatedAtDesc(schoolId, termId);
        return versions.stream().map(this::toSummary).toList();
    }

    public VersionView getVersion(UUID id) {
        TimetableVersion version = requireVersion(id);
        List<TimetableEntry> entries =
                entryRepository.findBySchoolIdAndVersionIdAndDeletedAtIsNullOrderByDayOfWeekAscPeriodSeqAsc(
                        schoolId(), id);
        return toView(version, entries);
    }

    public VersionView createVersion(UUID academicYearId, UUID termId, String name, String note) {
        UUID schoolId = schoolId();
        UUID resolvedYear = termYear(schoolId, termId, academicYearId);
        TimetableVersion version = new TimetableVersion();
        version.setSchoolId(schoolId);
        version.setAcademicYearId(resolvedYear);
        version.setTermId(termId);
        version.setName(name == null || name.isBlank() ? "Draft timetable" : name.trim());
        version.setNote(note);
        version.setAuthorMembershipId(TenantContext.actorMembershipId());
        version.setCreatedBy(TenantContext.actorUserId());
        version.setUpdatedBy(TenantContext.actorUserId());
        return toView(versionRepository.save(version), List.of());
    }

    public VersionView updateVersion(UUID id, String name, String note) {
        TimetableVersion version = requireDraftVersion(id);
        if (name != null && !name.isBlank()) {
            version.setName(name.trim());
        }
        if (note != null) {
            version.setNote(note);
        }
        return toView(versionRepository.save(version), entriesOf(id));
    }

    public void deleteVersion(UUID id) {
        TimetableVersion version = requireVersion(id);
        if ("published".equals(version.getState())) {
            throw ApiException.conflict("version_published", "Archive the published version before deleting it.");
        }
        version.setDeletedAt(Instant.now());
        versionRepository.save(version);
        for (TimetableEntry entry : entriesOf(id)) {
            entry.setDeletedAt(Instant.now());
            entryRepository.save(entry);
        }
    }

    public VersionView copyVersion(UUID id, String name) {
        UUID schoolId = schoolId();
        TimetableVersion source = requireVersion(id);
        TimetableVersion copy = new TimetableVersion();
        copy.setSchoolId(schoolId);
        copy.setAcademicYearId(source.getAcademicYearId());
        copy.setTermId(source.getTermId());
        copy.setName(name == null || name.isBlank() ? source.getName() + " (copy)" : name.trim());
        copy.setNote(source.getNote());
        copy.setAuthorMembershipId(TenantContext.actorMembershipId());
        copy.setCreatedBy(TenantContext.actorUserId());
        copy.setUpdatedBy(TenantContext.actorUserId());
        copy = versionRepository.save(copy);
        for (TimetableEntry entry : entriesOf(source.getId())) {
            TimetableEntry clone = new TimetableEntry();
            clone.setSchoolId(schoolId);
            clone.setVersionId(copy.getId());
            clone.setClassGroupId(entry.getClassGroupId());
            clone.setStreamId(entry.getStreamId());
            clone.setSubjectId(entry.getSubjectId());
            clone.setTeacherMembershipId(entry.getTeacherMembershipId());
            clone.setClassroomId(entry.getClassroomId());
            clone.setDayOfWeek(entry.getDayOfWeek());
            clone.setPeriodSeq(entry.getPeriodSeq());
            clone.setSpan(entry.getSpan());
            clone.setLocked(entry.isLocked());
            entryRepository.save(clone);
        }
        return toView(copy, entriesOf(copy.getId()));
    }

    public VersionView publish(UUID id) {
        TimetableVersion version = requireVersion(id);
        if ("archived".equals(version.getState())) {
            throw ApiException.conflict("version_archived", "The version is archived.");
        }
        if (version.getUnmetRequirements() > 0) {
            throw ApiException.preconditionFailed("timetable_unmet_requirements",
                    "This is a relaxed draft with " + version.getUnmetRequirements()
                            + " unmet requirement(s); it cannot be published. Generate a complete timetable first.");
        }
        List<TimetableEntry> entries = entriesOf(id);
        TimetableValidator.Report report = validateAndStore(version, entries);
        if (report.hardCount() > 0) {
            throw ApiException.preconditionFailed("timetable_constraints",
                    "Resolve " + report.hardCount() + " hard constraint conflicts before publishing.");
        }
        versionRepository.findFirstBySchoolIdAndTermIdAndStateAndDeletedAtIsNull(schoolId(), version.getTermId(),
                        "published")
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    other.setState("archived");
                    other.setArchivedAt(Instant.now());
                    versionRepository.saveAndFlush(other);
                });
        version.setState("published");
        version.setPublishedAt(Instant.now());
        versionRepository.save(version);
        // Rolling the timetable forward turns it into dated lessons and schedules the reminders
        // (spec 12.2); the previous version's future lessons and pending reminders are withdrawn.
        lessonScheduleService.publishSchedule(version.getId());
        return toView(version, entries);
    }

    public VersionView archive(UUID id) {
        TimetableVersion version = requireVersion(id);
        if (!"archived".equals(version.getState())) {
            version.setState("archived");
            version.setArchivedAt(Instant.now());
            versionRepository.save(version);
        }
        return toView(version, entriesOf(id));
    }

    public ValidationView validateVersion(UUID id) {
        TimetableVersion version = requireVersion(id);
        TimetableValidator.Report report = validateAndStore(version, entriesOf(id));
        return new ValidationView(version.getId(), report.issues(), report.hardCount(), report.softCount(),
                report.score(), report.generatedAt());
    }

    // ----- entries -----

    public List<EntryView> listEntries(UUID versionId, UUID classGroupId, UUID teacherMembershipId) {
        requireVersion(versionId);
        UUID schoolId = schoolId();
        List<TimetableEntry> entries;
        if (classGroupId != null) {
            entries = entryRepository
                    .findBySchoolIdAndVersionIdAndClassGroupIdAndDeletedAtIsNullOrderByDayOfWeekAscPeriodSeqAsc(
                            schoolId, versionId, classGroupId);
        } else if (teacherMembershipId != null) {
            entries = entryRepository
                    .findBySchoolIdAndVersionIdAndTeacherMembershipIdAndDeletedAtIsNullOrderByDayOfWeekAscPeriodSeqAsc(
                            schoolId, versionId, teacherMembershipId);
        } else {
            entries = entriesOf(versionId);
        }
        return toEntryViews(entries);
    }

    public EntryView addEntry(UUID versionId, EntryInput input) {
        TimetableVersion version = requireDraftVersion(versionId);
        validateEntryInput(input);
        List<TimetableEntry> existing = entriesOf(versionId);
        checkClash(input, existing, null, rulesFor(schoolId(), version.getAcademicYearId()));
        TimetableEntry entry = new TimetableEntry();
        entry.setSchoolId(schoolId());
        entry.setVersionId(version.getId());
        applyInput(entry, input);
        entry.setCreatedBy(TenantContext.actorUserId());
        entry.setUpdatedBy(TenantContext.actorUserId());
        entry = entryRepository.save(entry);
        return toEntryViews(List.of(entry)).get(0);
    }

    public EntryView updateEntry(UUID entryId, EntryInput input) {
        UUID schoolId = schoolId();
        TimetableEntry entry = entryRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, entryId)
                .orElseThrow(() -> ApiException.notFound("entry_not_found", "Timetable entry not found."));
        TimetableVersion version = requireDraftVersion(entry.getVersionId());
        validateEntryInput(input);
        checkClash(input, entriesOf(entry.getVersionId()), entryId, rulesFor(schoolId, version.getAcademicYearId()));
        applyInput(entry, input);
        entry.setUpdatedBy(TenantContext.actorUserId());
        entry = entryRepository.save(entry);
        return toEntryViews(List.of(entry)).get(0);
    }

    public void deleteEntry(UUID entryId) {
        UUID schoolId = schoolId();
        TimetableEntry entry = entryRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, entryId)
                .orElseThrow(() -> ApiException.notFound("entry_not_found", "Timetable entry not found."));
        requireDraftVersion(entry.getVersionId());
        entry.setDeletedAt(Instant.now());
        entryRepository.save(entry);
    }

    // ----- generation -----

    public VersionView generate(UUID versionId, GenerateInput input) {
        TimetableVersion version = requireDraftVersion(versionId);
        UUID schoolId = schoolId();
        List<Integer> days = input.days() == null || input.days().isEmpty()
                ? DEFAULT_DAYS
                : input.days().stream().distinct().filter(d -> d >= 1 && d <= 7).sorted().toList();
        TimetableRules schoolRules = rulesFor(schoolId, version.getAcademicYearId());
        TimetableRules rules = schoolRules.withTeachingDays(new HashSet<>(days));
        List<Integer> periods = schoolRules.hasLayout()
                ? schoolRules.teachingPeriods().stream().sorted().toList()
                : DEFAULT_PERIOD_SEQS;
        List<UUID> classrooms = classroomsFor(schoolId);
        Map<UUID, UUID> membershipByUser = membershipByUser(schoolId);

        List<TimetableGenerator.Demand> demands = new ArrayList<>();
        for (com.schoolsphere.people.api.PeopleApi.TeacherAssignmentRef assignment
                : peopleApi.assignmentsForTerm(schoolId, version.getTermId())) {
            int periodsPerWeek = assignment.periodsPerWeek() == null || assignment.periodsPerWeek() <= 0
                    ? DEFAULT_PERIODS_PER_WEEK : assignment.periodsPerWeek();
            demands.add(new TimetableGenerator.Demand(assignment.classGroupId(), assignment.streamId(),
                    assignment.subjectId(),
                    assignment.userId() == null ? null : membershipByUser.get(assignment.userId()),
                    periodsPerWeek));
        }
        if (demands.isEmpty()) {
            throw ApiException.badRequest("no_assignments",
                    "No teacher assignments exist for this term; add them before generating a timetable.");
        }
        checkTeacherCapacity(demands, rules);
        List<TimetableGenerator.Placement> locked = new ArrayList<>();
        for (TimetableEntry entry : entriesOf(versionId)) {
            if (entry.isLocked()) {
                locked.add(new TimetableGenerator.Placement(entry.getClassGroupId(), entry.getStreamId(),
                        entry.getSubjectId(), entry.getTeacherMembershipId(), entry.getClassroomId(),
                        entry.getDayOfWeek(), entry.getPeriodSeq(), entry.getSpan()));
            }
        }
        long seed = input.seed() == null ? System.nanoTime() : input.seed();
        int attempts = input.attempts() == null ? 24 : Math.min(Math.max(input.attempts(), 4), 200);
        TimetableGenerator.Result result = generateWithSolver(version, demands, locked, days, periods, classrooms,
                rules, seed, attempts, schoolId);

        for (TimetableEntry entry : entriesOf(versionId)) {
            if (!entry.isLocked()) {
                entry.setDeletedAt(Instant.now());
                entryRepository.save(entry);
            }
        }
        for (TimetableGenerator.Placement placement : result.placements()) {
            TimetableEntry entry = new TimetableEntry();
            entry.setSchoolId(schoolId);
            entry.setVersionId(versionId);
            entry.setClassGroupId(placement.classGroupId());
            entry.setStreamId(placement.streamId());
            entry.setSubjectId(placement.subjectId());
            entry.setTeacherMembershipId(placement.teacherMembershipId());
            entry.setClassroomId(placement.classroomId());
            entry.setDayOfWeek(placement.day());
            entry.setPeriodSeq(placement.period());
            entry.setSpan(placement.span());
            entry.setCreatedBy(TenantContext.actorUserId());
            entry.setUpdatedBy(TenantContext.actorUserId());
            entryRepository.save(entry);
        }
        version.setSolverSeed(seed);
        List<TimetableEntry> all = entriesOf(versionId);
        TimetableValidator.Report report = validateAndStore(version, all);
        version.setSolverScore(BigDecimal.valueOf(report.score()));
        versionRepository.save(version);
        return toView(version, all);
    }

    private static final String SETTING_SOLVER = "timetable.solver";
    private static final String SOLVER_HEURISTIC = "heuristic";

    private TimetableGenerator.Result generateWithSolver(TimetableVersion version, List<TimetableGenerator.Demand> demands,
                                                        List<TimetableGenerator.Placement> locked, List<Integer> days,
                                                        List<Integer> periods, List<UUID> classrooms, TimetableRules rules,
                                                        long seed, int attempts, UUID schoolId) {
        String configured = stringValue(tenantsApi.schoolSettings(schoolId).get(SETTING_SOLVER), "cp_sat");
        boolean cpSat = !"heuristic".equals(configured) && TimetableCpSolver.available();
        if (!cpSat || !locked.isEmpty()) {
            // The heuristic keeps locked placements exactly as they are; the CP-SAT path falls back to
            // it when the native library is unavailable or when locks must be preserved verbatim.
            if (cpSat && !locked.isEmpty()) {
                logFallback("locked entries present; using the heuristic generator");
            }
            return generator.generate(demands, locked, days, periods, classrooms, rules, seed, attempts);
        }
        List<TimetableCpSolver.Demand> cpDemands = new ArrayList<>();
        for (TimetableGenerator.Demand demand : demands) {
            cpDemands.add(new TimetableCpSolver.Demand(demand.classGroupId(), demand.streamId(), demand.subjectId(),
                    demand.teacherMembershipId(), demand.periodsPerWeek()));
        }
        List<TimetableCpSolver.Placement> cpLocked = new ArrayList<>();
        for (TimetableGenerator.Placement placement : locked) {
            cpLocked.add(new TimetableCpSolver.Placement(placement.classGroupId(), placement.streamId(),
                    placement.subjectId(), placement.teacherMembershipId(), placement.day(), placement.period(),
                    placement.span()));
        }
        int timeLimit = attempts <= 0 ? 30 : Math.min(60, attempts);
        TimetableCpSolver.Solution solution = cpSolver.solve(cpDemands, rules, days, cpLocked, timeLimit, seed);
        version.setUnmetRequirements(solution.unmet().size());
        if (solution.unmet().isEmpty()) {
            version.setNote(null);
        } else {
            version.setNote("Relaxed draft: " + solution.unmet().size()
                    + " requirement(s) could not be placed (" + solution.status() + ").");
        }
        List<RoomAssigner.Lesson> lessons = new ArrayList<>();
        for (TimetableCpSolver.Placement placement : solution.placements()) {
            lessons.add(new RoomAssigner.Lesson(placement.classGroupId(), placement.streamId(), placement.subjectId(),
                    placement.teacherMembershipId(), placement.day(), placement.period(), placement.span()));
        }
        List<RoomAssigner.Assigned> assigned = RoomAssigner.assign(lessons, classrooms);
        int roomsUnassigned = (int) RoomAssigner.unassigned(assigned);
        List<TimetableGenerator.Placement> placements = new ArrayList<>();
        for (RoomAssigner.Assigned entry : assigned) {
            RoomAssigner.Lesson lesson = entry.lesson();
            placements.add(new TimetableGenerator.Placement(lesson.classGroupId(), lesson.streamId(), lesson.subjectId(),
                    lesson.teacherMembershipId(), entry.classroomId(), lesson.day(), lesson.period(), lesson.span()));
        }
        int placed = placements.size();
        // Solver score: full marks when complete and roomed, minus gaps/unmet scaled like the validator's.
        double score = placed == 0 ? 0.0
                : 100.0 * placed / Math.max(1, demands.size() * 2) - (roomsUnassigned * 2.0) - (solution.unmet().size() * 5.0);
        return new TimetableGenerator.Result(placements, seed, demands.size(), placed, roomsUnassigned, 1);
    }

    private String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value).trim();
    }

    private void logFallback(String reason) {
        org.slf4j.LoggerFactory.getLogger(TimetableService.class).info("timetable.generate.fallback reason={}", reason);
    }

    // ----- published read -----

    public PublishedView getPublished(UUID termId, UUID classGroupId, UUID streamId, UUID teacherMembershipId) {
        UUID schoolId = schoolId();
        TimetableVersion published = termId == null
                ? versionRepository.findBySchoolIdAndDeletedAtIsNullOrderByCreatedAtDesc(schoolId).stream()
                        .filter(v -> "published".equals(v.getState())).findFirst().orElse(null)
                : versionRepository.findFirstBySchoolIdAndTermIdAndStateAndDeletedAtIsNull(schoolId, termId,
                        "published").orElse(null);
        if (published == null) {
            throw ApiException.notFound("no_published_timetable", "No published timetable for this term.");
        }
        List<TimetableEntry> entries = entriesOf(published.getId()).stream()
                .filter(e -> classGroupId == null || classGroupId.equals(e.getClassGroupId()))
                .filter(e -> streamId == null || streamId.equals(e.getStreamId()))
                .filter(e -> teacherMembershipId == null || teacherMembershipId.equals(e.getTeacherMembershipId()))
                .toList();
        return new PublishedView(toSummary(published), toEntryViews(entries));
    }

    // ----- internals -----

    private TimetableValidator.Report validateAndStore(TimetableVersion version, List<TimetableEntry> entries) {
        UUID schoolId = schoolId();
        TimetableRules rules = rulesFor(schoolId, version.getAcademicYearId());
        TimetableValidator.Report report = validator.validate(entries, rules, teacherWeeklyLimits(schoolId));
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("hardCount", report.hardCount());
        stored.put("softCount", report.softCount());
        stored.put("score", report.score());
        stored.put("generatedAt", report.generatedAt().toString());
        stored.put("issues", report.issues().stream().map(issue -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("kind", issue.kind());
            map.put("severity", issue.severity());
            map.put("message", issue.message());
            map.put("entryId", issue.entryId() == null ? null : issue.entryId().toString());
            map.put("otherEntryId", issue.otherEntryId() == null ? null : issue.otherEntryId().toString());
            return map;
        }).toList());
        version.setConstraintReport(stored);
        version.setValidatedAt(report.generatedAt());
        versionRepository.save(version);
        return report;
    }

    private void checkClash(EntryInput input, List<TimetableEntry> existing, UUID ignoreEntryId, TimetableRules rules) {
        TimetableEntry probe = new TimetableEntry();
        probe.setId(ignoreEntryId == null ? UUID.randomUUID() : ignoreEntryId);
        applyInput(probe, input);
        for (TimetableEntry entry : existing) {
            if (entry.getId().equals(ignoreEntryId) || !TimetableValidator.overlaps(probe, entry)) {
                continue;
            }
            if (TimetableValidator.sameClass(probe, entry)) {
                throw ApiException.conflict("class_clash", "The class already has a lesson in this slot.");
            }
            if (probe.getTeacherMembershipId() != null
                    && probe.getTeacherMembershipId().equals(entry.getTeacherMembershipId())) {
                throw ApiException.conflict("teacher_clash", "The teacher already has a lesson in this slot.");
            }
            if (probe.getClassroomId() != null && probe.getClassroomId().equals(entry.getClassroomId())) {
                throw ApiException.conflict("room_clash", "The room is already in use in this slot.");
            }
        }
        // Manual edits and swaps are checked against the consecutive-lesson rule immediately so the
        // editor can report the violation instead of saving it.
        List<TimetableEntry> candidate = new ArrayList<>();
        for (TimetableEntry entry : existing) {
            if (!entry.getId().equals(ignoreEntryId)) {
                candidate.add(entry);
            }
        }
        candidate.add(probe);
        for (TimetableValidator.Issue issue : validator.consecutiveIssues(candidate, rules)) {
            throw ApiException.conflict(issue.kind(), issue.message());
        }
    }

    private void validateEntryInput(EntryInput input) {
        if (input.classGroupId() == null || input.subjectId() == null) {
            throw ApiException.badRequest("missing_fields", "classGroupId and subjectId are required.");
        }
        if (input.dayOfWeek() != null && (input.dayOfWeek() < 1 || input.dayOfWeek() > 7)) {
            throw ApiException.badRequest("invalid_day", "dayOfWeek must be between 1 and 7.");
        }
        if (input.periodSeq() != null && input.periodSeq() < 1) {
            throw ApiException.badRequest("invalid_period", "periodSeq must be at least 1.");
        }
        if (input.span() != null && input.span() != 1 && input.span() != 2) {
            throw ApiException.badRequest("invalid_span", "span must be 1 or 2.");
        }
        UUID schoolId = schoolId();
        if (!exists("class_group", schoolId, input.classGroupId())) {
            throw ApiException.badRequest("class_not_found", "Class group not found.");
        }
        if (!exists("subject", schoolId, input.subjectId())) {
            throw ApiException.badRequest("subject_not_found", "Subject not found.");
        }
        if (input.classroomId() != null && !exists("classroom", schoolId, input.classroomId())) {
            throw ApiException.badRequest("room_not_found", "Classroom not found.");
        }
        if (input.teacherMembershipId() != null && !membershipExists(schoolId, input.teacherMembershipId())) {
            throw ApiException.badRequest("teacher_not_found", "Teacher membership not found.");
        }
    }

    private boolean exists(String table, UUID schoolId, UUID id) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE school_id = ? AND id = ? AND deleted_at IS NULL",
                Integer.class, schoolId, id);
        return count != null && count > 0;
    }

    private boolean membershipExists(UUID schoolId, UUID membershipId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM school_membership WHERE school_id = ? AND id = ? AND status = 'active'",
                Integer.class, schoolId, membershipId);
        return count != null && count > 0;
    }

    private void applyInput(TimetableEntry entry, EntryInput input) {
        if (input.classGroupId() != null) {
            entry.setClassGroupId(input.classGroupId());
        }
        if (input.streamId() != null) {
            entry.setStreamId(input.streamId());
        }
        if (input.subjectId() != null) {
            entry.setSubjectId(input.subjectId());
        }
        if (input.teacherMembershipId() != null) {
            entry.setTeacherMembershipId(input.teacherMembershipId());
        }
        if (input.classroomId() != null) {
            entry.setClassroomId(input.classroomId());
        }
        if (input.dayOfWeek() != null) {
            entry.setDayOfWeek(input.dayOfWeek());
        }
        if (input.periodSeq() != null) {
            entry.setPeriodSeq(input.periodSeq());
        }
        if (input.span() != null) {
            entry.setSpan(input.span());
        }
        if (input.locked() != null) {
            entry.setLocked(input.locked());
        }
    }

    private UUID termYear(UUID schoolId, UUID termId, UUID expectedYearId) {
        if (termId == null) {
            throw ApiException.badRequest("missing_term", "A term is required.");
        }
        List<UUID> years = jdbc.query("SELECT academic_year_id FROM term WHERE school_id = ? AND id = ? "
                        + "AND deleted_at IS NULL",
                (rs, i) -> rs.getObject("academic_year_id", UUID.class), schoolId, termId);
        if (years.isEmpty()) {
            throw ApiException.badRequest("term_not_found", "Term not found.");
        }
        UUID year = years.get(0);
        if (expectedYearId != null && !expectedYearId.equals(year)) {
            throw ApiException.badRequest("year_mismatch", "The term does not belong to the academic year.");
        }
        return year;
    }

    /**
     * Builds the period geometry and the school's consecutive-lesson rule. Breaks are read from the
     * same table as teaching periods, so two periods are only adjacent when nothing sits between
     * them, and a double period can never be placed across a break (spec 12.1).
     */
    private TimetableRules rulesFor(UUID schoolId, UUID academicYearId) {
        Map<Integer, Boolean> bySeq = new LinkedHashMap<>();
        jdbc.query("SELECT seq, is_break FROM timetable_period WHERE school_id = ? "
                        + "AND (academic_year_id = ? OR academic_year_id IS NULL) AND deleted_at IS NULL "
                        + "ORDER BY seq, (academic_year_id IS NULL)",
                rs -> {
                    while (rs.next()) {
                        bySeq.putIfAbsent(rs.getInt("seq"), rs.getBoolean("is_break"));
                    }
                }, schoolId, academicYearId);
        Set<Integer> teaching = new HashSet<>();
        Set<Long> adjacentPairs = new HashSet<>();
        Integer previousTeaching = null;
        for (Map.Entry<Integer, Boolean> period : bySeq.entrySet()) {
            if (Boolean.TRUE.equals(period.getValue())) {
                previousTeaching = null;
                continue;
            }
            teaching.add(period.getKey());
            if (previousTeaching != null) {
                adjacentPairs.add(TimetableRules.pairKey(previousTeaching, period.getKey()));
            }
            previousTeaching = period.getKey();
        }
        Map<String, Object> settings = tenantsApi.schoolSettings(schoolId);
        return new TimetableRules(teaching, adjacentPairs,
                intSet(settings.get(TimetableRules.SETTING_TEACHING_DAYS), TimetableRules.DEFAULT_TEACHING_DAYS),
                intValue(settings.get(TimetableRules.SETTING_MAX_CONSECUTIVE), TimetableRules.DEFAULT_MAX_CONSECUTIVE_PERIODS),
                teacherConsecutiveOverrides(settings));
    }

    private Set<Integer> intSet(Object value, Set<Integer> fallback) {
        if (!(value instanceof Collection<?> values)) {
            return fallback;
        }
        Set<Integer> resolved = new HashSet<>();
        for (Object item : values) {
            if (item instanceof Number number && number.intValue() >= 1 && number.intValue() <= 7) {
                resolved.add(number.intValue());
            }
        }
        return resolved.isEmpty() ? fallback : resolved;
    }

    private int intValue(Object value, int fallback) {
        return value instanceof Number number ? Math.max(1, number.intValue()) : fallback;
    }

    private Map<UUID, Integer> teacherConsecutiveOverrides(Map<String, Object> settings) {
        Object value = settings.get(TimetableRules.SETTING_TEACHER_MAX_CONSECUTIVE);
        if (!(value instanceof Map<?, ?> entries)) {
            return Map.of();
        }
        Map<UUID, Integer> overrides = new HashMap<>();
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            if (!(entry.getValue() instanceof Number number) || number.intValue() < 1) {
                continue;
            }
            try {
                overrides.put(UUID.fromString(String.valueOf(entry.getKey())), number.intValue());
            } catch (IllegalArgumentException ignored) {
                // A malformed key is not a reason to fail generation; the school default still applies.
            }
        }
        return overrides;
    }

    /**
     * Instant, readable feasibility check before the solver runs: a teacher whose weekly load
     * exceeds what the consecutive-lesson rule can physically place can never be scheduled, so the
     * caller is told which limit to change instead of waiting for an incomplete timetable.
     */
    private void checkTeacherCapacity(List<TimetableGenerator.Demand> demands, TimetableRules rules) {
        if (!rules.hasLayout()) {
            return;
        }
        Map<UUID, Integer> demanded = new HashMap<>();
        for (TimetableGenerator.Demand demand : demands) {
            if (demand.teacherMembershipId() != null) {
                demanded.merge(demand.teacherMembershipId(), demand.periodsPerWeek(), Integer::sum);
            }
        }
        int capacity = rules.weeklyCapacityInPeriods();
        for (Map.Entry<UUID, Integer> load : demanded.entrySet()) {
            if (load.getValue() > capacity) {
                throw ApiException.badRequest("teacher_capacity",
                        "A teacher is assigned " + load.getValue() + " periods but at most " + capacity
                                + " can be placed in a week without back-to-back lessons. Add double periods, "
                                + "reduce the load, or relax " + TimetableRules.SETTING_MAX_CONSECUTIVE + ".");
            }
        }
    }

    private List<UUID> classroomsFor(UUID schoolId) {
        return jdbc.query("SELECT id FROM classroom WHERE school_id = ? AND deleted_at IS NULL ORDER BY name",
                (rs, i) -> rs.getObject("id", UUID.class), schoolId);
    }

    private Map<UUID, UUID> membershipByUser(UUID schoolId) {
        Map<UUID, UUID> map = new HashMap<>();
        jdbc.query("SELECT id, user_id FROM school_membership WHERE school_id = ? AND status = 'active'",
                rs -> {
                    while (rs.next()) {
                        map.putIfAbsent(rs.getObject("user_id", UUID.class), rs.getObject("id", UUID.class));
                    }
                }, schoolId);
        return map;
    }

    private Map<UUID, Integer> teacherWeeklyLimits(UUID schoolId) {
        Map<UUID, Integer> limits = new HashMap<>();
        jdbc.query("SELECT sm.id AS membership_id, t.max_weekly_periods FROM school_membership sm "
                        + "JOIN teacher t ON t.school_id = sm.school_id AND t.user_id = sm.user_id "
                        + "AND t.deleted_at IS NULL "
                        + "WHERE sm.school_id = ? AND sm.status = 'active' AND t.max_weekly_periods IS NOT NULL",
                rs -> {
                    while (rs.next()) {
                        limits.put(rs.getObject("membership_id", UUID.class), rs.getInt("max_weekly_periods"));
                    }
                }, schoolId);
        return limits;
    }

    private TimetableVersion requireVersion(UUID id) {
        return versionRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("version_not_found", "Timetable version not found."));
    }

    private TimetableVersion requireDraftVersion(UUID id) {
        TimetableVersion version = requireVersion(id);
        if (!"draft".equals(version.getState())) {
            throw ApiException.conflict("version_not_draft",
                    "Only draft versions can be edited; copy the version to make changes.");
        }
        return version;
    }

    private List<TimetableEntry> entriesOf(UUID versionId) {
        return entryRepository.findBySchoolIdAndVersionIdAndDeletedAtIsNullOrderByDayOfWeekAscPeriodSeqAsc(
                schoolId(), versionId);
    }

    private UUID schoolId() {
        return TenantContext.schoolId();
    }

    // ----- views -----

    private VersionSummary toSummary(TimetableVersion version) {
        long entryCount = entryRepository
                .findBySchoolIdAndVersionIdAndDeletedAtIsNullOrderByDayOfWeekAscPeriodSeqAsc(schoolId(),
                        version.getId())
                .size();
        return new VersionSummary(version.getId(), version.getAcademicYearId(), version.getTermId(),
                version.getName(), version.getState(), version.getNote(), version.getSolverSeed(),
                version.getSolverScore(), version.getValidatedAt(), version.getPublishedAt(),
                version.getArchivedAt(), version.getCreatedAt(), entryCount);
    }

    private VersionView toView(TimetableVersion version, List<TimetableEntry> entries) {
        return new VersionView(toSummary(version), toEntryViews(entries), version.getConstraintReport());
    }

    private List<EntryView> toEntryViews(List<TimetableEntry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }
        Set<UUID> classIds = entries.stream().map(TimetableEntry::getClassGroupId).collect(Collectors.toSet());
        Set<UUID> subjectIds = entries.stream().map(TimetableEntry::getSubjectId).collect(Collectors.toSet());
        Set<UUID> membershipIds = entries.stream().map(TimetableEntry::getTeacherMembershipId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> classNames = new HashMap<>();
        academicsApi.findClasses(schoolId(), new ArrayList<>(classIds))
                .forEach(info -> classNames.put(info.id(), info.name()));
        Map<UUID, String> subjectNames = new HashMap<>();
        academicsApi.findSubjects(schoolId(), new ArrayList<>(subjectIds))
                .forEach(info -> subjectNames.put(info.id(), info.name()));
        Map<UUID, String> teacherNames = teacherNames(schoolId(), membershipIds);
        return entries.stream().map(entry -> new EntryView(entry.getId(), entry.getVersionId(),
                entry.getClassGroupId(), classNames.get(entry.getClassGroupId()),
                entry.getStreamId(), entry.getSubjectId(), subjectNames.get(entry.getSubjectId()),
                entry.getTeacherMembershipId(), teacherNames.get(entry.getTeacherMembershipId()),
                entry.getClassroomId(), entry.getDayOfWeek(), entry.getPeriodSeq(), entry.getSpan(),
                entry.isLocked(), entry.getVersion())).toList();
    }

    private Map<UUID, String> teacherNames(UUID schoolId, Set<UUID> membershipIds) {
        if (membershipIds.isEmpty()) {
            return Map.of();
        }
        String idList = membershipIds.stream().map(UUID::toString).collect(Collectors.joining(","));
        Map<UUID, String> names = new HashMap<>();
        jdbc.query("SELECT sm.id AS membership_id, u.display_name FROM school_membership sm "
                        + "JOIN app_user u ON u.id = sm.user_id "
                        + "WHERE sm.school_id = ? AND sm.id = ANY(string_to_array(?, ',')::uuid[])",
                rs -> {
                    while (rs.next()) {
                        names.put(rs.getObject("membership_id", UUID.class), rs.getString("display_name"));
                    }
                }, schoolId, idList);
        return names;
    }

    // ----- DTOs -----

    public record VersionSummary(UUID id, UUID academicYearId, UUID termId, String name, String state, String note,
                                 Long solverSeed, BigDecimal solverScore, Instant validatedAt, Instant publishedAt,
                                 Instant archivedAt, Instant createdAt, long entryCount) {
    }

    public record EntryView(UUID id, UUID versionId, UUID classGroupId, String className, UUID streamId,
                            UUID subjectId, String subjectName, UUID teacherMembershipId, String teacherName,
                            UUID classroomId, int dayOfWeek, int periodSeq, int span, boolean locked, int version) {
    }

    public record VersionView(VersionSummary summary, List<EntryView> entries,
                              Map<String, Object> constraintReport) {
    }

    public record PublishedView(VersionSummary summary, List<EntryView> entries) {
    }

    public record ValidationView(UUID versionId, List<TimetableValidator.Issue> issues, int hardCount,
                                 int softCount, double score, Instant generatedAt) {
    }

    public record EntryInput(UUID classGroupId, UUID streamId, UUID subjectId, UUID teacherMembershipId,
                             UUID classroomId, Integer dayOfWeek, Integer periodSeq, Integer span, Boolean locked) {
    }

    public record GenerateInput(Long seed, Integer attempts, List<Integer> days) {
    }
}
