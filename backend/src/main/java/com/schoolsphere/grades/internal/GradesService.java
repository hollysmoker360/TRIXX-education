package com.schoolsphere.grades.internal;

import com.schoolsphere.grades.api.GradesApi;
import com.schoolsphere.grades.internal.entity.Assessment;
import com.schoolsphere.grades.internal.entity.AssessmentComponent;
import com.schoolsphere.grades.internal.entity.GradeEntry;
import com.schoolsphere.grades.internal.entity.GradeBand;
import com.schoolsphere.grades.internal.entity.MarkCorrection;
import com.schoolsphere.grades.internal.repository.AssessmentComponentRepository;
import com.schoolsphere.grades.internal.repository.AssessmentRepository;
import com.schoolsphere.grades.internal.repository.GradeBandRepository;
import com.schoolsphere.grades.internal.repository.GradeEntryRepository;
import com.schoolsphere.grades.internal.repository.MarkCorrectionRepository;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.security.SecurityUtils;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import com.schoolsphere.people.api.PeopleApi;
import com.schoolsphere.tenants.api.PermissionCatalog;
import com.schoolsphere.tenants.api.TenantsApi;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
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
public class GradesService implements GradesApi {

    private static final Set<String> STATUSES = Set.of("draft", "submitted", "moderated", "published");

    private final AssessmentRepository assessmentRepository;
    private final AssessmentComponentRepository componentRepository;
    private final GradeEntryRepository gradeEntryRepository;
    private final MarkCorrectionRepository correctionRepository;
    private final GradeBandRepository gradeBandRepository;
    private final PeopleApi peopleApi;

    public GradesService(AssessmentRepository assessmentRepository,
                         AssessmentComponentRepository componentRepository,
                         GradeEntryRepository gradeEntryRepository,
                         MarkCorrectionRepository correctionRepository,
                         GradeBandRepository gradeBandRepository,
                         PeopleApi peopleApi) {
        this.assessmentRepository = assessmentRepository;
        this.componentRepository = componentRepository;
        this.gradeEntryRepository = gradeEntryRepository;
        this.correctionRepository = correctionRepository;
        this.gradeBandRepository = gradeBandRepository;
        this.peopleApi = peopleApi;
    }

    // ----- assessments -----

