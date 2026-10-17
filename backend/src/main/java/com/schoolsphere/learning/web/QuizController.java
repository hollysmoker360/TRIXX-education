package com.schoolsphere.learning.web;

import com.schoolsphere.learning.internal.QuizService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    // ----- teacher side -----

    @GetMapping("/quizzes")
    @PreAuthorize("@policy.can('assignment.create')")
    public List<QuizService.QuizView> quizzes() {
        return quizService.listQuizzes();
    }

    @PostMapping("/quizzes")
    @PreAuthorize("@policy.can('assignment.create')")
    public QuizService.QuizView createQuiz(@Valid @RequestBody QuizRequest request) {
        List<QuizService.QuestionInput> questions = request.questions() == null ? List.of()
                : request.questions().stream()
                .map(question -> new QuizService.QuestionInput(question.type(), question.prompt(),
                        question.options(), question.answerKey(), question.points()))
                .toList();
        return quizService.createQuiz(request.assignmentId(), request.title(), request.subjectId(),
                request.classGroupId(), request.timeLimitMinutes(), request.maxAttempts(), request.shuffle(),
                questions);
    }

    @GetMapping("/quizzes/{id}")
    @PreAuthorize("@policy.can('assignment.create')")
    public QuizService.QuizView quiz(@PathVariable UUID id) {
        return quizService.getQuiz(id);
    }

    @PostMapping("/quizzes/{id}/publish")
    @PreAuthorize("@policy.can('assignment.create')")
    public QuizService.QuizView publishQuiz(@PathVariable UUID id) {
        return quizService.publishQuiz(id);
    }

    @GetMapping("/quizzes/{id}/attempts")
    @PreAuthorize("@policy.can('assignment.create')")
    public List<QuizService.AttemptView> attempts(@PathVariable UUID id) {
        return quizService.listAttempts(id);
    }

    @GetMapping("/quizzes/{id}/attempts/{attemptId}")
    @PreAuthorize("@policy.can('assignment.create')")
    public QuizService.AttemptDetailView attemptDetail(@PathVariable UUID id, @PathVariable UUID attemptId) {
        return quizService.attemptDetail(id, attemptId);
    }

    // ----- learner side -----

    @GetMapping("/quizzes/for-me")
    public List<QuizService.LearnerQuizView> quizzesForMe() {
        return quizService.listQuizzesForMe();
    }

    @PostMapping("/quizzes/{id}/attempts")
    public QuizService.LearnerAttemptStart startAttempt(@PathVariable UUID id) {
        return quizService.startAttempt(id);
    }

    @PostMapping("/quizzes/{id}/attempts/{attemptId}/submit")
    @PreAuthorize("@policy.can('assignment.submit')")
    public QuizService.LearnerAttemptStart submitAttempt(@PathVariable UUID id, @PathVariable UUID attemptId,
                                                         @RequestBody(required = false) AttemptAnswersRequest request) {
        return quizService.submitAttempt(id, attemptId, request == null ? null : request.answers());
    }

    @GetMapping("/quizzes/{id}/my-attempts")
    public List<QuizService.AttemptView> myAttempts(@PathVariable UUID id) {
        return quizService.myAttempts(id);
    }

    public record QuizRequest(UUID assignmentId, @NotBlank String title, UUID subjectId, UUID classGroupId,
                              Integer timeLimitMinutes, Integer maxAttempts, Boolean shuffle,
                              @Valid List<QuestionRequest> questions) {
    }

    public record QuestionRequest(@NotBlank String type, @NotBlank String prompt, List<Object> options,
                                  Map<String, Object> answerKey, BigDecimal points) {
    }

    public record AttemptAnswersRequest(Map<String, Object> answers) {
    }
}
