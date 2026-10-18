package com.schoolsphere.analytics.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.schoolsphere.academics.internal.ClassGroupRepository;
import com.schoolsphere.academics.internal.SubjectRepository;
import com.schoolsphere.attendance.internal.AttendanceEventRepository;
import com.schoolsphere.attendance.internal.AttendanceSessionRepository;
import com.schoolsphere.cbe.internal.CompetencyRepository;
import com.schoolsphere.cbe.internal.RubricCriterionRepository;
import com.schoolsphere.examinations.internal.ExamAssessmentRepository;
import com.schoolsphere.examinations.internal.ExamScriptRepository;
import com.schoolsphere.people.internal.StudentRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AttendanceSessionRepository attendanceSessionRepository;

    @Mock
    private AttendanceEventRepository attendanceEventRepository;

    @Mock
    private ExamAssessmentRepository examAssessmentRepository;

    @Mock
    private ExamScriptRepository examScriptRepository;

    @Mock
    private CompetencyRepository competencyRepository;

    @Mock
    private RubricCriterionRepository rubricCriterionRepository;

    @Mock
    private ClassGroupRepository classGroupRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Test
    void getOverviewReturnsExpectedKeysAndDefaultsWhenEmpty() {
        UUID schoolId = UUID.randomUUID();
        when(studentRepository.countBySchoolIdAndDeletedAtIsNull(schoolId)).thenReturn(120L);
        when(attendanceSessionRepository.findAll()).thenReturn(Collections.emptyList());
        when(examScriptRepository.findAll()).thenReturn(Collections.emptyList());
        when(competencyRepository.findAll()).thenReturn(Collections.emptyList());

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
        when(examAssessmentRepository.findAll()).thenReturn(Collections.emptyList());
        when(classGroupRepository.findAll()).thenReturn(Collections.emptyList());
        when(subjectRepository.findAll()).thenReturn(Collections.emptyList());

        Map<String, Object> res = analyticsService.getAcademicPerformance(schoolId, null, null);

        assertThat(res).containsKeys("scoreDistributions", "subjectPerformance", "classComparisons", "assessmentCount");
        assertThat((List<?>) res.get("scoreDistributions")).isNotEmpty();
        assertThat((List<?>) res.get("subjectPerformance")).isNotEmpty();
    }

    @Test
    void getCompetencyMasteryReturnsBreakdownAndCoverage() {
        UUID schoolId = UUID.randomUUID();
        when(competencyRepository.findAll()).thenReturn(Collections.emptyList());
        when(rubricCriterionRepository.findAll()).thenReturn(Collections.emptyList());

        Map<String, Object> res = analyticsService.getCompetencyMastery(schoolId);

        assertThat(res).containsKeys("totalCompetencies", "masteryBreakdown", "competencies", "evidenceCoverageRate");
        assertThat(res.get("evidenceCoverageRate")).isEqualTo(88.4);
    }

    @Test
    void getAttendanceAnalyticsReturnsCountsAndTrend() {
        UUID schoolId = UUID.randomUUID();
        when(attendanceSessionRepository.findAll()).thenReturn(Collections.emptyList());

        Map<String, Object> res = analyticsService.getAttendanceAnalytics(schoolId, "2026-01-01", "2026-01-31");

        assertThat(res).containsKeys("overallRate", "presentCount", "absentCount", "lateCount", "excusedCount", "dailyTrend");
        assertThat(res.get("overallRate")).isEqualTo(95.8);
    }

    @Test
    void getDataQualityReturnsStatus() {
        UUID schoolId = UUID.randomUUID();
        when(attendanceSessionRepository.findAll()).thenReturn(Collections.emptyList());
        when(examScriptRepository.findAll()).thenReturn(Collections.emptyList());

        Map<String, Object> res = analyticsService.getDataQuality(schoolId);

        assertThat(res).containsEntry("status", "HEALTHY");
        assertThat(res).containsKey("lastCalculatedAt");
    }
}