    @Override
    @Transactional
    public GradesApi.AssessmentView createAssessment(GradesApi.AssessmentDTO dto) {
        UUID schoolId = TenantContext.schoolId();
        UUID actor = TenantContext.actorMembershipId();

        if (dto.classGroupId() == null || dto.subjectId() == null || dto.termId() == null) {
            throw ApiException.badRequest("class_subject_term_required",
                    "Class, subject, and term are all required.");
        }
        if (dto.title() == null || dto.title().isBlank()) {
            throw ApiException.badRequest("title_required", "Assessment title is required.");
        }
        if (dto.type() == null || !Set.of("test", "quiz", "exam", "practical", "project").contains(dto.type())) {
            throw ApiException.badRequest("invalid_type", "Unknown assessment type.");
        }
        if (dto.maxMarks() == null || dto.maxMarks() < 0) {
            throw ApiException.badRequest("max_marks_required", "Maximum marks must be zero or greater.");
        }
        if (dto.weight() == null || dto.weight() <= 0) {
            throw ApiException.badRequest("weight_required", "Weight must be greater than zero.");
        }
        if (dto.dateAssessed() == null) {
            throw ApiException.badRequest("date_required", "Assessment date is required.");
        }

        boolean assigned = peopleApi.assignmentsForTerm(schoolId, dto.termId()).stream()
                .anyMatch(a -> a.teacherId().equals(actor)
                        && a.classGroupId().equals(dto.classGroupId())
                        && a.subjectId().equals(dto.subjectId()));
        if (!assigned && !hasPermission(PermissionCatalog.ACADEMICS_MANAGE)) {
            throw ApiException.forbidden("not_assigned",
                    "You are not assigned to teach this subject in this class.");
        }

        Assessment assessment = new Assessment();
        assessment.setSchoolId(schoolId);
        assessment.setClassGroupId(dto.classGroupId());
        assessment.setSubjectId(dto.subjectId());
        assessment.setTeacherMembershipId(actor);
        assessment.setTermId(dto.termId());
        assessment.setTitle(dto.title());
        assessment.setType(dto.type());
        assessment.setMaxMarks(BigDecimal.valueOf(dto.maxMarks()));
        assessment.setWeight(BigDecimal.valueOf(dto.weight()));
        assessment.setDateAssessed(dto.dateAssessed());
        assessment.setDueDate(dto.dueDate());
        assessment.setGradingScaleId(dto.gradingScaleId());
        assessment.setPublished(false);
        assessment.setCreatedBy(TenantContext.actorUserId());
        assessment = assessmentRepository.save(assessment);

        BigDecimal total = BigDecimal.ZERO;
        int position = 0;
        if (dto.components() != null && !dto.components().isEmpty()) {
            for (GradesApi.AssessmentComponentDTO c : dto.components()) {
                if (c.name() == null || c.name().isBlank()) {
                    throw ApiException.badRequest("component_name_required", "Component name is required.");
                }
                if (c.maxMarks() == null || c.maxMarks() < 0) {
                    throw ApiException.badRequest("component_max_marks_required", "Component max marks must be >= 0.");
                }
                AssessmentComponent comp = new AssessmentComponent();
                comp.setSchoolId(schoolId);
                comp.setAssessmentId(assessment.getId());
                comp.setName(c.name());
                comp.setMaxMarks(BigDecimal.valueOf(c.maxMarks()));
                comp.setWeight(c.weight() == null ? BigDecimal.ONE : BigDecimal.valueOf(c.weight()));
                comp.setPosition(position++);
                comp.setCreatedBy(TenantContext.actorUserId());
                componentRepository.save(comp);
                total = total.add(BigDecimal.valueOf(c.maxMarks()));
            }
        } else {
            AssessmentComponent comp = new AssessmentComponent();
            comp.setSchoolId(schoolId);
            comp.setAssessmentId(assessment.getId());
            comp.setName("Main");
            comp.setMaxMarks(BigDecimal.valueOf(dto.maxMarks()));
            comp.setWeight(BigDecimal.ONE);
            comp.setPosition(position);
            comp.setCreatedBy(TenantContext.actorUserId());
            componentRepository.save(comp);
            total = BigDecimal.valueOf(dto.maxMarks());
        }

        createDraftEntries(schoolId, assessment.getId(), dto.classGroupId(), null, assessment.getMaxMarks());
        return getAssessment(assessment.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public GradesApi.AssessmentView getAssessment(UUID assessmentId) {
        UUID schoolId = TenantContext.schoolId();
        Assessment assessment = assessmentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, assessmentId)
                .orElseThrow(() -> ApiException.notFound("assessment_not_found", "Assessment not found."));
        return toAssessmentView(assessment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GradesApi.AssessmentView> listAssessments(UUID classGroupId, UUID subjectId,
                                                          UUID termId, Boolean published) {
        UUID schoolId = TenantContext.schoolId();
        List<Assessment> list;
        if (classGroupId != null && termId != null) {
            list = assessmentRepository.findBySchoolIdAndClassGroupIdAndTermIdAndDeletedAtIsNull(
                    schoolId, classGroupId, termId);
        } else if (classGroupId != null) {
            list = assessmentRepository.findBySchoolIdAndClassGroupIdAndDeletedAtIsNull(
                    schoolId, classGroupId);
        } else if (subjectId != null) {
            list = assessmentRepository.findBySchoolIdAndSubjectIdAndDeletedAtIsNull(
                    schoolId, subjectId);
        } else {
            list = List.of();
        }
        if (published != null) {
            list = list.stream()
                    .filter(a -> java.util.Objects.equals(a.getPublished(), published))
                    .collect(Collectors.toList());
        }
        return list.stream().map(this::toAssessmentView).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public GradesApi.AssessmentView updateAssessment(UUID assessmentId, GradesApi.AssessmentDTO dto) {
        UUID schoolId = TenantContext.schoolId();
        Assessment assessment = assessmentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, assessmentId)
                .orElseThrow(() -> ApiException.notFound("assessment_not_found", "Assessment not found."));
        if (assessment.getPublished()) {
            throw ApiException.conflict("assessment_published",
                    "A published assessment cannot be edited. Request a mark correction instead.");
        }
        if (dto.title() != null && !dto.title().isBlank()) {
            assessment.setTitle(dto.title());
        }
        if (dto.type() != null) {
            assessment.setType(dto.type());
        }
        if (dto.maxMarks() != null) {
            assessment.setMaxMarks(BigDecimal.valueOf(dto.maxMarks()));
        }
        if (dto.weight() != null) {
            assessment.setWeight(BigDecimal.valueOf(dto.weight()));
        }
        if (dto.dateAssessed() != null) {
            assessment.setDateAssessed(dto.dateAssessed());
        }
        if (dto.dueDate() != null) {
            assessment.setDueDate(dto.dueDate());
        }
        if (dto.gradingScaleId() != null) {
            assessment.setGradingScaleId(dto.gradingScaleId());
        }
        assessment.setUpdatedBy(TenantContext.actorUserId());
        assessmentRepository.save(assessment);
        return getAssessment(assessmentId);
    }

    @Override
    @Transactional
    public void deleteAssessment(UUID assessmentId) {
        UUID schoolId = TenantContext.schoolId();
        Assessment assessment = assessmentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, assessmentId)
                .orElseThrow(() -> ApiException.notFound("assessment_not_found", "Assessment not found."));
        if (assessment.getPublished()) {
            throw ApiException.conflict("assessment_published",
                    "A published assessment cannot be deleted.");
        }
        boolean hasEntries = gradeEntryRepository.findBySchoolIdAndAssessmentIdAndDeletedAtIsNull(
                schoolId, assessmentId).stream()
                .anyMatch(e -> e.getMarksObtained() != null);
        if (hasEntries) {
            throw ApiException.conflict("assessment_has_marks",
                    "An assessment with entered marks cannot be deleted.");
        }
        assessment.setDeletedAt(Instant.now());
        assessment.setUpdatedBy(TenantContext.actorUserId());
        assessmentRepository.save(assessment);
    }

    // ----- grade entry -----

    @Override
    @Transactional
    public List<GradesApi.GradeEntryView> enterGrades(UUID assessmentId,
                                                      List<GradesApi.GradeEntryInput> inputs) {
        UUID schoolId = TenantContext.schoolId();
        Assessment assessment = assessmentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, assessmentId)
                .orElseThrow(() -> ApiException.notFound("assessment_not_found", "Assessment not found."));
        if (assessment.getPublished()) {
            throw ApiException.conflict("assessment_published",
                    "A published assessment cannot be edited. Request a mark correction instead.");
        }
        if (inputs == null || inputs.isEmpty()) {
            throw ApiException.badRequest("grades_required", "At least one grade entry is required.");
        }

        List<GradeEntry> existing = gradeEntryRepository.findBySchoolIdAndAssessmentIdAndDeletedAtIsNull(
                schoolId, assessmentId);
        Map<UUID, GradeEntry> byStudent = new LinkedHashMap<>();
        for (GradeEntry e : existing) {
            byStudent.put(e.getStudentId(), e);
        }

        List<GradesApi.GradeEntryView> results = new java.util.ArrayList<>();
        for (GradesApi.GradeEntryInput input : inputs) {
            if (input.studentId() == null) {
                throw ApiException.badRequest("student_required", "A student is required.");
            }
            if (input.marksObtained() != null && (input.marksObtained() < 0
                    || input.marksObtained() > assessment.getMaxMarks().doubleValue())) {
                throw ApiException.badRequest("marks_out_of_range",
                        "Marks must be between 0 and " + assessment.getMaxMarks() + ".");
            }

            GradeEntry entry = byStudent.get(input.studentId());
            boolean isNew = entry == null;
            if (isNew) {
                entry = new GradeEntry();
                entry.setSchoolId(schoolId);
                entry.setAssessmentId(assessmentId);
                entry.setStudentId(input.studentId());
                entry.setMaxMarks(assessment.getMaxMarks());
                entry.setStatus("draft");
                entry.setCreatedBy(TenantContext.actorUserId());
            }
            entry.setMarksObtained(input.marksObtained() == null ? null
                    : BigDecimal.valueOf(input.marksObtained()));
            entry.setFeedback(input.feedback());
            entry.setStatus("submitted");
            entry.setSubmittedAt(Instant.now());
            entry.setUpdatedBy(TenantContext.actorUserId());
            entry = gradeEntryRepository.save(entry);

            String grade = computeGrade(entry.getMarksObtained(), entry.getMaxMarks());
            entry.setGrade(grade);
            entry = gradeEntryRepository.save(entry);

            results.add(toGradeEntryView(entry));
        }
        return results;
    }

    @Override
    @Transactional
    public GradesApi.GradeEntryView updateGradeEntry(UUID entryId, GradesApi.GradeEntryInput input) {
        UUID schoolId = TenantContext.schoolId();
        GradeEntry entry = gradeEntryRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, entryId)
                .orElseThrow(() -> ApiException.notFound("entry_not_found", "Grade entry not found."));
        if ("published".equals(entry.getStatus())) {
            throw ApiException.conflict("entry_published",
                    "A published grade entry cannot be edited directly. Request a correction instead.");
        }
        if (input.marksObtained() != null) {
            Assessment assessment = assessmentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(
                    schoolId, entry.getAssessmentId())
                    .orElseThrow(() -> ApiException.notFound("assessment_not_found", "Assessment not found."));
            if (input.marksObtained() < 0 || input.marksObtained() > assessment.getMaxMarks().doubleValue()) {
                throw ApiException.badRequest("marks_out_of_range",
                        "Marks must be between 0 and " + assessment.getMaxMarks() + ".");
            }
            entry.setMarksObtained(BigDecimal.valueOf(input.marksObtained()));
        }
        if (input.feedback() != null) {
            entry.setFeedback(input.feedback());
        }
        entry.setStatus("submitted");
        entry.setSubmittedAt(Instant.now());
        entry.setUpdatedBy(TenantContext.actorUserId());
        entry = gradeEntryRepository.save(entry);

        String grade = computeGrade(entry.getMarksObtained(), entry.getMaxMarks());
        entry.setGrade(grade);
        entry = gradeEntryRepository.save(entry);
        return toGradeEntryView(entry);
    }

