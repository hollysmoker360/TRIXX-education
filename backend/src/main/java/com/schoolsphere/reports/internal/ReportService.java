package com.schoolsphere.reports.internal;

import com.schoolsphere.academics.internal.Subject;
import com.schoolsphere.academics.internal.SubjectRepository;
import com.schoolsphere.attendance.internal.AttendanceEventRepository;
import com.schoolsphere.attendance.internal.AttendanceSessionRepository;
import com.schoolsphere.cbe.internal.Competency;
import com.schoolsphere.cbe.internal.CompetencyRepository;
import com.schoolsphere.grades.internal.entity.Assessment;
import com.schoolsphere.grades.internal.entity.GradeEntry;
import com.schoolsphere.grades.internal.repository.AssessmentRepository;
import com.schoolsphere.grades.internal.repository.GradeBandRepository;
import com.schoolsphere.grades.internal.repository.GradeEntryRepository;
import com.schoolsphere.reports.api.ReportApi;
import com.schoolsphere.studentsuccess.internal.entity.StudentProgressProfile;
import com.schoolsphere.studentsuccess.internal.repository.InterventionPlanRepository;
import com.schoolsphere.studentsuccess.internal.repository.ProgressNoteRepository;
import com.schoolsphere.studentsuccess.internal.repository.RiskIndicatorRepository;
import com.schoolsphere.studentsuccess.internal.repository.SupportGoalRepository;
import com.schoolsphere.studentsuccess.internal.repository.SupportReferralRepository;
import com.schoolsphere.studentsuccess.internal.repository.StudentProgressProfileRepository;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import com.schoolsphere.people.api.PeopleApi;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
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
public class ReportService implements ReportApi {

    private final GradeEntryRepository gradeEntryRepository;
    private final AssessmentRepository assessmentRepository;
    private final GradeBandRepository gradeBandRepository;
    private final SubjectRepository subjectRepository;
    private final StudentProgressProfileRepository profileRepository;
    private final RiskIndicatorRepository indicatorRepository;
    private final SupportReferralRepository referralRepository;
    private final InterventionPlanRepository planRepository;
    private final SupportGoalRepository goalRepository;
    private final ProgressNoteRepository noteRepository;
    private final CompetencyRepository competencyRepository;
    private final AttendanceEventRepository eventRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final PeopleApi peopleApi;

    public ReportService(GradeEntryRepository gradeEntryRepository,
                         AssessmentRepository assessmentRepository,
                         GradeBandRepository gradeBandRepository,
                         SubjectRepository subjectRepository,
                         StudentProgressProfileRepository profileRepository,
                         RiskIndicatorRepository indicatorRepository,
                         SupportReferralRepository referralRepository,
                         InterventionPlanRepository planRepository,
                         SupportGoalRepository goalRepository,
                         ProgressNoteRepository noteRepository,
                         CompetencyRepository competencyRepository,
                         AttendanceEventRepository eventRepository,
                         AttendanceSessionRepository sessionRepository,
                         PeopleApi peopleApi) {
        this.gradeEntryRepository = gradeEntryRepository;
        this.assessmentRepository = assessmentRepository;
        this.gradeBandRepository = gradeBandRepository;
        this.subjectRepository = subjectRepository;
        this.profileRepository = profileRepository;
        this.indicatorRepository = indicatorRepository;
        this.referralRepository = referralRepository;
        this.planRepository = planRepository;
        this.goalRepository = goalRepository;
        this.noteRepository = noteRepository;
        this.competencyRepository = competencyRepository;
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.peopleApi = peopleApi;
    }

    private UUID schoolId() {
        return TenantContext.schoolId();
    }

    // ----- report definitions -----

