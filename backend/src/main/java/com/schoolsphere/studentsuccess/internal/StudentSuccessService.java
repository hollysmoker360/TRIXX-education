package com.schoolsphere.studentsuccess.internal;

import com.schoolsphere.studentsuccess.api.StudentSuccessApi;
import com.schoolsphere.studentsuccess.internal.entity.InterventionPlan;
import com.schoolsphere.studentsuccess.internal.entity.ProgressNote;
import com.schoolsphere.studentsuccess.internal.entity.RiskIndicator;
import com.schoolsphere.studentsuccess.internal.entity.SupportGoal;
import com.schoolsphere.studentsuccess.internal.entity.SupportReferral;
import com.schoolsphere.studentsuccess.internal.entity.StudentProgressProfile;
import com.schoolsphere.studentsuccess.internal.repository.InterventionPlanRepository;
import com.schoolsphere.studentsuccess.internal.repository.ProgressNoteRepository;
import com.schoolsphere.studentsuccess.internal.repository.RiskIndicatorRepository;
import com.schoolsphere.studentsuccess.internal.repository.SupportGoalRepository;
import com.schoolsphere.studentsuccess.internal.repository.SupportReferralRepository;
import com.schoolsphere.studentsuccess.internal.repository.StudentProgressProfileRepository;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.security.SecurityUtils;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import com.schoolsphere.people.api.PeopleApi;
import com.schoolsphere.tenants.api.PermissionCatalog;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@TenantScoped
public class StudentSuccessService implements StudentSuccessApi {

    private static final Set<String> INDICATOR_TYPES = Set.of(
            "attendance_drop", "grade_decline", "missing_work", "teacher_concern", "guardian_concern");
    private static final Set<String> SEVERITIES = Set.of("low", "moderate", "high");
    private static final Set<String> REFERRAL_TYPES = Set.of(
            "academic", "behavioral", "health", "learning_support", "family");
    private static final Set<String> PRIORITIES = Set.of("routine", "urgent", "immediate");
    private static final Set<String> REFERRAL_STATUSES = Set.of("open", "in_progress", "completed", "declined");
    private static final Set<String> PLAN_STATUSES = Set.of("active", "paused", "completed", "discontinued");
    private static final Set<String> NOTE_TYPES = Set.of("observation", "review", "outcome", "guardian_communication");

    private final StudentProgressProfileRepository profileRepository;
    private final RiskIndicatorRepository indicatorRepository;
    private final SupportReferralRepository referralRepository;
    private final InterventionPlanRepository planRepository;
    private final SupportGoalRepository goalRepository;
    private final ProgressNoteRepository noteRepository;
    private final PeopleApi peopleApi;

    public StudentSuccessService(StudentProgressProfileRepository profileRepository,
                                 RiskIndicatorRepository indicatorRepository,
                                 SupportReferralRepository referralRepository,
                                 InterventionPlanRepository planRepository,
                                 SupportGoalRepository goalRepository,
                                 ProgressNoteRepository noteRepository,
                                 PeopleApi peopleApi) {
        this.profileRepository = profileRepository;
        this.indicatorRepository = indicatorRepository;
        this.referralRepository = referralRepository;
        this.planRepository = planRepository;
        this.goalRepository = goalRepository;
        this.noteRepository = noteRepository;
        this.peopleApi = peopleApi;
    }

    // ----- profile -----

    @Override
    @Transactional(readOnly = true)
    public StudentProfileDTO getProfile(UUID studentId) {
        UUID schoolId = TenantContext.schoolId();
        StudentProgressProfile profile = profileRepository.findBySchoolIdAndStudentIdAndDeletedAtIsNull(
                schoolId, studentId)
                .orElseGet(() -> {
                    StudentProgressProfile p = new StudentProgressProfile();
                    p.setSchoolId(schoolId);
                    p.setStudentId(studentId);
                    p.setOverallRiskLevel("low");
                    p.setAttendanceConcern(false);
                    p.setAcademicDecline(false);
                    p.setIncompleteWork(false);
                    p.setCreatedBy(TenantContext.actorUserId());
                    return profileRepository.save(p);
                });
        String name = studentName(studentId);
        String admission = admissionNumber(studentId);
        return new StudentProfileDTO(
                profile.getId(), profile.getStudentId(), name, admission,
                profile.getOverallRiskLevel(), profile.getAttendanceConcern(),
                profile.getAcademicDecline(), profile.getIncompleteWork(),
                profile.getLastReviewedAt());
    }

