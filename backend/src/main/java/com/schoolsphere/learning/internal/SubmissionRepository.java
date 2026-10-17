package com.schoolsphere.learning.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    Optional<Submission> findBySchoolIdAndAssignmentIdAndStudentIdAndDeletedAtIsNull(UUID schoolId, UUID assignmentId,
                                                                                     UUID studentId);

    List<Submission> findBySchoolIdAndAssignmentIdAndDeletedAtIsNull(UUID schoolId, UUID assignmentId);

    List<Submission> findBySchoolIdAndStudentIdAndDeletedAtIsNull(UUID schoolId, UUID studentId);

    Optional<Submission> findBySchoolIdAndIdAndDeletedAtIsNull(UUID schoolId, UUID id);

    Optional<Submission> findBySchoolIdAndIdempotencyKeyAndDeletedAtIsNull(UUID schoolId, String idempotencyKey);
}