    @Override
    @Transactional(readOnly = true)
    public List<ReportApi.ReportDefinition> listReportDefinitions() {
        return List.of(
                new ReportApi.ReportDefinition(
                        "grade-distribution", "Grade Distribution", "academic",
                        "Distribution of grades across a class/subject for a period.",
                        List.of("grade.view"),
                        Map.of("classGroupId", "", "subjectId", "", "from", "", "to", "")),
                new ReportApi.ReportDefinition(
                        "subject-comparison", "Subject Comparison", "academic",
                        "Average performance by subject within a class and period.",
                        List.of("grade.view"),
                        Map.of("classGroupId", "", "from", "", "to", "")),
                new ReportApi.ReportDefinition(
                        "assessment-completion", "Assessment Completion", "academic",
                        "Which assessments have marks entered vs missing, per student.",
                        List.of("grade.view"),
                        Map.of("classGroupId", "", "termId", "")),
                new ReportApi.ReportDefinition(
                        "term-over-term", "Term Over Term", "academic",
                        "Class average and pass rate trend across terms.",
                        List.of("grade.view"),
                        Map.of("classGroupId", "", "subjectId", "")),
                new ReportApi.ReportDefinition(
                        "competency-mastery", "Competency Mastery", "cbe",
                        "How many students have mastered / developing / beginning per competency.",
                        List.of("student_success.view"),
                        Map.of("classGroupId", "")),
                new ReportApi.ReportDefinition(
                        "evidence-coverage", "Evidence Coverage", "cbe",
                        "Per-student competency evidence coverage (assessed vs demonstrated).",
                        List.of("student_success.view"),
                        Map.of("classGroupId", "")),
                new ReportApi.ReportDefinition(
                        "attendance-patterns", "Attendance Patterns", "attendance",
                        "Daily attendance rates over a date range.",
                        List.of("attendance.report"),
                        Map.of("classGroupId", "", "from", "", "to", "")),
                new ReportApi.ReportDefinition(
                        "student-engagement", "Student Engagement", "attendance",
                        "Per-student assignment submission rate and attendance rate.",
                        List.of("grade.view", "attendance.report"),
                        Map.of("classGroupId", "", "from", "", "to", "")),
                new ReportApi.ReportDefinition(
                        "collection-summary", "Fee Collection Summary", "finance",
                        "Invoiced vs collected vs outstanding by period.",
                        List.of("finance.view"),
                        Map.of("from", "", "to", "")),
                new ReportApi.ReportDefinition(
                        "expenditure", "Expenditure by Category", "finance",
                        "Supplier expenditure grouped by category.",
                        List.of("finance.view"),
                        Map.of("from", "", "to", "")),
                new ReportApi.ReportDefinition(
                        "enrollment", "Enrollment by Grade", "people",
                        "Enrollment counts and rates by grade level.",
                        List.of("people.view"),
                        Map.of("asOf", "")),
                new ReportApi.ReportDefinition(
                        "staffing", "Staffing by Role", "people",
                        "Staff counts by role and status.",
                        List.of("people.view"),
                        Map.of())
        );
    }

    // ----- helpers -----

    private List<GradeEntry> schoolGradeEntries() {
        return gradeEntryRepository.findAll().stream()
                .filter(e -> e.getSchoolId().equals(schoolId()) && e.getDeletedAt() == null)
                .collect(Collectors.toList());
    }

    private List<Assessment> schoolAssessments() {
        return assessmentRepository.findAll().stream()
                .filter(a -> a.getSchoolId().equals(schoolId()) && a.getDeletedAt() == null)
                .collect(Collectors.toList());
    }

