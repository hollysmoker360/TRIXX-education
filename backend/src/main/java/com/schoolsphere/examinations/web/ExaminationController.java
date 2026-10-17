package com.schoolsphere.examinations.web;

import com.schoolsphere.common.api.PageResponse;
import com.schoolsphere.examinations.internal.ExaminationService;
import com.schoolsphere.examinations.internal.ExaminationService.AssessmentInput;
import com.schoolsphere.examinations.internal.ExaminationService.AssessmentView;
import com.schoolsphere.examinations.internal.ExaminationService.MyResultView;
import com.schoolsphere.examinations.internal.ExaminationService.ResultView;
import com.schoolsphere.examinations.internal.ExaminationService.ScriptView;
import com.schoolsphere.examinations.internal.ExaminationService.SessionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/examinations")
public class ExaminationController {

    private static final String VIEW = "@policy.can('exam.manage') or @policy.can('exam.mark') or @policy.can('exam.approve')";

    private final ExaminationService examinationService;

    public ExaminationController(ExaminationService examinationService) {
        this.examinationService = examinationService;
    }

    // ----- assessments -----

    @GetMapping("/assessments")
    @PreAuthorize(VIEW)
    public PageResponse<AssessmentView> assessments(@RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return examinationService.listAssessments(status, page, size);
    }

    @PostMapping("/assessments")
    @PreAuthorize("@policy.can('exam.manage')")
    public AssessmentView createAssessment(@RequestBody AssessmentInput request) {
        return examinationService.createAssessment(request);
    }

    @GetMapping("/assessments/{id}")
    @PreAuthorize(VIEW)
    public AssessmentView assessment(@PathVariable UUID id) {
        return examinationService.getAssessment(id);
    }

    @PatchMapping("/assessments/{id}")
    @PreAuthorize("@policy.can('exam.manage')")
    public AssessmentView updateAssessment(@PathVariable UUID id, @RequestBody AssessmentInput request) {
        return examinationService.updateAssessment(id, request);
    }

    @DeleteMapping("/assessments/{id}")
    @PreAuthorize("@policy.can('exam.manage')")
    public void deleteAssessment(@PathVariable UUID id) {
        examinationService.deleteAssessment(id);
    }

    @PostMapping("/assessments/{id}/submit")
    @PreAuthorize("@policy.can('exam.manage')")
    public AssessmentView submit(@PathVariable UUID id) {
        return examinationService.submitForReview(id);
    }

    @PostMapping("/assessments/{id}/return")
    @PreAuthorize("@policy.can('exam.approve')")
    public AssessmentView returnToDraft(@PathVariable UUID id) {
        return examinationService.returnToDraft(id);
    }

    @PostMapping("/assessments/{id}/approve")
    @PreAuthorize("@policy.can('exam.approve')")
    public AssessmentView approve(@PathVariable UUID id) {
        return examinationService.approve(id);
    }

    @PostMapping("/assessments/{id}/publish")
    @PreAuthorize("@policy.can('exam.approve')")
    public AssessmentView publish(@PathVariable UUID id) {
        return examinationService.publish(id);
    }

    @PostMapping("/assessments/{id}/archive")
    @PreAuthorize("@policy.can('exam.manage')")
    public AssessmentView archive(@PathVariable UUID id) {
        return examinationService.archive(id);
    }

    // ----- sittings -----

    @GetMapping("/exam-sessions")
    @PreAuthorize(VIEW)
    public PageResponse<SessionView> sessions(@RequestParam(required = false) UUID assessmentId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return examinationService.listSessions(assessmentId, page, size);
    }

    @PostMapping("/exam-sessions")
    @PreAuthorize("@policy.can('exam.manage')")
    public SessionView createSession(@Valid @RequestBody SessionRequest request) {
        return examinationService.createSession(request.assessmentId(), request.title(), request.startsAt(),
                request.endsAt(), request.venue(), request.studentIds());
    }

    @GetMapping("/exam-sessions/{id}/candidates")
    @PreAuthorize(VIEW)
    public List<ScriptView> candidates(@PathVariable UUID id) {
        return examinationService.sessionCandidates(id);
    }

    @PostMapping("/exam-sessions/{id}/candidates")
    @PreAuthorize("@policy.can('exam.manage')")
    public SessionView registerCandidates(@PathVariable UUID id, @RequestBody CandidatesRequest request) {
        return examinationService.registerCandidates(id, request.studentIds());
    }

    @PostMapping("/exam-sessions/{id}/start")
    @PreAuthorize("@policy.can('exam.manage')")
    public SessionView start(@PathVariable UUID id) {
        return examinationService.startSession(id);
    }

    @PostMapping("/exam-sessions/{id}/complete")
    @PreAuthorize("@policy.can('exam.manage')")
    public SessionView complete(@PathVariable UUID id) {
        return examinationService.completeSession(id);
    }

    @PostMapping("/exam-sessions/{id}/cancel")
    @PreAuthorize("@policy.can('exam.manage')")
    public SessionView cancel(@PathVariable UUID id) {
        return examinationService.cancelSession(id);
    }

    // ----- marking -----

    @GetMapping("/marking-queue")
    @PreAuthorize("@policy.can('exam.mark') or @policy.can('exam.approve')")
    public PageResponse<ScriptView> markingQueue(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return examinationService.markingQueue(page, size);
    }

    @PostMapping("/marking-queue/{scriptId}/mark")
    @PreAuthorize("@policy.can('exam.mark')")
    public ScriptView mark(@PathVariable UUID scriptId, @RequestBody MarkRequest request) {
        return examinationService.markScript(scriptId, request.marks(), Boolean.TRUE.equals(request.absent()),
                request.remarks());
    }

    @PostMapping("/marking-queue/{scriptId}/moderate")
    @PreAuthorize("@policy.can('exam.approve')")
    public ScriptView moderate(@PathVariable UUID scriptId, @RequestBody MarkRequest request) {
        return examinationService.moderateScript(scriptId, request.marks(), request.remarks());
    }

    // ----- results -----

    @GetMapping("/results")
    @PreAuthorize(VIEW)
    public PageResponse<ResultView> results(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return examinationService.listResults(page, size);
    }

    @GetMapping("/results/{assessmentId}/candidates")
    @PreAuthorize(VIEW)
    public List<ScriptView> resultCandidates(@PathVariable UUID assessmentId) {
        return examinationService.resultCandidates(assessmentId);
    }

    @PostMapping("/results/{assessmentId}/publish")
    @PreAuthorize("@policy.can('exam.approve')")
    public ResultView publishResults(@PathVariable UUID assessmentId) {
        return examinationService.publishResults(assessmentId);
    }

    @GetMapping("/my-results")
    @PreAuthorize("@policy.can('grade.view.own')")
    public List<MyResultView> myResults() {
        return examinationService.myResults();
    }

    public record SessionRequest(@NotNull UUID assessmentId, String title, @NotNull Instant startsAt,
                                 @NotNull Instant endsAt, String venue, List<UUID> studentIds) {
    }

    public record CandidatesRequest(List<UUID> studentIds) {
    }

    public record MarkRequest(BigDecimal marks, Boolean absent, String remarks) {
    }
}
