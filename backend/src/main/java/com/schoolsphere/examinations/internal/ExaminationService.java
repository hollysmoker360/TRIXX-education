package com.schoolsphere.examinations.internal;

import com.schoolsphere.audit.api.AuditLogService;
import com.schoolsphere.common.api.PageResponse;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.security.SecurityUtils;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import com.schoolsphere.people.api.PeopleApi;
import com.schoolsphere.people.api.PeopleApi.StudentRef;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Formal examinations: assessment authoring with review and approval, scheduled sittings with
 * registered candidates, marking and moderation, and result publication. Every state change that
 * affects marks or results is written to the audit log.
 */
@Service
@TenantScoped
public class ExaminationService {

    static final List<String> TYPES = List.of("EXAM", "CAT", "MOCK", "PRACTICAL");
    static final List<String> QUEUE_STATUSES = List.of("PENDING", "MARKED");

    private final ExamAssessmentRepository assessmentRepository;
    private final ExamSessionRepository sessionRepository;
    private final ExamScriptRepository scriptRepository;
    private final PeopleApi peopleApi;
    private final AuditLogService auditLogService;

    public ExaminationService(ExamAssessmentRepository assessmentRepository, ExamSessionRepository sessionRepository,
                              ExamScriptRepository scriptRepository, PeopleApi peopleApi,
                              AuditLogService auditLogService) {
        this.assessmentRepository = assessmentRepository;
        this.sessionRepository = sessionRepository;
        this.scriptRepository = scriptRepository;
        this.peopleApi = peopleApi;
        this.auditLogService = auditLogService;
    }

    // ---------------------------------------------------------------------------------------------
    // Assessments
    // ---------------------------------------------------------------------------------------------

    public PageResponse<AssessmentView> listAssessments(String status, int page, int size) {
        return PageResponse.of(assessmentRepository.search(schoolId(), blankToNull(status), pageRequest(page, size))
                .map(this::toAssessmentView));
    }

    public AssessmentView getAssessment(UUID id) {
        return toAssessmentView(requireAssessment(id));
    }

    public AssessmentView createAssessment(AssessmentInput input) {
        ExamAssessment assessment = new ExamAssessment();
        assessment.setSchoolId(schoolId());
        assessment.setCreatedBy(TenantContext.actorUserId());
        apply(assessment, input, true);
        ExamAssessment saved = assessmentRepository.save(assessment);
        auditLogService.record("exam.assessment.created", "exam_assessment", saved.getId(), null,
                toAssessmentView(saved));
        return toAssessmentView(saved);
    }

    public AssessmentView updateAssessment(UUID id, AssessmentInput input) {
        ExamAssessment assessment = requireAssessment(id);
        if (!"DRAFT".equals(assessment.getStatus())) {
            throw ApiException.conflict("assessment_locked", "Only draft assessments can be edited.");
        }
        AssessmentView before = toAssessmentView(assessment);
        apply(assessment, input, false);
        ExamAssessment saved = assessmentRepository.save(assessment);
        auditLogService.record("exam.assessment.updated", "exam_assessment", id, before, toAssessmentView(saved));
        return toAssessmentView(saved);
    }

    public AssessmentView submitForReview(UUID id) {
        return transition(id, "DRAFT", "IN_REVIEW", "exam.assessment.submitted");
    }

    public AssessmentView returnToDraft(UUID id) {
        return transition(id, "IN_REVIEW", "DRAFT", "exam.assessment.returned");
    }

    public AssessmentView approve(UUID id) {
        ExamAssessment assessment = requireStatus(requireAssessment(id), "IN_REVIEW");
        AssessmentView before = toAssessmentView(assessment);
        assessment.setStatus("APPROVED");
        assessment.setApprovedBy(TenantContext.actorUserId());
        assessment.setApprovedAt(Instant.now());
        return save(assessment, before, "exam.assessment.approved");
    }

    public AssessmentView publish(UUID id) {
        return transition(id, "APPROVED", "PUBLISHED", "exam.assessment.published");
    }