    // ----- indicators -----

    @Override
    @Transactional(readOnly = true)
    public List<RiskIndicatorDTO> listIndicators(UUID studentId, Boolean acknowledged, Boolean resolved) {
        UUID schoolId = TenantContext.schoolId();
        List<RiskIndicator> list;
        if (acknowledged != null && resolved != null) {
            list = indicatorRepository.findBySchoolIdAndAcknowledgedAndResolvedAndDeletedAtIsNull(
                    schoolId, acknowledged, resolved).stream()
                    .filter(i -> i.getStudentId().equals(studentId))
                    .collect(Collectors.toList());
        } else {
            list = indicatorRepository.findBySchoolIdAndStudentIdAndDeletedAtIsNull(schoolId, studentId);
        }
        return list.stream().map(this::toIndicatorDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RiskIndicatorDTO createIndicator(RiskIndicatorInput input) {
        UUID schoolId = TenantContext.schoolId();
        if (input.studentId() == null) {
            throw ApiException.badRequest("student_required", "A student is required.");
        }
        if (input.indicatorType() == null || !INDICATOR_TYPES.contains(input.indicatorType())) {
            throw ApiException.badRequest("invalid_type", "Unknown indicator type.");
        }
        if (input.severity() == null || !SEVERITIES.contains(input.severity())) {
            throw ApiException.badRequest("invalid_severity", "Invalid severity.");
        }
        if (input.description() == null || input.description().isBlank()) {
            throw ApiException.badRequest("description_required", "A description is required.");
        }

        RiskIndicator indicator = new RiskIndicator();
        indicator.setSchoolId(schoolId);
        indicator.setStudentId(input.studentId());
        indicator.setIndicatorType(input.indicatorType());
        indicator.setSeverity(input.severity());
        indicator.setDescription(input.description());
        indicator.setSourceModule(input.sourceModule());
        indicator.setSourceEntityId(input.sourceEntityId());
        indicator.setAcknowledged(false);
        indicator.setResolved(false);
        indicator.setCreatedBy(TenantContext.actorUserId());
        indicator = indicatorRepository.save(indicator);

        return toIndicatorDto(indicator);
    }

    @Override
    @Transactional
    public RiskIndicatorDTO acknowledgeIndicator(UUID indicatorId) {
        UUID schoolId = TenantContext.schoolId();
        RiskIndicator indicator = indicatorRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, indicatorId)
                .orElseThrow(() -> ApiException.notFound("indicator_not_found", "Indicator not found."));
        indicator.setAcknowledged(true);
        indicator.setAcknowledgedBy(TenantContext.actorMembershipId());
        indicator.setAcknowledgedAt(LocalDateTime.now());
        indicator.setUpdatedBy(TenantContext.actorUserId());
        indicatorRepository.save(indicator);
        return toIndicatorDto(indicator);
    }

    @Override
    @Transactional
    public RiskIndicatorDTO resolveIndicator(UUID indicatorId) {
        UUID schoolId = TenantContext.schoolId();
        RiskIndicator indicator = indicatorRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, indicatorId)
                .orElseThrow(() -> ApiException.notFound("indicator_not_found", "Indicator not found."));
        indicator.setResolved(true);
        indicator.setResolvedAt(LocalDateTime.now());
        indicator.setUpdatedBy(TenantContext.actorUserId());
        indicatorRepository.save(indicator);
        return toIndicatorDto(indicator);
    }

    // ----- referrals -----

