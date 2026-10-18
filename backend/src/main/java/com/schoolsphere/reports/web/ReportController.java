package com.schoolsphere.reports.web;

import com.schoolsphere.reports.api.ReportApi;
import com.schoolsphere.reports.internal.ReportService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/definitions")
    @PreAuthorize("@policy.can('grade.view') or @policy.can('attendance.report') or @policy.can('student_success.view') or @policy.can('people.view') or @policy.can('finance.view')")
    public List<ReportApi.ReportDefinition> listDefinitions() {
        return reportService.listReportDefinitions();
    }

    // ----- academic -----

    @GetMapping("/academic/grade-distribution")
    @PreAuthorize("@policy.can('grade.view')")
    public ReportApi.GradeDistributionReport gradeDistribution(
            @RequestParam(required = false) UUID classGroupId,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return reportService.gradeDistribution(classGroupId, subjectId, from, to);
    }

    @GetMapping("/academic/subject-comparison")
    @PreAuthorize("@policy.can('grade.view')")
    public ReportApi.SubjectComparisonReport subjectComparison(
            @RequestParam(required = false) UUID classGroupId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return reportService.subjectComparison(classGroupId, from, to);
    }

    @GetMapping("/academic/assessment-completion")
    @PreAuthorize("@policy.can('grade.view')")
    public ReportApi.AssessmentCompletionReport assessmentCompletion(
            @RequestParam(required = false) UUID classGroupId,
            @RequestParam(required = false) UUID termId) {
        return reportService.assessmentCompletion(classGroupId, termId);
    }

    @GetMapping("/academic/term-over-term")
    @PreAuthorize("@policy.can('grade.view')")
    public ReportApi.TermOverTermReport termOverTerm(
            @RequestParam(required = false) UUID classGroupId,
            @RequestParam(required = false) UUID subjectId) {
        return reportService.termOverTerm(classGroupId, subjectId);
    }

    // ----- CBE -----

    @GetMapping("/cbe/competency-mastery")
    @PreAuthorize("@policy.can('student_success.view')")
    public ReportApi.CompetencyMasteryReport competencyMastery(
            @RequestParam(required = false) UUID classGroupId) {
        return reportService.competencyMastery(classGroupId);
    }

    @GetMapping("/cbe/evidence-coverage")
    @PreAuthorize("@policy.can('student_success.view')")
    public ReportApi.EvidenceCoverageReport evidenceCoverage(
            @RequestParam(required = false) UUID classGroupId) {
        return reportService.evidenceCoverage(classGroupId);
    }

    // ----- attendance -----

    @GetMapping("/attendance/patterns")
    @PreAuthorize("@policy.can('attendance.report')")
    public ReportApi.AttendancePatternsReport attendancePatterns(
            @RequestParam(required = false) UUID classGroupId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return reportService.attendancePatterns(classGroupId, from, to);
    }

    @GetMapping("/attendance/engagement")
    @PreAuthorize("@policy.can('attendance.report') or @policy.can('grade.view')")
    public ReportApi.EngagementReport engagement(
            @RequestParam(required = false) UUID classGroupId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return reportService.engagement(classGroupId, from, to);
    }

    // ----- finance -----

    @GetMapping("/finance/collection-summary")
    @PreAuthorize("@policy.can('finance.view')")
    public ReportApi.CollectionSummaryReport collectionSummary(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return reportService.collectionSummary(from, to);
    }

    @GetMapping("/finance/expenditure")
    @PreAuthorize("@policy.can('finance.view')")
    public ReportApi.ExpenditureReport expenditure(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        return reportService.expenditure(from, to);
    }

    // ----- people -----

    @GetMapping("/people/enrollment")
    @PreAuthorize("@policy.can('people.view')")
    public ReportApi.EnrollmentReport enrollment(
            @RequestParam(required = false) LocalDate asOf) {
        return reportService.enrollment(asOf);
    }

    @GetMapping("/people/staffing")
    @PreAuthorize("@policy.can('people.view')")
    public ReportApi.StaffingReport staffing() {
        return reportService.staffing();
    }
}