    public AssessmentView archive(UUID id) {
        ExamAssessment assessment = requireAssessment(id);
        if ("ARCHIVED".equals(assessment.getStatus())) {
            return toAssessmentView(assessment);
        }
        AssessmentView before = toAssessmentView(assessment);
        assessment.setStatus("ARCHIVED");
        return save(assessment, before, "exam.assessment.archived");
    }

    public void deleteAssessment(UUID id) {
        ExamAssessment assessment = requireAssessment(id);
        if (!"DRAFT".equals(assessment.getStatus())
                || sessionRepository.existsBySchoolIdAndAssessmentIdAndDeletedAtIsNull(schoolId(), id)) {
            throw ApiException.conflict("assessment_in_use",
                    "Only draft assessments without sittings can be deleted; archive it instead.");
        }
        AssessmentView before = toAssessmentView(assessment);
        assessment.setDeletedAt(Instant.now());
        assessment.setUpdatedBy(TenantContext.actorUserId());
        assessmentRepository.save(assessment);
        auditLogService.record("exam.assessment.deleted", "exam_assessment", id, before, null);
    }

    // ---------------------------------------------------------------------------------------------
    // Sittings and candidates
    // ---------------------------------------------------------------------------------------------

    public PageResponse<SessionView> listSessions(UUID assessmentId, int page, int size) {
        Page<ExamSession> result = sessionRepository.search(schoolId(), assessmentId, pageRequest(page, size));
        Map<UUID, ExamAssessment> assessments = assessmentsById(
                result.getContent().stream().map(ExamSession::getAssessmentId).toList());
        return PageResponse.of(result.map(s -> toSessionView(s, assessments.get(s.getAssessmentId()))));
    }

    public SessionView createSession(UUID assessmentId, String title, Instant startsAt, Instant endsAt, String venue,
                                     List<UUID> studentIds) {
        ExamAssessment assessment = requireAssessment(assessmentId);
        if (!List.of("APPROVED", "PUBLISHED").contains(assessment.getStatus())) {
            throw ApiException.conflict("assessment_not_approved",
                    "Only approved or published assessments can be scheduled.");
        }
        validateWindow(assessment, startsAt, endsAt);
        ExamSession session = new ExamSession();
        session.setSchoolId(schoolId());
        session.setAssessmentId(assessmentId);
        session.setTitle(title == null || title.isBlank() ? assessment.getTitle() : title.trim());
        session.setStartsAt(startsAt);
        session.setEndsAt(endsAt);
        session.setVenue(blankToNull(venue));
        session.setCreatedBy(TenantContext.actorUserId());
        session.setUpdatedBy(TenantContext.actorUserId());
        ExamSession saved = sessionRepository.save(session);

        List<UUID> candidates = studentIds == null || studentIds.isEmpty()
                ? classCandidates(assessment)
                : studentIds;
        register(saved, candidates);
        auditLogService.record("exam.session.scheduled", "exam_session", saved.getId(), null,
                Map.of("assessmentId", assessmentId, "candidates", candidates.size()));
        return toSessionView(saved, assessment);
    }

    public SessionView registerCandidates(UUID sessionId, List<UUID> studentIds) {
        ExamSession session = requireSession(sessionId);
        if (!"SCHEDULED".equals(session.getStatus())) {
            throw ApiException.conflict("session_not_scheduled", "Candidates can only be added before the sitting starts.");
        }
        ExamAssessment assessment = requireAssessment(session.getAssessmentId());
        List<UUID> candidates = studentIds == null || studentIds.isEmpty() ? classCandidates(assessment) : studentIds;
        int added = register(session, candidates);
        auditLogService.record("exam.session.candidates_registered", "exam_session", sessionId, null,
                Map.of("added", added));
        return toSessionView(session, assessment);
    }

    public SessionView startSession(UUID id) {
        return sessionTransition(id, Set.of("SCHEDULED"), "IN_PROGRESS", "exam.session.started");
    }

    public SessionView completeSession(UUID id) {
        return sessionTransition(id, Set.of("IN_PROGRESS"), "COMPLETED", "exam.session.completed");
    }

