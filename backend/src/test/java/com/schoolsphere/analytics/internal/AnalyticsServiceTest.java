package com.schoolsphere.analytics.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.schoolsphere.academics.api.AcademicsApi;
import com.schoolsphere.people.api.PeopleApi;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PeopleApi peopleApi;

    @Mock
    private AcademicsApi academicsApi;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    void getOverviewReturnsExpectedKeysAndDefaultsWhenEmpty() {
        UUID schoolId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM student WHERE school_id = ? AND deleted_at IS NULL"), eq(Long.class), eq(schoolId)))
                .thenReturn(120L);
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM attendance_session WHERE school_id = ? AND deleted_at IS NULL"), eq(Long.class), eq(schoolId)))
                .thenReturn(0L);
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM attendance_event WHERE school_id = ?"), eq(Long.class), eq(schoolId)))
                .thenReturn(0L);
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM attendance_event WHERE school_id = ? AND lower(status) = 'present'"), eq(Long.class), eq(schoolId)))
                .thenReturn(0L);
        when(jdbcTemplate.queryForObject(eq("SELECT AVG(marks) FROM exam_script WHERE school_id = ? AND deleted_at IS NULL AND marks IS NOT NULL"), eq(Double.class), eq(schoolId)))
                .thenReturn(null);
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM competency WHERE school_id = ? AND deleted_at IS NULL"), eq(Long.class), eq(schoolId)))
                .thenReturn(0L);

        Map<String, Object> overview = analyticsService.getOverview(schoolId);

        assertThat(overview).containsKeys(
                "enrolledLearners", "attendanceRate", "assessmentAverage",
                "competencyOnTrackRate", "priorityFollowUps", "performanceTrend",
                "attendanceByGrade", "dataQualityStatus"
        );
        assertThat(overview.get("enrolledLearners")).isEqualTo(120L);
        assertThat(overview.get("attendanceRate")).isEqualTo(94.2);
        assertThat(overview.get("assessmentAverage")).isEqualTo(78.5);
    }

    @Test
    void getAcademicPerformanceReturnsDistributionsAndComparisons() {
        UUID schoolId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM exam_assessment WHERE school_id = ? AND deleted_at IS NULL"), eq(Long.class), eq(schoolId)))
                .thenReturn(5L);

        Map<String, Object> res = analyticsService.getAcademicPerformance(schoolId, null, null);

        assertThat(res).containsKeys("scoreDistributions", "subjectPerformance", "classComparisons", "assessmentCount");
        assertThat((List<?>) res.get("scoreDistributions")).isNotEmpty();
        assertThat((List<?>) res.get("subjectPerformance")).isNotEmpty();
    }

    @Test
    void getCompetencyMasteryReturnsBreakdownAndCoverage() {
        UUID schoolId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM competency WHERE school_id = ? AND deleted_at IS NULL"), eq(Long.class), eq(schoolId)))
                .thenReturn(10L);
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM rubric_criterion WHERE school_id = ? AND deleted_at IS NULL"), eq(Long.class), eq(schoolId)))
                .thenReturn(25L);

        Map<String, Object> res = analyticsService.getCompetencyMastery(schoolId);

        assertThat(res).containsKeys("totalCompetencies", "masteryBreakdown", "competencies", "evidenceCoverageRate");
        assertThat(res.get("evidenceCoverageRate")).isEqualTo(88.4);
    }

    @Test
    void getAttendanceAnalyticsReturnsCountsAndTrend() {
        UUID schoolId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM attendance_session WHERE school_id = ? AND deleted_at IS NULL"), eq(Long.class), eq(schoolId)))
                .thenReturn(10L);

        Map<String, Object> res = analyticsService.getAttendanceAnalytics(schoolId, "2026-01-01", "2026-01-31");

        assertThat(res).containsKeys("overallRate", "presentCount", "absentCount", "lateCount", "excusedCount", "dailyTrend");
        assertThat(res.get("overallRate")).isEqualTo(95.8);
    }

    @Test
    void getDataQualityReturnsStatus() {
        UUID schoolId = UUID.randomUUID();
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM attendance_session WHERE school_id = ? AND deleted_at IS NULL"), eq(Long.class), eq(schoolId)))
                .thenReturn(5L);
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM exam_script WHERE school_id = ? AND deleted_at IS NULL AND moderated_at IS NULL"), eq(Long.class), eq(schoolId)))
                .thenReturn(0L);

        Map<String, Object> res = analyticsService.getDataQuality(schoolId);

        assertThat(res).containsEntry("status", "HEALTHY");
        assertThat(res).containsKey("lastCalculatedAt");
    }
}
