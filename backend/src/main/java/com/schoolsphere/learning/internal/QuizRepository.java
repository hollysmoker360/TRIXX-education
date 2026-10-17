package com.schoolsphere.learning.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    Optional<Quiz> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    List<Quiz> findBySchoolIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID schoolId);

    List<Quiz> findBySchoolIdAndAssignmentIdAndDeletedAtIsNull(UUID schoolId, UUID assignmentId);

    List<Quiz> findBySchoolIdAndStateAndDeletedAtIsNullOrderByCreatedAtDesc(UUID schoolId, String state);
}