    public SessionView cancelSession(UUID id) {
        SessionView view = sessionTransition(id, Set.of("SCHEDULED", "IN_PROGRESS"), "CANCELLED",
                "exam.session.cancelled");
        // Cancelled candidates are released so they can be registered for another sitting.
        for (ExamScript script : scriptRepository.findBySchoolIdAndSessionIdAndDeletedAtIsNull(schoolId(), id)) {
            script.setDeletedAt(Instant.now());
            script.setUpdatedBy(TenantContext.actorUserId());
            scriptRepository.save(script);
        }
        return view;
    }

    public List<ScriptView> sessionCandidates(UUID sessionId) {
        ExamSession session = requireSession(sessionId);
        ExamAssessment assessment = requireAssessment(session.getAssessmentId());
        return toScriptViews(scriptRepository.findBySchoolIdAndSessionIdAndDeletedAtIsNull(schoolId(), sessionId),
                Map.of(assessment.getId(), assessment));
    }

    // ---------------------------------------------------------------------------------------------
    // Marking and moderation
    // ---------------------------------------------------------------------------------------------

    public PageResponse<ScriptView> markingQueue(int page, int size) {
        Page<ExamScript> result = scriptRepository.markingQueue(schoolId(), QUEUE_STATUSES, pageRequest(page, size));
        Map<UUID, ExamAssessment> assessments = assessmentsById(
                result.getContent().stream().map(ExamScript::getAssessmentId).toList());
        List<ScriptView> items = toScriptViews(result.getContent(), assessments);
        return PageResponse.of(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    public ScriptView markScript(UUID scriptId, BigDecimal marks, boolean absent, String remarks) {
        ExamScript script = requireScript(scriptId);
        ExamAssessment assessment = requireMarkable(script);
        if ("MODERATED".equals(script.getStatus())) {
            throw ApiException.conflict("script_moderated", "This script has been moderated and is locked.");
        }
        Map<String, Object> before = marksSnapshot(script);
        if (absent) {
            script.setStatus("ABSENT");
            script.setMarks(null);
        } else {
            script.setMarks(validateMarks(assessment, marks));
            script.setStatus("MARKED");
        }
        script.setRemarks(blankToNull(remarks));
        script.setMarkedBy(TenantContext.actorUserId());
        script.setMarkedAt(Instant.now());
        script.setUpdatedBy(TenantContext.actorUserId());
        ExamScript saved = scriptRepository.save(script);
        auditLogService.record("exam.script.marked", "exam_script", scriptId, before, marksSnapshot(saved));
        return toScriptViews(List.of(saved), Map.of(assessment.getId(), assessment)).get(0);
    }

    public ScriptView moderateScript(UUID scriptId, BigDecimal marks, String remarks) {
        ExamScript script = requireScript(scriptId);
        ExamAssessment assessment = requireMarkable(script);
        if (!"MARKED".equals(script.getStatus())) {
            throw ApiException.conflict("script_not_marked", "Only marked scripts can be moderated.");
        }
        if (Objects.equals(script.getMarkedBy(), TenantContext.actorUserId())) {
            throw ApiException.forbidden("self_moderation", "A different examiner must moderate this script.");
        }
        Map<String, Object> before = marksSnapshot(script);
        if (marks != null) {
            script.setMarks(validateMarks(assessment, marks));
        }
        if (remarks != null && !remarks.isBlank()) {
            script.setRemarks(remarks.trim());
        }
        script.setStatus("MODERATED");
        script.setModeratedBy(TenantContext.actorUserId());
        script.setModeratedAt(Instant.now());
        script.setUpdatedBy(TenantContext.actorUserId());
        ExamScript saved = scriptRepository.save(script);
        auditLogService.record("exam.script.moderated", "exam_script", scriptId, before, marksSnapshot(saved));
        return toScriptViews(List.of(saved), Map.of(assessment.getId(), assessment)).get(0);
    }

    // ---------------------------------------------------------------------------------------------
    // Results
    // ---------------------------------------------------------------------------------------------

    public PageResponse<ResultView> listResults(int page, int size) {
        List<UUID> ids = scriptRepository.assessmentsWithCandidates(schoolId());
        Map<UUID, ExamAssessment> assessments = assessmentsById(ids);
        Map<UUID, List<ExamScript>> scripts = ids.isEmpty() ? Map.of()
                : scriptRepository.findBySchoolIdAndAssessmentIdInAndDeletedAtIsNull(schoolId(), ids).stream()
                .collect(Collectors.groupingBy(ExamScript::getAssessmentId));
        List<ResultView> all = assessments.values().stream()
                .sorted(Comparator.comparing(ExamAssessment::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(a -> summarise(a, scripts.getOrDefault(a.getId(), List.of())))
                .toList();
        PageRequest request = pageRequest(page, size);
        int from = (int) Math.min(request.getOffset(), all.size());
        int to = Math.min(from + request.getPageSize(), all.size());
        return PageResponse.of(all.subList(from, to), request.getPageNumber(), request.getPageSize(), all.size());
    }

    public List<ScriptView> resultCandidates(UUID assessmentId) {
        ExamAssessment assessment = requireAssessment(assessmentId);
        List<ScriptView> views = toScriptViews(
                scriptRepository.findBySchoolIdAndAssessmentIdAndDeletedAtIsNull(schoolId(), assessmentId),
                Map.of(assessmentId, assessment));
        return views.stream()
                .sorted(Comparator.comparing(ScriptView::marks, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public ResultView publishResults(UUID assessmentId) {
        ExamAssessment assessment = requireAssessment(assessmentId);
        if (assessment.getResultsPublishedAt() != null) {
            throw ApiException.conflict("results_already_published", "Results have already been published.");
        }
        List<ExamScript> scripts = scriptRepository.findBySchoolIdAndAssessmentIdAndDeletedAtIsNull(schoolId(),
                assessmentId);
        ResultView summary = summarise(assessment, scripts);
        if (summary.candidates() == 0 || summary.pending() > 0) {
            throw ApiException.conflict("results_not_ready",
                    "Every registered candidate must be marked or recorded absent before publishing.");
        }
        assessment.setResultsPublishedAt(Instant.now());
        assessment.setResultsPublishedBy(TenantContext.actorUserId());
        assessment.setUpdatedBy(TenantContext.actorUserId());
        ExamAssessment saved = assessmentRepository.save(assessment);
        ResultView after = summarise(saved, scripts);
        auditLogService.record("exam.results.published", "exam_assessment", assessmentId, summary, after);
        return after;
    }

    /** The signed-in student's published results. */
    public List<MyResultView> myResults() {
        StudentRef student = peopleApi.studentForUser(schoolId(), SecurityUtils.requireUserId())
                .orElseThrow(() -> ApiException.forbidden("not_a_student", "Only students have examination results."));
        List<ExamScript> scripts = scriptRepository.findBySchoolIdAndStudentIdAndDeletedAtIsNull(schoolId(),
                student.id());
        Map<UUID, ExamAssessment> assessments = assessmentsById(
                scripts.stream().map(ExamScript::getAssessmentId).toList());
        return scripts.stream()
                .filter(s -> assessments.containsKey(s.getAssessmentId())
                        && assessments.get(s.getAssessmentId()).getResultsPublishedAt() != null)
                .map(s -> {
                    ExamAssessment a = assessments.get(s.getAssessmentId());
                    return new MyResultView(a.getId(), a.getTitle(), a.getAssessmentType(), s.getStatus(),
                            s.getMarks(), a.getTotalMarks(), passed(a, s), a.getResultsPublishedAt());
                })
                .sorted(Comparator.comparing(MyResultView::publishedAt).reversed())
                .toList();
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private void apply(ExamAssessment assessment, AssessmentInput input, boolean creating) {
        if (creating || input.title() != null) {
            if (input.title() == null || input.title().isBlank()) {
                throw ApiException.badRequest("title_required", "A title is required.");
            }
            assessment.setTitle(input.title().trim());
        }
        if (input.assessmentType() != null) {
            String type = input.assessmentType().trim().toUpperCase();
            if (!TYPES.contains(type)) {
                throw ApiException.badRequest("invalid_assessment_type",
                        "Assessment type must be one of " + String.join(", ", TYPES) + ".");
            }
            assessment.setAssessmentType(type);
        }
        if (input.subjectId() != null) {
            assessment.setSubjectId(input.subjectId());
        }
        if (input.classGroupId() != null) {
            assessment.setClassGroupId(input.classGroupId());
        }
        if (input.termId() != null) {
            assessment.setTermId(input.termId());
        }
        if (creating || input.totalMarks() != null) {
            if (input.totalMarks() == null || input.totalMarks().signum() <= 0) {
                throw ApiException.badRequest("invalid_total_marks", "Total marks must be greater than zero.");
            }
            assessment.setTotalMarks(input.totalMarks());
        }
        if (input.passMark() != null) {
            assessment.setPassMark(input.passMark());
        }
        BigDecimal pass = assessment.getPassMark();
        if (pass != null && (pass.signum() < 0 || pass.compareTo(assessment.getTotalMarks()) > 0)) {
            throw ApiException.badRequest("invalid_pass_mark", "Pass mark must be between 0 and the total marks.");
        }
        if (input.durationMinutes() != null) {
            if (input.durationMinutes() <= 0) {
                throw ApiException.badRequest("invalid_duration", "Duration must be a positive number of minutes.");
            }
            assessment.setDurationMinutes(input.durationMinutes());
        }
        if (input.instructions() != null) {
            assessment.setInstructions(blankToNull(input.instructions()));
        }
        assessment.setUpdatedBy(TenantContext.actorUserId());
    }

    private AssessmentView transition(UUID id, String from, String to, String auditAction) {
        ExamAssessment assessment = requireStatus(requireAssessment(id), from);
        AssessmentView before = toAssessmentView(assessment);
        assessment.setStatus(to);
        return save(assessment, before, auditAction);
    }

    private AssessmentView save(ExamAssessment assessment, AssessmentView before, String auditAction) {
        assessment.setUpdatedBy(TenantContext.actorUserId());
        ExamAssessment saved = assessmentRepository.save(assessment);
        AssessmentView after = toAssessmentView(saved);
        auditLogService.record(auditAction, "exam_assessment", saved.getId(), before, after);
        return after;
    }

    private ExamAssessment requireStatus(ExamAssessment assessment, String expected) {
        if (!expected.equals(assessment.getStatus())) {
            throw ApiException.conflict("invalid_assessment_state",
                    "This action needs an assessment in " + expected + " state; it is " + assessment.getStatus() + ".");
        }
        return assessment;
    }

    private SessionView sessionTransition(UUID id, Set<String> from, String to, String auditAction) {
        ExamSession session = requireSession(id);
        if (!from.contains(session.getStatus())) {
            throw ApiException.conflict("invalid_session_state",
                    "The sitting is " + session.getStatus() + " and cannot move to " + to + ".");
        }
        String previous = session.getStatus();
        session.setStatus(to);
        session.setUpdatedBy(TenantContext.actorUserId());
        ExamSession saved = sessionRepository.save(session);
        auditLogService.record(auditAction, "exam_session", id, Map.of("status", previous), Map.of("status", to));
        return toSessionView(saved, requireAssessment(saved.getAssessmentId()));
    }

    private void validateWindow(ExamAssessment assessment, Instant startsAt, Instant endsAt) {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw ApiException.badRequest("invalid_window", "The sitting must end after it starts.");
        }
        Integer duration = assessment.getDurationMinutes();
        if (duration != null && Duration.between(startsAt, endsAt).toMinutes() < duration) {
            throw ApiException.badRequest("window_too_short",
                    "The sitting is shorter than the assessment's " + duration + "-minute duration.");
        }
    }

    private List<UUID> classCandidates(ExamAssessment assessment) {
        if (assessment.getClassGroupId() == null) {
            return List.of();
        }
        return peopleApi.listStudentsByClass(schoolId(), assessment.getClassGroupId(), null).stream()
                .map(StudentRef::id).toList();
    }

    /** Registers each student once per assessment; returns how many new candidates were added. */
    private int register(ExamSession session, List<UUID> studentIds) {
        Set<UUID> requested = new LinkedHashSet<>(studentIds);
        if (requested.isEmpty()) {
            return 0;
        }
        Set<UUID> known = peopleApi.findStudents(schoolId(), new ArrayList<>(requested)).stream()
                .map(StudentRef::id).collect(Collectors.toSet());
        Set<UUID> unknown = new HashSet<>(requested);
        unknown.removeAll(known);
        if (!unknown.isEmpty()) {
            throw ApiException.badRequest("unknown_students", unknown.size() + " candidate(s) are not students of this school.");
        }
        Set<UUID> already = scriptRepository
                .findBySchoolIdAndAssessmentIdAndDeletedAtIsNull(schoolId(), session.getAssessmentId()).stream()
                .map(ExamScript::getStudentId).collect(Collectors.toSet());
        long seat = scriptRepository.countBySchoolIdAndSessionIdAndDeletedAtIsNull(schoolId(), session.getId());
        int added = 0;
        for (UUID studentId : requested) {
            if (already.contains(studentId)) {
                continue;
            }
            ExamScript script = new ExamScript();
            script.setSchoolId(schoolId());
            script.setSessionId(session.getId());
            script.setAssessmentId(session.getAssessmentId());
            script.setStudentId(studentId);
            script.setSeatNumber(String.format("%03d", ++seat));
            script.setCreatedBy(TenantContext.actorUserId());
            script.setUpdatedBy(TenantContext.actorUserId());
            scriptRepository.save(script);
            added++;
        }
        return added;
    }

    private ExamAssessment requireMarkable(ExamScript script) {
        ExamSession session = requireSession(script.getSessionId());
        if (!"COMPLETED".equals(session.getStatus())) {
            throw ApiException.conflict("session_not_completed", "Scripts can be marked once the sitting is completed.");
        }
        ExamAssessment assessment = requireAssessment(script.getAssessmentId());
        if (assessment.getResultsPublishedAt() != null) {
            throw ApiException.conflict("results_published", "Results are published; marks can no longer change.");
        }
        return assessment;
    }

    private BigDecimal validateMarks(ExamAssessment assessment, BigDecimal marks) {
        if (marks == null || marks.signum() < 0 || marks.compareTo(assessment.getTotalMarks()) > 0) {
            throw ApiException.badRequest("invalid_marks",
                    "Marks must be between 0 and " + assessment.getTotalMarks().stripTrailingZeros().toPlainString() + ".");
        }
        return marks;
    }

    private ResultView summarise(ExamAssessment assessment, List<ExamScript> scripts) {
        int pending = 0;
        int absent = 0;
        int passedCount = 0;
        List<BigDecimal> marks = new ArrayList<>();
        for (ExamScript script : scripts) {
            switch (script.getStatus()) {
                case "PENDING" -> pending++;
                case "ABSENT" -> absent++;
                default -> {
                    marks.add(script.getMarks());
                    if (Boolean.TRUE.equals(passed(assessment, script))) {
                        passedCount++;
                    }
                }
            }
        }
        BigDecimal average = marks.isEmpty() ? null
                : marks.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(marks.size()), 2, RoundingMode.HALF_UP);
        BigDecimal highest = marks.stream().max(Comparator.naturalOrder()).orElse(null);
        BigDecimal lowest = marks.stream().min(Comparator.naturalOrder()).orElse(null);
        Double passRate = assessment.getPassMark() == null || marks.isEmpty() ? null
                : Math.round(passedCount * 1000.0 / marks.size()) / 10.0;
        String status = assessment.getResultsPublishedAt() != null ? "PUBLISHED"
                : !scripts.isEmpty() && pending == 0 ? "READY" : "PENDING_MARKING";
        return new ResultView(assessment.getId(), assessment.getTitle(), assessment.getAssessmentType(),
                assessment.getTotalMarks(), scripts.size(), marks.size(), absent, pending, average, highest, lowest,
                passRate, status, assessment.getResultsPublishedAt());
    }

    private Boolean passed(ExamAssessment assessment, ExamScript script) {
        if (assessment.getPassMark() == null || script.getMarks() == null) {
            return null;
        }
        return script.getMarks().compareTo(assessment.getPassMark()) >= 0;
    }

    private List<ScriptView> toScriptViews(List<ExamScript> scripts, Map<UUID, ExamAssessment> assessments) {
        if (scripts.isEmpty()) {
            return List.of();
        }
        Map<UUID, StudentRef> students = peopleApi.findStudents(schoolId(),
                        scripts.stream().map(ExamScript::getStudentId).distinct().toList()).stream()
                .collect(Collectors.toMap(StudentRef::id, Function.identity(), (a, b) -> a));
        return scripts.stream().map(s -> {
            ExamAssessment a = assessments.get(s.getAssessmentId());
            StudentRef student = students.get(s.getStudentId());
            return new ScriptView(s.getId(), s.getAssessmentId(), a == null ? null : a.getTitle(), s.getSessionId(),
                    s.getStudentId(), student == null ? null : student.fullName(),
                    student == null ? null : student.admissionNumber(), s.getSeatNumber(), s.getStatus(),
                    s.getMarks(), a == null ? null : a.getTotalMarks(), s.getRemarks(), s.getMarkedAt(),
                    s.getModeratedAt());
        }).toList();
    }

    private Map<String, Object> marksSnapshot(ExamScript script) {
        Map<String, Object> snapshot = new java.util.HashMap<>();
        snapshot.put("status", script.getStatus());
        snapshot.put("marks", script.getMarks());
        return snapshot;
    }

    private Map<UUID, ExamAssessment> assessmentsById(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return assessmentRepository.findBySchoolIdAndIdInAndDeletedAtIsNull(schoolId(), new HashSet<>(ids)).stream()
                .collect(Collectors.toMap(ExamAssessment::getId, Function.identity()));
    }

    private ExamAssessment requireAssessment(UUID id) {
        return assessmentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("assessment_not_found", "Assessment not found."));
    }

    private ExamSession requireSession(UUID id) {
        return sessionRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("session_not_found", "Exam sitting not found."));
    }

    private ExamScript requireScript(UUID id) {
        return scriptRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("script_not_found", "Script not found."));
    }

    private AssessmentView toAssessmentView(ExamAssessment a) {
        return new AssessmentView(a.getId(), a.getTitle(), a.getAssessmentType(), a.getSubjectId(),
                a.getClassGroupId(), a.getTermId(), a.getTotalMarks(), a.getPassMark(), a.getDurationMinutes(),
                a.getInstructions(), a.getStatus(), a.getApprovedAt(), a.getResultsPublishedAt(), a.getCreatedAt());
    }

    private SessionView toSessionView(ExamSession s, ExamAssessment a) {
        return new SessionView(s.getId(), s.getAssessmentId(), a == null ? null : a.getTitle(), s.getTitle(),
                s.getStartsAt(), s.getEndsAt(), s.getVenue(), s.getStatus(),
                scriptRepository.countBySchoolIdAndSessionIdAndDeletedAtIsNull(schoolId(), s.getId()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    private static UUID schoolId() {
        return TenantContext.schoolId();
    }

    public record AssessmentInput(String title, String assessmentType, UUID subjectId, UUID classGroupId, UUID termId,
                                  BigDecimal totalMarks, BigDecimal passMark, Integer durationMinutes,
                                  String instructions) {
    }

    public record AssessmentView(UUID id, String title, String assessmentType, UUID subjectId, UUID classGroupId,
                                 UUID termId, BigDecimal totalMarks, BigDecimal passMark, Integer durationMinutes,
                                 String instructions, String status, Instant approvedAt, Instant resultsPublishedAt,
                                 Instant createdAt) {
    }

    public record SessionView(UUID id, UUID assessmentId, String assessmentTitle, String title, Instant startsAt,
                              Instant endsAt, String venue, String status, long candidates) {
    }

    public record ScriptView(UUID id, UUID assessmentId, String assessmentTitle, UUID sessionId, UUID studentId,
                             String candidateName, String admissionNumber, String seatNumber, String status,
                             BigDecimal marks, BigDecimal totalMarks, String remarks, Instant markedAt,
                             Instant moderatedAt) {
    }

    public record ResultView(UUID id, String assessmentTitle, String assessmentType, BigDecimal totalMarks,
                             int candidates, int marked, int absent, int pending, BigDecimal average,
                             BigDecimal highest, BigDecimal lowest, Double passRate, String status,
                             Instant publishedAt) {
    }

    public record MyResultView(UUID assessmentId, String assessmentTitle, String assessmentType, String status,
                               BigDecimal marks, BigDecimal totalMarks, Boolean passed, Instant publishedAt) {
    }
}