    @Override
    @Transactional
    public GradesApi.AssessmentView publishAssessment(UUID assessmentId) {
        UUID schoolId = TenantContext.schoolId();
        Assessment assessment = assessmentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, assessmentId)
                .orElseThrow(() -> ApiException.notFound("assessment_not_found", "Assessment not found."));
        if (assessment.getPublished()) {
            throw ApiException.conflict("already_published", "This assessment is already published.");
        }

        List<GradeEntry> entries = gradeEntryRepository.findBySchoolIdAndAssessmentIdAndDeletedAtIsNull(
                schoolId, assessmentId);
        List<UUID> missing = entries.stream()
                .filter(e -> e.getMarksObtained() == null)
                .map(GradeEntry::getStudentId)
                .collect(Collectors.toList());
        if (!missing.isEmpty()) {
            throw ApiException.conflict("missing_marks",
                    "All students must have marks entered before publishing.");
        }

        assessment.setPublished(true);
        assessment.setPublishedAt(Instant.now());
        assessment.setUpdatedBy(TenantContext.actorUserId());
        assessmentRepository.save(assessment);

        for (GradeEntry entry : entries) {
            entry.setStatus("published");
            entry.setUpdatedBy(TenantContext.actorUserId());
            gradeEntryRepository.save(entry);
        }

