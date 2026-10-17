package com.schoolsphere.learning.internal;

import com.schoolsphere.common.tenant.TenantScoped;
import com.schoolsphere.learning.api.LearningProgressApi;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
@TenantScoped
public class LearningProgressService implements LearningProgressApi {

    private static final Set<String> SUBMITTED = Set.of("submitted", "returned", "resubmitted", "graded");
    private static final Set<String> ATTEMPT_DONE = Set.of("submitted", "graded");

    private final LearningResourceRepository resourceRepository;
    private final AssignmentRepository assignmentRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final ResourceCompletionRepository completionRepository;
    private final SubmissionRepository submissionRepository;
    private final QuizAttemptRepository attemptRepository;

    public LearningProgressService(LearningResourceRepository resourceRepository,
                                   AssignmentRepository assignmentRepository, QuizRepository quizRepository,
                                   QuizQuestionRepository questionRepository,
                                   ResourceCompletionRepository completionRepository,
                                   SubmissionRepository submissionRepository,
                                   QuizAttemptRepository attemptRepository) {
        this.resourceRepository = resourceRepository;
        this.assignmentRepository = assignmentRepository;
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.completionRepository = completionRepository;
        this.submissionRepository = submissionRepository;
        this.attemptRepository = attemptRepository;
    }

    @Override
    public Optional<ItemInfo> findItem(UUID schoolId, ItemType type, UUID id) {
        return switch (type) {
            case RESOURCE -> resourceRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, id)
                    .map(r -> new ItemInfo(type, id, r.getTitle(), r.getState()));
            case ASSIGNMENT -> assignmentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, id)
                    .map(a -> new ItemInfo(type, id, a.getTitle(), a.getState()));
            case QUIZ -> quizRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, id)
                    .map(q -> new ItemInfo(type, id, q.getTitle(), q.getState()));
        };
    }

    @Override
    public List<ItemOutcome> outcomes(UUID schoolId, UUID studentId, Collection<ItemRef> items) {
        List<ItemOutcome> outcomes = new ArrayList<>(items.size());
        for (ItemRef item : items) {
            outcomes.add(switch (item.type()) {
                case RESOURCE -> new ItemOutcome(item.type(), item.id(),
                        completionRepository.findBySchoolIdAndResourceIdAndStudentId(schoolId, item.id(), studentId)
                                .isPresent(), null);
                case ASSIGNMENT -> assignmentOutcome(schoolId, studentId, item);
                case QUIZ -> quizOutcome(schoolId, studentId, item);
            });
        }
        return outcomes;
    }

    private ItemOutcome assignmentOutcome(UUID schoolId, UUID studentId, ItemRef item) {
        Optional<Submission> submission = submissionRepository
                .findBySchoolIdAndAssignmentIdAndStudentIdAndDeletedAtIsNull(schoolId, item.id(), studentId);
        boolean completed = submission.map(s -> SUBMITTED.contains(s.getState())).orElse(false);
        Double score = null;
        if (submission.isPresent() && submission.get().getGrade() != null) {
            Assignment assignment = assignmentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId, item.id())
                    .orElse(null);
            if (assignment != null && assignment.isGradesPublished() && assignment.getMaxMarks() != null
                    && assignment.getMaxMarks().signum() > 0) {
                score = percent(submission.get().getGrade(), assignment.getMaxMarks());
            }
        }
        return new ItemOutcome(item.type(), item.id(), completed, score);
    }

    private ItemOutcome quizOutcome(UUID schoolId, UUID studentId, ItemRef item) {
        List<QuizAttempt> done = attemptRepository
                .findBySchoolIdAndQuizIdAndStudentIdOrderByAttemptNoDesc(schoolId, item.id(), studentId).stream()
                .filter(a -> ATTEMPT_DONE.contains(a.getState())).toList();
        Double score = null;
        BigDecimal best = done.stream().map(QuizAttempt::getScore).filter(java.util.Objects::nonNull)
                .max(BigDecimal::compareTo).orElse(null);
        if (best != null) {
            BigDecimal total = questionRepository.findBySchoolIdAndQuizIdOrderByPositionAsc(schoolId, item.id())
                    .stream().map(QuizQuestion::getPoints).filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.signum() > 0) {
                score = percent(best, total);
            }
        }
        return new ItemOutcome(item.type(), item.id(), !done.isEmpty(), score);
    }

    private static Double percent(BigDecimal value, BigDecimal total) {
        return Math.round(value.doubleValue() * 1000.0 / total.doubleValue()) / 10.0;
    }
}
