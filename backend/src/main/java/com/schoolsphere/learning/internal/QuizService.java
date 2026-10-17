package com.schoolsphere.learning.internal;

import com.schoolsphere.academics.api.AcademicsApi;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.security.SecurityUtils;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import com.schoolsphere.people.api.PeopleApi;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Quizzes with auto-graded attempts; answer keys never leave the teacher view before submission (spec 11.2). */
@Service
@TenantScoped
public class QuizService {

    private static final List<String> QUESTION_TYPES = List.of("mcq", "true_false", "short_answer", "manual");

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final AssignmentRepository assignmentRepository;
    private final AcademicsApi academicsApi;
    private final PeopleApi peopleApi;

    public QuizService(QuizRepository quizRepository,
                       QuizQuestionRepository questionRepository,
                       QuizAttemptRepository attemptRepository,
                       AssignmentRepository assignmentRepository,
                       AcademicsApi academicsApi,
                       PeopleApi peopleApi) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.attemptRepository = attemptRepository;
        this.assignmentRepository = assignmentRepository;
        this.academicsApi = academicsApi;
        this.peopleApi = peopleApi;
    }

    // ----- management -----

    public List<QuizView> listQuizzes() {
        return quizRepository.findBySchoolIdAndDeletedAtIsNullOrderByCreatedAtDesc(schoolId()).stream()
                .map(quiz -> toQuizView(quiz, questions(quiz.getId()))).toList();
    }

    public QuizView getQuiz(UUID id) {
        Quiz quiz = requireQuiz(id);
        return toQuizView(quiz, questions(quiz.getId()));
    }

    public QuizView createQuiz(UUID assignmentId, String title, UUID subjectId, UUID classGroupId,
                               Integer timeLimitMinutes, Integer maxAttempts, Boolean shuffle,
                               List<QuestionInput> questions) {
        if (assignmentId != null) {
            Assignment assignment = assignmentRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), assignmentId)
                    .orElseThrow(() -> ApiException.notFound("assignment_not_found", "Assignment not found."));
            if (subjectId == null) {
                subjectId = assignment.getSubjectId();
            }
            if (classGroupId == null) {
                classGroupId = assignment.getClassGroupId();
            }
        }
        if (subjectId != null && academicsApi.findSubject(schoolId(), subjectId).isEmpty()) {
            throw ApiException.badRequest("subject_not_found", "Subject not found.");
        }
        if (classGroupId != null && academicsApi.findClass(schoolId(), classGroupId).isEmpty()) {
            throw ApiException.badRequest("class_not_found", "Class not found.");
        }
        Quiz quiz = new Quiz();
        quiz.setSchoolId(schoolId());
        quiz.setAssignmentId(assignmentId);
        quiz.setTitle(title);
        quiz.setSubjectId(subjectId);
        quiz.setClassGroupId(classGroupId);
        quiz.setTimeLimitMinutes(timeLimitMinutes);
        quiz.setMaxAttempts(maxAttempts == null || maxAttempts < 1 ? 1 : maxAttempts);
        quiz.setShuffle(Boolean.TRUE.equals(shuffle));
        quiz.setState("draft");
        quiz.setCreatedBy(TenantContext.actorUserId());
        quiz.setUpdatedBy(TenantContext.actorUserId());
        quiz = quizRepository.save(quiz);
        int position = 0;
        for (QuestionInput input : questions == null ? List.<QuestionInput>of() : questions) {
            position++;
            questionRepository.save(buildQuestion(quiz.getId(), position, input));
        }
        return toQuizView(quiz, questions(quiz.getId()));
    }

    public QuizView publishQuiz(UUID id) {
        Quiz quiz = requireQuiz(id);
        if ("archived".equals(quiz.getState())) {
            throw ApiException.conflict("quiz_archived", "An archived quiz cannot be published.");
        }
        List<QuizQuestion> questions = questions(quiz.getId());
        if (questions.isEmpty()) {
            throw ApiException.badRequest("quiz_empty", "A quiz needs at least one question before publishing.");
        }
        quiz.setState("published");
        quiz.setUpdatedBy(TenantContext.actorUserId());
        return toQuizView(quizRepository.save(quiz), questions);
    }

    // ----- learner side -----

    public List<LearnerQuizView> listQuizzesForMe() {
        UUID studentId = requireStudentId();
        List<LearnerQuizView> result = new ArrayList<>();
        for (Quiz quiz : quizRepository.findBySchoolIdAndStateAndDeletedAtIsNullOrderByCreatedAtDesc(schoolId(),
                "published")) {
            if (!canStudentRead(studentId, quiz)) {
                continue;
            }
            List<QuizAttempt> attempts = attemptRepository
                    .findBySchoolIdAndQuizIdAndStudentIdOrderByAttemptNoDesc(schoolId(), quiz.getId(), studentId);
            for (QuizAttempt attempt : attempts) {
                expireAttemptIfDue(quiz, attempt);
            }
            result.add(new LearnerQuizView(quiz.getId(), quiz.getTitle(), quiz.getSubjectId(), quiz.getClassGroupId(),
                    quiz.getTimeLimitMinutes(), quiz.getMaxAttempts(), quiz.isShuffle(),
                    questions(quiz.getId()).size(), attempts.size(),
                    attempts.isEmpty() ? null : attempts.get(0).getState()));
        }
        return result;
    }

    public LearnerAttemptStart startAttempt(UUID quizId) {
        UUID studentId = requireStudentId();
        Quiz quiz = requireQuizForStudent(quizId, studentId);
        List<QuizAttempt> attempts = attemptRepository
                .findBySchoolIdAndQuizIdAndStudentIdOrderByAttemptNoDesc(schoolId(), quizId, studentId);
        for (QuizAttempt attempt : attempts) {
            expireAttemptIfDue(quiz, attempt);
        }
        QuizAttempt active = attempts.stream().filter(attempt -> "in_progress".equals(attempt.getState()))
                .findFirst().orElse(null);
        if (active == null) {
            if (attempts.size() >= quiz.getMaxAttempts()) {
                throw ApiException.conflict("max_attempts_reached",
                        "The maximum number of attempts has been reached.");
            }
            active = new QuizAttempt();
            active.setSchoolId(schoolId());
            active.setQuizId(quizId);
            active.setStudentId(studentId);
            active.setAttemptNo(attempts.isEmpty() ? 1 : attempts.get(0).getAttemptNo() + 1);
            active.setState("in_progress");
            active.setStartedAt(Instant.now());
            active.setAnswers(new LinkedHashMap<>());
            active = attemptRepository.save(active);
        }
        return attemptSession(quiz, active);
    }

    public LearnerAttemptStart submitAttempt(UUID quizId, UUID attemptId, Map<String, Object> answers) {
        UUID studentId = requireStudentId();
        Quiz quiz = requireQuizForStudent(quizId, studentId);
        QuizAttempt attempt = attemptRepository.findBySchoolIdAndId(schoolId(), attemptId)
                .filter(candidate -> candidate.getQuizId().equals(quizId) && candidate.getStudentId().equals(studentId))
                .orElseThrow(() -> ApiException.notFound("attempt_not_found", "Attempt not found."));
        if (!"in_progress".equals(attempt.getState())) {
            throw ApiException.conflict("attempt_already_submitted", "The attempt has already been submitted.");
        }
        attempt.setAnswers(answers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(answers));
        attempt.setSubmittedAt(Instant.now());
        autoGrade(quiz, attempt);
        attempt = attemptRepository.save(attempt);
        return attemptSession(quiz, attempt);
    }

    public List<AttemptView> myAttempts(UUID quizId) {
        UUID studentId = requireStudentId();
        Quiz quiz = requireQuiz(quizId);
        List<QuizAttempt> attempts = attemptRepository
                .findBySchoolIdAndQuizIdAndStudentIdOrderByAttemptNoDesc(schoolId(), quizId, studentId);
        for (QuizAttempt attempt : attempts) {
            expireAttemptIfDue(quiz, attempt);
        }
        return attempts.stream().map(attempt -> toAttemptView(attempt, null)).toList();
    }

    // ----- teacher side -----

    public List<AttemptView> listAttempts(UUID quizId) {
        requireQuiz(quizId);
        List<QuizAttempt> attempts = attemptRepository.findBySchoolIdAndQuizIdOrderByStartedAtDesc(schoolId(), quizId);
        Map<UUID, String> names = studentNames(attempts.stream().map(QuizAttempt::getStudentId).distinct().toList());
        return attempts.stream().map(attempt -> toAttemptView(attempt, names.get(attempt.getStudentId()))).toList();
    }

    public AttemptDetailView attemptDetail(UUID quizId, UUID attemptId) {
        requireQuiz(quizId);
        QuizAttempt attempt = attemptRepository.findBySchoolIdAndId(schoolId(), attemptId)
                .filter(candidate -> candidate.getQuizId().equals(quizId))
                .orElseThrow(() -> ApiException.notFound("attempt_not_found", "Attempt not found."));
        Map<String, Object> answers = attempt.getAnswers() == null ? Map.of() : attempt.getAnswers();
        List<AttemptAnswerView> rows = new ArrayList<>();
        for (QuizQuestion question : questions(quizId)) {
            Object given = answers.get(question.getId().toString());
            Boolean correct = correctness(question, given);
            BigDecimal earned = correct == null ? null : (correct ? question.getPoints() : BigDecimal.ZERO);
            rows.add(new AttemptAnswerView(question.getId(), question.getPosition(), question.getQtype(),
                    question.getPrompt(), given, question.getAnswerKey(), question.getPoints(), earned, correct));
        }
        Map<UUID, String> names = studentNames(List.of(attempt.getStudentId()));
        return new AttemptDetailView(toAttemptView(attempt, names.get(attempt.getStudentId())), rows);
    }

    // ----- helpers -----

    private QuizQuestion buildQuestion(UUID quizId, int position, QuestionInput input) {
        String type = input.type();
        if (type == null || !QUESTION_TYPES.contains(type)) {
            throw ApiException.badRequest("invalid_question_type", "Unknown question type.");
        }
        if (input.prompt() == null || input.prompt().isBlank()) {
            throw ApiException.badRequest("prompt_required", "A question prompt is required.");
        }
        QuizQuestion question = new QuizQuestion();
        question.setSchoolId(schoolId());
        question.setQuizId(quizId);
        question.setPosition(position);
        question.setQtype(type);
        question.setPrompt(input.prompt());
        question.setPoints(input.points() == null ? BigDecimal.ONE : input.points());
        List<Object> options = input.options() == null ? new ArrayList<>() : new ArrayList<>(input.options());
        Map<String, Object> answerKey = input.answerKey();
        switch (type) {
            case "mcq" -> {
                if (options.size() < 2 || options.size() > 6) {
                    throw ApiException.badRequest("invalid_options",
                            "Multiple choice questions need between 2 and 6 options.");
                }
                Object index = answerKey == null ? null : answerKey.get("index");
                if (!(index instanceof Number number) || number.intValue() < 0
                        || number.intValue() >= options.size()) {
                    throw ApiException.badRequest("invalid_answer_key",
                            "Multiple choice questions need an {index} answer key.");
                }
                question.setAnswerKey(Map.of("index", number.intValue()));
            }
            case "true_false" -> {
                Object value = answerKey == null ? null : answerKey.get("value");
                if (!(value instanceof Boolean)) {
                    throw ApiException.badRequest("invalid_answer_key",
                            "True/false questions need a {value} answer key.");
                }
                question.setAnswerKey(Map.of("value", value));
            }
            case "short_answer" -> {
                Object text = answerKey == null ? null : answerKey.get("text");
                if (!(text instanceof String string) || string.isBlank()) {
                    throw ApiException.badRequest("invalid_answer_key",
                            "Short answer questions need a {text} answer key.");
                }
                question.setAnswerKey(Map.of("text", string));
            }
            default -> {
                // manual questions are graded by the teacher; they carry no answer key
            }
        }
        question.setOptions(options);
        return question;
    }

    private void expireAttemptIfDue(Quiz quiz, QuizAttempt attempt) {
        if (!"in_progress".equals(attempt.getState()) || quiz.getTimeLimitMinutes() == null
                || attempt.getStartedAt() == null) {
            return;
        }
        Instant deadline = attempt.getStartedAt().plusSeconds(quiz.getTimeLimitMinutes() * 60L);
        if (!Instant.now().isAfter(deadline)) {
            return;
        }
        attempt.setSubmittedAt(deadline);
        autoGrade(quiz, attempt);
        attemptRepository.save(attempt);
    }

    /** mcq/true_false grade exactly, short_answer trimmed case-insensitively, manual stays null. */
    private void autoGrade(Quiz quiz, QuizAttempt attempt) {
        Map<String, Object> answers = attempt.getAnswers() == null ? Map.of() : attempt.getAnswers();
        BigDecimal score = BigDecimal.ZERO;
        boolean allGraded = true;
        for (QuizQuestion question : questions(quiz.getId())) {
            Boolean correct = correctness(question, answers.get(question.getId().toString()));
            if (correct == null) {
                allGraded = false;
            } else if (correct) {
                score = score.add(question.getPoints());
            }
        }
        attempt.setScore(score);
        attempt.setState(allGraded ? "graded" : "submitted");
    }

    private Boolean correctness(QuizQuestion question, Object given) {
        return switch (question.getQtype()) {
            case "mcq" -> {
                Object index = question.getAnswerKey() == null ? null : question.getAnswerKey().get("index");
                if (!(index instanceof Number expected)) {
                    yield null;
                }
                yield given instanceof Number number && number.intValue() == expected.intValue();
            }
            case "true_false" -> {
                Object value = question.getAnswerKey() == null ? null : question.getAnswerKey().get("value");
                if (!(value instanceof Boolean expected)) {
                    yield null;
                }
                yield given instanceof Boolean answer && answer.equals(expected);
            }
            case "short_answer" -> {
                Object text = question.getAnswerKey() == null ? null : question.getAnswerKey().get("text");
                if (!(text instanceof String expected)) {
                    yield null;
                }
                yield given instanceof String answer && normalize(answer).equals(normalize(expected));
            }
            default -> null;
        };
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private LearnerAttemptStart attemptSession(Quiz quiz, QuizAttempt attempt) {
        List<QuestionView> views = questions(quiz.getId()).stream()
                .map(question -> toStudentQuestionView(question, attempt)).toList();
        return new LearnerAttemptStart(toAttemptView(attempt, null), views);
    }

    private Quiz requireQuiz(UUID id) {
        return quizRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), id)
                .orElseThrow(() -> ApiException.notFound("quiz_not_found", "Quiz not found."));
    }

    private Quiz requireQuizForStudent(UUID id, UUID studentId) {
        Quiz quiz = requireQuiz(id);
        if (!"published".equals(quiz.getState())) {
            throw ApiException.conflict("quiz_not_published", "The quiz is not published.");
        }
        if (!canStudentRead(studentId, quiz)) {
            throw ApiException.forbidden("quiz_not_available", "This quiz is not available to you.");
        }
        return quiz;
    }

    private boolean canStudentRead(UUID studentId, Quiz quiz) {
        if (quiz.getClassGroupId() == null) {
            return true;
        }
        return peopleApi.listStudentsByClass(schoolId(), quiz.getClassGroupId(), null).stream()
                .anyMatch(student -> student.id().equals(studentId));
    }

    private UUID requireStudentId() {
        return peopleApi.studentForUser(schoolId(), SecurityUtils.requireUserId())
                .map(PeopleApi.StudentRef::id)
                .orElseThrow(() -> ApiException.forbidden("not_a_student",
                        "This action is only available to students."));
    }

    private List<QuizQuestion> questions(UUID quizId) {
        return questionRepository.findBySchoolIdAndQuizIdOrderByPositionAsc(schoolId(), quizId);
    }

    private Map<UUID, String> studentNames(List<UUID> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        return peopleApi.findStudents(schoolId(), studentIds).stream()
                .collect(Collectors.toMap(PeopleApi.StudentRef::id, PeopleApi.StudentRef::fullName));
    }

    private UUID schoolId() {
        return TenantContext.schoolId();
    }

    private QuizView toQuizView(Quiz quiz, List<QuizQuestion> questions) {
        List<QuestionView> views = questions.stream().map(this::toTeacherQuestionView).toList();
        return new QuizView(quiz.getId(), quiz.getAssignmentId(), quiz.getTitle(), quiz.getSubjectId(),
                quiz.getClassGroupId(), quiz.getTimeLimitMinutes(), quiz.getMaxAttempts(), quiz.isShuffle(),
                quiz.getState(), views);
    }

    private QuestionView toTeacherQuestionView(QuizQuestion question) {
        return questionView(question, question.getAnswerKey());
    }

    private QuestionView toStudentQuestionView(QuizQuestion question, QuizAttempt attempt) {
        Map<String, Object> answerKey = "in_progress".equals(attempt.getState()) ? null : question.getAnswerKey();
        return questionView(question, answerKey);
    }

    private QuestionView questionView(QuizQuestion question, Map<String, Object> answerKey) {
        return new QuestionView(question.getId(), question.getPosition(), question.getQtype(), question.getPrompt(),
                question.getOptions(), question.getPoints(), answerKey);
    }

    private AttemptView toAttemptView(QuizAttempt attempt, String studentName) {
        return new AttemptView(attempt.getId(), attempt.getQuizId(), attempt.getStudentId(), studentName,
                attempt.getAttemptNo(), attempt.getState(), attempt.getStartedAt(), attempt.getSubmittedAt(),
                attempt.getScore(), attempt.getAnswers());
    }

    public record QuestionInput(String type, String prompt, List<Object> options, Map<String, Object> answerKey,
                                BigDecimal points) {
    }

    public record QuizView(UUID id, UUID assignmentId, String title, UUID subjectId, UUID classGroupId,
                           Integer timeLimitMinutes, int maxAttempts, boolean shuffle, String state,
                           List<QuestionView> questions) {
    }

    public record QuestionView(UUID id, int position, String type, String prompt, List<Object> options,
                               BigDecimal points, Map<String, Object> answerKey) {
    }

    public record LearnerQuizView(UUID id, String title, UUID subjectId, UUID classGroupId, Integer timeLimitMinutes,
                                  int maxAttempts, boolean shuffle, int questionCount, int attemptsUsed,
                                  String attemptState) {
    }

    public record AttemptView(UUID id, UUID quizId, UUID studentId, String studentName, int attemptNo, String state,
                              Instant startedAt, Instant submittedAt, BigDecimal score, Map<String, Object> answers) {
    }

    public record AttemptAnswerView(UUID questionId, int position, String type, String prompt, Object given,
                                    Map<String, Object> answerKey, BigDecimal points, BigDecimal earned,
                                    Boolean correct) {
    }

    public record AttemptDetailView(AttemptView attempt, List<AttemptAnswerView> answers) {
    }

    public record LearnerAttemptStart(AttemptView attempt, List<QuestionView> questions) {
    }
}
