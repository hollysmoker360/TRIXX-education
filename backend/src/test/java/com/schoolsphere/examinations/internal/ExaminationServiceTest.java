package com.schoolsphere.examinations.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schoolsphere.audit.api.AuditLogService;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.examinations.internal.ExaminationService.AssessmentInput;
import com.schoolsphere.examinations.internal.ExaminationService.ResultView;
import com.schoolsphere.people.api.PeopleApi;
import com.schoolsphere.people.api.PeopleApi.StudentRef;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Examination workflow rules: approval states, candidate registration, marking and publication. */
class ExaminationServiceTest {

    private ExamAssessmentRepository assessments;
    private ExamSessionRepository sessions;
    private ExamScriptRepository scripts;
    private PeopleApi people;
    private AuditLogService audit;
    private ExaminationService service;
    private UUID schoolId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        assessments = mock(ExamAssessmentRepository.class);
        sessions = mock(ExamSessionRepository.class);
        scripts = mock(ExamScriptRepository.class);
        people = mock(PeopleApi.class);
        audit = mock(AuditLogService.class);
        service = new ExaminationService(assessments, sessions, scripts, people, audit);
        schoolId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        TenantContext.set(schoolId, actorId, UUID.randomUUID(), "device-1");
        when(assessments.save(any())).thenAnswer(i -> withId(i.getArgument(0)));
        when(sessions.save(any())).thenAnswer(i -> withId(i.getArgument(0)));
        when(scripts.save(any())).thenAnswer(i -> withId(i.getArgument(0)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createsDraftAssessmentAndAuditsIt() {
        var view = service.createAssessment(new AssessmentInput(" Term 1 Maths ", "exam", null, null, null,
                new BigDecimal("100"), new BigDecimal("40"), 120, null));

        assertThat(view.status()).isEqualTo("DRAFT");
        assertThat(view.title()).isEqualTo("Term 1 Maths");
        assertThat(view.assessmentType()).isEqualTo("EXAM");
        verify(audit).record(eq("exam.assessment.created"), eq("exam_assessment"), any(), isNull(), any());
    }

    @Test
    void rejectsPassMarkAboveTotal() {
        assertThatThrownBy(() -> service.createAssessment(new AssessmentInput("Quiz", null, null, null, null,
                new BigDecimal("50"), new BigDecimal("60"), null, null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("Pass mark");
    }

    @Test
    void approvalRequiresReviewState() {
        ExamAssessment draft = assessment("DRAFT", null);
        assertThatThrownBy(() -> service.approve(draft.getId())).isInstanceOf(ApiException.class);

        draft.setStatus("IN_REVIEW");
        var approved = service.approve(draft.getId());
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(draft.getApprovedBy()).isEqualTo(actorId);
    }

    @Test
    void approvedAssessmentCannotBeEdited() {
        ExamAssessment approved = assessment("APPROVED", null);
        assertThatThrownBy(() -> service.updateAssessment(approved.getId(),
                new AssessmentInput("New", null, null, null, null, null, null, null, null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("draft");
    }

    @Test
    void schedulingRegistersClassOnceAndSkipsExistingCandidates() {
        ExamAssessment a = assessment("APPROVED", 60);
        a.setClassGroupId(UUID.randomUUID());
        StudentRef s1 = student();
        StudentRef s2 = student();
        when(people.listStudentsByClass(schoolId, a.getClassGroupId(), null)).thenReturn(List.of(s1, s2));
        when(people.findStudents(eq(schoolId), anyList())).thenReturn(List.of(s1, s2));
        ExamScript existing = new ExamScript();
        existing.setStudentId(s1.id());
        when(scripts.findBySchoolIdAndAssessmentIdAndDeletedAtIsNull(schoolId, a.getId())).thenReturn(List.of(existing));

        Instant start = Instant.parse("2026-11-02T08:00:00Z");
        service.createSession(a.getId(), null, start, start.plusSeconds(3600), "Hall A", null);

        ArgumentCaptor<ExamScript> saved = ArgumentCaptor.forClass(ExamScript.class);
        verify(scripts, times(1)).save(saved.capture());
        assertThat(saved.getValue().getStudentId()).isEqualTo(s2.id());
        assertThat(saved.getValue().getSeatNumber()).isEqualTo("001");
    }

    @Test
    void schedulingRejectsDraftsAndShortWindows() {
        ExamAssessment draft = assessment("DRAFT", 90);
        Instant start = Instant.parse("2026-11-02T08:00:00Z");
        assertThatThrownBy(() -> service.createSession(draft.getId(), null, start, start.plusSeconds(7200), null, null))
                .isInstanceOf(ApiException.class).hasMessageContaining("approved");

        draft.setStatus("APPROVED");
        assertThatThrownBy(() -> service.createSession(draft.getId(), null, start, start.plusSeconds(3600), null, null))
                .isInstanceOf(ApiException.class).hasMessageContaining("90-minute");
        verify(sessions, never()).save(any());
    }

    @Test
    void marksMustBeWithinRangeAndSittingCompleted() {
        ExamAssessment a = assessment("PUBLISHED", null);
        ExamSession session = session(a, "IN_PROGRESS");
        ExamScript script = script(a, session, "PENDING");

        assertThatThrownBy(() -> service.markScript(script.getId(), BigDecimal.TEN, false, null))
                .isInstanceOf(ApiException.class).hasMessageContaining("completed");

        session.setStatus("COMPLETED");
        assertThatThrownBy(() -> service.markScript(script.getId(), new BigDecimal("101"), false, null))
                .isInstanceOf(ApiException.class).hasMessageContaining("between 0 and 100");

        when(people.findStudents(eq(schoolId), anyList())).thenReturn(List.of());
        var marked = service.markScript(script.getId(), new BigDecimal("72.5"), false, "Good");
        assertThat(marked.status()).isEqualTo("MARKED");
        assertThat(marked.marks()).isEqualByComparingTo("72.5");
        verify(audit).record(eq("exam.script.marked"), eq("exam_script"), eq(script.getId()), any(), any());
    }

    @Test
    void markerCannotModerateOwnScript() {
        ExamAssessment a = assessment("PUBLISHED", null);
        ExamSession session = session(a, "COMPLETED");
        ExamScript script = script(a, session, "MARKED");
        script.setMarks(BigDecimal.TEN);
        script.setMarkedBy(actorId);

        assertThatThrownBy(() -> service.moderateScript(script.getId(), null, null))
                .isInstanceOf(ApiException.class).hasMessageContaining("different examiner");
    }

    @Test
    void publishingNeedsEveryCandidateMarkedAndLocksMarks() {
        ExamAssessment a = assessment("PUBLISHED", null);
        a.setPassMark(new BigDecimal("50"));
        ExamSession session = session(a, "COMPLETED");
        ExamScript pass = script(a, session, "MARKED");
        pass.setMarks(new BigDecimal("80"));
        ExamScript pending = script(a, session, "PENDING");
        when(scripts.findBySchoolIdAndAssessmentIdAndDeletedAtIsNull(schoolId, a.getId()))
                .thenReturn(List.of(pass, pending));

        assertThatThrownBy(() -> service.publishResults(a.getId()))
                .isInstanceOf(ApiException.class).hasMessageContaining("marked or recorded absent");

        pending.setStatus("ABSENT");
        ResultView result = service.publishResults(a.getId());
        assertThat(result.status()).isEqualTo("PUBLISHED");
        assertThat(result.marked()).isEqualTo(1);
        assertThat(result.absent()).isEqualTo(1);
        assertThat(result.passRate()).isEqualTo(100.0);
        assertThatThrownBy(() -> service.markScript(pass.getId(), BigDecimal.ONE, false, null))
                .isInstanceOf(ApiException.class).hasMessageContaining("published");
    }

    private ExamAssessment assessment(String status, Integer duration) {
        ExamAssessment a = withId(new ExamAssessment());
        a.setSchoolId(schoolId);
        a.setTitle("Mid-term");
        a.setStatus(status);
        a.setTotalMarks(new BigDecimal("100"));
        a.setDurationMinutes(duration);
        when(assessments.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, a.getId())).thenReturn(Optional.of(a));
        return a;
    }

    private ExamSession session(ExamAssessment a, String status) {
        ExamSession s = withId(new ExamSession());
        s.setAssessmentId(a.getId());
        s.setStatus(status);
        when(sessions.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, s.getId())).thenReturn(Optional.of(s));
        return s;
    }

    private ExamScript script(ExamAssessment a, ExamSession session, String status) {
        ExamScript s = withId(new ExamScript());
        s.setAssessmentId(a.getId());
        s.setSessionId(session.getId());
        s.setStudentId(UUID.randomUUID());
        s.setStatus(status);
        when(scripts.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, s.getId())).thenReturn(Optional.of(s));
        return s;
    }

    private static StudentRef student() {
        return new StudentRef(UUID.randomUUID(), null, "ADM", "Amina", "Otieno", "active");
    }

    private static <T extends com.schoolsphere.common.persistence.BaseEntity> T withId(T entity) {
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
        }
        return entity;
    }
}