        return getAssessment(assessmentId);
    }

    @Override
    @Transactional
    public GradesApi.GradeEntryView requestCorrection(UUID entryId,
                                                      GradesApi.CorrectionRequest request) {
        UUID schoolId = TenantContext.schoolId();
        GradeEntry entry = gradeEntryRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, entryId)
                .orElseThrow(() -> ApiException.notFound("entry_not_found", "Grade entry not found."));
        if (!"published".equals(entry.getStatus())) {
            throw ApiException.conflict("entry_not_published",
                    "Only published entries can be corrected.");
        }
        if (request.previousMarks() == null || request.newMarks() == null) {
            throw ApiException.badRequest("marks_required", "Previous and new marks are required.");
        }
        if (request.newMarks() < 0) {
            throw ApiException.badRequest("marks_negative", "New marks must be zero or greater.");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw ApiException.badRequest("reason_required", "A reason is required.");
        }
        if (!entry.getMarksObtained().equals(BigDecimal.valueOf(request.previousMarks()))) {
            throw ApiException.conflict("marks_mismatch",
                    "Previous marks do not match the current value.");
        }

        MarkCorrection correction = new MarkCorrection();
        correction.setSchoolId(schoolId);
        correction.setGradeEntryId(entryId);
        correction.setPreviousMarks(BigDecimal.valueOf(request.previousMarks()));
        correction.setNewMarks(BigDecimal.valueOf(request.newMarks()));
        correction.setReason(request.reason());
        correction.setCorrectedBy(TenantContext.actorMembershipId());
        correction.setCorrectedAt(Instant.now());
        correction.setCreatedBy(TenantContext.actorUserId());
        correctionRepository.save(correction);

        return toGradeEntryView(entry);
    }

    @Override
    @Transactional
    public GradesApi.GradeEntryView approveCorrection(UUID correctionId) {
        UUID schoolId = TenantContext.schoolId();
        MarkCorrection correction = correctionRepository.findBySchoolIdAndIdAndDeletedAtIsNull(
                schoolId, correctionId)
                .orElseThrow(() -> ApiException.notFound("correction_not_found", "Correction not found."));

        if (!hasPermission(PermissionCatalog.GRADE_MODERATE)) {
            throw ApiException.forbidden("moderation_forbidden",
                    "You may not approve mark corrections.");
        }

        correction.setApprovedBy(TenantContext.actorMembershipId());
        correction.setApprovedAt(Instant.now());
        correctionRepository.save(correction);

        GradeEntry entry = gradeEntryRepository.findBySchoolIdAndIdAndDeletedAtIsNull(
                schoolId, correction.getGradeEntryId())
                .orElseThrow(() -> ApiException.notFound("entry_not_found", "Grade entry not found."));
        entry.setMarksObtained(correction.getNewMarks());
        entry.setStatus("published");
        entry.setUpdatedBy(TenantContext.actorUserId());
        entry = gradeEntryRepository.save(entry);

        String grade = computeGrade(entry.getMarksObtained(), entry.getMaxMarks());
        entry.setGrade(grade);
        entry = gradeEntryRepository.save(entry);
        return toGradeEntryView(entry);
    }

    // ----- grade bands -----

    @Override
    @Transactional(readOnly = true)
    public List<GradesApi.GradebandDTO> listGradeBands() {
        UUID schoolId = TenantContext.schoolId();
        return gradeBandRepository.findBySchoolIdAndDeletedAtIsNullOrderByMinScoreAsc(schoolId).stream()
                .map(this::toGradebandDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public GradesApi.GradebandDTO createGradeBand(GradesApi.GradebandDTO dto) {
        UUID schoolId = TenantContext.schoolId();
        if (dto.name() == null || dto.name().isBlank()) {
            throw ApiException.badRequest("name_required", "Band name is required.");
        }
        if (dto.minScore() == null || dto.maxScore() == null) {
            throw ApiException.badRequest("score_range_required", "Min and max score are required.");
        }
        if (dto.minScore() > dto.maxScore()) {
            throw ApiException.badRequest("invalid_range", "Min score must be <= max score.");
        }

        GradeBand band = new GradeBand();
        band.setSchoolId(schoolId);
        band.setName(dto.name());
        band.setMinScore(BigDecimal.valueOf(dto.minScore()));
        band.setMaxScore(BigDecimal.valueOf(dto.maxScore()));
        band.setGradePoint(dto.gradePoint() == null ? null : BigDecimal.valueOf(dto.gradePoint()));
        band.setColorHex(dto.colorHex());
        band.setCreatedBy(TenantContext.actorUserId());
        band = gradeBandRepository.save(band);
        return toGradebandDto(band);
    }

    // ----- views -----

    @Override
    @Transactional(readOnly = true)
    public List<GradesApi.ClassGradebookRow> classGradebook(UUID classGroupId, UUID termId) {
        UUID schoolId = TenantContext.schoolId();
        List<Assessment> assessments;
        if (classGroupId != null && termId != null) {
            assessments = assessmentRepository.findBySchoolIdAndClassGroupIdAndTermIdAndDeletedAtIsNull(
                    schoolId, classGroupId, termId);
        } else if (classGroupId != null) {
            assessments = assessmentRepository.findBySchoolIdAndClassGroupIdAndDeletedAtIsNull(
                    schoolId, classGroupId);
        } else {
            assessments = List.of();
        }
        List<GradeEntry> allEntries = new java.util.ArrayList<>();
        for (Assessment a : assessments) {
            allEntries.addAll(gradeEntryRepository.findBySchoolIdAndAssessmentIdAndDeletedAtIsNull(
                    schoolId, a.getId()));
        }
        Map<UUID, List<GradeEntry>> entriesByStudent = new LinkedHashMap<>();
        for (GradeEntry e : allEntries) {
            entriesByStudent.computeIfAbsent(e.getStudentId(), k -> new java.util.ArrayList<>()).add(e);
        }

        List<PeopleApi.StudentRef> roster = peopleApi.listStudentsByClass(schoolId, classGroupId, null);
        List<GradesApi.ClassGradebookRow> rows = new java.util.ArrayList<>();
        for (PeopleApi.StudentRef student : roster) {
            List<GradeEntry> studentEntries = entriesByStudent.getOrDefault(student.id(), List.of());
            List<GradesApi.GradeEntryView> views = studentEntries.stream()
                    .map(this::toGradeEntryView)
                    .collect(Collectors.toList());
            Double avg = studentEntries.stream()
                    .filter(e -> e.getMarksObtained() != null)
                    .mapToDouble(e -> e.getMarksObtained().doubleValue()
                            / e.getMaxMarks().doubleValue() * 100)
                    .average().orElse(Double.NaN);
            rows.add(new GradesApi.ClassGradebookRow(student.id(), student.fullName(),
                    student.admissionNumber(), views,
                    Double.isNaN(avg) ? null
                            : BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP).doubleValue()));
        }
        return rows;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GradesApi.GradeEntryView> studentGrades(UUID studentId) {
        UUID schoolId = TenantContext.schoolId();
        List<GradeEntry> entries = gradeEntryRepository.findBySchoolIdAndStudentIdAndDeletedAtIsNull(
                schoolId, studentId);
        return entries.stream().map(this::toGradeEntryView).collect(Collectors.toList());
    }

    // ----- internals -----

    private void createDraftEntries(UUID schoolId, UUID assessmentId, UUID classGroupId,
                                    UUID streamId, BigDecimal maxMarks) {
        List<PeopleApi.StudentRef> roster = peopleApi.listStudentsByClass(schoolId, classGroupId, streamId);
        for (PeopleApi.StudentRef student : roster) {
            Optional<GradeEntry> existing = gradeEntryRepository.findBySchoolIdAndAssessmentIdAndStudentIdAndDeletedAtIsNull(
                    schoolId, assessmentId, student.id());
            if (existing.isPresent()) continue;
            GradeEntry entry = new GradeEntry();
            entry.setSchoolId(schoolId);
            entry.setAssessmentId(assessmentId);
            entry.setStudentId(student.id());
            entry.setMaxMarks(maxMarks != null ? maxMarks : BigDecimal.ZERO);
            entry.setStatus("draft");
            entry.setCreatedBy(TenantContext.actorUserId());
            gradeEntryRepository.save(entry);
        }
    }

    private String computeGrade(BigDecimal marks, BigDecimal maxMarks) {
        if (marks == null || maxMarks == null || maxMarks.doubleValue() == 0) {
            return null;
        }
        double pct = marks.doubleValue() / maxMarks.doubleValue() * 100;
        UUID schoolId = TenantContext.schoolId();
        List<GradeBand> bands = gradeBandRepository.findBySchoolIdAndDeletedAtIsNullOrderByMinScoreAsc(schoolId);
        for (GradeBand band : bands) {
            if (pct >= band.getMinScore().doubleValue() && pct <= band.getMaxScore().doubleValue()) {
                return band.getName();
            }
        }
        return null;
    }

    private GradesApi.AssessmentView toAssessmentView(Assessment a) {
        UUID schoolId = TenantContext.schoolId();
        List<AssessmentComponent> comps = componentRepository.findBySchoolIdAndAssessmentIdAndDeletedAtIsNullOrderByPositionAsc(
                schoolId, a.getId());
        List<GradesApi.AssessmentComponentDTO> compDtos = comps.stream()
                .map(c -> new GradesApi.AssessmentComponentDTO(c.getId(), c.getName(),
                        c.getMaxMarks() == null ? null : c.getMaxMarks().doubleValue(),
                        c.getWeight() == null ? null : c.getWeight().doubleValue(), c.getPosition()))
                .collect(Collectors.toList());
        List<GradeEntry> entries = gradeEntryRepository.findBySchoolIdAndAssessmentIdAndDeletedAtIsNull(
                schoolId, a.getId());
        Map<UUID, PeopleApi.StudentRef> students = new LinkedHashMap<>();
        for (GradeEntry e : entries) {
            if (!students.containsKey(e.getStudentId())) {
                peopleApi.findStudent(schoolId, e.getStudentId())
                        .ifPresent(s -> students.put(e.getStudentId(), s));
            }
        }
        List<GradesApi.GradeEntryView> entryViews = entries.stream()
                .map(e -> toGradeEntryView(e, students.get(e.getStudentId())))
                .collect(Collectors.toList());
        return new GradesApi.AssessmentView(
                a.getId(), a.getClassGroupId(), a.getSubjectId(), a.getTeacherMembershipId(),
                a.getTermId(), a.getTitle(), a.getType(),
                a.getMaxMarks() == null ? null : a.getMaxMarks().doubleValue(),
                a.getWeight() == null ? null : a.getWeight().doubleValue(),
                a.getDateAssessed(), a.getDueDate(), a.getGradingScaleId(), a.getPublished(),
                compDtos, entryViews);
    }

    private GradesApi.GradeEntryView toGradeEntryView(GradeEntry e) {
        return toGradeEntryView(e, null);
    }

    private GradesApi.GradeEntryView toGradeEntryView(GradeEntry e, PeopleApi.StudentRef student) {
        return new GradesApi.GradeEntryView(
                e.getId(), e.getStudentId(),
                student == null ? null : student.fullName(),
                student == null ? null : student.admissionNumber(),
                e.getMarksObtained() == null ? null : e.getMarksObtained().doubleValue(),
                e.getMaxMarks() == null ? null : e.getMaxMarks().doubleValue(),
                e.getGrade(), e.getFeedback(), e.getStatus(), null);
    }

    private GradesApi.GradebandDTO toGradebandDto(GradeBand b) {
        return new GradesApi.GradebandDTO(
                b.getId(), b.getName(),
                b.getMinScore() == null ? null : b.getMinScore().doubleValue(),
                b.getMaxScore() == null ? null : b.getMaxScore().doubleValue(),
                b.getGradePoint() == null ? null : b.getGradePoint().doubleValue(),
                b.getColorHex());
    }

    private boolean hasPermission(String permission) {
        var principal = SecurityUtils.principalOrNull();
        return principal != null && principal.permissions() != null && principal.permissions().contains(permission);
    }
}
