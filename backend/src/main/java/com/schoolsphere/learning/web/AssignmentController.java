package com.schoolsphere.learning.web;

import com.schoolsphere.common.api.PageResponse;
import com.schoolsphere.learning.internal.LearningService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning")
public class AssignmentController {

    private final LearningService learningService;

    public AssignmentController(LearningService learningService) {
        this.learningService = learningService;
    }

    // ----- teacher side -----

    @GetMapping("/assignments")
    @PreAuthorize("@policy.can('assignment.create')")
    public PageResponse<LearningService.AssignmentView> assignments(@RequestParam(required = false) UUID classGroupId,
                                                                    @RequestParam(required = false) UUID termId,
                                                                    @RequestParam(required = false) String state,
                                                                    @RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size) {
        return learningService.listAssignments(classGroupId, termId, state, page, size);
    }

    @PostMapping("/assignments")
    @PreAuthorize("@policy.can('assignment.create')")
    public LearningService.AssignmentView createAssignment(@Valid @RequestBody AssignmentRequest request) {
        return learningService.createAssignment(request.title(), request.instructions(), request.subjectId(),
                request.classGroupId(), request.streamId(), request.termId(), request.openAt(), request.dueAt(),
                request.latePolicy(), request.maxMarks(), request.rubric(), request.audience(),
                request.attachmentResourceIds());
    }

    @GetMapping("/assignments/{id}")
    @PreAuthorize("@policy.can('assignment.create')")
    public LearningService.AssignmentView assignment(@PathVariable UUID id) {
        return learningService.getAssignment(id);
    }

    @PatchMapping("/assignments/{id}")
    @PreAuthorize("@policy.can('assignment.create')")
    public LearningService.AssignmentView updateAssignment(@PathVariable UUID id,
                                                           @RequestBody AssignmentRequest request) {
        return learningService.updateAssignment(id, request.title(), request.instructions(), request.subjectId(),
                request.classGroupId(), request.streamId(), request.termId(), request.openAt(), request.dueAt(),
                request.latePolicy(), request.maxMarks(), request.rubric(), request.audience(),
                request.attachmentResourceIds());
    }

    @PostMapping("/assignments/{id}/publish")
    @PreAuthorize("@policy.can('assignment.create')")
    public LearningService.AssignmentView publishAssignment(@PathVariable UUID id) {
        return learningService.publishAssignment(id);
    }

    @GetMapping("/assignments/{id}/submissions")
    @PreAuthorize("@policy.can('assignment.grade')")
    public List<LearningService.SubmissionView> submissions(@PathVariable UUID id) {
        return learningService.listSubmissions(id);
    }

    @GetMapping("/assignments/{id}/grades")
    @PreAuthorize("@policy.can('assignment.grade')")
    public List<LearningService.GradeRowView> grades(@PathVariable UUID id) {
        return learningService.listGrades(id);
    }

    @PostMapping("/assignments/{id}/publish-grades")
    @PreAuthorize("@policy.can('assignment.grade')")
    public LearningService.AssignmentView publishGrades(@PathVariable UUID id) {
        return learningService.publishGrades(id);
    }

    @PatchMapping("/submissions/{id}/grade")
    @PreAuthorize("@policy.can('assignment.grade')")
    public LearningService.SubmissionView grade(@PathVariable UUID id, @Valid @RequestBody GradeRequest request) {
        return learningService.grade(id, request.marks(), request.feedback());
    }

    @PostMapping("/submissions/{id}/reopen")
    @PreAuthorize("@policy.can('assignment.grade')")
    public LearningService.SubmissionView reopen(@PathVariable UUID id) {
        return learningService.reopen(id);
    }

    // ----- learner side -----

    @GetMapping("/assignments/for-me")
    public List<LearningService.MyAssignmentView> myAssignments() {
        return learningService.listMyAssignments();
    }

    @GetMapping("/assignments/my-grades")
    public List<LearningService.LearnerGradeView> myGrades() {
        return learningService.learnerGradeView();
    }

    @GetMapping("/assignments/{id}/my-submission")
    public LearningService.SubmissionView mySubmission(@PathVariable UUID id) {
        return learningService.getMySubmission(id);
    }

    @PostMapping("/assignments/{id}/draft")
    @PreAuthorize("@policy.can('assignment.submit')")
    public LearningService.SubmissionView saveDraft(@PathVariable UUID id,
                                                    @Valid @RequestBody SubmissionRequest request) {
        return learningService.saveDraft(id, request.contentText(), request.attachmentResourceIds());
    }

    @PostMapping("/assignments/{id}/submit")
    @PreAuthorize("@policy.can('assignment.submit')")
    public LearningService.SubmissionView submit(@PathVariable UUID id,
                                                 @Valid @RequestBody SubmissionRequest request,
                                                 @RequestHeader(value = "Idempotency-Key", required = false)
                                                 String idempotencyKey) {
        return learningService.submit(id, request.contentText(), request.attachmentResourceIds(), idempotencyKey);
    }

    public record AssignmentRequest(@NotBlank String title, String instructions, UUID subjectId, UUID classGroupId,
                                    UUID streamId, UUID termId, Instant openAt, Instant dueAt, String latePolicy,
                                    BigDecimal maxMarks, Map<String, Object> rubric, Map<String, Object> audience,
                                    List<String> attachmentResourceIds) {
    }

    public record SubmissionRequest(String contentText, List<String> attachmentResourceIds) {
    }

    public record GradeRequest(@NotNull BigDecimal marks, String feedback) {
    }
}
