package com.schoolsphere.learning.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    List<QuizAttempt> findBySchoolIdAndQuizIdAndStudentIdOrderByAttemptNoDesc(UUID schoolId, UUID quizId,
                                                                             UUID studentId);

    List<QuizAttempt> findBySchoolIdAndQuizIdOrderByStartedAtDesc(UUID schoolId, UUID quizId);

    Optional<QuizAttempt> findBySchoolIdAndId(UUID schoolId, UUID id);

    List<QuizAttempt> findBySchoolIdAndQuizIdAndStudentIdAndStateOrderByAttemptNoDesc(UUID schoolId, UUID quizId,
                                                                                     UUID studentId, String state);
}
