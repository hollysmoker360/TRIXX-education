package com.schoolsphere.learning.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, UUID> {

    List<QuizQuestion> findBySchoolIdAndQuizIdOrderByPositionAsc(UUID schoolId, UUID quizId);
}
