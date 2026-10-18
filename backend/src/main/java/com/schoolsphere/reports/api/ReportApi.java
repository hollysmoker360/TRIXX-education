package com.schoolsphere.reports.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Public API of the Reports & Analytics module (spec section 5).
 * Cross-module aggregation endpoints. Every report enforces the same permissions as the
 * screen from which it is generated, and exports inherit the caller's tenant context.
 */
public interface ReportApi {

    record ReportDefinition(
            String id,
            String title,
            String category, // academic | cbe | attendance | finance | people | operations
            String description,
            List<String> requiredPermissions,
            Map<String, String> defaultFilters) {
    }

    record GradeDistributionRow(
            String grade,
            Long count,
            Double percent) {
    }

    record GradeDistributionReport(
            UUID schoolId,
            UUID classGroupId,
            UUID subjectId,
            LocalDate from,
            LocalDate to,
            Integer totalStudents,
            List<GradeDistributionRow> distribution,
            Double average,
            Double median) {
    }

    record SubjectComparisonRow(
            UUID subjectId,
            String subjectName,
            Double average,
            Long assessments,
            Long studentsAssessed) {
    }

    record SubjectComparisonReport(
            UUID schoolId,
            UUID classGroupId,
            LocalDate from,
            LocalDate to,
            List<SubjectComparisonRow> subjects) {
    }

    record AssessmentCompletionRow(
            UUID assessmentId,
            String assessmentTitle,
            Integer totalStudents,
            Integer entered,
            Integer missing,
            Double completionPercent) {
    }

    record AssessmentCompletionReport(
            UUID schoolId,
            UUID classGroupId,
            UUID termId,
            List<AssessmentCompletionRow> assessments) {
    }

    record TermOverTermRow(
            String term,
            Double average,
            Double passRate,
            Integer studentsAssessed) {
    }

    record TermOverTermReport(
            UUID schoolId,
            UUID classGroupId,
            UUID subjectId,
            List<TermOverTermRow> terms) {
    }

    record CompetencyMasteryRow(
            String competencyId,
            String competencyName,
            String strand,
            Integer totalStudents,
            Integer mastered,
            Integer developing,
            Integer beginning,
            Double masteryPercent) {
    }

    record CompetencyMasteryReport(
            UUID schoolId,
            UUID classGroupId,
            List<CompetencyMasteryRow> competencies) {
    }

    record EvidenceCoverageRow(
            UUID studentId,
            String studentName,
            Integer totalCompetencies,
            Integer withEvidence,
            Double coveragePercent) {
    }

    record EvidenceCoverageReport(
            UUID schoolId,
            UUID classGroupId,
            List<EvidenceCoverageRow> students) {
    }

    record AttendancePatternRow(
            LocalDate date,
            Integer present,
            Integer absent,
            Integer late,
            Integer excused,
            Double attendanceRate) {
    }

    record AttendancePatternsReport(
            UUID schoolId,
            UUID classGroupId,
            LocalDate from,
            LocalDate to,
            List<AttendancePatternRow> daily,
            Double overallRate) {
    }

    record EngagementRow(
            UUID studentId,
            String studentName,
            Integer assignmentsSubmitted,
            Integer assignmentsMissing,
            Double submissionRate,
            Integer sessionsAttended,
            Integer sessionsTotal,
            Double attendanceRate) {
    }

    record EngagementReport(
            UUID schoolId,
            UUID classGroupId,
            LocalDate from,
            LocalDate to,
            List<EngagementRow> students) {
    }

    record CollectionSummaryRow(
            String period,
            Double invoiced,
            Double collected,
            Double outstanding,
            Integer invoiceCount,
            Integer paidCount) {
    }

    record CollectionSummaryReport(
            UUID schoolId,
            LocalDate from,
            LocalDate to,
            List<CollectionSummaryRow> periods) {
    }

    record ExpenditureRow(
            String category,
            String supplierName,
            Double amount,
            Integer invoiceCount) {
    }

    record ExpenditureReport(
            UUID schoolId,
            LocalDate from,
            LocalDate to,
            List<ExpenditureRow> items,
            Double total) {
    }

    record EnrollmentRow(
            String grade,
            Integer totalEnrolled,
            Integer present,
            Integer absent,
            Double enrollmentRate) {
    }

    record EnrollmentReport(
            UUID schoolId,
            LocalDate asOf,
            List<EnrollmentRow> grades) {
    }

    record StaffingRow(
            String role,
            Integer totalStaff,
            Integer active,
            Integer onLeave) {
    }

    record StaffingReport(
            UUID schoolId,
            List<StaffingRow> roles) {
    }

    List<ReportDefinition> listReportDefinitions();

    GradeDistributionReport gradeDistribution(
            UUID classGroupId, UUID subjectId, LocalDate from, LocalDate to);

    SubjectComparisonReport subjectComparison(
            UUID classGroupId, LocalDate from, LocalDate to);

    AssessmentCompletionReport assessmentCompletion(
            UUID classGroupId, UUID termId);

    TermOverTermReport termOverTerm(
            UUID classGroupId, UUID subjectId);

    CompetencyMasteryReport competencyMastery(UUID classGroupId);

    EvidenceCoverageReport evidenceCoverage(UUID classGroupId);

    AttendancePatternsReport attendancePatterns(
            UUID classGroupId, LocalDate from, LocalDate to);

    EngagementReport engagement(
            UUID classGroupId, LocalDate from, LocalDate to);

    CollectionSummaryReport collectionSummary(LocalDate from, LocalDate to);

    ExpenditureReport expenditure(LocalDate from, LocalDate to);

    EnrollmentReport enrollment(LocalDate asOf);

    StaffingReport staffing();
}