    @Override
    @Transactional(readOnly = true)
    public List<SupportReferralDTO> listReferrals(UUID studentId, String status) {
        UUID schoolId = TenantContext.schoolId();
        List<SupportReferral> list;
        if (status != null) {
            list = referralRepository.findBySchoolIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(schoolId, status).stream()
                    .filter(r -> r.getStudentId().equals(studentId))
                    .collect(Collectors.toList());
        } else {
            list = referralRepository.findBySchoolIdAndStudentIdAndDeletedAtIsNull(schoolId, studentId);
        }
        return list.stream().map(this::toReferralDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SupportReferralDTO createReferral(SupportReferralInput input) {
        UUID schoolId = TenantContext.schoolId();
        if (input.studentId() == null) {
            throw ApiException.badRequest("student_required", "A student is required.");
        }
        if (input.referralType() == null || !REFERRAL_TYPES.contains(input.referralType())) {
            throw ApiException.badRequest("invalid_type", "Invalid referral type.");
        }
        if (input.priority() == null || !PRIORITIES.contains(input.priority())) {
            throw ApiException.badRequest("invalid_priority", "Invalid priority.");
        }
        if (input.reason() == null || input.reason().isBlank()) {
            throw ApiException.badRequest("reason_required", "A reason is required.");
        }

        SupportReferral referral = new SupportReferral();
        referral.setSchoolId(schoolId);
        referral.setStudentId(input.studentId());
        referral.setReferredBy(TenantContext.actorMembershipId());
        referral.setReferredTo(input.referredTo());
        referral.setReferralType(input.referralType());
        referral.setPriority(input.priority());
        referral.setReason(input.reason());
        referral.setStatus("open");
        referral.setCreatedBy(TenantContext.actorUserId());
        referral = referralRepository.save(referral);

        return toReferralDto(referral);
    }

    @Override
    @Transactional
    public SupportReferralDTO updateReferralStatus(UUID referralId, String status, String outcomeNotes) {
        UUID schoolId = TenantContext.schoolId();
        SupportReferral referral = referralRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, referralId)
                .orElseThrow(() -> ApiException.notFound("referral_not_found", "Referral not found."));
        if (!REFERRAL_STATUSES.contains(status)) {
            throw ApiException.badRequest("invalid_status", "Invalid referral status.");
        }
        referral.setStatus(status);
        referral.setOutcomeNotes(outcomeNotes);
        referral.setUpdatedBy(TenantContext.actorUserId());
        referralRepository.save(referral);
        return toReferralDto(referral);
    }

    // ----- intervention plans -----

    @Override
    @Transactional(readOnly = true)
    public List<InterventionPlanDTO> listInterventionPlans(UUID studentId) {
        UUID schoolId = TenantContext.schoolId();
        List<InterventionPlan> list = planRepository.findBySchoolIdAndStudentIdAndDeletedAtIsNull(schoolId, studentId);
        return list.stream().map(this::toPlanDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public InterventionPlanDTO createInterventionPlan(InterventionPlanInput input) {
        UUID schoolId = TenantContext.schoolId();
        if (input.studentId() == null) {
            throw ApiException.badRequest("student_required", "A student is required.");
        }
        if (input.title() == null || input.title().isBlank()) {
            throw ApiException.badRequest("title_required", "A plan title is required.");
        }
        if (input.description() == null || input.description().isBlank()) {
            throw ApiException.badRequest("description_required", "A description is required.");
        }
        if (input.startDate() == null || input.reviewDate() == null) {
            throw ApiException.badRequest("dates_required", "Start and review dates are required.");
        }

        InterventionPlan plan = new InterventionPlan();
        plan.setSchoolId(schoolId);
        plan.setStudentId(input.studentId());
        plan.setReferralId(input.referralId());
        plan.setTitle(input.title());
        plan.setDescription(input.description());
        plan.setStartDate(input.startDate());
        plan.setReviewDate(input.reviewDate());
        plan.setEndDate(input.endDate());
        plan.setStatus("active");
        plan.setCoordinatorMembershipId(input.coordinatorMembershipId() != null
                ? input.coordinatorMembershipId() : TenantContext.actorMembershipId());
        plan.setGuardianNotified(input.guardianNotified() != null ? input.guardianNotified() : false);
        plan.setGuardianConsent(input.guardianConsent());
        plan.setCreatedBy(TenantContext.actorUserId());
        plan = planRepository.save(plan);

        return toPlanDto(plan);
    }

    @Override
    @Transactional
    public InterventionPlanDTO updateInterventionPlan(UUID planId, InterventionPlanInput input) {
        UUID schoolId = TenantContext.schoolId();
        InterventionPlan plan = planRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, planId)
                .orElseThrow(() -> ApiException.notFound("plan_not_found", "Plan not found."));
        if (input.title() != null) plan.setTitle(input.title());
        if (input.description() != null) plan.setDescription(input.description());
        if (input.startDate() != null) plan.setStartDate(input.startDate());
        if (input.reviewDate() != null) plan.setReviewDate(input.reviewDate());
        if (input.endDate() != null) plan.setEndDate(input.endDate());
        if (input.status() != null && PLAN_STATUSES.contains(input.status())) plan.setStatus(input.status());
        if (input.coordinatorMembershipId() != null)
            plan.setCoordinatorMembershipId(input.coordinatorMembershipId());
        if (input.guardianNotified() != null) plan.setGuardianNotified(input.guardianNotified());
        if (input.guardianConsent() != null) plan.setGuardianConsent(input.guardianConsent());
        plan.setUpdatedBy(TenantContext.actorUserId());
        planRepository.save(plan);
        return toPlanDto(plan);
    }

    // ----- goals -----

    @Override
    @Transactional(readOnly = true)
    public List<SupportGoalDTO> listGoals(UUID planId) {
        UUID schoolId = TenantContext.schoolId();
        List<SupportGoal> list = goalRepository.findBySchoolIdAndPlanIdAndDeletedAtIsNull(schoolId, planId);
        return list.stream().map(this::toGoalDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SupportGoalDTO createGoal(SupportGoalInput input) {
        UUID schoolId = TenantContext.schoolId();
        if (input.planId() == null) {
            throw ApiException.badRequest("plan_required", "A plan is required.");
        }
        if (input.description() == null || input.description().isBlank()) {
            throw ApiException.badRequest("description_required", "A goal description is required.");
        }

        SupportGoal goal = new SupportGoal();
        goal.setSchoolId(schoolId);
        goal.setPlanId(input.planId());
        goal.setDescription(input.description());
        goal.setTargetDate(input.targetDate());
        goal.setAchieved(false);
        goal.setEvidenceRef(input.evidenceRef());
        goal.setCreatedBy(TenantContext.actorUserId());
        goal = goalRepository.save(goal);
        return toGoalDto(goal);
    }

    @Override
    @Transactional
    public SupportGoalDTO markGoalAchieved(UUID goalId) {
        UUID schoolId = TenantContext.schoolId();
        SupportGoal goal = goalRepository.findById(goalId)
                .filter(g -> g.getSchoolId().equals(schoolId) && g.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("goal_not_found", "Goal not found."));
        goal.setAchieved(true);
        goal.setAchievedAt(LocalDateTime.now());
        goal.setUpdatedBy(TenantContext.actorUserId());
        goalRepository.save(goal);
        return toGoalDto(goal);
    }

    // ----- progress notes -----

    @Override
    @Transactional(readOnly = true)
    public List<ProgressNoteDTO> listProgressNotes(UUID planId, Boolean confidentialOnly) {
        UUID schoolId = TenantContext.schoolId();
        List<ProgressNote> list = noteRepository.findBySchoolIdAndPlanIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                schoolId, planId);
        if (confidentialOnly != null && confidentialOnly) {
            list = list.stream().filter(ProgressNote::getIsConfidential).collect(Collectors.toList());
        }
        return list.stream().map(this::toNoteDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProgressNoteDTO createProgressNote(ProgressNoteInput input) {
        UUID schoolId = TenantContext.schoolId();
        if (input.planId() == null) {
            throw ApiException.badRequest("plan_required", "A plan is required.");
        }
        if (input.studentId() == null) {
            throw ApiException.badRequest("student_required", "A student is required.");
        }
        if (input.noteType() == null || !NOTE_TYPES.contains(input.noteType())) {
            throw ApiException.badRequest("invalid_type", "Invalid note type.");
        }
        if (input.content() == null || input.content().isBlank()) {
            throw ApiException.badRequest("content_required", "Note content is required.");
        }

        ProgressNote note = new ProgressNote();
        note.setSchoolId(schoolId);
        note.setPlanId(input.planId());
        note.setStudentId(input.studentId());
        note.setAuthorMembershipId(TenantContext.actorMembershipId());
        note.setNoteType(input.noteType());
        note.setContent(input.content());
        note.setIsConfidential(input.isConfidential() != null ? input.isConfidential() : false);
        note.setCreatedBy(TenantContext.actorUserId());
        note = noteRepository.save(note);
        return toNoteDto(note);
    }

    // ----- timeline -----

    @Override
    @Transactional(readOnly = true)
    public List<TimelineEvent> studentTimeline(UUID studentId) {
        UUID schoolId = TenantContext.schoolId();
        List<TimelineEvent> events = new ArrayList<>();

        List<RiskIndicator> indicators = indicatorRepository.findBySchoolIdAndStudentIdAndDeletedAtIsNull(schoolId, studentId);
        for (RiskIndicator i : indicators) {
            events.add(new TimelineEvent(
                    "indicator", i.getIndicatorType(), i.getDescription(),
                    i.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate(), i.getId()));
        }

        List<SupportReferral> referrals = referralRepository.findBySchoolIdAndStudentIdAndDeletedAtIsNull(schoolId, studentId);
        for (SupportReferral r : referrals) {
            events.add(new TimelineEvent(
                    "referral", r.getReferralType(), r.getReason(),
                    r.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate(), r.getId()));
        }

        List<InterventionPlan> plans = planRepository.findBySchoolIdAndStudentIdAndDeletedAtIsNull(schoolId, studentId);
        for (InterventionPlan p : plans) {
            events.add(new TimelineEvent(
                    "plan", p.getTitle(), p.getDescription(),
                    p.getStartDate(), p.getId()));
        }

        events.sort((a, b) -> b.occurredAt().compareTo(a.occurredAt()));
        return events;
    }

    // ----- DTO mappers -----

    private StudentProgressProfile toProfileEntity(StudentProfileDTO dto) {
        return null; // not used
    }

    private RiskIndicatorDTO toIndicatorDto(RiskIndicator i) {
        String name = studentName(i.getStudentId());
        return new RiskIndicatorDTO(
                i.getId(), i.getStudentId(), name,
                i.getIndicatorType(), i.getSeverity(), i.getDescription(),
                i.getSourceModule(), i.getSourceEntityId(),
                i.getAcknowledged(), i.getResolved());
    }

    private SupportReferralDTO toReferralDto(SupportReferral r) {
        return new SupportReferralDTO(
                r.getId(), r.getStudentId(), studentName(r.getStudentId()),
                r.getReferredBy(), null, r.getReferredTo(), null,
                r.getReferralType(), r.getPriority(), r.getReason(),
                r.getStatus(), r.getOutcomeNotes());
    }

    private InterventionPlanDTO toPlanDto(InterventionPlan p) {
        return new InterventionPlanDTO(
                p.getId(), p.getStudentId(), studentName(p.getStudentId()),
                p.getReferralId(), p.getTitle(), p.getDescription(),
                p.getStartDate(), p.getReviewDate(), p.getEndDate(),
                p.getStatus(), p.getCoordinatorMembershipId(), null,
                p.getGuardianNotified(), p.getGuardianConsent());
    }

    private SupportGoalDTO toGoalDto(SupportGoal g) {
        return new SupportGoalDTO(
                g.getId(), g.getPlanId(), g.getDescription(),
                g.getTargetDate(), g.getAchieved(), g.getAchievedAt(), g.getEvidenceRef());
    }

    private ProgressNoteDTO toNoteDto(ProgressNote n) {
        return new ProgressNoteDTO(
                n.getId(), n.getPlanId(), n.getStudentId(), n.getAuthorMembershipId(), null,
                n.getNoteType(), n.getContent(), n.getIsConfidential());
    }

    private String studentName(UUID studentId) {
        return peopleApi.findStudent(TenantContext.schoolId(), studentId)
                .map(PeopleApi.StudentRef::fullName)
                .orElse("Unknown student");
    }

    private String admissionNumber(UUID studentId) {
        return peopleApi.findStudent(TenantContext.schoolId(), studentId)
                .map(PeopleApi.StudentRef::admissionNumber)
                .orElse(null);
    }

    private boolean hasPermission(String permission) {
        var principal = SecurityUtils.principalOrNull();
        return principal != null && principal.permissions() != null && principal.permissions().contains(permission);
    }
}
