package com.schoolsphere.analytics.web;

import com.schoolsphere.analytics.internal.AnalyticsService;
import com.schoolsphere.common.tenant.TenantContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    @PreAuthorize("@policy.can('attendance.report')")
    public ResponseEntity<Map<String, Object>> getOverview() {
        UUID schoolId = TenantContext.schoolId();
        return ResponseEntity.ok(analyticsService.getOverview(schoolId));
    }

    @GetMapping("/academic-performance")
    @PreAuthorize("@policy.can('attendance.report')")
    public ResponseEntity<Map<String, Object>> getAcademicPerformance(
            @RequestParam(required = false) UUID classGroupId,
            @RequestParam(required = false) UUID subjectId) {
        UUID schoolId = TenantContext.schoolId();
        return ResponseEntity.ok(analyticsService.getAcademicPerformance(schoolId, classGroupId, subjectId));
    }

    @GetMapping("/competency-mastery")
    @PreAuthorize("@policy.can('attendance.report')")
    public ResponseEntity<Map<String, Object>> getCompetencyMastery() {
        UUID schoolId = TenantContext.schoolId();
        return ResponseEntity.ok(analyticsService.getCompetencyMastery(schoolId));
    }

    @GetMapping("/attendance")
    @PreAuthorize("@policy.can('attendance.report')")
    public ResponseEntity<Map<String, Object>> getAttendanceAnalytics(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        UUID schoolId = TenantContext.schoolId();
        return ResponseEntity.ok(analyticsService.getAttendanceAnalytics(schoolId, from, to));
    }

    @GetMapping("/data-quality")
    @PreAuthorize("@policy.can('attendance.report')")
    public ResponseEntity<Map<String, Object>> getDataQuality() {
        UUID schoolId = TenantContext.schoolId();
        return ResponseEntity.ok(analyticsService.getDataQuality(schoolId));
    }
}