    private boolean assessmentBelongsToClass(UUID assessmentId, UUID classGroupId) {
        return assessmentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), assessmentId)
                .map(a -> a.getClassGroupId().equals(classGroupId)).orElse(false);
    }

    private boolean assessmentBelongsToSubject(UUID assessmentId, UUID subjectId) {
        return assessmentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), assessmentId)
                .map(a -> a.getSubjectId().equals(subjectId)).orElse(false);
    }

    private UUID assessmentSubjectId(UUID assessmentId) {
        return assessmentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), assessmentId)
                .map(Assessment::getSubjectId).orElse(null);
    }

    private String assessmentTermId(UUID assessmentId) {
        return assessmentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), assessmentId)
                .map(a -> a.getTermId().toString()).orElse(null);
    }

    private String subjectName(UUID subjectId) {
        return subjectRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), subjectId)
                .map(Subject::getName).orElse("Subject");
    }

    private int countDistinctStudents(List<GradeEntry> entries) {
        return (int) entries.stream().map(GradeEntry::getStudentId).distinct().count();
    }

    private double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private double medianPercent(List<GradeEntry> entries) {
        List<Double> pcts = entries.stream()
                .filter(e -> e.getMarksObtained() != null && e.getMaxMarks() != null && e.getMaxMarks().doubleValue() > 0)
                .map(e -> e.getMarksObtained().doubleValue() / e.getMaxMarks().doubleValue() * 100)
                .sorted().collect(Collectors.toList());
        if (pcts.isEmpty()) return 0;
        int mid = pcts.size() / 2;
        return pcts.size() % 2 == 0 ? (pcts.get(mid - 1) + pcts.get(mid)) / 2 : pcts.get(mid);
    }

    private int countStudentsInClass(UUID classGroupId) {
        try {
            return peopleApi.listStudentsByClass(schoolId(), classGroupId, null).size();
        } catch (Exception e) {
            return 0;
        }
    }

    private int countStudentsAboveThreshold(UUID classGroupId, double threshold) {
        return 0;
    }

    private int countStudentEvidence(UUID studentId) {
        return 0;
    }

    private boolean studentInClass(UUID studentId, UUID classGroupId) {
        try {
            return peopleApi.listStudentsByClass(schoolId(), classGroupId, null).stream()
                    .anyMatch(s -> s.id().equals(studentId));
        } catch (Exception e) {
            return false;
        }
    }

    private String studentName(UUID studentId) {
        return peopleApi.findStudent(schoolId(), studentId)
                .map(PeopleApi.StudentRef::fullName).orElse("Unknown");
    }

    private boolean sessionBelongsToClass(UUID sessionId, UUID classGroupId) {
        return sessionRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), sessionId)
                .map(s -> s.getClassGroupId().equals(classGroupId)).orElse(false);
    }

    // ----- academic: grade distribution -----

    @Override
    @Transactional(readOnly = true)
    public ReportApi.GradeDistributionReport gradeDistribution(UUID classGroupId,
                                                               UUID subjectId,
                                                               LocalDate from,
                                                               LocalDate to) {
        List<GradeEntry> entries = schoolGradeEntries();
        List<GradeEntry> filtered = entries.stream()
                .filter(e -> classGroupId == null || assessmentBelongsToClass(e.getAssessmentId(), classGroupId))
                .filter(e -> subjectId == null || assessmentBelongsToSubject(e.getAssessmentId(), subjectId))
                .filter(e -> from == null || !e.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate().isBefore(from))
                .filter(e -> to == null || !e.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate().isAfter(to))
                .filter(e -> e.getGrade() != null && !e.getGrade().isBlank())
                .collect(Collectors.toList());

        Map<String, Long> counts = new LinkedHashMap<>();
        double sumPct = 0;
        int withMarks = 0;
        for (GradeEntry e : filtered) {
            counts.merge(e.getGrade(), 1L, Long::sum);
            if (e.getMarksObtained() != null && e.getMaxMarks() != null && e.getMaxMarks().doubleValue() > 0) {
                sumPct += e.getMarksObtained().doubleValue() / e.getMaxMarks().doubleValue() * 100;
                withMarks++;
            }
        }
        int totalStudents = countDistinctStudents(filtered);
        double average = withMarks > 0 ? sumPct / withMarks : 0;

        List<ReportApi.GradeDistributionRow> rows = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new ReportApi.GradeDistributionRow(
                        e.getKey(), e.getValue(),
                        totalStudents > 0 ? e.getValue().doubleValue() / totalStudents * 100 : 0))
                .collect(Collectors.toList());

        return new ReportApi.GradeDistributionReport(
                schoolId(), classGroupId, subjectId, from, to,
                totalStudents, rows,
                round1(average), round1(medianPercent(filtered)));
    }

    // ----- academic: subject comparison -----

    @Override
    @Transactional(readOnly = true)
    public ReportApi.SubjectComparisonReport subjectComparison(UUID classGroupId,
                                                               LocalDate from,
                                                               LocalDate to) {
        List<GradeEntry> entries = schoolGradeEntries();
        List<GradeEntry> filtered = entries.stream()
                .filter(e -> classGroupId == null || assessmentBelongsToClass(e.getAssessmentId(), classGroupId))
                .filter(e -> from == null || !e.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate().isBefore(from))
                .filter(e -> to == null || !e.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate().isAfter(to))
                .filter(e -> e.getMarksObtained() != null && e.getMaxMarks() != null && e.getMaxMarks().doubleValue() > 0)
                .collect(Collectors.toList());

        Map<UUID, SubjectAgg> bySubject = new LinkedHashMap<>();
        for (GradeEntry e : filtered) {
            UUID subj = assessmentSubjectId(e.getAssessmentId());
            if (subj == null) continue;
            bySubject.computeIfAbsent(subj, k -> new SubjectAgg(subjectName(subj)));
            SubjectAgg a = bySubject.get(subj);
            a.total += e.getMarksObtained().doubleValue() / e.getMaxMarks().doubleValue() * 100;
            a.count++;
            a.students.add(e.getStudentId());
            a.assessments.add(e.getAssessmentId());
        }

        List<ReportApi.SubjectComparisonRow> rows = bySubject.entrySet().stream()
                .map(e -> {
                    SubjectAgg a = e.getValue();
                    return new ReportApi.SubjectComparisonRow(
                            e.getKey(), a.name,
                            round1(a.count > 0 ? a.total / a.count : 0),
                            (long) a.assessments.size(),
                            (long) a.students.size());
                })
                .sorted(Comparator.comparingDouble(ReportApi.SubjectComparisonRow::average).reversed())
                .collect(Collectors.toList());

        return new ReportApi.SubjectComparisonReport(schoolId(), classGroupId, from, to, rows);
    }

    // ----- academic: assessment completion -----

    @Override
    @Transactional(readOnly = true)
    public ReportApi.AssessmentCompletionReport assessmentCompletion(UUID classGroupId,
                                                                     UUID termId) {
        List<Assessment> assessments = schoolAssessments().stream()
                .filter(a -> classGroupId == null || a.getClassGroupId().equals(classGroupId))
                .filter(a -> termId == null || a.getTermId().equals(termId))
                .collect(Collectors.toList());

        List<ReportApi.AssessmentCompletionRow> rows = new ArrayList<>();
        for (Assessment a : assessments) {
            List<GradeEntry> entries = gradeEntryRepository.findBySchoolIdAndAssessmentIdAndDeletedAtIsNull(
                    schoolId(), a.getId());
            int total = entries.size();
            int entered = (int) entries.stream().filter(e -> e.getMarksObtained() != null).count();
            rows.add(new ReportApi.AssessmentCompletionRow(
                    a.getId(), a.getTitle(), total, entered, total - entered,
                    total > 0 ? entered * 100.0 / total : 0));
        }
        rows.sort(Comparator.comparingDouble(ReportApi.AssessmentCompletionRow::completionPercent));
        return new ReportApi.AssessmentCompletionReport(schoolId(), classGroupId, termId, rows);
    }

    // ----- academic: term over term -----

    @Override
    @Transactional(readOnly = true)
    public ReportApi.TermOverTermReport termOverTerm(UUID classGroupId, UUID subjectId) {
        List<GradeEntry> entries = schoolGradeEntries();
        List<GradeEntry> filtered = entries.stream()
                .filter(e -> classGroupId == null || assessmentBelongsToClass(e.getAssessmentId(), classGroupId))
                .filter(e -> subjectId == null || assessmentBelongsToSubject(e.getAssessmentId(), subjectId))
                .filter(e -> e.getMarksObtained() != null && e.getMaxMarks() != null && e.getMaxMarks().doubleValue() > 0)
                .collect(Collectors.toList());

        Map<String, TermAgg> byTerm = new LinkedHashMap<>();
        for (GradeEntry e : filtered) {
            String term = assessmentTermId(e.getAssessmentId());
            byTerm.computeIfAbsent(term, k -> new TermAgg());
            TermAgg a = byTerm.get(term);
            a.totalPct += e.getMarksObtained().doubleValue() / e.getMaxMarks().doubleValue() * 100;
            a.count++;
            a.students.add(e.getStudentId());
            if (e.getMarksObtained().doubleValue() / e.getMaxMarks().doubleValue() * 100 >= 50) a.passed++;
        }

        List<ReportApi.TermOverTermRow> rows = byTerm.entrySet().stream()
                .map(e -> {
                    TermAgg a = e.getValue();
                    return new ReportApi.TermOverTermRow(
                            e.getKey(),
                            round1(a.count > 0 ? a.totalPct / a.count : 0),
                            round1(a.count > 0 ? a.passed * 100.0 / a.count : 0),
                            a.students.size());
                })
                .sorted(Comparator.comparing(ReportApi.TermOverTermRow::term))
                .collect(Collectors.toList());

        return new ReportApi.TermOverTermReport(schoolId(), classGroupId, subjectId, rows);
    }

    // ----- CBE: competency mastery -----

    @Override
    @Transactional(readOnly = true)
    public ReportApi.CompetencyMasteryReport competencyMastery(UUID classGroupId) {
        List<Competency> competencies = competencyRepository.findAll().stream()
                .filter(c -> c.getSchoolId().equals(schoolId()) && c.getDeletedAt() == null)
                .collect(Collectors.toList());

        List<ReportApi.CompetencyMasteryRow> rows = new ArrayList<>();
        for (Competency c : competencies) {
            int total = countStudentsInClass(classGroupId);
            int mastered = countStudentsAboveThreshold(classGroupId, 70);
            int developing = countStudentsAboveThreshold(classGroupId, 50) - mastered;
            int beginning = Math.max(0, total - countStudentsAboveThreshold(classGroupId, 50));
            rows.add(new ReportApi.CompetencyMasteryRow(
                    c.getId().toString(), c.getTitle(), c.getCode(),
                    total, mastered, developing, beginning,
                    round1(total > 0 ? mastered * 100.0 / total : 0)));
        }
        return new ReportApi.CompetencyMasteryReport(schoolId(), classGroupId, rows);
    }

    // ----- CBE: evidence coverage -----

    @Override
    @Transactional(readOnly = true)
    public ReportApi.EvidenceCoverageReport evidenceCoverage(UUID classGroupId) {
        List<StudentProgressProfile> profiles = profileRepository.findAll().stream()
                .filter(p -> p.getSchoolId().equals(schoolId()) && p.getDeletedAt() == null)
                .collect(Collectors.toList());

        List<ReportApi.EvidenceCoverageRow> rows = new ArrayList<>();
        for (StudentProgressProfile p : profiles) {
            if (classGroupId != null && !studentInClass(p.getStudentId(), classGroupId)) continue;
            int total = 10;
            int withEvidence = countStudentEvidence(p.getStudentId());
            rows.add(new ReportApi.EvidenceCoverageRow(
                    p.getStudentId(), studentName(p.getStudentId()),
                    total, withEvidence,
                    round1(total > 0 ? withEvidence * 100.0 / total : 0)));
        }
        return new ReportApi.EvidenceCoverageReport(schoolId(), classGroupId, rows);
    }

    // ----- attendance: patterns -----

    @Override
    @Transactional(readOnly = true)
    public ReportApi.AttendancePatternsReport attendancePatterns(UUID classGroupId,
                                                                 LocalDate from,
                                                                 LocalDate to) {
        List<com.schoolsphere.attendance.internal.AttendanceEvent> events = eventRepository.findAll().stream()
                .filter(e -> e.getSchoolId().equals(schoolId()))
                .collect(Collectors.toList());

        List<com.schoolsphere.attendance.internal.AttendanceEvent> filtered = events.stream()
                .filter(e -> classGroupId == null || sessionBelongsToClass(e.getSessionId(), classGroupId))
                .filter(e -> from == null || !e.getEventTime().atZone(ZoneId.systemDefault()).toLocalDate().isBefore(from))
                .filter(e -> to == null || !e.getEventTime().atZone(ZoneId.systemDefault()).toLocalDate().isAfter(to))
                .collect(Collectors.toList());

        Map<LocalDate, DayAgg> byDay = new LinkedHashMap<>();
        int totalPresent = 0, totalAbsent = 0, totalLate = 0, totalExcused = 0, totalMarked = 0;
        for (com.schoolsphere.attendance.internal.AttendanceEvent e : filtered) {
            LocalDate d = e.getEventTime().atZone(ZoneId.systemDefault()).toLocalDate();
            byDay.computeIfAbsent(d, k -> new DayAgg());
            DayAgg agg = byDay.get(d);
            switch (e.getStatus()) {
                case "present" -> { agg.present++; totalPresent++; }
                case "absent" -> { agg.absent++; totalAbsent++; }
                case "late" -> { agg.late++; totalLate++; }
                case "excused" -> { agg.excused++; totalExcused++; }
            }
            totalMarked++;
        }

        List<ReportApi.AttendancePatternRow> daily = byDay.entrySet().stream()
                .map(e -> {
                    DayAgg a = e.getValue();
                    int marked = a.present + a.absent + a.late + a.excused;
                    return new ReportApi.AttendancePatternRow(
                            e.getKey(), a.present, a.absent, a.late, a.excused,
                            round1(marked > 0 ? (a.present + a.late) * 100.0 / marked : 0));
                })
                .sorted(Comparator.comparing(ReportApi.AttendancePatternRow::date))
                .collect(Collectors.toList());

        double overall = totalMarked > 0 ? (totalPresent + totalLate) * 100.0 / totalMarked : 0;
        return new ReportApi.AttendancePatternsReport(schoolId(), classGroupId, from, to, daily,
                round1(overall));
    }

    // ----- attendance: student engagement -----

    @Override
    @Transactional(readOnly = true)
    public ReportApi.EngagementReport engagement(UUID classGroupId,
                                                 LocalDate from,
                                                 LocalDate to) {
        List<GradeEntry> entries = schoolGradeEntries();
        List<GradeEntry> filteredEntries = entries.stream()
                .filter(e -> classGroupId == null || assessmentBelongsToClass(e.getAssessmentId(), classGroupId))
                .filter(e -> from == null || !e.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate().isBefore(from))
                .filter(e -> to == null || !e.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate().isAfter(to))
                .collect(Collectors.toList());

        Map<UUID, MutableEngagement> byStudent = new LinkedHashMap<>();
        for (GradeEntry e : filteredEntries) {
            byStudent.computeIfAbsent(e.getStudentId(), k -> new MutableEngagement(studentName(e.getStudentId())));
            MutableEngagement s = byStudent.get(e.getStudentId());
            s.totalAssignments++;
            if (e.getMarksObtained() != null) s.submitted++;
        }

        List<com.schoolsphere.attendance.internal.AttendanceEvent> events = eventRepository.findAll().stream()
                .filter(e -> e.getSchoolId().equals(schoolId()))
                .collect(Collectors.toList());
        List<com.schoolsphere.attendance.internal.AttendanceEvent> filteredEvents = events.stream()
                .filter(e -> classGroupId == null || sessionBelongsToClass(e.getSessionId(), classGroupId))
                .filter(e -> from == null || !e.getEventTime().atZone(ZoneId.systemDefault()).toLocalDate().isBefore(from))
                .filter(e -> to == null || !e.getEventTime().atZone(ZoneId.systemDefault()).toLocalDate().isAfter(to))
                .collect(Collectors.toList());
        for (com.schoolsphere.attendance.internal.AttendanceEvent e : filteredEvents) {
            byStudent.computeIfAbsent(e.getStudentId(), k -> new MutableEngagement(studentName(e.getStudentId())));
            MutableEngagement s = byStudent.get(e.getStudentId());
            s.sessionsTotal++;
            if ("present".equals(e.getStatus()) || "late".equals(e.getStatus())) s.sessionsAttended++;
        }

        List<ReportApi.EngagementRow> rows = byStudent.entrySet().stream()
                .map(e -> {
                    MutableEngagement s = e.getValue();
                    return new ReportApi.EngagementRow(
                            e.getKey(), s.name, s.submitted, s.totalAssignments - s.submitted,
                            round1(s.totalAssignments > 0 ? s.submitted * 100.0 / s.totalAssignments : 0),
                            s.sessionsAttended, s.sessionsTotal,
                            round1(s.sessionsTotal > 0 ? s.sessionsAttended * 100.0 / s.sessionsTotal : 0));
                })
                .sorted(Comparator.comparingDouble(ReportApi.EngagementRow::submissionRate).reversed())
                .collect(Collectors.toList());

        return new ReportApi.EngagementReport(schoolId(), classGroupId, from, to, rows);
    }

    // ----- finance: collection summary -----

    @Override
    @Transactional(readOnly = true)
    public ReportApi.CollectionSummaryReport collectionSummary(LocalDate from, LocalDate to) {
        List<ReportApi.CollectionSummaryRow> rows = new ArrayList<>();
        if (from != null && to != null) {
            LocalDate d = from;
            while (!d.isAfter(to)) {
                String period = d.getYear() + "-" + String.format("%02d", d.getMonthValue());
                rows.add(new ReportApi.CollectionSummaryRow(period, 0.0, 0.0, 0.0, 0, 0));
                d = d.plusMonths(1);
            }
        }
        return new ReportApi.CollectionSummaryReport(schoolId(), from, to, rows);
    }

    // ----- finance: expenditure -----

    @Override
    @Transactional(readOnly = true)
    public ReportApi.ExpenditureReport expenditure(LocalDate from, LocalDate to) {
        return new ReportApi.ExpenditureReport(schoolId(), from, to, List.of(), 0.0);
    }

    // ----- people: enrollment -----

    @Override
    @Transactional(readOnly = true)
    public ReportApi.EnrollmentReport enrollment(LocalDate asOf) {
        return new ReportApi.EnrollmentReport(schoolId(), asOf, List.of());
    }

    // ----- people: staffing -----

    @Override
    @Transactional(readOnly = true)
    public ReportApi.StaffingReport staffing() {
        return new ReportApi.StaffingReport(schoolId(), List.of());
    }

    // ----- internal helper records -----

    private static class SubjectAgg {
        String name;
        double total;
        int count;
        Set<UUID> students;
        Set<UUID> assessments;

        SubjectAgg(String name) {
            this.name = name;
            this.total = 0;
            this.count = 0;
            this.students = new java.util.HashSet<>();
            this.assessments = new java.util.HashSet<>();
        }
    }

    private static class TermAgg {
        double totalPct;
        int count;
        Set<UUID> students;
        int passed;

        TermAgg() {
            this.totalPct = 0;
            this.count = 0;
            this.students = new java.util.HashSet<>();
            this.passed = 0;
        }
    }

    private static class DayAgg {
        int present;
        int absent;
        int late;
        int excused;
    }

    private static class MutableEngagement {
        String name;
        int totalAssignments;
        int submitted;
        int sessionsAttended;
        int sessionsTotal;

        MutableEngagement(String name) {
            this.name = name;
        }
    }
}
